package com.catshop.integration;

import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.Duration;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** Opt-in: mvn -Psqlserver-it verify. Unlike unit tests this uses Flyway, validate and real HTTP/JDBC. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.profiles.active=test", "spring.jpa.hibernate.ddl-auto=validate", "spring.flyway.enabled=true",
        "spring.flyway.baseline-on-migrate=false", "spring.jpa.database-platform=org.hibernate.dialect.SQLServerDialect",
        "app.auth.cookie-secure=false", "logging.level.org.hibernate.SQL=warn", "app.auth.login-limit=1000",
        "app.auth.cleanup.enabled=false",
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "spring.jpa.properties.hibernate.query.fail_on_pagination_over_collection_fetch=true",
        "logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener=warn",
        "app.cors.allowed-origins=http://localhost:5173,http://127.0.0.1:5173,http://127.0.0.1:5175"
})
class SqlServerCheckoutIT {
    private com.catshop.dto.order.CheckoutAmounts confirmedAmounts(JsonNode q) {
        return new com.catshop.dto.order.CheckoutAmounts(q.path("goodsTotal").decimalValue(),q.path("discountAmount").decimalValue(),
                q.path("shippingFee").decimalValue(),q.path("totalAmount").decimalValue(),q.path("priceSnapshot").asText());
    }
    @Autowired com.catshop.service.CouponService requestCoupons;

    @Test void requestIdentityConcurrencyRollbackOwnershipCouponAndUniqueIndex() throws Exception {
        String prefix = "rid-" + UUID.randomUUID().toString().substring(0,8);
        var category = categories.saveAndFlush(com.catshop.entity.Category.builder().name(prefix).slug(prefix).build());
        var product = products.saveAndFlush(com.catshop.entity.Product.builder().category(category).name(prefix).slug(prefix).basePrice(java.math.BigDecimal.TEN).build());
        var sku = skus.saveAndFlush(com.catshop.entity.ProductSku.builder().product(product).skuCode(prefix).specName("small").price(java.math.BigDecimal.TEN).stock(10).build());
        var memberIds = new ArrayList<Long>();
        Long couponId = null;
        try {
            var now = java.time.LocalDateTime.now(java.time.Clock.systemUTC());
            var coupon = requestCoupons.save(null, new com.catshop.dto.admin.CouponRequest(
                    prefix.toUpperCase(Locale.ROOT), java.math.BigDecimal.ONE, java.math.BigDecimal.ZERO,
                    now.minusDays(1), now.plusDays(1), 1, true, null));
            couponId = coupon.id();
            var a = data(request("POST","/api/auth/register",Map.of("email",prefix+"@example.com","password","password123","name","識別碼會員"),null),201);
            long memberId = a.path("member").path("id").asLong(); memberIds.add(memberId);
            String token = a.path("token").asText(), requestId = UUID.randomUUID().toString();
            var body = new HashMap<String,Object>(Map.of("receiverName","收件人","receiverPhone","0912345678",
                    "shippingAddress","台北市","paymentMethod","MOCK","couponCode",coupon.code(),"requestId",requestId));
            data(request("POST","/api/cart/items",Map.of("skuId",sku.getId(),"quantity",2),token),201);
            body.put("cartSnapshot","[[" + sku.getId() + ",2]]");
            var quoted=data(request("POST","/api/orders/quote",Map.of("code",coupon.code()),token),200);
            body.put("expectedAmounts",confirmedAmounts(quoted));
            jdbc.update("UPDATE product_sku SET price=price+1 WHERE id=?",sku.getId());
            var priceRejected=request("POST","/api/orders",body,token);
            assertThat(priceRejected.statusCode()).isEqualTo(409);
            assertThat(json.readTree(priceRejected.body()).at("/error/code").asText()).isEqualTo("CHECKOUT_PRICE_CONFLICT");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE member_id=?",Integer.class,memberId)).isZero();
            assertThat(jdbc.queryForObject("SELECT stock FROM product_sku WHERE id=?",Integer.class,sku.getId())).isEqualTo(10);
            jdbc.update("UPDATE product_sku SET price=price-1 WHERE id=?",sku.getId());
            var stale = new HashMap<>(body); stale.put("cartSnapshot","[[" + sku.getId() + ",1]]");
            var staleResult = request("POST","/api/orders",stale,token);
            assertThat(staleResult.statusCode()).isEqualTo(409);
            assertThat(json.readTree(staleResult.body()).at("/error/code").asText()).isEqualTo("CART_SNAPSHOT_CONFLICT");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE member_id=?",Integer.class,memberId)).isZero();
            assertThat(jdbc.queryForObject("SELECT stock FROM product_sku WHERE id=?",Integer.class,sku.getId())).isEqualTo(10);
            Long orderId = null;
            for (var response : concurrently(4, () -> request("POST","/api/orders",body,token))) {
                var replay = data(response,201);
                if (orderId == null) orderId = replay.path("id").asLong();
                assertThat(replay.path("id").asLong()).isEqualTo(orderId);
                assertThat(replay.path("discountAmount").decimalValue()).isEqualByComparingTo("1");
                assertThat(replay.has("requestHash")).isFalse();
            }
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE member_id=?",Integer.class,memberId)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT stock FROM product_sku WHERE id=?",Integer.class,sku.getId())).isEqualTo(8);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM stock_movement WHERE order_id=?",Integer.class,orderId)).isEqualTo(1);
            data(request("GET","/api/orders/requests/"+requestId.toUpperCase(Locale.ROOT),null,token),200);
            assertThat(request("GET","/api/orders/requests/"+requestId,null,null).statusCode()).isEqualTo(401);
            var changed = new HashMap<>(body); changed.put("receiverName","不同收件人");
            var conflict = request("POST","/api/orders",changed,token);
            assertThat(conflict.statusCode()).isEqualTo(409);
            assertThat(json.readTree(conflict.body()).at("/error/code").asText()).isEqualTo("REQUEST_ID_CONFLICT");
            changed = new HashMap<>(body); changed.put("cartSnapshot","[]");
            assertThat(json.readTree(request("POST","/api/orders",changed,token).body()).at("/error/code").asText()).isEqualTo("REQUEST_ID_CONFLICT");
            data(request("POST","/api/cart/items",Map.of("skuId",sku.getId(),"quantity",1),token),201);
            assertThat(data(request("POST","/api/orders",body,token),201).path("id").asLong()).isEqualTo(orderId);
            assertThat(data(request("GET","/api/cart",null,token),200).path("totalQuantity").asInt()).isEqualTo(1);

