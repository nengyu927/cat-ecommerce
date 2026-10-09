package com.catshop.mapper;

import com.catshop.dto.category.CategoryResponse;
import com.catshop.dto.product.*;
import com.catshop.entity.*;
import org.mapstruct.*;
import java.util.List;

@Mapper(config = DtoMapperConfig.class)
public interface CatalogMapper {
    ProductImageResponse image(ProductImage image);
    ProductSkuResponse sku(ProductSku sku);

    @Mapping(target = "parentId", source = "parent.id")
    @Mapping(target = "children", ignore = true)
    CategoryResponse category(Category category);

    @Mapping(target = "parentId", source = "category.parent.id")
    @Mapping(target = "children", source = "children")
    CategoryResponse categoryTree(Category category, List<CategoryResponse> children);

    @Mapping(target = "categoryId", source = "product.category.id")
    @Mapping(target = "categoryName", source = "product.category.name")
    @Mapping(target = "primaryImageUrl", source = "primaryImageUrl")
    @Mapping(target = "description", ignore = true)
    @Mapping(target = "ingredients", ignore = true)
    @Mapping(target = "suitableAge", ignore = true)
    @Mapping(target = "origin", ignore = true)
    @Mapping(target = "storageInstructions", ignore = true)
    @Mapping(target = "images", ignore = true)
    @Mapping(target = "skus", ignore = true)
    ProductResponse summary(Product product, String primaryImageUrl);

    @Mapping(target = "categoryId", source = "product.category.id")
    @Mapping(target = "categoryName", source = "product.category.name")
    @Mapping(target = "primaryImageUrl", expression = "java(primaryImage(images))")
    @Mapping(target = "images", source = "images")
    @Mapping(target = "skus", source = "skus")
    ProductResponse detail(Product product, List<ProductImageResponse> images, List<ProductSkuResponse> skus);

    default String primaryImage(List<ProductImageResponse> images) {
        if (images == null || images.isEmpty()) return null;
        return images.stream().filter(i -> Boolean.TRUE.equals(i.isPrimary())).map(ProductImageResponse::url)
                .findFirst().orElse(images.get(0).url());
    }
}
