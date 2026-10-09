package com.catshop;

import com.catshop.entity.*;
import com.catshop.repository.*;
import com.catshop.service.*;
import com.catshop.service.admin.*;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties = {
        "spring.profiles.active=test", "spring.datasource.url=jdbc:h2:mem:listqueries;MODE=MSSQLServer;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.username=sa", "spring.datasource.password=", "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect", "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false", "app.auth.cleanup.enabled=false", "logging.level.org.hibernate.SQL=warn",
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "logging.level.org.hibernate.stat=warn", "logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener=warn",
        "spring.jpa.properties.hibernate.query.fail_on_pagination_over_collection_fetch=true"
})
@Transactional
class ListQueryIntegrationTest {
    @Test void adminQueriesRejectUnboundedSizesAndAmbiguousSorts() {
        assertThatThrownBy(() -> adminProducts.list(null,null,null,null,0,101,null)).isInstanceOf(com.catshop.exception.BusinessException.class);
        assertThatThrownBy(() -> adminOrders.list(null,null,null,null,null,0,101,null)).isInstanceOf(com.catshop.exception.BusinessException.class);
        assertThatThrownBy(() -> adminCategories.list(null,null,0,101)).isInstanceOf(com.catshop.exception.BusinessException.class);
        assertThatThrownBy(() -> adminProducts.list(null,null,null,null,0,20,"name,other")).isInstanceOf(com.catshop.exception.BusinessException.class);
        assertThatThrownBy(() -> adminOrders.list(null,null,null,null,null,0,20,"id,asc,extra")).isInstanceOf(com.catshop.exception.BusinessException.class);
        assertThatThrownBy(() -> adminProducts.list(0L,null,null,null,0,20,null)).isInstanceOf(com.catshop.exception.BusinessException.class);
        assertThatThrownBy(() -> adminOrders.list("UNKNOWN",null,null,null,null,0,20,null)).isInstanceOf(com.catshop.exception.BusinessException.class);
        assertThatThrownBy(() -> adminCategories.list("x".repeat(201),null,0,20)).isInstanceOf(com.catshop.exception.BusinessException.class);
        assertThatThrownBy(() -> adminOrders.list(null,null,null,null,java.time.LocalDate.of(9999,12,31),0,20,null)).isInstanceOf(com.catshop.exception.BusinessException.class);
    }
    @Test void equalProductSortValuesHaveStableNonOverlappingPages() {
        for (Long id : productIds) {var product=products.findById(id).orElseThrow();product.setName("Equal sort");}
        em.flush();em.clear();
        var expected=productIds.stream().sorted(java.util.Comparator.reverseOrder()).toList();
        var first=adminProducts.list(null,"Equal sort",null,null,0,7,"name,asc").content();
        var second=adminProducts.list(null,"Equal sort",null,null,1,7,"name,asc").content();
        assertThat(first).extracting(p->p.id()).containsExactlyElementsOf(expected.subList(0,7));
        assertThat(second).extracting(p->p.id()).containsExactlyElementsOf(expected.subList(7,14));
    }
    @Autowired EntityManager em;
    @Autowired CategoryRepository categories;
    @Autowired ProductRepository products;
    @Autowired ProductSkuRepository skus;
    @Autowired ProductImageRepository images;
    @Autowired MemberRepository members;
    @Autowired OrderRepository orders;
    @Autowired OrderItemRepository items;
    @Autowired ProductService catalog;
    @Autowired AdminProductService adminProducts;
    @Autowired AdminOrderService adminOrders;
    @Autowired AdminCategoryService adminCategories;
    @Autowired OrderService myOrders;
    @Autowired CartService cartService;
    @Autowired CartRepository carts;
    @Autowired CartItemRepository cartItems;
    private final List<Long> productIds = new ArrayList<>();
    private Long firstMemberId;
    private Long firstCategoryId;

