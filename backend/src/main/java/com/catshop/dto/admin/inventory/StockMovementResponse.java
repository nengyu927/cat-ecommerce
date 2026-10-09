package com.catshop.dto.admin.inventory;

import com.catshop.entity.StockMovement;
import java.time.LocalDateTime;

public record StockMovementResponse(Long id, Long skuId, Long productId, String productName, String skuCode,
 String reason, Long actorMemberId, int beforeStock, int afterStock, int quantityDelta,
 Long orderId, String orderNo, LocalDateTime createdAt) {
 public static StockMovementResponse of(StockMovement m) {
  return new StockMovementResponse(m.getId(),m.getSkuId(),m.getProductId(),m.getProductName(),m.getSkuCode(),
   m.getReason(),m.getActorMemberId(),m.getBeforeStock(),m.getAfterStock(),m.getQuantityDelta(),m.getOrderId(),m.getOrderNo(),m.getCreatedAt());
 }
}
