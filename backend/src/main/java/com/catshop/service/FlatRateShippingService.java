package com.catshop.service;

import java.math.BigDecimal;
import org.springframework.stereotype.Service;

@Service
public class FlatRateShippingService implements ShippingService {
    @Override
    public BigDecimal quote(BigDecimal goodsTotal) {
        return goodsTotal.compareTo(new BigDecimal("1000")) >= 0 ? BigDecimal.ZERO : new BigDecimal("80");
    }
}
