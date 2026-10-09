package com.catshop;

import com.catshop.dto.order.CreateOrderRequest;
import com.catshop.dto.order.OrderResponse;
import com.catshop.entity.*;
import com.catshop.repository.*;
import com.catshop.service.OrderService;
import com.catshop.service.ShippingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.profiles.active=test", "spring.datasource.url=jdbc:h2:mem:checkout;MODE=MSSQLServer;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.username=sa", "spring.datasource.password=", "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect", "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false", "logging.level.org.hibernate.SQL=warn", "app.auth.login-limit=1000",
        "app.auth.cleanup.enabled=false"
})
@AutoConfigureMockMvc
class CheckoutIntegrationTest {
    private CreateOrderRequest quotedRequest(String code) {
        var q=coupons.quote(member.getId(),code);
        return new CreateOrderRequest("收件人","0912345678","台北市",null,"COD",q.couponCode(),
                java.util.UUID.randomUUID().toString(),q.cartSnapshot(),
                new com.catshop.dto.order.CheckoutAmounts(q.goodsTotal(),q.discountAmount(),q.shippingFee(),q.totalAmount(),q.priceSnapshot()));
    }

    @Test void baseQuoteUsesServerFeeAndConfirmedReplayIgnoresLaterFeeChanges() {
        doReturn(new BigDecimal("37")).when(shipping).quote(any());
        var req=quotedRequest(null);
        assertThat(req.expectedAmounts().shippingFee()).isEqualByComparingTo("37");
        assertThat(orders.count()).isZero();
        var first=service.createOrder(member.getId(),req);
        assertThat(first.totalAmount()).isEqualByComparingTo(req.expectedAmounts().totalAmount());
        doReturn(new BigDecimal("99")).when(shipping).quote(any());
        refillCart();
        assertThat(service.createOrder(member.getId(),req).id()).isEqualTo(first.id());
        assertThat(cartItems.count()).isEqualTo(1);
        assertThat(movements.count()).isEqualTo(1);
    }

    @Test void changedPriceRejectsConfirmedQuoteWithoutConsumingCouponOrStock() {
        coupons.save(null,couponRequest("PRICES","100","0",1,true,null));
        var req=quotedRequest("PRICES");
        sku.setPrice(sku.getPrice().add(BigDecimal.ONE));skus.saveAndFlush(sku);
        assertThatThrownBy(()->service.createOrder(member.getId(),req))
                .isInstanceOfSatisfying(com.catshop.exception.BusinessException.class,e->assertThat(e.getCode()).isEqualTo("CHECKOUT_PRICE_CONFLICT"));
        assertThat(orders.count()).isZero();assertThat(movements.count()).isZero();
        assertThat(skus.findById(sku.getId()).orElseThrow().getStock()).isEqualTo(10);
        assertThat(cartService.getCart(member.getId()).totalQuantity()).isEqualTo(2);
        var updated=quotedRequest("PRICES");
        assertThat(service.createOrder(member.getId(),updated).totalAmount()).isEqualByComparingTo(updated.expectedAmounts().totalAmount());
    }

    @Test void shippingChangeAndEveryTamperedConfirmedAmountAreRejected() {
        var req=quotedRequest(null);var a=req.expectedAmounts();
        for(var wrong:List.of(
                new com.catshop.dto.order.CheckoutAmounts(a.goodsTotal().add(BigDecimal.ONE),a.discountAmount(),a.shippingFee(),a.totalAmount(),a.priceSnapshot()),
                new com.catshop.dto.order.CheckoutAmounts(a.goodsTotal(),BigDecimal.ONE,a.shippingFee(),a.totalAmount(),a.priceSnapshot()),
                new com.catshop.dto.order.CheckoutAmounts(a.goodsTotal(),a.discountAmount(),a.shippingFee().add(BigDecimal.ONE),a.totalAmount(),a.priceSnapshot()),
                new com.catshop.dto.order.CheckoutAmounts(a.goodsTotal(),a.discountAmount(),a.shippingFee(),BigDecimal.ZERO,a.priceSnapshot()),
                new com.catshop.dto.order.CheckoutAmounts(a.goodsTotal(),a.discountAmount(),a.shippingFee(),a.totalAmount(),"wrong"))) {
            var tampered=new CreateOrderRequest(req.receiverName(),req.receiverPhone(),req.shippingAddress(),req.remark(),
                    req.paymentMethod(),req.couponCode(),req.requestId(),req.cartSnapshot(),wrong);
            assertThatThrownBy(()->service.createOrder(member.getId(),tampered)).hasMessageContaining("已變更");
        }
        doReturn(a.shippingFee().add(BigDecimal.ONE)).when(shipping).quote(any());
        assertThatThrownBy(()->service.createOrder(member.getId(),req)).hasMessageContaining("已變更");
        assertThat(orders.count()).isZero();assertThat(movements.count()).isZero();
        assertThat(cartItems.count()).isEqualTo(1);
    }

