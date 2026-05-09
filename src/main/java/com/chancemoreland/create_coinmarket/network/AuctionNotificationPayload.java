package com.chancemoreland.create_coinmarket.network;

import com.chancemoreland.create_coinmarket.CreateCoinMarket;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record AuctionNotificationPayload(String notificationType, String title, String message, int durationTicks) implements CustomPacketPayload {
    public static final Type<AuctionNotificationPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CreateCoinMarket.MOD_ID, "auction_notification"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AuctionNotificationPayload> STREAM_CODEC = StreamCodec.ofMember(AuctionNotificationPayload::write, AuctionNotificationPayload::new);

    public AuctionNotificationPayload(RegistryFriendlyByteBuf buf) {
        this(buf.readUtf(24), buf.readUtf(96), buf.readUtf(32767), buf.readVarInt());
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeUtf(notificationType == null ? "info" : notificationType, 24);
        buf.writeUtf(title == null ? "" : title, 96);
        buf.writeUtf(message == null ? "" : message, 32767);
        buf.writeVarInt(Math.max(20, durationTicks));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
