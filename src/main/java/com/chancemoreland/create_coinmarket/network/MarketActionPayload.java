package com.chancemoreland.create_coinmarket.network;

import com.chancemoreland.create_coinmarket.CreateCoinMarket;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record MarketActionPayload(String action, String listingId, String mode, int page, String sort, String query, long amount, int quantity, String extra) implements CustomPacketPayload {
    public static final Type<MarketActionPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CreateCoinMarket.MOD_ID, "market_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MarketActionPayload> STREAM_CODEC = StreamCodec.ofMember(MarketActionPayload::write, MarketActionPayload::new);

    public MarketActionPayload(RegistryFriendlyByteBuf buf) {
        this(buf.readUtf(64), buf.readUtf(128), buf.readUtf(64), buf.readVarInt(), buf.readUtf(64), buf.readUtf(128),
            buf.readLong(), buf.readVarInt(), buf.readUtf(256));
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeUtf(action == null ? "refresh" : action);
        buf.writeUtf(listingId == null ? "" : listingId);
        buf.writeUtf(mode == null ? "all" : mode);
        buf.writeVarInt(page);
        buf.writeUtf(sort == null ? "newest" : sort);
        buf.writeUtf(query == null ? "" : query);
        buf.writeLong(amount);
        buf.writeVarInt(Math.max(0, quantity));
        buf.writeUtf(extra == null ? "" : extra, 256);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
