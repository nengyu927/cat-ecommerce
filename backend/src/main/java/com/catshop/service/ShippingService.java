package com.catshop.service;

import java.math.BigDecimal;

public interface ShippingService {
    BigDecimal quote(BigDecimal goodsTotal);
}
