package com.catshop.mapper;

import com.catshop.dto.order.*;
import com.catshop.entity.*;
import org.mapstruct.*;
import java.util.List;

@Mapper(config = DtoMapperConfig.class)
public interface OrderMapper {
    // Product name, spec and money are copied from the snapshot, never from the current SKU.
    @Mapping(target = "skuId", source = "sku.id")
    OrderItemResponse item(OrderItem item);

    @Mapping(target = "memberId", source = "member.id")
    @Mapping(target = "items", ignore = true)
    OrderResponse summary(Order order);

    @Mapping(target = "memberId", source = "order.member.id")
    @Mapping(target = "items", source = "items")
    OrderResponse detail(Order order, List<OrderItemResponse> items);
}
