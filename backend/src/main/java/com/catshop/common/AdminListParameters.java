package com.catshop.common;

import com.catshop.exception.BusinessException;
import org.springframework.data.domain.Sort;
import java.util.Locale;
import java.util.Set;

/** Validates queries before constructing a database page or sort. */
public final class AdminListParameters {
    private AdminListParameters() {}

    public static void paging(int page, int size) {
        if (page < 0 || size < 1 || size > 100)
            throw BusinessException.badRequest("頁碼不可為負數，分頁大小須為 1 至 100");
    }

    public static void text(String value, int max, String label) {
        if (value != null && value.length() > max)
            throw BusinessException.badRequest(label + "不可超過 " + max + " 字");
    }

    public static Sort sort(String value, Set<String> fields) {
        String raw = value == null ? "createdAt,desc" : value;
        String[] parts = raw.split(",", -1);
        if (parts.length != 2 || !fields.contains(parts[0].trim()))
            throw BusinessException.badRequest("排序須為允許的欄位及 asc／desc");
        String direction = parts[1].trim().toLowerCase(Locale.ROOT);
        if (!Set.of("asc", "desc").contains(direction))
            throw BusinessException.badRequest("排序方向須為 asc 或 desc");
        String field = parts[0].trim();
        Sort result = Sort.by(Sort.Direction.fromString(direction), field);
        // A unique tie breaker prevents equal values from moving between pages.
        return field.equals("id") ? result : result.and(Sort.by(Sort.Direction.DESC, "id"));
    }
}
