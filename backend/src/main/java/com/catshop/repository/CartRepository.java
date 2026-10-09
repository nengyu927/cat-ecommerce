package com.catshop.repository;

import com.catshop.entity.Cart;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CartRepository extends JpaRepository<Cart, Long> {
    Optional<Cart> findByMemberId(Long memberId);

    // All cart mutations and checkout acquire this lock before reading items/SKUs.
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select c from Cart c where c.member.id = :memberId")
    Optional<Cart> lockByMemberId(@org.springframework.data.repository.query.Param("memberId") Long memberId);
}
