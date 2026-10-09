package com.catshop.repository;

import com.catshop.entity.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"sku", "sku.product"})
    List<CartItem> findByCartIdOrderByIdAsc(Long cartId);
    Optional<CartItem> findByCartIdAndSkuId(Long cartId, Long skuId);
    void deleteByCartId(Long cartId);
}
