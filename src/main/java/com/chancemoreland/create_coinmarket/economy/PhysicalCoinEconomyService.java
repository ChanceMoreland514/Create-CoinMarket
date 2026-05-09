package com.chancemoreland.create_coinmarket.economy;

import com.chancemoreland.create_coinmarket.CreateCoinMarket;
import com.chancemoreland.create_coinmarket.data.AuctionConfig;
import com.chancemoreland.create_coinmarket.data.AuctionDatabase;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class PhysicalCoinEconomyService implements EconomyService {
    @Override
    public boolean canPay(ServerPlayer player, long amount) {
        return planPayment(player, amount).success();
    }

    @Override
    public boolean withdraw(ServerPlayer player, long amount) {
        PaymentPlan plan = planPayment(player, amount);
        if (!plan.success()) {
            return false;
        }
        Inventory inventory = player.getInventory();
        applyRemovals(inventory.items, plan.removals());
        addStacks(player, plan.changeStacks());
        return true;
    }

    @Override
    public boolean deposit(UUID playerUuid, String playerName, long amount) {
        ServerPlayer player = AuctionDatabase.server().getPlayerList().getPlayer(playerUuid);
        Optional<List<ItemStack>> coins = makeCoins(amount);
        if (player == null || coins.isEmpty() || !canFit(player, coins.get())) {
            return false;
        }
        return addStacks(player, coins.get());
    }

    @Override
    public boolean depositOrCreateCollection(UUID playerUuid, String playerName, long amount) {
        if (amount <= 0L || deposit(playerUuid, playerName, amount)) {
            return true;
        }
        return AuctionDatabase.createCoinCollection(playerUuid, playerName, amount, "payout");
    }

    @Override
    public Optional<List<ItemStack>> makeCoins(long amount) {
        if (amount < 0L) {
            return Optional.empty();
        }
        List<ItemStack> stacks = new ArrayList<>();
        long remaining = amount;
        for (Denomination denomination : denominations()) {
            if (remaining < denomination.value()) {
                continue;
            }
            long count = remaining / denomination.value();
            remaining %= denomination.value();
            int maxStackSize = new ItemStack(denomination.item()).getMaxStackSize();
            while (count > 0L) {
                int stackSize = (int) Math.min(maxStackSize, count);
                stacks.add(new ItemStack(denomination.item(), stackSize));
                count -= stackSize;
            }
        }
        return remaining == 0L ? Optional.of(stacks) : Optional.empty();
    }

    @Override
    public String format(long amount) {
        return amount + " spurs";
    }

    @Override
    public long balance(ServerPlayer player) {
        long total = 0L;
        List<Denomination> denominations = denominations();
        for (ItemStack stack : player.getInventory().items) {
            if (stack.isEmpty()) {
                continue;
            }
            for (Denomination denomination : denominations) {
                if (denomination.matches(stack)) {
                    total += denomination.value() * stack.getCount();
                    break;
                }
            }
        }
        return total;
    }

    @Override
    public EconomyBalance balanceDetails(ServerPlayer player) {
        return EconomyBalance.coinsOnly(balance(player));
    }

    @Override
    public String modeName() {
        return "physical_coins";
    }

    public static boolean canFit(ServerPlayer player, List<ItemStack> additions) {
        return canFit(copyMainInventory(player.getInventory().items), copyStacks(additions));
    }

    public static boolean addStacks(ServerPlayer player, List<ItemStack> stacks) {
        boolean success = addStacks(player.getInventory().items, copyStacks(stacks));
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
        return success;
    }

    public static List<String> configuredCurrencyWarnings() {
        List<String> warnings = new ArrayList<>();
        for (AuctionConfig.CurrencyEntry entry : AuctionConfig.currencyItems()) {
            ResourceLocation id = parseId(entry.item());
            if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
                warnings.add(entry.item());
            }
        }
        return warnings;
    }

    private PaymentPlan planPayment(ServerPlayer player, long amount) {
        if (amount <= 0L) {
            return PaymentPlan.success(List.of(), List.of());
        }
        List<Denomination> denominations = denominations();
        if (denominations.isEmpty()) {
            return PaymentPlan.fail();
        }
        List<ItemStack> slots = player.getInventory().items;
        List<SlotRemoval> removals = new ArrayList<>();
        long removedValue = 0L;

        for (Denomination denomination : denominations) {
            if (removedValue >= amount || denomination.value() > amount - removedValue) {
                continue;
            }
            long wanted = (amount - removedValue) / denomination.value();
            for (int slot = 0; slot < slots.size() && wanted > 0L; slot++) {
                ItemStack stack = slots.get(slot);
                if (!denomination.matches(stack)) {
                    continue;
                }
                int toTake = (int) Math.min(stack.getCount(), wanted);
                removals.add(new SlotRemoval(slot, toTake));
                removedValue += toTake * denomination.value();
                wanted -= toTake;
            }
        }

        if (removedValue < amount) {
            long remaining = amount - removedValue;
            Optional<Denomination> overpay = denominations.stream()
                .filter(denomination -> denomination.value() >= remaining && countAvailable(slots, removals, denomination) > 0)
                .min(Comparator.comparingLong(Denomination::value));
            if (overpay.isPresent()) {
                int slot = firstAvailableSlot(slots, removals, overpay.get());
                if (slot >= 0) {
                    removals.add(new SlotRemoval(slot, 1));
                    removedValue += overpay.get().value();
                }
            }
        }

        if (removedValue < amount) {
            return PaymentPlan.fail();
        }
        Optional<List<ItemStack>> change = makeCoins(removedValue - amount);
        if (change.isEmpty()) {
            return PaymentPlan.fail();
        }

        NonNullList<ItemStack> simulated = copyMainInventory(slots);
        applyRemovals(simulated, removals);
        if (!canFit(simulated, change.get())) {
            return PaymentPlan.fail();
        }
        return PaymentPlan.success(removals, change.get());
    }

    private static List<Denomination> denominations() {
        List<Denomination> denominations = new ArrayList<>();
        for (AuctionConfig.CurrencyEntry entry : AuctionConfig.currencyItems()) {
            ResourceLocation id = parseId(entry.item());
            if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
                CreateCoinMarket.LOGGER.debug("Skipping unknown currency item '{}'", entry.item());
                continue;
            }
            denominations.add(new Denomination(BuiltInRegistries.ITEM.get(id), entry.value()));
        }
        denominations.sort(Comparator.comparingLong(Denomination::value).reversed());
        return denominations;
    }

    private static ResourceLocation parseId(String raw) {
        try {
            return ResourceLocation.parse(raw);
        } catch (Exception ex) {
            return null;
        }
    }

    private static long countAvailable(List<ItemStack> slots, List<SlotRemoval> removals, Denomination denomination) {
        long count = 0L;
        for (int slot = 0; slot < slots.size(); slot++) {
            ItemStack stack = slots.get(slot);
            if (!denomination.matches(stack)) {
                continue;
            }
            int removing = 0;
            for (SlotRemoval removal : removals) {
                if (removal.slot() == slot) {
                    removing += removal.count();
                }
            }
            count += Math.max(0, stack.getCount() - removing);
        }
        return count;
    }

    private static int firstAvailableSlot(List<ItemStack> slots, List<SlotRemoval> removals, Denomination denomination) {
        for (int slot = 0; slot < slots.size(); slot++) {
            ItemStack stack = slots.get(slot);
            if (!denomination.matches(stack)) {
                continue;
            }
            int removing = 0;
            for (SlotRemoval removal : removals) {
                if (removal.slot() == slot) {
                    removing += removal.count();
                }
            }
            if (stack.getCount() > removing) {
                return slot;
            }
        }
        return -1;
    }

    private static NonNullList<ItemStack> copyMainInventory(List<ItemStack> source) {
        NonNullList<ItemStack> copy = NonNullList.withSize(source.size(), ItemStack.EMPTY);
        for (int i = 0; i < source.size(); i++) {
            copy.set(i, source.get(i).copy());
        }
        return copy;
    }

    private static List<ItemStack> copyStacks(List<ItemStack> stacks) {
        List<ItemStack> copies = new ArrayList<>();
        for (ItemStack stack : stacks) {
            if (!stack.isEmpty()) {
                copies.add(stack.copy());
            }
        }
        return copies;
    }

    private static void applyRemovals(List<ItemStack> slots, List<SlotRemoval> removals) {
        for (SlotRemoval removal : removals) {
            ItemStack stack = slots.get(removal.slot());
            stack.shrink(removal.count());
            if (stack.isEmpty()) {
                slots.set(removal.slot(), ItemStack.EMPTY);
            }
        }
    }

    private static boolean canFit(List<ItemStack> slots, List<ItemStack> additions) {
        return addStacks(slots, additions);
    }

    private static boolean addStacks(List<ItemStack> slots, List<ItemStack> additions) {
        for (ItemStack addition : additions) {
            ItemStack remaining = addition.copy();
            for (int i = 0; i < slots.size() && !remaining.isEmpty(); i++) {
                ItemStack existing = slots.get(i);
                if (existing.isEmpty() || !ItemStack.isSameItemSameComponents(existing, remaining)) {
                    continue;
                }
                int room = existing.getMaxStackSize() - existing.getCount();
                if (room <= 0) {
                    continue;
                }
                int moved = Math.min(room, remaining.getCount());
                existing.grow(moved);
                remaining.shrink(moved);
            }
            for (int i = 0; i < slots.size() && !remaining.isEmpty(); i++) {
                if (!slots.get(i).isEmpty()) {
                    continue;
                }
                int moved = Math.min(remaining.getMaxStackSize(), remaining.getCount());
                slots.set(i, remaining.copyWithCount(moved));
                remaining.shrink(moved);
            }
            if (!remaining.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private record Denomination(Item item, long value) {
        boolean matches(ItemStack stack) {
            return !stack.isEmpty() && stack.is(item);
        }
    }

    private record SlotRemoval(int slot, int count) {
    }

    private record PaymentPlan(boolean success, List<SlotRemoval> removals, List<ItemStack> changeStacks) {
        static PaymentPlan success(List<SlotRemoval> removals, List<ItemStack> changeStacks) {
            return new PaymentPlan(true, removals, changeStacks);
        }

        static PaymentPlan fail() {
            return new PaymentPlan(false, List.of(), List.of());
        }
    }
}
