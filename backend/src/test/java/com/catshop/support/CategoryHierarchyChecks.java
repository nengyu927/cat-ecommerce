package com.catshop.support;

import com.catshop.dto.admin.category.CreateCategoryRequest;
import com.catshop.dto.admin.category.UpdateCategoryRequest;
import com.catshop.exception.BusinessException;
import com.catshop.service.admin.AdminCategoryService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;

/** Shared assertions run against H2 and real SQL Server, with fixture-only cleanup. */
public final class CategoryHierarchyChecks {
    private CategoryHierarchyChecks() {}

    public static void descendants(AdminCategoryService service, JdbcTemplate jdbc) {
        try (var f = new Fixture(service, jdbc)) {
            long root = f.create(null), child = f.create(root), grandchild = f.create(child);
            jdbc.update("UPDATE category SET is_active = ? WHERE id = ?", false, grandchild);
            for (long parent : List.of(child, grandchild)) {
                conflict(() -> f.move(root, parent), "CATEGORY_HAS_CHILDREN");
                assertThat(f.parent(root)).isNull();
                assertThat(jdbc.queryForObject("SELECT name FROM category WHERE id = ?", String.class, root))
                        .isEqualTo("分類 " + rootSlug(f, root));
            }
        }
    }

    private static String rootSlug(Fixture f, long id) {
        return f.jdbc.queryForObject("SELECT slug FROM category WHERE id = ?", String.class, id);
    }

    public static void validMoves(AdminCategoryService service, JdbcTemplate jdbc) {
        try (var f = new Fixture(service, jdbc)) {
            long root = f.create(null), child = f.create(root), other = f.create(null);
            f.move(child, other);
            assertThat(f.parent(child)).isEqualTo(other);
            f.move(child, null);
            assertThat(f.parent(child)).isNull();
            f.move(root, child);
            assertThat(f.parent(root)).isEqualTo(child);
        }
    }

    public static void selfAndMissing(AdminCategoryService service, JdbcTemplate jdbc) {
        try (var f = new Fixture(service, jdbc)) {
            long root = f.create(null);
            conflict(() -> f.move(root, root), "CATEGORY_HAS_CHILDREN");
            assertThatThrownBy(() -> f.move(root, Long.MAX_VALUE)).isInstanceOfSatisfying(BusinessException.class, e -> {
                assertThat(e.getCode()).isEqualTo("PARENT_NOT_FOUND");
                assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
            });
            assertThat(f.parent(root)).isNull();
        }
    }

    public static void corruptedAncestors(AdminCategoryService service, JdbcTemplate jdbc) {
        try (var f = new Fixture(service, jdbc)) {
            long a = f.create(null), b = f.create(a), outside = f.create(null);
            // Simulate pre-existing corruption introduced outside the guarded service.
            jdbc.update("UPDATE category SET parent_id = ? WHERE id = ?", b, a);
            conflict(() -> f.move(outside, a), "CATEGORY_HIERARCHY_INVALID");
            conflict(() -> f.create(a), "CATEGORY_HIERARCHY_INVALID");
            assertThat(f.parent(outside)).isNull();
            // Moving an affected node to the root is an intentional repair path.
            f.move(a, null);
            f.move(outside, b);
            assertThat(f.parent(outside)).isEqualTo(b);
        }
    }

    public static void concurrentMoves(AdminCategoryService service, JdbcTemplate jdbc) throws Exception {
        try (var f = new Fixture(service, jdbc)) {
            long a = f.create(null), b = f.create(null);
            var pool = Executors.newFixedThreadPool(2);
            var ready = new CountDownLatch(2); var start = new CountDownLatch(1);
            try {
                var futures = new ArrayList<Future<String>>();
                for (var pair : List.of(new long[]{a, b}, new long[]{b, a})) futures.add(pool.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("start timeout");
                    try { f.move(pair[0], pair[1]); return "saved"; }
                    catch (BusinessException e) {
                        assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                        return e.getCode();
                    }
                }));
                assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue(); start.countDown();
                var results = new ArrayList<String>();
                for (var future : futures) results.add(future.get(30, TimeUnit.SECONDS));
                assertThat(results).containsExactlyInAnyOrder("saved", "CATEGORY_HAS_CHILDREN");
                assertThat(f.parent(a) == null || f.parent(b) == null).isTrue();
            } finally {
                start.countDown(); pool.shutdownNow();
                assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
            }
        }
    }

    private static void conflict(Runnable action, String code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class, e -> {
            assertThat(e.getCode()).isEqualTo(code);
            assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        });
    }

    private static final class Fixture implements AutoCloseable {
        final AdminCategoryService service;
        final JdbcTemplate jdbc;
        final String prefix = "hierarchy-" + UUID.randomUUID().toString().replace("-", "");
        final List<Long> ids = new ArrayList<>();
        Fixture(AdminCategoryService service, JdbcTemplate jdbc) { this.service = service; this.jdbc = jdbc; }
        long create(Long parentId) {
            String slug = prefix + "-" + ids.size();
            long id = service.create(new CreateCategoryRequest("分類 " + slug, slug, parentId, 0, true)).id();
            ids.add(id); return id;
        }
        void move(long id, Long parentId) {
            String slug = jdbc.queryForObject("SELECT slug FROM category WHERE id = ?", String.class, id);
            service.update(id, new UpdateCategoryRequest("已修改", slug, parentId, 1, true));
        }
        Long parent(long id) { return jdbc.queryForObject("SELECT parent_id FROM category WHERE id = ?", Long.class, id); }
        @Override public void close() {
            for (var id : ids) jdbc.update("UPDATE category SET parent_id = NULL WHERE id = ?", id);
            for (var id : ids) jdbc.update("DELETE FROM category WHERE id = ?", id);
        }
    }
}
