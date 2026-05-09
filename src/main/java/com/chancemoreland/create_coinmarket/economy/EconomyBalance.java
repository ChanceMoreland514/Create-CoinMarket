package com.chancemoreland.create_coinmarket.economy;

public record EconomyBalance(
    long coinBalance,
    long bankBalance,
    long totalSpendableBalance,
    String activePaymentSource,
    boolean hasBankCard,
    boolean bankAvailable,
    String warning
) {
    public static EconomyBalance coinsOnly(long coinBalance) {
        return new EconomyBalance(coinBalance, 0L, coinBalance, "coins", false, false, "");
    }
}
