package com.catshop.common;

import com.catshop.dto.order.CreateOrderRequest;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class OrderRequestIdentityTest {
    @Test void confirmedAmountsAreHashedCanonicallyAndCannotChangeOnReplay() {
        var a=new com.catshop.dto.order.CheckoutAmounts(new java.math.BigDecimal("100.00"),java.math.BigDecimal.ZERO,
                new java.math.BigDecimal("80.0"),new java.math.BigDecimal("180"),"1:1:100");
        var b=new com.catshop.dto.order.CheckoutAmounts(new java.math.BigDecimal("100"),new java.math.BigDecimal("0.00"),
                new java.math.BigDecimal("80"),new java.math.BigDecimal("180.00"),"1:1:100");
        var c=new com.catshop.dto.order.CheckoutAmounts(new java.math.BigDecimal("100"),java.math.BigDecimal.ZERO,
                new java.math.BigDecimal("81"),new java.math.BigDecimal("181"),"1:1:100");
        var ra=new CreateOrderRequest("姓名","電話","地址",null,"COD",null,null,"[[1,1]]",a);
        var rb=new CreateOrderRequest("姓名","電話","地址",null,"COD",null,null,"[[1,1]]",b);
        var rc=new CreateOrderRequest("姓名","電話","地址",null,"COD",null,null,"[[1,1]]",c);
        assertThat(OrderRequestIdentity.hash(ra)).isEqualTo(OrderRequestIdentity.hash(rb)).isNotEqualTo(OrderRequestIdentity.hash(rc));
        assertThat(OrderRequestIdentity.hash(ra)).isNotEqualTo(OrderRequestIdentity.hash(
                new CreateOrderRequest("姓名","電話","地址",null,"COD",null,null,"[[1,1]]")));
    }
    @Test void snapshotIsFramedIntoHashWhileLegacyHashRemainsStable() {
        var legacy = new CreateOrderRequest("姓名","電話","地址",null,"COD");
        var a = new CreateOrderRequest("姓名","電話","地址",null,"COD",null,null,"[[1,2]]");
        var b = new CreateOrderRequest("姓名","電話","地址",null,"COD",null,null,"[[1,3]]");
        assertThat(OrderRequestIdentity.hash(a)).isNotEqualTo(OrderRequestIdentity.hash(b))
                .isNotEqualTo(OrderRequestIdentity.hash(legacy));
        assertThat(OrderRequestIdentity.hash(legacy)).isEqualTo(OrderRequestIdentity.hash(
                new CreateOrderRequest("姓名","電話","地址",null,"COD",null,null,null)));
    }
    @Test void uuidValidationAndCaseNormalization() {
        assertThat(OrderRequestIdentity.normalize(null)).isNull();
        assertThat(OrderRequestIdentity.normalize("ABCDEFAB-1234-1234-1234-ABCDEFABCDEF"))
                .isEqualTo("abcdefab-1234-1234-1234-abcdefabcdef");
        for (String value : new String[]{"", " ", "1-1-1-1-1", "abcdefab-1234-1234-1234-abcdefabcdef "}) {
            assertThatThrownBy(() -> OrderRequestIdentity.normalize(value)).hasMessageContaining("UUID");
        }
    }
    @Test void framedFieldsCannotCollideByConcatenation() {
        var a = new CreateOrderRequest("ab","c","地址",null,"COD");
        var b = new CreateOrderRequest("a","bc","地址",null,"COD");
        assertThat(OrderRequestIdentity.hash(a)).hasSize(64).isNotEqualTo(OrderRequestIdentity.hash(b));
    }
    @Test void identityIsNotPartOfBodyHashAndNullDiffersFromEmpty() {
        var a = new CreateOrderRequest("姓名","電話","地址",null,"COD",null,"abcdefab-1234-1234-1234-abcdefabcdef");
        var b = new CreateOrderRequest("姓名","電話","地址",null,"COD");
        var c = new CreateOrderRequest("姓名","電話","地址","","COD");
        assertThat(OrderRequestIdentity.hash(a)).isEqualTo(OrderRequestIdentity.hash(b)).isNotEqualTo(OrderRequestIdentity.hash(c));
    }
}