    @BeforeEach void seed() {
        // Distinct categories/members prevent first-level caching from hiding an N+1 regression.
        for (int i = 0; i < 14; i++) {
            var category = categories.save(Category.builder().name("分類" + i).slug("category-" + i).isActive(i != 13).build());
            var product = products.save(Product.builder().category(category).name(String.format("Product%02d", i))
                    .slug("product-" + i).basePrice(BigDecimal.TEN).build());
            productIds.add(product.getId());
            if (i == 0) firstCategoryId = category.getId();
            if (i < 12) {
                for (int j = 1; j <= 3; j++) skus.save(ProductSku.builder().product(product).skuCode("SKU-" + i + "-" + j)
                        .specName("規格" + j).price(BigDecimal.TEN).stock(j).isActive(j != 3).build());
                // No primary for product 11. Two primary images elsewhere test deterministic ordering.
                images.save(ProductImage.builder().product(product).url("secondary-" + i).sortOrder(0).isPrimary(false).build());
                images.save(ProductImage.builder().product(product).url("later-" + i).sortOrder(2).isPrimary(i != 11).build());
                images.save(ProductImage.builder().product(product).url("primary-" + i).sortOrder(1).isPrimary(i != 11).build());
            }
            var member = members.save(Member.builder().name("會員" + i).email("member" + i + "@test.local").passwordHash("test-hash").build());
            if (i == 0) firstMemberId = member.getId();
            var order = orders.save(Order.builder().member(member).orderNo(String.format("ORDER%02d", i))
                    .totalAmount(BigDecimal.valueOf(60)).receiverName("收件人").receiverPhone("0912345678").shippingAddress("台北市").build());
            if (i < 13) {
                for (int j = 0; j < 2; j++) items.save(OrderItem.builder().order(order).productName("快照商品")
                        .skuSpec("快照規格").unitPrice(BigDecimal.TEN).quantity(3).subtotal(BigDecimal.valueOf(30)).build());
            }
        }
        em.flush(); em.clear();
    }

    private long queries(int budget, Runnable action) {
        em.clear();
        var stats = em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        stats.clear();
        action.run();
        long count = stats.getPrepareStatementCount();
        assertThat(count).as("SQL statement budget (cold persistence context)").isLessThanOrEqualTo(budget);
        return count;
    }

    private Cart seedCart(Long memberId, int count) {
        var cart = carts.save(Cart.builder().member(members.getReferenceById(memberId)).build());
        for (int i = 0; i < count; i++) {
            var sku = skus.findByProductIdOrderByIdAsc(productIds.get(i)).get(0);
            cartItems.save(CartItem.builder().cart(cart).sku(sku).quantity(2).unitPrice(sku.getPrice()).build());
        }
        em.flush(); em.clear(); return cart;
    }

    @Test void cartsFetchDistinctProductsInConstantQueries() {
        seedCart(firstMemberId, 1);
        Long other = members.findByEmail("member1@test.local").orElseThrow().getId();
        seedCart(other, 6);
        long one = queries(3, () -> assertThat(cartService.getCart(firstMemberId).items()).hasSize(1));
        long six = queries(3, () -> {
            var response = cartService.getCart(other);
            assertThat(response.items()).hasSize(6);
            assertThat(response.items()).extracting(i -> i.productName()).containsExactly("Product00", "Product01", "Product02", "Product03", "Product04", "Product05");
            assertThat(response.items()).extracting(i -> i.imageUrl()).containsExactly("primary-0", "primary-1", "primary-2", "primary-3", "primary-4", "primary-5");
            assertThat(response.items()).allSatisfy(i -> {
                assertThat(i.specName()).isEqualTo("規格1"); assertThat(i.stock()).isEqualTo(1);
                assertThat(i.unitPrice()).isEqualByComparingTo("10"); assertThat(i.subtotal()).isEqualByComparingTo("20");
            });
            assertThat(response.totalQuantity()).isEqualTo(12);
            assertThat(response.totalAmount()).isEqualByComparingTo("120");
            em.flush();
        });
        assertThat(one).isEqualTo(six).isEqualTo(3);
    }

