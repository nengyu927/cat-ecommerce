package com.catshop.dto.order;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;

public record CreateOrderRequest(
        @NotBlank(message = "收件人不可為空") @Size(max = 100) String receiverName,
        @NotBlank(message = "收件電話不可為空") @Size(max = 30) String receiverPhone,
        @NotBlank(message = "收件地址不可為空") @Size(max = 500) String shippingAddress,
        @Size(max = 500) String remark,
        @NotBlank(message = "請選擇付款方式")
        @Pattern(regexp = "COD|MOCK", message = "付款方式必須為 COD 或 MOCK") String paymentMethod,
        @Size(max=32) String couponCode,
        @Pattern(regexp = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}",
                message = "下單識別碼必須為 UUID") String requestId,
        @Size(max = 24000) String cartSnapshot,
        @jakarta.validation.Valid CheckoutAmounts expectedAmounts
) {
 public CreateOrderRequest(String name,String phone,String address,String remark,String paymentMethod,String couponCode,String requestId,String cartSnapshot) {
  this(name,phone,address,remark,paymentMethod,couponCode,requestId,cartSnapshot,null);
 }
 public CreateOrderRequest(String name,String phone,String address,String remark,String paymentMethod,String couponCode,String requestId) {
  this(name,phone,address,remark,paymentMethod,couponCode,requestId,null);
 }
 public CreateOrderRequest(String name,String phone,String address,String remark,String paymentMethod,String couponCode) {
  this(name,phone,address,remark,paymentMethod,couponCode,null);
 }
 public CreateOrderRequest(String name,String phone,String address,String remark,String paymentMethod) {
  this(name,phone,address,remark,paymentMethod,null);
 }
}
