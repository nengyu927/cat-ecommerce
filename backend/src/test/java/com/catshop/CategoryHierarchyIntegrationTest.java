package com.catshop;

import com.catshop.service.admin.AdminCategoryService;
import com.catshop.support.CategoryHierarchyChecks;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = {
        "spring.profiles.active=test", "spring.datasource.url=jdbc:h2:mem:hierarchy;MODE=MSSQLServer;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.username=sa", "spring.datasource.password=", "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect", "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false", "logging.level.org.hibernate.SQL=warn", "app.auth.cleanup.enabled=false"
})
class CategoryHierarchyIntegrationTest {
    @Autowired AdminCategoryService service;
    @Autowired JdbcTemplate jdbc;
    @Test void rejectsChildrenAndInactiveGrandchildrenWithoutPartialUpdates() { CategoryHierarchyChecks.descendants(service, jdbc); }
    @Test void permitsSiblingAndRootMoves() { CategoryHierarchyChecks.validMoves(service, jdbc); }
    @Test void retainsSelfAndMissingParentErrors() { CategoryHierarchyChecks.selfAndMissing(service, jdbc); }
    @Test void rejectsExistingAncestorCyclesAndAllowsRepair() { CategoryHierarchyChecks.corruptedAncestors(service, jdbc); }
    @Test void serializesOppositeReparents() throws Exception { CategoryHierarchyChecks.concurrentMoves(service, jdbc); }
}
