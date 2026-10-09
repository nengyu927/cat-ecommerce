package com.catshop.common;

import com.catshop.exception.BusinessException;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.assertj.core.api.Assertions.*;

class AdminListParametersTest {
    @Test void pagingBounds() {
        AdminListParameters.paging(0, 1);
        AdminListParameters.paging(Integer.MAX_VALUE, 100);
        for (int[] args : new int[][]{{-1,20},{0,0},{0,101}})
            assertThatThrownBy(() -> AdminListParameters.paging(args[0], args[1])).isInstanceOf(BusinessException.class);
    }
    @Test void strictSortAndUniqueTieBreaker() {
        for (String value : new String[]{"", "name", "name,", "name,other", "name,asc,extra", "password,asc"})
            assertThatThrownBy(() -> AdminListParameters.sort(value, Set.of("createdAt","name","id"))).isInstanceOf(BusinessException.class);
        assertThat(AdminListParameters.sort(null, Set.of("createdAt","id")).toString()).isEqualTo("createdAt: DESC,id: DESC");
        assertThat(AdminListParameters.sort(" name , ASC ", Set.of("name","id")).toString()).isEqualTo("name: ASC,id: DESC");
        assertThat(AdminListParameters.sort("id,asc", Set.of("id")).toString()).isEqualTo("id: ASC");
    }
    @Test void textLength() {
        AdminListParameters.text(null,200,"關鍵字");
        AdminListParameters.text("x".repeat(200),200,"關鍵字");
        assertThatThrownBy(() -> AdminListParameters.text("x".repeat(201),200,"關鍵字")).isInstanceOf(BusinessException.class);
    }
}
