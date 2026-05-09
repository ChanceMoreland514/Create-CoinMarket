package com.chancemoreland.create_coinmarket.client;

import com.chancemoreland.create_coinmarket.data.MarketScreenData;
import com.chancemoreland.create_coinmarket.network.AuctionNotificationPayload;

public interface MarketDataReceiver {
    void create_coinmarket$acceptMarketData(MarketScreenData data);

    default void create_coinmarket$acceptNotification(AuctionNotificationPayload payload) {
    }
}
