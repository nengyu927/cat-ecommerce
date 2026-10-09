package com.catshop.repository;

import com.catshop.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
    interface ItemCount {
        Long getOrderId();
        Long getItemCount();
    }

    @org.springframework.data.jpa.repository.Query("""
            select i.order.id as orderId, count(i.id) as itemCount from OrderItem i
            where i.order.id in :ids group by i.order.id
            """)
    List<ItemCount> countItems(@org.springframework.data.repository.query.Param("ids") java.util.Collection<Long> ids);

    List<OrderItem> findByOrderId(Long orderId);
}
