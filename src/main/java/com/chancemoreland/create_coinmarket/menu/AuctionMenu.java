package com.chancemoreland.create_coinmarket.menu;

import com.chancemoreland.create_coinmarket.data.AuctionConfig;
import com.chancemoreland.create_coinmarket.data.AuctionDatabase;
import com.chancemoreland.create_coinmarket.data.AuctionDatabase.OperationResult;
import com.chancemoreland.create_coinmarket.data.AuctionListing;
import com.chancemoreland.create_coinmarket.data.CollectionEntry;
import com.chancemoreland.create_coinmarket.data.MarketStats;
import com.chancemoreland.create_coinmarket.economy.EconomyManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class AuctionMenu extends AbstractContainerMenu {
    private final SimpleContainer display = new SimpleContainer(AuctionMenus.SIZE);
    private final Map<Integer, String> listingSlots = new HashMap<>();
    private final Inventory playerInventory;
    private final UUID viewerId;
    private String mode;
    private int page;

    public AuctionMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, AuctionConfig.defaultOpenMode(), 0, playerInventory.player.getUUID());
    }

    public AuctionMenu(int containerId, Inventory playerInventory, String mode, int page, UUID viewerId) {
        super(AuctionMenus.AUCTION_BROWSER.get(), containerId);
        this.playerInventory = playerInventory;
        this.mode = mode;
        this.page = page;
        this.viewerId = viewerId;

        for (int row = 0; row < AuctionMenus.ROWS; row++) {
            for (int column = 0; column < 9; column++) {
                int slot = column + row * 9;
                addSlot(new DisplaySlot(display, slot, 8 + column * 18, 18 + row * 18));
            }
        }
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(playerInventory, column + row * 9 + 9, 8 + column * 18, 140 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(playerInventory, column, 8 + column * 18, 198));
        }
        refresh();
    }

    public String titleSuffix() {
        if (mode.startsWith("buy:")) {
            return "Confirm";
        }
        return switch (mode) {
            case "admin" -> "Admin";
            case "public" -> "Public";
            case "my" -> "My Listings";
            case "collection" -> "Collection";
            case "dashboard" -> "Dashboard";
            case "pricecheck" -> "Price Check";
            case "economy" -> "Economy";
            case "adminpage" -> "Admin";
            default -> "All";
        };
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canDragTo(Slot slot) {
        return !(slot instanceof DisplaySlot);
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        if (slotId >= 0 && slotId < AuctionMenus.SIZE) {
            handleAuctionClick(serverPlayer, slotId, button, clickType);
            return;
        }
        super.clicked(slotId, button, clickType, player);
    }

    private void handleAuctionClick(ServerPlayer player, int slotId, int button, ClickType clickType) {
        boolean primary = clickType == ClickType.PICKUP && button == 0;
        boolean shift = clickType == ClickType.QUICK_MOVE;
        if (!primary && !shift) {
            return;
        }

        if (mode.startsWith("buy:")) {
            handleConfirmClick(player, slotId);
            return;
        }

        if (slotId == AuctionMenus.SLOT_ALL) {
            AuctionMenus.open(player, "all", 0);
        } else if (slotId == AuctionMenus.SLOT_ADMIN) {
            AuctionMenus.open(player, "admin", 0);
        } else if (slotId == AuctionMenus.SLOT_PUBLIC) {
            AuctionMenus.open(player, "public", 0);
        } else if (slotId == AuctionMenus.SLOT_MY) {
            AuctionMenus.open(player, "my", 0);
        } else if (slotId == AuctionMenus.SLOT_COLLECTION) {
            AuctionMenus.open(player, "collection", 0);
        } else if (slotId == AuctionMenus.SLOT_REFRESH || slotId == AuctionMenus.SLOT_SORT) {
            AuctionMenus.open(player, mode, page);
        } else if (slotId == AuctionMenus.SLOT_CLOSE_TOP || slotId == AuctionMenus.SLOT_CLOSE_BOTTOM) {
            player.closeContainer();
        } else if (slotId == AuctionMenus.SLOT_PREVIOUS) {
            AuctionMenus.open(player, mode, Math.max(0, page - 1));
        } else if (slotId == AuctionMenus.SLOT_NEXT) {
            AuctionMenus.open(player, mode, page + 1);
        } else if ("collection".equals(mode) && slotId == AuctionMenus.SLOT_ACTION_RIGHT) {
            OperationResult result = AuctionDatabase.collect(player);
            player.sendSystemMessage(AuctionDatabase.chat(result));
            AuctionMenus.open(player, "collection", 0);
        } else if (listingSlots.containsKey(slotId)) {
            String listingId = listingSlots.get(slotId);
            if (AuctionConfig.requireBuyConfirmation()) {
                AuctionMenus.openBuyConfirm(player, listingId, mode, page);
            } else {
                OperationResult result = AuctionDatabase.buyListing(player, listingId);
                player.sendSystemMessage(AuctionDatabase.chat(result));
                AuctionMenus.open(player, mode, page);
            }
        }
    }

    private void handleConfirmClick(ServerPlayer player, int slotId) {
        ConfirmState confirm = ConfirmState.parse(mode);
        if (confirm == null) {
            AuctionMenus.open(player, AuctionConfig.defaultOpenMode(), 0);
            return;
        }
        if (slotId == AuctionMenus.SLOT_ACTION_LEFT || slotId == AuctionMenus.SLOT_CLOSE_BOTTOM || slotId == AuctionMenus.SLOT_CLOSE_TOP) {
            AuctionMenus.open(player, confirm.returnMode(), confirm.returnPage());
        } else if (slotId == AuctionMenus.SLOT_ACTION_RIGHT) {
            OperationResult result = AuctionDatabase.buyListing(player, confirm.listingId());
            player.sendSystemMessage(AuctionDatabase.chat(result));
            AuctionMenus.open(player, confirm.returnMode(), confirm.returnPage());
        }
    }

    private void refresh() {
        display.clearContent();
        listingSlots.clear();
        if (mode.startsWith("buy:")) {
            setConfirmItems();
        } else if ("collection".equals(mode)) {
            setCollectionItems();
        } else {
            setBrowserItems();
        }
        display.setChanged();
        broadcastChanges();
    }

    private void setBrowserItems() {
        List<AuctionListing> active = AuctionDatabase.activeListings(mode, viewerId);
        int maxPage = Math.max(0, (active.size() - 1) / AuctionMenus.PAGE_SIZE);
        page = Math.min(page, maxPage);
        setControlItems(active.size(), maxPage);
        int start = page * AuctionMenus.PAGE_SIZE;
        int end = Math.min(active.size(), start + AuctionMenus.PAGE_SIZE);
        int slot = 9;
        for (int index = start; index < end && slot <= 44; index++) {
            AuctionListing listing = active.get(index);
            display.setItem(slot, listingDisplayItem(listing));
            listingSlots.put(slot, listing.id);
            slot++;
        }
    }

    private void setCollectionItems() {
        List<CollectionEntry> entries = AuctionDatabase.collections(viewerId);
        setControlItems(entries.size(), 0);
        long coins = 0L;
        int itemCount = 0;
        for (CollectionEntry entry : entries) {
            if ("coins".equals(entry.type)) {
                coins += entry.amount;
            } else {
                itemCount++;
            }
        }
        display.setItem(22, button(Items.HOPPER, "Unclaimed Collection", List.of(
            line(itemCount + " item stack(s)", ChatFormatting.GRAY),
            line(EconomyManager.service().format(coins), ChatFormatting.GOLD)
        )));
        display.setItem(AuctionMenus.SLOT_ACTION_RIGHT, button(Items.EMERALD_BLOCK, "Claim All", List.of(
            line("Collect all payouts and items", ChatFormatting.GREEN)
        )));
    }

    private void setConfirmItems() {
        ConfirmState confirm = ConfirmState.parse(mode);
        if (confirm == null) {
            return;
        }
        AuctionListing listing = AuctionDatabase.listing(confirm.listingId());
        setControlItems(1, 0);
        if (listing == null) {
            display.setItem(22, button(Items.BARRIER, "Listing Unavailable", List.of()));
        } else {
            display.setItem(22, listingDisplayItem(listing));
        }
        display.setItem(AuctionMenus.SLOT_ACTION_LEFT, button(Items.RED_STAINED_GLASS_PANE, "Cancel", List.of(
            line("Return to market", ChatFormatting.RED)
        )));
        display.setItem(AuctionMenus.SLOT_ACTION_RIGHT, button(Items.LIME_STAINED_GLASS_PANE, "Confirm Buy", List.of(
            line("Server will re-check price, stock, and inventory", ChatFormatting.GREEN)
        )));
    }

    private void setControlItems(int total, int maxPage) {
        display.setItem(AuctionMenus.SLOT_ALL, button(Items.COMPASS, "All", selectedLore("all", total)));
        display.setItem(AuctionMenus.SLOT_ADMIN, button(Items.GOLD_BLOCK, "Admin", selectedLore("admin", total)));
        display.setItem(AuctionMenus.SLOT_PUBLIC, button(Items.CHEST, "Public", selectedLore("public", total)));
        display.setItem(AuctionMenus.SLOT_MY, button(Items.PLAYER_HEAD, "My Listings", selectedLore("my", total)));
        display.setItem(AuctionMenus.SLOT_COLLECTION, button(Items.HOPPER, "Collection", selectedLore("collection", total)));
        display.setItem(AuctionMenus.SLOT_SORT, button(Items.NAME_TAG, "Sort: Newest", List.of(line("Advanced sorting is a Phase 2 TODO", ChatFormatting.GRAY))));
        display.setItem(AuctionMenus.SLOT_REFRESH, button(Items.CLOCK, "Refresh", List.of(line("Reload current view", ChatFormatting.GRAY))));
        display.setItem(AuctionMenus.SLOT_CLOSE_TOP, button(Items.BARRIER, "Close", List.of()));
        display.setItem(AuctionMenus.SLOT_PREVIOUS, button(Items.ARROW, "Previous", List.of(line("Page " + (page + 1) + " of " + (maxPage + 1), ChatFormatting.GRAY))));
        display.setItem(AuctionMenus.SLOT_CLOSE_BOTTOM, button(Items.BARRIER, "Close", List.of()));
        display.setItem(AuctionMenus.SLOT_NEXT, button(Items.ARROW, "Next", List.of(line("Page " + (page + 1) + " of " + (maxPage + 1), ChatFormatting.GRAY))));
    }

    private List<Component> selectedLore(String target, int total) {
        List<Component> lore = new ArrayList<>();
        lore.add(line(target.equals(mode) ? "Selected" : "Open view", ChatFormatting.GRAY));
        lore.add(line(total + " visible", ChatFormatting.DARK_GRAY));
        return lore;
    }

    private ItemStack listingDisplayItem(AuctionListing listing) {
        ItemStack stack = AuctionDatabase.decodeItem(listing);
        if (stack.isEmpty()) {
            stack = new ItemStack(Items.BARRIER);
            stack.set(DataComponents.CUSTOM_NAME, Component.literal("Invalid Item").withStyle(ChatFormatting.RED));
        }
        MarketStats stats = AuctionDatabase.marketStats(listing.itemId);
        List<Component> lore = new ArrayList<>();
        lore.add(line("Seller: " + listing.sellerName, ChatFormatting.GRAY));
        lore.add(line("Price: " + EconomyManager.service().format(listing.price), ChatFormatting.GOLD));
        lore.add(line("Category: " + listing.category, listing.isAdmin() ? ChatFormatting.YELLOW : ChatFormatting.GREEN));
        lore.add(line("Time remaining: " + AuctionDatabase.timeRemaining(listing), ChatFormatting.GRAY));
        lore.add(line("Listing ID: " + listing.id, ChatFormatting.DARK_GRAY));
        lore.add(line("Lowest active: " + formatStat(stats.lowestActiveListing()), ChatFormatting.AQUA));
        lore.add(line("7d average: " + formatStat(Math.round(stats.average7d())), ChatFormatting.AQUA));
        lore.add(line("Sold 24h/7d: " + stats.soldCount24h() + "/" + stats.soldCount7d(), ChatFormatting.GRAY));
        lore.add(line("Left-click: buy", ChatFormatting.WHITE));
        if (listing.isAdmin()) {
            lore.add(line("Admin auction", ChatFormatting.LIGHT_PURPLE));
        }
        stack.set(DataComponents.LORE, new ItemLore(lore));
        return stack;
    }

    private static String formatStat(long value) {
        return value <= 0L ? "unknown" : EconomyManager.service().format(value);
    }

    private static ItemStack button(Item item, String name, List<Component> lore) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name).withStyle(ChatFormatting.WHITE));
        if (!lore.isEmpty()) {
            stack.set(DataComponents.LORE, new ItemLore(lore));
        }
        return stack;
    }

    private static Component line(String text, ChatFormatting color) {
        return Component.literal(text).withStyle(color);
    }

    private record ConfirmState(String listingId, String returnMode, int returnPage) {
        static ConfirmState parse(String raw) {
            String[] parts = raw.split(":", 4);
            if (parts.length != 4 || !"buy".equals(parts[0])) {
                return null;
            }
            int page;
            try {
                page = Integer.parseInt(parts[3]);
            } catch (NumberFormatException ex) {
                page = 0;
            }
            return new ConfirmState(parts[1], AuctionConfig.normalizeMode(parts[2]), Math.max(0, page));
        }
    }

    private static class DisplaySlot extends Slot {
        DisplaySlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }
    }
}
