package com.chancemoreland.create_coinmarket.economy;

public record BankDepositResult(boolean success, String message) {
    public static BankDepositResult success(String message) {
        return new BankDepositResult(true, message);
    }

    public static BankDepositResult fail(String message) {
        return new BankDepositResult(false, message);
    }
}
