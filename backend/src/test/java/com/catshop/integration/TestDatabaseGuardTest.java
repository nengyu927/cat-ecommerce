package com.catshop.integration;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class TestDatabaseGuardTest {
    private final String name = "catshop_it_0123456789abcdef0123456789abcdef";
    @Test void acceptsOnlyExplicitUniqueTestDatabase() {
        assertThat(TestDatabaseGuard.databaseName("jdbc:sqlserver://localhost:1433;databaseName=" + name + ";encrypt=true"))
                .isEqualTo(name);
    }
    @Test void refusesBusinessDatabaseMissingNameAndCredentialsInUrl() {
        for (String url : new String[]{"jdbc:sqlserver://localhost;databaseName=catshop", "jdbc:sqlserver://localhost",
                "jdbc:h2:mem:test", "jdbc:sqlserver://localhost;databaseName=" + name + ";password=secret"}) {
            assertThatThrownBy(() -> TestDatabaseGuard.databaseName(url)).isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> TestDatabaseGuard.databaseName(null)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void refusesDuplicateNamesEvenIfOneLooksSafe() {
        assertThatThrownBy(() -> TestDatabaseGuard.databaseName("jdbc:sqlserver://localhost;databaseName=" + name
                + ";databaseName=catshop")).isInstanceOf(IllegalArgumentException.class);
    }
}
