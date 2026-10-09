package com.catshop.repository;

import com.catshop.entity.WishlistItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface WishlistRepository extends JpaRepository<WishlistItem, Long> {
    @EntityGraph(attributePaths = {"product", "product.category"})
    Page<WishlistItem> findByMemberId(Long memberId, Pageable pageable);
    boolean existsByMemberIdAndProductId(Long memberId, Long productId);
    long countByMemberId(Long memberId);
    void deleteByMemberIdAndProductId(Long memberId, Long productId);
    @Query("select w.product.id from WishlistItem w where w.member.id = :memberId order by w.id desc")
    List<Long> productIds(@Param("memberId") Long memberId);
}
