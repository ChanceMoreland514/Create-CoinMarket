package com.chancemoreland.create_coinmarket.menu;

import com.chancemoreland.create_coinmarket.CreateCoinMarket;
import com.chancemoreland.create_coinmarket.data.AuctionConfig;
import com.chancemoreland.create_coinmarket.network.AuctionNetwork;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public final class AuctionMenus {
    public static final DeferredRegister<MenuType<?>> REGISTER = DeferredRegister.create(Registries.MENU, CreateCoinMarket.MOD_ID);
    public static final Supplier<MenuType<AuctionMenu>> AUCTION_BROWSER = REGISTER.register(
        "auction_browser",
        () -> new MenuType<>(AuctionMenu::new, FeatureFlags.DEFAULT_FLAGS)
    );

    public static final int ROWS = 6;
    public static final int SIZE = ROWS * 9;
    public static final int PAGE_SIZE = 36;

    public static final int SLOT_ALL = 0;
    public static final int SLOT_ADMIN = 1;
    public static final int SLOT_PUBLIC = 2;
    public static final int SLOT_MY = 3;
    public static final int SLOT_COLLECTION = 4;
    public static final int SLOT_SORT = 5;
    public static final int SLOT_REFRESH = 7;
    public static final int SLOT_CLOSE_TOP = 8;
    public static final int SLOT_PREVIOUS = 45;
    public static final int SLOT_ACTION_LEFT = 48;
    public static final int SLOT_CLOSE_BOTTOM = 49;
    public static final int SLOT_ACTION_RIGHT = 50;
    public static final int SLOT_NEXT = 53;

    private AuctionMenus() {
    }

    public static void register(IEventBus modEventBus) {
        REGISTER.register(modEventBus);
    }

    public static void open(ServerPlayer player, String mode, int page) {
        String normalizedMode = AuctionConfig.normalizeMode(mode);
        int normalizedPage = Math.max(0, page);
        if (!AuctionConfig.enableLegacyChestUi()) {
            AuctionNetwork.openMarket(player, normalizedMode, normalizedPage);
            return;
        }
        player.openMenu(new SimpleMenuProvider(
            (containerId, inventory, openedBy) -> new AuctionMenu(containerId, inventory, normalizedMode, normalizedPage, openedBy.getUUID()),
            Component.literal("Create: CoinMarket")
        ));
    }

    public static void openBuyConfirm(ServerPlayer player, String listingId, String returnMode, int returnPage) {
        if (!AuctionConfig.enableLegacyChestUi()) {
            AuctionNetwork.openMarket(player, AuctionConfig.normalizeMode(returnMode), Math.max(0, returnPage));
            return;
        }
        player.openMenu(new SimpleMenuProvider(
            (containerId, inventory, openedBy) -> new AuctionMenu(containerId, inventory, "buy:" + listingId + ":" + returnMode + ":" + returnPage, 0, openedBy.getUUID()),
            Component.literal("Confirm Purchase")
        ));
    }
}
