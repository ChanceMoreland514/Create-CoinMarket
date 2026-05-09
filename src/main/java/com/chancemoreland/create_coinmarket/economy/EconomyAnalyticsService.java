package com.chancemoreland.create_coinmarket.economy;

import com.chancemoreland.create_coinmarket.data.AuctionConfig;
import com.chancemoreland.create_coinmarket.data.AuctionDatabase;

public final class EconomyAnalyticsService {
    private static long lastSnapshotTick = Long.MIN_VALUE;

    private EconomyAnalyticsService() {
    }

    public static void onServerTick(long serverTicks) {
        long intervalTicks = Math.max(1L, AuctionConfig.economySnapshotIntervalMinutes()) * 60L * 20L;
        if (lastSnapshotTick == Long.MIN_VALUE || serverTicks - lastSnapshotTick >= intervalTicks) {
            lastSnapshotTick = serverTicks;
            AuctionDatabase.refreshEconomyAnalytics();
        }
    }
}
