package com.catshop;

import com.catshop.entity.*;
import com.catshop.repository.*;
import com.catshop.service.ProductService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.profiles.active=test", "spring.datasource.url=jdbc:h2:mem:productfilters;MODE=MSSQLServer;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
 "spring.datasource.username=sa", "spring.datasource.password=", "spring.datasource.driver-class-name=org.h2.Driver",
 "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect", "spring.jpa.hibernate.ddl-auto=create-drop", "spring.flyway.enabled=false", "app.auth.cleanup.enabled=false", "logging.level.org.hibernate.SQL=warn"})
@AutoConfigureMockMvc
class ProductFilterIntegrationTest {
 @Autowired ProductRepository products;
 @Autowired CategoryRepository categories;
 @Autowired ProductSkuRepository skus;
 @Autowired ProductService service;
 @Autowired MockMvc mvc;
 Category root, child;
 @BeforeEach void setup() {
   skus.deleteAll(); products.deleteAll(); categories.deleteAll();
   root=categories.saveAndFlush(Category.builder().name("Food").slug("filters-food").build());
   child=categories.saveAndFlush(Category.builder().name("Dry").slug("filters-dry").parent(root).build());
   add("A", "Royal", "100", true, "ON_SALE", child);
   add("B", "Royal", "200", true, "ON_SALE", root);
   add("C", "Royal Plus", "150", true, "ON_SALE", root);
   add("Hidden", "Hidden", "150", false, "ON_SALE", root);
   add("Off", "Off", "150", true, "OFF_SALE", root);
   var inactive=categories.saveAndFlush(Category.builder().name("Inactive").slug("filters-inactive").isActive(false).build());
   add("Inactive", "Inactive", "150", true, "ON_SALE", inactive);
 }
 void add(String name,String brand,String price,boolean active,String status,Category category) {
   products.saveAndFlush(Product.builder().name(name).slug("filters-"+name.toLowerCase()).brand(brand).basePrice(new BigDecimal(price)).isActive(active).status(status).category(category).build());
 }
 @Test void brandAndInclusivePricesCombineWithCategoryDescendantsAndPagination() {
   var result=service.search(root.getId(), null, 0, 1, "price,asc", " royal ", new BigDecimal("100"), new BigDecimal("200"));
   assertThat(result.totalElements()).isEqualTo(2);
   assertThat(result.content().get(0).name()).isEqualTo("A");
   assertThat(service.search(root.getId(),null,1,1,"price,asc","Royal",new BigDecimal("100"),new BigDecimal("200")).content().get(0).name()).isEqualTo("B");
   assertThat(service.search(null,"B",0,12,"name,asc","Royal",null,new BigDecimal("200")).totalElements()).isEqualTo(1);
 }
 @Test void visibleBrandsExcludeInactiveAndOffShelfProducts() {
   assertThat(service.brands()).containsExactly("Royal","Royal Plus");
   assertThat(service.search(null,null,0,12,"price,asc",null,BigDecimal.ZERO,null).totalElements()).isEqualTo(3);
   assertThat(service.search(null,null,0,12,"price,asc","Unknown",null,null).content()).isEmpty();
 }
 @Test void publicHttpApiAcceptsBrandAndBoundsWithoutLogin() throws Exception {
   mvc.perform(get("/api/products").param("brand","Royal").param("minPrice","200").param("maxPrice","200"))
     .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1)).andExpect(jsonPath("$.data.content[0].name").value("B"));
   mvc.perform(get("/api/products/brands")).andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(2));
 }
 @Test void searchLengthLimitsApplyBeforeTrimming() throws Exception {
   for (String invalid : new String[]{"x".repeat(201), " ".repeat(201), " "+"x".repeat(200)}) {
     mvc.perform(get("/api/products").param("keyword",invalid)).andExpect(status().isBadRequest());
   }
   for (String invalid : new String[]{"x".repeat(101), " ".repeat(101), " "+"x".repeat(100)}) {
     mvc.perform(get("/api/products").param("brand",invalid)).andExpect(status().isBadRequest());
   }
   mvc.perform(get("/api/products").param("keyword"," ".repeat(200)).param("brand"," ".repeat(100)))
     .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(3));
   mvc.perform(get("/api/products").param("keyword"," A ").param("brand"," royal "))
     .andExpect(status().isOk()).andExpect(jsonPath("$.data.content[0].name").value("A"));
 }
 @Test void invalidPricesAndSortReturn400() throws Exception {
   for (String value:new String[]{"-1","1.001","100000000","abc"}) mvc.perform(get("/api/products").param("minPrice",value)).andExpect(status().isBadRequest());
   mvc.perform(get("/api/products").param("minPrice","2").param("maxPrice","1")).andExpect(status().isBadRequest());
   mvc.perform(get("/api/products").param("sort",",")).andExpect(status().isBadRequest());
   mvc.perform(get("/api/products").param("categoryId","0")).andExpect(status().isBadRequest());
 }
 void sku(String name,String code,int stock,boolean active) {
   var product=products.findAll().stream().filter(p->p.getName().equals(name)).findFirst().orElseThrow();
   skus.saveAndFlush(ProductSku.builder().product(product).skuCode(code).specName(code).price(BigDecimal.TEN).stock(stock).isActive(active).build());
 }
 @Test void stockFilterRequiresAtLeastOneActivePositiveSkuWithoutDuplicateProducts() {
   sku("A","a-one",1,true); sku("A","a-two",2,true);
   sku("B","b-empty",0,true); sku("B","b-disabled",99,false);
   sku("Hidden","hidden",99,true); sku("Off","off",99,true); sku("Inactive","inactive",99,true);
   var page=service.search(root.getId(),null,0,1,"price,asc","Royal",new BigDecimal("100"),new BigDecimal("200"),true);
   assertThat(page.totalElements()).isEqualTo(1); assertThat(page.content()).hasSize(1); assertThat(page.content().get(0).name()).isEqualTo("A");
   assertThat(service.search(null,null,0,12,"price,asc",null,null,null,false).totalElements()).isEqualTo(3);
   assertThat(service.search(null,null,1,1,"price,asc",null,null,null,true).content()).isEmpty();
 }
 @Test void stockChangesAreReflectedOnTheNextQuery() {
   sku("A","a-stock",1,true);
   assertThat(service.search(null,null,0,12,"price,asc",null,null,null,true).totalElements()).isEqualTo(1);
   var sku=skus.findAll().get(0); sku.setStock(0); sku=skus.saveAndFlush(sku);
   assertThat(service.search(null,null,0,12,"price,asc",null,null,null,true).content()).isEmpty();
   sku.setStock(1); sku=skus.saveAndFlush(sku); sku.setIsActive(false); skus.saveAndFlush(sku);
   assertThat(service.search(null,null,0,12,"price,asc",null,null,null,true).content()).isEmpty();
 }
 @Test void stockHttpParameterIsPublicAndInvalidValuesReturn400() throws Exception {
   sku("A","a-http",1,true);
   mvc.perform(get("/api/products").param("inStock","true")).andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1));
   mvc.perform(get("/api/products").param("inStock","false")).andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(3));
   mvc.perform(get("/api/products").param("inStock","garbage")).andExpect(status().isBadRequest());
 }
}
