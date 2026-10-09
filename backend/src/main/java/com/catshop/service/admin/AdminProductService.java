package com.catshop.service.admin;

import com.catshop.common.PageResult;
import com.catshop.dto.admin.product.*;
import com.catshop.dto.product.ProductImageResponse;
import com.catshop.entity.Category;
import com.catshop.entity.Product;
import com.catshop.entity.ProductImage;
import com.catshop.entity.ProductSku;
import com.catshop.exception.BusinessException;
import com.catshop.repository.CategoryRepository;
import com.catshop.repository.ProductImageRepository;
import com.catshop.repository.ProductRepository;
import com.catshop.repository.ProductSkuRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/**
 * Back-office product management (AP1-AP7).
 * Reachable only through /api/admin/** guarded by hasRole('ADMIN').
 */
@Service
@RequiredArgsConstructor
public class AdminProductService {
    private final com.catshop.service.StockMovementService stockMovements;
    private final com.catshop.mapper.CatalogMapper catalogMapper;

    private final com.catshop.mapper.AdminMapper adminMapper;
    private final com.catshop.service.CatalogSummaryLoader summaries;

    private static final Set<String> VALID_STATUS = Set.of("ON_SALE", "OFF_SHELF", "SOLD_OUT");

    private final ProductRepository productRepository;
    private final ProductImageRepository imageRepository;
    private final ProductSkuRepository skuRepository;
    private final CategoryRepository categoryRepository;

    /** AP1 — admin product list (includes off-shelf / inactive). */
    @Transactional(readOnly = true)
    public PageResult<AdminProductSummaryResponse> list(Long categoryId, String keyword, String status,
                                                        Boolean isActive, int page, int size, String sort) {
        return list(categoryId, keyword, status, isActive, null, page, size, sort);
    }

    @Transactional(readOnly = true)
    public PageResult<AdminProductSummaryResponse> list(Long categoryId, String keyword, String status,
                                                        Boolean isActive, String brand, int page, int size, String sort) {
        com.catshop.common.AdminListParameters.paging(page, size);
        com.catshop.common.AdminListParameters.text(keyword, 200, "關鍵字");
        com.catshop.common.AdminListParameters.text(brand, 100, "品牌");
        if (categoryId != null && categoryId < 1) throw BusinessException.badRequest("分類編號須為正整數");
        if (status != null && !status.isBlank() && !VALID_STATUS.contains(status.trim().toUpperCase(java.util.Locale.ROOT)))
            throw BusinessException.badRequest("不支援此商品狀態");
        String brandName = (brand == null || brand.isBlank()) ? null : brand.trim().toLowerCase(java.util.Locale.ROOT);
        if (brandName != null && brandName.length() > 100) throw BusinessException.badRequest("品牌不可超過 100 字");
        String kw = (keyword == null || keyword.isBlank()) ? null : keyword.trim();
        String st = (status == null || status.isBlank()) ? null : status.trim().toUpperCase(java.util.Locale.ROOT);
        Page<Product> result = productRepository.adminSearch(categoryId, kw, st, isActive, brandName,
                PageRequest.of(page, size, parseSort(sort)));
        var ids = result.getContent().stream().map(Product::getId).toList();
        var images = summaries.primaryImages(ids);
        var stocks = summaries.stock(ids);
        return PageResult.of(result, p -> {
            var stock = stocks.getOrDefault(p.getId(), new com.catshop.service.CatalogSummaryLoader.Stock(0, 0));
            return adminMapper.productSummary(p, stock.skuCount(), stock.totalStock(), images.get(p.getId()));
        });
    }

    /** AP2 — admin product detail (all SKUs incl. inactive, all images). */
    @Transactional(readOnly = true)
    public AdminProductDetailResponse detail(Long id) {
        Product p = find(id);
        return toDetail(p);
    }

