package com.chancemoreland.create_coinmarket.data;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@SuppressWarnings("deprecation")
public final class AuctionConfig {
    public static final ModConfigSpec SPEC;

    private static final ModConfigSpec.ConfigValue<String> DATABASE_BACKEND;
    private static final ModConfigSpec.ConfigValue<String> DATABASE_PATH;
    private static final ModConfigSpec.LongValue PUBLIC_LISTING_DURATION_HOURS;
    private static final ModConfigSpec.BooleanValue ADMIN_LISTINGS_EXPIRE;
    private static final ModConfigSpec.LongValue ADMIN_LISTING_DURATION_HOURS;
    private static final ModConfigSpec.DoubleValue PUBLIC_TAX_PERCENT;
    private static final ModConfigSpec.LongValue LISTING_FEE;
    private static final ModConfigSpec.IntValue MAX_LISTINGS_PER_PLAYER;
    private static final ModConfigSpec.BooleanValue ALLOW_BUYING_OWN_LISTINGS;
    private static final ModConfigSpec.BooleanValue REQUIRE_BUY_CONFIRMATION;
    private static final ModConfigSpec.ConfigValue<String> DEFAULT_OPEN_MODE;
    private static final ModConfigSpec.BooleanValue REQUIRE_NUMISMATICS;
    private static final ModConfigSpec.BooleanValue ENABLE_NUMISMATICS_BANK_INTEGRATION;
    private static final ModConfigSpec.ConfigValue<String> PREFERRED_PAYMENT_SOURCE;
    private static final ModConfigSpec.BooleanValue FALLBACK_TO_PHYSICAL_COINS;
    private static final ModConfigSpec.BooleanValue SHOW_BANK_BALANCE_IN_HEADER;
    private static final ModConfigSpec.BooleanValue REQUIRE_BANK_CARD_FOR_BANK_PAYMENTS;
    private static final ModConfigSpec.BooleanValue ALLOW_SELLER_PAYOUT_TO_BANK;
    private static final ModConfigSpec.ConfigValue<String> BANK_INTEGRATION_FAILURE_MODE;
    private static final ModConfigSpec.BooleanValue ADMIN_LISTINGS_ARE_INFINITE_BY_DEFAULT;
    private static final ModConfigSpec.BooleanValue AUTO_CREDIT_SELLER_PROCEEDS;
    private static final ModConfigSpec.ConfigValue<String> PAYOUT_MODE;
    private static final ModConfigSpec.BooleanValue ALLOW_OFFLINE_PAYOUT_ACCRUAL;
    private static final ModConfigSpec.IntValue MAX_PENDING_PAYOUT_ENTRIES;
    private static final ModConfigSpec.LongValue DEFAULT_AUCTION_DURATION_HOURS;
    private static final ModConfigSpec.LongValue MIN_AUCTION_DURATION_HOURS;
    private static final ModConfigSpec.LongValue MAX_AUCTION_DURATION_HOURS;
    private static final ModConfigSpec.LongValue DEFAULT_MIN_BID_INCREMENT;
    private static final ModConfigSpec.IntValue MARKET_HISTORY_DAYS;
    private static final ModConfigSpec.BooleanValue ENABLE_MODERN_UI;
    private static final ModConfigSpec.BooleanValue ENABLE_ECONOMY_DASHBOARD;
    private static final ModConfigSpec.BooleanValue ENABLE_CHARTS;
    private static final ModConfigSpec.IntValue ECONOMY_SNAPSHOT_INTERVAL_MINUTES;
    private static final ModConfigSpec.IntValue MAX_CHART_DAYS;
    private static final ModConfigSpec.IntValue MAX_LEADERBOARD_ENTRIES;
    private static final ModConfigSpec.IntValue DEFAULT_MARKET_PAGE_SIZE;
    private static final ModConfigSpec.BooleanValue ENABLE_LEGACY_CHEST_UI;
    private static final ModConfigSpec.ConfigValue<String> UI_THEME;
    private static final ModConfigSpec.IntValue UI_PRIMARY_COLOR;
    private static final ModConfigSpec.IntValue UI_PANEL_COLOR;
    private static final ModConfigSpec.IntValue UI_RAISED_PANEL_COLOR;
    private static final ModConfigSpec.IntValue UI_ACCENT_COLOR;
    private static final ModConfigSpec.IntValue UI_TEXT_COLOR;
    private static final ModConfigSpec.IntValue UI_MUTED_TEXT_COLOR;
    private static final ModConfigSpec.IntValue UI_DANGER_COLOR;
    private static final ModConfigSpec.IntValue UI_SUCCESS_COLOR;
    private static final ModConfigSpec.IntValue UI_WARNING_COLOR;
    private static final ModConfigSpec.IntValue UI_CARD_WIDTH;
    private static final ModConfigSpec.IntValue UI_CARD_HEIGHT;
    private static final ModConfigSpec.DoubleValue UI_ANIMATION_SPEED;
    private static final ModConfigSpec.BooleanValue UI_SHOW_ITEM_MARKET_STATS;
    private static final ModConfigSpec.BooleanValue UI_SHOW_PLAYER_BALANCE;
    private static final ModConfigSpec.BooleanValue UI_SHOW_ECONOMY_GRAPHS;
    private static final ModConfigSpec.ConfigValue<List<? extends String>> CURRENCY_ITEMS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.push("database");
        DATABASE_BACKEND = builder.comment("Only sqlite is implemented in Phase 1.").define("databaseBackend", "sqlite");
        DATABASE_PATH = builder.comment("World-relative path. {world} is replaced with the active world root.")
            .define("databasePath", "{world}/serverconfig/create_coinmarket/coinmarket.db");
        builder.pop();

