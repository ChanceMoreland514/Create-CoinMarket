package com.chancemoreland.create_coinmarket.economy;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EconomyService {
    boolean canPay(ServerPlayer player, long amount);

    boolean withdraw(ServerPlayer player, long amount);

    boolean deposit(UUID playerUuid, String playerName, long amount);

    boolean depositOrCreateCollection(UUID playerUuid, String playerName, long amount);

    Optional<List<ItemStack>> makeCoins(long amount);

    String format(long amount);

    default long balance(ServerPlayer player) {
        return 0L;
    }

    default EconomyBalance balanceDetails(ServerPlayer player) {
        return EconomyBalance.coinsOnly(balance(player));
    }

    default String modeName() {
        return "physical_coins";
    }
}
