package com.catshop.service;

import com.catshop.common.PageResult;
import com.catshop.dto.order.CreateOrderRequest;
import com.catshop.dto.order.OrderItemResponse;
import com.catshop.dto.order.OrderResponse;
import com.catshop.entity.*;
import com.catshop.exception.BusinessException;
import com.catshop.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final com.catshop.mapper.OrderMapper orderMapper;

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final MemberRepository memberRepository;
    private final ProductSkuRepository skuRepository;
    private final PaymentService paymentService;
    private final ShippingService shippingService;
    private final StockMovementService stockMovements;
    private final CouponService coupons;

    /** Core checkout flow: cart -> order, with stock deduction. */
    @Transactional
    public OrderResponse createOrder(Long memberId, CreateOrderRequest req) {
        if (req.expectedAmounts()!=null && req.cartSnapshot()==null)
            throw BusinessException.badRequest("金額確認需要購物車快照");
        String requestId = com.catshop.common.OrderRequestIdentity.normalize(req.requestId());
        String requestHash = requestId == null ? null : com.catshop.common.OrderRequestIdentity.hash(req);
        if (requestId != null) {
            var previous = orderRepository.findByMemberIdAndRequestId(memberId, requestId);
            if (previous.isPresent()) return replay(previous.get(), requestHash);
        }
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> BusinessException.notFound("找不到會員"));
        Cart cart = cartRepository.lockByMemberId(memberId)
                .orElseThrow(() -> BusinessException.badRequest("購物車為空"));
        // Recheck after waiting for the cart lock: a concurrent request may have committed.
        if (requestId != null) {
            var previous = orderRepository.findByMemberIdAndRequestId(memberId, requestId);
            if (previous.isPresent()) return replay(previous.get(), requestHash);
        }
        List<CartItem> cartItems = cartItemRepository.findByCartIdOrderByIdAsc(cart.getId());
        // Replay precedes this check: a committed request must not consume a refilled cart.
        // All normal cart writes share this lock, so no mutation can pass between comparison and checkout.
        if (req.cartSnapshot() != null && !req.cartSnapshot().equals(com.catshop.common.CheckoutCartSnapshot.of(cartItems))) {
            throw new BusinessException("CART_SNAPSHOT_CONFLICT", "購物車內容已變更，請重新同步並確認商品與數量",
                    org.springframework.http.HttpStatus.CONFLICT);
        }
        if (cartItems.isEmpty()) {
            throw BusinessException.badRequest("購物車為空");
        }

        BigDecimal goodsTotal = BigDecimal.ZERO;
        for (CartItem ci : cartItems) {
            ProductSku sku = ci.getSku();
            if (!Boolean.TRUE.equals(sku.getIsActive()) || !Boolean.TRUE.equals(sku.getProduct().getIsActive())
                    || !"ON_SALE".equals(sku.getProduct().getStatus())) {
                throw BusinessException.badRequest("購物車包含已下架商品，請先移除");
            }
            if (ci.getQuantity() > sku.getStock()) {
                throw BusinessException.badRequest("商品「" + sku.getProduct().getName() + "」庫存不足");
            }
            // Use the current SKU price at checkout; order items freeze that price.
            ci.setUnitPrice(sku.getPrice());
            goodsTotal = goodsTotal.add(sku.getPrice().multiply(BigDecimal.valueOf(ci.getQuantity())));
        }
        BigDecimal shippingFee = shippingService.quote(goodsTotal);
        var redemption=coupons.redeem(memberId,req.couponCode(),goodsTotal);
        // Recompute under cart/coupon locks; never use client amounts to price the order.
        if (req.expectedAmounts()!=null && !req.expectedAmounts().matches(goodsTotal,redemption.discount(),shippingFee,
                com.catshop.common.CheckoutCartSnapshot.prices(cartItems))) {
            throw new BusinessException("CHECKOUT_PRICE_CONFLICT","商品價格、優惠或運費已變更，請重新同步報價並確認金額",
                    org.springframework.http.HttpStatus.CONFLICT);
        }

        Order order = Order.builder()
                .orderNo(generateOrderNo())
                .member(member)
                .requestId(requestId).requestHash(requestHash)
                .status(paymentService.initialOrderStatus(req.paymentMethod()))
                .paymentMethod(req.paymentMethod())
                .totalAmount(goodsTotal.subtract(redemption.discount()).add(shippingFee))
                .couponId(redemption.id()).couponCode(redemption.code()).discountAmount(redemption.discount())
                .shippingFee(shippingFee)
                .receiverName(req.receiverName())
                .receiverPhone(req.receiverPhone())
                .shippingAddress(req.shippingAddress())
                .remark(req.remark())
                .build();
        order = orderRepository.save(order);

        for (CartItem ci : cartItems) {
            ProductSku sku = ci.getSku();
            OrderItem oi = OrderItem.builder()
                    .order(order)
                    .sku(sku)
                    .productName(sku.getProduct().getName())
                    .skuSpec(sku.getSpecName())
                    .unitPrice(ci.getUnitPrice())
                    .quantity(ci.getQuantity())
                    .subtotal(ci.getUnitPrice().multiply(BigDecimal.valueOf(ci.getQuantity())))
                    .build();
            orderItemRepository.save(oi);

            // deduct stock
            int beforeStock = sku.getStock();
            sku.setStock(sku.getStock() - ci.getQuantity());
            skuRepository.save(sku);
            stockMovements.record(sku, beforeStock, "ORDER_PLACED", memberId, order);
        }

        cartItemRepository.deleteByCartId(cart.getId());

        List<OrderItemResponse> items = orderItemRepository.findByOrderId(order.getId())
                .stream().map(orderMapper::item).toList();
        return orderMapper.detail(order, items);
    }

    @Transactional(readOnly = true)
    public OrderResponse getByRequestId(Long memberId, String requestId) {
        String normalized = com.catshop.common.OrderRequestIdentity.normalize(requestId);
        if (normalized == null) throw BusinessException.badRequest("請提供下單識別碼");
        Order order = orderRepository.findByMemberIdAndRequestId(memberId, normalized)
                .orElseThrow(() -> BusinessException.notFound("尚未查到此下單識別碼的訂單，請勿改用新識別碼重送"));
        return detail(order);
    }

    private OrderResponse replay(Order order, String requestHash) {
        if (!requestHash.equals(order.getRequestHash())) {
            throw new BusinessException("REQUEST_ID_CONFLICT", "同一下單識別碼不可使用不同的下單內容",
                    org.springframework.http.HttpStatus.CONFLICT);
        }
        return detail(order);
    }

    private OrderResponse detail(Order order) {
        var items = orderItemRepository.findByOrderId(order.getId()).stream().map(orderMapper::item).toList();
        return orderMapper.detail(order, items);
    }

    @Transactional(readOnly = true)
    public PageResult<OrderResponse> listMyOrders(Long memberId, int page, int size) {
        if(page<0||size<1||size>100)throw BusinessException.badRequest("頁碼不可為負數，分頁大小須為 1 至 100");
        var result = orderRepository.findByMemberIdOrderByCreatedAtDesc(memberId, PageRequest.of(page, size));
        return PageResult.of(result, orderMapper::summary);
    }

    @Transactional(readOnly = true)
    public OrderResponse getMyOrder(Long memberId, Long orderId) {
        Order order = findOwned(memberId, orderId);
        List<OrderItemResponse> items = orderItemRepository.findByOrderId(order.getId())
                .stream().map(orderMapper::item).toList();
        return orderMapper.detail(order, items);
    }

    @Transactional
    public OrderResponse cancelMyOrder(Long memberId, Long orderId) {
        Order order = findOwned(memberId, orderId);
        if (!"PENDING".equals(order.getStatus()) && !"PAID".equals(order.getStatus())) {
            throw BusinessException.badRequest("僅能取消尚未出貨的訂單");
        }
        // restore stock
        for (OrderItem oi : orderItemRepository.findByOrderId(order.getId())) {
            if (oi.getSku() != null) {
                ProductSku sku = oi.getSku();
                int beforeStock = sku.getStock();
                sku.setStock(sku.getStock() + oi.getQuantity());
                skuRepository.save(sku);
                stockMovements.record(sku, beforeStock, "ORDER_CANCELLED", memberId, order);
            }
        }
        order.setStatus("CANCELLED");
        orderRepository.save(order);
        List<OrderItemResponse> items = orderItemRepository.findByOrderId(order.getId())
                .stream().map(orderMapper::item).toList();
        return orderMapper.detail(order, items);
    }

    private Order findOwned(Long memberId, Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> BusinessException.notFound("找不到訂單"));
        if (!order.getMember().getId().equals(memberId)) {
            throw BusinessException.notFound("找不到訂單");
        }
        return order;
    }

    private String generateOrderNo() {
        return "CS" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 30);
    }
}
