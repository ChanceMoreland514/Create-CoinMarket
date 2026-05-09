package com.chancemoreland.create_coinmarket.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

public final class ItemStackCodec {
    private ItemStackCodec() {
    }

    public static JsonElement encode(ItemStack stack, HolderLookup.Provider lookupProvider) {
        Tag tag = stack.save(lookupProvider);
        return NbtOps.INSTANCE.convertTo(JsonOps.INSTANCE, tag);
    }

    public static String encodeToString(ItemStack stack, HolderLookup.Provider lookupProvider) {
        return encode(stack, lookupProvider).toString();
    }

    public static ItemStack decode(JsonElement json, HolderLookup.Provider lookupProvider) {
        if (json == null || json.isJsonNull()) {
            return ItemStack.EMPTY;
        }
        Tag tag = JsonOps.INSTANCE.convertTo(NbtOps.INSTANCE, json);
        Optional<ItemStack> parsed = ItemStack.parse(lookupProvider, tag);
        return parsed.orElse(ItemStack.EMPTY);
    }

    public static ItemStack decodeFromString(String json, HolderLookup.Provider lookupProvider) {
        if (json == null || json.isBlank()) {
            return ItemStack.EMPTY;
        }
        return decode(JsonParser.parseString(json), lookupProvider);
    }
}
