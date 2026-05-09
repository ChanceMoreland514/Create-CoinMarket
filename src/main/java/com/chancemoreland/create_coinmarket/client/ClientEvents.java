package com.chancemoreland.create_coinmarket.client;

import com.chancemoreland.create_coinmarket.menu.AuctionMenus;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

public final class ClientEvents {
    private ClientEvents() {
    }

    public static void registerModBus(IEventBus modEventBus) {
        modEventBus.addListener(ClientEvents::registerScreens);
    }

    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(AuctionMenus.AUCTION_BROWSER.get(), LegacyAuctionBrowserScreen::new);
    }
}
