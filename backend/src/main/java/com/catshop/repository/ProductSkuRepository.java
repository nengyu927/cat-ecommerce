package com.catshop.repository;

import com.catshop.entity.ProductSku;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductSkuRepository extends JpaRepository<ProductSku, Long> {
    interface LowStockCounts {
        Long getLowStockCount();
        Long getOutOfStockCount();
    }

    @org.springframework.data.jpa.repository.Query("""
        select s from ProductSku s where s.isActive=true and s.product.isActive=true
        and s.stock <= s.safetyStock and (:outOnly=false or s.stock=0)
        and (:term is null or lower(s.product.name) like concat('%',:term,'%') escape '!'
             or lower(s.skuCode) like concat('%',:term,'%') escape '!')
        """)
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths={"product","product.category"})
    org.springframework.data.domain.Page<ProductSku> lowStock(
        @org.springframework.data.repository.query.Param("term") String term,
        @org.springframework.data.repository.query.Param("outOnly") boolean outOnly,
        org.springframework.data.domain.Pageable pageable);

    @org.springframework.data.jpa.repository.Query("""
        select count(s.id) as lowStockCount, sum(case when s.stock=0 then 1 else 0 end) as outOfStockCount
        from ProductSku s where s.isActive=true and s.product.isActive=true and s.stock <= s.safetyStock
        and (:term is null or lower(s.product.name) like concat('%',:term,'%') escape '!'
             or lower(s.skuCode) like concat('%',:term,'%') escape '!')
        """)
    LowStockCounts lowStockCounts(@org.springframework.data.repository.query.Param("term") String term);
    interface StockSummary {
        Long getProductId();
        Long getSkuCount();
        Long getTotalStock();
    }

    @org.springframework.data.jpa.repository.Query("""
            select s.product.id as productId, count(s.id) as skuCount, sum(coalesce(s.stock, 0)) as totalStock
            from ProductSku s where s.product.id in :ids group by s.product.id
            """)
    List<StockSummary> summarizeStock(@org.springframework.data.repository.query.Param("ids") java.util.Collection<Long> ids);

    List<ProductSku> findByProductIdAndIsActiveTrue(Long productId);
    Optional<ProductSku> findBySkuCode(String skuCode);

    // ---- admin ----
    List<ProductSku> findByProductIdOrderByIdAsc(Long productId);
    long countByProductId(Long productId);
    boolean existsBySkuCode(String skuCode);
}
