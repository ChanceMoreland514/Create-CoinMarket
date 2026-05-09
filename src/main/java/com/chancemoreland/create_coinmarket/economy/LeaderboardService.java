package com.chancemoreland.create_coinmarket.economy;

import com.chancemoreland.create_coinmarket.data.MarketScreenData;

import java.util.List;

public final class LeaderboardService {
    private LeaderboardService() {
    }

    public static List<MarketScreenData.NamedValue> limit(List<MarketScreenData.NamedValue> entries, int max) {
        return entries.stream().limit(Math.max(0, max)).toList();
    }
}
