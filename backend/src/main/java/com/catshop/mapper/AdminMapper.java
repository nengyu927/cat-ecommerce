package com.catshop.mapper;

import com.catshop.dto.admin.category.AdminCategoryResponse;
import com.catshop.dto.admin.product.*;
import com.catshop.dto.admin.order.*;
import com.catshop.dto.product.ProductImageResponse;
import com.catshop.dto.order.OrderItemResponse;
import com.catshop.entity.*;
import org.mapstruct.*;
import java.util.List;

@Mapper(config = DtoMapperConfig.class)
public interface AdminMapper {
    AdminSkuResponse sku(ProductSku sku);

    @Mapping(target = "parentId", source = "category.parent.id")
    @Mapping(target = "parentName", source = "category.parent.name")
    @Mapping(target = "productCount", source = "productCount")
    AdminCategoryResponse category(Category category, long productCount);

    @Mapping(target = "categoryId", source = "product.category.id")
    @Mapping(target = "categoryName", source = "product.category.name")
    @Mapping(target = "skuCount", source = "skuCount")
    @Mapping(target = "totalStock", source = "totalStock")
    @Mapping(target = "primaryImageUrl", source = "primaryImageUrl")
    AdminProductSummaryResponse productSummary(Product product, int skuCount, int totalStock, String primaryImageUrl);

    @Mapping(target = "categoryId", source = "product.category.id")
    @Mapping(target = "categoryName", source = "product.category.name")
    @Mapping(target = "images", source = "images")
    @Mapping(target = "skus", source = "skus")
    AdminProductDetailResponse productDetail(Product product, List<ProductImageResponse> images, List<AdminSkuResponse> skus);

    @Mapping(target = "memberId", source = "order.member.id")
    @Mapping(target = "memberName", source = "order.member.name")
    @Mapping(target = "memberEmail", source = "order.member.email")
    @Mapping(target = "itemCount", source = "itemCount")
    AdminOrderSummaryResponse orderSummary(Order order, int itemCount);

    @Mapping(target = "memberId", source = "order.member.id")
    @Mapping(target = "memberName", source = "order.member.name")
    @Mapping(target = "memberEmail", source = "order.member.email")
    @Mapping(target = "items", source = "items")
    AdminOrderDetailResponse orderDetail(Order order, List<OrderItemResponse> items);
}
