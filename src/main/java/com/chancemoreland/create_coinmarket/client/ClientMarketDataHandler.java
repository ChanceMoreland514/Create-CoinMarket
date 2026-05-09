package com.chancemoreland.create_coinmarket.client;

import com.chancemoreland.create_coinmarket.network.MarketDataPayload;
import com.chancemoreland.create_coinmarket.network.AuctionNotificationPayload;
import com.chancemoreland.create_coinmarket.network.OpenMarketScreenPayload;
import net.minecraft.client.Minecraft;

public final class ClientMarketDataHandler {
    private ClientMarketDataHandler() {
    }

    public static void open(OpenMarketScreenPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.setScreen(new AuctionBrowserScreen(payload.mode(), payload.page()));
    }

    public static void handle(MarketDataPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof MarketDataReceiver receiver) {
            receiver.create_coinmarket$acceptMarketData(payload.data());
        }
    }

    public static void handleNotification(AuctionNotificationPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof MarketDataReceiver receiver) {
            receiver.create_coinmarket$acceptNotification(payload);
        }
    }
}