    @Test void emptyCartSkipsImageQuery() {
        seedCart(firstMemberId, 0);
        queries(2, () -> {
            var response = cartService.getCart(firstMemberId);
            assertThat(response.items()).isEmpty(); assertThat(response.totalQuantity()).isZero();
            assertThat(response.totalAmount()).isEqualByComparingTo("0");
        });
    }

    @Test void cartPreservesMultipleSkusAndMissingPrimaryImage() {
        var cart = seedCart(firstMemberId, 0);
        var skuList = skus.findByProductIdOrderByIdAsc(productIds.get(11));
        for (int i = 0; i < 2; i++) cartItems.save(CartItem.builder().cart(cart).sku(skuList.get(i))
                .quantity(i + 1).unitPrice(BigDecimal.TEN).build());
        em.flush(); em.clear();
        queries(3, () -> {
            var response = cartService.getCart(firstMemberId);
            assertThat(response.items()).hasSize(2).allSatisfy(i -> assertThat(i.imageUrl()).isNull());
            assertThat(response.items()).extracting(i -> i.specName()).containsExactly("規格1", "規格2");
            assertThat(response.totalQuantity()).isEqualTo(3); assertThat(response.totalAmount()).isEqualByComparingTo("30");
        });
    }

    @Test void cartRefreshesAndPersistsCurrentPrice() {
        var cart = seedCart(firstMemberId, 1);
        var item = cartItems.findByCartIdOrderByIdAsc(cart.getId()).get(0);
        item.setUnitPrice(BigDecimal.ONE); em.flush(); em.clear();
        queries(4, () -> {
            var response = cartService.getCart(firstMemberId);
            assertThat(response.items().get(0).unitPrice()).isEqualByComparingTo("10");
            assertThat(response.totalAmount()).isEqualByComparingTo("20"); em.flush();
        });
        em.clear();
        assertThat(cartItems.findByCartIdOrderByIdAsc(cart.getId()).get(0).getUnitPrice()).isEqualByComparingTo("10");
    }

    @Test void cartMutationResponsesRetainCompleteRemainingItems() {
        var cart = seedCart(firstMemberId, 6);
        var addedSku = skus.findByProductIdOrderByIdAsc(productIds.get(6)).get(1);
        var added = cartService.addItem(firstMemberId, new com.catshop.dto.cart.AddCartItemRequest(addedSku.getId(), 1));
        assertThat(added.items()).hasSize(7); assertThat(added.totalAmount()).isEqualByComparingTo("130");
        var addedItem = added.items().get(6);
        assertThat(addedItem.imageUrl()).isEqualTo("primary-6");
        em.flush(); em.clear();
        var updated = cartService.updateItem(firstMemberId, addedItem.id(), new com.catshop.dto.cart.UpdateCartItemRequest(2));
        assertThat(updated.items()).hasSize(7); assertThat(updated.totalAmount()).isEqualByComparingTo("140");
        em.flush(); em.clear();
        var removed = cartService.removeItem(firstMemberId, addedItem.id());
        assertThat(removed.items()).hasSize(6); assertThat(removed.totalAmount()).isEqualByComparingTo("120");
        cartService.clear(firstMemberId); em.flush(); em.clear();
        assertThat(cartService.getCart(firstMemberId).items()).isEmpty();
    }

