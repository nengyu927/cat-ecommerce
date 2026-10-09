package com.catshop.common;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Keep IN-query parameters well below SQL Server's parameter limit, even for large admin pages. */
public final class IdBatches {
    private static final int SIZE = 500;
    private IdBatches() {}
    public static List<List<Long>> of(Collection<Long> ids) {
        var values = List.copyOf(ids);
        List<List<Long>> result = new ArrayList<>();
        for (int from = 0; from < values.size(); from += SIZE)
            result.add(values.subList(from, Math.min(from + SIZE, values.size())));
        return List.copyOf(result);
    }
}
