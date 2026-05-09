package com.chancemoreland.create_coinmarket.data;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.Locale;

@SuppressWarnings("deprecation")
public final class AuctionServerConfig {
    public static final ModConfigSpec SPEC;

    private static final ModConfigSpec.BooleanValue DATABASE_ENABLED;
    private static final ModConfigSpec.ConfigValue<String> DATABASE_MODE;
    private static final ModConfigSpec.ConfigValue<String> DATABASE_HOST;
    private static final ModConfigSpec.IntValue DATABASE_PORT;
    private static final ModConfigSpec.ConfigValue<String> DATABASE_NAME;
    private static final ModConfigSpec.ConfigValue<String> DATABASE_SCHEMA;
    private static final ModConfigSpec.ConfigValue<String> DATABASE_USERNAME;
    private static final ModConfigSpec.ConfigValue<String> DATABASE_PASSWORD;
    private static final ModConfigSpec.ConfigValue<String> DATABASE_JDBC_URL;
    private static final ModConfigSpec.ConfigValue<String> DATABASE_TABLE_PREFIX;
    private static final ModConfigSpec.IntValue DATABASE_POOL_SIZE;
    private static final ModConfigSpec.IntValue DATABASE_CONNECTION_TIMEOUT_SECONDS;
    private static final ModConfigSpec.BooleanValue DATABASE_AUTO_CREATE_TABLES;
    private static final ModConfigSpec.BooleanValue DATABASE_AUTO_MIGRATE;
    private static final ModConfigSpec.BooleanValue DATABASE_LOG_SQL_ERRORS;
    private static final ModConfigSpec.ConfigValue<String> DATABASE_SQLITE_PATH;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.push("database");
        DATABASE_ENABLED = builder.comment("Create: CoinMarket requires a database. Leave enabled unless intentionally disabling the market.")
            .define("enabled", true);
        DATABASE_MODE = builder.comment("Valid values: sqlite, mysql. SQLite is the default and bundled mode.")
            .define("mode", "sqlite");
        DATABASE_HOST = builder.define("host", "localhost");
        DATABASE_PORT = builder.defineInRange("port", 3306, 1, 65535);
        DATABASE_NAME = builder.define("name", "create_coinmarket");
        DATABASE_SCHEMA = builder.define("schema", "");
        DATABASE_USERNAME = builder.define("username", "");
        DATABASE_PASSWORD = builder.define("password", "");
        DATABASE_JDBC_URL = builder.comment("Optional full JDBC URL override. Leave blank to build one from host/port/name.")
            .define("jdbcUrl", "");
        DATABASE_TABLE_PREFIX = builder.comment("Reserved for hosted SQL installs; leave blank for the bundled schema.")
            .define("tablePrefix", "");
        DATABASE_POOL_SIZE = builder.defineInRange("poolSize", 4, 1, 32);
        DATABASE_CONNECTION_TIMEOUT_SECONDS = builder.defineInRange("connectionTimeoutSeconds", 10, 1, 120);
        DATABASE_AUTO_CREATE_TABLES = builder.define("autoCreateTables", true);
        DATABASE_AUTO_MIGRATE = builder.define("autoMigrate", true);
        DATABASE_LOG_SQL_ERRORS = builder.define("logSqlErrors", true);
        DATABASE_SQLITE_PATH = builder.comment("World-relative SQLite path. {world} is replaced with the active world root.")
            .define("sqlitePath", "{world}/serverconfig/create_coinmarket/coinmarket.db");
        builder.pop();

        SPEC = builder.build();
    }

    private AuctionServerConfig() {
    }

    public static boolean databaseEnabled() {
        return DATABASE_ENABLED.get();
    }

    public static String databaseMode() {
        String mode = DATABASE_MODE.get();
        if (mode == null) {
            return "sqlite";
        }
        String normalized = mode.toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "sqlite", "mysql" -> normalized;
            default -> "sqlite";
        };
    }

    public static String host() {
        return DATABASE_HOST.get();
    }

    public static int port() {
        return DATABASE_PORT.get();
    }

    public static String name() {
        return DATABASE_NAME.get();
    }

    public static String schema() {
        return DATABASE_SCHEMA.get();
    }

    public static String username() {
        return DATABASE_USERNAME.get();
    }

    public static String password() {
        return DATABASE_PASSWORD.get();
    }

    public static String jdbcUrl() {
        return DATABASE_JDBC_URL.get();
    }

    public static String tablePrefix() {
        return DATABASE_TABLE_PREFIX.get();
    }

    public static int poolSize() {
        return DATABASE_POOL_SIZE.get();
    }

    public static int connectionTimeoutSeconds() {
        return DATABASE_CONNECTION_TIMEOUT_SECONDS.get();
    }

    public static boolean autoCreateTables() {
        return DATABASE_AUTO_CREATE_TABLES.get();
    }

    public static boolean autoMigrate() {
        return DATABASE_AUTO_MIGRATE.get();
    }

    public static boolean logSqlErrors() {
        return DATABASE_LOG_SQL_ERRORS.get();
    }

    public static String sqlitePath() {
        return DATABASE_SQLITE_PATH.get();
    }

    public static String maskedPassword() {
        return password().isBlank() ? "<blank>" : "********";
    }
}
