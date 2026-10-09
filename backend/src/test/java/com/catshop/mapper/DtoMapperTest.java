package com.catshop.mapper;

import com.catshop.dto.product.ProductImageResponse;
import com.catshop.entity.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import java.math.BigDecimal;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class DtoMapperTest {
    private final MemberMapper members = Mappers.getMapper(MemberMapper.class);
    private final CatalogMapper catalog = Mappers.getMapper(CatalogMapper.class);
    private final CartMapper carts = Mappers.getMapper(CartMapper.class);
    private final OrderMapper orders = Mappers.getMapper(OrderMapper.class);
    private final AdminMapper admin = Mappers.getMapper(AdminMapper.class);

    private Product product() {
        Category category = Category.builder().id(10L).name("飼料").slug("food").build();
        return Product.builder().id(20L).category(category).name("現在的商品名稱").slug("food-item")
                .description("詳細描述").ingredients("雞肉").suitableAge("成貓").origin("台灣")
                .storageInstructions("乾燥保存").basePrice(new BigDecimal("99.90")).build();
    }
    private ProductSku sku() {
        return ProductSku.builder().id(30L).product(product()).skuCode("FOOD-1").specName("2kg")
                .price(new BigDecimal("99.90")).stock(7).safetyStock(2).version(4).build();
    }

    @Test void memberAndAddressDoNotExposeSecretsOrRelationships() throws Exception {
        Member member = Member.builder().id(1L).email("test@example.com").name("會員")
                .passwordHash("never-expose-this").phone("0912345678").build();
        var response = members.member(member);
        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.role()).isEqualTo("CUSTOMER");
        assertThat(new ObjectMapper().writeValueAsString(response)).doesNotContain("password", "never-expose-this");
        Address address = Address.builder().id(2L).member(member).receiverName("收件人").isDefault(true).build();
        assertThat(members.address(address).isDefault()).isTrue();
        assertThat(new ObjectMapper().writeValueAsString(members.address(address))).doesNotContain("member", "password");
        assertThat(members.member(null)).isNull();
    }

    @Test void categoryParentIsFlattenedWithoutRecursingThroughEntities() {
        Category parent = Category.builder().id(1L).name("主分類").build();
        Category child = Category.builder().id(2L).name("子分類").parent(parent).build();
        var dto = catalog.category(child);
        assertThat(dto.parentId()).isEqualTo(1L);
        assertThat(dto.children()).isNull();
        var tree = catalog.categoryTree(parent, List.of(dto));
        assertThat(tree.parentId()).isNull();
        assertThat(tree.children()).containsExactly(dto);
        assertThat(admin.category(child, 8L).parentName()).isEqualTo("主分類");
        assertThat(admin.category(child, 8L).productCount()).isEqualTo(8L);
    }

    @Test void productSummaryKeepsListPayloadSmallAndFieldNamesStable() {
        var response = catalog.summary(product(), "/api/images/example.png");
        assertThat(response.categoryId()).isEqualTo(10L);
        assertThat(response.categoryName()).isEqualTo("飼料");
        assertThat(response.isActive()).isTrue();
        assertThat(response.primaryImageUrl()).isEqualTo("/api/images/example.png");
        assertThat(response.description()).isNull();
        assertThat(response.ingredients()).isNull();
        assertThat(response.images()).isNull();
        assertThat(response.skus()).isNull();
    }

    @Test void productDetailPrefersPrimaryThenFirstImageAndKeepsInformation() {
        var first = new ProductImageResponse(1L, "/first.png", "第一張", 0, false);
        var primary = new ProductImageResponse(2L, "/primary.png", "主圖", 1, true);
        var response = catalog.detail(product(), List.of(first, primary), List.of(catalog.sku(sku())));
        assertThat(response.primaryImageUrl()).isEqualTo("/primary.png");
        assertThat(response.ingredients()).isEqualTo("雞肉");
        assertThat(response.suitableAge()).isEqualTo("成貓");
        assertThat(response.origin()).isEqualTo("台灣");
        assertThat(response.storageInstructions()).isEqualTo("乾燥保存");
        assertThat(response.skus().get(0).stock()).isEqualTo(7);
        assertThat(catalog.detail(product(), List.of(first), List.of()).primaryImageUrl()).isEqualTo("/first.png");
        assertThat(catalog.detail(product(), List.of(), List.of()).primaryImageUrl()).isNull();
    }

    @Test void cartMapsNestedSkuAndCalculatesDecimalSubtotal() {
        CartItem item = CartItem.builder().id(40L).sku(sku()).quantity(3).unitPrice(new BigDecimal("12.30")).build();
        var response = carts.item(item, "/image.png");
        assertThat(response.skuId()).isEqualTo(30L);
        assertThat(response.productId()).isEqualTo(20L);
        assertThat(response.productName()).isEqualTo("現在的商品名稱");
        assertThat(response.specName()).isEqualTo("2kg");
        assertThat(response.unitPrice()).isEqualByComparingTo("12.30");
        assertThat(response.subtotal()).isEqualByComparingTo("36.90");
        assertThat(response.stock()).isEqualTo(7);
    }

    @Test void orderItemMapsHistoricalSnapshotNotLiveSkuAndAllowsDeletedSku() {
        OrderItem item = OrderItem.builder().id(50L).sku(sku()).productName("下單當時商品")
                .skuSpec("舊規格").unitPrice(new BigDecimal("8.50")).quantity(2).subtotal(new BigDecimal("17.00")).build();
        var response = orders.item(item);
        assertThat(response.productName()).isEqualTo("下單當時商品");
        assertThat(response.skuSpec()).isEqualTo("舊規格");
        assertThat(response.unitPrice()).isEqualByComparingTo("8.50");
        assertThat(response.skuId()).isEqualTo(30L);
        item.setSku(null);
        assertThat(orders.item(item).skuId()).isNull();
        assertThat(orders.item(item).productName()).isEqualTo("下單當時商品");
    }

    @Test void orderSummaryAndAdminFlattenMemberWithoutEntityPayload() {
        Member member = Member.builder().id(1L).name("會員").email("test@example.com").build();
        Order order = Order.builder().id(60L).member(member).orderNo("TEST-ORDER").paymentMethod("COD")
                .totalAmount(new BigDecimal("100.00")).shippingFee(BigDecimal.ZERO).build();
        assertThat(orders.summary(order).memberId()).isEqualTo(1L);
        assertThat(orders.summary(order).items()).isNull();
        assertThat(orders.detail(order, List.of()).items()).isEmpty();
        assertThat(admin.orderSummary(order, 2).itemCount()).isEqualTo(2);
        assertThat(admin.orderSummary(order, 2).memberEmail()).isEqualTo("test@example.com");
        assertThat(admin.orderDetail(order, List.of()).memberName()).isEqualTo("會員");
        assertThat(admin.orderDetail(order, List.of()).paymentMethod()).isEqualTo("COD");
    }

    @Test void adminPreservesSkuVersionSafetyStockAndAggregates() {
        var response = admin.sku(sku());
        assertThat(response.version()).isEqualTo(4);
        assertThat(response.safetyStock()).isEqualTo(2);
        assertThat(response.isActive()).isTrue();
        var summary = admin.productSummary(product(), 3, 9, "/primary.png");
        assertThat(summary.skuCount()).isEqualTo(3);
        assertThat(summary.totalStock()).isEqualTo(9);
        assertThat(summary.categoryName()).isEqualTo("飼料");
        assertThat(admin.productDetail(product(), List.of(), List.of(response)).skus()).containsExactly(response);
    }
}