            String retryId = UUID.randomUUID().toString();
            var retry = new com.catshop.dto.order.CreateOrderRequest("收件人","0912345678","台北市",null,"COD",null,retryId);
            assertThatThrownBy(() -> new org.springframework.transaction.support.TransactionTemplate(transactionManager).execute(status -> {
                orderService.createOrder(memberId,retry);
                throw new IllegalStateException("rollback identity");
            })).hasMessage("rollback identity");
            assertThat(jdbc.queryForObject("SELECT stock FROM product_sku WHERE id=?",Integer.class,sku.getId())).isEqualTo(8);
            assertThat(request("GET","/api/orders/requests/"+retryId,null,token).statusCode()).isEqualTo(404);
            long secondId = data(request("POST","/api/orders",retry,token),201).path("id").asLong();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys.indexes WHERE object_id=OBJECT_ID('orders') AND name='ux_orders_member_request' AND is_unique=1 AND has_filter=1",Integer.class)).isEqualTo(1);
            String originalHash = jdbc.queryForObject("SELECT request_hash FROM orders WHERE id=?",String.class,orderId);
            assertThat(originalHash).hasSize(64);
            assertThatThrownBy(() -> jdbc.update("UPDATE orders SET request_id=?,request_hash=? WHERE id=?",requestId,originalHash,secondId))
                    .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);

            var b = data(request("POST","/api/auth/register",Map.of("email",prefix+"-b@example.com","password","password123","name","其他會員"),null),201);
            memberIds.add(b.path("member").path("id").asLong());
            String otherToken = b.path("token").asText();
            assertThat(request("GET","/api/orders/requests/"+requestId,null,otherToken).statusCode()).isEqualTo(404);
            data(request("POST","/api/cart/items",Map.of("skuId",sku.getId(),"quantity",1),otherToken),201);
            var otherBody = new HashMap<>(body); otherBody.remove("couponCode");
            otherBody.put("cartSnapshot","[[" + sku.getId() + ",1]]");
            otherBody.put("expectedAmounts",confirmedAmounts(data(request("GET","/api/orders/checkout-quote",null,otherToken),200)));
            assertThat(data(request("POST","/api/orders",otherBody,otherToken),201).path("id").asLong()).isNotEqualTo(orderId);
            data(request("POST","/api/orders/"+orderId+"/cancel",null,token),200);
            assertThat(data(request("POST","/api/orders",body,token),201).path("status").asText()).isEqualTo("CANCELLED");
            assertThat(jdbc.queryForObject("SELECT stock FROM product_sku WHERE id=?",Integer.class,sku.getId())).isEqualTo(8);
        } finally {
            for (Long memberId : memberIds) {
                jdbc.update("DELETE FROM stock_movement WHERE order_id IN (SELECT id FROM orders WHERE member_id=?)",memberId);
                jdbc.update("DELETE FROM order_item WHERE order_id IN (SELECT id FROM orders WHERE member_id=?)",memberId);
                jdbc.update("DELETE FROM orders WHERE member_id=?",memberId);
                jdbc.update("DELETE FROM cart_item WHERE cart_id IN (SELECT id FROM cart WHERE member_id=?)",memberId);
                jdbc.update("DELETE FROM cart WHERE member_id=?",memberId);
                jdbc.update("DELETE FROM auth_session WHERE member_id=?",memberId);
                jdbc.update("DELETE FROM member WHERE id=?",memberId);
            }
            if (couponId != null) jdbc.update("DELETE FROM coupon WHERE id=?",couponId);
            jdbc.update("DELETE FROM product_sku WHERE id=?",sku.getId());
            jdbc.update("DELETE FROM product WHERE id=?",product.getId());
            jdbc.update("DELETE FROM category WHERE id=?",category.getId());
        }
    }
    @Test void searchTextBoundsAndStatusNormalizationThroughSqlServerHttp() throws Exception {
        String admin=data(request("POST","/api/auth/login",Map.of("email","admin@catshop.local","password","password123"),null),200).path("token").asText();
        for (String path : List.of("/api/products","/api/admin/products","/api/admin/categories","/api/admin/members","/api/admin/inventory/low-stock")) {
            for (String invalid : List.of("x".repeat(201)," ".repeat(201)," "+"x".repeat(200)))
                assertThat(request("GET",path+"?keyword="+URLEncoder.encode(invalid,StandardCharsets.UTF_8),null,admin).statusCode()).as(path).isEqualTo(400);
            data(request("GET",path+"?keyword="+URLEncoder.encode(" ".repeat(200),StandardCharsets.UTF_8),null,admin),200);
        }
        for (String path : List.of("/api/products","/api/admin/products")) {
            for (String invalid : List.of("x".repeat(101)," ".repeat(101)," "+"x".repeat(100)))
                assertThat(request("GET",path+"?brand="+URLEncoder.encode(invalid,StandardCharsets.UTF_8),null,admin).statusCode()).isEqualTo(400);
            data(request("GET",path+"?brand="+URLEncoder.encode(" ".repeat(100),StandardCharsets.UTF_8),null,admin),200);
        }
        for (String section : List.of("products","orders")) {
            String upper=section.equals("products")?"ON_SALE":"PENDING";
            var expected=data(request("GET","/api/admin/"+section+"?status="+upper+"&sort=id,asc",null,admin),200);
            var actual=data(request("GET","/api/admin/"+section+"?status="+URLEncoder.encode(" "+upper.toLowerCase(Locale.ROOT)+" ",StandardCharsets.UTF_8)+"&sort=id,asc",null,admin),200);
            assertThat(actual).isEqualTo(expected);
        }
    }
    @Test void duplicateListQueryParametersThroughSqlServerHttp() throws Exception {
        String admin=data(request("POST","/api/auth/login",Map.of("email","admin@catshop.local","password","password123"),null),200).path("token").asText();
        String customer=data(request("POST","/api/auth/login",Map.of("email","customer@catshop.local","password","password123"),null),200).path("token").asText();
        long memberId=jdbc.queryForObject("SELECT id FROM member WHERE email='customer@catshop.local'",Long.class);
        for(String path:List.of("/api/products","/api/orders","/api/wishlist","/api/admin/products","/api/admin/orders","/api/admin/categories",
                "/api/admin/coupons","/api/admin/members","/api/admin/members/"+memberId+"/history","/api/admin/inventory/low-stock","/api/admin/inventory/movements")){
            var response=request("GET",path+"?page=0&page=1",null,admin);
            assertThat(response.statusCode()).isEqualTo(400);
            assertThat(json.readTree(response.body()).path("error").path("code").asText()).isEqualTo("DUPLICATE_QUERY_PARAMETER");
        }
        assertThat(request("GET","/api/products?page=0&%70age=1",null,null).statusCode()).isEqualTo(400);
        assertThat(request("GET","/api/products?size=20&size=20",null,null).statusCode()).isEqualTo(400);
        assertThat(request("GET","/api/admin/products?page=0&page=1",null,null).statusCode()).isEqualTo(401);
        assertThat(request("GET","/api/admin/products?page=0&page=1",null,customer).statusCode()).isEqualTo(403);
        data(request("GET","/api/products?page=0&size=1",null,null),200);
    }
    @Test void adminPagingAndSortBoundsThroughSqlServerHttp() throws Exception {
        String admin=data(request("POST","/api/auth/login",Map.of("email","admin@catshop.local","password","password123"),null),200).path("token").asText();
        for (String section : List.of("products","orders","categories")) {
            for (String query : List.of("page=-1", "size=0", "size=101"))
                assertThat(request("GET","/api/admin/"+section+"?"+query,null,admin).statusCode()).isEqualTo(400);
            data(request("GET","/api/admin/"+section+"?size=100",null,admin),200);
        }
        for (String section : List.of("products","orders")) {
            for (String sort : List.of("id", "id,", "id,other", "id,asc,extra", "password,asc"))
                assertThat(request("GET","/api/admin/"+section+"?sort="+URLEncoder.encode(sort,StandardCharsets.UTF_8),null,admin).statusCode()).isEqualTo(400);
            data(request("GET","/api/admin/"+section+"?sort=id,asc&size=1",null,admin),200);
        }
        for (String path : List.of("products?categoryId=0", "orders?status=UNKNOWN", "orders?to=9999-12-31", "orders?from=2026-02-30", "categories?keyword="+"x".repeat(201)))
            assertThat(request("GET","/api/admin/"+path,null,admin).statusCode()).isEqualTo(400);
    }
    @Test void addressOwnershipAndConcurrentDefaultsThroughSqlServerHttp() throws Exception {
        var ids=new ArrayList<Long>();var tokens=new ArrayList<String>();
        try {
            for(int i=0;i<2;i++){
                var signed=data(request("POST","/api/auth/register",Map.of("email","address-"+UUID.randomUUID()+"@example.com","name","Address test","password","password123"),null),201);
                ids.add(signed.path("member").path("id").asLong());tokens.add(signed.path("token").asText());
            }
            var payload=Map.<String,Object>of("receiverName","Test","phone","0912345678","city","Taipei","district","","line1","Test road","line2","","postalCode","100","isDefault",true);
            var responses=concurrently(2,()->request("POST","/api/members/me/addresses",payload,tokens.get(0)));
            assertThat(responses).allSatisfy(r->assertThat(r.statusCode()).isEqualTo(200));
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM address WHERE member_id=? AND is_default=1",Integer.class,ids.get(0))).isEqualTo(1);
            var first=data(responses.get(0),200);var second=data(responses.get(1),200);
            String path="/api/members/me/addresses/"+first.path("id").asLong();
            assertThat(request("GET",path,null,null).statusCode()).isEqualTo(401);
            assertThat(request("GET",path,null,tokens.get(1)).statusCode()).isEqualTo(404);
            assertThat(request("PUT",path,payload,tokens.get(1)).statusCode()).isEqualTo(404);
            assertThat(request("DELETE",path,null,tokens.get(1)).statusCode()).isEqualTo(404);
            var detail=data(request("GET",path,null,tokens.get(0)),200);
            var fields=new ArrayList<String>();detail.fieldNames().forEachRemaining(fields::add);
            assertThat(fields).contains("id","receiverName","phone","city","line1","isDefault")
                    .isSubsetOf("id","receiverName","phone","city","district","line1","line2","postalCode","isDefault");
            assertThat(request("GET","/api/members/me/addresses/0",null,tokens.get(0)).statusCode()).isEqualTo(400);
            assertThat(request("GET","/api/members/me/addresses/9223372036854775807",null,tokens.get(0)).statusCode()).isEqualTo(404);
            var counter=new java.util.concurrent.atomic.AtomicInteger();
            var updates=concurrently(2,()->request("PUT","/api/members/me/addresses/"+(counter.getAndIncrement()==0?first:second).path("id").asLong(),payload,tokens.get(0)));
            assertThat(updates).allSatisfy(r->assertThat(r.statusCode()).isEqualTo(200));
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM address WHERE member_id=? AND is_default=1",Integer.class,ids.get(0))).isEqualTo(1);
            var rows=data(request("GET","/api/members/me/addresses",null,tokens.get(0)),200);
            var defaultId=java.util.stream.StreamSupport.stream(rows.spliterator(),false).filter(r->r.path("isDefault").asBoolean()).findFirst().orElseThrow().path("id").asLong();
            data(request("DELETE","/api/members/me/addresses/"+defaultId,null,tokens.get(0)),200);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM address WHERE member_id=? AND is_default=1",Integer.class,ids.get(0))).isZero();
            System.out.println("PASS ADDRESS SQL: owner-only detail/update/delete, safe DTO, concurrent create/update default uniqueness and explicit zero-default after deletion.");
        } finally {
            for(var id:ids){jdbc.update("DELETE FROM address WHERE member_id=?",id);jdbc.update("DELETE FROM auth_session WHERE member_id=?",id);jdbc.update("DELETE FROM member WHERE id=?",id);}
        }
    }
    @Test void imageOnlyUpdateSnapshotRaceAndPermissionsThroughSqlServerHttp() throws Exception {
        String admin=data(request("POST","/api/auth/login",Map.of("email","admin@catshop.local","password","password123"),null),200).path("token").asText();
        long categoryId=jdbc.queryForObject("SELECT TOP 1 id FROM category ORDER BY id",Long.class);
        var image=Map.of("url","/api/images/test.png","altText","Test","sortOrder",0,"isPrimary",true);
        var product=data(request("POST","/api/admin/products",Map.of("name","Image-only test","slug","images-"+UUID.randomUUID(),
                "categoryId",categoryId,"brand","Keep","basePrice",123,"images",List.of(image)),admin),201);
        long id=product.path("id").asLong();
        String path="/api/admin/products/"+id+"/images";
        try {
            var ids=new ArrayList<Long>();product.path("images").forEach(img->ids.add(img.path("id").asLong()));
            var payload=Map.of("expectedImageIds",ids,"images",List.of(image));
            assertThat(request("PUT",path,payload,null).statusCode()).isEqualTo(401);
            String customer=data(request("POST","/api/auth/login",Map.of("email","customer@catshop.local","password","password123"),null),200).path("token").asText();
            assertThat(request("PUT",path,payload,customer).statusCode()).isEqualTo(403);
            assertThat(request("PUT",path,Map.of("images",List.of(image)),admin).statusCode()).isEqualTo(400);
            assertThat(request("PUT",path,Map.of("expectedImageIds",ids,"images",List.of(Map.of("url","/api/images/test.png","isPrimary",false))),admin).statusCode()).isEqualTo(400);
            var raced=concurrently(2,()->request("PUT",path,payload,admin));
            assertThat(raced).extracting(HttpResponse::statusCode).containsExactlyInAnyOrder(200,409);
            var latest=data(request("GET","/api/admin/products/"+id,null,admin),200);
            assertThat(latest.path("name").asText()).isEqualTo("Image-only test");
            assertThat(latest.path("brand").asText()).isEqualTo("Keep");
            assertThat(latest.path("basePrice").decimalValue()).isEqualByComparingTo("123");
            assertThat(latest.path("images").size()).isEqualTo(1);
            assertThat(request("PUT",path,payload,admin).statusCode()).isEqualTo(409);
            var newIds=new ArrayList<Long>();latest.path("images").forEach(img->newIds.add(img.path("id").asLong()));
            assertThat(data(request("PUT",path,Map.of("expectedImageIds",newIds,"images",List.of()),admin),200).size()).isZero();
        } finally {
            jdbc.update("DELETE FROM product_image WHERE product_id=?",id);
            jdbc.update("DELETE FROM product WHERE id=?",id);
        }
    }

    @Test void couponLastQuotaConcurrencyCancellationAndSnapshotThroughSqlServerHttp() throws Exception {
        var memberIds=new java.util.ArrayList<Long>();var tokens=new java.util.ArrayList<String>();long couponId=0;
        String code="SAVE_"+UUID.randomUUID().toString().replace("-","").substring(0,12).toUpperCase();
        try {
            String admin=data(request("POST","/api/auth/login",Map.of("email","admin@catshop.local","password","password123"),null),200).path("token").asText();
            var now=java.time.LocalDateTime.now(java.time.Clock.systemUTC()).truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
            var payload=new java.util.HashMap<String,Object>(Map.of("code",code,"discountAmount",100,"minimumAmount",0,"maxUses",1,"active",true,"startsAt",now.minusDays(1).toString(),"endsAt",now.plusDays(1).toString()));
            assertThat(request("GET","/api/admin/coupons",null,null).statusCode()).isEqualTo(401);
            var coupon=data(request("POST","/api/admin/coupons",payload,admin),201);couponId=coupon.path("id").asLong();
            String detailPath="/api/admin/coupons/"+couponId;
            assertThat(request("GET",detailPath,null,null).statusCode()).isEqualTo(401);
            assertThat(request("GET","/api/admin/coupons/0",null,admin).statusCode()).isEqualTo(400);
            assertThat(request("GET","/api/admin/coupons/9223372036854775807",null,admin).statusCode()).isEqualTo(404);
            var detail=data(request("GET",detailPath,null,admin),200);
            assertThat(detail.path("id")).isEqualTo(coupon.path("id"));
            assertThat(detail.path("code")).isEqualTo(coupon.path("code"));
            assertThat(detail.path("version")).isEqualTo(coupon.path("version"));
            assertThat(detail.path("discountAmount").decimalValue()).isEqualByComparingTo(coupon.path("discountAmount").decimalValue());
            assertThat(java.time.LocalDateTime.parse(detail.path("startsAt").asText()))
                    .isEqualTo(now.minusDays(1));
            assertThat(java.time.LocalDateTime.parse(detail.path("endsAt").asText()))
                    .isEqualTo(now.plusDays(1));
            assertThat(detail.size()).isEqualTo(9);
            var skuIds=jdbc.queryForList("SELECT TOP 2 id FROM product_sku WHERE is_active=1 AND stock>0 ORDER BY id",Long.class);
            for(int i=0;i<2;i++) {
                var signed=data(request("POST","/api/auth/register",Map.of("email","coupon-"+UUID.randomUUID()+"@example.com","password","password123","name","Coupon SQL"),null),201);
                memberIds.add(signed.path("member").path("id").asLong());tokens.add(signed.path("token").asText());
                data(request("POST","/api/cart/items",Map.of("skuId",skuIds.get(i),"quantity",1),tokens.get(i)),201);
                var quote=data(request("POST","/api/orders/quote",Map.of("code",code.toLowerCase()),tokens.get(i)),200);
                assertThat(quote.path("couponCode").asText()).isEqualTo(code);
                assertThat(quote.path("totalAmount").decimalValue()).isEqualByComparingTo(quote.path("goodsTotal").decimalValue().subtract(quote.path("discountAmount").decimalValue()).add(quote.path("shippingFee").decimalValue()));
            }
            assertThat(request("POST","/api/admin/coupons",payload,tokens.get(0)).statusCode()).isEqualTo(403);
            assertThat(request("GET",detailPath,null,tokens.get(0)).statusCode()).isEqualTo(403);
            var checkout=Map.<String,Object>of("receiverName","Test","receiverPhone","0912345678","shippingAddress","Test address","paymentMethod","COD","couponCode",code,"discountAmount",999999);
            var turn=new java.util.concurrent.atomic.AtomicInteger();
            var results=concurrently(2,()->request("POST","/api/orders",checkout,tokens.get(turn.getAndIncrement())));
            assertThat(results).extracting(HttpResponse::statusCode).containsExactlyInAnyOrder(201,400);
            assertThat(json.readTree(results.stream().filter(r->r.statusCode()==400).findFirst().orElseThrow().body()).path("error").path("code").asText()).isEqualTo("COUPON_EXHAUSTED");
            var order=data(results.stream().filter(r->r.statusCode()==201).findFirst().orElseThrow(),201);
            int winner=memberIds.indexOf(order.path("memberId").asLong()),loser=1-winner;
            long orderId=order.path("id").asLong();
            assertThat(order.path("discountAmount").decimalValue()).isEqualByComparingTo("100");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE coupon_id=? AND status<>'CANCELLED'",Integer.class,couponId)).isEqualTo(1);
            assertThat(data(request("GET","/api/cart",null,tokens.get(loser)),200).path("items").size()).isEqualTo(1);
            assertThat(request("POST","/api/orders/quote",Map.of("code",code),tokens.get(loser)).statusCode()).isEqualTo(400);
            data(request("PATCH","/api/admin/orders/"+orderId+"/status",Map.of("status","CANCELLED"),admin),200);
            payload.put("version",coupon.path("version").asInt());payload.put("discountAmount",150);
            var updated=data(request("PUT","/api/admin/coupons/"+couponId,payload,admin),200);
            var reloaded=data(request("GET",detailPath,null,admin),200);
            assertThat(reloaded.path("version")).isEqualTo(updated.path("version"));
            assertThat(reloaded.path("discountAmount").decimalValue()).isEqualByComparingTo("150");
            var renamed=new HashMap<String,Object>(payload);renamed.put("code",code+"X");renamed.put("version",updated.path("version").asInt());
            assertThat(request("PUT",detailPath,renamed,admin).statusCode()).isEqualTo(400);
            assertThat(request("PUT","/api/admin/coupons/"+couponId,payload,admin).statusCode()).isEqualTo(409);
            assertThat(data(request("GET","/api/orders/"+orderId,null,tokens.get(winner)),200).path("discountAmount").decimalValue()).isEqualByComparingTo("100");
            var next=data(request("POST","/api/orders",checkout,tokens.get(loser)),201);
            assertThat(next.path("discountAmount").decimalValue()).isEqualByComparingTo("150");
            data(request("POST","/api/orders/"+next.path("id").asLong()+"/cancel",null,tokens.get(loser)),200);
            payload.put("version",updated.path("version").asInt());payload.put("active",false);
            data(request("PUT","/api/admin/coupons/"+couponId,payload,admin),200);
            data(request("POST","/api/cart/items",Map.of("skuId",skuIds.get(loser),"quantity",1),tokens.get(loser)),201);
            assertThat(request("POST","/api/orders/quote",Map.of("code",code),tokens.get(loser)).statusCode()).isEqualTo(400);
            System.out.println("PASS COUPON SQL: quote is not reservation, one last-quota winner, no client discount trust, rollback cart, Admin/member cancellation release, version conflict, immutable snapshot and disabled coupon.");
        } finally {
            for(var id:memberIds) {
                jdbc.update("DELETE FROM stock_movement WHERE order_id IN(SELECT id FROM orders WHERE member_id=?)",id);
                jdbc.update("DELETE FROM order_item WHERE order_id IN(SELECT id FROM orders WHERE member_id=?)",id);jdbc.update("DELETE FROM orders WHERE member_id=?",id);
                jdbc.update("DELETE FROM cart_item WHERE cart_id IN(SELECT id FROM cart WHERE member_id=?)",id);jdbc.update("DELETE FROM cart WHERE member_id=?",id);
                jdbc.update("DELETE FROM auth_session WHERE member_id=?",id);jdbc.update("DELETE FROM member WHERE id=?",id);
            }
            if(couponId!=0)jdbc.update("DELETE FROM coupon WHERE id=?",couponId);
        }
    }
    @Test void shipmentIsAtomicUniqueAndMemberScopedThroughSqlServerHttp() throws Exception {
        String email="shipment-"+UUID.randomUUID()+"@example.com";
        var signed=data(request("POST","/api/auth/register",Map.of("email",email,"password","password123","name","Shipment test"),null),201);
        long memberId=signed.path("member").path("id").asLong();String user=signed.path("token").asText();long orderId=0;long raceId=0;
        try {
            long skuId=jdbc.queryForObject("SELECT TOP 1 id FROM product_sku WHERE is_active=1 AND stock>0 ORDER BY id",Long.class);
            data(request("POST","/api/cart/items",Map.of("skuId",skuId,"quantity",1),user),201);
            orderId=data(request("POST","/api/orders",Map.of("receiverName","Test","receiverPhone","0912345678","shippingAddress","Test address","paymentMethod","COD"),user),201).path("id").asLong();
            String path="/api/admin/orders/"+orderId+"/shipment";
            assertThat(request("POST",path,Map.of("carrier","宅配","trackingNumber","TEST"),user).statusCode()).isEqualTo(403);
            assertThat(request("GET","/api/orders/"+orderId+"/shipment",null,null).statusCode()).isEqualTo(401);
            String other=data(request("POST","/api/auth/login",Map.of("email","customer@catshop.local","password","password123"),null),200).path("token").asText();
            assertThat(request("GET","/api/orders/"+orderId+"/shipment",null,other).statusCode()).isEqualTo(404);
            String admin=data(request("POST","/api/auth/login",Map.of("email","admin@catshop.local","password","password123"),null),200).path("token").asText();
            assertThat(request("PATCH","/api/admin/orders/"+orderId+"/status",Map.of("status","SHIPPED"),admin).statusCode()).isEqualTo(400);
            assertThat(request("POST",path,Map.of("carrier"," ","trackingNumber","TEST"),admin).statusCode()).isEqualTo(400);
            var results=concurrently(2,()->request("POST",path,Map.of("carrier","宅配","trackingNumber","TEST123"),admin));
            assertThat(results).extracting(HttpResponse::statusCode).containsExactlyInAnyOrder(201,409);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM order_shipment WHERE order_id=?",Integer.class,orderId)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT status FROM orders WHERE id=?",String.class,orderId)).isEqualTo("SHIPPED");
            var shipment=data(request("GET","/api/orders/"+orderId+"/shipment",null,user),200);
            assertThat(shipment.path("trackingNumber").asText()).isEqualTo("TEST123");assertThat(shipment.has("actorMemberId")).isFalse();
            assertThat(request("POST","/api/orders/"+orderId+"/cancel",null,user).statusCode()).isEqualTo(400);
            data(request("PATCH","/api/admin/orders/"+orderId+"/status",Map.of("status","COMPLETED"),admin),200);
            assertThat(data(request("GET",path,null,admin),200).path("trackingNumber").asText()).isEqualTo("TEST123");
            data(request("POST","/api/cart/items",Map.of("skuId",skuId,"quantity",1),user),201);
            raceId=data(request("POST","/api/orders",Map.of("receiverName","Test","receiverPhone","0912345678","shippingAddress","Test address","paymentMethod","COD"),user),201).path("id").asLong();
            final long concurrentId=raceId;var turn=new java.util.concurrent.atomic.AtomicInteger();
            var race=concurrently(2,()->turn.getAndIncrement()==0
                ? request("POST","/api/admin/orders/"+concurrentId+"/shipment",Map.of("carrier","宅配","trackingNumber","RACE"),admin)
                : request("POST","/api/orders/"+concurrentId+"/cancel",null,user));
            assertThat(race.stream().filter(r->r.statusCode()==200 || r.statusCode()==201).count()).isEqualTo(1);
            assertThat(race).allSatisfy(r->assertThat(r.statusCode()).isIn(200,201,400,409));
            String finalStatus=jdbc.queryForObject("SELECT status FROM orders WHERE id=?",String.class,raceId);
            assertThat(finalStatus).isIn("SHIPPED","CANCELLED");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM order_shipment WHERE order_id=?",Integer.class,raceId)).isEqualTo("SHIPPED".equals(finalStatus)?1:0);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM stock_movement WHERE order_id=? AND reason='ORDER_CANCELLED'",Integer.class,raceId)).isEqualTo("CANCELLED".equals(finalStatus)?1:0);
        } finally {
            if(raceId!=0){jdbc.update("DELETE FROM order_shipment WHERE order_id=?",raceId);jdbc.update("DELETE FROM stock_movement WHERE order_id=?",raceId);jdbc.update("DELETE FROM order_item WHERE order_id=?",raceId);jdbc.update("DELETE FROM orders WHERE id=?",raceId);}
            if(orderId!=0){jdbc.update("DELETE FROM order_shipment WHERE order_id=?",orderId);jdbc.update("DELETE FROM stock_movement WHERE order_id=?",orderId);jdbc.update("DELETE FROM order_item WHERE order_id=?",orderId);jdbc.update("DELETE FROM orders WHERE id=?",orderId);}
            jdbc.update("DELETE FROM cart_item WHERE cart_id IN (SELECT id FROM cart WHERE member_id=?)",memberId);jdbc.update("DELETE FROM cart WHERE member_id=?",memberId);
            jdbc.update("DELETE FROM auth_session WHERE member_id=?",memberId);jdbc.update("DELETE FROM member WHERE id=?",memberId);
        }
    }
    @Autowired com.catshop.service.admin.AdminMemberService adminMembers;
    @Test void adminMemberLifecycleThroughSqlServerPreservesDataAndRevokesSessions() throws Exception {
        String prefix="member-"+UUID.randomUUID().toString().substring(0,8),email=prefix+"@example.com";
        var registration=request("POST","/api/auth/register",Map.of("email",email,"password","password123","name",prefix+"%_[!"),null);
        var signed=data(registration,201);long memberId=signed.path("member").path("id").asLong();
        String user=signed.path("token").asText(),cookie=registration.headers().firstValue("set-cookie").orElseThrow().split(";")[0];
        long orderId=0;
        try {
            var a=data(request("POST","/api/auth/login",Map.of("email","admin@catshop.local","password","password123"),null),200);
            String admin=a.path("token").asText();long actor=a.path("member").path("id").asLong();
            var sku=jdbc.queryForMap("SELECT TOP 1 s.id,s.product_id FROM product_sku s JOIN product p ON p.id=s.product_id WHERE s.is_active=1 AND p.is_active=1 AND p.status='ON_SALE' AND s.stock>0 ORDER BY s.id");
            data(request("POST","/api/cart/items",Map.of("skuId",sku.get("id"),"quantity",1),user),201);
            orderId=data(request("POST","/api/orders",Map.of("receiverName","Test","receiverPhone","0912345678","shippingAddress","Test road","paymentMethod","COD"),user),201).path("id").asLong();
            data(request("POST","/api/orders/"+orderId+"/cancel",null,user),200);
            data(request("POST","/api/cart/items",Map.of("skuId",sku.get("id"),"quantity",1),user),201);
            data(request("PUT","/api/wishlist/"+sku.get("product_id"),null,user),200);
            data(request("POST","/api/auth/forgot-password",Map.of("email",email),null),200);
            String listPath="/api/admin/members?keyword="+URLEncoder.encode(prefix+"%_[!",StandardCharsets.UTF_8)+"&size=1";
            var list=data(request("GET",listPath,null,admin),200);
            assertThat(list.path("totalElements").asInt()).isEqualTo(1);
            assertThat(list.toString()).doesNotContain("passwordHash","password_hash","tokenHash","refreshToken");
            assertThat(request("GET","/api/admin/members",null,null).statusCode()).isEqualTo(401);
            assertThat(request("GET","/api/admin/members",null,user).statusCode()).isEqualTo(403);
            String detailPath="/api/admin/members/"+memberId;
            assertThat(request("GET",detailPath,null,null).statusCode()).isEqualTo(401);
            assertThat(request("GET",detailPath,null,user).statusCode()).isEqualTo(403);
            assertThat(request("GET","/api/admin/members/0",null,admin).statusCode()).isEqualTo(400);
            assertThat(request("GET","/api/admin/members/9223372036854775807",null,admin).statusCode()).isEqualTo(404);
            var detail=data(request("GET",detailPath,null,admin),200);
            var detailFields=new ArrayList<String>();detail.fieldNames().forEachRemaining(detailFields::add);
            assertThat(detailFields).contains("id","email","name","role","status","statusVersion","createdAt","updatedAt")
                    .isSubsetOf("id","email","name","phone","role","status","statusVersion","createdAt","updatedAt");
            assertThat(detail.path("email").asText()).isEqualTo(email);
            assertThat(detail.toString()).doesNotContain("password","token","session","reset");
            assertThat(request("PATCH","/api/admin/members/"+actor+"/status",Map.of("status","DISABLED","version",0,"reason","test self"),admin).statusCode()).isEqualTo(403);
            var refresh=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/auth/refresh")).header("Cookie",cookie).POST(HttpRequest.BodyPublishers.noBody()).build();
            // No CookieManager: the shared test client now holds the Admin login cookie.
            var recoveryClient=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
            var turn=new java.util.concurrent.atomic.AtomicInteger();
            var concurrent=concurrently(3,()->turn.getAndIncrement()<2
                ? request("PATCH","/api/admin/members/"+memberId+"/status",Map.of("status","DISABLED","version",0,"reason","SQL test"),admin)
                : recoveryClient.send(refresh,HttpResponse.BodyHandlers.ofString()));
            assertThat(concurrent).allSatisfy(response->assertThat(response.statusCode()).isIn(200,401,409));
            assertThat(concurrent.stream().filter(response->response.statusCode()==409).count()).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT status FROM member WHERE id=?",String.class,memberId)).isEqualTo("DISABLED");
            var disabledDetail=data(request("GET",detailPath,null,admin),200);
            assertThat(disabledDetail.path("status").asText()).isEqualTo("DISABLED");
            assertThat(disabledDetail.path("statusVersion").asLong()).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM auth_session WHERE member_id=? AND revoked=0",Integer.class,memberId)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM password_reset WHERE member_id=?",Integer.class,memberId)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cart_item ci JOIN cart c ON c.id=ci.cart_id WHERE c.member_id=?",Integer.class,memberId)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wishlist_item WHERE member_id=?",Integer.class,memberId)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE member_id=?",Integer.class,memberId)).isEqualTo(1);
            assertThat(request("GET","/api/members/me",null,user).statusCode()).isEqualTo(401);
            assertThat(request("POST","/api/auth/login",Map.of("email",email,"password","password123"),null).statusCode()).isEqualTo(401);
            data(request("PATCH","/api/admin/members/"+memberId+"/status",Map.of("status","ACTIVE","version",1,"reason","Resume SQL"),admin),200);
            assertThat(request("PATCH","/api/admin/members/"+memberId+"/status",Map.of("status","DISABLED","version",0,"reason","Stale"),admin).statusCode()).isEqualTo(409);
            assertThat(request("GET","/api/members/me",null,user).statusCode()).isEqualTo(401);
            assertThat(recoveryClient.send(refresh,HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(401);
            data(request("POST","/api/auth/login",Map.of("email",email,"password","password123"),null),200);
            var history=data(request("GET","/api/admin/members/"+memberId+"/history?size=1",null,admin),200);
            assertThat(history.path("totalElements").asInt()).isEqualTo(2);assertThat(history.path("content").get(0).path("actorMemberId").asLong()).isEqualTo(actor);
            var stats=entityManagerFactory.unwrap(org.hibernate.SessionFactory.class).getStatistics();
            for(int size:List.of(1,20)){stats.clear();adminMembers.list(null,null,null,0,size);assertThat(stats.getPrepareStatementCount()).isLessThanOrEqualTo(2);}
            System.out.println("PASS ADMIN MEMBERS SQL: literal search, protected Admin, concurrent status/refresh, data retention, reset/access revocation, resume, history and bounded queries.");
        } finally {
            if(orderId!=0){jdbc.update("DELETE FROM stock_movement WHERE order_id=?",orderId);jdbc.update("DELETE FROM order_item WHERE order_id=?",orderId);}
            jdbc.update("DELETE FROM orders WHERE member_id=?",memberId);jdbc.update("DELETE FROM wishlist_item WHERE member_id=?",memberId);
            jdbc.update("DELETE FROM cart_item WHERE cart_id IN (SELECT id FROM cart WHERE member_id=?)",memberId);jdbc.update("DELETE FROM cart WHERE member_id=?",memberId);
            jdbc.update("DELETE FROM member_status_audit WHERE member_id=?",memberId);jdbc.update("DELETE FROM password_reset WHERE member_id=?",memberId);
            jdbc.update("DELETE FROM auth_session WHERE member_id=?",memberId);jdbc.update("DELETE FROM member WHERE id=?",memberId);
        }
    }
    @org.springframework.boot.test.mock.mockito.MockBean com.catshop.service.PasswordResetMailer resetMailer;
    @Test void passwordResetThroughSqlServerHttpIsSingleUseAndRevokesSessions() throws Exception {
        String email="reset-"+UUID.randomUUID()+"@example.com";
        var links=new java.util.concurrent.CopyOnWriteArrayList<String>();
        org.mockito.Mockito.doAnswer(i->{links.add(i.getArgument(1));return null;}).when(resetMailer).send(org.mockito.ArgumentMatchers.eq(email),org.mockito.ArgumentMatchers.anyString());
        var registration=request("POST","/api/auth/register",Map.of("email",email,"password","old-password","name","Reset SQL"),null);
        var signedIn=data(registration,201);long memberId=signedIn.path("member").path("id").asLong();
        String token=signedIn.path("token").asText(),cookie=registration.headers().firstValue("set-cookie").orElseThrow().split(";")[0];
        try {
            var known=data(request("POST","/api/auth/forgot-password",Map.of("email",email),null),200);
            var unknown=data(request("POST","/api/auth/forgot-password",Map.of("email","missing-"+email),null),200);
            assertThat(known.asText()).isEqualTo(unknown.asText());assertThat(links).hasSize(1);
            String secret=links.get(0).split("#token=")[1];
            assertThat(jdbc.queryForObject("SELECT token_hash FROM password_reset WHERE member_id=?",String.class,memberId)).hasSize(64).isNotEqualTo(secret);
            data(request("POST","/api/auth/forgot-password",Map.of("email",email),null),200);assertThat(links).hasSize(1);
            var concurrentRefresh=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/auth/refresh")).header("Cookie",cookie).POST(HttpRequest.BodyPublishers.noBody()).build();
            var turn=new java.util.concurrent.atomic.AtomicInteger();
            var responses=concurrently(2,()->turn.getAndIncrement()==0
                ? request("POST","/api/auth/reset-password",Map.of("token",secret,"newPassword","new-password"),null)
                : client.send(concurrentRefresh,HttpResponse.BodyHandlers.ofString()));
            assertThat(responses).allSatisfy(response->assertThat(response.statusCode()).isIn(200,401));
            var reset=responses.stream().filter(response->response.statusCode()==200 && jsonValueIsReset(response.body())).findFirst().orElseThrow();
            data(reset,200);assertThat(reset.headers().firstValue("set-cookie").orElseThrow()).contains("Max-Age=0");
            assertThat(request("GET","/api/members/me",null,token).statusCode()).isEqualTo(401);
            assertThat(request("POST","/api/auth/login",Map.of("email",email,"password","old-password"),null).statusCode()).isEqualTo(401);
            var refresh=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/auth/refresh")).header("Cookie",cookie).POST(HttpRequest.BodyPublishers.noBody()).build();
            assertThat(client.send(refresh,HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(401);
            assertThat(request("POST","/api/auth/reset-password",Map.of("token",secret,"newPassword","other-password"),null).statusCode()).isEqualTo(400);
            data(request("POST","/api/auth/login",Map.of("email",email,"password","new-password"),null),200);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM password_reset WHERE member_id=?",Integer.class,memberId)).isZero();
            System.out.println("PASS PASSWORD RESET SQL: generic response, hash-only storage, cooldown, single use, access/refresh revocation and new-password login.");
        } finally {
            jdbc.update("DELETE FROM password_reset WHERE member_id=?",memberId);jdbc.update("DELETE FROM auth_session WHERE member_id=?",memberId);jdbc.update("DELETE FROM member WHERE id=?",memberId);
        }
    }
    private boolean jsonValueIsReset(String body) {
        try {return json.readTree(body).path("data").isNull() || json.readTree(body).path("data").isMissingNode();}
        catch(Exception e){throw new IllegalStateException(e);}
    }
    @Autowired com.catshop.service.OrderService orderService;
    @Autowired com.catshop.repository.StockMovementRepository movements;
    @Test void stockMovementSqlHistorySurvivesCatalogRenameAndTracksOrderRestoration() throws Exception {
        String prefix="movement-"+UUID.randomUUID().toString().substring(0,8);
        var category=categories.saveAndFlush(com.catshop.entity.Category.builder().name(prefix).slug(prefix).build());
        var product=products.saveAndFlush(com.catshop.entity.Product.builder().category(category).name(prefix).slug(prefix).basePrice(java.math.BigDecimal.TEN).build());
        var member=members.saveAndFlush(com.catshop.entity.Member.builder().email(prefix+"@local").name(prefix).passwordHash("unused").build());
        try {
            String admin=data(request("POST","/api/auth/login",Map.of("email","admin@catshop.local","password","password123"),null),200).path("token").asText();
            var initial=data(request("POST","/api/admin/products/"+product.getId()+"/skus",Map.of("skuCode",prefix,"specName","1kg","price",10,"stock",10,"safetyStock",0),admin),201);
            long skuId=initial.path("id").asLong();assertThat(skuId).isPositive();
            data(request("PUT","/api/admin/skus/"+skuId,Map.of("specName","1kg","price",10,"stock",12,"version",initial.path("version").asInt()),admin),200);
            cartService.addItem(member.getId(),new com.catshop.dto.cart.AddCartItemRequest(skuId,2));
            var order=orderService.createOrder(member.getId(),new com.catshop.dto.order.CreateOrderRequest("Test","0912345678","Test road",null,"COD"));
            orderService.cancelMyOrder(member.getId(),order.id());
            jdbc.update("UPDATE product SET name=? WHERE id=?",prefix+" renamed",product.getId());
            var history=data(request("GET","/api/admin/inventory/movements?skuId="+skuId+"&size=2",null,admin),200);
            assertThat(history.path("totalElements").asInt()).isEqualTo(4);
            assertThat(history.path("content").get(0).path("reason").asText()).isEqualTo("ORDER_CANCELLED");
            assertThat(history.path("content").get(0).path("productName").asText()).isEqualTo(prefix);
            assertThat(history.path("content").get(0).path("quantityDelta").asInt()).isEqualTo(2);
            assertThat(history.path("content").get(0).path("orderNo").asText()).isEqualTo(order.orderNo());
            assertThat(movements.findByOrderIdOrderByIdAsc(order.id())).extracting(com.catshop.entity.StockMovement::getBeforeStock).containsExactly(12,10);
            assertThat(request("GET","/api/admin/inventory/movements?skuId=0",null,admin).statusCode()).isEqualTo(400);
            assertThat(request("GET","/api/admin/inventory/movements",null,null).statusCode()).isEqualTo(401);
            System.out.println("PASS STOCK MOVEMENTS SQL: initial stock, adjustment, checkout/cancel, paging, snapshot and authorization.");
        } finally {
            jdbc.update("DELETE FROM stock_movement WHERE product_id=?",product.getId());
            jdbc.update("DELETE FROM order_item WHERE order_id IN (SELECT id FROM orders WHERE member_id=?)",member.getId());
            jdbc.update("DELETE FROM orders WHERE member_id=?",member.getId());
            jdbc.update("DELETE FROM cart_item WHERE cart_id IN (SELECT id FROM cart WHERE member_id=?)",member.getId());
            jdbc.update("DELETE FROM cart WHERE member_id=?",member.getId());
            jdbc.update("DELETE FROM member WHERE id=?",member.getId());
            jdbc.update("DELETE FROM product_sku WHERE product_id=?",product.getId());
            jdbc.update("DELETE FROM product WHERE id=?",product.getId());jdbc.update("DELETE FROM category WHERE id=?",category.getId());
        }
    }
    @LocalServerPort int port;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired com.catshop.service.AuthSessionCleanupService cleanup;
    @Autowired com.catshop.repository.AuthSessionRepository authSessions;
    @Autowired jakarta.persistence.EntityManagerFactory entityManagerFactory;
    @Autowired com.catshop.service.ProductService catalog;
    @Autowired com.catshop.service.admin.AdminProductService adminProducts;
    @Autowired com.catshop.service.admin.AdminOrderService adminOrders;
    @Autowired com.catshop.repository.MemberRepository members;
    @Autowired com.catshop.repository.OrderRepository orders;
    @Autowired com.catshop.repository.OrderItemRepository orderItems;
    @Autowired com.catshop.repository.CategoryRepository categories;
    @Autowired com.catshop.repository.ProductRepository products;
    @Autowired com.catshop.service.admin.AdminCategoryService adminCategories;
    @Autowired com.catshop.service.CartService cartService;
    @Autowired com.catshop.repository.CartRepository carts;
    @Autowired com.catshop.repository.CartItemRepository cartItems;
    @Autowired com.catshop.repository.ProductSkuRepository skus;
    @Autowired com.catshop.repository.ProductImageRepository images;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;
    @jakarta.persistence.PersistenceContext jakarta.persistence.EntityManager entityManager;

    @Test void lowStockSummaryAndLiteralSearchThroughSqlServerHttp() throws Exception {
        String prefix="inv-"+UUID.randomUUID().toString().replace("-","").substring(0,12);
        var category=categories.saveAndFlush(com.catshop.entity.Category.builder().name(prefix).slug(prefix).build());
        var product=products.saveAndFlush(com.catshop.entity.Product.builder().category(category).name(prefix).slug(prefix).status("OFF_SHELF").basePrice(java.math.BigDecimal.TEN).build());
        try {
            for(int i=0;i<4;i++) skus.saveAndFlush(com.catshop.entity.ProductSku.builder().product(product).skuCode(prefix+(i==0?"%_[":"-"+i)).specName("spec"+i).price(java.math.BigDecimal.TEN).stock(i).safetyStock(3).isActive(i<3).build());
            assertThat(request("GET","/api/admin/inventory/low-stock",null,null).statusCode()).isEqualTo(401);
            String admin=data(request("POST","/api/auth/login",Map.of("email","admin@catshop.local","password","password123"),null),200).path("token").asText();
            String customer=data(request("POST","/api/auth/login",Map.of("email","customer@catshop.local","password","password123"),null),200).path("token").asText();
            assertThat(request("GET","/api/admin/inventory/low-stock",null,customer).statusCode()).isEqualTo(403);
            String path="/api/admin/inventory/low-stock?keyword="+prefix+"&size=1";
            var result=data(request("GET",path,null,admin),200);
            assertThat(result.path("lowStockCount").asLong()).isEqualTo(3); assertThat(result.path("outOfStockCount").asLong()).isEqualTo(1);
            assertThat(result.path("items").path("totalElements").asLong()).isEqualTo(3);
            var zeros=data(request("GET",path+"&outOfStockOnly=true",null,admin),200);
            assertThat(zeros.path("items").path("totalElements").asLong()).isEqualTo(1); assertThat(zeros.path("lowStockCount").asLong()).isEqualTo(3);
            var literal=data(request("GET","/api/admin/inventory/low-stock?keyword=%25_%5B",null,admin),200);
            assertThat(literal.path("items").path("totalElements").asLong()).isEqualTo(1);
            jdbc.update("UPDATE product_sku SET stock=4, version=version+1 WHERE product_id=?",product.getId());
            assertThat(data(request("GET",path,null,admin),200).path("lowStockCount").asLong()).isZero();
            System.out.println("PASS LOW STOCK SQL: global summary, paging, literal wildcards, replenishment and ADMIN authorization.");
        } finally {
            jdbc.update("DELETE FROM product_sku WHERE product_id=?",product.getId()); jdbc.update("DELETE FROM product WHERE id=?",product.getId()); jdbc.update("DELETE FROM category WHERE id=?",category.getId());
        }
    }

    private <T> List<T> concurrently(int count, java.util.concurrent.Callable<T> action) throws Exception {
        var pool = java.util.concurrent.Executors.newFixedThreadPool(count);
        var ready = new java.util.concurrent.CountDownLatch(count); var start = new java.util.concurrent.CountDownLatch(1);
        try {
            var futures = new ArrayList<java.util.concurrent.Future<T>>();
            for (int i = 0; i < count; i++) futures.add(pool.submit(() -> {
                ready.countDown();
                if (!start.await(10, java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("start timeout");
                return action.call();
            }));
            assertThat(ready.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue(); start.countDown();
            var results = new ArrayList<T>();
            for (var future : futures) results.add(future.get(30, java.util.concurrent.TimeUnit.SECONDS));
            return results;
        } finally { start.countDown(); pool.shutdownNow(); assertThat(pool.awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS)).isTrue(); }
    }

    @Test
    void concurrentCartMutationsAndSameCartCheckoutStayConsistentOnSqlServer() throws Exception {
        var prefix = "cart-lock-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        var memberIds = new ArrayList<Long>();
        Long categoryId = null, productId = null, skuId = null;
        try {
            var category = categories.saveAndFlush(com.catshop.entity.Category.builder().name("並行分類").slug(prefix).build()); categoryId = category.getId();
            var product = products.saveAndFlush(com.catshop.entity.Product.builder().category(category).name("並行商品").slug(prefix).basePrice(java.math.BigDecimal.TEN).build()); productId = product.getId();
            var sku = skus.saveAndFlush(com.catshop.entity.ProductSku.builder().product(product).skuCode(prefix).specName("測試規格").price(java.math.BigDecimal.TEN).stock(10).build()); skuId = sku.getId();
            var a = data(request("POST", "/api/auth/register", Map.of("email", prefix + "a@test.local", "password", "testpassword123", "name", "並行會員 A"), null), 201);
            var aId = a.path("member").path("id").asLong(); memberIds.add(aId); var aToken = a.path("token").asText();
            data(request("GET", "/api/cart", null, aToken), 200);
            var addBody = Map.of("skuId", skuId, "quantity", 1);
            for (var response : concurrently(8, () -> request("POST", "/api/cart/items", addBody, aToken))) data(response, 201);
            var aCart = data(request("GET", "/api/cart", null, aToken), 200);
            assertThat(aCart.path("items").size()).isEqualTo(1); assertThat(aCart.path("totalQuantity").asInt()).isEqualTo(8);
            var stockLimited = concurrently(8, () -> request("POST", "/api/cart/items", addBody, aToken));
            assertThat(stockLimited.stream().filter(r -> r.statusCode() == 201).count()).isEqualTo(2);
            assertThat(stockLimited.stream().filter(r -> r.statusCode() == 400).count()).isEqualTo(6);
            assertThat(data(request("GET", "/api/cart", null, aToken), 200).path("totalQuantity").asInt()).isEqualTo(10);
            data(request("DELETE", "/api/cart", null, aToken), 200);
            data(request("POST", "/api/cart/items", Map.of("skuId", skuId, "quantity", 2), aToken), 201);
            var orderBody = Map.of("receiverName", "收件人", "receiverPhone", "0912345678", "shippingAddress", "台北市測試路", "paymentMethod", "COD");
            var checkouts = concurrently(2, () -> request("POST", "/api/orders", orderBody, aToken));
            assertThat(checkouts.stream().filter(r -> r.statusCode() == 201).count()).isEqualTo(1);
            assertThat(checkouts.stream().filter(r -> r.statusCode() == 400).count()).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE member_id = ?", Integer.class, aId)).isEqualTo(1);
            assertThat(skus.findById(skuId).orElseThrow().getStock()).isEqualTo(8);
            assertThat(data(request("GET", "/api/cart", null, aToken), 200).path("items")).isEmpty();

            var b = data(request("POST", "/api/auth/register", Map.of("email", prefix + "b@test.local", "password", "testpassword123", "name", "並行會員 B"), null), 201);
            var bId = b.path("member").path("id").asLong(); memberIds.add(bId); var bToken = b.path("token").asText();
            // No initial GET: cart creation and first same-SKU insert contend together.
            for (var response : concurrently(8, () -> request("POST", "/api/cart/items", addBody, bToken))) data(response, 201);
            var bCart = data(request("GET", "/api/cart", null, bToken), 200);
            assertThat(bCart.path("items").size()).isEqualTo(1); assertThat(bCart.path("totalQuantity").asInt()).isEqualTo(8);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cart WHERE member_id = ?", Integer.class, bId)).isEqualTo(1);
            var itemId = bCart.path("items").get(0).path("id").asLong();
            assertThat(request("PATCH", "/api/cart/items/" + itemId, Map.of("quantity", 2), aToken).statusCode()).isEqualTo(404);
            var snapshotBody = Map.of("receiverName","收件人","receiverPhone","0912345678","shippingAddress","台北市",
                    "paymentMethod","COD","requestId",UUID.randomUUID().toString(),"cartSnapshot","[[" + skuId + ",8]]");
            var mutationHeld = new java.util.concurrent.CountDownLatch(1);
            var mutationRelease = new java.util.concurrent.CountDownLatch(1);
            var snapshotPool = java.util.concurrent.Executors.newFixedThreadPool(2);
            try {
                var mutation = snapshotPool.submit(() -> new org.springframework.transaction.support.TransactionTemplate(transactionManager).execute(status -> {
                    cartService.updateItem(bId,itemId,new com.catshop.dto.cart.UpdateCartItemRequest(4));
                    mutationHeld.countDown();
                    try { if (!mutationRelease.await(10,java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("mutation timeout"); }
                    catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
                    return true;
                }));
                assertThat(mutationHeld.await(10,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                var waitingCheckout = snapshotPool.submit(() -> request("POST","/api/orders",snapshotBody,bToken));
                assertThatThrownBy(() -> waitingCheckout.get(200,java.util.concurrent.TimeUnit.MILLISECONDS)).isInstanceOf(java.util.concurrent.TimeoutException.class);
                mutationRelease.countDown();
                assertThat(mutation.get(20,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                var rejected = waitingCheckout.get(20,java.util.concurrent.TimeUnit.SECONDS);
                assertThat(rejected.statusCode()).isEqualTo(409);
                assertThat(json.readTree(rejected.body()).at("/error/code").asText()).isEqualTo("CART_SNAPSHOT_CONFLICT");
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE member_id=?",Integer.class,bId)).isZero();
                assertThat(skus.findById(skuId).orElseThrow().getStock()).isEqualTo(8);
                assertThat(data(request("GET","/api/cart",null,bToken),200).path("totalQuantity").asInt()).isEqualTo(4);
                data(request("PATCH","/api/cart/items/"+itemId,Map.of("quantity",8),bToken),200);
                System.out.println("PASS CART SNAPSHOT SQL: checkout waits for cart mutation commit, then rejects the stale snapshot without stock/order side effects.");
            } finally { mutationRelease.countDown(); snapshotPool.shutdownNow(); assertThat(snapshotPool.awaitTermination(30,java.util.concurrent.TimeUnit.SECONDS)).isTrue(); }
            var held = new java.util.concurrent.CountDownLatch(1); var release = new java.util.concurrent.CountDownLatch(1);
            var pool = java.util.concurrent.Executors.newFixedThreadPool(3);
            try {
                var read = pool.submit(() -> new org.springframework.transaction.support.TransactionTemplate(transactionManager).execute(status -> {
                    var response = cartService.getCart(bId); held.countDown();
                    try { if (!release.await(10, java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("read timeout"); }
                    catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
                    return response;
                }));
                assertThat(held.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                var update = pool.submit(() -> request("PATCH", "/api/cart/items/" + itemId, Map.of("quantity", 4), bToken));
                var clear = pool.submit(() -> request("DELETE", "/api/cart", null, bToken));
                assertThatThrownBy(() -> update.get(150, java.util.concurrent.TimeUnit.MILLISECONDS)).isInstanceOf(java.util.concurrent.TimeoutException.class);
                assertThatThrownBy(() -> clear.get(150, java.util.concurrent.TimeUnit.MILLISECONDS)).isInstanceOf(java.util.concurrent.TimeoutException.class);
                release.countDown();
                assertThat(read.get(20, java.util.concurrent.TimeUnit.SECONDS).totalQuantity()).isEqualTo(8);
                assertThat(update.get(20, java.util.concurrent.TimeUnit.SECONDS).statusCode()).isIn(200, 404);
                data(clear.get(20, java.util.concurrent.TimeUnit.SECONDS), 200);
                assertThat(data(request("GET", "/api/cart", null, bToken), 200).path("items")).isEmpty();
            } finally { release.countDown(); pool.shutdownNow(); assertThat(pool.awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS)).isTrue(); }
            System.out.println("PASS CART CONCURRENCY: same-SKU creation/accumulation, stock rejection, one checkout, ownership and read/update/clear locking through SQL Server HTTP.");
        } finally {
            for (var id : memberIds) {
                jdbc.update("DELETE FROM order_item WHERE order_id IN (SELECT id FROM orders WHERE member_id = ?)", id);
                jdbc.update("DELETE FROM orders WHERE member_id = ?", id);
                jdbc.update("DELETE FROM cart_item WHERE cart_id IN (SELECT id FROM cart WHERE member_id = ?)", id);
                jdbc.update("DELETE FROM cart WHERE member_id = ?", id);
                jdbc.update("DELETE FROM auth_session WHERE member_id = ?", id);
                jdbc.update("DELETE FROM member WHERE id = ?", id);
            }
            if (skuId != null) jdbc.update("DELETE FROM product_sku WHERE id = ?", skuId);
            if (productId != null) jdbc.update("DELETE FROM product WHERE id = ?", productId);
            if (categoryId != null) jdbc.update("DELETE FROM category WHERE id = ?", categoryId);
        }
    }

    @Test
    void wishlistIsIdempotentAndMemberScopedThroughSqlServerHttp() throws Exception {
        var prefix = "wish-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        var category = categories.saveAndFlush(com.catshop.entity.Category.builder().name(prefix).slug(prefix).build());
        var product = products.saveAndFlush(com.catshop.entity.Product.builder().category(category).name(prefix).slug(prefix)
                .basePrice(java.math.BigDecimal.TEN).build());
        var memberIds = new ArrayList<Long>();
        try {
            var a = data(request("POST", "/api/auth/register", Map.of("email", prefix + "a@test.local", "password", "testpassword123", "name", "收藏 A"), null), 201);
            memberIds.add(a.path("member").path("id").asLong()); var token = a.path("token").asText();
            var b = data(request("POST", "/api/auth/register", Map.of("email", prefix + "b@test.local", "password", "testpassword123", "name", "收藏 B"), null), 201);
            memberIds.add(b.path("member").path("id").asLong()); var bToken = b.path("token").asText();
            assertThat(request("GET", "/api/wishlist", null, null).statusCode()).isEqualTo(401);
            for (var response : concurrently(8, () -> request("PUT", "/api/wishlist/" + product.getId(), null, token))) {
                assertThat(data(response, 200).get(0).asLong()).isEqualTo(product.getId());
            }
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wishlist_item WHERE member_id = ?", Integer.class, memberIds.get(0))).isEqualTo(1);
            assertThat(data(request("GET", "/api/wishlist/ids", null, bToken), 200)).isEmpty();
            data(request("DELETE", "/api/wishlist/" + product.getId(), null, bToken), 200);
            var page = data(request("GET", "/api/wishlist?size=1", null, token), 200);
            assertThat(page.path("totalElements").asLong()).isEqualTo(1);
            assertThat(page.path("content").get(0).path("available").asBoolean()).isTrue();
            jdbc.update("UPDATE product SET is_active = 0 WHERE id = ?", product.getId());
            var unavailable = data(request("GET", "/api/wishlist", null, token), 200);
            assertThat(unavailable.path("content").get(0).path("available").asBoolean()).isFalse();
            data(request("PUT", "/api/wishlist/" + product.getId(), null, token), 200);
            assertThat(request("PUT", "/api/wishlist/" + product.getId(), null, bToken).statusCode()).isEqualTo(400);
            assertThat(request("GET", "/api/wishlist?size=101", null, token).statusCode()).isEqualTo(400);
            assertThat(request("PUT", "/api/wishlist/0", null, token).statusCode()).isEqualTo(400);
            data(request("DELETE", "/api/wishlist/" + product.getId(), null, token), 200);
            assertThat(data(request("DELETE", "/api/wishlist/" + product.getId(), null, token), 200)).isEmpty();
            System.out.println("PASS WISHLIST SQL: concurrent idempotence, member isolation, unavailable items, paging and removal.");
        } finally {
            for (var id : memberIds) {
                jdbc.update("DELETE FROM wishlist_item WHERE member_id = ?", id);
                jdbc.update("DELETE FROM auth_session WHERE member_id = ?", id);
                jdbc.update("DELETE FROM member WHERE id = ?", id);
            }
            jdbc.update("DELETE FROM product WHERE id = ?", product.getId());
            jdbc.update("DELETE FROM category WHERE id = ?", category.getId());
        }
    }

    @Test
    void adminFiltersWorkThroughHttpOnSqlServer() throws Exception {
        var prefix = "filter-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        var memberIds = new ArrayList<Long>(); var orderIds = new ArrayList<Long>(); var productIds = new ArrayList<Long>();
        Long categoryId = null;
        try {
            var category = categories.saveAndFlush(com.catshop.entity.Category.builder().name("篩選分類").slug(prefix).build()); categoryId = category.getId();
            for (int i = 0; i < 2; i++) {
                var product = products.saveAndFlush(com.catshop.entity.Product.builder().category(category).name(prefix + i).slug(prefix + i)
                        .brand(i == 0 ? "Acme" : "Other").basePrice(java.math.BigDecimal.TEN).build()); productIds.add(product.getId());
                memberIds.add(members.saveAndFlush(com.catshop.entity.Member.builder().name("篩選會員").email(prefix + i + "@test.local").passwordHash("test-hash").build()).getId());
            }
            for (int i = 0; i < 4; i++) {
                var member = members.findById(memberIds.get(i == 2 ? 1 : 0)).orElseThrow();
                var order = orders.saveAndFlush(com.catshop.entity.Order.builder().member(member).orderNo(prefix + i)
                        .status(i == 1 ? "PAID" : "PENDING").totalAmount(java.math.BigDecimal.TEN).receiverName("收件人").receiverPhone("0912345678").shippingAddress("台北市").build());
                orderIds.add(order.getId());
                String time = i == 3 ? "2026-01-03T00:00:00.000" : i == 1 ? "2026-01-02T23:59:59.999" : "2026-01-02T00:00:00.000";
                var expectedTime = java.time.LocalDateTime.parse(time);
                // Use the application's Hibernate UTC JDBC binding, not raw SQL wall-clock values.
                new org.springframework.transaction.support.TransactionTemplate(transactionManager).execute(status -> {
                    entityManager.createNativeQuery("UPDATE orders SET created_at = :time WHERE id = :id")
                            .setParameter("time", expectedTime).setParameter("id", order.getId()).executeUpdate();
                    return null;
                });
                assertThat(orders.findById(order.getId()).orElseThrow().getCreatedAt()).isEqualTo(expectedTime);
            }
            var token = data(request("POST", "/api/auth/login", Map.of("email", "admin@catshop.local", "password", "password123"), null), 200).path("token").asText();
            var filteredProducts = data(request("GET", "/api/admin/products?categoryId=" + categoryId + "&brand=%20ACME%20&isActive=true", null, token), 200);
            assertThat(filteredProducts.path("totalElements").asLong()).isEqualTo(1);
            assertThat(filteredProducts.path("content").get(0).path("id").asLong()).isEqualTo(productIds.get(0));
            var orderPath = "/api/admin/orders?memberId=" + memberIds.get(0) + "&orderNo=" + prefix + "&from=2026-01-02&to=2026-01-02&sort=orderNo,asc";
            var filteredOrders = data(request("GET", orderPath, null, token), 200);
            assertThat(filteredOrders.path("totalElements").asLong()).isEqualTo(2);
            assertThat(filteredOrders.path("content").get(1).path("createdAt").asText()).isEqualTo("2026-01-02T23:59:59.999");
            var paid = data(request("GET", orderPath + "&status=PAID", null, token), 200);
            assertThat(paid.path("totalElements").asLong()).isEqualTo(1);
            assertThat(paid.path("content").get(0).path("id").asLong()).isEqualTo(orderIds.get(1));
            assertThat(request("GET", "/api/admin/orders?from=2026-01-03&to=2026-01-02", null, token).statusCode()).isEqualTo(400);
            assertThat(request("GET", "/api/admin/orders?memberId=0", null, token).statusCode()).isEqualTo(400);
            assertThat(request("GET", "/api/admin/products?brand=" + "x".repeat(101), null, token).statusCode()).isEqualTo(400);
            assertThat(data(request("GET", "/api/admin/categories?keyword=" + prefix + "&isActive=false", null, token), 200).path("content")).isEmpty();
            assertThat(data(request("GET", "/api/admin/categories?keyword=" + prefix + "&isActive=true", null, token), 200).path("totalElements").asLong()).isEqualTo(1);
        } finally {
            for (var id : orderIds) jdbc.update("DELETE FROM orders WHERE id = ?", id);
            for (var id : memberIds) jdbc.update("DELETE FROM member WHERE id = ?", id);
            for (var id : productIds) jdbc.update("DELETE FROM product WHERE id = ?", id);
            if (categoryId != null) jdbc.update("DELETE FROM category WHERE id = ?", categoryId);
        }
    }

    @Test
    void cartReadsBatchProductsAndImagesOnSqlServer() {
        var prefix = "cart-query-" + UUID.randomUUID();
        var memberIds = new ArrayList<Long>(); var cartIds = new ArrayList<Long>();
        try {
            var uniqueSkus = skus.findAll().stream().sorted(Comparator.comparing(com.catshop.entity.ProductSku::getId))
                    .filter(s -> Boolean.TRUE.equals(s.getIsActive())).toList();
            // Read product identities from FK values, without relying on an open persistence context.
            var chosen = new ArrayList<com.catshop.entity.ProductSku>(); var productIds = new HashSet<Long>();
            for (var sku : uniqueSkus) if (productIds.add(sku.getProduct().getId())) { chosen.add(sku); if (chosen.size() == 6) break; }
            assertThat(chosen).hasSize(6);
            var expectedImages = new HashMap<Long, String>();
            for (var row : images.findPrimaryImages(productIds)) expectedImages.putIfAbsent(row.getProductId(), row.getUrl());
            for (int count : List.of(1, 6, 0)) {
                var member = members.saveAndFlush(com.catshop.entity.Member.builder().email(prefix + count + "@test.local").name("購物車測試").passwordHash("test-hash").build());
                memberIds.add(member.getId());
                var cart = carts.saveAndFlush(com.catshop.entity.Cart.builder().member(member).build()); cartIds.add(cart.getId());
                for (int i = 0; i < count; i++) cartItems.saveAndFlush(com.catshop.entity.CartItem.builder().cart(cart)
                        .sku(chosen.get(i)).quantity(i + 1).unitPrice(chosen.get(i).getPrice()).build());
            }
            long one = queryBudget("cart 1 distinct product", 3, () -> assertThat(cartService.getCart(memberIds.get(0)).items()).hasSize(1));
            long six = queryBudget("cart 6 distinct products", 3, () -> {
                var response = cartService.getCart(memberIds.get(1));
                assertThat(response.items()).hasSize(6); assertThat(response.totalQuantity()).isEqualTo(21);
                var total = java.math.BigDecimal.ZERO;
                for (int i = 0; i < 6; i++) {
                    var item = response.items().get(i); var sku = chosen.get(i);
                    assertThat(item.skuId()).isEqualTo(sku.getId());
                    assertThat(item.stock()).isEqualTo(sku.getStock());
                    assertThat(item.imageUrl()).isEqualTo(expectedImages.get(sku.getProduct().getId()));
                    total = total.add(sku.getPrice().multiply(java.math.BigDecimal.valueOf(i + 1)));
                }
                assertThat(response.totalAmount()).isEqualByComparingTo(total);
            });
            assertThat(one).isEqualTo(six).isEqualTo(3);
            queryBudget("empty cart", 2, () -> assertThat(cartService.getCart(memberIds.get(2)).items()).isEmpty());
            jdbc.update("UPDATE cart_item SET unit_price = ? WHERE cart_id = ?", chosen.get(0).getPrice().subtract(java.math.BigDecimal.ONE), cartIds.get(0));
            queryBudget("cart repricing includes one write", 4, () -> assertThat(cartService.getCart(memberIds.get(0)).items().get(0).unitPrice()).isEqualByComparingTo(chosen.get(0).getPrice()));
            assertThat(jdbc.queryForObject("SELECT unit_price FROM cart_item WHERE cart_id = ?", java.math.BigDecimal.class, cartIds.get(0))).isEqualByComparingTo(chosen.get(0).getPrice());
        } finally {
            for (var id : cartIds) { jdbc.update("DELETE FROM cart_item WHERE cart_id = ?", id); jdbc.update("DELETE FROM cart WHERE id = ?", id); }
            for (var id : memberIds) jdbc.update("DELETE FROM member WHERE id = ?", id);
        }
    }

    @Test
    void categoryHierarchyRejectsCyclesAndSerializesReparentsOnSqlServer() throws Exception {
        com.catshop.support.CategoryHierarchyChecks.descendants(adminCategories, jdbc);
        com.catshop.support.CategoryHierarchyChecks.validMoves(adminCategories, jdbc);
        com.catshop.support.CategoryHierarchyChecks.selfAndMissing(adminCategories, jdbc);
        com.catshop.support.CategoryHierarchyChecks.corruptedAncestors(adminCategories, jdbc);
        com.catshop.support.CategoryHierarchyChecks.concurrentMoves(adminCategories, jdbc);
    }

    @Test
    void categoryPagingAndLiteralSearchStayBoundedOnSqlServer() {
        var prefix = "CATEGORY" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        var parentIds = new ArrayList<Long>(); var childIds = new ArrayList<Long>();
        try {
            for (int i = 0; i < 8; i++) {
                var parent = categories.saveAndFlush(com.catshop.entity.Category.builder().name("外頁父分類" + i).slug(prefix + "-parent-" + i).sortOrder(1000).build());
                parentIds.add(parent.getId());
                var child = categories.saveAndFlush(com.catshop.entity.Category.builder().name(prefix + "-child-" + i)
                        .slug(prefix + "-child-" + i).parent(parent).sortOrder(i).build());
                childIds.add(child.getId());
                for (int j = 0; j < 3; j++) products.saveAndFlush(com.catshop.entity.Product.builder().category(child)
                        .name("計數商品").slug(prefix + "-product-" + i + "-" + j).basePrice(java.math.BigDecimal.TEN)
                        .isActive(j != 2).status(j == 1 ? "OFF_SHELF" : "ON_SALE").build());
            }
            String keyword = prefix + "-child";
            var one = queryBudget("admin categories size 1", 3, () -> adminCategories.list(keyword, null, 0, 1));
            var six = queryBudget("admin categories size 6", 3, () -> {
                var page = adminCategories.list(keyword, null, 0, 6);
                assertThat(page.totalElements()).isEqualTo(8);
                assertThat(page.content()).hasSize(6).allSatisfy(c -> {
                    assertThat(c.parentName()).startsWith("外頁父分類"); assertThat(c.productCount()).isEqualTo(2);
                });
            });
            assertThat(six).isEqualTo(one);
            var special = categories.saveAndFlush(com.catshop.entity.Category.builder().name(prefix + "%_! [VIP]").slug(prefix + "-special").build());
            childIds.add(special.getId());
            for (String literal : List.of("%", "_", "!", "[", "[VIP]")) {
                var result = adminCategories.list(literal, null, 0, 100);
                assertThat(result.content()).extracting(c -> c.id()).containsExactly(special.getId());
            }
        } finally {
            for (var id : childIds) { jdbc.update("DELETE FROM product WHERE category_id = ?", id); jdbc.update("DELETE FROM category WHERE id = ?", id); }
            for (var id : parentIds) jdbc.update("DELETE FROM category WHERE id = ?", id);
        }
    }

    private long queryBudget(String label, int budget, Runnable action) {
        var stats = entityManagerFactory.unwrap(org.hibernate.SessionFactory.class).getStatistics();
        stats.clear(); action.run();
        long count = stats.getPrepareStatementCount();
        System.out.println("SQL QUERY BUDGET: " + label + " = " + count);
        assertThat(count).as(label).isLessThanOrEqualTo(budget);
        return count;
    }

    @Test
    void concurrentFirstCartReadsReturnOneCartOnSqlServer() throws Exception {
        var registration = data(request("POST", "/api/auth/register", Map.of("email", "cart-race-" + UUID.randomUUID() + "@example.com",
                "password", "testpassword123", "name", "購物車併發測試"), null), 201);
        var token = registration.path("token").asText();
        var memberId = registration.path("member").path("id").asLong();
        var pool = java.util.concurrent.Executors.newFixedThreadPool(8);
        var ready = new java.util.concurrent.CountDownLatch(8); var start = new java.util.concurrent.CountDownLatch(1);
        try {
            var futures = new ArrayList<java.util.concurrent.Future<Long>>();
            for (int i = 0; i < 8; i++) futures.add(pool.submit(() -> {
                ready.countDown(); if (!start.await(5, java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("start timeout");
                return data(request("GET", "/api/cart", null, token), 200).path("id").asLong();
            }));
            assertThat(ready.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue(); start.countDown();
            var ids = new HashSet<Long>();
            for (var future : futures) ids.add(future.get(20, java.util.concurrent.TimeUnit.SECONDS));
            assertThat(ids).hasSize(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cart WHERE member_id = ?", Integer.class, memberId)).isEqualTo(1);
        } finally { start.countDown(); pool.shutdownNow(); }
    }

    @Test
    void listQueriesStayBoundedOnRealSqlServer() {
        var oneProduct = queryBudget("public products size 1", 3, () -> assertThat(catalog.search(null, null, 0, 1, "name,asc").content()).hasSize(1));
        var sixProducts = queryBudget("public products size 6", 3, () -> assertThat(catalog.search(null, null, 0, 6, "name,asc").content()).hasSize(6));
        assertThat(sixProducts).isEqualTo(oneProduct);
        var oneAdmin = queryBudget("admin products size 1", 4, () -> adminProducts.list(null, null, null, null, 0, 1, "name,asc"));
        var sixAdmin = queryBudget("admin products size 6", 4, () -> {
            var page = adminProducts.list(null, null, null, null, 0, 6, "name,asc");
            assertThat(page.content()).hasSize(6).allSatisfy(p -> {
                assertThat(p.categoryName()).isNotBlank(); assertThat(p.skuCount()).isGreaterThan(0);
                assertThat(p.totalStock()).isGreaterThan(0); assertThat(p.primaryImageUrl()).isNotBlank();
            });
        });
        assertThat(sixAdmin).isEqualTo(oneAdmin);
        var prefix = "PERF" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        List<Long> memberIds = new ArrayList<>(), orderIds = new ArrayList<>();
        try {
            for (int i = 0; i < 8; i++) {
                var member = members.saveAndFlush(com.catshop.entity.Member.builder().email(prefix + i + "@test.local").name("效能會員" + i).passwordHash("test-hash").build());
                memberIds.add(member.getId());
                var order = orders.saveAndFlush(com.catshop.entity.Order.builder().member(member).orderNo(prefix + i)
                        .totalAmount(java.math.BigDecimal.valueOf(60)).receiverName("測試").receiverPhone("0912345678").shippingAddress("測試地址").build());
                orderIds.add(order.getId());
                for (int j = 0; j < 2; j++) orderItems.saveAndFlush(com.catshop.entity.OrderItem.builder().order(order)
                        .productName("快照").unitPrice(java.math.BigDecimal.TEN).quantity(3).subtotal(java.math.BigDecimal.valueOf(30)).build());
            }
            var oneOrder = queryBudget("admin orders size 1", 3, () -> adminOrders.list(null, null, prefix, null, null, 0, 1, "orderNo,asc"));
            var sixOrders = queryBudget("admin orders size 6", 3, () -> {
                var page = adminOrders.list(null, null, prefix, null, null, 0, 6, "orderNo,asc");
                assertThat(page.totalElements()).isEqualTo(8);
                assertThat(page.content()).hasSize(6).allSatisfy(o -> {
                    assertThat(o.memberName()).startsWith("效能會員"); assertThat(o.itemCount()).isEqualTo(2);
                });
            });
            assertThat(sixOrders).isEqualTo(oneOrder);
        } finally {
            // Only fixture IDs in this preflight-guarded disposable test database are removed.
            for (var id : orderIds) { jdbc.update("DELETE FROM order_item WHERE order_id = ?", id); jdbc.update("DELETE FROM orders WHERE id = ?", id); }
            for (var id : memberIds) jdbc.update("DELETE FROM member WHERE id = ?", id);
        }
    }

    @Test
    void cleanupUsesBoundedSqlServerQueriesAndPreservesUnexpiredSessions() {
        var cutoff = java.time.LocalDateTime.now(java.time.Clock.systemUTC()).withNano(0);
        var memberId = jdbc.queryForObject("SELECT id FROM member WHERE email = 'customer@catshop.local'", Long.class);
        var ids = new ArrayList<String>();
        try {
            for (int i = 0; i < 5; i++) {
                var id = UUID.randomUUID().toString(); ids.add(id);
                // Use the application's Hibernate UTC timestamp binding, not a different raw JDBC binding.
                var session = new com.catshop.entity.AuthSession();
                session.setId(id); session.setMemberId(memberId); session.setTokenHash("a".repeat(64));
                session.setExpiresAt(i < 3 ? cutoff : cutoff.plusDays(1)); session.setRevoked(i == 1 || i == 4);
                authSessions.saveAndFlush(session);
                assertThat(authSessions.findById(id).orElseThrow().getExpiresAt()).isEqualTo(session.getExpiresAt());
            }
            assertThat(cleanup.deleteExpiredBatch(cutoff, 2)).isEqualTo(2);
            assertThat(cleanup.deleteExpiredBatch(cutoff, 2)).isEqualTo(1);
            assertThat(cleanup.deleteExpiredBatch(cutoff, 2)).isZero();
            for (int i = 0; i < 5; i++) {
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM auth_session WHERE id = ?", Integer.class, ids.get(i)))
                        .isEqualTo(i < 3 ? 0 : 1);
            }
        } finally {
            for (var id : ids) jdbc.update("DELETE FROM auth_session WHERE id = ?", id);
        }
    }
    private final HttpClient client = HttpClient.newBuilder()
            .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).connectTimeout(Duration.ofSeconds(10)).build();

    private static String required(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) throw new IllegalStateException("Set " + key + " to run SQL Server integration tests");
        return value;
    }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) throws Exception {
        String url = required("CATSHOP_IT_JDBC_URL");
        String name = TestDatabaseGuard.databaseName(url);
        String user = required("CATSHOP_IT_DB_USER");
        String password = required("CATSHOP_IT_DB_PASSWORD");
        // Preflight before Spring/Flyway can write anything. No DROP, clean or schema creation here.
        try (Connection connection = DriverManager.getConnection(url, user, password);
             Statement statement = connection.createStatement()) {
            try (ResultSet result = statement.executeQuery("SELECT DB_NAME(), CONVERT(int, SERVERPROPERTY('ProductMajorVersion'))")) {
                result.next();
                if (!name.equals(result.getString(1))) throw new IllegalStateException("Connected database does not match the isolated test name");
                if (result.getInt(2) != 16 && !"true".equals(System.getenv("CATSHOP_IT_ALLOW_OTHER_VERSION")))
                    throw new IllegalStateException("SQL Server 2022 (major 16) is required; another version cannot satisfy acceptance");
            }
            try (ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM sys.tables WHERE is_ms_shipped = 0")) {
                result.next();
                if (result.getInt(1) != 0) throw new IllegalStateException("Integration database must be empty; existing data will not be touched");
            }
        }
        properties.add("spring.datasource.url", () -> url);
        properties.add("spring.datasource.username", () -> user);
        properties.add("spring.datasource.password", () -> password);
        properties.add("spring.datasource.driver-class-name", () -> "com.microsoft.sqlserver.jdbc.SQLServerDriver");
        properties.add("app.jwt.secret", () -> "isolated-integration-test-secret-not-for-production-32bytes");
    }

    private HttpResponse<String> request(String method, String path, Object body, String token) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(20)).header("Content-Type", "application/json");
        if (token != null) builder.header("Authorization", "Bearer " + token);
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body), StandardCharsets.UTF_8));
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }
    private JsonNode data(HttpResponse<String> response, int status) throws Exception {
        assertThat(response.statusCode()).isEqualTo(status);
        JsonNode envelope = json.readTree(response.body());
        assertThat(envelope.path("success").asBoolean()).isTrue();
        assertThat(envelope.path("code").asInt()).isEqualTo(status);
        return envelope.path("data");
    }

    @Test void flywayValidateAndRealHttpCheckoutCancellationAndRevocation() throws Exception {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1", Integer.class)).isEqualTo(13);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM product_image WHERE url LIKE '/product-images/%.png' AND alt_text LIKE N'%商品示意圖%'", Integer.class)).isEqualTo(10);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM product", Integer.class)).isEqualTo(10);
        assertThat(jdbc.queryForObject("SELECT name FROM category WHERE slug = 'food'", String.class)).isEqualTo("飼料");
        data(request("GET", "/api/health", null, null), 200);
        var registration = request("POST", "/api/auth/register", Map.of("email", "it-" + UUID.randomUUID() + "@example.com",
                "password", "testpassword123", "name", "整合測試會員", "phone", "0912345678"), null);
        String token = data(registration, 201).path("token").asText();
        assertThat(registration.headers().firstValue("set-cookie").orElseThrow()).contains("HttpOnly", "SameSite=Strict");
        assertThat(data(request("GET", "/api/members/me", null, token), 200).path("name").asText()).isEqualTo("整合測試會員");
        var sku = jdbc.queryForMap("SELECT TOP 1 id, stock, price FROM product_sku WHERE is_active = 1 AND stock >= 2 ORDER BY id");
        long skuId = ((Number) sku.get("id")).longValue(); int before = ((Number) sku.get("stock")).intValue();
        data(request("POST", "/api/cart/items", Map.of("skuId", skuId, "quantity", 2), token), 201);
        JsonNode order = data(request("POST", "/api/orders", Map.of("receiverName", "收件人", "receiverPhone", "0912345678",
                "shippingAddress", "台北市測試路一號", "paymentMethod", "MOCK"), token), 201);
        long orderId = order.path("id").asLong();
        assertThat(order.path("status").asText()).isEqualTo("PAID");
        assertThat(order.path("items").get(0).path("unitPrice").decimalValue()).isEqualByComparingTo(sku.get("price").toString());
        assertThat(jdbc.queryForObject("SELECT stock FROM product_sku WHERE id = ?", Integer.class, skuId)).isEqualTo(before - 2);
        assertThat(data(request("GET", "/api/cart", null, token), 200).path("items").size()).isZero();
        data(request("POST", "/api/orders/" + orderId + "/cancel", null, token), 200);
        assertThat(jdbc.queryForObject("SELECT stock FROM product_sku WHERE id = ?", Integer.class, skuId)).isEqualTo(before);
        assertThat(request("POST", "/api/orders/" + orderId + "/cancel", null, token).statusCode()).isEqualTo(400);
        String refreshed = data(request("POST", "/api/auth/refresh", Map.of(), null), 200).path("token").asText();
        assertThat(refreshed).isNotEqualTo(token);
        data(request("POST", "/api/auth/logout", Map.of(), null), 200);
        assertThat(request("GET", "/api/members/me", null, refreshed).statusCode()).isEqualTo(401);
        if ("true".equals(System.getenv("CATSHOP_IT_BROWSER"))) {
            var frontend = java.nio.file.Path.of(System.getProperty("user.dir")).resolve("../frontend").normalize();
            var browserLog = java.nio.file.Path.of(System.getProperty("user.dir")).resolve("target/e2e-browser.log");
            var builder = new ProcessBuilder("node", frontend.resolve("scripts/e2e.cjs").toString())
                    .directory(frontend.toFile()).redirectErrorStream(true).redirectOutput(browserLog.toFile());
            builder.environment().put("E2E_BACKEND_URL", "http://127.0.0.1:" + port);
            var browserTest = builder.start();
            try {
                // Expanded route and pagination flow has a bounded total budget; individual Playwright waits remain unchanged.
                boolean finished=browserTest.waitFor(180, java.util.concurrent.TimeUnit.SECONDS);
                System.out.println(java.nio.file.Files.readString(browserLog, StandardCharsets.UTF_8));
                assertThat(finished).as("Browser integration test must finish within 180 seconds").isTrue();
                assertThat(browserTest.exitValue()).as("Real Vue/SQL Server browser test").isZero();
            } finally { if (browserTest.isAlive()) browserTest.destroyForcibly(); }
        }
    }
}
