package com.catshop.service;

import com.catshop.exception.BusinessException;
import org.springframework.stereotype.Service;

@Service
public class MockPaymentService implements PaymentService {
    @Override
    public String initialOrderStatus(String paymentMethod) {
        if ("COD".equals(paymentMethod)) return "PENDING";
        if ("MOCK".equals(paymentMethod)) return "PAID";
        throw BusinessException.badRequest("不支援的付款方式");
    }
}
