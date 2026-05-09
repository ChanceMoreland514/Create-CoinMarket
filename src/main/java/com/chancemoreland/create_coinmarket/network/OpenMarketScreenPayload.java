package com.chancemoreland.create_coinmarket.network;

import com.chancemoreland.create_coinmarket.CreateCoinMarket;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record OpenMarketScreenPayload(String mode, int page) implements CustomPacketPayload {
    public static final Type<OpenMarketScreenPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CreateCoinMarket.MOD_ID, "open_market"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenMarketScreenPayload> STREAM_CODEC = StreamCodec.ofMember(OpenMarketScreenPayload::write, OpenMarketScreenPayload::new);

    public OpenMarketScreenPayload(RegistryFriendlyByteBuf buf) {
        this(buf.readUtf(64), buf.readVarInt());
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeUtf(mode == null ? "all" : mode, 64);
        buf.writeVarInt(Math.max(0, page));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
