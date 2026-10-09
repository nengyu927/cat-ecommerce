package com.catshop.mapper;

import com.catshop.dto.cart.CartItemResponse;
import com.catshop.entity.CartItem;
import org.mapstruct.*;

@Mapper(config = DtoMapperConfig.class)
public interface CartMapper {
    @Mapping(target = "skuId", source = "item.sku.id")
    @Mapping(target = "skuCode", source = "item.sku.skuCode")
    @Mapping(target = "specName", source = "item.sku.specName")
    @Mapping(target = "productId", source = "item.sku.product.id")
    @Mapping(target = "productName", source = "item.sku.product.name")
    @Mapping(target = "stock", source = "item.sku.stock")
    @Mapping(target = "imageUrl", source = "imageUrl")
    @Mapping(target = "subtotal", expression = "java(item.getUnitPrice().multiply(java.math.BigDecimal.valueOf(item.getQuantity())))")
    CartItemResponse item(CartItem item, String imageUrl);
}
