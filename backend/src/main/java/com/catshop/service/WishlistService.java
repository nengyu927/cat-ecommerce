package com.catshop.service;

import com.catshop.common.PageResult;
import com.catshop.dto.wishlist.WishlistItemResponse;
import com.catshop.entity.Product;
import com.catshop.entity.WishlistItem;
import com.catshop.exception.BusinessException;
import com.catshop.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service @RequiredArgsConstructor
public class WishlistService {
    private final WishlistRepository wishlist;
    private final MemberRepository members;
    private final ProductRepository products;
    private final CatalogSummaryLoader summaries;
    private static final int LIMIT = 500;

    @Transactional(readOnly = true)
    public List<Long> ids(Long memberId) { return wishlist.productIds(memberId); }

    @Transactional(readOnly = true)
    public PageResult<WishlistItemResponse> list(Long memberId, int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw BusinessException.badRequest("分頁參數不合法");
        var result = wishlist.findByMemberId(memberId, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id")));
        var images = summaries.primaryImages(result.getContent().stream().map(w -> w.getProduct().getId()).toList());
        return PageResult.of(result, w -> {
            var p = w.getProduct();
            return new WishlistItemResponse(p.getId(), p.getName(), p.getBrand(), p.getBasePrice(), images.get(p.getId()),
                    p.getStatus(), available(p), w.getCreatedAt());
        });
    }

    @Transactional
    public List<Long> add(Long memberId, Long productId) {
        validateId(productId);
        // Serialize all writes per member before checking uniqueness/capacity.
        var member = members.lockForWishlistMutation(memberId).orElseThrow(() -> BusinessException.notFound("找不到會員"));
        if (wishlist.existsByMemberIdAndProductId(memberId, productId)) return wishlist.productIds(memberId);
        var product = products.findById(productId).orElseThrow(() -> BusinessException.notFound("找不到商品"));
        if (!available(product)) throw BusinessException.badRequest("此商品目前無法加入收藏");
        if (wishlist.countByMemberId(memberId) >= LIMIT) throw BusinessException.badRequest("收藏上限為 500 件商品");
        wishlist.saveAndFlush(WishlistItem.builder().member(member).product(product).build());
        return wishlist.productIds(memberId);
    }

    @Transactional
    public List<Long> remove(Long memberId, Long productId) {
        validateId(productId);
        members.lockForWishlistMutation(memberId).orElseThrow(() -> BusinessException.notFound("找不到會員"));
        wishlist.deleteByMemberIdAndProductId(memberId, productId);
        wishlist.flush();
        return wishlist.productIds(memberId);
    }

    private void validateId(Long id) {
        if (id == null || id <= 0) throw BusinessException.badRequest("商品編號須為正整數");
    }
    private boolean available(Product p) {
        return Boolean.TRUE.equals(p.getIsActive()) && "ON_SALE".equals(p.getStatus()) && Boolean.TRUE.equals(p.getCategory().getIsActive());
    }
}
