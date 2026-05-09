package com.chancemoreland.create_coinmarket.economy;

import com.chancemoreland.create_coinmarket.data.MarketScreenData;

import java.util.List;

public final class MarketChartService {
    private MarketChartService() {
    }

    public static long maxValue(List<? extends MarketScreenData.NamedValue> values) {
        return values.stream().mapToLong(MarketScreenData.NamedValue::value).max().orElse(0L);
    }

    public static long maxPointValue(List<MarketScreenData.ChartPoint> values) {
        return values.stream().mapToLong(MarketScreenData.ChartPoint::value).max().orElse(0L);
    }
}
