package com.chancemoreland.create_coinmarket.network;

import com.chancemoreland.create_coinmarket.CreateCoinMarket;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record MarketRequestPayload(String mode, int page, String sort, String query) implements CustomPacketPayload {
    public static final Type<MarketRequestPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CreateCoinMarket.MOD_ID, "market_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MarketRequestPayload> STREAM_CODEC = StreamCodec.ofMember(MarketRequestPayload::write, MarketRequestPayload::new);

    public MarketRequestPayload(RegistryFriendlyByteBuf buf) {
        this(buf.readUtf(64), buf.readVarInt(), buf.readUtf(64), buf.readUtf(128));
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeUtf(mode == null ? "all" : mode);
        buf.writeVarInt(page);
        buf.writeUtf(sort == null ? "newest" : sort);
        buf.writeUtf(query == null ? "" : query);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
