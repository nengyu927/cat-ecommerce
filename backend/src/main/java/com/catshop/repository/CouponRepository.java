package com.catshop.repository;
import com.catshop.entity.Coupon;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
import java.util.Optional;
public interface CouponRepository extends JpaRepository<Coupon,Long> {
 @Lock(LockModeType.PESSIMISTIC_WRITE)
 @Query("select c from Coupon c where c.id = :id")
 Optional<Coupon> lockById(@org.springframework.data.repository.query.Param("id") Long id);
 @Lock(LockModeType.PESSIMISTIC_WRITE)
 @Query("select c from Coupon c where c.code = :code")
 Optional<Coupon> lockByCode(@org.springframework.data.repository.query.Param("code") String code);
}
