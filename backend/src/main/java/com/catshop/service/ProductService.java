package com.catshop.service;

import com.catshop.common.PageResult;
import com.catshop.dto.product.ProductImageResponse;
import com.catshop.dto.product.ProductResponse;
import com.catshop.dto.product.ProductSkuResponse;
import com.catshop.entity.Product;
import com.catshop.exception.BusinessException;
import com.catshop.repository.ProductImageRepository;
import com.catshop.repository.ProductRepository;
import com.catshop.repository.ProductSkuRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final com.catshop.mapper.CatalogMapper catalogMapper;
    private final CatalogSummaryLoader summaries;

    private final ProductRepository productRepository;
    private final ProductImageRepository imageRepository;
    private final ProductSkuRepository skuRepository;
    private final com.catshop.repository.CategoryRepository categoryRepository;

    @Transactional(readOnly = true)
    public PageResult<ProductResponse> search(Long categoryId, String keyword, int page, int size, String sort) {
        return search(categoryId, keyword, page, size, sort, null, null, null);
    }

    @Transactional(readOnly = true)
    public List<String> brands() { return productRepository.visibleBrands(); }

    @Transactional(readOnly = true)
    public PageResult<ProductResponse> search(Long categoryId, String keyword, int page, int size, String sort,
            String brand, java.math.BigDecimal minPrice, java.math.BigDecimal maxPrice) {
        return search(categoryId, keyword, page, size, sort, brand, minPrice, maxPrice, false);
    }

    @Transactional(readOnly = true)
    public PageResult<ProductResponse> search(Long categoryId, String keyword, int page, int size, String sort,
            String brand, java.math.BigDecimal minPrice, java.math.BigDecimal maxPrice, boolean inStock) {
        if (categoryId != null && categoryId <= 0) throw BusinessException.badRequest("分類編號須為正整數");
        if (keyword != null && keyword.length() > 200) throw BusinessException.badRequest("搜尋字詞不可超過 200 字");
        if (brand != null && brand.length() > 100) throw BusinessException.badRequest("品牌名稱不可超過 100 字");
        for (var price : new java.math.BigDecimal[]{minPrice, maxPrice}) {
            if (price != null && (price.signum() < 0 || price.compareTo(new java.math.BigDecimal("99999999.99")) > 0
                    || price.stripTrailingZeros().scale() > 2)) throw BusinessException.badRequest("價格須為 0 至 99999999.99，最多兩位小數");
        }
        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) throw BusinessException.badRequest("最低價格不可高於最高價格");
        if (page < 0 || size < 1 || size > 100) throw BusinessException.badRequest("分頁大小必須介於 1 至 100");
        String[] parts = sort == null ? new String[0] : sort.split(",", -1);
        if (parts.length != 2) throw BusinessException.badRequest("排序格式不合法");
        String field = "price".equals(parts[0]) ? "basePrice" : parts[0];
        if (!java.util.Set.of("basePrice", "createdAt", "name").contains(field)
                || parts.length != 2 || !java.util.Set.of("asc", "desc").contains(parts[1])) {
            throw BusinessException.badRequest("排序格式必須為 price、createdAt 或 name，搭配 asc 或 desc");
        }
        java.util.Set<Long> categoryIds = new java.util.HashSet<>();
        categoryIds.add(categoryId == null ? -1L : categoryId);
        if (categoryId != null) {
            List<com.catshop.entity.Category> active = categoryRepository.findByIsActiveTrueOrderBySortOrderAscIdAsc();
            boolean changed;
            do {
                changed = false;
                for (var category : active) {
                    if (category.getParent() != null && categoryIds.contains(category.getParent().getId())) {
                        changed |= categoryIds.add(category.getId());
                    }
                }
            } while (changed);
        }
        Page<Product> result = productRepository.search(
                categoryId == null, categoryIds,
                (keyword == null || keyword.isBlank()) ? null : keyword.trim(),
                (brand == null || brand.isBlank()) ? null : brand.trim().toLowerCase(java.util.Locale.ROOT),
                minPrice, maxPrice, inStock,
                PageRequest.of(page, size, Sort.by(Sort.Direction.fromString(parts[1]), field).and(Sort.by("id"))));
        var images = summaries.primaryImages(result.getContent().stream().map(Product::getId).toList());
        return PageResult.of(result, p -> catalogMapper.summary(p, images.get(p.getId())));
    }

    @Transactional(readOnly = true)
    public ProductResponse getById(Long id) {
        Product p = productRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("找不到商品"));
        requireVisible(p);
        return toDetail(p);
    }

    @Transactional(readOnly = true)
    public ProductResponse getBySlug(String slug) {
        Product p = productRepository.findBySlug(slug)
                .orElseThrow(() -> BusinessException.notFound("找不到商品"));
        requireVisible(p);
        return toDetail(p);
    }

    private ProductResponse toDetail(Product p) {
        List<ProductImageResponse> images = imageRepository
                .findByProductIdOrderBySortOrderAscIdAsc(p.getId())
                .stream().map(catalogMapper::image).toList();
        List<ProductSkuResponse> skus = skuRepository
                .findByProductIdAndIsActiveTrue(p.getId())
                .stream().map(catalogMapper::sku).toList();
        return catalogMapper.detail(p, images, skus);
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> related(Long id) {
        Product p = productRepository.findById(id).orElseThrow(() -> BusinessException.notFound("找不到商品"));
        requireVisible(p);
        var products = productRepository.findTop4ByCategoryIdAndIsActiveTrueAndStatusAndIdNotOrderByCreatedAtDesc(
                p.getCategory().getId(), "ON_SALE", id);
        var images = summaries.primaryImages(products.stream().map(Product::getId).toList());
        return products.stream().map(item -> catalogMapper.summary(item, images.get(item.getId()))).toList();
    }

    private void requireVisible(Product p) {
        if (!Boolean.TRUE.equals(p.getIsActive()) || !"ON_SALE".equals(p.getStatus())
                || !Boolean.TRUE.equals(p.getCategory().getIsActive())) {
            throw BusinessException.notFound("找不到商品");
        }
    }

}
