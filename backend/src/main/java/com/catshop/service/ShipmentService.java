package com.catshop.service;

import com.catshop.dto.admin.order.CreateShipmentRequest;
import com.catshop.dto.order.ShipmentResponse;
import com.catshop.entity.OrderShipment;
import com.catshop.exception.BusinessException;
import com.catshop.repository.OrderRepository;
import com.catshop.repository.OrderShipmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

@Service @RequiredArgsConstructor
public class ShipmentService {
    private final OrderRepository orders;
    private final OrderShipmentRepository shipments;

    @Transactional(readOnly=true)
    public ShipmentResponse get(Long orderId, Long ownerId) {
        var order = orders.findById(orderId).orElseThrow(() -> BusinessException.notFound("找不到訂單"));
        if (ownerId != null && !ownerId.equals(order.getMember().getId()))
            throw BusinessException.notFound("找不到訂單");
        return shipments.findByOrderId(orderId).map(this::response).orElse(null);
    }

    @Transactional
    public ShipmentResponse ship(Long orderId, Long actorId, CreateShipmentRequest request) {
        String carrier = text(request.carrier(),80);
        String tracking = text(request.trackingNumber(),100);
        var order = orders.findById(orderId).orElseThrow(() -> BusinessException.notFound("找不到訂單"));
        if (!("PAID".equals(order.getStatus()) ||
              ("PENDING".equals(order.getStatus()) && "COD".equals(order.getPaymentMethod()))))
            throw BusinessException.conflict("訂單狀態已變更或不可出貨，請重新載入");
        if (shipments.findByOrderId(orderId).isPresent()) throw BusinessException.conflict("訂單已有出貨紀錄");
        order.setStatus("SHIPPED");
        // Flush the optimistic order version first: concurrent cancellation/shipment cannot both commit.
        orders.saveAndFlush(order);
        return response(shipments.saveAndFlush(OrderShipment.builder().orderId(orderId)
            .carrier(carrier).trackingNumber(tracking).actorMemberId(actorId)
            .shippedAt(LocalDateTime.now()).build()));
    }

    private String text(String value,int max) {
        if(value == null || value.strip().isEmpty() || value.length()>max || value.codePoints().anyMatch(Character::isISOControl))
            throw BusinessException.badRequest("物流公司與單號須填寫有效文字，不可含控制字元");
        return value.strip();
    }
    private ShipmentResponse response(OrderShipment shipment) {
        return new ShipmentResponse(shipment.getCarrier(),shipment.getTrackingNumber(),shipment.getShippedAt());
    }
}
