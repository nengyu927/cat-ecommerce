package com.catshop;

import com.catshop.entity.*;
import com.catshop.repository.*;
import com.catshop.service.admin.AdminInventoryService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.profiles.active=test", "spring.datasource.url=jdbc:h2:mem:lowstock;MODE=MSSQLServer;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
 "spring.datasource.username=sa", "spring.datasource.password=", "spring.datasource.driver-class-name=org.h2.Driver",
 "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect", "spring.jpa.hibernate.ddl-auto=create-drop", "spring.flyway.enabled=false", "app.auth.cleanup.enabled=false", "spring.jpa.properties.hibernate.generate_statistics=true", "logging.level.org.hibernate.SQL=warn", "logging.level.org.hibernate.stat=warn", "logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener=warn"})
@AutoConfigureMockMvc
class LowStockIntegrationTest {
 @Autowired ProductRepository products;
 @Autowired ProductSkuRepository skus;
 @Autowired CategoryRepository categories;
 @Autowired MemberRepository members;
 @Autowired AuthSessionRepository sessions;
 @Autowired PasswordEncoder encoder;
 @Autowired AdminInventoryService service;
 @Autowired MockMvc mvc;
 @Autowired ObjectMapper json;
 @Autowired EntityManagerFactory emf;
 Product p;
 @BeforeEach void setup() {
   sessions.deleteAll(); members.deleteAll(); skus.deleteAll(); products.deleteAll(); categories.deleteAll();
   var c=categories.saveAndFlush(Category.builder().name("Food").slug("low-food").build());
   p=products.saveAndFlush(Product.builder().category(c).name("Low food").slug("low-product").basePrice(BigDecimal.TEN).build());
   sku(p,"ZERO%_[",0,0,true); sku(p,"UNDER",2,5,true); sku(p,"EQUAL",5,5,true); sku(p,"HEALTHY",6,5,true); sku(p,"DISABLED",0,10,false);
   var hidden=products.saveAndFlush(Product.builder().category(c).name("Hidden").slug("low-hidden").basePrice(BigDecimal.TEN).isActive(false).build()); sku(hidden,"HIDDEN",0,5,true);
   var off=products.saveAndFlush(Product.builder().category(c).name("Off shelf").slug("low-off").basePrice(BigDecimal.TEN).status("OFF_SHELF").build()); sku(off,"OFF",1,5,true);
 }
 void sku(Product product,String code,int stock,int safety,boolean active) { skus.saveAndFlush(ProductSku.builder().product(product).skuCode(code).specName(code).price(BigDecimal.TEN).stock(stock).safetyStock(safety).isActive(active).build()); }
 @Test void thresholdsVisibilityAndGlobalCountsDoNotDependOnPage() {
   var data=service.lowStock(null,false,0,2);
   assertThat(data.lowStockCount()).isEqualTo(4); assertThat(data.outOfStockCount()).isEqualTo(1); assertThat(data.items().totalElements()).isEqualTo(4);
   assertThat(data.items().content()).extracting(i->i.skuCode()).containsExactly("ZERO%_[","OFF");
   assertThat(data.items().content().get(1).shortage()).isEqualTo(4);
   var next=service.lowStock(null,false,1,2); assertThat(next.items().content()).extracting(i->i.skuCode()).containsExactly("UNDER","EQUAL"); assertThat(next.lowStockCount()).isEqualTo(4);
   assertThat(next.items().content().get(1).shortage()).isZero();
 }
 @Test void outOnlyAndLiteralSearchRetainCorrectSummaryScope() {
   var data=service.lowStock(null,true,0,20); assertThat(data.items().totalElements()).isEqualTo(1); assertThat(data.lowStockCount()).isEqualTo(4);
   assertThat(service.lowStock("%_[",false,0,20).items().totalElements()).isEqualTo(1);
   assertThat(service.lowStock("low food",false,0,20).lowStockCount()).isEqualTo(3);
   var empty=service.lowStock("unknown",false,0,20); assertThat(empty.lowStockCount()).isZero(); assertThat(empty.outOfStockCount()).isZero(); assertThat(empty.items().content()).isEmpty();
 }
 @Test void replenishmentRemovesAlertOnNextRead() {
   var sku=skus.findBySkuCode("UNDER").orElseThrow(); sku.setStock(6); skus.saveAndFlush(sku);
   assertThat(service.lowStock(null,false,0,20).lowStockCount()).isEqualTo(3);
 }
 @Test void queriesStayBoundedInsteadOfLoadingEverySkuProductSeparately() {
   var stats=emf.unwrap(org.hibernate.SessionFactory.class).getStatistics();
   for (int size:new int[]{1,2}) { stats.clear(); service.lowStock(null,false,0,size); assertThat(stats.getPrepareStatementCount()).isLessThanOrEqualTo(3); }
 }
 String login(String role) throws Exception {
   String email=role.toLowerCase()+"@lowstock.local";
   members.saveAndFlush(Member.builder().email(email).name(role).role(role).passwordHash(encoder.encode("password123")).build());
   var response=mvc.perform(post("/api/auth/login").contentType("application/json").content("{\"email\":\""+email+"\",\"password\":\"password123\"}")).andExpect(status().isOk()).andReturn();
   return "Bearer "+json.readTree(response.getResponse().getContentAsString()).at("/data/token").asText();
 }
 @Test void adminOnlyApiRejectsAnonymousCustomerAndInvalidParameters() throws Exception {
   mvc.perform(get("/api/admin/inventory/low-stock")).andExpect(status().isUnauthorized());
   mvc.perform(get("/api/admin/inventory/low-stock").header("Authorization",login("CUSTOMER"))).andExpect(status().isForbidden());
   String token=login("ADMIN");
   mvc.perform(get("/api/admin/inventory/low-stock").header("Authorization",token)).andExpect(status().isOk()).andExpect(jsonPath("$.data.lowStockCount").value(4));
   for (String size:new String[]{"0","101"}) mvc.perform(get("/api/admin/inventory/low-stock").header("Authorization",token).param("size",size)).andExpect(status().isBadRequest());
   mvc.perform(get("/api/admin/inventory/low-stock").header("Authorization",token).param("keyword","x".repeat(201))).andExpect(status().isBadRequest());
 }
 @Test void movementApiRequiresAdminAndValidFilters() throws Exception {
   String path="/api/admin/inventory/movements";
   mvc.perform(get(path)).andExpect(status().isUnauthorized());
   mvc.perform(get(path).header("Authorization",login("CUSTOMER"))).andExpect(status().isForbidden());
   String token=login("ADMIN");
   mvc.perform(get(path).header("Authorization",token)).andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(0));
   for (String[] pair:new String[][]{{"skuId","0"},{"skuId","-1"},{"skuId","9223372036854775808"},{"reason","INVALID"},{"size","101"},{"page","-1"}})
    mvc.perform(get(path).header("Authorization",token).param(pair[0],pair[1])).andExpect(status().isBadRequest());
 }
}
