package com.chancemoreland.create_coinmarket.economy;

import java.util.UUID;

public record BankBalanceResult(
    boolean available,
    boolean hasCard,
    UUID accountId,
    long balance,
    String message
) {
    public static BankBalanceResult unavailable(String message) {
        return new BankBalanceResult(false, false, null, 0L, message);
    }
}
