package com.catshop.repository;
import com.catshop.entity.StockMovement;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.*;
public interface StockMovementRepository extends JpaRepository<StockMovement,Long> {
 @Query("select m from StockMovement m where (:skuId is null or m.skuId=:skuId) and (:reason is null or m.reason=:reason)")
 Page<StockMovement> history(@Param("skuId") Long skuId,@Param("reason") String reason,Pageable pageable);
 java.util.List<StockMovement> findByOrderIdOrderByIdAsc(Long orderId);
}
