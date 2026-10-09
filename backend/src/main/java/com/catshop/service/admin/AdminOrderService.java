package com.catshop.service.admin;

import com.catshop.common.PageResult;
import com.catshop.dto.admin.order.AdminOrderDetailResponse;
import com.catshop.dto.admin.order.AdminOrderSummaryResponse;
import com.catshop.dto.admin.order.UpdateOrderStatusRequest;
import com.catshop.dto.order.OrderItemResponse;
import com.catshop.entity.Order;
import com.catshop.entity.OrderItem;
import com.catshop.entity.ProductSku;
import com.catshop.exception.BusinessException;
import com.catshop.repository.OrderItemRepository;
import com.catshop.repository.OrderRepository;
import com.catshop.repository.ProductSkuRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Back-office order management (AO1-AO3).
 * Reachable only through /api/admin/** guarded by hasRole('ADMIN').
 */
@Service
@RequiredArgsConstructor
public class AdminOrderService {
    private final com.catshop.service.StockMovementService stockMovements;
    private final com.catshop.mapper.OrderMapper orderMapper;

    private final com.catshop.mapper.AdminMapper adminMapper;

    /** Order status state machine — arbitrary jumps are forbidden. */
    private static final Map<String, Set<String>> TRANSITIONS = Map.of(
            "PENDING", Set.of("PAID", "SHIPPED", "CANCELLED"),
            "PAID", Set.of("SHIPPED", "CANCELLED"),
            "SHIPPED", Set.of("COMPLETED"),
            "COMPLETED", Set.of(),
            "CANCELLED", Set.of()
    );

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductSkuRepository skuRepository;

    /** AO1 — admin order list with status / member / orderNo / date filters. */
    @Transactional(readOnly = true)
    public PageResult<AdminOrderSummaryResponse> list(String status, Long memberId, String orderNo,
                                                      LocalDate from, LocalDate to,
                                                      int page, int size, String sort) {
        com.catshop.common.AdminListParameters.paging(page, size);
        com.catshop.common.AdminListParameters.text(orderNo, 200, "訂單編號");
        if (status != null && !status.isBlank() && !TRANSITIONS.containsKey(status.trim().toUpperCase(java.util.Locale.ROOT)))
            throw BusinessException.badRequest("不支援此訂單狀態");
        for (LocalDate date : new LocalDate[]{from, to})
            if (date != null && (date.getYear() < 1 || date.getYear() > 9998))
                throw BusinessException.badRequest("日期須介於 0001-01-01 至 9998-12-31");
        if (memberId != null && memberId < 1) throw BusinessException.badRequest("會員編號須為正整數");
        if (from != null && to != null && from.isAfter(to)) throw BusinessException.badRequest("開始日期不可晚於結束日期");
        String st = (status == null || status.isBlank()) ? null : status.trim().toUpperCase(java.util.Locale.ROOT);
        String no = (orderNo == null || orderNo.isBlank()) ? null : orderNo.trim();
        LocalDateTime fromTs = from != null ? from.atStartOfDay() : null;
        // Exclusive next-day boundary avoids JDBC/SQL Server fractional-second precision loss.
        LocalDateTime toTs = to != null ? to.plusDays(1).atStartOfDay() : null;

        Page<Order> result = orderRepository.adminSearch(st, memberId, no, fromTs, toTs,
                PageRequest.of(page, size, parseSort(sort)));
        Map<Long, Integer> counts = new java.util.HashMap<>();
        for (var batch : com.catshop.common.IdBatches.of(result.getContent().stream().map(Order::getId).toList())) {
            for (var count : orderItemRepository.countItems(batch))
                counts.put(count.getOrderId(), count.getItemCount().intValue());
        }
        return PageResult.of(result, o -> adminMapper.orderSummary(o, counts.getOrDefault(o.getId(), 0)));
    }

    /** AO2 — admin order detail (items + receiver snapshot + member info). */
    @Transactional(readOnly = true)
    public AdminOrderDetailResponse detail(Long id) {
        Order order = find(id);
        return toDetail(order);
    }

    /** AO3 — update order status (ship / complete / cancel), enforcing the state machine. */
    @Transactional
    public AdminOrderDetailResponse updateStatus(Long id, UpdateOrderStatusRequest req) {
        Order order = find(id);
        String target = req.status().trim().toUpperCase();
        if ("SHIPPED".equals(target)) throw BusinessException.badRequest("請使用出貨登錄並填寫物流公司與單號");
        if (!TRANSITIONS.containsKey(target)) {
            throw new BusinessException("INVALID_STATUS_TRANSITION", "不合法的訂單狀態：" + req.status(), HttpStatus.BAD_REQUEST);
        }
        String current = order.getStatus();
        if ("PENDING".equals(current) && "SHIPPED".equals(target) && !"COD".equals(order.getPaymentMethod())) {
            throw BusinessException.badRequest("未付款訂單不可出貨");
        }
        Set<String> allowed = TRANSITIONS.getOrDefault(current, Set.of());
        if (!allowed.contains(target)) {
            throw new BusinessException("INVALID_STATUS_TRANSITION",
                    "訂單狀態不可由 " + current + " 變更為 " + target, HttpStatus.BAD_REQUEST);
        }

        // Cancelling restores SKU stock within the same transaction.
        if ("CANCELLED".equals(target)) {
            for (OrderItem oi : orderItemRepository.findByOrderId(order.getId())) {
                if (oi.getSku() != null) {
                    ProductSku sku = oi.getSku();
                    int beforeStock = sku.getStock();
                    sku.setStock(sku.getStock() + oi.getQuantity());
                    skuRepository.save(sku);
                    stockMovements.record(sku,beforeStock,"ORDER_CANCELLED",null,order);
                }
            }
        }

        order.setStatus(target);
        if (req.remark() != null && !req.remark().isBlank()) {
            order.setRemark(req.remark());
        }
        orderRepository.save(order);
        return toDetail(order);
    }

    // ---- helpers ----

    private AdminOrderDetailResponse toDetail(Order order) {
        List<OrderItemResponse> items = orderItemRepository.findByOrderId(order.getId())
                .stream().map(orderMapper::item).toList();
        return adminMapper.orderDetail(order, items);
    }

    private Order find(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new BusinessException("ORDER_NOT_FOUND", "找不到訂單", HttpStatus.NOT_FOUND));
    }

    private Sort parseSort(String sort) {
        return com.catshop.common.AdminListParameters.sort(sort, Set.of("createdAt", "orderNo", "totalAmount", "status", "id"));
    }
}
