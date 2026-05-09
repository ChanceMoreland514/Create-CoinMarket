package com.chancemoreland.create_coinmarket.network;

import com.chancemoreland.create_coinmarket.CreateCoinMarket;
import com.chancemoreland.create_coinmarket.data.MarketScreenData;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record MarketDataPayload(MarketScreenData data) implements CustomPacketPayload {
    public static final Type<MarketDataPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CreateCoinMarket.MOD_ID, "market_data"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MarketDataPayload> STREAM_CODEC = StreamCodec.ofMember(MarketDataPayload::write, MarketDataPayload::new);

    public MarketDataPayload(RegistryFriendlyByteBuf buf) {
        this(MarketScreenData.read(buf));
    }

    public void write(RegistryFriendlyByteBuf buf) {
        data.write(buf);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
