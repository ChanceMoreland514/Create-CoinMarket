package com.chancemoreland.create_coinmarket.data;

public record MarketStats(
    long lastSoldPrice,
    double average24h,
    double average7d,
    double average30d,
    long lowestActiveListing,
    int activeListingCount,
    int soldCount24h,
    int soldCount7d,
    String trend
) {
    public static MarketStats empty() {
        return new MarketStats(0L, 0D, 0D, 0D, 0L, 0, 0, 0, "unknown");
    }
}
