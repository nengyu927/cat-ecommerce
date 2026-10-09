package com.catshop.repository;

import com.catshop.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "parent")
    @org.springframework.data.jpa.repository.Query("""
            select c from Category c
            where (:isActive is null or c.isActive = :isActive)
              and (:pattern is null or lower(c.name) like :pattern escape '!'
                   or lower(c.slug) like :pattern escape '!')
            """)
    org.springframework.data.domain.Page<Category> adminSearch(
            @org.springframework.data.repository.query.Param("pattern") String pattern,
            @org.springframework.data.repository.query.Param("isActive") Boolean isActive,
            org.springframework.data.domain.Pageable pageable);

    Optional<Category> findBySlug(String slug);
    List<Category> findByIsActiveTrueOrderBySortOrderAscIdAsc();
    List<Category> findByParentIsNullAndIsActiveTrueOrderBySortOrderAscIdAsc();

    // ---- admin ----
    // Categories are soft-deleted only: the oldest row is a stable hierarchy-write mutex.
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    Optional<Category> findFirstByOrderByIdAsc();

    List<Category> findAllByOrderBySortOrderAscIdAsc();
    List<Category> findByParentId(Long parentId);
    long countByParentId(Long parentId);
    boolean existsBySlug(String slug);
    boolean existsBySlugAndIdNot(String slug, Long id);
}
