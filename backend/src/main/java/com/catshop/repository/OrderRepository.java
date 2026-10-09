package com.catshop.repository;

import com.catshop.entity.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {
    Optional<Order> findByMemberIdAndRequestId(Long memberId, String requestId);
    long countByCouponIdAndStatusNot(Long couponId,String status);
    long countByCouponIdAndMemberIdAndStatusNot(Long couponId,Long memberId,String status);
    Page<Order> findByMemberIdOrderByCreatedAtDesc(Long memberId, Pageable pageable);
    Optional<Order> findByOrderNo(String orderNo);

    // ---- admin ----
    @Query("""
            SELECT o FROM Order o
            WHERE (:status IS NULL OR o.status = :status)
              AND (:memberId IS NULL OR o.member.id = :memberId)
              AND (:orderNo IS NULL OR o.orderNo LIKE CONCAT('%', :orderNo, '%'))
              AND (:from IS NULL OR o.createdAt >= :from)
              AND (:to IS NULL OR o.createdAt < :to)
            """)
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "member")
    Page<Order> adminSearch(@Param("status") String status,
                            @Param("memberId") Long memberId,
                            @Param("orderNo") String orderNo,
                            @Param("from") LocalDateTime from,
                            @Param("to") LocalDateTime to,
                            Pageable pageable);
}
