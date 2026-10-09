package com.catshop.common;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.stream.LongStream;
import static org.assertj.core.api.Assertions.*;

class IdBatchesTest {
    @Test void skipsEmptyCollection() { assertThat(IdBatches.of(List.of())).isEmpty(); }
    @Test void preservesIdsAndLimitsEachQueryBatch() {
        var ids = LongStream.rangeClosed(1, 2101).boxed().toList();
        var batches = IdBatches.of(ids);
        assertThat(batches).hasSize(5).allSatisfy(batch -> assertThat(batch.size()).isLessThanOrEqualTo(500));
        assertThat(batches.stream().flatMap(List::stream).toList()).containsExactlyElementsOf(ids);
    }
}
