package com.chancemoreland.create_coinmarket.economy;

public record NumismaticsDiagnostics(
    boolean modDetected,
    String modVersion,
    boolean coinItemsValid,
    boolean bankCardItemValid,
    boolean bankApiDetected,
    boolean bankIntegrationEnabled,
    String economyMode,
    String detail
) {
}
