package com.chancemoreland.create_coinmarket.economy;

import com.chancemoreland.create_coinmarket.CreateCoinMarket;
import com.chancemoreland.create_coinmarket.data.AuctionConfig;
import net.neoforged.fml.ModList;

public final class EconomyManager {
    private static EconomyService service = new PhysicalCoinEconomyService();
    private static PhysicalCoinEconomyService physical = new PhysicalCoinEconomyService();
    private static NumismaticsDiagnostics lastBankDiagnostics;

    private EconomyManager() {
    }

    public static void init() {
        physical = new PhysicalCoinEconomyService();
        lastBankDiagnostics = null;
        if (!ModList.get().isLoaded("numismatics")) {
            String message = "Create: Numismatics is required for Create: CoinMarket's default economy but was not detected.";
            if (AuctionConfig.requireNumismatics()) {
                throw new IllegalStateException(message);
            }
            CreateCoinMarket.LOGGER.warn(message + " Physical coin economy will only work if configured coin items exist.");
            service = physical;
        } else if (AuctionConfig.enableNumismaticsBankIntegration()) {
            NumismaticsBankEconomyService bankService = new NumismaticsBankEconomyService(physical);
            lastBankDiagnostics = bankService.diagnostics();
            if (bankService.bankApiAvailable()) {
                service = bankService;
            } else if (AuctionConfig.fallbackToPhysicalCoins()) {
                service = physical;
                CreateCoinMarket.LOGGER.warn("Numismatics bank/card API could not be verified; using physical coin economy fallback.");
            } else {
                service = bankService;
                CreateCoinMarket.LOGGER.error("Numismatics bank/card API could not be verified and physical coin fallback is disabled.");
            }
        } else if (AuctionConfig.fallbackToPhysicalCoins()) {
            service = physical;
        } else {
            service = physical;
            CreateCoinMarket.LOGGER.warn("Numismatics bank integration is disabled and no non-physical economy is configured; using physical coins.");
        }
    }

    public static EconomyService service() {
        return service;
    }

    public static String modeName() {
        return service.modeName();
    }

    public static NumismaticsDiagnostics diagnostics() {
        if (service instanceof NumismaticsBankEconomyService bankService) {
            return bankService.diagnostics();
        }
        String version = ModList.get().getModContainerById("numismatics")
            .map(container -> container.getModInfo().getVersion().toString())
            .orElse("missing");
        if (lastBankDiagnostics != null) {
            return new NumismaticsDiagnostics(
                lastBankDiagnostics.modDetected(),
                lastBankDiagnostics.modVersion(),
                lastBankDiagnostics.coinItemsValid(),
                lastBankDiagnostics.bankCardItemValid(),
                lastBankDiagnostics.bankApiDetected(),
                lastBankDiagnostics.bankIntegrationEnabled(),
                modeName(),
                lastBankDiagnostics.detail()
            );
        }
        return new NumismaticsDiagnostics(
            ModList.get().isLoaded("numismatics"),
            version,
            PhysicalCoinEconomyService.configuredCurrencyWarnings().isEmpty(),
            false,
            false,
            AuctionConfig.enableNumismaticsBankIntegration(),
            modeName(),
            "Physical coin economy active."
        );
    }

    public static PhysicalCoinEconomyService physical() {
        return physical;
    }
}
