package com.catshop.integration;

import java.util.regex.Pattern;

/** Integration tests may mutate only an explicitly supplied, uniquely named empty database. */
final class TestDatabaseGuard {
    private static final Pattern DATABASE = Pattern.compile("(?:^|;)databaseName=(catshop_it_[a-f0-9]{32})(?:;|$)");
    private TestDatabaseGuard() {}
    static String databaseName(String url) {
        if (url == null || !url.startsWith("jdbc:sqlserver://"))
            throw new IllegalArgumentException("CATSHOP_IT_JDBC_URL must be a SQL Server JDBC URL");
        if (url.toLowerCase(java.util.Locale.ROOT).contains("password="))
            throw new IllegalArgumentException("Keep the password in CATSHOP_IT_DB_PASSWORD, not the JDBC URL");
        var matcher = DATABASE.matcher(url);
        if (!matcher.find()) throw new IllegalArgumentException("Database must be named catshop_it_<32 lowercase hex characters>");
        String name = matcher.group(1);
        if (url.split("(?i)databaseName=", -1).length != 2)
            throw new IllegalArgumentException("Duplicate databaseName is forbidden");
        return name;
    }
}