    /** AP3 — create product with optional SKUs and images. */
    @Transactional
    public AdminProductDetailResponse create(CreateProductRequest req) {
        if (productRepository.existsBySlug(req.slug())) {
            throw new BusinessException("SLUG_DUPLICATED", "商品 slug 已存在", HttpStatus.CONFLICT);
        }
        Category category = findCategory(req.categoryId());
        String status = normalizeStatus(req.status());

        Product product = Product.builder()
                .category(category)
                .name(req.name())
                .slug(req.slug())
                .description(req.description())
                .ingredients(req.ingredients())
                .suitableAge(req.suitableAge())
                .origin(req.origin())
                .storageInstructions(req.storageInstructions())
                .brand(req.brand())
                .basePrice(req.basePrice())
                .status(status)
                .isActive(req.isActive() != null ? req.isActive() : true)
                .build();
        productRepository.save(product);

        if (req.images() != null) {
            for (CreateProductRequest.ImageItem img : req.images()) {
                imageRepository.save(ProductImage.builder()
                        .product(product)
                        .url(img.url())
                        .altText(img.altText())
                        .sortOrder(img.sortOrder() != null ? img.sortOrder() : 0)
                        .isPrimary(img.isPrimary() != null ? img.isPrimary() : false)
                        .build());
            }
        }
        if (req.skus() != null) {
            for (CreateProductRequest.SkuItem sku : req.skus()) {
                if (skuRepository.existsBySkuCode(sku.skuCode())) {
                    throw new BusinessException("SKU_CODE_DUPLICATED", "SKU 編號已存在：" + sku.skuCode(), HttpStatus.CONFLICT);
                }
                var createdSku = skuRepository.save(ProductSku.builder()
                        .product(product)
                        .skuCode(sku.skuCode())
                        .specName(sku.specName())
                        .price(sku.price())
                        .stock(sku.stock() != null ? sku.stock() : 0)
                        .safetyStock(sku.safetyStock() != null ? sku.safetyStock() : 0)
                        .isActive(true)
                        .build());
                stockMovements.record(createdSku,0,"SKU_CREATED",null,null);
            }
        }
        return toDetail(product);
    }

    /** AP4 — update product main record (SKUs handled by AP6/AP7). */
    @Transactional
    public AdminProductDetailResponse update(Long id, UpdateProductRequest req) {
        Product product = findForUpdate(id);
        if (productRepository.existsBySlugAndIdNot(req.slug(), id)) {
            throw new BusinessException("SLUG_DUPLICATED", "商品 slug 已存在", HttpStatus.CONFLICT);
        }
        Category category = findCategory(req.categoryId());
        product.setName(req.name());
        product.setSlug(req.slug());
        product.setDescription(req.description());
        product.setIngredients(req.ingredients());
        product.setSuitableAge(req.suitableAge());
        product.setOrigin(req.origin());
        product.setStorageInstructions(req.storageInstructions());
        product.setCategory(category);
        product.setBrand(req.brand());
        product.setBasePrice(req.basePrice());
        if (req.status() != null) {
            product.setStatus(normalizeStatus(req.status()));
        }
        if (req.isActive() != null) {
            product.setIsActive(req.isActive());
        }
        productRepository.save(product);
        if (req.images() != null) {
            imageRepository.deleteAll(imageRepository.findByProductIdOrderBySortOrderAscIdAsc(id));
            imageRepository.flush();
            for (CreateProductRequest.ImageItem img : req.images()) {
                imageRepository.save(ProductImage.builder().product(product).url(img.url()).altText(img.altText())
                        .sortOrder(img.sortOrder() == null ? 0 : img.sortOrder())
                        .isPrimary(Boolean.TRUE.equals(img.isPrimary())).build());
            }
        }
        return toDetail(product);
    }

    /** Update only image associations; a stale image snapshot cannot replace a newer set. */
    @Transactional
    public List<ProductImageResponse> updateImages(Long id, UpdateProductImagesRequest req) {
        Product product = findForUpdate(id);
        var existing = imageRepository.findByProductIdOrderBySortOrderAscIdAsc(id);
        if (!existing.stream().map(ProductImage::getId).toList().equals(req.expectedImageIds())) {
            throw BusinessException.conflict("商品圖片已變更，請重新載入後再編輯");
        }
        long primaryCount = req.images().stream().filter(img -> Boolean.TRUE.equals(img.isPrimary())).count();
        if (!req.images().isEmpty() && primaryCount != 1) {
            throw BusinessException.badRequest("圖片清單須選擇一張主圖");
        }
        imageRepository.deleteAll(existing);
        imageRepository.flush();
        for (int index = 0; index < req.images().size(); index++) {
            var img = req.images().get(index);
            imageRepository.save(ProductImage.builder().product(product).url(img.url())
                    .altText(img.altText()).sortOrder(index).isPrimary(Boolean.TRUE.equals(img.isPrimary())).build());
        }
        imageRepository.flush();
        return imageRepository.findByProductIdOrderBySortOrderAscIdAsc(id).stream().map(catalogMapper::image).toList();
    }

