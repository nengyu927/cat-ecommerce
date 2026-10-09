package com.catshop.repository;

import com.catshop.entity.ProductImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {
    interface PrimaryImageRow {
        Long getProductId();
        String getUrl();
    }

    @org.springframework.data.jpa.repository.Query("""
            select i.product.id as productId, i.url as url from ProductImage i
            where i.product.id in :ids and i.isPrimary = true
            order by i.product.id, i.sortOrder, i.id
            """)
    List<PrimaryImageRow> findPrimaryImages(@org.springframework.data.repository.query.Param("ids") java.util.Collection<Long> ids);

    List<ProductImage> findByProductIdOrderBySortOrderAscIdAsc(Long productId);
}
