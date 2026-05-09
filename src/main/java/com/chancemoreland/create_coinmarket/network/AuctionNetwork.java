package com.chancemoreland.create_coinmarket.network;

import com.chancemoreland.create_coinmarket.CreateCoinMarket;
import com.chancemoreland.create_coinmarket.data.AuctionConfig;
import com.chancemoreland.create_coinmarket.data.AuctionDatabase;
import com.chancemoreland.create_coinmarket.data.AuctionDatabase.OperationResult;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.lang.reflect.Method;

public final class AuctionNetwork {
    private AuctionNetwork() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(CreateCoinMarket.NETWORK_PROTOCOL_VERSION);
        registrar.playToServer(MarketRequestPayload.TYPE, MarketRequestPayload.STREAM_CODEC, AuctionNetwork::handleRequest);
        registrar.playToServer(MarketActionPayload.TYPE, MarketActionPayload.STREAM_CODEC, AuctionNetwork::handleAction);
        registrar.playToClient(OpenMarketScreenPayload.TYPE, OpenMarketScreenPayload.STREAM_CODEC, AuctionNetwork::handleClientOpen);
        registrar.playToClient(MarketDataPayload.TYPE, MarketDataPayload.STREAM_CODEC, AuctionNetwork::handleClientData);
        registrar.playToClient(AuctionNotificationPayload.TYPE, AuctionNotificationPayload.STREAM_CODEC, AuctionNetwork::handleClientNotification);
    }

    public static void requestData(String mode, int page, String sort, String query) {
        PacketDistributor.sendToServer(new MarketRequestPayload(mode, page, sort, query));
    }

    public static void sendAction(String action, String listingId, String mode, int page, String sort, String query) {
        sendAction(action, listingId, mode, page, sort, query, 0L, 0, "");
    }

    public static void sendAction(String action, String listingId, String mode, int page, String sort, String query, long amount, int quantity, String extra) {
        PacketDistributor.sendToServer(new MarketActionPayload(action, listingId, mode, page, sort, query, amount, quantity, extra));
    }

    public static void openMarket(ServerPlayer player, String mode, int page) {
        PacketDistributor.sendToPlayer(player, new OpenMarketScreenPayload(mode, page));
    }

    public static void sendNotification(ServerPlayer player, OperationResult result) {
        sendNotification(player, result.success() ? "success" : "error", result.success() ? "CoinMarket" : "Action Failed", result.message(), result.success() ? 120 : 180);
    }

    public static void sendNotification(ServerPlayer player, String type, String message, int durationTicks) {
        sendNotification(player, type, switch (type == null ? "info" : type) {
            case "success" -> "Success";
            case "error" -> "Error";
            case "warning" -> "Warning";
            default -> "CoinMarket";
        }, message, durationTicks);
    }

    public static void sendNotification(ServerPlayer player, String type, String title, String message, int durationTicks) {
        PacketDistributor.sendToPlayer(player, new AuctionNotificationPayload(type, title, message, durationTicks));
    }

    private static void handleRequest(MarketRequestPayload payload, IPayloadContext context) {
        Player player = context.player();
        if (player instanceof ServerPlayer serverPlayer) {
            sendData(serverPlayer, payload.mode(), payload.page(), payload.sort(), payload.query());
        }
    }

    private static void handleAction(MarketActionPayload payload, IPayloadContext context) {
        Player player = context.player();
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }

        OperationResult result = switch (payload.action()) {
            case "buy" -> AuctionDatabase.buyListing(serverPlayer, payload.listingId());
            case "bid" -> AuctionDatabase.placeBid(serverPlayer, payload.listingId(), payload.amount());
            case "buyout" -> AuctionDatabase.buyoutAuction(serverPlayer, payload.listingId());
            case "sell_fixed_hand" -> AuctionDatabase.addPublicListing(serverPlayer, payload.amount(), payload.quantity(), parseFixedDuration(payload.extra()));
            case "sell_auction_hand" -> AuctionDatabase.addAuctionListing(serverPlayer, payload.amount(), payload.quantity(), payload.extra());
            case "cancel" -> AuctionDatabase.cancelOwnListing(serverPlayer, payload.listingId());
            case "collect" -> AuctionDatabase.collect(serverPlayer);
            case "collect_money" -> AuctionDatabase.collectMoney(serverPlayer);
            case "collect_one" -> AuctionDatabase.collectOne(serverPlayer, payload.listingId());
            case "admin_remove" -> serverPlayer.hasPermissions(2)
                ? AuctionDatabase.adminRemove(serverPlayer, serverPlayer.getGameProfile().getName(), payload.listingId())
                : OperationResult.fail("You do not have permission to remove listings.");
            case "admin_repair", "clear_expired" -> serverPlayer.hasPermissions(2)
                ? AuctionDatabase.repair(serverPlayer, serverPlayer.getGameProfile().getName())
                : OperationResult.fail("You do not have permission to repair the market.");
            case "admin_backup" -> serverPlayer.hasPermissions(2)
                ? AuctionDatabase.backupDatabase(serverPlayer, serverPlayer.getGameProfile().getName())
                : OperationResult.fail("You do not have permission to back up the market.");
            default -> OperationResult.success("Market refreshed.");
        };

        serverPlayer.sendSystemMessage(AuctionDatabase.chat(result));
        sendNotification(serverPlayer, result);
        sendData(serverPlayer, payload.mode(), payload.page(), payload.sort(), payload.query());
    }

    private static void sendData(ServerPlayer player, String mode, int page, String sort, String query) {
        PacketDistributor.sendToPlayer(player, new MarketDataPayload(AuctionDatabase.screenData(player, mode, page, sort, query)));
    }

    private static long parseFixedDuration(String raw) {
        long fallback = Math.max(1L, AuctionConfig.publicDurationMillis() / (60L * 60L * 1000L));
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }

    private static void handleClientOpen(OpenMarketScreenPayload payload, IPayloadContext context) {
        if (!FMLEnvironment.dist.isClient()) {
            return;
        }
        try {
            Class<?> handler = Class.forName("com.chancemoreland.create_coinmarket.client.ClientMarketDataHandler");
            Method method = handler.getMethod("open", OpenMarketScreenPayload.class);
            method.invoke(null, payload);
        } catch (ReflectiveOperationException ex) {
            CreateCoinMarket.LOGGER.warn("Unable to open Create: CoinMarket client UI", ex);
        }
    }

    private static void handleClientData(MarketDataPayload payload, IPayloadContext context) {
        if (!FMLEnvironment.dist.isClient()) {
            return;
        }
        try {
            Class<?> handler = Class.forName("com.chancemoreland.create_coinmarket.client.ClientMarketDataHandler");
            Method method = handler.getMethod("handle", MarketDataPayload.class);
            method.invoke(null, payload);
        } catch (ReflectiveOperationException ex) {
            CreateCoinMarket.LOGGER.warn("Unable to deliver market data to client UI", ex);
        }
    }

    private static void handleClientNotification(AuctionNotificationPayload payload, IPayloadContext context) {
        if (!FMLEnvironment.dist.isClient()) {
            return;
        }
        try {
            Class<?> handler = Class.forName("com.chancemoreland.create_coinmarket.client.ClientMarketDataHandler");
            Method method = handler.getMethod("handleNotification", AuctionNotificationPayload.class);
            method.invoke(null, payload);
        } catch (ReflectiveOperationException ex) {
            CreateCoinMarket.LOGGER.warn("Unable to deliver auction notification to client UI", ex);
        }
    }
}
