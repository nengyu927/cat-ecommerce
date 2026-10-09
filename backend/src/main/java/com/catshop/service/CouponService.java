package com.catshop.service;
import com.catshop.entity.Coupon;
import com.catshop.repository.*;
import com.catshop.exception.BusinessException;
import com.catshop.dto.admin.CouponRequest;
import com.catshop.dto.admin.CouponResponse;
import com.catshop.dto.order.CouponQuoteResponse;
import com.catshop.common.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.Locale;

@Service @RequiredArgsConstructor
public class CouponService {
 private final CouponRepository coupons;
 private final OrderRepository orders;
 private final CartRepository carts;
 private final CartItemRepository items;
 private final ShippingService shipping;

 public record Redemption(Long id,String code,BigDecimal discount) {}
 private static BusinessException couponError(String code,String message) {
  return new BusinessException(code,message,org.springframework.http.HttpStatus.BAD_REQUEST);
 }
 // Must run inside checkout's transaction so the coupon lock survives until order commit.
 @Transactional(propagation=org.springframework.transaction.annotation.Propagation.MANDATORY)
 public Redemption redeem(Long memberId,String code,BigDecimal goods) {
  if(code==null || code.isBlank()) return new Redemption(null,null,BigDecimal.ZERO);
  String normalized=code.strip().toUpperCase(Locale.ROOT);
  if(!normalized.matches("[A-Z0-9_-]{3,32}")) throw couponError("COUPON_INVALID_FORMAT","優惠碼格式不正確");
  var c=coupons.lockByCode(normalized).orElseThrow(()->couponError("COUPON_NOT_FOUND","優惠碼不存在"));
  var now=LocalDateTime.now(Clock.systemUTC());
  if(!Boolean.TRUE.equals(c.getActive())) throw couponError("COUPON_INACTIVE","優惠券已停用");
  if(now.isBefore(c.getStartsAt())) throw couponError("COUPON_NOT_STARTED","優惠券尚未開始");
  if(!now.isBefore(c.getEndsAt())) throw couponError("COUPON_EXPIRED","優惠券已到期");
  if(goods.compareTo(c.getMinimumAmount())<0) throw couponError("COUPON_MINIMUM_NOT_MET","商品金額未達優惠券門檻");
  if(orders.countByCouponIdAndStatusNot(c.getId(),"CANCELLED")>=c.getMaxUses())
   throw couponError("COUPON_EXHAUSTED","優惠券名額已用完");
  if(orders.countByCouponIdAndMemberIdAndStatusNot(c.getId(),memberId,"CANCELLED")>0)
   throw couponError("COUPON_ALREADY_USED","每位會員同一張優惠券限用一次");
  return new Redemption(c.getId(),c.getCode(),goods.min(c.getDiscountAmount()));
 }
 @Transactional
 public CouponQuoteResponse quote(Long memberId,String code) {
  var cart=carts.lockByMemberId(memberId).orElseThrow(()->BusinessException.badRequest("購物車為空"));
  var rows=items.findByCartIdOrderByIdAsc(cart.getId());
  if(rows.isEmpty()) throw BusinessException.badRequest("購物車為空");
  BigDecimal goods=BigDecimal.ZERO;
  for(var row:rows) {
   var sku=row.getSku();
   if(!Boolean.TRUE.equals(sku.getIsActive()) || !Boolean.TRUE.equals(sku.getProduct().getIsActive()) || !"ON_SALE".equals(sku.getProduct().getStatus()) || row.getQuantity()>sku.getStock())
    throw BusinessException.badRequest("購物車包含無法購買的商品，請更新購物車");
   goods=goods.add(sku.getPrice().multiply(BigDecimal.valueOf(row.getQuantity())));
  }
  var redemption=redeem(memberId,code,goods);var fee=shipping.quote(goods);
  return new CouponQuoteResponse(redemption.code(),goods,redemption.discount(),fee,goods.subtract(redemption.discount()).add(fee),
          com.catshop.common.CheckoutCartSnapshot.of(rows),com.catshop.common.CheckoutCartSnapshot.prices(rows));
 }
 @Transactional(readOnly=true)
 public CouponResponse detail(Long id) {
  if(id==null || id<1) throw BusinessException.badRequest("優惠券編號須為正整數");
  return CouponResponse.of(coupons.findById(id).orElseThrow(()->BusinessException.notFound("找不到優惠券")));
 }
 @Transactional(readOnly=true)
 public PageResult<CouponResponse> list(int page,int size) {
  if(page<0 || size<1 || size>100) throw BusinessException.badRequest("分頁大小須為 1 到 100");
  return PageResult.of(coupons.findAll(PageRequest.of(page,size,Sort.by(Sort.Direction.DESC,"id"))),CouponResponse::of);
 }
 @Transactional
 public CouponResponse save(Long id,CouponRequest request) {
  if(!request.endsAt().isAfter(request.startsAt())) throw BusinessException.badRequest("結束時間須晚於開始時間");
  String code=request.code().toUpperCase(Locale.ROOT);
  Coupon c;
  if(id==null) {
   if(coupons.lockByCode(code).isPresent()) throw BusinessException.conflict("優惠碼已存在");
   c=new Coupon();c.setCode(code);
  } else {
   c=coupons.lockById(id).orElseThrow(()->BusinessException.notFound("找不到優惠券"));
   if(!c.getCode().equals(code)) throw BusinessException.badRequest("優惠碼建立後不可更名");
   if(request.version()==null || !request.version().equals(c.getVersion())) throw BusinessException.conflict("優惠券已變更，請重新載入");
  }
  c.setDiscountAmount(request.discountAmount());c.setMinimumAmount(request.minimumAmount());
  c.setStartsAt(request.startsAt());c.setEndsAt(request.endsAt());c.setMaxUses(request.maxUses());c.setActive(request.active());
  try {return CouponResponse.of(coupons.saveAndFlush(c));}
  catch(org.springframework.dao.DataIntegrityViolationException ex) {
   if(id==null) throw BusinessException.conflict("優惠碼已存在或資料不符合限制");
   throw ex;
  }
 }
}