    @Test void productBrandFilterCombinesWithCategoryStatusAndActivation() {
        var first = products.findById(productIds.get(0)).orElseThrow(); first.setBrand("Acme");
        var second = products.findById(productIds.get(1)).orElseThrow(); second.setBrand("ACME"); second.setIsActive(false);
        var special = products.findById(productIds.get(2)).orElseThrow(); special.setBrand("A%_![Brand]");
        em.flush(); em.clear();
        assertThat(adminProducts.list(null, null, null, null, " acme ", 0, 20, "id,asc").content()).extracting(p -> p.id()).containsExactly(first.getId(), second.getId());
        assertThat(adminProducts.list(firstCategoryId, "Product00", "ON_SALE", true, "ACME", 0, 20, "id,asc").content()).extracting(p -> p.id()).containsExactly(first.getId());
        assertThat(adminProducts.list(null, null, null, false, "Acme", 0, 20, "id,asc").content()).extracting(p -> p.id()).containsExactly(second.getId());
        assertThat(adminProducts.list(null, null, null, null, "%", 0, 20, "id,asc").content()).isEmpty();
        assertThat(adminProducts.list(null, null, null, null, "A%_![Brand]", 0, 20, "id,asc").content()).extracting(p -> p.id()).containsExactly(special.getId());
        assertThat(adminProducts.list(null, null, null, null, "  ", 0, 20, "id,asc").totalElements()).isEqualTo(14);
    }

    @Test void orderDateRangeIncludesLastMillisecondAndCombinesMemberAndStatus() {
        var date = java.time.LocalDate.of(2026, 1, 2);
        for (int i = 0; i < 4; i++) {
            var member = i == 2 ? members.findByEmail("member1@test.local").orElseThrow() : members.getReferenceById(firstMemberId);
            var order = orders.save(Order.builder().member(member).orderNo("RANGE" + i).status(i == 1 ? "PAID" : "PENDING")
                    .totalAmount(BigDecimal.TEN).receiverName("收件人").receiverPhone("0912345678").shippingAddress("台北市").build());
            em.flush();
            var time = i == 3 ? date.plusDays(1).atStartOfDay() : i == 1 ? date.atTime(23, 59, 59, 999000000) : date.atStartOfDay();
            em.createNativeQuery("UPDATE orders SET created_at = :time WHERE id = :id").setParameter("time", time).setParameter("id", order.getId()).executeUpdate();
        }
        em.clear();
        assertThat(adminOrders.list(null, firstMemberId, "RANGE", date, date, 0, 20, "orderNo,asc").content()).extracting(o -> o.orderNo()).containsExactly("RANGE0", "RANGE1");
        assertThat(adminOrders.list("PAID", firstMemberId, "RANGE", date, date, 0, 20, "orderNo,asc").content()).extracting(o -> o.orderNo()).containsExactly("RANGE1");
        assertThat(adminOrders.list(null, firstMemberId, "RANGE", null, date, 0, 20, "orderNo,asc").content()).hasSize(2);
        assertThat(adminOrders.list(null, firstMemberId, "RANGE", date.plusDays(1), null, 0, 20, "orderNo,asc").content()).extracting(o -> o.orderNo()).containsExactly("RANGE3");
    }

    @Test void rejectsInvalidAdminFilterInputs() {
        var from = java.time.LocalDate.of(2026, 1, 2);
        assertThatThrownBy(() -> adminOrders.list(null, null, null, from, from.minusDays(1), 0, 20, null)).isInstanceOf(com.catshop.exception.BusinessException.class);
        assertThatThrownBy(() -> adminOrders.list(null, 0L, null, null, null, 0, 20, null)).isInstanceOf(com.catshop.exception.BusinessException.class);
        assertThatThrownBy(() -> adminOrders.list(null, null, null, null, null, -1, 20, null)).isInstanceOf(com.catshop.exception.BusinessException.class);
        assertThatThrownBy(() -> adminProducts.list(null, null, null, null, "x".repeat(101), 0, 20, null)).isInstanceOf(com.catshop.exception.BusinessException.class);
    }