        builder.push("auction");
        PUBLIC_LISTING_DURATION_HOURS = builder.defineInRange("publicListingDurationHours", 168L, 1L, 24L * 365L);
        ADMIN_LISTINGS_EXPIRE = builder.define("adminListingsExpire", false);
        ADMIN_LISTING_DURATION_HOURS = builder.defineInRange("adminListingDurationHours", 0L, 0L, 24L * 365L);
        PUBLIC_TAX_PERCENT = builder.defineInRange("publicTaxPercent", 5.0D, 0.0D, 100.0D);
        LISTING_FEE = builder.defineInRange("listingFee", 0L, 0L, Long.MAX_VALUE);
        MAX_LISTINGS_PER_PLAYER = builder.defineInRange("maxListingsPerPlayer", 20, 0, 100000);
        ALLOW_BUYING_OWN_LISTINGS = builder.define("allowBuyingOwnListings", false);
        REQUIRE_BUY_CONFIRMATION = builder.define("requireBuyConfirmation", true);
        DEFAULT_OPEN_MODE = builder.define("defaultOpenMode", "all");
        ADMIN_LISTINGS_ARE_INFINITE_BY_DEFAULT = builder.define("adminListingsAreInfiniteByDefault", true);
        DEFAULT_AUCTION_DURATION_HOURS = builder.defineInRange("defaultAuctionDurationHours", 24L, 1L, 24L * 30L);
        MIN_AUCTION_DURATION_HOURS = builder.defineInRange("minAuctionDurationHours", 1L, 1L, 24L * 30L);
        MAX_AUCTION_DURATION_HOURS = builder.defineInRange("maxAuctionDurationHours", 168L, 1L, 24L * 365L);
        DEFAULT_MIN_BID_INCREMENT = builder.defineInRange("defaultMinBidIncrement", 1L, 1L, Long.MAX_VALUE);
        MARKET_HISTORY_DAYS = builder.defineInRange("marketHistoryDays", 30, 1, 3650);
        builder.pop();

