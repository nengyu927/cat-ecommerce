package com.catshop.service;

import com.catshop.dto.cart.AddCartItemRequest;
import com.catshop.dto.cart.CartItemResponse;
import com.catshop.dto.cart.CartResponse;
import com.catshop.dto.cart.UpdateCartItemRequest;
import com.catshop.entity.Cart;
import com.catshop.entity.CartItem;
import com.catshop.entity.Member;
import com.catshop.entity.ProductSku;
import com.catshop.exception.BusinessException;
import com.catshop.repository.CartItemRepository;
import com.catshop.repository.CartRepository;
import com.catshop.repository.MemberRepository;
import com.catshop.repository.ProductSkuRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CartService {

    private final com.catshop.mapper.CartMapper cartMapper;

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductSkuRepository skuRepository;
    private final CatalogSummaryLoader summaries;
    private final MemberRepository memberRepository;

    @Transactional
    public CartResponse getCart(Long memberId) {
        Cart cart = getOrCreateCart(memberId);
        // A refreshed cart always quotes current prices; only placed orders retain historical prices.
        var items = cartItemRepository.findByCartIdOrderByIdAsc(cart.getId());
        items.forEach(item -> item.setUnitPrice(item.getSku().getPrice()));
        return toResponse(cart, items);
    }

    @Transactional
    public CartResponse addItem(Long memberId, AddCartItemRequest req) {
        Cart cart = getOrCreateCart(memberId);
        ProductSku sku = skuRepository.findById(req.skuId())
                .orElseThrow(() -> BusinessException.notFound("找不到商品規格"));
        if (!Boolean.TRUE.equals(sku.getIsActive()) || !Boolean.TRUE.equals(sku.getProduct().getIsActive())
                || !"ON_SALE".equals(sku.getProduct().getStatus())) {
            throw BusinessException.badRequest("此規格已下架");
        }
        CartItem item = cartItemRepository.findByCartIdAndSkuId(cart.getId(), sku.getId())
                .orElseGet(() -> CartItem.builder()
                        .cart(cart)
                        .sku(sku)
                        .quantity(0)
                        .unitPrice(sku.getPrice())
                        .build());
        long newQty = (long) item.getQuantity() + req.quantity();
        if (newQty > sku.getStock() || newQty > Integer.MAX_VALUE) {
            throw BusinessException.badRequest("庫存不足，目前僅剩 " + sku.getStock() + " 件");
        }
        item.setQuantity((int) newQty);
        item.setUnitPrice(sku.getPrice());
        cartItemRepository.save(item);
        return toResponse(cart);
    }

    @Transactional
    public CartResponse updateItem(Long memberId, Long itemId, UpdateCartItemRequest req) {
        Cart cart = getOrCreateCart(memberId);
        CartItem item = findOwnedItem(cart.getId(), itemId);
        if (!Boolean.TRUE.equals(item.getSku().getIsActive()) || !Boolean.TRUE.equals(item.getSku().getProduct().getIsActive())
                || !"ON_SALE".equals(item.getSku().getProduct().getStatus())) {
            throw BusinessException.badRequest("此商品已下架");
        }
        if (req.quantity() > item.getSku().getStock()) {
            throw BusinessException.badRequest("庫存不足，目前僅剩 " + item.getSku().getStock() + " 件");
        }
        item.setQuantity(req.quantity());
        item.setUnitPrice(item.getSku().getPrice());
        cartItemRepository.save(item);
        return toResponse(cart);
    }

    @Transactional
    public CartResponse removeItem(Long memberId, Long itemId) {
        Cart cart = getOrCreateCart(memberId);
        cartItemRepository.delete(findOwnedItem(cart.getId(), itemId));
        return toResponse(cart);
    }

    @Transactional
    public void clear(Long memberId) {
        Cart cart = getOrCreateCart(memberId);
        cartItemRepository.deleteByCartId(cart.getId());
    }

    private Cart getOrCreateCart(Long memberId) {
        // GET reprices persisted items, so it shares the same lock as writes and checkout.
        return cartRepository.lockByMemberId(memberId).orElseGet(() -> {
            // A missing cart has no row to lock. Serialize first creation on its owning member row.
            Member member = memberRepository.lockForCartCreation(memberId)
                    .orElseThrow(() -> BusinessException.notFound("找不到會員"));
            // Another transaction may have created it while this transaction waited for the lock.
            return cartRepository.lockByMemberId(memberId)
                    .orElseGet(() -> cartRepository.save(Cart.builder().member(member).build()));
        });
    }

    private CartItem findOwnedItem(Long cartId, Long itemId) {
        CartItem item = cartItemRepository.findById(itemId)
                .orElseThrow(() -> BusinessException.notFound("找不到購物車項目"));
        if (!item.getCart().getId().equals(cartId)) {
            throw BusinessException.notFound("找不到購物車項目");
        }
        return item;
    }

    private CartResponse toResponse(Cart cart) {
        return toResponse(cart, cartItemRepository.findByCartIdOrderByIdAsc(cart.getId()));
    }

    private CartResponse toResponse(Cart cart, List<CartItem> items) {
        var images = summaries.primaryImages(items.stream().map(ci -> ci.getSku().getProduct().getId()).distinct().toList());
        List<CartItemResponse> itemResponses = items.stream()
                .map(ci -> cartMapper.item(ci, images.get(ci.getSku().getProduct().getId())))
                .toList();
        int totalQty = items.stream().mapToInt(CartItem::getQuantity).sum();
        BigDecimal total = items.stream()
                .map(ci -> ci.getUnitPrice().multiply(BigDecimal.valueOf(ci.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new CartResponse(cart.getId(), itemResponses, totalQty, total);
    }

}
