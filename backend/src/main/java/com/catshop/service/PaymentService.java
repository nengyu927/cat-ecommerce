package com.catshop.service;

/** MVP payment adapter. Never accepts or stores card data. */
public interface PaymentService {
    String initialOrderStatus(String paymentMethod);
}
