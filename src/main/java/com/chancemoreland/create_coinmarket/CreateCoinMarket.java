package com.chancemoreland.create_coinmarket;

import com.chancemoreland.create_coinmarket.command.AuctionCommand;
import com.chancemoreland.create_coinmarket.data.AuctionConfig;
import com.chancemoreland.create_coinmarket.data.AuctionDatabase;
import com.chancemoreland.create_coinmarket.data.AuctionServerConfig;
import com.chancemoreland.create_coinmarket.data.CreateCoinMarketClientConfig;
import com.chancemoreland.create_coinmarket.economy.EconomyAnalyticsService;
import com.chancemoreland.create_coinmarket.economy.EconomyManager;
import com.chancemoreland.create_coinmarket.economy.NumismaticsDiagnostics;
import com.chancemoreland.create_coinmarket.economy.PhysicalCoinEconomyService;
import com.chancemoreland.create_coinmarket.menu.AuctionMenus;
import com.chancemoreland.create_coinmarket.network.AuctionNetwork;
import com.mojang.logging.LogUtils;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

@Mod(CreateCoinMarket.MOD_ID)
public class CreateCoinMarket {
    public static final String MOD_ID = "create_coinmarket";
    public static final String DISPLAY_NAME = "Create: CoinMarket";
    public static final String VERSION = "1.2.0";
    public static final String NETWORK_PROTOCOL_VERSION = "1.2";
    public static final Logger LOGGER = LogUtils.getLogger();

    private long serverTicks;

    public CreateCoinMarket(IEventBus modEventBus, ModContainer modContainer) {
        migrateLegacyCommonConfig();
        modContainer.registerConfig(ModConfig.Type.COMMON, AuctionConfig.SPEC, "create_coinmarket-common.toml");
        modContainer.registerConfig(ModConfig.Type.SERVER, AuctionServerConfig.SPEC, "create_coinmarket-server.toml");
        modContainer.registerConfig(ModConfig.Type.CLIENT, CreateCoinMarketClientConfig.SPEC, "create_coinmarket-client.toml");
        AuctionMenus.register(modEventBus);
        modEventBus.addListener(AuctionNetwork::register);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            registerClientEvents(modEventBus);
        }
        NeoForge.EVENT_BUS.addListener(AuctionCommand::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(AuctionCommand::onRegisterPermissions);
        NeoForge.EVENT_BUS.addListener(this::onServerStarted);
        NeoForge.EVENT_BUS.addListener(this::onServerTick);
    }

    private void onServerStarted(ServerStartedEvent event) {
        try {
            validateRequiredMods();
            validateCurrencyConfig();
            EconomyManager.init();
            validateEconomyMode();
            AuctionDatabase.init(event.getServer());
            logStartup();
        } catch (RuntimeException | LinkageError ex) {
            throw startupFailure(ex);
        }
    }

    private void onServerTick(ServerTickEvent.Post event) {
        serverTicks++;
        if (serverTicks % 1200L == 0L) {
            AuctionDatabase.expireDueListings();
        }
        if (AuctionConfig.enableEconomyDashboard()) {
            EconomyAnalyticsService.onServerTick(serverTicks);
        }
    }

    private static void registerClientEvents(IEventBus modEventBus) {
        try {
            Class<?> clientEvents = Class.forName("com.chancemoreland.create_coinmarket.client.ClientEvents");
            Method method = clientEvents.getMethod("registerModBus", IEventBus.class);
            method.invoke(null, modEventBus);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Unable to register Create: CoinMarket client screens", ex);
        }
    }

    private static void validateRequiredMods() {
        requireLoadedMod("create", "Create");
        requireLoadedMod("numismatics", "Create: Numismatics");
    }

    private static void requireLoadedMod(String modId, String displayName) {
        if (!ModList.get().isLoaded(modId)) {
            throw new IllegalStateException(displayName + " (" + modId + ") is required but was not detected.");
        }
    }

    private static void validateCurrencyConfig() {
        if (AuctionConfig.currencyItems().isEmpty()) {
            throw new IllegalStateException("No configured Create: Numismatics currency items were parsed from currencyItems.");
        }
        List<String> invalidItems = PhysicalCoinEconomyService.configuredCurrencyWarnings();
        if (!invalidItems.isEmpty()) {
            throw new IllegalStateException("Unknown configured currency item IDs: " + String.join(", ", invalidItems));
        }
    }