    @Test void amountHttpValidationAndAuthenticatedBaseQuote() throws Exception {
        String token=login();
        mvc.perform(get("/api/orders/checkout-quote")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/orders/checkout-quote").header("Authorization",token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cartSnapshot").value("[["+sku.getId()+",2]]"))
                .andExpect(jsonPath("$.data.priceSnapshot").exists());
        var req=quotedRequest(null);
        var body=(com.fasterxml.jackson.databind.node.ObjectNode)json.valueToTree(req);
        var amount=(com.fasterxml.jackson.databind.node.ObjectNode)body.get("expectedAmounts");
        amount.put("totalAmount",-1);
        mvc.perform(post("/api/orders").header("Authorization",token).contentType(MediaType.APPLICATION_JSON)
                .content(body.toString())).andExpect(status().isBadRequest());
        amount.remove("totalAmount");
        mvc.perform(post("/api/orders").header("Authorization",token).contentType(MediaType.APPLICATION_JSON)
                .content(body.toString())).andExpect(status().isBadRequest());
        assertThat(orders.count()).isZero();
    }
    private CreateOrderRequest snapshotRequest() {
        return new CreateOrderRequest("收件人","0912345678","台北市",null,"COD",null,
                java.util.UUID.randomUUID().toString(),"[[" + sku.getId() + ",2]]");
    }

    @Test void changedSnapshotRejectsBeforeAnyOrderOrStockSideEffect() {
        var req = snapshotRequest();
        cartService.addItem(member.getId(),new com.catshop.dto.cart.AddCartItemRequest(sku.getId(),1));
        assertThatThrownBy(() -> service.createOrder(member.getId(),req))
                .isInstanceOfSatisfying(com.catshop.exception.BusinessException.class,e -> {
                    assertThat(e.getCode()).isEqualTo("CART_SNAPSHOT_CONFLICT");
                    assertThat(e.getStatus().value()).isEqualTo(409);
                });
        assertThat(orders.count()).isZero();
        assertThat(movements.count()).isZero();
        assertThat(skus.findById(sku.getId()).orElseThrow().getStock()).isEqualTo(10);
        assertThat(cartService.getCart(member.getId()).totalQuantity()).isEqualTo(3);
        assertThatThrownBy(() -> service.getByRequestId(member.getId(),req.requestId())).hasMessageContaining("尚未查到");
    }

    @Test void snapshotReplayKeepsLaterCartAndChangedSnapshotCannotReuseId() {
        var req = snapshotRequest();
        var first = service.createOrder(member.getId(),req);
        assertThat(service.createOrder(member.getId(),req).id()).isEqualTo(first.id());
        refillCart();
        assertThat(service.createOrder(member.getId(),req).id()).isEqualTo(first.id());
        var changed = new CreateOrderRequest(req.receiverName(),req.receiverPhone(),req.shippingAddress(),
                req.remark(),req.paymentMethod(),req.couponCode(),req.requestId(),"[]");
        assertThatThrownBy(() -> service.createOrder(member.getId(),changed))
                .isInstanceOfSatisfying(com.catshop.exception.BusinessException.class,e -> assertThat(e.getCode()).isEqualTo("REQUEST_ID_CONFLICT"));
        assertThat(orders.count()).isEqualTo(1);
        assertThat(cartItems.count()).isEqualTo(1);
        assertThat(movements.count()).isEqualTo(1);
    }

    @Test void checkoutWaitingForCartMutationValidatesCommittedSnapshot() throws Exception {
        var req = snapshotRequest();
        var changed = new CountDownLatch(1); var release = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var mutation = pool.submit(() -> new org.springframework.transaction.support.TransactionTemplate(transactionManager).execute(status -> {
                cartService.addItem(member.getId(),new com.catshop.dto.cart.AddCartItemRequest(sku.getId(),1));
                changed.countDown();
                try { if (!release.await(10,TimeUnit.SECONDS)) throw new IllegalStateException("mutation timeout"); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
                return true;
            }));
            assertThat(changed.await(5,TimeUnit.SECONDS)).isTrue();
            var checkout = pool.submit(() -> service.createOrder(member.getId(),req));
            assertThatThrownBy(() -> checkout.get(200,TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            release.countDown();
            assertThat(mutation.get(15,TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> checkout.get(15,TimeUnit.SECONDS)).hasCauseInstanceOf(com.catshop.exception.BusinessException.class)
                    .hasStackTraceContaining("購物車內容已變更");
            assertThat(orders.count()).isZero(); assertThat(movements.count()).isZero();
            assertThat(cartService.getCart(member.getId()).totalQuantity()).isEqualTo(3);
        } finally { release.countDown(); pool.shutdownNow(); assertThat(pool.awaitTermination(15,TimeUnit.SECONDS)).isTrue(); }
    }

    @Test void snapshotHttpBoundsMismatchAndLegacyCompatibility() throws Exception {
        String token = login();
        var req = snapshotRequest();
        var body = (com.fasterxml.jackson.databind.node.ObjectNode) json.valueToTree(req);
        body.put("cartSnapshot","x".repeat(24001));
        mvc.perform(post("/api/orders").header("Authorization",token).contentType(MediaType.APPLICATION_JSON)
                .content(body.toString())).andExpect(status().isBadRequest());
        body.put("cartSnapshot","[]");
        mvc.perform(post("/api/orders").header("Authorization",token).contentType(MediaType.APPLICATION_JSON)
                .content(body.toString())).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("CART_SNAPSHOT_CONFLICT"));
        assertThat(orders.count()).isZero();
        // Seven-field old clients stay compatible; only v2 clients assert a snapshot.
        assertThat(service.createOrder(member.getId(),identified(req.requestId())).id()).isPositive();
    }

    private CreateOrderRequest identified(String id) {
        return new CreateOrderRequest("收件人", "0912345678", "台北市", "備註", "COD", null, id);
    }

    @Test void identifiedCheckoutReplaysWithoutTouchingEmptyOrRefilledCart() {
        var req = identified(java.util.UUID.randomUUID().toString());
        var first = service.createOrder(member.getId(), req);
        assertThat(service.createOrder(member.getId(), req).id()).isEqualTo(first.id());
        refillCart();
        assertThat(service.createOrder(member.getId(), req).id()).isEqualTo(first.id());
        assertThat(service.getByRequestId(member.getId(), req.requestId().toUpperCase(java.util.Locale.ROOT)).id()).isEqualTo(first.id());
        assertThat(orders.count()).isEqualTo(1);
        assertThat(orderItems.count()).isEqualTo(1);
        assertThat(cartItems.count()).isEqualTo(1);
        assertThat(skus.findById(sku.getId()).orElseThrow().getStock()).isEqualTo(8);
        assertThat(movements.count()).isEqualTo(1);
    }

    @Test void identifiedCheckoutRejectsEveryChangedBodyField() {
        String id = java.util.UUID.randomUUID().toString();
        service.createOrder(member.getId(), identified(id));
        for (var req : List.of(
                new CreateOrderRequest("其他人","0912345678","台北市","備註","COD",null,id),
                new CreateOrderRequest("收件人","0922222222","台北市","備註","COD",null,id),
                new CreateOrderRequest("收件人","0912345678","高雄市","備註","COD",null,id),
                new CreateOrderRequest("收件人","0912345678","台北市",null,"COD",null,id),
                new CreateOrderRequest("收件人","0912345678","台北市","備註","MOCK",null,id),
                new CreateOrderRequest("收件人","0912345678","台北市","備註","COD","SAVE",id))) {
            assertThatThrownBy(() -> service.createOrder(member.getId(), req))
                    .isInstanceOfSatisfying(com.catshop.exception.BusinessException.class, e -> {
                        assertThat(e.getCode()).isEqualTo("REQUEST_ID_CONFLICT");
                        assertThat(e.getStatus().value()).isEqualTo(409);
                    });
        }
        assertThat(orders.count()).isEqualTo(1);
    }

    @Test void concurrentIdentifiedRequestsBothReturnTheSameOrder() throws Exception {
        var req = identified(java.util.UUID.randomUUID().toString());
        var barrier = new CyclicBarrier(2);
        var pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Long> send = () -> { barrier.await(5, TimeUnit.SECONDS); return service.createOrder(member.getId(), req).id(); };
            var a = pool.submit(send); var b = pool.submit(send);
            assertThat(a.get(15, TimeUnit.SECONDS)).isEqualTo(b.get(15, TimeUnit.SECONDS));
            assertThat(orders.count()).isEqualTo(1);
            assertThat(skus.findById(sku.getId()).orElseThrow().getStock()).isEqualTo(8);
            assertThat(movements.count()).isEqualTo(1);
        } finally { pool.shutdownNow(); }
    }

    @Test void rolledBackIdentityCanBeSafelyRetried() {
        var req = identified(java.util.UUID.randomUUID().toString());
        assertThatThrownBy(() -> new org.springframework.transaction.support.TransactionTemplate(transactionManager).execute(status -> {
            service.createOrder(member.getId(), req);
            throw new IllegalStateException("rollback");
        })).hasMessage("rollback");
        assertThat(orders.count()).isZero();
        assertThat(movements.count()).isZero();
        assertThat(skus.findById(sku.getId()).orElseThrow().getStock()).isEqualTo(10);
        assertThatThrownBy(() -> service.getByRequestId(member.getId(), req.requestId())).hasMessageContaining("尚未查到");
        var result = service.createOrder(member.getId(), req);
        assertThat(service.createOrder(member.getId(), req).id()).isEqualTo(result.id());
        assertThat(orders.count()).isEqualTo(1);
    }

    @Test void couponReplayAndCancelledReplayHaveNoNewSideEffects() {
        coupons.save(null, couponRequest("ONCE","100","0",1,true,null));
        var req = new CreateOrderRequest("收件人","0912345678","台北市",null,"MOCK","ONCE",java.util.UUID.randomUUID().toString());
        var first = service.createOrder(member.getId(), req);
        assertThat(service.createOrder(member.getId(), req).discountAmount()).isEqualByComparingTo("100");
        service.cancelMyOrder(member.getId(), first.id());
        assertThat(service.createOrder(member.getId(), req).status()).isEqualTo("CANCELLED");
        assertThat(skus.findById(sku.getId()).orElseThrow().getStock()).isEqualTo(10);
        assertThat(movements.count()).isEqualTo(2);
        assertThat(orders.count()).isEqualTo(1);
    }

    @Test void requestIdentityIsScopedToMember() {
        var req = identified(java.util.UUID.randomUUID().toString());
        var first = service.createOrder(member.getId(), req);
        var other = members.saveAndFlush(Member.builder().email("other@example.com").name("其他會員").passwordHash(encoder.encode("password123")).build());
        assertThatThrownBy(() -> service.getByRequestId(other.getId(), req.requestId()))
                .isInstanceOfSatisfying(com.catshop.exception.BusinessException.class, e -> assertThat(e.getStatus().value()).isEqualTo(404));
        var otherCart = carts.saveAndFlush(Cart.builder().member(other).build());
        cartItems.saveAndFlush(CartItem.builder().cart(otherCart).sku(skus.findById(sku.getId()).orElseThrow()).quantity(1).unitPrice(BigDecimal.ONE).build());
        assertThat(service.createOrder(other.getId(), req).id()).isNotEqualTo(first.id());
        assertThat(orders.count()).isEqualTo(2);
    }

    @Test void requestIdentityHttpContractAndValidation() throws Exception {
        var req = identified(java.util.UUID.randomUUID().toString());
        String token = login();
        mvc.perform(get("/api/orders/request-identity").header("Authorization",token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.version").value(3));
        mvc.perform(get("/api/orders/request-identity")).andExpect(status().isUnauthorized());
        var first = mvc.perform(post("/api/orders").header("Authorization",token).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(req))).andExpect(status().isCreated()).andReturn();
        long id = json.readTree(first.getResponse().getContentAsString()).at("/data/id").asLong();
        mvc.perform(post("/api/orders").header("Authorization",token).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(req))).andExpect(status().isCreated()).andExpect(jsonPath("$.data.id").value(id));
        mvc.perform(get("/api/orders/requests/"+req.requestId()).header("Authorization",token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(id));
        mvc.perform(get("/api/orders/requests/"+req.requestId())).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/orders/requests/not-a-uuid").header("Authorization",token)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/orders/requests/"+java.util.UUID.randomUUID()).header("Authorization",token)).andExpect(status().isNotFound());
        mvc.perform(post("/api/orders").header("Authorization",token).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(identified("invalid")))).andExpect(status().isBadRequest());
    }
    @Test void adminProductStatusIsNormalizedBeforeCaseSensitiveQuery() {
        var upper=adminProducts.list(null,null,"ON_SALE",null,0,20,"id,asc");
        var lower=adminProducts.list(null,null," on_sale ",null,0,20,"id,asc");
        assertThat(upper.totalElements()).isPositive();
        assertThat(lower).isEqualTo(upper);
    }
    @Autowired StockMovementRepository movements;
    @Autowired com.catshop.service.StockMovementService stockMovements;
    @Autowired com.catshop.service.admin.AdminInventoryService inventory;
    @Autowired com.catshop.service.admin.AdminOrderService adminOrders;

    @Test void stockLedgerTracksCheckoutAndCancellationExactlyOnce() {
        var order=service.createOrder(member.getId(),request("COD"));
        service.cancelMyOrder(member.getId(),order.id());
        assertThatThrownBy(()->service.cancelMyOrder(member.getId(),order.id())).isInstanceOf(com.catshop.exception.BusinessException.class);
        var history=movements.findByOrderIdOrderByIdAsc(order.id());
        assertThat(history).extracting(StockMovement::getReason).containsExactly("ORDER_PLACED","ORDER_CANCELLED");
        assertThat(history).extracting(StockMovement::getBeforeStock).containsExactly(10,8);
        assertThat(history).extracting(StockMovement::getAfterStock).containsExactly(8,10);
        assertThat(history).extracting(StockMovement::getQuantityDelta).containsExactly(-2,2);
        assertThat(history).allSatisfy(m->{assertThat(m.getActorMemberId()).isEqualTo(member.getId());assertThat(m.getOrderNo()).isEqualTo(order.orderNo());});
    }
    @Test void stockLedgerAdminCancellationRecordsAuthenticatedActor() {
        var order=service.createOrder(member.getId(),request("COD"));
        var context=org.springframework.security.core.context.SecurityContextHolder.getContext();
        context.setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(123L,null,List.of()));
        try {adminOrders.updateStatus(order.id(),new com.catshop.dto.admin.order.UpdateOrderStatusRequest("CANCELLED",null));}
        finally {org.springframework.security.core.context.SecurityContextHolder.clearContext();}
        assertThat(movements.findByOrderIdOrderByIdAsc(order.id()).get(1).getActorMemberId()).isEqualTo(123L);
    }
    @Test void stockLedgerNoopConflictAndSnapshots() {
        adminProducts.updateSku(sku.getId(),new com.catshop.dto.admin.product.UpdateSkuRequest("1kg",BigDecimal.TEN,10,7,true,sku.getVersion()));
        assertThat(movements.count()).isZero();
        var current=skus.findById(sku.getId()).orElseThrow();
        adminProducts.updateSku(sku.getId(),new com.catshop.dto.admin.product.UpdateSkuRequest("1kg",BigDecimal.TEN,12,7,true,current.getVersion()));
        assertThatThrownBy(()->adminProducts.updateSku(sku.getId(),new com.catshop.dto.admin.product.UpdateSkuRequest("1kg",BigDecimal.TEN,15,7,true,current.getVersion()))).hasMessageContaining("已變更");
        product.setName("新名稱");products.saveAndFlush(product);
        var row=inventory.movements(sku.getId(),"ADMIN_ADJUSTMENT",0,1).content().get(0);
        assertThat(row.productName()).isEqualTo("幼貓飼料"); assertThat(row.quantityDelta()).isEqualTo(2);
        assertThat(movements.count()).isEqualTo(1);
    }
    @Test void stockLedgerRollsBackWithStockAndRequiresTransaction() {
        assertThatThrownBy(()->stockMovements.record(sku,0,"SKU_CREATED",null,null)).isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
        assertThatThrownBy(()->new org.springframework.transaction.support.TransactionTemplate(transactionManager).execute(status->{
            var current=skus.findById(sku.getId()).orElseThrow();current.setStock(7);skus.saveAndFlush(current);
            stockMovements.record(current,10,"ADMIN_ADJUSTMENT",member.getId(),null);
            throw new IllegalStateException("rollback");
        })).hasMessage("rollback");
        assertThat(movements.count()).isZero();assertThat(skus.findById(sku.getId()).orElseThrow().getStock()).isEqualTo(10);
    }
    @Test void stockLedgerPagingAndFilters() {
        var order=service.createOrder(member.getId(),request("COD"));service.cancelMyOrder(member.getId(),order.id());
        var first=inventory.movements(sku.getId(),null,0,1);
        assertThat(first.totalElements()).isEqualTo(2);assertThat(first.content().get(0).reason()).isEqualTo("ORDER_CANCELLED");
        assertThat(inventory.movements(sku.getId(),null,1,1).content().get(0).reason()).isEqualTo("ORDER_PLACED");
        assertThat(inventory.movements(sku.getId(),"SKU_CREATED",0,20).content()).isEmpty();
    }
    @Test void stockLedgerRecordsInitialPositiveStockButNotZero() {
        var positive=adminProducts.addSku(product.getId(),new com.catshop.dto.admin.product.CreateSkuRequest("POSITIVE","small",BigDecimal.TEN,5,0));
        adminProducts.addSku(product.getId(),new com.catshop.dto.admin.product.CreateSkuRequest("ZERO","small",BigDecimal.TEN,0,0));
        assertThat(movements.count()).isEqualTo(1);
        var row=inventory.movements(positive.id(),"SKU_CREATED",0,20).content().get(0);
        assertThat(row.beforeStock()).isZero();assertThat(row.afterStock()).isEqualTo(5);
    }
    @Autowired MemberRepository members;
    @Autowired AuthSessionRepository authSessions;
    @Autowired com.catshop.service.AuthSessionService sessionService;
    @Autowired CategoryRepository categories;
    @Autowired ProductRepository products;
    @Autowired ProductSkuRepository skus;
    @Autowired ProductImageRepository images;
    @Autowired CartRepository carts;
    @Autowired CartItemRepository cartItems;
    @Autowired OrderRepository orders;
    @Autowired OrderItemRepository orderItems;
    @Autowired AddressRepository addresses;
    @Autowired OrderService service;
    @Autowired com.catshop.service.ShipmentService shipments;
    @Autowired com.catshop.service.CouponService coupons;
    @Autowired CouponRepository couponRows;

    private com.catshop.dto.admin.CouponRequest couponRequest(String code,String discount,String minimum,int limit,boolean active,Integer version) {
        var now=java.time.LocalDateTime.now(java.time.Clock.systemUTC());
        return new com.catshop.dto.admin.CouponRequest(code,new BigDecimal(discount),new BigDecimal(minimum),now.minusDays(1),now.plusDays(1),limit,active,version);
    }
    private CreateOrderRequest withCoupon(String code) {return new CreateOrderRequest("收件人","0912345678","台北市",null,"COD",code);}
    private void refillCart() {
        cartItems.saveAndFlush(CartItem.builder().cart(cart).sku(skus.findById(sku.getId()).orElseThrow()).quantity(2).unitPrice(BigDecimal.ONE).build());
    }
    private void assertCouponFailure(String code,String reason) {
        assertThatThrownBy(()->coupons.quote(member.getId(),code))
            .isInstanceOfSatisfying(com.catshop.exception.BusinessException.class,e->{
                assertThat(e.getCode()).isEqualTo(reason);
                assertThat(e.getStatus().value()).isEqualTo(400);
            });
    }
    @Test void couponFailureCodesDistinguishReasonsAndCheckoutKeepsStockAndCart() {
        assertCouponFailure("!bad","COUPON_INVALID_FORMAT");
        assertCouponFailure("MISSING","COUPON_NOT_FOUND");
        var c=coupons.save(null,couponRequest("REASONS","100","500",1,true,null));
        assertCouponFailure("REASONS","COUPON_MINIMUM_NOT_MET");
        var row=couponRows.findById(c.id()).orElseThrow();
        row.setMinimumAmount(BigDecimal.ZERO);row.setActive(false);row=couponRows.saveAndFlush(row);
        assertCouponFailure("REASONS","COUPON_INACTIVE");
        row.setActive(true);row.setStartsAt(java.time.LocalDateTime.now(java.time.Clock.systemUTC()).plusHours(1));row=couponRows.saveAndFlush(row);
        assertCouponFailure("REASONS","COUPON_NOT_STARTED");
        row.setStartsAt(java.time.LocalDateTime.now(java.time.Clock.systemUTC()).minusDays(1));
        row.setEndsAt(java.time.LocalDateTime.now(java.time.Clock.systemUTC()).minusMinutes(1));row=couponRows.saveAndFlush(row);
        assertCouponFailure("REASONS","COUPON_EXPIRED");
        assertThatThrownBy(()->service.createOrder(member.getId(),withCoupon("REASONS")))
            .isInstanceOfSatisfying(com.catshop.exception.BusinessException.class,e->assertThat(e.getCode()).isEqualTo("COUPON_EXPIRED"));
        assertThat(orders.count()).isZero();assertThat(cartItems.count()).isEqualTo(1);
        assertThat(skus.findById(sku.getId()).orElseThrow().getStock()).isEqualTo(10);
        row.setEndsAt(java.time.LocalDateTime.now(java.time.Clock.systemUTC()).plusDays(1));row.setMaxUses(2);couponRows.saveAndFlush(row);
        service.createOrder(member.getId(),withCoupon("REASONS"));refillCart();
        assertCouponFailure("REASONS","COUPON_ALREADY_USED");
        row=couponRows.findById(c.id()).orElseThrow();row.setMaxUses(1);couponRows.saveAndFlush(row);
        assertCouponFailure("REASONS","COUPON_EXHAUSTED");
    }
    @Test void couponQuoteUsesCurrentPricesAndDoesNotReserve() {
        coupons.save(null,couponRequest("SAVE100","100","400",1,true,null));
        var quote=coupons.quote(member.getId()," save100 ");
        assertThat(quote.goodsTotal()).isEqualByComparingTo("400");assertThat(quote.discountAmount()).isEqualByComparingTo("100");
        assertThat(quote.shippingFee()).isEqualByComparingTo("80");assertThat(quote.totalAmount()).isEqualByComparingTo("380");
        assertThat(coupons.quote(member.getId(),"SAVE100").totalAmount()).isEqualByComparingTo("380");
        assertThat(orders.count()).isZero();assertThat(cartItems.count()).isEqualTo(1);
    }
    @Test void couponCheckoutSnapshotMemberLimitAndCancellationRelease() {
        var coupon=coupons.save(null,couponRequest("SAVE100","100","0",10,true,null));
        var order=service.createOrder(member.getId(),withCoupon("SAVE100"));
        assertThat(order.couponCode()).isEqualTo("SAVE100");assertThat(order.discountAmount()).isEqualByComparingTo("100");assertThat(order.totalAmount()).isEqualByComparingTo("380");
        refillCart();
        assertThatThrownBy(()->service.createOrder(member.getId(),withCoupon("SAVE100"))).hasMessageContaining("限用一次");
        service.cancelMyOrder(member.getId(),order.id());
        coupons.save(coupon.id(),couponRequest("SAVE100","150","0",10,true,coupon.version()));
        assertThat(service.getMyOrder(member.getId(),order.id()).discountAmount()).isEqualByComparingTo("100");
        assertThat(service.createOrder(member.getId(),withCoupon("SAVE100")).discountAmount()).isEqualByComparingTo("150");
    }
    @Test void couponRejectsInvalidThresholdExpiryInactiveAndStaleEdit() {
        assertThatThrownBy(()->coupons.quote(member.getId(),"MISSING")).hasMessageContaining("不存在");
        var coupon=coupons.save(null,couponRequest("MIN500","100","500",10,true,null));
        assertThatThrownBy(()->coupons.quote(member.getId(),"MIN500")).hasMessageContaining("門檻");
        coupons.save(coupon.id(),couponRequest("MIN500","100","0",10,false,coupon.version()));
        assertThatThrownBy(()->coupons.quote(member.getId(),"MIN500")).hasMessageContaining("停用");
        assertThatThrownBy(()->coupons.save(coupon.id(),couponRequest("MIN500","100","0",10,true,coupon.version()))).hasMessageContaining("已變更");
        var entity=couponRows.findById(coupon.id()).orElseThrow();entity.setActive(true);entity.setEndsAt(java.time.LocalDateTime.now(java.time.Clock.systemUTC()).minusMinutes(1));couponRows.saveAndFlush(entity);
        assertThatThrownBy(()->coupons.quote(member.getId(),"MIN500")).hasMessageContaining("到期");
        assertThatThrownBy(()->coupons.quote(member.getId(),"!bad")).hasMessageContaining("格式");
    }
    @Test void couponClampsDiscountToGoodsAndPreservesShippingPolicy() {
        coupons.save(null,couponRequest("FREEGOODS","9999","0",10,true,null));
        var order=service.createOrder(member.getId(),withCoupon("FREEGOODS"));
        assertThat(order.discountAmount()).isEqualByComparingTo("400");assertThat(order.totalAmount()).isEqualByComparingTo("80");
    }
    @Test void failedCheckoutRollsBackCouponUsageStockAndCart() {
        coupons.save(null,couponRequest("ROLLBACK","100","0",1,true,null));
        assertThatThrownBy(()->new org.springframework.transaction.support.TransactionTemplate(transactionManager).execute(status->{
            service.createOrder(member.getId(),withCoupon("ROLLBACK"));throw new IllegalStateException("rollback");
        })).hasMessage("rollback");
        assertThat(orders.count()).isZero();assertThat(cartItems.count()).isEqualTo(1);assertThat(skus.findById(sku.getId()).orElseThrow().getStock()).isEqualTo(10);
        assertThat(coupons.quote(member.getId(),"ROLLBACK").discountAmount()).isEqualByComparingTo("100");
    }
    @Autowired OrderShipmentRepository shipmentRows;

    @Test void shipmentRequiresMetadataAndOwnedRead() {
        var order = service.createOrder(member.getId(),request("COD"));
        assertThat(shipments.get(order.id(),member.getId())).isNull();
        assertThatThrownBy(() -> shipments.get(order.id(),-1L)).isInstanceOf(com.catshop.exception.BusinessException.class);
        assertThatThrownBy(() -> shipments.ship(order.id(),123L,new com.catshop.dto.admin.order.CreateShipmentRequest(" ","123"))).isInstanceOf(com.catshop.exception.BusinessException.class);
        assertThatThrownBy(() -> shipments.ship(order.id(),123L,new com.catshop.dto.admin.order.CreateShipmentRequest("物流","123\n456"))).isInstanceOf(com.catshop.exception.BusinessException.class);
        assertThatThrownBy(() -> adminOrders.updateStatus(order.id(),new com.catshop.dto.admin.order.UpdateOrderStatusRequest("SHIPPED",null))).hasMessageContaining("出貨登錄");
        assertThat(orders.findById(order.id()).orElseThrow().getStatus()).isEqualTo("PENDING");
        var record = shipments.ship(order.id(),123L,new com.catshop.dto.admin.order.CreateShipmentRequest(" 黑貓 "," TEST123 "));
        assertThat(record.carrier()).isEqualTo("黑貓");assertThat(record.trackingNumber()).isEqualTo("TEST123");assertThat(record.shippedAt()).isNotNull();
        assertThat(shipmentRows.findByOrderId(order.id()).orElseThrow().getActorMemberId()).isEqualTo(123L);
        assertThat(orders.findById(order.id()).orElseThrow().getStatus()).isEqualTo("SHIPPED");
        assertThatThrownBy(() -> shipments.ship(order.id(),123L,new com.catshop.dto.admin.order.CreateShipmentRequest("宅配","REPLACE"))).isInstanceOf(com.catshop.exception.BusinessException.class);
        assertThatThrownBy(() -> service.cancelMyOrder(member.getId(),order.id())).isInstanceOf(com.catshop.exception.BusinessException.class);
        assertThat(shipmentRows.count()).isEqualTo(1);assertThat(skus.findById(sku.getId()).orElseThrow().getStock()).isEqualTo(8);
    }

    @Test void shipmentRejectsCancelledAndUnpaidNonCodOrders() {
        var order = service.createOrder(member.getId(),request("MOCK"));
        var entity = orders.findById(order.id()).orElseThrow();entity.setStatus("PENDING");orders.saveAndFlush(entity);
        assertThatThrownBy(() -> shipments.ship(order.id(),123L,new com.catshop.dto.admin.order.CreateShipmentRequest("宅配","123"))).isInstanceOf(com.catshop.exception.BusinessException.class);
        service.cancelMyOrder(member.getId(),order.id());
        assertThatThrownBy(() -> shipments.ship(order.id(),123L,new com.catshop.dto.admin.order.CreateShipmentRequest("宅配","123"))).isInstanceOf(com.catshop.exception.BusinessException.class);
        assertThat(shipmentRows.count()).isZero();
    }
    @Autowired PasswordEncoder encoder;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired com.catshop.service.admin.AdminProductService adminProducts;
    @Autowired com.catshop.service.AddressService addressService;
    @SpyBean ShippingService shipping;
    Member member;
    Product product;
    ProductSku sku;
    Cart cart;

    @BeforeEach
    void setup() {
        couponRows.deleteAll();
        shipmentRows.deleteAll();
        movements.deleteAll();
        reset(shipping);
        authSessions.deleteAll();
        orderItems.deleteAll(); orders.deleteAll(); cartItems.deleteAll(); carts.deleteAll();
        images.deleteAll(); skus.deleteAll(); products.deleteAll(); categories.deleteAll(); addresses.deleteAll(); members.deleteAll();
        member = members.saveAndFlush(Member.builder().email("member@example.com").name("測試會員")
                .passwordHash(encoder.encode("password123")).build());
        Category category = categories.saveAndFlush(Category.builder().name("飼料").slug("food").build());
        product = products.saveAndFlush(Product.builder().category(category).name("幼貓飼料").slug("kitten-food")
                .basePrice(new BigDecimal("200")).build());
        sku = skus.saveAndFlush(ProductSku.builder().product(product).skuCode("FOOD-1").specName("1kg")
                .price(new BigDecimal("200")).stock(10).build());
        cart = carts.saveAndFlush(Cart.builder().member(member).build());
        cartItems.saveAndFlush(CartItem.builder().cart(cart).sku(sku).quantity(2).unitPrice(new BigDecimal("150")).build());
    }

    private CreateOrderRequest request(String method) {
        return new CreateOrderRequest("收件人", "0912345678", "台北市測試路 1 號", null, method);
    }

    @Test
    void cleanupDeletesOnlyExpiredSessionsInBoundedBatches() {
        var cutoff = java.time.LocalDateTime.now(java.time.Clock.systemUTC());
        for (int i = 0; i < 5; i++) {
            var session = new com.catshop.entity.AuthSession();
            session.setId("cleanup-" + i); session.setMemberId(member.getId()); session.setTokenHash("a".repeat(64));
            session.setExpiresAt(i < 3 ? cutoff.minusSeconds(1) : cutoff.plusDays(1));
            session.setRevoked(i == 1 || i == 4);
            authSessions.saveAndFlush(session);
        }
        // Use the Spring proxy for real per-batch transactions, not a manually created service instance.
        assertThat(cleanupService.deleteExpiredBatch(cutoff, 2)).isEqualTo(2);
        assertThat(cleanupService.deleteExpiredBatch(cutoff, 2)).isEqualTo(1);
        assertThat(cleanupService.deleteExpiredBatch(cutoff, 2)).isZero();
        assertThat(authSessions.findAll()).extracting(com.catshop.entity.AuthSession::getId)
                .containsExactlyInAnyOrder("cleanup-3", "cleanup-4");
        assertThat(sessionService.active("cleanup-3", member.getId())).isTrue();
        assertThat(sessionService.active("cleanup-4", member.getId())).isFalse();
        assertThat(sessionService.active("cleanup-0", member.getId())).isFalse();
    }

    @Autowired com.catshop.service.AuthSessionCleanupService cleanupService;

    @Test
    void cleanupRechecksExpiryAndIncludesExactExpiryBoundary() {
        var cutoff = java.time.LocalDateTime.of(2026, 1, 1, 0, 0);
        var session = new com.catshop.entity.AuthSession();
        session.setId("expiry-boundary"); session.setMemberId(member.getId()); session.setTokenHash("b".repeat(64));
        session.setExpiresAt(cutoff); authSessions.saveAndFlush(session);
        var ids = authSessions.findExpiredIds(cutoff, org.springframework.data.domain.PageRequest.of(0, 10));
        assertThat(ids).containsExactly("expiry-boundary");
        session.setExpiresAt(cutoff.plusDays(1)); authSessions.saveAndFlush(session);
        new org.springframework.transaction.support.TransactionTemplate(transactionManager).execute(status -> {
            assertThat(authSessions.deleteExpiredIds(ids, cutoff)).isZero();
            return null;
        });
        assertThat(authSessions.findById("expiry-boundary")).isPresent();
        assertThat(cleanupService.deleteExpiredBatch(cutoff.plusDays(1), 1)).isEqualTo(1);
    }

    @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;
    @Autowired com.catshop.service.CartService cartService;

    @Test
    void concurrentFirstCartReadsCreateOneCartWithoutFailures() throws Exception {
        cartItems.deleteAll(); carts.deleteAll();
        var pool = Executors.newFixedThreadPool(8);
        var ready = new CountDownLatch(8); var start = new CountDownLatch(1);
        try {
            var futures = new java.util.ArrayList<Future<Long>>();
            for (int i = 0; i < 8; i++) futures.add(pool.submit(() -> {
                ready.countDown(); if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("start timeout");
                return cartService.getCart(member.getId()).id();
            }));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue(); start.countDown();
            var ids = new java.util.HashSet<Long>();
            for (var future : futures) ids.add(future.get(15, TimeUnit.SECONDS));
            assertThat(ids).hasSize(1); assertThat(carts.count()).isEqualTo(1);
        } finally { start.countDown(); pool.shutdownNow(); }
    }

    private <T> List<T> parallel(int count, Callable<T> action) throws Exception {
        var pool = Executors.newFixedThreadPool(count);
        var ready = new CountDownLatch(count); var start = new CountDownLatch(1);
        try {
            var futures = new java.util.ArrayList<Future<T>>();
            for (int i = 0; i < count; i++) futures.add(pool.submit(() -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("start timeout");
                return action.call();
            }));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue(); start.countDown();
            var results = new java.util.ArrayList<T>();
            for (var future : futures) results.add(future.get(30, TimeUnit.SECONDS));
            return results;
        } finally { start.countDown(); pool.shutdownNow(); assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue(); }
    }

    @Test void concurrentAddsToExistingItemAccumulateWithoutLostQuantity() throws Exception {
        var results = parallel(8, () -> cartService.addItem(member.getId(), new com.catshop.dto.cart.AddCartItemRequest(sku.getId(), 1)).totalQuantity());
        assertThat(results).containsExactlyInAnyOrder(3, 4, 5, 6, 7, 8, 9, 10);
        assertThat(cartItems.count()).isEqualTo(1);
        assertThat(cartService.getCart(member.getId()).totalQuantity()).isEqualTo(10);
    }

    @Test void concurrentAddsCreateOnlyOneItemForTheSameSku() throws Exception {
        cartItems.deleteAll();
        parallel(8, () -> cartService.addItem(member.getId(), new com.catshop.dto.cart.AddCartItemRequest(sku.getId(), 1)));
        assertThat(cartItems.count()).isEqualTo(1);
        assertThat(cartService.getCart(member.getId()).totalQuantity()).isEqualTo(8);
    }

    @Test void concurrentAddsRejectOnlyExcessStockAndKeepAcceptedQuantity() throws Exception {
        sku.setStock(5); skus.saveAndFlush(sku);
        var results = parallel(8, () -> {
            try { cartService.addItem(member.getId(), new com.catshop.dto.cart.AddCartItemRequest(sku.getId(), 1)); return "saved"; }
            catch (com.catshop.exception.BusinessException e) {
                assertThat(e.getStatus()).isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST);
                assertThat(e.getMessage()).contains("庫存不足"); return "stock";
            }
        });
        assertThat(results.stream().filter("saved"::equals).count()).isEqualTo(3);
        assertThat(results.stream().filter("stock"::equals).count()).isEqualTo(5);
        assertThat(cartService.getCart(member.getId()).totalQuantity()).isEqualTo(5);
        assertThat(cartItems.count()).isEqualTo(1);
    }

    @Test void simultaneousCheckoutsOfSameCartCreateOneOrder() throws Exception {
        var results = parallel(2, () -> {
            try { service.createOrder(member.getId(), request("COD")); return "saved"; }
            catch (com.catshop.exception.BusinessException e) {
                assertThat(e.getStatus()).isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST);
                assertThat(e.getMessage()).contains("購物車為空"); return "empty";
            }
        });
        assertThat(results).containsExactlyInAnyOrder("saved", "empty");
        assertThat(orders.count()).isEqualTo(1); assertThat(orderItems.count()).isEqualTo(1);
        assertThat(skus.findById(sku.getId()).orElseThrow().getStock()).isEqualTo(8);
        assertThat(cartItems.count()).isZero();
    }

    @Test void checkoutKeepsCartLockedAgainstQuantityChangeAndClear() throws Exception {
        var insideCheckout = new CountDownLatch(1); var release = new CountDownLatch(1);
        doAnswer(invocation -> {
            insideCheckout.countDown();
            if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("checkout timeout");
            return new BigDecimal("80");
        }).when(shipping).quote(any());
        Long itemId = cartItems.findByCartIdOrderByIdAsc(cart.getId()).get(0).getId();
        var pool = Executors.newFixedThreadPool(3);
        try {
            var checkout = pool.submit(() -> service.createOrder(member.getId(), snapshotRequest()));
            assertThat(insideCheckout.await(10, TimeUnit.SECONDS)).isTrue();
            var started = new CountDownLatch(2);
            var update = pool.submit(() -> {
                started.countDown();
                try { cartService.updateItem(member.getId(), itemId, new com.catshop.dto.cart.UpdateCartItemRequest(3)); return "unexpected"; }
                catch (com.catshop.exception.BusinessException e) { assertThat(e.getStatus().value()).isEqualTo(404); return "removed"; }
            });
            var clear = pool.submit(() -> { started.countDown(); cartService.clear(member.getId()); return true; });
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> update.get(150, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            assertThatThrownBy(() -> clear.get(150, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            release.countDown();
            assertThat(checkout.get(20, TimeUnit.SECONDS).items().get(0).quantity()).isEqualTo(2);
            assertThat(update.get(20, TimeUnit.SECONDS)).isEqualTo("removed"); assertThat(clear.get(20, TimeUnit.SECONDS)).isTrue();
            assertThat(orders.count()).isEqualTo(1); assertThat(cartItems.count()).isZero();
            assertThat(skus.findById(sku.getId()).orElseThrow().getStock()).isEqualTo(8);
        } finally { release.countDown(); pool.shutdownNow(); assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue(); }
    }

    @Test void repricingReadDoesNotOverwriteConcurrentQuantityUpdate() throws Exception {
        sku.setPrice(new BigDecimal("300")); skus.saveAndFlush(sku);
        var readReady = new CountDownLatch(1); var release = new CountDownLatch(1);
        Long itemId = cartItems.findByCartIdOrderByIdAsc(cart.getId()).get(0).getId();
        var pool = Executors.newFixedThreadPool(2);
        try {
            var read = pool.submit(() -> new org.springframework.transaction.support.TransactionTemplate(transactionManager).execute(status -> {
                var response = cartService.getCart(member.getId()); readReady.countDown();
                try { if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("read timeout"); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
                return response;
            }));
            assertThat(readReady.await(10, TimeUnit.SECONDS)).isTrue();
            var update = pool.submit(() -> cartService.updateItem(member.getId(), itemId, new com.catshop.dto.cart.UpdateCartItemRequest(3)));
            assertThatThrownBy(() -> update.get(150, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            release.countDown();
            assertThat(read.get(20, TimeUnit.SECONDS).totalAmount()).isEqualByComparingTo("600");
            assertThat(update.get(20, TimeUnit.SECONDS).totalAmount()).isEqualByComparingTo("900");
            assertThat(cartService.getCart(member.getId()).totalQuantity()).isEqualTo(3);
        } finally { release.countDown(); pool.shutdownNow(); assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue(); }
    }

    @Test void quantityAdditionCannotOverflowToNegative() {
        sku.setStock(Integer.MAX_VALUE); skus.saveAndFlush(sku);
        var item = cartItems.findByCartIdOrderByIdAsc(cart.getId()).get(0); item.setQuantity(Integer.MAX_VALUE); cartItems.saveAndFlush(item);
        assertThatThrownBy(() -> cartService.addItem(member.getId(), new com.catshop.dto.cart.AddCartItemRequest(sku.getId(), 1)))
                .isInstanceOf(com.catshop.exception.BusinessException.class).hasMessageContaining("庫存不足");
        assertThat(cartItems.findById(item.getId()).orElseThrow().getQuantity()).isEqualTo(Integer.MAX_VALUE);
    }

    private org.springframework.test.web.servlet.MvcResult sessionLogin() throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"member@example.com\",\"password\":\"password123\"}"))
                .andExpect(status().isOk()).andReturn();
    }
    private String bearer(org.springframework.test.web.servlet.MvcResult result) throws Exception {
        return "Bearer " + json.readTree(result.getResponse().getContentAsString()).at("/data/token").asText();
    }
    @Test void refreshRotatesCookieAndReplayRevokesAccess() throws Exception {
        var login = sessionLogin();
        var cookie = login.getResponse().getCookie("catshop_refresh");
        assertThat(login.getResponse().getHeader("Set-Cookie")).contains("HttpOnly", "SameSite=Strict", "Path=/api/auth");
        assertThat(login.getResponse().getContentAsString()).doesNotContain(cookie.getValue());
        var refresh = mvc.perform(post("/api/auth/refresh").cookie(cookie)).andExpect(status().isOk()).andReturn();
        assertThat(refresh.getResponse().getCookie("catshop_refresh").getValue()).isNotEqualTo(cookie.getValue());
        mvc.perform(get("/api/members/me").header("Authorization", bearer(refresh))).andExpect(status().isOk());
        mvc.perform(post("/api/auth/refresh").cookie(cookie)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/members/me").header("Authorization", bearer(refresh))).andExpect(status().isUnauthorized());
    }
    @Test void forgedRefreshDoesNotRevokeRealSession() throws Exception {
        var login = sessionLogin(); var cookie = login.getResponse().getCookie("catshop_refresh");
        var forged = new jakarta.servlet.http.Cookie("catshop_refresh", cookie.getValue().substring(0, 37) + "A".repeat(43));
        mvc.perform(post("/api/auth/refresh").cookie(forged)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/members/me").header("Authorization", bearer(login))).andExpect(status().isOk());
        mvc.perform(post("/api/auth/refresh").cookie(cookie)).andExpect(status().isOk());
    }
    @Test void logoutRevokesOnlyCurrentSessionAndClearsCookie() throws Exception {
        var first = sessionLogin(); var second = sessionLogin();
        mvc.perform(post("/api/auth/logout").cookie(first.getResponse().getCookie("catshop_refresh")))
                .andExpect(status().isOk()).andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("Max-Age=0")));
        mvc.perform(get("/api/members/me").header("Authorization", bearer(first))).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/members/me").header("Authorization", bearer(second))).andExpect(status().isOk());
    }
    @Test void passwordChangeRevokesAllSessions() throws Exception {
        var first = sessionLogin(); var second = sessionLogin();
        mvc.perform(put("/api/members/me/password").header("Authorization", bearer(first)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"password123\",\"newPassword\":\"newpassword123\"}"))
                .andExpect(status().isOk());
        for (var result : List.of(first, second)) {
            mvc.perform(get("/api/members/me").header("Authorization", bearer(result))).andExpect(status().isUnauthorized());
            mvc.perform(post("/api/auth/refresh").cookie(result.getResponse().getCookie("catshop_refresh")))
                    .andExpect(status().isUnauthorized());
        }
    }
    @Test void missingExpiredAndCrossSiteRefreshAreRejected() throws Exception {
        mvc.perform(post("/api/auth/refresh")).andExpect(status().isUnauthorized());
        var login = sessionLogin(); var cookie = login.getResponse().getCookie("catshop_refresh");
        mvc.perform(post("/api/auth/refresh").cookie(cookie).header("Origin", "https://evil.example"))
                .andExpect(status().isForbidden());
        var session = authSessions.findAll().get(0);
        session.setExpiresAt(java.time.LocalDateTime.now(java.time.Clock.systemUTC()).minusSeconds(1));
        authSessions.saveAndFlush(session);
        mvc.perform(post("/api/auth/refresh").cookie(cookie)).andExpect(status().isUnauthorized());
    }
    @Test void concurrentRotationAllowsAtMostOneSuccess() throws Exception {
        var login = sessionLogin(); var token = login.getResponse().getCookie("catshop_refresh").getValue();
        var barrier = new CyclicBarrier(2); var pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Boolean> rotate = () -> { barrier.await(); try { sessionService.refresh(token); return true; }
                catch (com.catshop.exception.BusinessException e) { return false; } };
            var a = pool.submit(rotate); var b = pool.submit(rotate);
            assertThat(List.of(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS))).containsExactlyInAnyOrder(true, false);
            assertThat(authSessions.findAll().get(0).isRevoked()).isTrue();
        } finally { pool.shutdownNow(); }
    }

    @Test void checkoutUsesCurrentPriceAndFreezesSnapshots() {
        OrderResponse result = service.createOrder(member.getId(), request("COD"));
        assertThat(result.totalAmount()).isEqualByComparingTo("480");
        assertThat(result.paymentMethod()).isEqualTo("COD");
        assertThat(result.items().get(0).unitPrice()).isEqualByComparingTo("200");
        assertThat(skus.findById(sku.getId()).orElseThrow().getStock()).isEqualTo(8);
        assertThat(cartItems.count()).isZero();
        product.setName("改名商品"); products.saveAndFlush(product);
        sku = skus.findById(sku.getId()).orElseThrow(); sku.setPrice(new BigDecimal("900")); skus.saveAndFlush(sku);
        OrderResponse historical = service.getMyOrder(member.getId(), result.id());
        assertThat(historical.items().get(0).productName()).isEqualTo("幼貓飼料");
        assertThat(historical.items().get(0).unitPrice()).isEqualByComparingTo("200");
    }

    @Test void mockPaymentIsPaidAndCanCancelBeforeShipmentExactlyOnce() {
        OrderResponse result = service.createOrder(member.getId(), request("MOCK"));
        assertThat(result.status()).isEqualTo("PAID");
        assertThat(service.cancelMyOrder(member.getId(), result.id()).status()).isEqualTo("CANCELLED");
        assertThatThrownBy(() -> service.cancelMyOrder(member.getId(), result.id())).hasMessageContaining("尚未出貨");
        assertThat(skus.findById(sku.getId()).orElseThrow().getStock()).isEqualTo(10);
    }

    @Test void shippedOrderCannotBeCancelled() {
        OrderResponse result = service.createOrder(member.getId(), request("COD"));
        Order order = orders.findById(result.id()).orElseThrow(); order.setStatus("SHIPPED"); orders.saveAndFlush(order);
        assertThatThrownBy(() -> service.cancelMyOrder(member.getId(), result.id())).hasMessageContaining("尚未出貨");
        assertThat(skus.findById(sku.getId()).orElseThrow().getStock()).isEqualTo(8);
    }

    @Test void outOfStockRollsBackAndKeepsCart() {
        sku.setStock(1); skus.saveAndFlush(sku);
        assertThatThrownBy(() -> service.createOrder(member.getId(), request("COD"))).hasMessageContaining("庫存不足");
        assertThat(orders.count()).isZero(); assertThat(cartItems.count()).isEqualTo(1);
        assertThat(skus.findById(sku.getId()).orElseThrow().getStock()).isEqualTo(1);
    }

    @Test void offShelfProductCannotCheckoutOrLeakThroughPublicDetail() throws Exception {
        product.setIsActive(false); products.saveAndFlush(product);
        assertThatThrownBy(() -> service.createOrder(member.getId(), request("COD"))).hasMessageContaining("下架");
        mvc.perform(get("/api/products/" + product.getId())).andExpect(status().isNotFound());
        mvc.perform(get("/api/products")).andExpect(jsonPath("$.data.totalElements").value(0));
        assertThat(orders.count()).isZero(); assertThat(cartItems.count()).isEqualTo(1);
    }

    @Test void otherMemberCannotReadOrCancelOrder() {
        OrderResponse result = service.createOrder(member.getId(), request("COD"));
        assertThatThrownBy(() -> service.getMyOrder(member.getId() + 100, result.id())).hasMessageContaining("找不到訂單");
        assertThatThrownBy(() -> service.cancelMyOrder(member.getId() + 100, result.id())).hasMessageContaining("找不到訂單");
        assertThat(skus.findById(sku.getId()).orElseThrow().getStock()).isEqualTo(8);
    }

    @Test void concurrentCheckoutsCannotOversellAndLosingOrderRollsBack() throws Exception {
        sku.setStock(2); skus.saveAndFlush(sku);
        Member other = members.saveAndFlush(Member.builder().email("other@example.com").name("另一會員").passwordHash("hash").build());
        Cart otherCart = carts.saveAndFlush(Cart.builder().member(other).build());
        cartItems.saveAndFlush(CartItem.builder().cart(otherCart).sku(sku).quantity(2).unitPrice(sku.getPrice()).build());
        CyclicBarrier barrier = new CyclicBarrier(2);
        doAnswer(invocation -> { barrier.await(10, TimeUnit.SECONDS); return new BigDecimal("80"); }).when(shipping).quote(any());
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Boolean>> results = List.of(pool.submit(() -> checkoutSucceeded(member.getId())), pool.submit(() -> checkoutSucceeded(other.getId())));
            int successes = 0;
            for (Future<Boolean> result : results) if (result.get(20, TimeUnit.SECONDS)) successes++;
            assertThat(successes).isEqualTo(1);
            assertThat(skus.findById(sku.getId()).orElseThrow().getStock()).isZero();
            assertThat(orders.count()).isEqualTo(1); assertThat(orderItems.count()).isEqualTo(1);
            assertThat(cartItems.count()).isEqualTo(1);
        } finally { pool.shutdownNow(); }
    }

    private boolean checkoutSucceeded(Long id) {
        try { service.createOrder(id, request("COD")); return true; }
        catch (org.springframework.dao.OptimisticLockingFailureException ex) { return false; }
    }

    @Test void authSecurityAndApiContracts() throws Exception {
        mvc.perform(get("/api/health")).andExpect(status().isOk());
        mvc.perform(get("/api/cart")).andExpect(status().isUnauthorized());
        String response = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"member@example.com\",\"password\":\"password123\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String token = json.readTree(response).path("data").path("token").asText();
        mvc.perform(get("/api/admin/orders").header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
        mvc.perform(post("/api/orders").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"receiverName\":\"收件人\",\"receiverPhone\":\"123\",\"shippingAddress\":\"地址\",\"paymentMethod\":\"CARD\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/products").param("sort", "price,asc")).andExpect(status().isOk());
        mvc.perform(get("/api/products").param("sort", "password,asc")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/products").param("size", "1000")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/categories/" + product.getCategory().getId())).andExpect(status().isOk());
        mvc.perform(get("/api/categories")).andExpect(jsonPath("$.data[0].children").isArray());
    }

    private String login() throws Exception {
        String response = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"member@example.com\",\"password\":\"password123\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + json.readTree(response).path("data").path("token").asText();
    }

    @Test void completeHttpCheckoutAndPatchCartContract() throws Exception {
        String authorization = login();
        Long itemId = cartItems.findByCartIdOrderByIdAsc(cart.getId()).get(0).getId();
        mvc.perform(patch("/api/cart/items/" + itemId).header("Authorization", authorization)
                .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":3}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.totalAmount").value(600));
        String response = mvc.perform(post("/api/orders").header("Authorization", authorization)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request("MOCK"))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("OK")).andExpect(jsonPath("$.data.status").value("PAID"))
                .andReturn().getResponse().getContentAsString();
        Long id = json.readTree(response).path("data").path("id").asLong();
        mvc.perform(get("/api/orders/" + id).header("Authorization", authorization)).andExpect(status().isOk());
        mvc.perform(post("/api/orders/" + id + "/cancel").header("Authorization", authorization))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("CANCELLED"));
        assertThat(skus.findById(sku.getId()).orElseThrow().getStock()).isEqualTo(10);
    }

    @Test void roleAndDisabledAccountChangesApplyToExistingToken() throws Exception {
        String authorization = login();
        member.setRole("ADMIN"); members.saveAndFlush(member);
        mvc.perform(get("/api/admin/orders").header("Authorization", authorization)).andExpect(status().isOk());
        member.setRole("CUSTOMER"); members.saveAndFlush(member);
        mvc.perform(get("/api/admin/orders").header("Authorization", authorization)).andExpect(status().isForbidden());
        member.setStatus("DISABLED"); members.saveAndFlush(member);
        mvc.perform(get("/api/members/me").header("Authorization", authorization)).andExpect(status().isUnauthorized());
    }

    @Test void staleAdminStockEditCannotOverwriteCompletedPurchase() {
        Integer oldVersion = sku.getVersion();
        service.createOrder(member.getId(), request("COD"));
        var stale = new com.catshop.dto.admin.product.UpdateSkuRequest("1kg", new BigDecimal("200"), 10, 0, true, oldVersion);
        assertThatThrownBy(() -> adminProducts.updateSku(sku.getId(), stale)).hasMessageContaining("已變更");
        assertThat(skus.findById(sku.getId()).orElseThrow().getStock()).isEqualTo(8);
    }

    @Test void rootCategoryIncludesDescendantProductsAndSortsPrices() throws Exception {
        Category parent = product.getCategory();
        Category child = categories.saveAndFlush(Category.builder().name("乾糧").slug("dry").parent(parent).build());
        product.setCategory(child); products.saveAndFlush(product);
        products.saveAndFlush(Product.builder().category(parent).name("主食罐").slug("wet-food").basePrice(new BigDecimal("50")).build());
        mvc.perform(get("/api/products").param("categoryId", parent.getId().toString()).param("sort", "price,asc"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.content[0].name").value("主食罐"));
        // Remove the child fixture now; the shared cleanup does not depend on DB cascade behavior.
        product.setCategory(parent); products.saveAndFlush(product); categories.delete(child);
    }

    @Test void registrationAndInvalidQuantityReturnSpecEnvelope() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"new@example.com\",\"name\":\"新會員\",\"password\":\"password123\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.data.member.role").value("CUSTOMER"));
        mvc.perform(post("/api/cart/items").header("Authorization", login()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"skuId\":" + sku.getId() + ",\"quantity\":0}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.errors[0].reason").exists());
    }

    @Test void addressDefaultCanBeUnsetAndOtherMembersCannotModifyIt() throws Exception {
        var request = new com.catshop.dto.address.AddressRequest("收件人", "0912345678", "台北市", "", "測試路", "", "100", true);
        var saved = addressService.create(member.getId(), request);
        assertThat(saved.isDefault()).isTrue();
        assertThat(addressService.detail(member.getId(),saved.id())).isEqualTo(saved);
        assertThatThrownBy(()->addressService.detail(member.getId()+100,saved.id())).hasMessageContaining("找不到地址");
        mvc.perform(get("/api/members/me/addresses/"+saved.id())).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/members/me/addresses/"+saved.id()).header("Authorization",login()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(saved.id()))
                .andExpect(jsonPath("$.data.member").doesNotExist()).andExpect(jsonPath("$.data.memberId").doesNotExist());
        mvc.perform(get("/api/members/me/addresses/0").header("Authorization",login())).andExpect(status().isBadRequest());
        mvc.perform(get("/api/members/me/addresses/9223372036854775807").header("Authorization",login())).andExpect(status().isNotFound());
        var update = new com.catshop.dto.address.AddressRequest("收件人", "0912345678", "台北市", "", "測試路", "", "100", false);
        assertThat(addressService.update(member.getId(), saved.id(), update).isDefault()).isFalse();
        assertThatThrownBy(() -> addressService.update(member.getId() + 100, saved.id(), update)).hasMessageContaining("找不到地址");
        assertThatThrownBy(() -> addressService.delete(member.getId() + 100, saved.id())).hasMessageContaining("找不到地址");
    }

    @Test void memberOrderPagingIsBounded() throws Exception {
        var token=login();
        for(String query:List.of("?page=-1","?size=0","?size=101","?page=2147483648"))
            mvc.perform(get("/api/orders"+query).header("Authorization",token)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/orders?page=0&size=1").header("Authorization",token)).andExpect(status().isOk());
    }
    @Test void duplicateListParametersAreRejectedAfterAuthorization() throws Exception {
        String customer=login();
        mvc.perform(get("/api/admin/products").param("page","0","1")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/products").param("page","0","1").header("Authorization",customer)).andExpect(status().isForbidden());
        for(String path:List.of("/api/products","/api/orders","/api/wishlist"))
            mvc.perform(get(path).param("page","0","1").header("Authorization",customer))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("DUPLICATE_QUERY_PARAMETER"));
        member.setRole("ADMIN");members.saveAndFlush(member);
        String admin=login();
        for(String path:List.of("/api/admin/products","/api/admin/orders","/api/admin/categories","/api/admin/coupons","/api/admin/members",
                "/api/admin/members/"+member.getId()+"/history","/api/admin/inventory/low-stock","/api/admin/inventory/movements"))
            mvc.perform(get(path).param("size","20","50").header("Authorization",admin))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("DUPLICATE_QUERY_PARAMETER"));
        mvc.perform(get("/api/admin/products").param("keyword","a","a").header("Authorization",admin)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/admin/products").param("sort","name,asc","id,desc").header("Authorization",admin)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/admin/products").param("page","0").param("size","20").header("Authorization",admin)).andExpect(status().isOk());
        mvc.perform(get("/api/members/me").param("ignored","a","b").header("Authorization",admin)).andExpect(status().isOk());
    }
}