    /** AP5 — off-shelf (soft delete: status=OFF_SHELF, is_active=false). */
    @Transactional
    public void offShelf(Long id) {
        Product product = find(id);
        product.setStatus("OFF_SHELF");
        product.setIsActive(false);
        productRepository.save(product);
    }

    /** AP6 — add a SKU to a product. */
    @Transactional
    public AdminSkuResponse addSku(Long productId, CreateSkuRequest req) {
        Product product = find(productId);
        if (skuRepository.existsBySkuCode(req.skuCode())) {
            throw new BusinessException("SKU_CODE_DUPLICATED", "SKU 編號已存在：" + req.skuCode(), HttpStatus.CONFLICT);
        }
        ProductSku sku = ProductSku.builder()
                .product(product)
                .skuCode(req.skuCode())
                .specName(req.specName())
                .price(req.price())
                .stock(req.stock() != null ? req.stock() : 0)
                .safetyStock(req.safetyStock() != null ? req.safetyStock() : 0)
                .isActive(true)
                .build();
        skuRepository.save(sku);
        stockMovements.record(sku,0,"SKU_CREATED",null,null);
        return adminMapper.sku(sku);
    }

    /** AP7 — update a SKU (price / stock / safety stock / active). */
    @Transactional
    public AdminSkuResponse updateSku(Long skuId, UpdateSkuRequest req) {
        ProductSku sku = skuRepository.findById(skuId)
                .orElseThrow(() -> new BusinessException("SKU_NOT_FOUND", "找不到 SKU", HttpStatus.NOT_FOUND));
        int beforeStock = sku.getStock();
        if (!java.util.Objects.equals(req.version(), sku.getVersion())) {
            throw BusinessException.conflict("規格價格或庫存已變更，請重新載入後再編輯");
        }
        sku.setSpecName(req.specName());
        sku.setPrice(req.price());
        if (req.stock() != null) {
            sku.setStock(req.stock());
        }
        if (req.safetyStock() != null) {
            sku.setSafetyStock(req.safetyStock());
        }
        if (req.isActive() != null) {
            sku.setIsActive(req.isActive());
        }
        skuRepository.saveAndFlush(sku);
        stockMovements.record(sku,beforeStock,"ADMIN_ADJUSTMENT",null,null);
        return adminMapper.sku(sku);
    }

    // ---- helpers ----

    private AdminProductDetailResponse toDetail(Product p) {
        List<ProductImageResponse> images = imageRepository
                .findByProductIdOrderBySortOrderAscIdAsc(p.getId())
                .stream().map(catalogMapper::image).toList();
        List<AdminSkuResponse> skus = skuRepository
                .findByProductIdOrderByIdAsc(p.getId())
                .stream().map(adminMapper::sku).toList();
        return adminMapper.productDetail(p, images, skus);
    }


    private Product find(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new BusinessException("PRODUCT_NOT_FOUND", "找不到商品", HttpStatus.NOT_FOUND));
    }

    private Product findForUpdate(Long id) {
        return productRepository.findForUpdate(id)
                .orElseThrow(() -> new BusinessException("PRODUCT_NOT_FOUND", "找不到商品", HttpStatus.NOT_FOUND));
    }

    private Category findCategory(Long categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new BusinessException("CATEGORY_NOT_FOUND", "找不到分類", HttpStatus.NOT_FOUND));
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return "ON_SALE";
        }
        String s = status.trim().toUpperCase();
        if (!VALID_STATUS.contains(s)) {
            throw new BusinessException("VALIDATION_ERROR", "商品狀態不合法：" + status, HttpStatus.BAD_REQUEST);
        }
        return s;
    }

    private Sort parseSort(String sort) {
        return com.catshop.common.AdminListParameters.sort(sort, Set.of("createdAt", "name", "basePrice", "status", "id"));
    }
}