    private static void validateEconomyMode() {
        NumismaticsDiagnostics diagnostics = EconomyManager.diagnostics();
        boolean bankRequested = AuctionConfig.enableNumismaticsBankIntegration();
        boolean bankUnavailable = bankRequested && !diagnostics.bankApiDetected();
        if (bankUnavailable && AuctionConfig.fallbackToPhysicalCoins()) {
            LOGGER.warn("Numismatics bank/card integration requested but unavailable: {}. Create: CoinMarket will use physical coins.", diagnostics.detail());
            if ("bank_only".equals(AuctionConfig.preferredPaymentSource())) {
                LOGGER.warn("preferredPaymentSource=bank_only cannot be honored while the bank/card API is unavailable; physical coins are active because fallbackToPhysicalCoins=true.");
            }
            return;
        }
        if (bankUnavailable) {
            throw new IllegalStateException("Numismatics bank/card API is unavailable and physical coin fallback is disabled: " + diagnostics.detail());
        }
        if (!AuctionConfig.fallbackToPhysicalCoins() && !diagnostics.bankApiDetected()) {
            throw new IllegalStateException("No usable economy service is available. Enable physical coin fallback or a verified Numismatics bank/card integration.");
        }
    }

    private static IllegalStateException startupFailure(Throwable cause) {
        String reason = cause.getMessage() == null || cause.getMessage().isBlank()
            ? cause.getClass().getSimpleName()
            : cause.getMessage();
        String prefix = DISPLAY_NAME + " failed startup validation: ";
        if (reason.startsWith(prefix)) {
            LOGGER.error(reason, cause);
            return cause instanceof IllegalStateException illegalState ? illegalState : new IllegalStateException(reason, cause);
        }
        String message = prefix + reason;
        LOGGER.error(message, cause);
        return new IllegalStateException(message, cause);
    }

    private static void logStartup() {
        String loadedVersion = ModList.get().getModContainerById(MOD_ID)
            .map(container -> container.getModInfo().getVersion().toString())
            .orElse(VERSION);
        String numismaticsVersion = ModList.get().getModContainerById("numismatics")
            .map(container -> container.getModInfo().getVersion().toString())
            .orElse("missing");
        LOGGER.info("{} version {}", DISPLAY_NAME, loadedVersion);
        LOGGER.info("{} network protocol {}", DISPLAY_NAME, NETWORK_PROTOCOL_VERSION);
        LOGGER.info("{} database path: {}", DISPLAY_NAME, AuctionDatabase.databasePath());
        LOGGER.info("{} database mode: {}, schema='{}', tablePrefix='{}', autoCreateTables={}, autoMigrate={}",
            DISPLAY_NAME, AuctionServerConfig.databaseMode(), AuctionServerConfig.schema(),
            AuctionServerConfig.tablePrefix(), AuctionServerConfig.autoCreateTables(), AuctionServerConfig.autoMigrate());
        LOGGER.info("{} config path: {}", DISPLAY_NAME, FMLPaths.CONFIGDIR.get().resolve("create_coinmarket-common.toml"));
        LOGGER.info("{} server config: world/serverconfig/create_coinmarket-server.toml", DISPLAY_NAME);
        LOGGER.info("Create: Numismatics detected: {} ({})", ModList.get().isLoaded("numismatics"), numismaticsVersion);
        LOGGER.info("{} economy mode: {}", DISPLAY_NAME, EconomyManager.modeName());
        LOGGER.info("{} bank integration enabled: {}", DISPLAY_NAME, AuctionConfig.enableNumismaticsBankIntegration());
        LOGGER.info("{} UI mode: {}", DISPLAY_NAME, AuctionConfig.enableLegacyChestUi() ? "legacy menu" : "modern Screen");
        LOGGER.info("{} legacy UI fallback enabled: {}", DISPLAY_NAME, AuctionConfig.enableLegacyChestUi());
    }

    private static void migrateLegacyCommonConfig() {
        Path configDir = FMLPaths.CONFIGDIR.get();
        Path oldConfig = configDir.resolve("auctionhousejs-common.toml");
        Path newConfig = configDir.resolve("create_coinmarket-common.toml");
        if (!Files.exists(oldConfig) || Files.exists(newConfig)) {
            return;
        }
        try {
            Files.copy(oldConfig, newConfig, StandardCopyOption.COPY_ATTRIBUTES);
            LOGGER.info("Migrated old AuctionHouseJS config to Create: CoinMarket config.");
        } catch (IOException ex) {
            LOGGER.warn("Unable to migrate old AuctionHouseJS config at {} to Create: CoinMarket config at {}", oldConfig, newConfig, ex);
        }
    }
}
