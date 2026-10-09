package com.catshop;

import com.catshop.entity.*;
import com.catshop.repository.*;
import com.catshop.service.WishlistService;
import com.catshop.exception.BusinessException;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
 "spring.profiles.active=test", "spring.datasource.url=jdbc:h2:mem:wishlist;MODE=MSSQLServer;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
 "spring.datasource.username=sa", "spring.datasource.password=", "spring.datasource.driver-class-name=org.h2.Driver",
 "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect", "spring.jpa.hibernate.ddl-auto=create-drop", "spring.flyway.enabled=false",
 "app.auth.cleanup.enabled=false", "logging.level.org.hibernate.SQL=warn"
})
@AutoConfigureMockMvc
class WishlistIntegrationTest {
 @Autowired WishlistRepository wishlist;
 @Autowired WishlistService service;
 @Autowired MemberRepository members;
 @Autowired ProductRepository products;
 @Autowired CategoryRepository categories;
 @Autowired ProductImageRepository images;
 @Autowired MockMvc mvc;
 Member a, b; Product p; Category c;
 @BeforeEach void setup() {
   wishlist.deleteAll(); images.deleteAll(); products.deleteAll(); categories.deleteAll(); members.deleteAll();
   a = members.saveAndFlush(Member.builder().name("A").email("a@wishlist.local").passwordHash("hash").build());
   b = members.saveAndFlush(Member.builder().name("B").email("b@wishlist.local").passwordHash("hash").build());
   c = categories.saveAndFlush(Category.builder().name("飼料").slug("wishlist-food").build());
   p = products.saveAndFlush(Product.builder().name("收藏商品").slug("wishlist-product").category(c).basePrice(BigDecimal.TEN).build());
 }
 @Test void addAndRemoveAreIdempotentAndMemberScoped() {
   assertThat(service.add(a.getId(), p.getId())).containsExactly(p.getId());
   service.add(a.getId(), p.getId()); assertThat(wishlist.count()).isEqualTo(1);
   assertThat(service.ids(b.getId())).isEmpty(); service.remove(b.getId(), p.getId());
   assertThat(service.ids(a.getId())).containsExactly(p.getId());
   service.remove(a.getId(), p.getId()); service.remove(a.getId(), p.getId()); assertThat(wishlist.count()).isZero();
 }
 @Test void savedUnavailableProductIsRetainedAndCanBeRemoved() {
   service.add(a.getId(), p.getId()); p.setIsActive(false); products.saveAndFlush(p);
   var page = service.list(a.getId(), 0, 12); assertThat(page.content()).hasSize(1);
   assertThat(page.content().get(0).available()).isFalse();
   assertThat(service.add(a.getId(), p.getId())).containsExactly(p.getId());
   assertThatThrownBy(() -> service.add(b.getId(), p.getId())).isInstanceOf(BusinessException.class);
   service.remove(a.getId(), p.getId()); assertThat(service.ids(a.getId())).isEmpty();
 }
 @Test void inactiveCategoryAndInvalidIdsAreRejected() {
   c.setIsActive(false); categories.saveAndFlush(c);
   assertThatThrownBy(() -> service.add(a.getId(), p.getId())).isInstanceOf(BusinessException.class);
   assertThatThrownBy(() -> service.add(a.getId(), Long.MAX_VALUE)).isInstanceOf(BusinessException.class);
   assertThatThrownBy(() -> service.add(a.getId(), 0L)).isInstanceOf(BusinessException.class);
   assertThatThrownBy(() -> service.remove(a.getId(), -1L)).isInstanceOf(BusinessException.class);
 }
 @Test void pagedListUsesCurrentPriceAndDeterministicPrimaryImage() {
   images.saveAndFlush(ProductImage.builder().product(p).url("later").sortOrder(2).isPrimary(true).build());
   images.saveAndFlush(ProductImage.builder().product(p).url("first").sortOrder(1).isPrimary(true).build());
   service.add(a.getId(), p.getId()); p.setBasePrice(BigDecimal.valueOf(25)); products.saveAndFlush(p);
   var result = service.list(a.getId(), 0, 1);
   assertThat(result.content().get(0).basePrice()).isEqualByComparingTo("25");
   assertThat(result.content().get(0).primaryImageUrl()).isEqualTo("first");
   assertThat(result.totalElements()).isEqualTo(1); assertThat(service.list(a.getId(), 1, 1).content()).isEmpty();
   assertThatThrownBy(() -> service.list(a.getId(), -1, 12)).isInstanceOf(BusinessException.class);
   assertThatThrownBy(() -> service.list(a.getId(), 0, 101)).isInstanceOf(BusinessException.class);
 }
 @Test void parallelSameProductAddsOnlyOneRow() throws Exception {
   var pool = Executors.newFixedThreadPool(8); var start = new CountDownLatch(1);
   try {
     var jobs = new ArrayList<Future<List<Long>>>();
     for (int i = 0; i < 8; i++) jobs.add(pool.submit(() -> { start.await(); return service.add(a.getId(), p.getId()); }));
     start.countDown(); for (var job : jobs) assertThat(job.get(30, TimeUnit.SECONDS)).containsExactly(p.getId());
     assertThat(wishlist.count()).isEqualTo(1);
   } finally { start.countDown(); pool.shutdownNow(); assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue(); }
 }
 @Test void capacityLimitDoesNotRejectAnExistingFavorite() {
   var records = new ArrayList<WishlistItem>();
   for (int i = 0; i < 500; i++) {
     var product = products.save(Product.builder().name("商品" + i).slug("limit-" + i).category(c).basePrice(BigDecimal.ONE).build());
     records.add(WishlistItem.builder().member(a).product(product).build());
   }
   wishlist.saveAllAndFlush(records);
   assertThatThrownBy(() -> service.add(a.getId(), p.getId())).isInstanceOf(BusinessException.class).hasMessageContaining("500");
   assertThat(service.add(a.getId(), records.get(0).getProduct().getId())).hasSize(500);
 }
 @Test void anonymousRequestsRequireLogin() throws Exception {
   mvc.perform(get("/api/wishlist")).andExpect(status().isUnauthorized());
   mvc.perform(get("/api/wishlist/ids")).andExpect(status().isUnauthorized());
 }
}
