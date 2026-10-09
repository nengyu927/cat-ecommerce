package com.catshop.service;
import com.catshop.entity.*;
import com.catshop.repository.StockMovementRepository;
import com.catshop.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service @RequiredArgsConstructor
public class StockMovementService {
 private final StockMovementRepository movements;
 @Transactional(propagation=Propagation.MANDATORY)
 public void record(ProductSku sku,int before,String reason,Long actor,Order order) {
  int after=sku.getStock(); if(before==after) return;
  movements.save(StockMovement.builder().skuId(sku.getId()).productId(sku.getProduct().getId())
   .productName(sku.getProduct().getName()).skuCode(sku.getSkuCode()).reason(reason)
   .actorMemberId(actor==null?SecurityUtils.currentMemberId():actor).beforeStock(before).afterStock(after).quantityDelta(after-before)
   .orderId(order==null?null:order.getId()).orderNo(order==null?null:order.getOrderNo()).build());
 }
}
