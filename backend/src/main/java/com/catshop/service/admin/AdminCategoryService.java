package com.catshop.service.admin;

import com.catshop.common.PageResult;
import com.catshop.dto.admin.category.AdminCategoryResponse;
import com.catshop.dto.admin.category.CreateCategoryRequest;
import com.catshop.dto.admin.category.UpdateCategoryRequest;
import com.catshop.entity.Category;
import com.catshop.exception.BusinessException;
import com.catshop.repository.CategoryRepository;
import com.catshop.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;

/**
 * Back-office category management (AC1-AC4).
 * All methods are only reachable through /api/admin/** which is guarded by hasRole('ADMIN').
 */
@Service
@RequiredArgsConstructor
public class AdminCategoryService {

    private final com.catshop.mapper.AdminMapper adminMapper;

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    /** AC1 — list categories (includes inactive), optional keyword / isActive filter, paginated. */
    @Transactional(readOnly = true)
    public PageResult<AdminCategoryResponse> list(String keyword, Boolean isActive, int page, int size) {
        com.catshop.common.AdminListParameters.paging(page, size);
        com.catshop.common.AdminListParameters.text(keyword, 200, "關鍵字");
        String pattern = null;
        if (keyword != null && !keyword.isBlank()) {
            // Preserve literal contains semantics, including SQL Server's bracket pattern syntax.
            var literal = keyword.trim().toLowerCase(Locale.ROOT).replace("!", "!!")
                    .replace("%", "!%").replace("_", "!_").replace("[", "![");
            pattern = "%" + literal + "%";
        }
        var result = categoryRepository.adminSearch(pattern, isActive,
                PageRequest.of(page, size, Sort.by("sortOrder").ascending().and(Sort.by("id"))));
        Map<Long, Long> counts = new HashMap<>();
        for (var batch : com.catshop.common.IdBatches.of(result.getContent().stream().map(Category::getId).toList()))
            for (var row : productRepository.countActiveProductsByCategory(batch))
                counts.put(row.getCategoryId(), row.getProductCount());
        return PageResult.of(result, c -> adminMapper.category(c, counts.getOrDefault(c.getId(), 0L)));
    }

    /** AC2 — create category. */
    @Transactional
    public AdminCategoryResponse create(CreateCategoryRequest req) {
        lockHierarchy();
        if (categoryRepository.existsBySlug(req.slug())) {
            throw new BusinessException("SLUG_DUPLICATED", "分類 slug 已存在", org.springframework.http.HttpStatus.CONFLICT);
        }
        Category parent = resolveParent(req.parentId());
        validateParent(null, parent);
        Category category = Category.builder()
                .name(req.name())
                .slug(req.slug())
                .parent(parent)
                .sortOrder(req.sortOrder() != null ? req.sortOrder() : 0)
                .isActive(req.isActive() != null ? req.isActive() : true)
                .build();
        categoryRepository.save(category);
        return adminMapper.category(category, 0L);
    }

    /** AC3 — update category. */
    @Transactional
    public AdminCategoryResponse update(Long id, UpdateCategoryRequest req) {
        lockHierarchy();
        Category category = find(id);
        if (categoryRepository.existsBySlugAndIdNot(req.slug(), id)) {
            throw new BusinessException("SLUG_DUPLICATED", "分類 slug 已存在", org.springframework.http.HttpStatus.CONFLICT);
        }
        if (req.parentId() != null && req.parentId().equals(id)) {
            throw new BusinessException("CATEGORY_HAS_CHILDREN", "分類不可將自己設為上層", org.springframework.http.HttpStatus.CONFLICT);
        }
        Category parent = resolveParent(req.parentId());
        validateParent(id, parent);
        category.setName(req.name());
        category.setSlug(req.slug());
        category.setParent(parent);
        if (req.sortOrder() != null) {
            category.setSortOrder(req.sortOrder());
        }
        if (req.isActive() != null) {
            category.setIsActive(req.isActive());
        }
        categoryRepository.save(category);
        return adminMapper.category(category,
                productRepository.countByCategoryIdAndIsActiveTrue(category.getId()));
    }

    /** AC4 — soft delete (is_active = false). */
    @Transactional
    public void deactivate(Long id) {
        lockHierarchy();
        Category category = find(id);
        if (categoryRepository.countByParentId(id) > 0) {
            throw new BusinessException("CATEGORY_HAS_CHILDREN", "分類仍有子分類，無法停用", org.springframework.http.HttpStatus.CONFLICT);
        }
        if (productRepository.countByCategoryIdAndIsActiveTrue(id) > 0) {
            throw new BusinessException("CATEGORY_HAS_ACTIVE_PRODUCTS", "分類仍有上架商品，無法停用", org.springframework.http.HttpStatus.CONFLICT);
        }
        category.setIsActive(false);
        categoryRepository.save(category);
    }

    private void lockHierarchy() {
        // Acquire before reading any category. This serializes competing reparent operations
        // across application instances, not just within one JVM. Empty catalogs cannot cycle.
        categoryRepository.findFirstByOrderByIdAsc();
    }

    private void validateParent(Long categoryId, Category parent) {
        var visited = new HashSet<Long>();
        for (Category ancestor = parent; ancestor != null; ancestor = ancestor.getParent()) {
            if (ancestor.getId().equals(categoryId)) {
                throw new BusinessException("CATEGORY_HAS_CHILDREN", "不可將子分類或孫分類設為上層分類",
                        org.springframework.http.HttpStatus.CONFLICT);
            }
            if (!visited.add(ancestor.getId())) {
                throw new BusinessException("CATEGORY_HIERARCHY_INVALID", "上層分類已有循環，請先修正分類階層",
                        org.springframework.http.HttpStatus.CONFLICT);
            }
        }
    }

    private Category find(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new BusinessException("CATEGORY_NOT_FOUND", "找不到分類", org.springframework.http.HttpStatus.NOT_FOUND));
    }

    private Category resolveParent(Long parentId) {
        if (parentId == null) {
            return null;
        }
        return categoryRepository.findById(parentId)
                .orElseThrow(() -> new BusinessException("PARENT_NOT_FOUND", "找不到上層分類", org.springframework.http.HttpStatus.NOT_FOUND));
    }
}
