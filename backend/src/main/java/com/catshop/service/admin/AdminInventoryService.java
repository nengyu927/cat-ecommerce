package com.catshop.service.admin;

import com.catshop.common.PageResult;
import com.catshop.dto.admin.inventory.LowStockResponse;
import com.catshop.exception.BusinessException;
import com.catshop.repository.ProductSkuRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import java.util.Locale;

@Service @RequiredArgsConstructor
public class AdminInventoryService {
    private final ProductSkuRepository skus;
    private final com.catshop.repository.StockMovementRepository movements;

    @Transactional(readOnly=true)
    public PageResult<com.catshop.dto.admin.inventory.StockMovementResponse> movements(Long skuId, String reason, int page, int size) {
        if (page<0 || size<1 || size>100 || (skuId!=null && skuId<1)) throw BusinessException.badRequest("SKU 須為正整數，分頁大小須為 1 至 100，頁碼不可為負數");
        String filter=reason==null || reason.isBlank() ? null : reason.trim();
        if (filter!=null && !java.util.Set.of("SKU_CREATED","ADMIN_ADJUSTMENT","ORDER_PLACED","ORDER_CANCELLED").contains(filter)) throw BusinessException.badRequest("不支援的庫存異動原因");
        return PageResult.of(movements.history(skuId,filter,PageRequest.of(page,size,Sort.by(Sort.Direction.DESC,"id"))),com.catshop.dto.admin.inventory.StockMovementResponse::of);
    }

    @Transactional(readOnly=true)
    public LowStockResponse lowStock(String keyword, boolean outOfStockOnly, int page, int size) {
        if (page<0 || size<1 || size>100) throw BusinessException.badRequest("分頁大小須為 1 至 100，頁碼不可為負數");
        if (keyword!=null && keyword.length()>200) throw BusinessException.badRequest("搜尋字詞不可超過 200 字");
        String term=keyword==null ? "" : keyword.trim();
        term=term.isEmpty() ? null : term.toLowerCase(Locale.ROOT).replace("!","!!").replace("%","!%").replace("_","!_").replace("[","![");
        var counts=skus.lowStockCounts(term);
        var rows=skus.lowStock(term,outOfStockOnly,PageRequest.of(page,size,Sort.by("stock").and(Sort.by("id"))));
        var items=PageResult.of(rows,s -> new LowStockResponse.Item(s.getId(),s.getProduct().getId(),s.getProduct().getName(),s.getProduct().getStatus(),
            s.getSkuCode(),s.getSpecName(),s.getProduct().getCategory().getName(),s.getStock(),s.getSafetyStock(),Math.max(0,s.getSafetyStock()-s.getStock())));
        return new LowStockResponse(items,counts.getLowStockCount()==null ? 0 : counts.getLowStockCount(),counts.getOutOfStockCount()==null ? 0 : counts.getOutOfStockCount());
    }
}
