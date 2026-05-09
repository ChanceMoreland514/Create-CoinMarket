package com.chancemoreland.create_coinmarket.economy;

public record BankWithdrawResult(boolean success, String message) {
    public static BankWithdrawResult success(String message) {
        return new BankWithdrawResult(true, message);
    }

    public static BankWithdrawResult fail(String message) {
        return new BankWithdrawResult(false, message);
    }
}
