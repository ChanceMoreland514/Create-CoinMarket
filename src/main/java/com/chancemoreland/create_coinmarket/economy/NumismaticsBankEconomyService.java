package com.chancemoreland.create_coinmarket.economy;

import com.chancemoreland.create_coinmarket.CreateCoinMarket;
import com.chancemoreland.create_coinmarket.data.AuctionConfig;
import com.chancemoreland.create_coinmarket.data.AuctionDatabase;
import com.chancemoreland.create_coinmarket.network.AuctionNetwork;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class NumismaticsBankEconomyService implements EconomyService {
    private static final long MAX_BANK_AMOUNT = Integer.MAX_VALUE;

    private final PhysicalCoinEconomyService fallback;
    private final BankBridge bridge;
    private boolean warnedUnavailable;

    public NumismaticsBankEconomyService(PhysicalCoinEconomyService fallback) {
        this.fallback = fallback;
        this.bridge = BankBridge.create();
        if (bridge.available()) {
            CreateCoinMarket.LOGGER.info("Create: Numismatics bank/card API verified; bank integration enabled.");
        } else {
            CreateCoinMarket.LOGGER.warn("Numismatics bank integration unavailable: {}. Using physical coin fallback when allowed.", bridge.detail());
        }
    }

    @Override
    public boolean canPay(ServerPlayer player, long amount) {
        if (amount <= 0L) {
            return true;
        }
        return switch (AuctionConfig.preferredPaymentSource()) {
            case "coins_only" -> fallback.canPay(player, amount);
            case "bank_only" -> canPayBank(player, amount);
            case "coins_then_bank" -> fallback.canPay(player, amount) || canPayBank(player, amount);
            default -> canPayBank(player, amount) || (AuctionConfig.fallbackToPhysicalCoins() && fallback.canPay(player, amount));
        };
    }

    @Override
    public boolean withdraw(ServerPlayer player, long amount) {
        if (amount <= 0L) {
            return true;
        }
        return switch (AuctionConfig.preferredPaymentSource()) {
            case "coins_only" -> fallback.withdraw(player, amount);
            case "bank_only" -> withdrawBankOrFail(player, amount);
            case "coins_then_bank" -> withdrawCoinsThenBank(player, amount);
            default -> withdrawBankThenCoins(player, amount);
        };
    }

    @Override
    public boolean deposit(UUID playerUuid, String playerName, long amount) {
        if (amount <= 0L) {
            return true;
        }
        if (AuctionConfig.allowSellerPayoutToBank() && depositBank(playerUuid, amount).success()) {
            return true;
        }
        return fallback.deposit(playerUuid, playerName, amount);
    }

    @Override
    public boolean depositOrCreateCollection(UUID playerUuid, String playerName, long amount) {
        if (amount <= 0L) {
            return true;
        }
        if (AuctionConfig.allowSellerPayoutToBank() && depositBank(playerUuid, amount).success()) {
            return true;
        }
        return AuctionDatabase.createCoinCollection(playerUuid, playerName, amount, "payout");
    }

    @Override
    public Optional<List<ItemStack>> makeCoins(long amount) {
        return fallback.makeCoins(amount);
    }

    @Override
    public String format(long amount) {
        return fallback.format(amount);
    }

    @Override
    public long balance(ServerPlayer player) {
        return balanceDetails(player).totalSpendableBalance();
    }

    @Override
    public EconomyBalance balanceDetails(ServerPlayer player) {
        long coinBalance = fallback.balance(player);
        BankBalanceResult bank = bankBalance(player);
        boolean includeCoins = !"bank_only".equals(AuctionConfig.preferredPaymentSource());
        boolean includeBank = !"coins_only".equals(AuctionConfig.preferredPaymentSource()) && bank.available();
        long total = 0L;
        if (includeCoins) {
            total = safeAdd(total, coinBalance);
        }
        if (includeBank) {
            total = safeAdd(total, bank.balance());
        }
        String source = switch (AuctionConfig.preferredPaymentSource()) {
            case "coins_only" -> "coins only";
            case "bank_only" -> bank.available() ? "bank only" : "bank unavailable";
            case "coins_then_bank" -> bank.available() ? "coins then bank" : "coins";
            default -> bank.available() ? "bank then coins" : "coins";
        };
        String warning = "";
        if (AuctionConfig.enableNumismaticsBankIntegration() && !bridge.available()) {
            warning = "Bank API unavailable: " + bridge.detail();
        } else if (bank.hasCard() && !bank.available()) {
            warning = bank.message();
        }
        return new EconomyBalance(coinBalance, bank.balance(), total, source, bank.hasCard(), bank.available(), warning);
    }

    @Override
    public String modeName() {
        if (!AuctionConfig.enableNumismaticsBankIntegration()) {
            return "physical_coins";
        }
        return bridge.available() ? "numismatics_bank_plus_physical_coins" : "physical_coins_bank_unavailable";
    }

    public BankBalanceResult bankBalance(ServerPlayer player) {
        if (!AuctionConfig.enableNumismaticsBankIntegration()) {
            return BankBalanceResult.unavailable("Bank integration is disabled.");
        }
        if (!bridge.available()) {
            warnUnavailableOnce();
            return BankBalanceResult.unavailable(bridge.detail());
        }
        try {
            AccountRef account = resolveSpendAccount(player);
            if (account == null) {
                return new BankBalanceResult(false, false, null, 0L, "No bound Numismatics bank card found.");
            }
            return new BankBalanceResult(account.authorized(), true, account.accountId(), account.authorized() ? account.balance() : 0L,
                account.authorized() ? "" : "Bank card is not authorized for this player.");
        } catch (ReflectiveOperationException | RuntimeException ex) {
            CreateCoinMarket.LOGGER.warn("Failed to read Numismatics bank/card balance for {}", player.getGameProfile().getName(), ex);
            return new BankBalanceResult(false, true, null, 0L, "Bank/card API failed.");
        }
    }

    public NumismaticsDiagnostics diagnostics() {
        List<String> missingCoins = PhysicalCoinEconomyService.configuredCurrencyWarnings();
        boolean cardItem = BankBridge.anyBankCardItemRegistered();
        String version = ModList.get().getModContainerById("numismatics")
            .map(container -> container.getModInfo().getVersion().toString())
            .orElse("missing");
        return new NumismaticsDiagnostics(
            ModList.get().isLoaded("numismatics"),
            version,
            missingCoins.isEmpty(),
            cardItem,
            bridge.available(),
            AuctionConfig.enableNumismaticsBankIntegration(),
            modeName(),
            bridge.detail()
        );
    }

    public boolean bankApiAvailable() {
        return bridge.available();
    }

    private boolean withdrawBankThenCoins(ServerPlayer player, long amount) {
        BankWithdrawResult bank = withdrawBank(player, amount);
        if (bank.success()) {
            return true;
        }
        if (canFallbackFromBankFailure() && fallback.withdraw(player, amount)) {
            AuctionNetwork.sendNotification(player, "warning", "Bank/card payment unavailable or insufficient; paid with physical coins.", 160);
            return true;
        }
        notifyBankFailure(player, bank);
        return false;
    }

    private boolean withdrawCoinsThenBank(ServerPlayer player, long amount) {
        if (fallback.withdraw(player, amount)) {
            return true;
        }
        BankWithdrawResult bank = withdrawBank(player, amount);
        if (bank.success()) {
            return true;
        }
        notifyBankFailure(player, bank);
        return false;
    }

    private boolean withdrawBankOrFail(ServerPlayer player, long amount) {
        BankWithdrawResult bank = withdrawBank(player, amount);
        if (bank.success()) {
            return true;
        }
        notifyBankFailure(player, bank);
        return false;
    }

    private boolean canPayBank(ServerPlayer player, long amount) {
        BankBalanceResult balance = bankBalance(player);
        return balance.available() && amount <= MAX_BANK_AMOUNT && balance.balance() >= amount;
    }

    private BankWithdrawResult withdrawBank(ServerPlayer player, long amount) {
        if (!AuctionConfig.enableNumismaticsBankIntegration()) {
            return BankWithdrawResult.fail("Bank integration is disabled.");
        }
        if (!bridge.available()) {
            warnUnavailableOnce();
            return BankWithdrawResult.fail(bridge.detail());
        }
        if (amount > MAX_BANK_AMOUNT) {
            return BankWithdrawResult.fail("Numismatics bank accounts cannot withdraw more than " + MAX_BANK_AMOUNT + " at once.");
        }
        try {
            AccountRef account = resolveSpendAccount(player);
            if (account == null) {
                return BankWithdrawResult.fail("No bound Numismatics bank card found.");
            }
            if (!account.authorized()) {
                return BankWithdrawResult.fail("Bank card is not authorized for this player.");
            }
            if (account.balance() < amount) {
                return BankWithdrawResult.fail("Not enough bank/card balance.");
            }
            return account.deduct((int) amount)
                ? BankWithdrawResult.success("Paid from Numismatics bank/card account.")
                : BankWithdrawResult.fail("Numismatics bank/card account rejected the withdrawal.");
        } catch (ReflectiveOperationException | RuntimeException ex) {
            CreateCoinMarket.LOGGER.warn("Failed to withdraw from Numismatics bank/card for {}", player.getGameProfile().getName(), ex);
            return BankWithdrawResult.fail("Bank/card API failed.");
        }
    }

    private BankDepositResult depositBank(UUID playerUuid, long amount) {
        if (!AuctionConfig.enableNumismaticsBankIntegration() || !AuctionConfig.allowSellerPayoutToBank()) {
            return BankDepositResult.fail("Bank seller payouts disabled.");
        }
        if (!bridge.available()) {
            warnUnavailableOnce();
            return BankDepositResult.fail(bridge.detail());
        }
        if (amount > MAX_BANK_AMOUNT) {
            return BankDepositResult.fail("Payout exceeds Numismatics bank integer balance range.");
        }
        try {
            Object account = bridge.getAccount(playerUuid);
            if (account == null) {
                return BankDepositResult.fail("Seller bank account not found.");
            }
            long balance = bridge.getBalance(account);
            if (balance > MAX_BANK_AMOUNT - amount) {
                return BankDepositResult.fail("Seller bank account would overflow.");
            }
            bridge.deposit(account, (int) amount);
            return BankDepositResult.success("Deposited into seller Numismatics bank account.");
        } catch (ReflectiveOperationException | RuntimeException ex) {
            CreateCoinMarket.LOGGER.warn("Failed to deposit into Numismatics bank account {}", playerUuid, ex);
            return BankDepositResult.fail("Bank deposit API failed.");
        }
    }

    private AccountRef resolveSpendAccount(ServerPlayer player) throws ReflectiveOperationException {
        if (!AuctionConfig.requireBankCardForBankPayments()) {
            Object account = bridge.getPlayerAccount(player);
            return account == null ? null : AccountRef.from(bridge, account, player.getUUID());
        }

        CardRef card = bridge.findBoundCard(player);
        if (card == null) {
            return null;
        }
        Object account = bridge.getAccount(card.accountId());
        if (account == null && player.getUUID().equals(card.accountId())) {
            account = bridge.getPlayerAccount(player);
        }
        return account == null ? null : AccountRef.from(bridge, account, player.getUUID());
    }

    private boolean canFallbackFromBankFailure() {
        return AuctionConfig.fallbackToPhysicalCoins() && !"block_purchase".equals(AuctionConfig.bankIntegrationFailureMode());
    }

    private void notifyBankFailure(ServerPlayer player, BankWithdrawResult bank) {
        if ("block_purchase".equals(AuctionConfig.bankIntegrationFailureMode()) || "bank_only".equals(AuctionConfig.preferredPaymentSource())) {
            AuctionNetwork.sendNotification(player, "error", bank.message(), 180);
        }
    }

    private void warnUnavailableOnce() {
        if (!warnedUnavailable) {
            warnedUnavailable = true;
            CreateCoinMarket.LOGGER.warn("Numismatics bank integration not available; physical coin fallback is {}. Reason: {}",
                AuctionConfig.fallbackToPhysicalCoins() ? "enabled" : "disabled", bridge.detail());
        }
    }

    private static long safeAdd(long a, long b) {
        long result = a + b;
        if (((a ^ result) & (b ^ result)) < 0L) {
            return Long.MAX_VALUE;
        }
        return result;
    }

    private record CardRef(UUID accountId, String itemId) {
    }

    private record AccountRef(BankBridge bridge, Object account, UUID playerUuid, UUID accountId, boolean authorized, long balance) {
        static AccountRef from(BankBridge bridge, Object account, UUID playerUuid) throws ReflectiveOperationException {
            UUID accountId = bridge.accountId(account);
            boolean authorized = bridge.isAuthorized(account, playerUuid);
            long balance = authorized ? bridge.getBalance(account) : 0L;
            return new AccountRef(bridge, account, playerUuid, accountId, authorized, balance);
        }

        boolean deduct(int amount) throws ReflectiveOperationException {
            return bridge.deduct(account, amount);
        }
    }

    private static final class BankBridge {
        private final boolean available;
        private final String detail;
        private final Object bank;
        private final Class<?> cardItemClass;
        private final Method cardGet;
        private final Method getAccountPlayer;
        private final Method getAccountUuid;
        private final Method getBalance;
        private final Method deposit;
        private final Method deduct;
        private final Method isAuthorized;
        private final Field accountId;

        private BankBridge(boolean available, String detail, Object bank, Class<?> cardItemClass, Method cardGet,
                           Method getAccountPlayer, Method getAccountUuid, Method getBalance, Method deposit,
                           Method deduct, Method isAuthorized, Field accountId) {
            this.available = available;
            this.detail = detail;
            this.bank = bank;
            this.cardItemClass = cardItemClass;
            this.cardGet = cardGet;
            this.getAccountPlayer = getAccountPlayer;
            this.getAccountUuid = getAccountUuid;
            this.getBalance = getBalance;
            this.deposit = deposit;
            this.deduct = deduct;
            this.isAuthorized = isAuthorized;
            this.accountId = accountId;
        }

        static BankBridge create() {
            try {
                Class<?> numismatics = Class.forName("dev.ithundxr.createnumismatics.Numismatics");
                Field bankField = numismatics.getField("BANK");
                Object bank = bankField.get(null);
                Class<?> cardItemClass = Class.forName("dev.ithundxr.createnumismatics.content.bank.CardItem");
                Class<?> bankManagerClass = Class.forName("dev.ithundxr.createnumismatics.content.backend.GlobalBankManager");
                Class<?> accountClass = Class.forName("dev.ithundxr.createnumismatics.content.backend.BankAccount");
                Method cardGet = cardItemClass.getMethod("get", ItemStack.class);
                Method getAccountPlayer = bankManagerClass.getMethod("getAccount", Player.class);
                Method getAccountUuid = bankManagerClass.getMethod("getAccount", UUID.class);
                Method getBalance = accountClass.getMethod("getBalance");
                Method deposit = accountClass.getMethod("deposit", int.class);
                Method deduct = accountClass.getMethod("deduct", int.class);
                Method isAuthorized = accountClass.getMethod("isAuthorized", UUID.class);
                Field accountId = accountClass.getField("id");
                return new BankBridge(true, "verified", bank, cardItemClass, cardGet, getAccountPlayer, getAccountUuid,
                    getBalance, deposit, deduct, isAuthorized, accountId);
            } catch (ReflectiveOperationException | LinkageError ex) {
                return unavailable(ex.getClass().getSimpleName() + ": " + ex.getMessage());
            }
        }

        static BankBridge unavailable(String detail) {
            return new BankBridge(false, detail, null, null, null, null, null, null, null, null, null, null);
        }

        static boolean anyBankCardItemRegistered() {
            for (String color : List.of("white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
                "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black")) {
                ResourceLocation id = ResourceLocation.fromNamespaceAndPath("numismatics", color + "_card");
                if (BuiltInRegistries.ITEM.containsKey(id)) {
                    return true;
                }
            }
            return false;
        }

        boolean available() {
            return available;
        }

        String detail() {
            return detail;
        }

        CardRef findBoundCard(ServerPlayer player) throws ReflectiveOperationException {
            CardRef card = findBoundCard(player.getInventory().items);
            if (card != null) {
                return card;
            }
            card = findBoundCard(player.getInventory().offhand);
            if (card != null) {
                return card;
            }
            return findBoundCard(player.getInventory().armor);
        }

        private CardRef findBoundCard(NonNullList<ItemStack> stacks) throws ReflectiveOperationException {
            for (ItemStack stack : stacks) {
                if (stack.isEmpty() || !cardItemClass.isInstance(stack.getItem())) {
                    continue;
                }
                Object value = cardGet.invoke(null, stack);
                if (value instanceof UUID accountId) {
                    ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
                    return new CardRef(accountId, itemId == null ? "unknown" : itemId.toString());
                }
            }
            return null;
        }

        Object getPlayerAccount(ServerPlayer player) throws ReflectiveOperationException {
            return getAccountPlayer.invoke(bank, player);
        }

        Object getAccount(UUID uuid) throws ReflectiveOperationException {
            return getAccountUuid.invoke(bank, uuid);
        }

        UUID accountId(Object account) throws ReflectiveOperationException {
            Object value = accountId.get(account);
            return value instanceof UUID uuid ? uuid : null;
        }

        long getBalance(Object account) throws ReflectiveOperationException {
            Object value = getBalance.invoke(account);
            return value instanceof Number number ? number.longValue() : 0L;
        }

        void deposit(Object account, int amount) throws ReflectiveOperationException {
            deposit.invoke(account, amount);
        }

        boolean deduct(Object account, int amount) throws ReflectiveOperationException {
            Object value = deduct.invoke(account, amount);
            return value instanceof Boolean success && success;
        }

        boolean isAuthorized(Object account, UUID playerUuid) throws ReflectiveOperationException {
            Object value = isAuthorized.invoke(account, playerUuid);
            return value instanceof Boolean authorized && authorized;
        }
    }
}
