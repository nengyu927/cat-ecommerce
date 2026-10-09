package com.catshop.service;

import com.catshop.dto.category.CategoryResponse;
import com.catshop.entity.Category;
import com.catshop.exception.BusinessException;
import com.catshop.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final com.catshop.mapper.CatalogMapper catalogMapper;

    private final CategoryRepository categoryRepository;

    @Transactional(readOnly = true)
    public List<CategoryResponse> listAll() {
        return categoryRepository.findByIsActiveTrueOrderBySortOrderAscIdAsc()
                .stream().map(catalogMapper::category).toList();
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> tree() {
        List<Category> roots = categoryRepository.findByParentIsNullAndIsActiveTrueOrderBySortOrderAscIdAsc();
        return roots.stream().map(this::toTree).toList();
    }

    @Transactional(readOnly = true)
    public CategoryResponse getBySlug(String slug) {
        Category c = categoryRepository.findBySlug(slug)
                .orElseThrow(() -> BusinessException.notFound("找不到分類"));
        if (!Boolean.TRUE.equals(c.getIsActive())) throw BusinessException.notFound("找不到分類");
        return catalogMapper.category(c);
    }

    private CategoryResponse toTree(Category c) {
        List<CategoryResponse> children = c.getChildren().stream()
                .filter(ch -> Boolean.TRUE.equals(ch.getIsActive()))
                .sorted(java.util.Comparator.comparing(Category::getSortOrder).thenComparing(Category::getId))
                .map(this::toTree)
                .toList();
        return catalogMapper.categoryTree(c, children);
    }

    @Transactional(readOnly = true)
    public CategoryResponse getById(Long id) {
        Category c = categoryRepository.findById(id).filter(item -> Boolean.TRUE.equals(item.getIsActive()))
                .orElseThrow(() -> BusinessException.notFound("找不到分類"));
        return catalogMapper.category(c);
    }
}