        builder.push("economy");
        REQUIRE_NUMISMATICS = builder.comment("Create: CoinMarket declares Create: Numismatics as a required NeoForge dependency for the default economy.")
            .define("requireNumismatics", true);
        ENABLE_NUMISMATICS_BANK_INTEGRATION = builder.comment("Use verified Create: Numismatics bank/card account APIs when available.")
            .define("enableNumismaticsBankIntegration", true);
        PREFERRED_PAYMENT_SOURCE = builder.comment("Valid values: coins_only, bank_only, bank_then_coins, coins_then_bank.")
            .define("preferredPaymentSource", "bank_then_coins");
        FALLBACK_TO_PHYSICAL_COINS = builder.define("fallbackToPhysicalCoins", true);
        SHOW_BANK_BALANCE_IN_HEADER = builder.define("showBankBalanceInHeader", true);
        REQUIRE_BANK_CARD_FOR_BANK_PAYMENTS = builder.define("requireBankCardForBankPayments", true);
        ALLOW_SELLER_PAYOUT_TO_BANK = builder.define("allowSellerPayoutToBank", true);
        BANK_INTEGRATION_FAILURE_MODE = builder.comment("Valid values: fallback, block_purchase.")
            .define("bankIntegrationFailureMode", "fallback");
        AUTO_CREDIT_SELLER_PROCEEDS = builder.comment("Try to credit seller proceeds immediately when safe. Failed or offline payouts remain claimable.")
            .define("autoCreditSellerProceeds", true);
        PAYOUT_MODE = builder.comment("Valid values: bank, physical, hybrid. Physical creates claimable coin payouts.")
            .define("payoutMode", "hybrid");
        ALLOW_OFFLINE_PAYOUT_ACCRUAL = builder.define("allowOfflinePayoutAccrual", true);
        MAX_PENDING_PAYOUT_ENTRIES = builder.defineInRange("maxPendingPayoutEntries", 500, 1, 100000);
        CURRENCY_ITEMS = builder.comment("Format: item_id=value. Values are in the lowest denomination unit.")
            .defineListAllowEmpty("currencyItems", defaultCurrencyItems(), AuctionConfig::isCurrencyEntry);
        builder.pop();

        builder.push("ui");
        ENABLE_MODERN_UI = builder.define("enableModernUi", true);
        ENABLE_ECONOMY_DASHBOARD = builder.define("enableEconomyDashboard", true);
        ENABLE_CHARTS = builder.define("enableCharts", true);
        ECONOMY_SNAPSHOT_INTERVAL_MINUTES = builder.defineInRange("economySnapshotIntervalMinutes", 30, 1, 24 * 60);
        MAX_CHART_DAYS = builder.defineInRange("maxChartDays", 30, 1, 3650);
        MAX_LEADERBOARD_ENTRIES = builder.defineInRange("maxLeaderboardEntries", 10, 1, 100);
        DEFAULT_MARKET_PAGE_SIZE = builder.defineInRange("defaultMarketPageSize", 12, 1, 100);
        ENABLE_LEGACY_CHEST_UI = builder.define("enableLegacyChestUi", false);
        UI_THEME = builder.define("uiTheme", "dark_navy");
        UI_PRIMARY_COLOR = builder.defineInRange("uiPrimaryColor", 0x07111F, 0, 0xFFFFFF);
        UI_PANEL_COLOR = builder.defineInRange("uiPanelColor", 0x0B1B2E, 0, 0xFFFFFF);
        UI_RAISED_PANEL_COLOR = builder.defineInRange("uiRaisedPanelColor", 0x10243A, 0, 0xFFFFFF);
        UI_ACCENT_COLOR = builder.defineInRange("uiAccentColor", 0x1E90FF, 0, 0xFFFFFF);
        UI_TEXT_COLOR = builder.defineInRange("uiTextColor", 0xFFFFFF, 0, 0xFFFFFF);
        UI_MUTED_TEXT_COLOR = builder.defineInRange("uiMutedTextColor", 0xB7C5D8, 0, 0xFFFFFF);
        UI_DANGER_COLOR = builder.defineInRange("uiDangerColor", 0xFF4D4D, 0, 0xFFFFFF);
        UI_SUCCESS_COLOR = builder.defineInRange("uiSuccessColor", 0x4DFF88, 0, 0xFFFFFF);
        UI_WARNING_COLOR = builder.defineInRange("uiWarningColor", 0xFFD166, 0, 0xFFFFFF);
        UI_CARD_WIDTH = builder.defineInRange("uiCardWidth", 160, 120, 320);
        UI_CARD_HEIGHT = builder.defineInRange("uiCardHeight", 72, 56, 160);
        UI_ANIMATION_SPEED = builder.defineInRange("uiAnimationSpeed", 1.0D, 0.0D, 10.0D);
        UI_SHOW_ITEM_MARKET_STATS = builder.define("uiShowItemMarketStats", true);
        UI_SHOW_PLAYER_BALANCE = builder.define("uiShowPlayerBalance", true);
        UI_SHOW_ECONOMY_GRAPHS = builder.define("uiShowEconomyGraphs", true);
        builder.pop();