    @Test void publicProductsHaveConstantQueryCountAndCorrectPaging() {
        long one = queries(3, () -> assertThat(catalog.search(null, null, 0, 1, "name,asc").content()).hasSize(1));
        long six = queries(3, () -> {
            var page = catalog.search(null, null, 0, 6, "name,asc");
            assertThat(page.totalElements()).isEqualTo(13);
            assertThat(page.totalPages()).isEqualTo(3);
            assertThat(page.content()).extracting(p -> p.name()).containsExactly("Product00", "Product01", "Product02", "Product03", "Product04", "Product05");
            assertThat(page.content().get(0).primaryImageUrl()).isEqualTo("primary-0");
            assertThat(page.content()).allSatisfy(p -> assertThat(p.categoryName()).startsWith("分類"));
        });
        assertThat(six).isEqualTo(one);
        queries(3, () -> {
            var last = catalog.search(null, null, 2, 6, "name,asc");
            assertThat(last.content()).extracting(p -> p.name()).containsExactly("Product12");
            assertThat(last.last()).isTrue();
            assertThat(last.content().get(0).primaryImageUrl()).isNull();
        });
        queries(3, () -> assertThat(catalog.search(null, "Product11", 0, 6, "name,asc").content().get(0).primaryImageUrl()).isNull());
    }

    @Test void adminProductsAggregateAllSkusWithoutLoadingCollections() {
        long one = queries(4, () -> adminProducts.list(null, null, null, null, 0, 1, "name,asc"));
        long six = queries(4, () -> {
            var page = adminProducts.list(null, null, null, null, 0, 6, "name,asc");
            assertThat(page.totalElements()).isEqualTo(14);
            assertThat(page.content()).allSatisfy(p -> {
                assertThat(p.skuCount()).isEqualTo(3); // Includes inactive SKU, as before.
                assertThat(p.totalStock()).isEqualTo(6);
                assertThat(p.primaryImageUrl()).startsWith("primary-");
            });
        });
        assertThat(six).isEqualTo(one);
        queries(4, () -> {
            var empty = adminProducts.list(null, "Product12", null, null, 0, 6, "name,asc").content().get(0);
            assertThat(empty.skuCount()).isZero(); assertThat(empty.totalStock()).isZero();
            assertThat(empty.primaryImageUrl()).isNull();
        });
    }

    @Test void adminOrdersCountRowsNotQuantitiesAndFetchMembersOnce() {
        long one = queries(3, () -> adminOrders.list(null, null, null, null, null, 0, 1, "orderNo,asc"));
        long six = queries(3, () -> {
            var page = adminOrders.list(null, null, null, null, null, 0, 6, "orderNo,asc");
            assertThat(page.totalElements()).isEqualTo(14);
            assertThat(page.content()).hasSize(6).allSatisfy(o -> {
                assertThat(o.itemCount()).isEqualTo(2); // 2 rows, not quantity sum 6.
                assertThat(o.memberName()).startsWith("會員"); assertThat(o.memberEmail()).endsWith("@test.local");
            });
        });
        assertThat(six).isEqualTo(one);
        queries(3, () -> assertThat(adminOrders.list(null, null, "ORDER13", null, null, 0, 6, "orderNo,asc").content().get(0).itemCount()).isZero());
    }

    @Test void emptyPagesSkipBatchQueries() {
        queries(2, () -> assertThat(catalog.search(null, "missing", 0, 6, "name,asc").content()).isEmpty());
        queries(2, () -> assertThat(adminProducts.list(null, "missing", null, null, 0, 6, "name,asc").content()).isEmpty());
        queries(2, () -> assertThat(adminOrders.list(null, null, "missing", null, null, 0, 6, "orderNo,asc").content()).isEmpty());
    }

    @Test void relatedProductsBatchPrimaryImagesAndKeepFourItemLimit() {
        var category = categories.getReferenceById(firstCategoryId);
        for (int i = 0; i < 6; i++) {
            var p = products.save(Product.builder().category(category).name("Related" + i).slug("related-" + i).basePrice(BigDecimal.TEN).build());
            images.save(ProductImage.builder().product(p).url("related-primary-" + i).isPrimary(true).build());
        }
        em.flush();
        queries(4, () -> assertThat(catalog.related(productIds.get(0))).hasSize(4).allSatisfy(p -> {
            assertThat(p.id()).isNotEqualTo(productIds.get(0));
            assertThat(p.primaryImageUrl()).startsWith("related-primary-");
        }));
    }

