package com.chancemoreland.create_coinmarket.command;

import com.chancemoreland.create_coinmarket.data.AuctionConfig;
import com.chancemoreland.create_coinmarket.data.AuctionDatabase;
import com.chancemoreland.create_coinmarket.data.AuctionDatabase.OperationResult;
import com.chancemoreland.create_coinmarket.data.AuctionListing;
import com.chancemoreland.create_coinmarket.data.MarketStats;
import com.chancemoreland.create_coinmarket.economy.EconomyBalance;
import com.chancemoreland.create_coinmarket.economy.EconomyManager;
import com.chancemoreland.create_coinmarket.economy.NumismaticsDiagnostics;
import com.chancemoreland.create_coinmarket.economy.PhysicalCoinEconomyService;
import com.chancemoreland.create_coinmarket.menu.AuctionMenus;
import com.chancemoreland.create_coinmarket.network.AuctionNetwork;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.commands.arguments.item.ItemInput;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.server.permission.PermissionAPI;
import net.neoforged.neoforge.server.permission.events.PermissionGatherEvent;
import net.neoforged.neoforge.server.permission.nodes.PermissionNode;
import net.neoforged.neoforge.server.permission.nodes.PermissionTypes;

import java.util.List;

@SuppressWarnings({"unchecked", "rawtypes"})
public final class AuctionCommand {
    private static final PermissionNode<Boolean> OPEN = node("open", true);
    private static final PermissionNode<Boolean> SELL = node("sell", true);
    private static final PermissionNode<Boolean> CANCEL_OWN = node("cancel.own", true);
    private static final PermissionNode<Boolean> COLLECT = node("collect", true);
    private static final PermissionNode<Boolean> HISTORY = node("history", true);
    private static final PermissionNode<Boolean> PRICECHECK = node("pricecheck", true);
    private static final PermissionNode<Boolean> ADMIN_ADD = node("admin.add", false);
    private static final PermissionNode<Boolean> ADMIN_REMOVE = node("admin.remove", false);
    private static final PermissionNode<Boolean> ADMIN_CLEAR = node("admin.clear", false);
    private static final PermissionNode<Boolean> ADMIN_RELOAD = node("admin.reload", false);
    private static final PermissionNode<Boolean> ADMIN_REPAIR = node("admin.repair", false);

    private AuctionCommand() {
    }

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        CommandBuildContext buildContext = event.getBuildContext();

