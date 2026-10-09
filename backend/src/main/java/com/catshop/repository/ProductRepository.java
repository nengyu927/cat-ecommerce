package com.catshop.repository;

import com.catshop.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id = :id")
    Optional<Product> findForUpdate(@Param("id") Long id);

    interface CategoryProductCount {
        Long getCategoryId();
        Long getProductCount();
    }

    @Query("""
            select p.category.id as categoryId, count(p.id) as productCount
            from Product p where p.category.id in :ids and p.isActive = true
            group by p.category.id
            """)
    java.util.List<CategoryProductCount> countActiveProductsByCategory(
            @Param("ids") java.util.Collection<Long> ids);


    Optional<Product> findBySlug(String slug);

    @Query("""
            SELECT p FROM Product p
            WHERE p.isActive = true
              AND p.status = 'ON_SALE'
              AND p.category.isActive = true
              AND (:allCategories = true OR p.category.id IN :categoryIds)
              AND (:keyword IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%')))
              AND (:brand IS NULL OR LOWER(p.brand) = :brand)
              AND (:minPrice IS NULL OR p.basePrice >= :minPrice)
              AND (:maxPrice IS NULL OR p.basePrice <= :maxPrice)
              AND (:inStock = false OR EXISTS (
                  SELECT s.id FROM ProductSku s WHERE s.product = p AND s.isActive = true AND s.stock > 0
              ))
            """)
    @EntityGraph(attributePaths = "category")
    Page<Product> search(@Param("allCategories") boolean allCategories,
                         @Param("categoryIds") java.util.Collection<Long> categoryIds,
                         @Param("keyword") String keyword,
                         @Param("brand") String brand,
                         @Param("minPrice") java.math.BigDecimal minPrice,
                         @Param("maxPrice") java.math.BigDecimal maxPrice,
                         @Param("inStock") boolean inStock,
                         Pageable pageable);

    @Query("""
            select distinct p.brand from Product p
            where p.isActive = true and p.status = 'ON_SALE' and p.category.isActive = true
            and p.brand is not null and trim(p.brand) <> '' order by p.brand
            """)
    java.util.List<String> visibleBrands();

    // ---- admin (includes off-shelf / inactive) ----
    @Query("""
            SELECT p FROM Product p
            WHERE (:categoryId IS NULL OR p.category.id = :categoryId)
              AND (:keyword IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%')))
              AND (:status IS NULL OR p.status = :status)
              AND (:isActive IS NULL OR p.isActive = :isActive)
              AND (:brand IS NULL OR LOWER(p.brand) = :brand)
            """)
    @EntityGraph(attributePaths = "category")
    Page<Product> adminSearch(@Param("categoryId") Long categoryId,
                              @Param("keyword") String keyword,
                              @Param("status") String status,
                              @Param("isActive") Boolean isActive,
                              @Param("brand") String brand,
                              Pageable pageable);

    boolean existsBySlug(String slug);
    @EntityGraph(attributePaths = "category")
    java.util.List<Product> findTop4ByCategoryIdAndIsActiveTrueAndStatusAndIdNotOrderByCreatedAtDesc(
            Long categoryId, String status, Long id);
    boolean existsBySlugAndIdNot(String slug, Long id);
    long countByCategoryIdAndIsActiveTrue(Long categoryId);
}