    @Test void myOrdersKeepOwnershipAndListContract() {
        queries(2, () -> {
            var page = myOrders.listMyOrders(firstMemberId, 0, 6);
            assertThat(page.totalElements()).isEqualTo(1);
            assertThat(page.content()).hasSize(1).allSatisfy(o -> {
                assertThat(o.memberId()).isEqualTo(firstMemberId);
                assertThat(o.items()).isNull();
            });
        });
    }

    @Test void categoryPagesHaveConstantQueriesAndFetchParentsOutsideThePage() {
        var parent = categories.save(Category.builder().name("外頁父分類").slug("outer-parent").sortOrder(1000).build());
        var child = categories.save(Category.builder().name("第一頁子分類").slug("first-child").parent(parent).sortOrder(-10).build());
        em.flush();
        long one = queries(3, () -> adminCategories.list(null, null, 0, 1));
        long six = queries(3, () -> {
            var page = adminCategories.list(null, null, 0, 6);
            assertThat(page.totalElements()).isEqualTo(16); assertThat(page.totalPages()).isEqualTo(3);
            assertThat(page.content()).hasSize(6);
            assertThat(page.content().get(0).id()).isEqualTo(child.getId());
            assertThat(page.content().get(0).parentName()).isEqualTo("外頁父分類");
            assertThat(page.content().get(0).productCount()).isZero();
        });
        assertThat(six).isEqualTo(one);
        queries(3, () -> {
            var last = adminCategories.list(null, null, 2, 6);
            assertThat(last.content()).hasSize(4); assertThat(last.last()).isTrue();
            assertThat(last.content().get(3).name()).isEqualTo("外頁父分類");
        });
    }

    @Test void categoryCountsOnlyActiveProductsRegardlessOfSaleStatus() {
        var category = categories.getReferenceById(firstCategoryId);
        products.save(Product.builder().category(category).name("下架但啟用").slug("active-off-shelf")
                .basePrice(BigDecimal.TEN).status("OFF_SHELF").build());
        products.save(Product.builder().category(category).name("停用商品").slug("inactive-product")
                .basePrice(BigDecimal.TEN).isActive(false).build());
        em.flush();
        queries(3, () -> {
            var page = adminCategories.list("  CATEGORY-0  ", null, 0, 6);
            assertThat(page.content()).hasSize(1); assertThat(page.content().get(0).productCount()).isEqualTo(2);
        });
    }

    @Test void categoryKeywordWildcardsAreLiteral() {
        categories.save(Category.builder().name("100%_! [VIP]").slug("literal-special").build());
        categories.save(Category.builder().name("100 ordinary VIP").slug("ordinary-special").build());
        em.flush();
        for (var literal : List.of("%", "_", "!", "[", "[VIP]")) queries(3, () -> {
            var page = adminCategories.list(literal, null, 0, 6);
            assertThat(page.content()).hasSize(1); assertThat(page.content().get(0).name()).isEqualTo("100%_! [VIP]");
        });
    }

    @Test void categoryFilteringEmptyPagesAndInvalidPaging() {
        queries(3, () -> {
            var page = adminCategories.list(null, false, 0, 6);
            assertThat(page.content()).hasSize(1); assertThat(page.content().get(0).name()).isEqualTo("分類13");
            assertThat(page.content().get(0).productCount()).isEqualTo(1);
        });
        queries(2, () -> assertThat(adminCategories.list("missing", null, 0, 6).content()).isEmpty());
        queries(2, () -> {
            var page = adminCategories.list(null, null, 20, 6);
            assertThat(page.content()).isEmpty(); assertThat(page.totalElements()).isEqualTo(14);
        });
        assertThatThrownBy(() -> adminCategories.list(null, null, -1, 6)).isInstanceOf(com.catshop.exception.BusinessException.class);
        assertThatThrownBy(() -> adminCategories.list(null, null, 0, 0)).isInstanceOf(com.catshop.exception.BusinessException.class);
    }
}