        dispatcher.register(Commands.literal("auction")
            .then(Commands.literal("open")
                .requires(source -> has(source, OPEN, 0))
                .executes(context -> open(context, AuctionConfig.defaultOpenMode()))
                .then(Commands.literal("all").executes(context -> open(context, "all")))
                .then(Commands.literal("admin").executes(context -> open(context, "admin")))
                .then(Commands.literal("public").executes(context -> open(context, "public"))))
            .then(Commands.literal("sell")
                .requires(source -> has(source, SELL, 0))
                .then(Commands.literal("hand")
                    .then(Commands.argument("price", LongArgumentType.longArg(1L))
                        .executes(context -> sell(context, -1))))
                .then(Commands.argument("price", LongArgumentType.longArg(1L))
                    .executes(context -> sell(context, -1))
                    .then(Commands.argument("quantity", IntegerArgumentType.integer(1))
                        .executes(context -> sell(context, IntegerArgumentType.getInteger(context, "quantity"))))))
            .then(Commands.literal("auction")
                .requires(source -> has(source, SELL, 0))
                .then(Commands.literal("hand")
                    .then(Commands.argument("startBid", LongArgumentType.longArg(1L))
                        .then(Commands.argument("durationHours", LongArgumentType.longArg(1L))
                            .executes(context -> auctionHand(context, 0L))
                            .then(Commands.argument("buyout", LongArgumentType.longArg(0L))
                                .executes(context -> auctionHand(context, LongArgumentType.getLong(context, "buyout"))))))))
            .then(Commands.literal("bid")
                .requires(source -> has(source, OPEN, 0))
                .then(Commands.argument("listingId", StringArgumentType.word())
                    .suggests((context, builder) -> SharedSuggestionProvider.suggest(activeListingIds(), builder))
                    .then(Commands.argument("amount", LongArgumentType.longArg(1L))
                        .executes(AuctionCommand::bid))))
            .then(Commands.literal("buyout")
                .requires(source -> has(source, OPEN, 0))
                .then(Commands.argument("listingId", StringArgumentType.word())
                    .suggests((context, builder) -> SharedSuggestionProvider.suggest(activeListingIds(), builder))
                    .executes(AuctionCommand::buyout)))
            .then(Commands.literal("cancel")
                .requires(source -> has(source, CANCEL_OWN, 0))
                .then(Commands.argument("listingId", StringArgumentType.word())
                    .suggests((context, builder) -> SharedSuggestionProvider.suggest(activeListingIds(), builder))
                    .executes(AuctionCommand::cancel)))
            .then(Commands.literal("collect")
                .requires(source -> has(source, COLLECT, 0))
                .executes(AuctionCommand::collect))
            .then(Commands.literal("collectmoney")
                .requires(source -> has(source, COLLECT, 0))
                .executes(AuctionCommand::collectMoney))
            .then(Commands.literal("resolveexpired")
                .requires(source -> has(source, ADMIN_REPAIR, 2))
                .executes(AuctionCommand::adminRepair))
            .then(Commands.literal("balance")
                .requires(source -> has(source, OPEN, 0))
                .executes(AuctionCommand::balance))
            .then(Commands.literal("history")
                .requires(source -> has(source, HISTORY, 0))
                .executes(AuctionCommand::history))
            .then(Commands.literal("pricecheck")
                .requires(source -> has(source, PRICECHECK, 0))
                .executes(AuctionCommand::pricecheck))
            .then(Commands.literal("market")
                .requires(source -> has(source, PRICECHECK, 0))
                .then(Commands.argument("item", ItemArgument.item(buildContext))
                    .executes(AuctionCommand::market)))
            .then(Commands.literal("admin")
                .requires(AuctionCommand::hasAnyAdmin)
                .then(Commands.literal("addhand")
                    .requires(source -> has(source, ADMIN_ADD, 2))
                    .then(Commands.argument("price", LongArgumentType.longArg(1L))
                        .executes(AuctionCommand::adminAddHand)))
                .then(Commands.literal("add")
                    .requires(source -> has(source, ADMIN_ADD, 2))
                    .then(Commands.argument("item", ItemArgument.item(buildContext))
                        .then(Commands.argument("price", LongArgumentType.longArg(1L))
                            .executes(AuctionCommand::adminAdd))))
                .then(Commands.literal("remove")
                    .requires(source -> has(source, ADMIN_REMOVE, 2))
                    .then(Commands.argument("listingId", StringArgumentType.word())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(activeListingIds(), builder))
                        .executes(AuctionCommand::adminRemove)))
                .then(Commands.literal("list")
                    .requires(source -> has(source, ADMIN_REMOVE, 2))
                    .executes(AuctionCommand::adminList))
                .then(Commands.literal("clear")
                    .requires(source -> has(source, ADMIN_CLEAR, 2))
                    .then(Commands.literal("admin").executes(context -> adminClear(context, "admin")))
                    .then(Commands.literal("public").executes(context -> adminClear(context, "public")))
                    .then(Commands.literal("all").executes(context -> adminClear(context, "all"))))
                .then(Commands.literal("reload")
                    .requires(source -> has(source, ADMIN_RELOAD, 2))
                    .executes(AuctionCommand::adminReload))
                .then(Commands.literal("save")
                    .requires(source -> has(source, ADMIN_RELOAD, 2))
                    .executes(AuctionCommand::adminSave))
                .then(Commands.literal("inspect")
                    .requires(source -> has(source, ADMIN_REMOVE, 2))
                    .then(Commands.argument("listingId", StringArgumentType.word())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(activeListingIds(), builder))
                        .executes(AuctionCommand::adminInspect)))
                .then(Commands.literal("repair")
                    .requires(source -> has(source, ADMIN_REPAIR, 2))
                    .executes(AuctionCommand::adminRepair))
                .then(Commands.literal("endauction")
                    .requires(source -> has(source, ADMIN_REMOVE, 2))
                    .then(Commands.argument("listingId", StringArgumentType.word())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(activeListingIds(), builder))
                        .executes(AuctionCommand::adminEndAuction)))
                .then(Commands.literal("refundlisting")
                    .requires(source -> has(source, ADMIN_REMOVE, 2))
                    .then(Commands.argument("listingId", StringArgumentType.word())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(activeListingIds(), builder))
                        .executes(AuctionCommand::adminRefundListing)))
                .then(Commands.literal("returnlisting")
                    .requires(source -> has(source, ADMIN_REMOVE, 2))
                    .then(Commands.argument("listingId", StringArgumentType.word())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(activeListingIds(), builder))
                        .executes(AuctionCommand::adminReturnListing)))
                .then(Commands.literal("viewclaims")
                    .requires(source -> has(source, ADMIN_REMOVE, 2))
                    .then(Commands.argument("player", StringArgumentType.word())
                        .executes(AuctionCommand::adminViewClaims)))
                .then(Commands.literal("forceclaim")
                    .requires(source -> has(source, ADMIN_REMOVE, 2))
                    .then(Commands.argument("player", StringArgumentType.word())
                        .then(Commands.argument("claimId", StringArgumentType.word())
                            .executes(AuctionCommand::adminForceClaim))))
                .then(Commands.literal("numismaticscheck")
                    .requires(source -> has(source, ADMIN_RELOAD, 2))
                    .executes(AuctionCommand::adminNumismaticsCheck))));
    }

    public static void onRegisterPermissions(PermissionGatherEvent.Nodes event) {
        event.addNodes(OPEN, SELL, CANCEL_OWN, COLLECT, HISTORY, PRICECHECK, ADMIN_ADD, ADMIN_REMOVE, ADMIN_CLEAR, ADMIN_RELOAD, ADMIN_REPAIR);
    }

    private static int open(CommandContext<CommandSourceStack> context, String mode) throws CommandSyntaxException {
        AuctionMenus.open(context.getSource().getPlayerOrException(), mode, 0);
        return 1;
    }

    private static int sell(CommandContext<CommandSourceStack> context, int quantity) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        if (quantity < 0) {
            quantity = player.getMainHandItem().getCount();
        }
        OperationResult result = AuctionDatabase.addPublicListing(player, LongArgumentType.getLong(context, "price"), quantity);
        sendPlayer(player, result);
        return result.success() ? 1 : 0;
    }

    private static int auctionHand(CommandContext<CommandSourceStack> context, long buyout) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        long startBid = LongArgumentType.getLong(context, "startBid");
        long duration = LongArgumentType.getLong(context, "durationHours");
        int quantity = player.getMainHandItem().getCount();
        OperationResult result = AuctionDatabase.addAuctionListing(player, startBid, quantity,
            duration + "|" + buyout + "|" + AuctionConfig.defaultMinBidIncrement());
        sendPlayer(player, result);
        return result.success() ? 1 : 0;
    }

    private static int bid(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        OperationResult result = AuctionDatabase.placeBid(player, StringArgumentType.getString(context, "listingId"), LongArgumentType.getLong(context, "amount"));
        sendPlayer(player, result);
        return result.success() ? 1 : 0;
    }

    private static int buyout(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        OperationResult result = AuctionDatabase.buyoutAuction(player, StringArgumentType.getString(context, "listingId"));
        sendPlayer(player, result);
        return result.success() ? 1 : 0;
    }

    private static int cancel(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        OperationResult result = AuctionDatabase.cancelOwnListing(player, StringArgumentType.getString(context, "listingId"));
        sendPlayer(player, result);
        return result.success() ? 1 : 0;
    }

    private static int collect(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        OperationResult result = AuctionDatabase.collect(player);
        sendPlayer(player, result);
        return result.success() ? 1 : 0;
    }

    private static int collectMoney(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        OperationResult result = AuctionDatabase.collectMoney(player);
        sendPlayer(player, result);
        return result.success() ? 1 : 0;
    }

    private static int balance(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        EconomyBalance balance = EconomyManager.service().balanceDetails(player);
        player.sendSystemMessage(Component.literal("Physical coins: " + EconomyManager.service().format(balance.coinBalance())).withStyle(ChatFormatting.GOLD));
        if (balance.hasBankCard()) {
            player.sendSystemMessage(Component.literal("Bank/card balance: " + EconomyManager.service().format(balance.bankBalance())).withStyle(balance.bankAvailable() ? ChatFormatting.AQUA : ChatFormatting.YELLOW));
        } else {
            player.sendSystemMessage(Component.literal("Bank/card balance: no bound card detected.").withStyle(ChatFormatting.GRAY));
        }
        player.sendSystemMessage(Component.literal("Total spendable: " + EconomyManager.service().format(balance.totalSpendableBalance())
            + " | Source: " + balance.activePaymentSource()).withStyle(ChatFormatting.GREEN));
        if (!balance.warning().isBlank()) {
            player.sendSystemMessage(Component.literal(balance.warning()).withStyle(ChatFormatting.YELLOW));
        }
        AuctionNetwork.sendNotification(player, "info", "Spendable balance: " + EconomyManager.service().format(balance.totalSpendableBalance()), 140);
        return 1;
    }

    private static int history(CommandContext<CommandSourceStack> context) {
        context.getSource().sendSuccess(() -> Component.literal("History UI is a Phase 2 TODO; use /auction pricecheck and /auction market for live market stats.").withStyle(ChatFormatting.YELLOW), false);
        return 1;
    }

    private static int pricecheck(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            OperationResult result = OperationResult.fail("Hold an item to pricecheck.");
            sendPlayer(player, result);
            return 0;
        }
        String itemId = BuiltInRegistries.ITEM.getKey(held.getItem()).toString();
        sendMarketStats(context.getSource(), itemId, held.getHoverName().getString());
        return 1;
    }

    private static int market(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ItemInput input = ItemArgument.getItem(context, "item");
        ItemStack stack = input.createItemStack(1, false);
        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        sendMarketStats(context.getSource(), itemId, stack.getHoverName().getString());
        return 1;
    }

    private static int adminAddHand(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        long price = LongArgumentType.getLong(context, "price");
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            sendPlayer(player, OperationResult.fail("Hold an item in your main hand."));
            return 0;
        }
        ItemStack listed = held.copy();
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        OperationResult result = AuctionDatabase.addAdminListing(player, player.getGameProfile().getName(), listed, price, "admin_hand");
        if (!result.success()) {
            player.setItemInHand(InteractionHand.MAIN_HAND, listed);
        }
        sendPlayer(player, result);
        return result.success() ? 1 : 0;
    }

    private static int adminAdd(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ItemStack stack = ItemArgument.getItem(context, "item").createItemStack(1, false);
        OperationResult result = AuctionDatabase.addAdminListing(playerOrNull(context.getSource()), sourceName(context.getSource()), stack, LongArgumentType.getLong(context, "price"), "admin_command");
        send(context.getSource(), result);
        if (result.success()) {
            context.getSource().sendSuccess(() -> Component.literal("Tip: use /auction admin addhand <price> for exact NBT/components.").withStyle(ChatFormatting.GRAY), false);
        }
        return result.success() ? 1 : 0;
    }

    private static int adminRemove(CommandContext<CommandSourceStack> context) {
        OperationResult result = AuctionDatabase.adminRemove(playerOrNull(context.getSource()), sourceName(context.getSource()), StringArgumentType.getString(context, "listingId"));
        send(context.getSource(), result);
        return result.success() ? 1 : 0;
    }

    private static int adminList(CommandContext<CommandSourceStack> context) {
        List<AuctionListing> listings = AuctionDatabase.allActiveListings();
        context.getSource().sendSuccess(() -> Component.literal("Active listings: " + listings.size()).withStyle(ChatFormatting.GOLD), false);
        for (AuctionListing listing : listings.stream().limit(25).toList()) {
            ItemStack item = AuctionDatabase.decodeItem(listing);
            context.getSource().sendSuccess(() -> Component.literal(listing.id + " | " + listing.category + " | " + item.getHoverName().getString()
                + " x" + item.getCount() + " | " + EconomyManager.service().format(listing.price)
                + " | seller " + listing.sellerName + " | " + AuctionDatabase.timeRemaining(listing)), false);
        }
        return listings.size();
    }

    private static int adminClear(CommandContext<CommandSourceStack> context, String target) {
        OperationResult result = AuctionDatabase.adminClear(playerOrNull(context.getSource()), sourceName(context.getSource()), target);
        send(context.getSource(), result);
        return result.success() ? 1 : 0;
    }

    private static int adminReload(CommandContext<CommandSourceStack> context) {
        OperationResult result = AuctionDatabase.repair(playerOrNull(context.getSource()), sourceName(context.getSource()));
        send(context.getSource(), result);
        List<String> missing = PhysicalCoinEconomyService.configuredCurrencyWarnings();
        if (!missing.isEmpty()) {
            context.getSource().sendFailure(Component.literal("Unknown configured currency item(s): " + String.join(", ", missing)));
            ServerPlayer player = playerOrNull(context.getSource());
            if (player != null) {
                AuctionNetwork.sendNotification(player, "warning", "Unknown configured currency item(s): " + String.join(", ", missing), 180);
            }
        }
        return result.success() ? 1 : 0;
    }

    private static int adminSave(CommandContext<CommandSourceStack> context) {
        context.getSource().sendSuccess(() -> Component.literal("SQLite commits every operation immediately; no manual save is needed.").withStyle(ChatFormatting.GREEN), false);
        ServerPlayer player = playerOrNull(context.getSource());
        if (player != null) {
            AuctionNetwork.sendNotification(player, "info", "SQLite commits every operation immediately.", 120);
        }
        return 1;
    }

    private static int adminInspect(CommandContext<CommandSourceStack> context) {
        String id = StringArgumentType.getString(context, "listingId");
        AuctionListing listing = AuctionDatabase.listing(id);
        if (listing == null) {
            context.getSource().sendFailure(Component.literal("Listing not found."));
            return 0;
        }
        ItemStack item = AuctionDatabase.decodeItem(listing);
        context.getSource().sendSuccess(() -> Component.literal("Listing " + listing.id + ": " + listing.status + " " + listing.category), false);
        context.getSource().sendSuccess(() -> Component.literal("Item: " + listing.itemId + " x" + item.getCount() + " / " + item.getHoverName().getString()), false);
        context.getSource().sendSuccess(() -> Component.literal("Seller: " + listing.sellerName + " " + listing.sellerUuid), false);
        context.getSource().sendSuccess(() -> Component.literal("Price: " + EconomyManager.service().format(listing.price) + ", source=" + listing.source + ", version=" + listing.version), false);
        return 1;
    }

    private static int adminRepair(CommandContext<CommandSourceStack> context) {
        OperationResult result = AuctionDatabase.repair(playerOrNull(context.getSource()), sourceName(context.getSource()));
        send(context.getSource(), result);
        return result.success() ? 1 : 0;
    }

    private static int adminEndAuction(CommandContext<CommandSourceStack> context) {
        OperationResult result = AuctionDatabase.endAuction(playerOrNull(context.getSource()), sourceName(context.getSource()), StringArgumentType.getString(context, "listingId"));
        send(context.getSource(), result);
        return result.success() ? 1 : 0;
    }

    private static int adminRefundListing(CommandContext<CommandSourceStack> context) {
        OperationResult result = AuctionDatabase.refundListing(playerOrNull(context.getSource()), sourceName(context.getSource()), StringArgumentType.getString(context, "listingId"));
        send(context.getSource(), result);
        return result.success() ? 1 : 0;
    }

    private static int adminReturnListing(CommandContext<CommandSourceStack> context) {
        OperationResult result = AuctionDatabase.returnListing(playerOrNull(context.getSource()), sourceName(context.getSource()), StringArgumentType.getString(context, "listingId"));
        send(context.getSource(), result);
        return result.success() ? 1 : 0;
    }

    private static int adminViewClaims(CommandContext<CommandSourceStack> context) {
        List<String> claims = AuctionDatabase.adminClaimSummaries(StringArgumentType.getString(context, "player"));
        if (claims.isEmpty()) {
            context.getSource().sendSuccess(() -> Component.literal("No pending claims found.").withStyle(ChatFormatting.GRAY), false);
            return 0;
        }
        for (String claim : claims) {
            context.getSource().sendSuccess(() -> Component.literal(claim).withStyle(ChatFormatting.YELLOW), false);
        }
        return claims.size();
    }

    private static int adminForceClaim(CommandContext<CommandSourceStack> context) {
        OperationResult result = AuctionDatabase.adminForceClaim(playerOrNull(context.getSource()), sourceName(context.getSource()),
            StringArgumentType.getString(context, "player"), StringArgumentType.getString(context, "claimId"));
        send(context.getSource(), result);
        return result.success() ? 1 : 0;
    }

    private static int adminNumismaticsCheck(CommandContext<CommandSourceStack> context) {
        NumismaticsDiagnostics diagnostics = EconomyManager.diagnostics();
        context.getSource().sendSuccess(() -> Component.literal("Create: Numismatics detected: " + diagnostics.modDetected()
            + " (" + diagnostics.modVersion() + ")").withStyle(diagnostics.modDetected() ? ChatFormatting.GREEN : ChatFormatting.RED), false);
        context.getSource().sendSuccess(() -> Component.literal("Coin item IDs valid: " + diagnostics.coinItemsValid()).withStyle(diagnostics.coinItemsValid() ? ChatFormatting.GREEN : ChatFormatting.YELLOW), false);
        context.getSource().sendSuccess(() -> Component.literal("Bank card item ID valid: " + diagnostics.bankCardItemValid()).withStyle(diagnostics.bankCardItemValid() ? ChatFormatting.GREEN : ChatFormatting.YELLOW), false);
        context.getSource().sendSuccess(() -> Component.literal("Bank API detected: " + diagnostics.bankApiDetected()).withStyle(diagnostics.bankApiDetected() ? ChatFormatting.GREEN : ChatFormatting.YELLOW), false);
        context.getSource().sendSuccess(() -> Component.literal("Bank integration enabled: " + diagnostics.bankIntegrationEnabled()), false);
        context.getSource().sendSuccess(() -> Component.literal("Economy service mode: " + diagnostics.economyMode()), false);
        context.getSource().sendSuccess(() -> Component.literal("Detail: " + diagnostics.detail()).withStyle(ChatFormatting.GRAY), false);
        List<String> missing = PhysicalCoinEconomyService.configuredCurrencyWarnings();
        if (!missing.isEmpty()) {
            context.getSource().sendFailure(Component.literal("Unknown configured currency item(s): " + String.join(", ", missing)));
        }
        ServerPlayer player = playerOrNull(context.getSource());
        if (player != null) {
            AuctionNetwork.sendNotification(player, diagnostics.bankApiDetected() ? "success" : "warning",
                "Numismatics check complete: " + diagnostics.economyMode(), 160);
        }
        return diagnostics.modDetected() && diagnostics.coinItemsValid() ? 1 : 0;
    }

    private static void sendMarketStats(CommandSourceStack source, String itemId, String name) {
        MarketStats stats = AuctionDatabase.marketStats(itemId);
        source.sendSuccess(() -> Component.literal("Market: " + name + " (" + itemId + ")").withStyle(ChatFormatting.GOLD), false);
        source.sendSuccess(() -> Component.literal("Last sold: " + stat(stats.lastSoldPrice())
            + " | 24h avg: " + stat(Math.round(stats.average24h()))
            + " | 7d avg: " + stat(Math.round(stats.average7d()))
            + " | 30d avg: " + stat(Math.round(stats.average30d()))), false);
        source.sendSuccess(() -> Component.literal("Lowest active: " + stat(stats.lowestActiveListing())
            + " | Active: " + stats.activeListingCount()
            + " | Sold 24h/7d: " + stats.soldCount24h() + "/" + stats.soldCount7d()
            + " | Trend: " + stats.trend()), false);
    }

    private static String stat(long value) {
        return value <= 0L ? "unknown" : EconomyManager.service().format(value);
    }

    private static void send(CommandSourceStack source, OperationResult result) {
        if (result.success()) {
            source.sendSuccess(() -> AuctionDatabase.chat(result), true);
        } else {
            source.sendFailure(AuctionDatabase.chat(result));
        }
        ServerPlayer player = playerOrNull(source);
        if (player != null) {
            AuctionNetwork.sendNotification(player, result);
        }
    }

    private static void sendPlayer(ServerPlayer player, OperationResult result) {
        player.sendSystemMessage(AuctionDatabase.chat(result));
        AuctionNetwork.sendNotification(player, result);
    }

    private static List<String> activeListingIds() {
        return AuctionDatabase.allActiveListings().stream().map(listing -> listing.id).toList();
    }

    private static ServerPlayer playerOrNull(CommandSourceStack source) {
        return source.getEntity() instanceof ServerPlayer player ? player : null;
    }

    private static String sourceName(CommandSourceStack source) {
        return source.getEntity() instanceof ServerPlayer player ? player.getGameProfile().getName() : source.getTextName();
    }

    private static boolean hasAnyAdmin(CommandSourceStack source) {
        return has(source, ADMIN_ADD, 2) || has(source, ADMIN_REMOVE, 2) || has(source, ADMIN_CLEAR, 2) || has(source, ADMIN_RELOAD, 2) || has(source, ADMIN_REPAIR, 2);
    }

    private static boolean has(CommandSourceStack source, PermissionNode<Boolean> node, int vanillaLevel) {
        if (source.hasPermission(vanillaLevel)) {
            return true;
        }
        if (source.getEntity() instanceof ServerPlayer player) {
            try {
                return PermissionAPI.getPermission(player, node);
            } catch (RuntimeException ignored) {
                return vanillaLevel <= 0;
            }
        }
        return vanillaLevel <= 0;
    }

    private static PermissionNode<Boolean> node(String path, boolean defaultValue) {
        return new PermissionNode<>(
            ResourceLocation.fromNamespaceAndPath("create_coinmarket", path),
            PermissionTypes.BOOLEAN,
            (player, playerUUID, context) -> defaultValue
        ).setInformation(
            Component.literal("create_coinmarket." + path),
            Component.literal("Allows use of create_coinmarket." + path)
        );
    }
}