        SPEC = builder.build();
    }

    private AuctionConfig() {
    }

    public static String databaseBackend() {
        return DATABASE_BACKEND.get();
    }

    public static String databasePath() {
        return DATABASE_PATH.get();
    }

    public static long publicDurationMillis() {
        return PUBLIC_LISTING_DURATION_HOURS.get() * 60L * 60L * 1000L;
    }

    public static boolean adminListingsExpire() {
        return ADMIN_LISTINGS_EXPIRE.get();
    }

    public static long adminDurationMillis() {
        return ADMIN_LISTING_DURATION_HOURS.get() * 60L * 60L * 1000L;
    }

    public static double publicTaxPercent() {
        return PUBLIC_TAX_PERCENT.get();
    }

    public static long listingFee() {
        return LISTING_FEE.get();
    }

    public static int maxListingsPerPlayer() {
        return MAX_LISTINGS_PER_PLAYER.get();
    }

    public static boolean allowBuyingOwnListings() {
        return ALLOW_BUYING_OWN_LISTINGS.get();
    }

    public static boolean requireBuyConfirmation() {
        return REQUIRE_BUY_CONFIRMATION.get();
    }

    public static String defaultOpenMode() {
        return normalizeMode(DEFAULT_OPEN_MODE.get());
    }

    public static boolean enableNumismaticsBankIntegration() {
        return ENABLE_NUMISMATICS_BANK_INTEGRATION.get();
    }

    public static boolean requireNumismatics() {
        return REQUIRE_NUMISMATICS.get();
    }

    public static String preferredPaymentSource() {
        String value = PREFERRED_PAYMENT_SOURCE.get();
        if (value == null) {
            return "bank_then_coins";
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "coins_only", "bank_only", "bank_then_coins", "coins_then_bank" -> normalized;
            default -> "bank_then_coins";
        };
    }

    public static boolean fallbackToPhysicalCoins() {
        return FALLBACK_TO_PHYSICAL_COINS.get();
    }

    public static boolean showBankBalanceInHeader() {
        return SHOW_BANK_BALANCE_IN_HEADER.get();
    }

    public static boolean requireBankCardForBankPayments() {
        return REQUIRE_BANK_CARD_FOR_BANK_PAYMENTS.get();
    }

    public static boolean allowSellerPayoutToBank() {
        return ALLOW_SELLER_PAYOUT_TO_BANK.get();
    }

    public static String bankIntegrationFailureMode() {
        String value = BANK_INTEGRATION_FAILURE_MODE.get();
        if (value == null) {
            return "fallback";
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "fallback", "block_purchase" -> normalized;
            default -> "fallback";
        };
    }

    public static boolean adminListingsAreInfiniteByDefault() {
        return ADMIN_LISTINGS_ARE_INFINITE_BY_DEFAULT.get();
    }

    public static boolean autoCreditSellerProceeds() {
        return AUTO_CREDIT_SELLER_PROCEEDS.get();
    }

    public static String payoutMode() {
        String value = PAYOUT_MODE.get();
        if (value == null) {
            return "hybrid";
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "bank", "physical", "hybrid" -> normalized;
            default -> "hybrid";
        };
    }

    public static boolean allowOfflinePayoutAccrual() {
        return ALLOW_OFFLINE_PAYOUT_ACCRUAL.get();
    }

    public static int maxPendingPayoutEntries() {
        return MAX_PENDING_PAYOUT_ENTRIES.get();
    }

    public static long defaultAuctionDurationHours() {
        return DEFAULT_AUCTION_DURATION_HOURS.get();
    }

    public static long minAuctionDurationHours() {
        return MIN_AUCTION_DURATION_HOURS.get();
    }

    public static long maxAuctionDurationHours() {
        return MAX_AUCTION_DURATION_HOURS.get();
    }

    public static long defaultMinBidIncrement() {
        return DEFAULT_MIN_BID_INCREMENT.get();
    }

    public static int marketHistoryDays() {
        return MARKET_HISTORY_DAYS.get();
    }

    public static boolean enableModernUi() {
        return ENABLE_MODERN_UI.get();
    }

    public static boolean enableEconomyDashboard() {
        return ENABLE_ECONOMY_DASHBOARD.get();
    }

    public static boolean enableCharts() {
        return ENABLE_CHARTS.get();
    }

    public static int economySnapshotIntervalMinutes() {
        return ECONOMY_SNAPSHOT_INTERVAL_MINUTES.get();
    }

    public static int maxChartDays() {
        return MAX_CHART_DAYS.get();
    }

    public static int maxLeaderboardEntries() {
        return MAX_LEADERBOARD_ENTRIES.get();
    }

    public static int defaultMarketPageSize() {
        return DEFAULT_MARKET_PAGE_SIZE.get();
    }

    public static boolean enableLegacyChestUi() {
        return ENABLE_LEGACY_CHEST_UI.get();
    }

    public static String uiTheme() {
        return UI_THEME.get();
    }

    public static int uiPrimaryColor() {
        return UI_PRIMARY_COLOR.get();
    }

    public static int uiPanelColor() {
        return UI_PANEL_COLOR.get();
    }

    public static int uiRaisedPanelColor() {
        return UI_RAISED_PANEL_COLOR.get();
    }

    public static int uiAccentColor() {
        return UI_ACCENT_COLOR.get();
    }

    public static int uiTextColor() {
        return UI_TEXT_COLOR.get();
    }

    public static int uiMutedTextColor() {
        return UI_MUTED_TEXT_COLOR.get();
    }

    public static int uiDangerColor() {
        return UI_DANGER_COLOR.get();
    }

    public static int uiSuccessColor() {
        return UI_SUCCESS_COLOR.get();
    }

    public static int uiWarningColor() {
        return UI_WARNING_COLOR.get();
    }

    public static int uiCardWidth() {
        return UI_CARD_WIDTH.get();
    }

    public static int uiCardHeight() {
        return UI_CARD_HEIGHT.get();
    }

    public static double uiAnimationSpeed() {
        return UI_ANIMATION_SPEED.get();
    }

    public static boolean uiShowItemMarketStats() {
        return UI_SHOW_ITEM_MARKET_STATS.get();
    }

    public static boolean uiShowPlayerBalance() {
        return UI_SHOW_PLAYER_BALANCE.get();
    }

    public static boolean uiShowEconomyGraphs() {
        return UI_SHOW_ECONOMY_GRAPHS.get();
    }

    public static List<CurrencyEntry> currencyItems() {
        List<CurrencyEntry> entries = new ArrayList<>();
        for (String raw : CURRENCY_ITEMS.get()) {
            CurrencyEntry parsed = CurrencyEntry.parse(raw);
            if (parsed != null) {
                entries.add(parsed);
            }
        }
        return entries;
    }

    public static String normalizeMode(String mode) {
        if (mode == null) {
            return "all";
        }
        String lowered = mode.toLowerCase(Locale.ROOT);
        return switch (lowered) {
            case "admin", "public", "all", "browse", "sell", "my", "collection", "dashboard", "pricecheck", "economy", "adminpage" -> lowered;
            default -> "all";
        };
    }

    private static List<String> defaultCurrencyItems() {
        return List.of(
            "numismatics:spur=1",
            "numismatics:bevel=8",
            "numismatics:sprocket=16",
            "numismatics:cog=64",
            "numismatics:crown=512",
            "numismatics:sun=4096"
        );
    }

    private static boolean isCurrencyEntry(Object value) {
        return value instanceof String raw && CurrencyEntry.parse(raw) != null;
    }

    public record CurrencyEntry(String item, long value) {
        public static CurrencyEntry parse(String raw) {
            if (raw == null) {
                return null;
            }
            int separator = raw.lastIndexOf('=');
            if (separator <= 0 || separator >= raw.length() - 1) {
                return null;
            }
            try {
                String item = raw.substring(0, separator).trim();
                long value = Long.parseLong(raw.substring(separator + 1).trim());
                if (item.isBlank() || value <= 0L) {
                    return null;
                }
                return new CurrencyEntry(item, value);
            } catch (NumberFormatException ex) {
                return null;
            }
        }
    }
}
