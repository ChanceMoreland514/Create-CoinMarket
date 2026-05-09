package com.chancemoreland.create_coinmarket.data;

import com.chancemoreland.create_coinmarket.CreateCoinMarket;
import com.chancemoreland.create_coinmarket.economy.EconomyBalance;
import com.chancemoreland.create_coinmarket.economy.EconomyManager;
import com.chancemoreland.create_coinmarket.economy.PhysicalCoinEconomyService;
import com.chancemoreland.create_coinmarket.util.ItemStackCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;

import org.jetbrains.annotations.Nullable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public final class AuctionDatabase {
    private static final Object LOCK = new Object();
    private static final long DAY_MILLIS = 24L * 60L * 60L * 1000L;
    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneOffset.UTC);
    private static final String NEW_DEFAULT_DATABASE_PATH = "{world}/serverconfig/create_coinmarket/coinmarket.db";
    private static final String OLD_DEFAULT_DATABASE_PATH = "{world}/serverconfig/auctionhousejs/auctionhouse.db";
    private static MinecraftServer server;
    private static HolderLookup.Provider lookupProvider;
    private static Path databasePath;

    private AuctionDatabase() {
    }

    public static void init(MinecraftServer minecraftServer) {
        synchronized (LOCK) {
            server = minecraftServer;
            lookupProvider = minecraftServer.registryAccess();
            databasePath = resolveDatabasePath(minecraftServer);
            try {
                if (!AuctionServerConfig.databaseEnabled()) {
                    throw new IllegalStateException("database.enabled=false but Create: CoinMarket requires a database.");
                }
                if (!"sqlite".equals(AuctionServerConfig.databaseMode())) {
                    validateExternalDatabaseMode();
                }
                validateDatabaseParent(databasePath.getParent());
                migrateLegacyDatabase(minecraftServer, databasePath);
                Class.forName("org.sqlite.JDBC");
                try (Connection connection = openConnection()) {
                    if (AuctionServerConfig.autoCreateTables() || AuctionServerConfig.autoMigrate()) {
                        migrate(connection);
                    }
                    expireOldListings(connection, now());
                }
                CreateCoinMarket.LOGGER.info("Create: CoinMarket SQLite database ready at {}", databasePath);
            } catch (Exception | LinkageError ex) {
                throw new IllegalStateException("SQLite/database validation failed at " + databasePath + ": " + ex.getMessage(), ex);
            }
        }
    }

    public static MinecraftServer server() {
        return server;
    }

    public static HolderLookup.Provider lookupProvider() {
        return lookupProvider;
    }

    public static Path databasePath() {
        return databasePath;
    }

    public static OperationResult addPublicListing(ServerPlayer seller, long price, int quantity) {
        long durationHours = Math.max(1L, AuctionConfig.publicDurationMillis() / (60L * 60L * 1000L));
        return addPublicListing(seller, price, quantity, durationHours);
    }

    public static OperationResult addPublicListing(ServerPlayer seller, long price, int quantity, long durationHours) {
        synchronized (LOCK) {
            if (price <= 0L) {
                return OperationResult.fail("Price must be at least 1 spur.");
            }
            ItemStack held = seller.getMainHandItem();
            if (held.isEmpty()) {
                return OperationResult.fail("Hold the item you want to sell in your main hand.");
            }
            if (quantity <= 0 || quantity > held.getCount()) {
                return OperationResult.fail("Quantity must be between 1 and " + held.getCount() + ".");
            }
            if (durationHours < 1L) {
                return OperationResult.fail("Duration must be at least 1 hour.");
            }
            if (countActivePublicListings(seller.getUUID()) >= AuctionConfig.maxListingsPerPlayer()) {
                return OperationResult.fail("You have reached the listing limit of " + AuctionConfig.maxListingsPerPlayer() + ".");
            }
            long fee = AuctionConfig.listingFee();
            if (fee > 0L && !EconomyManager.service().canPay(seller, fee)) {
                return OperationResult.fail("You need " + EconomyManager.service().format(fee) + " for the listing fee.");
            }

            ItemStack listed = held.copyWithCount(quantity);
            String encoded = ItemStackCodec.encodeToString(listed, lookupProvider);
            String itemId = BuiltInRegistries.ITEM.getKey(listed.getItem()).toString();
            long now = now();
            AuctionListing listing = new AuctionListing();
            listing.id = nextId();
            listing.category = AuctionListing.CATEGORY_PUBLIC;
            listing.sellerUuid = seller.getUUID();
            listing.sellerName = seller.getGameProfile().getName();
            listing.itemStack = encoded;
            listing.itemId = itemId;
            listing.itemCount = listed.getCount();
            listing.price = price;
            listing.listingType = AuctionListing.TYPE_FIXED_PRICE;
            listing.startPrice = price;
            listing.updatedAt = now;
            listing.createdAt = now;
            listing.expiresAt = now + durationHours * 60L * 60L * 1000L;
            listing.endsAt = listing.expiresAt;
            listing.status = AuctionListing.STATUS_ACTIVE;
            listing.source = "player_hand";
            listing.version = 1;

            ItemStack before = held.copy();
            if (fee > 0L && !EconomyManager.service().withdraw(seller, fee)) {
                return OperationResult.fail("Could not withdraw the listing fee.");
            }
            held.shrink(quantity);
            if (held.isEmpty()) {
                seller.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            }

            try (Connection connection = openConnection()) {
                connection.setAutoCommit(false);
                insertListing(connection, listing);
                insertAudit(connection, seller.getUUID(), seller.getGameProfile().getName(), "sell", listing.id,
                    "price=" + price + ", quantity=" + quantity + ", durationHours=" + durationHours + ", fee=" + fee);
                connection.commit();
                return OperationResult.success("Listed " + listed.getHoverName().getString() + " x" + quantity + " as " + listing.id + " for " + price + ".");
            } catch (SQLException ex) {
                seller.setItemInHand(InteractionHand.MAIN_HAND, before);
                if (fee > 0L) {
                    EconomyManager.service().depositOrCreateCollection(seller.getUUID(), seller.getGameProfile().getName(), fee);
                }
                CreateCoinMarket.LOGGER.error("Failed to add public listing", ex);
                return OperationResult.fail("Could not save the listing. Your item was returned.");
            }
        }
    }

    public static OperationResult addAdminListing(@Nullable ServerPlayer actor, String actorName, ItemStack stack, long price, String source) {
        synchronized (LOCK) {
            if (stack.isEmpty()) {
                return OperationResult.fail("Cannot list air.");
            }
            if (price <= 0L) {
                return OperationResult.fail("Price must be positive.");
            }
            long now = now();
            AuctionListing listing = new AuctionListing();
            listing.id = nextId();
            listing.category = AuctionListing.CATEGORY_ADMIN;
            listing.sellerUuid = actor == null ? null : actor.getUUID();
            listing.sellerName = actorName;
            listing.itemStack = ItemStackCodec.encodeToString(stack.copy(), lookupProvider);
            listing.itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            listing.itemCount = stack.getCount();
            listing.price = price;
            listing.listingType = AuctionListing.TYPE_FIXED_PRICE;
            listing.startPrice = price;
            listing.createdAt = now;
            listing.updatedAt = now;
            listing.startsAt = now;
            listing.infiniteAdminListing = AuctionConfig.adminListingsAreInfiniteByDefault();
            listing.expiresAt = AuctionConfig.adminListingsExpire() && !listing.infiniteAdminListing ? now + AuctionConfig.adminDurationMillis() : 0L;
            listing.endsAt = listing.expiresAt;
            listing.status = AuctionListing.STATUS_ACTIVE;
            listing.source = source;
            listing.version = 1;

            try (Connection connection = openConnection()) {
                connection.setAutoCommit(false);
                insertListing(connection, listing);
                insertAudit(connection, actor == null ? null : actor.getUUID(), actorName, "admin_add", listing.id,
                    "price=" + price + ", source=" + source);
                connection.commit();
                return OperationResult.success("Added admin listing " + listing.id + " for " + price + ".");
            } catch (SQLException ex) {
                CreateCoinMarket.LOGGER.error("Failed to add admin listing", ex);
                return OperationResult.fail("Could not save the admin listing.");
            }
        }
    }

    public static OperationResult addAuctionListing(ServerPlayer seller, long startBid, int quantity, String options) {
        synchronized (LOCK) {
            if (startBid <= 0L) {
                return OperationResult.fail("Starting bid must be at least 1.");
            }
            ItemStack held = seller.getMainHandItem();
            if (held.isEmpty()) {
                return OperationResult.fail("Hold the item you want to auction in your main hand.");
            }
            if (quantity <= 0 || quantity > held.getCount()) {
                return OperationResult.fail("Quantity must be between 1 and " + held.getCount() + ".");
            }
            AuctionOptions parsed = AuctionOptions.parse(options);
            if (parsed.durationHours() < 1L) {
                return OperationResult.fail("Duration must be at least 1 hour.");
            }
            if (parsed.minIncrement() < 1L) {
                return OperationResult.fail("Minimum increment must be at least 1.");
            }
            if (parsed.buyoutPrice() < 0L) {
                return OperationResult.fail("Buyout must be greater than starting bid, or 0 for no buyout.");
            }
            long durationHours = Math.max(AuctionConfig.minAuctionDurationHours(), Math.min(AuctionConfig.maxAuctionDurationHours(), parsed.durationHours()));
            long buyout = parsed.buyoutPrice();
            long minIncrement = parsed.minIncrement();
            if (buyout > 0L && buyout <= startBid) {
                return OperationResult.fail("Buyout must be greater than starting bid, or 0 for no buyout.");
            }
            if (countActivePublicListings(seller.getUUID()) >= AuctionConfig.maxListingsPerPlayer()) {
                return OperationResult.fail("You have reached the listing limit of " + AuctionConfig.maxListingsPerPlayer() + ".");
            }

            ItemStack listed = held.copyWithCount(quantity);
            String encoded = ItemStackCodec.encodeToString(listed, lookupProvider);
            String itemId = BuiltInRegistries.ITEM.getKey(listed.getItem()).toString();
            long now = now();
            AuctionListing listing = new AuctionListing();
            listing.id = nextId();
            listing.category = AuctionListing.CATEGORY_PUBLIC;
            listing.sellerUuid = seller.getUUID();
            listing.sellerName = seller.getGameProfile().getName();
            listing.itemStack = encoded;
            listing.itemId = itemId;
            listing.itemCount = listed.getCount();
            listing.price = startBid;
            listing.listingType = AuctionListing.TYPE_AUCTION;
            listing.startPrice = startBid;
            listing.buyoutPrice = buyout;
            listing.currentBid = 0L;
            listing.minIncrement = minIncrement;
            listing.createdAt = now;
            listing.updatedAt = now;
            listing.startsAt = now;
            listing.endsAt = now + durationHours * 60L * 60L * 1000L;
            listing.expiresAt = listing.endsAt;
            listing.status = AuctionListing.STATUS_ACTIVE;
            listing.source = "player_auction_hand";
            listing.version = 1;

            ItemStack before = held.copy();
            held.shrink(quantity);
            if (held.isEmpty()) {
                seller.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            }

            try (Connection connection = openConnection()) {
                connection.setAutoCommit(false);
                insertListing(connection, listing);
                insertAudit(connection, seller.getUUID(), seller.getGameProfile().getName(), "auction_create", listing.id,
                    "start=" + startBid + ", buyout=" + buyout + ", quantity=" + quantity + ", durationHours=" + durationHours);
                connection.commit();
                return OperationResult.success("Created auction " + listing.id + " starting at " + startBid + ".");
            } catch (SQLException ex) {
                seller.setItemInHand(InteractionHand.MAIN_HAND, before);
                CreateCoinMarket.LOGGER.error("Failed to add auction listing", ex);
                return OperationResult.fail("Could not save the auction. Your item was returned.");
            }
        }
    }

    public static OperationResult cancelOwnListing(ServerPlayer seller, String listingId) {
        synchronized (LOCK) {
            try (Connection connection = openConnection()) {
                connection.setAutoCommit(false);
                expireOldListings(connection, now());
                AuctionListing listing = selectListing(connection, listingId);
                if (listing == null || !listing.isPublic() || !AuctionListing.STATUS_ACTIVE.equals(listing.status)) {
                    connection.rollback();
                    return OperationResult.fail("Active public listing not found.");
                }
                if (!seller.getUUID().equals(listing.sellerUuid)) {
                    connection.rollback();
                    return OperationResult.fail("You can only cancel your own listings.");
                }
                returnListingAndRefund(connection, listing, AuctionListing.STATUS_CANCELED, "CANCELLED_BY_SELLER");
                insertAudit(connection, seller.getUUID(), seller.getGameProfile().getName(), "cancel", listing.id, "player_cancel");
                connection.commit();
                return OperationResult.success("Canceled listing " + listing.id + ". Claim the item in Collection.");
            } catch (SQLException ex) {
                CreateCoinMarket.LOGGER.error("Failed to cancel listing {}", listingId, ex);
                return OperationResult.fail("Could not cancel that listing.");
            }
        }
    }

    public static OperationResult buyListing(ServerPlayer buyer, String listingId) {
        synchronized (LOCK) {
            long withdrawnAmount = 0L;
            long payoutAmount = 0L;
            UUID payoutUuid = null;
            String payoutName = null;
            String payoutListingId = listingId;
            try (Connection connection = openConnection()) {
                connection.setAutoCommit(false);
                expireOldListings(connection, now());
                AuctionListing listing = selectListing(connection, listingId);
                long now = now();
                if (listing == null || !listing.isActive(now)) {
                    connection.rollback();
                    return OperationResult.fail("That listing is no longer available.");
                }
                if (listing.isAuction()) {
                    connection.rollback();
                    return OperationResult.fail("Use Bid or Buyout for auction listings.");
                }
                if (listing.isPublic() && !AuctionConfig.allowBuyingOwnListings() && buyer.getUUID().equals(listing.sellerUuid)) {
                    connection.rollback();
                    return OperationResult.fail("You cannot buy your own public listing.");
                }
                ItemStack item = decodeItem(listing);
                if (item.isEmpty()) {
                    connection.rollback();
                    return OperationResult.fail("The stored item could not be decoded.");
                }
                if (!PhysicalCoinEconomyService.canFit(buyer, List.of(item))) {
                    connection.rollback();
                    return OperationResult.fail("Make room in your inventory before buying.");
                }
                if (!EconomyManager.service().canPay(buyer, listing.price)) {
                    connection.rollback();
                    EconomyBalance balance = EconomyManager.service().balanceDetails(buyer);
                    String message = switch (AuctionConfig.preferredPaymentSource()) {
                        case "coins_only" -> "You do not have enough physical coins.";
                        case "bank_only" -> balance.hasBankCard()
                            ? "You do not have enough bank/card balance."
                            : "You need a bound Numismatics bank card to use bank-only payments.";
                        default -> "You do not have enough bank/card balance or physical coins.";
                    };
                    return OperationResult.fail(message);
                }
                if (!EconomyManager.service().withdraw(buyer, listing.price)) {
                    connection.rollback();
                    return OperationResult.fail("Could not withdraw currency.");
                }
                withdrawnAmount = listing.price;

                long tax = listing.isPublic() ? Math.round(listing.price * (AuctionConfig.publicTaxPercent() / 100.0D)) : 0L;
                tax = Math.max(0L, Math.min(listing.price, tax));
                long payout = listing.isPublic() ? listing.price - tax : 0L;
                payoutAmount = payout;
                payoutUuid = listing.sellerUuid;
                payoutName = listing.sellerName;
                payoutListingId = listing.id;
                updateListingStatus(connection, listing.id, AuctionListing.STATUS_SOLD, buyer.getUUID(), buyer.getGameProfile().getName(), now);
                insertTransaction(connection, listing, buyer, tax, now, item);
                insertAudit(connection, buyer.getUUID(), buyer.getGameProfile().getName(), "buy", listing.id,
                    "price=" + listing.price + ", tax=" + tax);
                connection.commit();

                boolean delivered = PhysicalCoinEconomyService.addStacks(buyer, List.of(item));
                if (payoutAmount > 0L && payoutUuid != null
                    && !createPayoutOrCredit(payoutUuid, payoutName, payoutListingId, payoutAmount, "sale_proceeds")) {
                    CreateCoinMarket.LOGGER.error("Failed to create seller payout for listing {} to {}", payoutListingId, payoutUuid);
                }
                if (!delivered) {
                    createItemCollection(buyer.getUUID(), buyer.getGameProfile().getName(), listing.itemStack, "purchase_delivery");
                    return OperationResult.success("Purchased " + listing.id + ". Delivery is waiting in /auction collect.");
                }
                return OperationResult.success("Purchased " + listing.id + " for " + EconomyManager.service().format(listing.price) + ".");
            } catch (SQLException ex) {
                CreateCoinMarket.LOGGER.error("Failed to buy listing {}", listingId, ex);
                if (withdrawnAmount > 0L) {
                    EconomyManager.service().depositOrCreateCollection(buyer.getUUID(), buyer.getGameProfile().getName(), withdrawnAmount);
                }
                return OperationResult.fail("Could not complete the purchase.");
            }
        }
    }

    public static OperationResult placeBid(ServerPlayer bidder, String listingId, long amount) {
        synchronized (LOCK) {
            long withdrawn = 0L;
            try (Connection connection = openConnection()) {
                connection.setAutoCommit(false);
                expireOldListings(connection, now());
                AuctionListing listing = selectListing(connection, listingId);
                long now = now();
                if (listing == null || !listing.isActive(now) || !listing.isAuction()) {
                    connection.rollback();
                    return OperationResult.fail("Active auction not found.");
                }
                if (!AuctionConfig.allowBuyingOwnListings() && bidder.getUUID().equals(listing.sellerUuid)) {
                    connection.rollback();
                    return OperationResult.fail("You cannot bid on your own auction.");
                }
                long minimum = listing.currentBid > 0L ? listing.currentBid + Math.max(1L, listing.minIncrement) : listing.startPrice;
                if (amount < minimum) {
                    connection.rollback();
                    return OperationResult.fail("Bid must be at least " + EconomyManager.service().format(minimum) + ".");
                }
                if (listing.buyoutPrice > 0L && amount >= listing.buyoutPrice) {
                    connection.rollback();
                    return buyoutAuction(bidder, listingId);
                }
                if (!EconomyManager.service().canPay(bidder, amount)) {
                    connection.rollback();
                    return OperationResult.fail("You do not have enough spendable balance for that bid.");
                }
                if (!EconomyManager.service().withdraw(bidder, amount)) {
                    connection.rollback();
                    return OperationResult.fail("Could not reserve your bid funds.");
                }
                withdrawn = amount;
                if (listing.highestBidderUuid != null && listing.currentBid > 0L) {
                    insertPayout(connection, listing.highestBidderUuid, listing.highestBidderName, listing.id, listing.currentBid, "refund", "outbid", now);
                }
                insertBid(connection, listing.id, bidder.getUUID(), bidder.getGameProfile().getName(), amount, now);
                updateAuctionBid(connection, listing.id, amount, bidder.getUUID(), bidder.getGameProfile().getName(), now);
                insertAudit(connection, bidder.getUUID(), bidder.getGameProfile().getName(), "bid", listing.id, "amount=" + amount);
                connection.commit();
                return OperationResult.success("Bid placed on " + listing.id + " for " + EconomyManager.service().format(amount) + ".");
            } catch (SQLException ex) {
                CreateCoinMarket.LOGGER.error("Failed to place bid on {}", listingId, ex);
                if (withdrawn > 0L) {
                    createPayoutOrCredit(bidder.getUUID(), bidder.getGameProfile().getName(), listingId, withdrawn, "bid_recovery");
                }
                return OperationResult.fail("Could not place that bid. Your funds were moved to pending proceeds if needed.");
            }
        }
    }

    public static OperationResult buyoutAuction(ServerPlayer buyer, String listingId) {
        synchronized (LOCK) {
            long withdrawn = 0L;
            try (Connection connection = openConnection()) {
                connection.setAutoCommit(false);
                expireOldListings(connection, now());
                AuctionListing listing = selectListing(connection, listingId);
                long now = now();
                if (listing == null || !listing.isActive(now) || !listing.isAuction()) {
                    connection.rollback();
                    return OperationResult.fail("Active auction not found.");
                }
                if (listing.buyoutPrice <= 0L) {
                    connection.rollback();
                    return OperationResult.fail("This auction does not have a buyout price.");
                }
                if (!AuctionConfig.allowBuyingOwnListings() && buyer.getUUID().equals(listing.sellerUuid)) {
                    connection.rollback();
                    return OperationResult.fail("You cannot buy out your own auction.");
                }
                if (!EconomyManager.service().canPay(buyer, listing.buyoutPrice)) {
                    connection.rollback();
                    return OperationResult.fail("You do not have enough spendable balance for the buyout.");
                }
                if (!EconomyManager.service().withdraw(buyer, listing.buyoutPrice)) {
                    connection.rollback();
                    return OperationResult.fail("Could not reserve buyout funds.");
                }
                withdrawn = listing.buyoutPrice;
                if (listing.highestBidderUuid != null && listing.currentBid > 0L) {
                    insertPayout(connection, listing.highestBidderUuid, listing.highestBidderName, listing.id, listing.currentBid, "refund", "buyout_outbid", now);
                }
                listing.currentBid = listing.buyoutPrice;
                listing.highestBidderUuid = buyer.getUUID();
                listing.highestBidderName = buyer.getGameProfile().getName();
                resolveAuctionAsSold(connection, listing, buyer.getUUID(), buyer.getGameProfile().getName(), now, "buyout");
                insertBid(connection, listing.id, buyer.getUUID(), buyer.getGameProfile().getName(), listing.buyoutPrice, now);
                insertAudit(connection, buyer.getUUID(), buyer.getGameProfile().getName(), "buyout", listing.id, "amount=" + listing.buyoutPrice);
                connection.commit();
                return OperationResult.success("Bought out auction " + listing.id + " for " + EconomyManager.service().format(listing.buyoutPrice) + ". Claim the item in Collection.");
            } catch (SQLException ex) {
                CreateCoinMarket.LOGGER.error("Failed to buy out auction {}", listingId, ex);
                if (withdrawn > 0L) {
                    createPayoutOrCredit(buyer.getUUID(), buyer.getGameProfile().getName(), listingId, withdrawn, "buyout_recovery");
                }
                return OperationResult.fail("Could not complete the buyout. Your funds were moved to pending proceeds if needed.");
            }
        }
    }

    public static OperationResult collect(ServerPlayer player) {
        synchronized (LOCK) {
            try (Connection connection = openConnection()) {
                connection.setAutoCommit(false);
                expireOldListings(connection, now());
                List<CollectionEntry> entries = selectUnclaimedCollections(connection, player.getUUID());
                if (entries.isEmpty()) {
                    connection.rollback();
                    return OperationResult.fail("You have nothing to collect.");
                }
                List<ItemStack> additions = new ArrayList<>();
                long coins = 0L;
                int items = 0;
                for (CollectionEntry entry : entries) {
                    if ("coins".equals(entry.type)) {
                        Optional<List<ItemStack>> coinStacks = EconomyManager.service().makeCoins(entry.amount);
                        if (coinStacks.isEmpty()) {
                            connection.rollback();
                            return OperationResult.fail("Configured currency cannot create a payout for " + entry.amount + ".");
                        }
                        additions.addAll(coinStacks.get());
                        coins += entry.amount;
                    } else if ("item".equals(entry.type)) {
                        ItemStack item = ItemStackCodec.decodeFromString(entry.itemStack, lookupProvider);
                        if (item.isEmpty()) {
                            connection.rollback();
                            return OperationResult.fail("A collection item could not be decoded. Ask an admin to inspect " + entry.id + ".");
                        }
                        additions.add(item);
                        items++;
                    }
                }
                if (!PhysicalCoinEconomyService.canFit(player, additions)) {
                    connection.rollback();
                    return OperationResult.fail("Make room in your inventory before collecting.");
                }
                for (CollectionEntry entry : entries) {
                    markCollectionClaimed(connection, entry.id, now());
                }
                connection.commit();
                PhysicalCoinEconomyService.addStacks(player, additions);
                return OperationResult.success("Collected " + items + " item stack(s) and " + EconomyManager.service().format(coins) + ".");
            } catch (SQLException ex) {
                CreateCoinMarket.LOGGER.error("Failed to collect for {}", player.getGameProfile().getName(), ex);
                return OperationResult.fail("Could not collect right now.");
            }
        }
    }

    public static OperationResult collectMoney(ServerPlayer player) {
        synchronized (LOCK) {
            try (Connection connection = openConnection()) {
                connection.setAutoCommit(false);
                List<CollectionEntry> payouts = selectPendingPayouts(connection, player.getUUID());
                if (payouts.isEmpty()) {
                    connection.rollback();
                    return OperationResult.fail("No pending proceeds to collect.");
                }
                long amount = payouts.stream().mapToLong(entry -> entry.amount).sum();
                Optional<List<ItemStack>> coins = EconomyManager.service().makeCoins(amount);
                if (coins.isEmpty()) {
                    connection.rollback();
                    return OperationResult.fail("Configured currency cannot create a payout for " + amount + ".");
                }
                if (!PhysicalCoinEconomyService.canFit(player, coins.get())) {
                    connection.rollback();
                    return OperationResult.fail("Make room in your inventory before collecting proceeds.");
                }
                long now = now();
                for (CollectionEntry payout : payouts) {
                    markPayoutClaimed(connection, payout.id, now);
                }
                insertAudit(connection, player.getUUID(), player.getGameProfile().getName(), "collect_money", null, "amount=" + amount + ", entries=" + payouts.size());
                connection.commit();
                PhysicalCoinEconomyService.addStacks(player, coins.get());
                return OperationResult.success("Collected pending proceeds: " + EconomyManager.service().format(amount) + ".");
            } catch (SQLException ex) {
                CreateCoinMarket.LOGGER.error("Failed to collect proceeds for {}", player.getGameProfile().getName(), ex);
                return OperationResult.fail("Could not collect proceeds right now.");
            }
        }
    }

    public static OperationResult collectOne(ServerPlayer player, String collectionId) {
        synchronized (LOCK) {
            try (Connection connection = openConnection()) {
                connection.setAutoCommit(false);
                if (collectionId != null && collectionId.startsWith("PO-")) {
                    CollectionEntry payout = selectPendingPayout(connection, player.getUUID(), collectionId);
                    if (payout == null) {
                        connection.rollback();
                        return OperationResult.fail("That payout is not claimable.");
                    }
                    Optional<List<ItemStack>> coins = EconomyManager.service().makeCoins(payout.amount);
                    if (coins.isEmpty()) {
                        connection.rollback();
                        return OperationResult.fail("Configured currency cannot create a payout for " + payout.amount + ".");
                    }
                    if (!PhysicalCoinEconomyService.canFit(player, coins.get())) {
                        connection.rollback();
                        return OperationResult.fail("Make room in your inventory before collecting proceeds.");
                    }
                    markPayoutClaimed(connection, payout.id, now());
                    connection.commit();
                    PhysicalCoinEconomyService.addStacks(player, coins.get());
                    return OperationResult.success("Collected " + EconomyManager.service().format(payout.amount) + ".");
                }
                expireOldListings(connection, now());
                CollectionEntry entry = selectUnclaimedCollection(connection, player.getUUID(), collectionId);
                if (entry == null) {
                    connection.rollback();
                    return OperationResult.fail("That collection entry is not claimable.");
                }
                List<ItemStack> additions = new ArrayList<>();
                long coins = 0L;
                int items = 0;
                if ("coins".equals(entry.type)) {
                    Optional<List<ItemStack>> coinStacks = EconomyManager.service().makeCoins(entry.amount);
                    if (coinStacks.isEmpty()) {
                        connection.rollback();
                        return OperationResult.fail("Configured currency cannot create a payout for " + entry.amount + ".");
                    }
                    additions.addAll(coinStacks.get());
                    coins += entry.amount;
                } else if ("item".equals(entry.type)) {
                    ItemStack item = ItemStackCodec.decodeFromString(entry.itemStack, lookupProvider);
                    if (item.isEmpty()) {
                        connection.rollback();
                        return OperationResult.fail("That collection item could not be decoded.");
                    }
                    additions.add(item);
                    items++;
                }
                if (!PhysicalCoinEconomyService.canFit(player, additions)) {
                    connection.rollback();
                    return OperationResult.fail("Make room in your inventory before collecting.");
                }
                markCollectionClaimed(connection, entry.id, now());
                connection.commit();
                PhysicalCoinEconomyService.addStacks(player, additions);
                return OperationResult.success("Collected " + items + " item stack(s) and " + EconomyManager.service().format(coins) + ".");
            } catch (SQLException ex) {
                CreateCoinMarket.LOGGER.error("Failed to collect entry {} for {}", collectionId, player.getGameProfile().getName(), ex);
                return OperationResult.fail("Could not collect that entry right now.");
            }
        }
    }

    public static List<AuctionListing> activeListings(String mode, UUID viewer) {
        synchronized (LOCK) {
            try (Connection connection = openConnection()) {
                expireOldListings(connection, now());
                String normalized = AuctionConfig.normalizeMode(mode);
                StringBuilder sql = new StringBuilder("SELECT * FROM listings WHERE status='active' AND ((ends_at=0 AND (expires_at=0 OR expires_at>?)) OR ends_at>?)");
                List<Object> params = new ArrayList<>();
                params.add(now());
                params.add(now());
                if ("admin".equals(normalized) || "public".equals(normalized)) {
                    sql.append(" AND category=?");
                    params.add(normalized);
                } else if ("my".equals(normalized)) {
                    sql.append(" AND seller_uuid=?");
                    params.add(viewer.toString());
                }
                sql.append(" ORDER BY created_at DESC LIMIT 500");
                try (PreparedStatement statement = connection.prepareStatement(sql.toString())) {
                    bind(statement, params);
                    try (ResultSet rs = statement.executeQuery()) {
                        List<AuctionListing> result = new ArrayList<>();
                        while (rs.next()) {
                            result.add(readListing(rs));
                        }
                        return result;
                    }
                }
            } catch (SQLException ex) {
                CreateCoinMarket.LOGGER.error("Failed to query active listings", ex);
                return List.of();
            }
        }
    }

    public static List<CollectionEntry> collections(UUID playerUuid) {
        synchronized (LOCK) {
            try (Connection connection = openConnection()) {
                return selectUnclaimedCollections(connection, playerUuid);
            } catch (SQLException ex) {
                return List.of();
            }
        }
    }

    public static AuctionListing listing(String listingId) {
        synchronized (LOCK) {
            try (Connection connection = openConnection()) {
                return selectListing(connection, listingId);
            } catch (SQLException ex) {
                return null;
            }
        }
    }

    public static List<AuctionListing> allActiveListings() {
        synchronized (LOCK) {
            return activeListings("all", new UUID(0L, 0L));
        }
    }

    public static MarketScreenData screenData(ServerPlayer player, String mode, int page, String sort, String query) {
        synchronized (LOCK) {
            try (Connection connection = openConnection()) {
                long now = now();
                expireOldListings(connection, now);
                int pageSize = AuctionConfig.defaultMarketPageSize();
                String normalizedMode = normalizeScreenMode(mode);
                String normalizedSort = normalizeSort(sort);
                String normalizedQuery = query == null ? "" : query.trim();
                int safePage = Math.max(0, page);
                long total = countVisibleListings(connection, normalizedMode, player.getUUID(), normalizedQuery, now);
                int maxPage = Math.max(0, (int) ((total - 1L) / pageSize));
                safePage = Math.min(safePage, maxPage);

                List<AuctionListing> listings = selectVisibleListings(connection, normalizedMode, player.getUUID(), normalizedSort, normalizedQuery, now, pageSize, safePage * pageSize);
                List<MarketScreenData.ListingView> listingViews = new ArrayList<>();
                for (AuctionListing listing : listings) {
                    listingViews.add(toListingView(listing));
                }

                List<CollectionEntry> collectionEntries = selectUnclaimedCollections(connection, player.getUUID());
                List<CollectionEntry> payoutEntries = selectPendingPayouts(connection, player.getUUID());
                List<MarketScreenData.CollectionView> collectionViews = new ArrayList<>();
                for (CollectionEntry entry : collectionEntries) {
                    collectionViews.add(toCollectionView(entry));
                }
                for (CollectionEntry entry : payoutEntries) {
                    collectionViews.add(toCollectionView(entry));
                }
                long pendingProceeds = payoutEntries.stream().mapToLong(entry -> entry.amount).sum();
                EconomyBalance balance = EconomyManager.service().balanceDetails(player);

                return new MarketScreenData(
                    normalizedMode,
                    safePage,
                    maxPage,
                    (int) Math.min(Integer.MAX_VALUE, total),
                    normalizedSort,
                    normalizedQuery,
                    balance.totalSpendableBalance(),
                    balance.coinBalance(),
                    balance.bankBalance(),
                    balance.totalSpendableBalance(),
                    balance.activePaymentSource(),
                    balance.hasBankCard(),
                    balance.bankAvailable(),
                    balance.warning(),
                    collectionEntries.size(),
                    pendingProceeds,
                    payoutEntries.size(),
                    player.hasPermissions(2),
                    dashboardSummary(connection, now),
                    listingViews,
                    collectionViews,
                    priceCheckForHand(player),
                    chartPoints(connection, "SUM(price)", now),
                    chartPoints(connection, "AVG(price)", now),
                    flowBreakdown(connection),
                    categoryBreakdown(connection, now),
                    topItems(connection),
                    topPlayers(connection, "seller_name", "seller_uuid", "category='public' AND seller_uuid IS NOT NULL"),
                    topPlayers(connection, "buyer_name", "buyer_uuid", "buyer_uuid IS NOT NULL")
                );
            } catch (SQLException ex) {
                CreateCoinMarket.LOGGER.error("Failed to build market screen data", ex);
                return MarketScreenData.empty(mode, page);
            }
        }
    }

    public static OperationResult backupDatabase(@Nullable ServerPlayer actor, String actorName) {
        synchronized (LOCK) {
            try {
                Files.createDirectories(databasePath.getParent().resolve("backups"));
                try (Connection connection = openConnection();
                     Statement statement = connection.createStatement()) {
                    statement.execute("PRAGMA wal_checkpoint(FULL)");
                    insertAudit(connection, actor == null ? null : actor.getUUID(), actorName, "admin_backup", null, databasePath.toString());
                }
                String stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC).format(Instant.ofEpochMilli(now()));
                Path backup = databasePath.getParent().resolve("backups").resolve("coinmarket-" + stamp + ".db");
                Files.copy(databasePath, backup, StandardCopyOption.REPLACE_EXISTING);
                return OperationResult.success("Backed up database to " + backup.getFileName() + ".");
            } catch (Exception ex) {
                CreateCoinMarket.LOGGER.error("Failed to back up database", ex);
                return OperationResult.fail("Could not back up the database.");
            }
        }
    }

    public static OperationResult adminRemove(@Nullable ServerPlayer actor, String actorName, String listingId) {
        synchronized (LOCK) {
            try (Connection connection = openConnection()) {
                connection.setAutoCommit(false);
                AuctionListing listing = selectListing(connection, listingId);
                if (listing == null) {
                    connection.rollback();
                    return OperationResult.fail("Listing not found.");
                }
                if (AuctionListing.STATUS_ACTIVE.equals(listing.status) && listing.sellerUuid != null) {
                    returnListingAndRefund(connection, listing, AuctionListing.STATUS_CANCELED, "ADMIN_REMOVED");
                } else {
                    updateAnyListingStatus(connection, listing.id, AuctionListing.STATUS_REMOVED, null, null, 0L);
                }
                insertAudit(connection, actor == null ? null : actor.getUUID(), actorName, "admin_remove", listing.id, "");
                connection.commit();
                return OperationResult.success("Removed listing " + listingId + ".");
            } catch (SQLException ex) {
                CreateCoinMarket.LOGGER.error("Failed to remove listing {}", listingId, ex);
                return OperationResult.fail("Could not remove listing.");
            }
        }
    }

    public static OperationResult adminClear(@Nullable ServerPlayer actor, String actorName, String target) {
        synchronized (LOCK) {
            String normalized = target.toLowerCase(Locale.ROOT);
            int count = 0;
            try (Connection connection = openConnection()) {
                connection.setAutoCommit(false);
                List<AuctionListing> active = activeListingsForAdmin(connection, normalized);
                for (AuctionListing listing : active) {
                    if (listing.sellerUuid != null) {
                        returnListingAndRefund(connection, listing, AuctionListing.STATUS_CANCELED, "ADMIN_REMOVED");
                    } else {
                        updateListingStatus(connection, listing.id, AuctionListing.STATUS_REMOVED, null, null, 0L);
                    }
                    count++;
                }
                insertAudit(connection, actor == null ? null : actor.getUUID(), actorName, "admin_clear", null, "target=" + target + ", count=" + count);
                connection.commit();
                return OperationResult.success("Cleared " + count + " listing(s).");
            } catch (SQLException ex) {
                CreateCoinMarket.LOGGER.error("Failed to clear listings", ex);
                return OperationResult.fail("Could not clear listings.");
            }
        }
    }

    public static OperationResult adminExpire(@Nullable ServerPlayer actor, String actorName, String listingId) {
        synchronized (LOCK) {
            try (Connection connection = openConnection()) {
                connection.setAutoCommit(false);
                AuctionListing listing = selectListing(connection, listingId);
                if (listing == null || !AuctionListing.STATUS_ACTIVE.equals(listing.status)) {
                    connection.rollback();
                    return OperationResult.fail("Active listing not found.");
                }
                returnListingAndRefund(connection, listing, AuctionListing.STATUS_EXPIRED, "INVALIDATED");
                insertAudit(connection, actor == null ? null : actor.getUUID(), actorName, "admin_expire", listing.id, "");
                connection.commit();
                return OperationResult.success("Expired listing " + listingId + ".");
            } catch (SQLException ex) {
                CreateCoinMarket.LOGGER.error("Failed to expire listing {}", listingId, ex);
                return OperationResult.fail("Could not expire listing.");
            }
        }
    }

    public static OperationResult endAuction(@Nullable ServerPlayer actor, String actorName, String listingId) {
        synchronized (LOCK) {
            try (Connection connection = openConnection()) {
                connection.setAutoCommit(false);
                AuctionListing listing = selectListing(connection, listingId);
                if (listing == null || !AuctionListing.STATUS_ACTIVE.equals(listing.status) || !listing.isAuction()) {
                    connection.rollback();
                    return OperationResult.fail("Active auction not found.");
                }
                long now = now();
                if (listing.highestBidderUuid != null && listing.currentBid > 0L) {
                    resolveAuctionAsSold(connection, listing, listing.highestBidderUuid, listing.highestBidderName, now, "admin_endauction");
                } else {
                    returnListingAndRefund(connection, listing, AuctionListing.STATUS_EXPIRED, "NO_BIDS_EXPIRED");
                }
                insertAudit(connection, actor == null ? null : actor.getUUID(), actorName, "admin_endauction", listing.id, "");
                connection.commit();
                return OperationResult.success("Ended auction " + listingId + ".");
            } catch (SQLException ex) {
                CreateCoinMarket.LOGGER.error("Failed to end auction {}", listingId, ex);
                return OperationResult.fail("Could not end auction.");
            }
        }
    }

    public static OperationResult refundListing(@Nullable ServerPlayer actor, String actorName, String listingId) {
        synchronized (LOCK) {
            try (Connection connection = openConnection()) {
                connection.setAutoCommit(false);
                AuctionListing listing = selectListing(connection, listingId);
                if (listing == null) {
                    connection.rollback();
                    return OperationResult.fail("Listing not found.");
                }
                returnListingAndRefund(connection, listing, AuctionListing.STATUS_CANCELED, "ADMIN_REFUND");
                insertAudit(connection, actor == null ? null : actor.getUUID(), actorName, "admin_refundlisting", listing.id, "");
                connection.commit();
                return OperationResult.success("Refunded and returned listing " + listingId + ".");
            } catch (SQLException ex) {
                CreateCoinMarket.LOGGER.error("Failed to refund listing {}", listingId, ex);
                return OperationResult.fail("Could not refund listing.");
            }
        }
    }

    public static OperationResult returnListing(@Nullable ServerPlayer actor, String actorName, String listingId) {
        synchronized (LOCK) {
            try (Connection connection = openConnection()) {
                connection.setAutoCommit(false);
                AuctionListing listing = selectListing(connection, listingId);
                if (listing == null) {
                    connection.rollback();
                    return OperationResult.fail("Listing not found.");
                }
                returnListingAndRefund(connection, listing, AuctionListing.STATUS_CANCELED, "ADMIN_RETURNED");
                insertAudit(connection, actor == null ? null : actor.getUUID(), actorName, "admin_returnlisting", listing.id, "");
                connection.commit();
                return OperationResult.success("Returned listing " + listingId + " to seller collection.");
            } catch (SQLException ex) {
                CreateCoinMarket.LOGGER.error("Failed to return listing {}", listingId, ex);
                return OperationResult.fail("Could not return listing.");
            }
        }
    }

    public static List<String> adminClaimSummaries(String target) {
        synchronized (LOCK) {
            String like = "%" + (target == null ? "" : target.toLowerCase(Locale.ROOT)) + "%";
            try (Connection connection = openConnection()) {
                List<String> result = new ArrayList<>();
                try (PreparedStatement statement = connection.prepareStatement("SELECT id, player_name, type, amount, reason FROM collections WHERE claimed=0 AND (LOWER(player_name) LIKE ? OR player_uuid LIKE ?) ORDER BY created_at DESC LIMIT 20")) {
                    statement.setString(1, like);
                    statement.setString(2, like);
                    try (ResultSet rs = statement.executeQuery()) {
                        while (rs.next()) {
                            result.add(rs.getString("id") + " | " + rs.getString("player_name") + " | " + rs.getString("type") + " | " + rs.getLong("amount") + " | " + rs.getString("reason"));
                        }
                    }
                }
                try (PreparedStatement statement = connection.prepareStatement("SELECT id, player_name, amount, reason FROM payouts WHERE status='pending' AND (LOWER(player_name) LIKE ? OR player_uuid LIKE ?) ORDER BY created_at DESC LIMIT 20")) {
                    statement.setString(1, like);
                    statement.setString(2, like);
                    try (ResultSet rs = statement.executeQuery()) {
                        while (rs.next()) {
                            result.add(rs.getString("id") + " | " + rs.getString("player_name") + " | proceeds | " + rs.getLong("amount") + " | " + rs.getString("reason"));
                        }
                    }
                }
                return result;
            } catch (SQLException ex) {
                CreateCoinMarket.LOGGER.error("Failed to view claims for {}", target, ex);
                return List.of("Could not read claims.");
            }
        }
    }

    public static OperationResult adminForceClaim(@Nullable ServerPlayer actor, String actorName, String target, String claimId) {
        synchronized (LOCK) {
            try (Connection connection = openConnection()) {
                connection.setAutoCommit(false);
                int updated;
                long timestamp = now();
                if (claimId.startsWith("PO-")) {
                    try (PreparedStatement statement = connection.prepareStatement("UPDATE payouts SET status='claimed', claimed_at=? WHERE id=? AND status='pending' AND (LOWER(player_name) LIKE ? OR player_uuid LIKE ?)")) {
                        statement.setLong(1, timestamp);
                        statement.setString(2, claimId);
                        statement.setString(3, "%" + target.toLowerCase(Locale.ROOT) + "%");
                        statement.setString(4, "%" + target.toLowerCase(Locale.ROOT) + "%");
                        updated = statement.executeUpdate();
                    }
                } else {
                    try (PreparedStatement statement = connection.prepareStatement("UPDATE collections SET claimed=1, claimed_at=? WHERE id=? AND claimed=0 AND (LOWER(player_name) LIKE ? OR player_uuid LIKE ?)")) {
                        statement.setLong(1, timestamp);
                        statement.setString(2, claimId);
                        statement.setString(3, "%" + target.toLowerCase(Locale.ROOT) + "%");
                        statement.setString(4, "%" + target.toLowerCase(Locale.ROOT) + "%");
                        updated = statement.executeUpdate();
                    }
                }
                if (updated != 1) {
                    connection.rollback();
                    return OperationResult.fail("Claim not found or already claimed.");
                }
                insertAudit(connection, actor == null ? null : actor.getUUID(), actorName, "admin_forceclaim", null, "target=" + target + ", claim=" + claimId);
                connection.commit();
                return OperationResult.success("Force-claimed " + claimId + " for " + target + ".");
            } catch (SQLException ex) {
                CreateCoinMarket.LOGGER.error("Failed to force claim {}", claimId, ex);
                return OperationResult.fail("Could not force-claim that entry.");
            }
        }
    }

    public static OperationResult repair(@Nullable ServerPlayer actor, String actorName) {
        synchronized (LOCK) {
            try (Connection connection = openConnection()) {
                connection.setAutoCommit(false);
                int expired = expireOldListings(connection, now());
                insertAudit(connection, actor == null ? null : actor.getUUID(), actorName, "admin_repair", null, "expired=" + expired);
                connection.commit();
                return OperationResult.success("Repair complete. Expired " + expired + " stale listing(s).");
            } catch (SQLException ex) {
                CreateCoinMarket.LOGGER.error("Repair failed", ex);
                return OperationResult.fail("Repair failed.");
            }
        }
    }

    public static void expireDueListings() {
        synchronized (LOCK) {
            try (Connection connection = openConnection()) {
                connection.setAutoCommit(false);
                expireOldListings(connection, now());
                connection.commit();
            } catch (SQLException ex) {
                CreateCoinMarket.LOGGER.error("Failed to expire due auction listings", ex);
            }
        }
    }

    public static MarketStats marketStats(String itemId) {
        synchronized (LOCK) {
            long now = now();
            long day = 24L * 60L * 60L * 1000L;
            try (Connection connection = openConnection()) {
                long last = scalarLong(connection, "SELECT price FROM transactions WHERE item_id=? ORDER BY timestamp DESC LIMIT 1", itemId);
                double avg24 = scalarDouble(connection, "SELECT AVG(price) FROM transactions WHERE item_id=? AND timestamp>=?", itemId, now - day);
                double avg7 = scalarDouble(connection, "SELECT AVG(price) FROM transactions WHERE item_id=? AND timestamp>=?", itemId, now - 7L * day);
                double avg30 = scalarDouble(connection, "SELECT AVG(price) FROM transactions WHERE item_id=? AND timestamp>=?", itemId, now - (long) AuctionConfig.marketHistoryDays() * day);
                long lowest = scalarLong(connection, "SELECT MIN(price) FROM listings WHERE item_id=? AND status='active' AND (expires_at=0 OR expires_at>?)", itemId, now);
                int active = (int) scalarLong(connection, "SELECT COUNT(*) FROM listings WHERE item_id=? AND status='active' AND (expires_at=0 OR expires_at>?)", itemId, now);
                int sold24 = (int) scalarLong(connection, "SELECT COUNT(*) FROM transactions WHERE item_id=? AND timestamp>=?", itemId, now - day);
                int sold7 = (int) scalarLong(connection, "SELECT COUNT(*) FROM transactions WHERE item_id=? AND timestamp>=?", itemId, now - 7L * day);
                String trend = trend(avg24, avg7);
                return new MarketStats(last, avg24, avg7, avg30, lowest, active, sold24, sold7, trend);
            } catch (SQLException ex) {
                return MarketStats.empty();
            }
        }
    }

    public static ItemStack decodeItem(AuctionListing listing) {
        return ItemStackCodec.decodeFromString(listing.itemStack, lookupProvider);
    }

    public static String timeRemaining(AuctionListing listing) {
        if (!listing.expires()) {
            return "Never";
        }
        long millis = Math.max(0L, listing.effectiveEndsAt() - now());
        long hours = millis / (60L * 60L * 1000L);
        long minutes = (millis / (60L * 1000L)) % 60L;
        if (hours >= 24L) {
            return (hours / 24L) + "d " + (hours % 24L) + "h";
        }
        return hours > 0L ? hours + "h " + minutes + "m" : minutes + "m";
    }

    public static Component chat(OperationResult result) {
        return Component.literal(result.message()).withStyle(result.success() ? ChatFormatting.GREEN : ChatFormatting.RED);
    }

    public static boolean createCoinCollection(UUID playerUuid, String playerName, long amount, String reason) {
        synchronized (LOCK) {
            try (Connection connection = openConnection()) {
                insertCoinCollection(connection, playerUuid, playerName, amount, reason);
                return true;
            } catch (SQLException ex) {
                CreateCoinMarket.LOGGER.error("Failed to create coin collection", ex);
                return false;
            }
        }
    }

    public static boolean createPayoutOrCredit(UUID playerUuid, String playerName, String listingId, long amount, String reason) {
        if (amount <= 0L) {
            return true;
        }
        synchronized (LOCK) {
            if (AuctionConfig.autoCreditSellerProceeds() && shouldTryDirectPayout(playerUuid)) {
                if (EconomyManager.service().deposit(playerUuid, playerName, amount)) {
                    return true;
                }
            }
            try (Connection connection = openConnection()) {
                insertPayout(connection, playerUuid, playerName, listingId, amount, "proceeds", reason, now());
                return true;
            } catch (SQLException ex) {
                CreateCoinMarket.LOGGER.error("Failed to create payout for {}", playerUuid, ex);
                return false;
            }
        }
    }

    private static boolean shouldTryDirectPayout(UUID playerUuid) {
        if ("physical".equals(AuctionConfig.payoutMode())) {
            return false;
        }
        if (!AuctionConfig.allowOfflinePayoutAccrual() && server.getPlayerList().getPlayer(playerUuid) == null) {
            return false;
        }
        return true;
    }

    public static boolean createItemCollection(UUID playerUuid, String playerName, String itemStack, String reason) {
        synchronized (LOCK) {
            try (Connection connection = openConnection()) {
                insertItemCollection(connection, playerUuid, playerName, itemStack, reason);
                return true;
            } catch (SQLException ex) {
                CreateCoinMarket.LOGGER.error("Failed to create item collection", ex);
                return false;
            }
        }
    }

    public static void refreshEconomyAnalytics() {
        synchronized (LOCK) {
            try (Connection connection = openConnection()) {
                connection.setAutoCommit(false);
                refreshDailySnapshots(connection, now());
                refreshItemMarketStats(connection, now());
                refreshPlayerMarketStats(connection, now());
                connection.commit();
            } catch (SQLException ex) {
                CreateCoinMarket.LOGGER.error("Failed to refresh economy analytics", ex);
            }
        }
    }

    private static String normalizeScreenMode(String mode) {
        String normalized = AuctionConfig.normalizeMode(mode);
        if ("all".equals(normalized)) {
            return "browse";
        }
        if ("dashboard".equals(normalized) || "pricecheck".equals(normalized) || "economy".equals(normalized) || "adminpage".equals(normalized)) {
            return normalized;
        }
        return normalized;
    }

    private static String listingFilterMode(String mode) {
        return switch (mode) {
            case "admin" -> "admin";
            case "public" -> "public";
            case "my" -> "my";
            case "browse" -> "all";
            default -> "all";
        };
    }

    private static String normalizeSort(String sort) {
        if (sort == null) {
            return "newest";
        }
        return switch (sort.toLowerCase(Locale.ROOT)) {
            case "cheapest", "ending", "volume" -> sort.toLowerCase(Locale.ROOT);
            default -> "newest";
        };
    }

    private static long countVisibleListings(Connection connection, String mode, UUID viewer, String query, long now) throws SQLException {
        QueryParts parts = visibleListingWhere(mode, viewer, query, now);
        return scalarLong(connection, "SELECT COUNT(*) FROM listings " + parts.where(), parts.params().toArray());
    }

    private static List<AuctionListing> selectVisibleListings(Connection connection, String mode, UUID viewer, String sort, String query, long now, int limit, int offset) throws SQLException {
        QueryParts parts = visibleListingWhere(mode, viewer, query, now);
        List<Object> params = new ArrayList<>(parts.params());
        params.add(limit);
        params.add(offset);
        String orderBy = switch (sort) {
            case "cheapest" -> " ORDER BY CASE WHEN listing_type='auction' AND current_bid>0 THEN current_bid ELSE price END ASC, created_at DESC";
            case "ending" -> " ORDER BY CASE WHEN ends_at=0 THEN 1 ELSE 0 END ASC, ends_at ASC, created_at DESC";
            case "volume" -> " ORDER BY (SELECT COUNT(*) FROM transactions t WHERE t.item_id=listings.item_id) DESC, created_at DESC";
            default -> " ORDER BY created_at DESC";
        };
        try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM listings " + parts.where() + orderBy + " LIMIT ? OFFSET ?")) {
            bind(statement, params);
            try (ResultSet rs = statement.executeQuery()) {
                List<AuctionListing> result = new ArrayList<>();
                while (rs.next()) {
                    result.add(readListing(rs));
                }
                return result;
            }
        }
    }

    private static QueryParts visibleListingWhere(String mode, UUID viewer, String query, long now) {
        String listingMode = listingFilterMode(mode);
        StringBuilder where = new StringBuilder("WHERE status='active' AND ((ends_at=0 AND (expires_at=0 OR expires_at>?)) OR ends_at>?)");
        List<Object> params = new ArrayList<>();
        params.add(now);
        params.add(now);
        if ("admin".equals(listingMode) || "public".equals(listingMode)) {
            where.append(" AND category=?");
            params.add(listingMode);
        } else if ("my".equals(listingMode)) {
            where.append(" AND seller_uuid=?");
            params.add(viewer.toString());
        }
        if (query != null && !query.isBlank()) {
            where.append(" AND (LOWER(item_id) LIKE ? OR LOWER(seller_name) LIKE ?)");
            String like = "%" + query.toLowerCase(Locale.ROOT) + "%";
            params.add(like);
            params.add(like);
        }
        return new QueryParts(where.toString(), params);
    }

    private static MarketScreenData.ListingView toListingView(AuctionListing listing) {
        ItemStack item = decodeItem(listing);
        String itemName = item.isEmpty() ? listing.itemId : item.getHoverName().getString();
        MarketStats stats = marketStats(listing.itemId);
        return new MarketScreenData.ListingView(
            listing.id,
            listing.category,
            listing.sellerName == null ? "Server" : listing.sellerName,
            listing.itemStack,
            listing.itemId,
            itemName,
            listing.itemCount,
            listing.price,
            listing.createdAt,
            listing.expiresAt,
            timeRemaining(listing),
            stats.lowestActiveListing(),
            Math.round(stats.average7d()),
            stats.soldCount7d(),
            listing.listingType,
            listing.startPrice,
            listing.buyoutPrice,
            listing.currentBid,
            listing.minIncrement,
            listing.highestBidderName == null ? "" : listing.highestBidderName
        );
    }

    private static MarketScreenData.CollectionView toCollectionView(CollectionEntry entry) {
        String itemName = "";
        if ("item".equals(entry.type)) {
            ItemStack stack = ItemStackCodec.decodeFromString(entry.itemStack, lookupProvider);
            itemName = stack.isEmpty() ? "Stored item" : stack.getHoverName().getString();
        } else if ("proceeds".equals(entry.type)) {
            itemName = "Pending Proceeds";
        } else if ("coins".equals(entry.type)) {
            itemName = "Coins";
        }
        return new MarketScreenData.CollectionView(
            entry.id,
            entry.type,
            entry.amount,
            entry.itemStack == null ? "" : entry.itemStack,
            itemName,
            entry.reason,
            entry.createdAt
        );
    }

    private static MarketScreenData.MarketInsight priceCheckForHand(ServerPlayer player) {
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            return MarketScreenData.MarketInsight.empty();
        }
        String itemId = BuiltInRegistries.ITEM.getKey(held.getItem()).toString();
        MarketStats stats = marketStats(itemId);
        return new MarketScreenData.MarketInsight(
            itemId,
            held.getHoverName().getString(),
            stats.lastSoldPrice(),
            Math.round(stats.average24h()),
            Math.round(stats.average7d()),
            Math.round(stats.average30d()),
            stats.lowestActiveListing(),
            stats.activeListingCount(),
            stats.soldCount24h(),
            stats.soldCount7d(),
            stats.trend()
        );
    }

    private static MarketScreenData.DashboardSummary dashboardSummary(Connection connection, long now) throws SQLException {
        long day = DAY_MILLIS;
        long active = scalarLong(connection, "SELECT COUNT(*) FROM listings WHERE status='active' AND ((ends_at=0 AND (expires_at=0 OR expires_at>?)) OR ends_at>?)", now, now);
        long publicActive = scalarLong(connection, "SELECT COUNT(*) FROM listings WHERE status='active' AND category='public' AND ((ends_at=0 AND (expires_at=0 OR expires_at>?)) OR ends_at>?)", now, now);
        long adminActive = scalarLong(connection, "SELECT COUNT(*) FROM listings WHERE status='active' AND category='admin' AND ((ends_at=0 AND (expires_at=0 OR expires_at>?)) OR ends_at>?)", now, now);
        long volume24h = scalarLong(connection, "SELECT COALESCE(SUM(price),0) FROM transactions WHERE timestamp>=?", now - day);
        long volume7d = scalarLong(connection, "SELECT COALESCE(SUM(price),0) FROM transactions WHERE timestamp>=?", now - 7L * day);
        long volume30d = scalarLong(connection, "SELECT COALESCE(SUM(price),0) FROM transactions WHERE timestamp>=?", now - 30L * day);
        long allTime = scalarLong(connection, "SELECT COALESCE(SUM(price),0) FROM transactions");
        long taxes = scalarLong(connection, "SELECT COALESCE(SUM(tax),0) FROM transactions");
        long adminSales = scalarLong(connection, "SELECT COALESCE(SUM(price),0) FROM transactions WHERE category='admin'");
        long playerSales = scalarLong(connection, "SELECT COALESCE(SUM(price),0) FROM transactions WHERE category='public'");
        long averageSale = Math.round(scalarDouble(connection, "SELECT COALESCE(AVG(price),0) FROM transactions"));
        String mostTraded = scalarString(connection, "SELECT item_name FROM transactions GROUP BY item_id, item_name ORDER BY COUNT(*) DESC LIMIT 1", "unknown");
        return new MarketScreenData.DashboardSummary(active, publicActive, adminActive, volume24h, volume7d, volume30d, allTime, adminSales + taxes, playerSales, averageSale, mostTraded);
    }

    private static List<MarketScreenData.ChartPoint> chartPoints(Connection connection, String aggregate, long now) throws SQLException {
        int days = AuctionConfig.maxChartDays();
        long cutoff = now - (long) days * DAY_MILLIS;
        String sql = "SELECT strftime('%m-%d', timestamp / 1000, 'unixepoch') AS day_label, COALESCE(" + aggregate + ",0) AS value, COUNT(*) AS count_value FROM transactions WHERE timestamp>=? GROUP BY day_label ORDER BY MIN(timestamp) ASC";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, cutoff);
            try (ResultSet rs = statement.executeQuery()) {
                List<MarketScreenData.ChartPoint> result = new ArrayList<>();
                while (rs.next()) {
                    result.add(new MarketScreenData.ChartPoint(rs.getString("day_label"), rs.getLong("value"), rs.getLong("count_value")));
                }
                return result;
            }
        }
    }

    private static List<MarketScreenData.NamedValue> flowBreakdown(Connection connection) throws SQLException {
        long admin = scalarLong(connection, "SELECT COALESCE(SUM(price),0) FROM transactions WHERE category='admin'");
        long publicSales = scalarLong(connection, "SELECT COALESCE(SUM(price),0) FROM transactions WHERE category='public'");
        long taxes = scalarLong(connection, "SELECT COALESCE(SUM(tax),0) FROM transactions");
        return List.of(
            new MarketScreenData.NamedValue("Admin sales", admin, 0, "sink"),
            new MarketScreenData.NamedValue("Player sales", publicSales, 0, "flow"),
            new MarketScreenData.NamedValue("Taxes", taxes, 0, "sink")
        );
    }

    private static List<MarketScreenData.NamedValue> categoryBreakdown(Connection connection, long now) throws SQLException {
        long admin = scalarLong(connection, "SELECT COUNT(*) FROM listings WHERE status='active' AND category='admin' AND (expires_at=0 OR expires_at>?)", now);
        long market = scalarLong(connection, "SELECT COUNT(*) FROM listings WHERE status='active' AND category='public' AND (expires_at=0 OR expires_at>?)", now);
        return List.of(
            new MarketScreenData.NamedValue("Admin", admin, 0, "listings"),
            new MarketScreenData.NamedValue("Public", market, 0, "listings")
        );
    }

    private static List<MarketScreenData.NamedValue> topItems(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT item_name, item_id, COALESCE(SUM(price),0) AS total, COUNT(*) AS sold FROM transactions GROUP BY item_id, item_name ORDER BY total DESC LIMIT ?")) {
            statement.setInt(1, AuctionConfig.maxLeaderboardEntries());
            try (ResultSet rs = statement.executeQuery()) {
                List<MarketScreenData.NamedValue> result = new ArrayList<>();
                while (rs.next()) {
                    result.add(new MarketScreenData.NamedValue(rs.getString("item_name"), rs.getLong("total"), rs.getInt("sold"), rs.getString("item_id")));
                }
                return result;
            }
        }
    }

    private static List<MarketScreenData.NamedValue> topPlayers(Connection connection, String nameColumn, String uuidColumn, String where) throws SQLException {
        String sql = "SELECT " + nameColumn + " AS name, " + uuidColumn + " AS uuid, COALESCE(SUM(price),0) AS total, COUNT(*) AS count_value FROM transactions WHERE " + where + " GROUP BY " + uuidColumn + ", " + nameColumn + " ORDER BY total DESC LIMIT ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, AuctionConfig.maxLeaderboardEntries());
            try (ResultSet rs = statement.executeQuery()) {
                List<MarketScreenData.NamedValue> result = new ArrayList<>();
                while (rs.next()) {
                    result.add(new MarketScreenData.NamedValue(rs.getString("name"), rs.getLong("total"), rs.getInt("count_value"), rs.getString("uuid")));
                }
                return result;
            }
        }
    }

    private static void refreshDailySnapshots(Connection connection, long now) throws SQLException {
        long cutoff = now - (long) AuctionConfig.maxChartDays() * DAY_MILLIS;
        try (PreparedStatement delete = connection.prepareStatement("DELETE FROM economy_daily_snapshots WHERE day>=?")) {
            delete.setString(1, DAY_FORMAT.format(Instant.ofEpochMilli(cutoff)));
            delete.executeUpdate();
        }
        String sql = "SELECT strftime('%Y-%m-%d', timestamp / 1000, 'unixepoch') AS day_key, COALESCE(SUM(price),0) AS total_volume, COALESCE(SUM(CASE WHEN category='admin' THEN price ELSE 0 END),0) AS admin_volume, COALESCE(SUM(CASE WHEN category='public' THEN price ELSE 0 END),0) AS public_volume, COALESCE(SUM(tax),0) AS taxes_collected, COUNT(*) AS items_sold, COUNT(DISTINCT buyer_uuid) AS unique_buyers, COUNT(DISTINCT seller_uuid) AS unique_sellers, COALESCE(AVG(price),0) AS average_sale_price FROM transactions WHERE timestamp>=? GROUP BY day_key";
        try (PreparedStatement select = connection.prepareStatement(sql)) {
            select.setLong(1, cutoff);
            try (ResultSet rs = select.executeQuery()) {
                while (rs.next()) {
                    try (PreparedStatement upsert = connection.prepareStatement("INSERT OR REPLACE INTO economy_daily_snapshots(day, total_volume, admin_volume, public_volume, taxes_collected, items_sold, unique_buyers, unique_sellers, active_listings, average_sale_price) VALUES(?,?,?,?,?,?,?,?,?,?)")) {
                        upsert.setString(1, rs.getString("day_key"));
                        upsert.setLong(2, rs.getLong("total_volume"));
                        upsert.setLong(3, rs.getLong("admin_volume"));
                        upsert.setLong(4, rs.getLong("public_volume"));
                        upsert.setLong(5, rs.getLong("taxes_collected"));
                        upsert.setInt(6, rs.getInt("items_sold"));
                        upsert.setInt(7, rs.getInt("unique_buyers"));
                        upsert.setInt(8, rs.getInt("unique_sellers"));
                        upsert.setLong(9, scalarLong(connection, "SELECT COUNT(*) FROM listings WHERE status='active' AND (expires_at=0 OR expires_at>?)", now));
                        upsert.setLong(10, Math.round(rs.getDouble("average_sale_price")));
                        upsert.executeUpdate();
                    }
                }
            }
        }
    }

    private static void refreshItemMarketStats(Connection connection, long now) throws SQLException {
        List<String> itemIds = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT item_id FROM transactions UNION SELECT item_id FROM listings")) {
            while (rs.next()) {
                itemIds.add(rs.getString(1));
            }
        }
        for (String itemId : itemIds) {
            MarketStats stats = marketStats(itemId);
            long high = scalarLong(connection, "SELECT MAX(price) FROM listings WHERE item_id=? AND status='active' AND (expires_at=0 OR expires_at>?)", itemId, now);
            try (PreparedStatement statement = connection.prepareStatement("INSERT OR REPLACE INTO item_market_stats(item_id, last_sale_price, average_24h, average_7d, average_30d, lowest_active_price, highest_active_price, active_listing_count, sold_24h, sold_7d, sold_30d, updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)")) {
                statement.setString(1, itemId);
                statement.setLong(2, stats.lastSoldPrice());
                statement.setLong(3, Math.round(stats.average24h()));
                statement.setLong(4, Math.round(stats.average7d()));
                statement.setLong(5, Math.round(stats.average30d()));
                statement.setLong(6, stats.lowestActiveListing());
                statement.setLong(7, high);
                statement.setInt(8, stats.activeListingCount());
                statement.setInt(9, stats.soldCount24h());
                statement.setInt(10, stats.soldCount7d());
                statement.setInt(11, (int) scalarLong(connection, "SELECT COUNT(*) FROM transactions WHERE item_id=? AND timestamp>=?", itemId, now - 30L * DAY_MILLIS));
                statement.setLong(12, now);
                statement.executeUpdate();
            }
        }
    }

    private static void refreshPlayerMarketStats(Connection connection, long now) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("DELETE FROM player_market_stats");
            statement.execute("INSERT INTO player_market_stats(player_uuid, player_name, total_sales, total_purchases, listings_created, items_sold, items_bought, taxes_paid, last_active_at) SELECT seller_uuid, COALESCE(MAX(seller_name),'Unknown'), COALESCE(SUM(price),0), 0, 0, COUNT(*), 0, COALESCE(SUM(tax),0), MAX(timestamp) FROM transactions WHERE seller_uuid IS NOT NULL GROUP BY seller_uuid");
            statement.execute("INSERT INTO player_market_stats(player_uuid, player_name, total_sales, total_purchases, listings_created, items_sold, items_bought, taxes_paid, last_active_at) SELECT buyer_uuid, COALESCE(MAX(buyer_name),'Unknown'), 0, COALESCE(SUM(price),0), 0, 0, COUNT(*), 0, MAX(timestamp) FROM transactions WHERE buyer_uuid IS NOT NULL GROUP BY buyer_uuid ON CONFLICT(player_uuid) DO UPDATE SET total_purchases=excluded.total_purchases, items_bought=excluded.items_bought, last_active_at=MAX(player_market_stats.last_active_at, excluded.last_active_at)");
            statement.execute("UPDATE player_market_stats SET listings_created=(SELECT COUNT(*) FROM listings WHERE listings.seller_uuid=player_market_stats.player_uuid)");
        }
    }

    private static String scalarString(Connection connection, String sql, String fallback, Object... params) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, List.of(params));
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getString(1) : fallback;
            }
        }
    }

    private record QueryParts(String where, List<Object> params) {
    }

    private static Path resolveDatabasePath(MinecraftServer minecraftServer) {
        Path worldRoot = minecraftServer.getWorldPath(LevelResource.ROOT);
        String raw = AuctionServerConfig.sqlitePath();
        if (raw == null || raw.isBlank()) {
            raw = AuctionConfig.databasePath();
        }
        if (isLegacyDefaultDatabasePath(raw)) {
            raw = NEW_DEFAULT_DATABASE_PATH;
        }
        String configured = raw.replace("{world}", worldRoot.toString());
        return Path.of(configured).toAbsolutePath().normalize();
    }

    private static void validateExternalDatabaseMode() {
        if ("mysql".equals(AuctionServerConfig.databaseMode())) {
            throw new IllegalStateException("database.mode=mysql is configured, but Create: CoinMarket 1.2.0 does not bundle a MySQL JDBC driver. Add a compatible driver/mod integration or use database.mode=sqlite.");
        }
        throw new IllegalStateException("Unsupported database.mode=" + AuctionServerConfig.databaseMode());
    }

    private static void migrateLegacyDatabase(MinecraftServer minecraftServer, Path newPath) throws IOException {
        Path worldRoot = minecraftServer.getWorldPath(LevelResource.ROOT);
        Path oldPath = Path.of(OLD_DEFAULT_DATABASE_PATH.replace("{world}", worldRoot.toString())).toAbsolutePath().normalize();
        if (oldPath.equals(newPath) || !Files.exists(oldPath)) {
            return;
        }
        if (Files.exists(newPath)) {
            CreateCoinMarket.LOGGER.info("Found old AuctionHouseJS database at {}; using existing Create: CoinMarket database at {} and leaving the old database untouched.", oldPath, newPath);
            return;
        }
        Files.createDirectories(newPath.getParent());
        Files.copy(oldPath, newPath, StandardCopyOption.COPY_ATTRIBUTES);
        copyLegacySQLiteSidecar(oldPath, newPath, "-wal");
        copyLegacySQLiteSidecar(oldPath, newPath, "-shm");
        CreateCoinMarket.LOGGER.info("Migrated old AuctionHouseJS database from {} to Create: CoinMarket database at {}. The old database was left untouched.", oldPath, newPath);
    }

    private static void copyLegacySQLiteSidecar(Path oldPath, Path newPath, String suffix) throws IOException {
        Path oldSidecar = oldPath.resolveSibling(oldPath.getFileName() + suffix);
        if (!Files.exists(oldSidecar)) {
            return;
        }
        Path newSidecar = newPath.resolveSibling(newPath.getFileName() + suffix);
        Files.copy(oldSidecar, newSidecar, StandardCopyOption.COPY_ATTRIBUTES);
    }

    private static void validateDatabaseParent(Path parent) throws IOException {
        if (parent == null) {
            throw new IOException("Database path has no parent directory.");
        }
        Files.createDirectories(parent);
        if (!Files.isDirectory(parent)) {
            throw new IOException("Database parent is not a directory: " + parent);
        }
        Path probe = Files.createTempFile(parent, "coinmarket-write-test", ".tmp");
        Files.deleteIfExists(probe);
    }

    private static boolean isLegacyDefaultDatabasePath(String path) {
        if (path == null) {
            return false;
        }
        String normalized = path.replace('\\', '/').toLowerCase(Locale.ROOT);
        return normalized.endsWith("/serverconfig/auctionhousejs/auctionhouse.db")
            || "serverconfig/auctionhousejs/auctionhouse.db".equals(normalized)
            || OLD_DEFAULT_DATABASE_PATH.equals(normalized);
    }

    private static Connection openConnection() throws SQLException {
        Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA busy_timeout=5000");
            statement.execute("PRAGMA foreign_keys=ON");
            statement.execute("PRAGMA journal_mode=WAL");
        }
        return connection;
    }

    private static void migrate(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS schema_version (version INTEGER NOT NULL)");
            statement.execute("CREATE TABLE IF NOT EXISTS listings (id TEXT PRIMARY KEY, category TEXT NOT NULL, seller_uuid TEXT, seller_name TEXT, item_stack TEXT NOT NULL, item_id TEXT NOT NULL, item_count INTEGER NOT NULL, price BIGINT NOT NULL, created_at BIGINT NOT NULL, expires_at BIGINT NOT NULL, status TEXT NOT NULL, buyer_uuid TEXT, buyer_name TEXT, sold_at BIGINT, source TEXT NOT NULL, infinite_admin_listing INTEGER NOT NULL DEFAULT 0, version INTEGER NOT NULL DEFAULT 1)");
            statement.execute("CREATE TABLE IF NOT EXISTS transactions (id TEXT PRIMARY KEY, listing_id TEXT NOT NULL, item_id TEXT NOT NULL, item_name TEXT NOT NULL, item_count INTEGER NOT NULL, price BIGINT NOT NULL, buyer_uuid TEXT NOT NULL, buyer_name TEXT NOT NULL, seller_uuid TEXT, seller_name TEXT, category TEXT NOT NULL, tax BIGINT NOT NULL DEFAULT 0, timestamp BIGINT NOT NULL)");
            statement.execute("CREATE TABLE IF NOT EXISTS collections (id TEXT PRIMARY KEY, player_uuid TEXT NOT NULL, player_name TEXT NOT NULL, type TEXT NOT NULL, amount BIGINT, item_stack TEXT, reason TEXT NOT NULL, created_at BIGINT NOT NULL, claimed_at BIGINT, claimed INTEGER NOT NULL DEFAULT 0)");
            statement.execute("CREATE TABLE IF NOT EXISTS audit_log (id TEXT PRIMARY KEY, actor_uuid TEXT, actor_name TEXT, action TEXT NOT NULL, listing_id TEXT, details TEXT, timestamp BIGINT NOT NULL)");
            statement.execute("CREATE TABLE IF NOT EXISTS bids (id TEXT PRIMARY KEY, listing_id TEXT NOT NULL, bidder_uuid TEXT NOT NULL, bidder_name TEXT NOT NULL, bid_amount BIGINT NOT NULL, created_at BIGINT NOT NULL, refunded INTEGER NOT NULL DEFAULT 0, refund_reference TEXT)");
            statement.execute("CREATE TABLE IF NOT EXISTS payouts (id TEXT PRIMARY KEY, player_uuid TEXT NOT NULL, player_name TEXT NOT NULL, source_listing_id TEXT, amount BIGINT NOT NULL, currency_mode TEXT NOT NULL, status TEXT NOT NULL, reason TEXT NOT NULL, created_at BIGINT NOT NULL, claimed_at BIGINT)");
            statement.execute("CREATE TABLE IF NOT EXISTS economy_daily_snapshots (day TEXT PRIMARY KEY, total_volume BIGINT NOT NULL, admin_volume BIGINT NOT NULL, public_volume BIGINT NOT NULL, taxes_collected BIGINT NOT NULL, items_sold INTEGER NOT NULL, unique_buyers INTEGER NOT NULL, unique_sellers INTEGER NOT NULL, active_listings INTEGER NOT NULL, average_sale_price BIGINT NOT NULL)");
            statement.execute("CREATE TABLE IF NOT EXISTS item_market_stats (item_id TEXT PRIMARY KEY, last_sale_price BIGINT, average_24h BIGINT, average_7d BIGINT, average_30d BIGINT, lowest_active_price BIGINT, highest_active_price BIGINT, active_listing_count INTEGER, sold_24h INTEGER, sold_7d INTEGER, sold_30d INTEGER, updated_at BIGINT)");
            statement.execute("CREATE TABLE IF NOT EXISTS player_market_stats (player_uuid TEXT PRIMARY KEY, player_name TEXT NOT NULL, total_sales BIGINT NOT NULL, total_purchases BIGINT NOT NULL, listings_created INTEGER NOT NULL, items_sold INTEGER NOT NULL, items_bought INTEGER NOT NULL, taxes_paid BIGINT NOT NULL, last_active_at BIGINT NOT NULL)");
            addColumnIfMissing(statement, "listings", "listing_type", "TEXT NOT NULL DEFAULT 'fixed_price'");
            addColumnIfMissing(statement, "listings", "start_price", "BIGINT NOT NULL DEFAULT 0");
            addColumnIfMissing(statement, "listings", "buyout_price", "BIGINT NOT NULL DEFAULT 0");
            addColumnIfMissing(statement, "listings", "current_bid", "BIGINT NOT NULL DEFAULT 0");
            addColumnIfMissing(statement, "listings", "highest_bidder_uuid", "TEXT");
            addColumnIfMissing(statement, "listings", "highest_bidder_name", "TEXT");
            addColumnIfMissing(statement, "listings", "min_increment", "BIGINT NOT NULL DEFAULT 1");
            addColumnIfMissing(statement, "listings", "starts_at", "BIGINT NOT NULL DEFAULT 0");
            addColumnIfMissing(statement, "listings", "ends_at", "BIGINT NOT NULL DEFAULT 0");
            addColumnIfMissing(statement, "listings", "updated_at", "BIGINT NOT NULL DEFAULT 0");
            statement.execute("UPDATE listings SET start_price=price WHERE start_price=0");
            statement.execute("UPDATE listings SET starts_at=created_at WHERE starts_at=0");
            statement.execute("UPDATE listings SET ends_at=expires_at WHERE ends_at=0");
            statement.execute("UPDATE listings SET updated_at=created_at WHERE updated_at=0");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_listings_active ON listings(status, category, expires_at)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_listings_type_status ON listings(listing_type, status)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_listings_ends_at ON listings(ends_at)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_listings_item ON listings(item_id, status, price)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_bids_listing ON bids(listing_id, created_at)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_bids_bidder ON bids(bidder_uuid)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_payouts_player ON payouts(player_uuid, status)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_transactions_item_time ON transactions(item_id, timestamp)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_collections_player ON collections(player_uuid, claimed)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_listings_status ON listings(status)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_listings_category ON listings(category)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_listings_item_id ON listings(item_id)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_listings_seller_uuid ON listings(seller_uuid)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_listings_created_at ON listings(created_at)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_listings_expires_at ON listings(expires_at)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_transactions_item_id ON transactions(item_id)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_transactions_timestamp ON transactions(timestamp)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_transactions_buyer_uuid ON transactions(buyer_uuid)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_transactions_seller_uuid ON transactions(seller_uuid)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_collections_claimed ON collections(claimed)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_audit_log_timestamp ON audit_log(timestamp)");
        }
        if (scalarLong(connection, "SELECT COUNT(*) FROM schema_version") == 0L) {
            try (PreparedStatement statement = connection.prepareStatement("INSERT INTO schema_version(version) VALUES(1)")) {
                statement.executeUpdate();
            }
        }
    }

    private static void addColumnIfMissing(Statement statement, String table, String column, String definition) throws SQLException {
        try {
            statement.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
        } catch (SQLException ex) {
            String message = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase(Locale.ROOT);
            if (!message.contains("duplicate column") && !message.contains("already exists")) {
                throw ex;
            }
        }
    }

    private static int expireOldListings(Connection connection, long now) throws SQLException {
        List<AuctionListing> expiring = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM listings WHERE status='active' AND ((ends_at>0 AND ends_at<=?) OR (ends_at=0 AND expires_at>0 AND expires_at<=?))")) {
            statement.setLong(1, now);
            statement.setLong(2, now);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    expiring.add(readListing(rs));
                }
            }
        }
        for (AuctionListing listing : expiring) {
            if (listing.isAuction()) {
                if (listing.highestBidderUuid != null && listing.currentBid > 0L) {
                    resolveAuctionAsSold(connection, listing, listing.highestBidderUuid, listing.highestBidderName, now, "auction_expired");
                } else {
                    returnListingAndRefund(connection, listing, AuctionListing.STATUS_EXPIRED, "NO_BIDS_EXPIRED");
                }
            } else {
                returnListingAndRefund(connection, listing, AuctionListing.STATUS_EXPIRED, "EXPIRED");
            }
        }
        return expiring.size();
    }

    private static void resolveAuctionAsSold(Connection connection, AuctionListing listing, UUID winnerUuid, String winnerName, long timestamp, String reason) throws SQLException {
        if (winnerUuid == null || listing.currentBid <= 0L) {
            returnListingAndRefund(connection, listing, AuctionListing.STATUS_EXPIRED, "NO_BIDS_EXPIRED");
            return;
        }
        listing.price = listing.currentBid;
        updateListingStatus(connection, listing.id, AuctionListing.STATUS_SOLD, winnerUuid, winnerName, timestamp);
        ItemStack item = decodeItem(listing);
        long tax = listing.isPublic() ? Math.round(listing.currentBid * (AuctionConfig.publicTaxPercent() / 100.0D)) : 0L;
        tax = Math.max(0L, Math.min(listing.currentBid, tax));
        insertTransaction(connection, listing, winnerUuid, winnerName, tax, timestamp, item);
        insertItemCollection(connection, winnerUuid, winnerName, listing.itemStack, "auction_won");
        if (listing.sellerUuid != null) {
            insertPayout(connection, listing.sellerUuid, listing.sellerName, listing.id, listing.currentBid - tax, "proceeds", reason, timestamp);
        }
    }

    private static void returnListingAndRefund(Connection connection, AuctionListing listing, String status, String reason) throws SQLException {
        updateListingStatus(connection, listing.id, status, null, null, 0L);
        if (listing.highestBidderUuid != null && listing.currentBid > 0L) {
            insertPayout(connection, listing.highestBidderUuid, listing.highestBidderName, listing.id, listing.currentBid, "refund", reason, now());
        }
        if (listing.sellerUuid != null) {
            insertItemCollection(connection, listing.sellerUuid, listing.sellerName, listing.itemStack, reason);
        }
    }

    private static void insertBid(Connection connection, String listingId, UUID bidderUuid, String bidderName, long amount, long createdAt) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO bids(id, listing_id, bidder_uuid, bidder_name, bid_amount, created_at, refunded, refund_reference) VALUES(?,?,?,?,?,?,0,'')")) {
            statement.setString(1, "BID-" + UUID.randomUUID());
            statement.setString(2, listingId);
            statement.setString(3, bidderUuid.toString());
            statement.setString(4, bidderName);
            statement.setLong(5, amount);
            statement.setLong(6, createdAt);
            statement.executeUpdate();
        }
    }

    private static void updateAuctionBid(Connection connection, String listingId, long amount, UUID bidderUuid, String bidderName, long updatedAt) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("UPDATE listings SET current_bid=?, highest_bidder_uuid=?, highest_bidder_name=?, updated_at=?, version=version+1 WHERE id=? AND status='active' AND listing_type='auction'")) {
            statement.setLong(1, amount);
            statement.setString(2, bidderUuid.toString());
            statement.setString(3, bidderName);
            statement.setLong(4, updatedAt);
            statement.setString(5, listingId);
            if (statement.executeUpdate() != 1) {
                throw new SQLException("Auction bid update failed for " + listingId);
            }
        }
    }

    private static void insertPayout(Connection connection, UUID playerUuid, String playerName, String listingId, long amount, String type, String reason, long createdAt) throws SQLException {
        if (playerUuid == null || amount <= 0L) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO payouts(id, player_uuid, player_name, source_listing_id, amount, currency_mode, status, reason, created_at, claimed_at) VALUES(?,?,?,?,?,?,?,?,?,0)")) {
            statement.setString(1, "PO-" + UUID.randomUUID());
            statement.setString(2, playerUuid.toString());
            statement.setString(3, playerName == null ? "Unknown" : playerName);
            statement.setString(4, listingId);
            statement.setLong(5, amount);
            statement.setString(6, EconomyManager.modeName());
            statement.setString(7, "pending");
            statement.setString(8, type + ":" + reason);
            statement.setLong(9, createdAt);
            statement.executeUpdate();
        }
    }

    private static void insertListing(Connection connection, AuctionListing listing) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO listings(id, category, seller_uuid, seller_name, item_stack, item_id, item_count, price, created_at, expires_at, status, buyer_uuid, buyer_name, sold_at, source, infinite_admin_listing, version, listing_type, start_price, buyout_price, current_bid, highest_bidder_uuid, highest_bidder_name, min_increment, starts_at, ends_at, updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
            statement.setString(1, listing.id);
            statement.setString(2, listing.category);
            statement.setString(3, listing.sellerUuid == null ? null : listing.sellerUuid.toString());
            statement.setString(4, listing.sellerName);
            statement.setString(5, listing.itemStack);
            statement.setString(6, listing.itemId);
            statement.setInt(7, listing.itemCount);
            statement.setLong(8, listing.price);
            statement.setLong(9, listing.createdAt);
            statement.setLong(10, listing.expiresAt);
            statement.setString(11, listing.status);
            statement.setString(12, null);
            statement.setString(13, null);
            statement.setLong(14, 0L);
            statement.setString(15, listing.source);
            statement.setInt(16, listing.infiniteAdminListing ? 1 : 0);
            statement.setInt(17, listing.version);
            statement.setString(18, listing.listingType == null ? AuctionListing.TYPE_FIXED_PRICE : listing.listingType);
            statement.setLong(19, listing.startPrice > 0L ? listing.startPrice : listing.price);
            statement.setLong(20, listing.buyoutPrice);
            statement.setLong(21, listing.currentBid);
            statement.setString(22, listing.highestBidderUuid == null ? null : listing.highestBidderUuid.toString());
            statement.setString(23, listing.highestBidderName);
            statement.setLong(24, listing.minIncrement <= 0L ? AuctionConfig.defaultMinBidIncrement() : listing.minIncrement);
            statement.setLong(25, listing.startsAt <= 0L ? listing.createdAt : listing.startsAt);
            statement.setLong(26, listing.endsAt <= 0L ? listing.expiresAt : listing.endsAt);
            statement.setLong(27, listing.updatedAt <= 0L ? listing.createdAt : listing.updatedAt);
            statement.executeUpdate();
        }
    }

    private static AuctionListing selectListing(Connection connection, String listingId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM listings WHERE id=?")) {
            statement.setString(1, listingId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? readListing(rs) : null;
            }
        }
    }

    private static List<AuctionListing> activeListingsForAdmin(Connection connection, String target) throws SQLException {
        String sql = "SELECT * FROM listings WHERE status='active' AND ((ends_at=0 AND (expires_at=0 OR expires_at>?)) OR ends_at>?)";
        List<Object> params = new ArrayList<>();
        params.add(now());
        params.add(now());
        if (!"all".equals(target)) {
            sql += " AND category=?";
            params.add(target);
        }
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, params);
            try (ResultSet rs = statement.executeQuery()) {
                List<AuctionListing> result = new ArrayList<>();
                while (rs.next()) {
                    result.add(readListing(rs));
                }
                return result;
            }
        }
    }

    private static AuctionListing readListing(ResultSet rs) throws SQLException {
        AuctionListing listing = new AuctionListing();
        listing.id = rs.getString("id");
        listing.category = rs.getString("category");
        listing.sellerUuid = parseUuid(rs.getString("seller_uuid"));
        listing.sellerName = rs.getString("seller_name");
        listing.itemStack = rs.getString("item_stack");
        listing.itemId = rs.getString("item_id");
        listing.itemCount = rs.getInt("item_count");
        listing.price = rs.getLong("price");
        listing.createdAt = rs.getLong("created_at");
        listing.expiresAt = rs.getLong("expires_at");
        listing.status = rs.getString("status");
        listing.buyerUuid = parseUuid(rs.getString("buyer_uuid"));
        listing.buyerName = rs.getString("buyer_name");
        listing.soldAt = rs.getLong("sold_at");
        listing.source = rs.getString("source");
        listing.infiniteAdminListing = rs.getInt("infinite_admin_listing") != 0;
        listing.version = rs.getInt("version");
        listing.listingType = valueOrDefault(rs.getString("listing_type"), AuctionListing.TYPE_FIXED_PRICE);
        listing.startPrice = rs.getLong("start_price");
        if (listing.startPrice <= 0L) {
            listing.startPrice = listing.price;
        }
        listing.buyoutPrice = rs.getLong("buyout_price");
        listing.currentBid = rs.getLong("current_bid");
        listing.highestBidderUuid = parseUuid(rs.getString("highest_bidder_uuid"));
        listing.highestBidderName = rs.getString("highest_bidder_name");
        listing.minIncrement = Math.max(1L, rs.getLong("min_increment"));
        listing.startsAt = rs.getLong("starts_at");
        listing.endsAt = rs.getLong("ends_at");
        if (listing.endsAt <= 0L) {
            listing.endsAt = listing.expiresAt;
        }
        listing.updatedAt = rs.getLong("updated_at");
        return listing;
    }

    private static void updateListingStatus(Connection connection, String listingId, String status, @Nullable UUID buyerUuid, @Nullable String buyerName, long soldAt) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("UPDATE listings SET status=?, buyer_uuid=?, buyer_name=?, sold_at=?, updated_at=?, version=version+1 WHERE id=? AND status='active'")) {
            statement.setString(1, status);
            statement.setString(2, buyerUuid == null ? null : buyerUuid.toString());
            statement.setString(3, buyerName);
            statement.setLong(4, soldAt);
            statement.setLong(5, now());
            statement.setString(6, listingId);
            if (statement.executeUpdate() != 1) {
                throw new SQLException("Listing update failed for " + listingId);
            }
        }
    }

    private static void updateAnyListingStatus(Connection connection, String listingId, String status, @Nullable UUID buyerUuid, @Nullable String buyerName, long soldAt) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("UPDATE listings SET status=?, buyer_uuid=?, buyer_name=?, sold_at=?, updated_at=?, version=version+1 WHERE id=?")) {
            statement.setString(1, status);
            statement.setString(2, buyerUuid == null ? null : buyerUuid.toString());
            statement.setString(3, buyerName);
            statement.setLong(4, soldAt);
            statement.setLong(5, now());
            statement.setString(6, listingId);
            if (statement.executeUpdate() != 1) {
                throw new SQLException("Listing update failed for " + listingId);
            }
        }
    }

    private static void insertTransaction(Connection connection, AuctionListing listing, ServerPlayer buyer, long tax, long timestamp, ItemStack item) throws SQLException {
        insertTransaction(connection, listing, buyer.getUUID(), buyer.getGameProfile().getName(), tax, timestamp, item);
    }

    private static void insertTransaction(Connection connection, AuctionListing listing, UUID buyerUuid, String buyerName, long tax, long timestamp, ItemStack item) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO transactions(id, listing_id, item_id, item_name, item_count, price, buyer_uuid, buyer_name, seller_uuid, seller_name, category, tax, timestamp) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
            statement.setString(1, "TX-" + UUID.randomUUID());
            statement.setString(2, listing.id);
            statement.setString(3, listing.itemId);
            statement.setString(4, item.getHoverName().getString());
            statement.setInt(5, listing.itemCount);
            statement.setLong(6, listing.price);
            statement.setString(7, buyerUuid.toString());
            statement.setString(8, buyerName);
            statement.setString(9, listing.sellerUuid == null ? null : listing.sellerUuid.toString());
            statement.setString(10, listing.sellerName);
            statement.setString(11, listing.category);
            statement.setLong(12, tax);
            statement.setLong(13, timestamp);
            statement.executeUpdate();
        }
    }

    private static void insertCoinCollection(Connection connection, UUID playerUuid, String playerName, long amount, String reason) throws SQLException {
        if (amount <= 0L) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO collections(id, player_uuid, player_name, type, amount, item_stack, reason, created_at, claimed_at, claimed) VALUES(?,?,?,?,?,?,?,?,?,0)")) {
            statement.setString(1, "CL-" + UUID.randomUUID());
            statement.setString(2, playerUuid.toString());
            statement.setString(3, playerName);
            statement.setString(4, "coins");
            statement.setLong(5, amount);
            statement.setString(6, null);
            statement.setString(7, reason);
            statement.setLong(8, now());
            statement.setLong(9, 0L);
            statement.executeUpdate();
        }
    }

    private static void insertItemCollection(Connection connection, UUID playerUuid, String playerName, String itemStack, String reason) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO collections(id, player_uuid, player_name, type, amount, item_stack, reason, created_at, claimed_at, claimed) VALUES(?,?,?,?,?,?,?,?,?,0)")) {
            statement.setString(1, "CL-" + UUID.randomUUID());
            statement.setString(2, playerUuid.toString());
            statement.setString(3, playerName);
            statement.setString(4, "item");
            statement.setLong(5, 0L);
            statement.setString(6, itemStack);
            statement.setString(7, reason);
            statement.setLong(8, now());
            statement.setLong(9, 0L);
            statement.executeUpdate();
        }
    }

    private static List<CollectionEntry> selectUnclaimedCollections(Connection connection, UUID playerUuid) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM collections WHERE player_uuid=? AND claimed=0 ORDER BY created_at ASC")) {
            statement.setString(1, playerUuid.toString());
            try (ResultSet rs = statement.executeQuery()) {
                List<CollectionEntry> entries = new ArrayList<>();
                while (rs.next()) {
                    CollectionEntry entry = new CollectionEntry();
                    entry.id = rs.getString("id");
                    entry.playerUuid = rs.getString("player_uuid");
                    entry.playerName = rs.getString("player_name");
                    entry.type = rs.getString("type");
                    entry.amount = rs.getLong("amount");
                    entry.itemStack = rs.getString("item_stack");
                    entry.reason = rs.getString("reason");
                    entry.createdAt = rs.getLong("created_at");
                    entry.claimedAt = rs.getLong("claimed_at");
                    entry.claimed = rs.getInt("claimed") != 0;
                    entries.add(entry);
                }
                return entries;
            }
        }
    }

    private static CollectionEntry selectUnclaimedCollection(Connection connection, UUID playerUuid, String collectionId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM collections WHERE player_uuid=? AND id=? AND claimed=0")) {
            statement.setString(1, playerUuid.toString());
            statement.setString(2, collectionId);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                CollectionEntry entry = new CollectionEntry();
                entry.id = rs.getString("id");
                entry.playerUuid = rs.getString("player_uuid");
                entry.playerName = rs.getString("player_name");
                entry.type = rs.getString("type");
                entry.amount = rs.getLong("amount");
                entry.itemStack = rs.getString("item_stack");
                entry.reason = rs.getString("reason");
                entry.createdAt = rs.getLong("created_at");
                entry.claimedAt = rs.getLong("claimed_at");
                entry.claimed = rs.getInt("claimed") != 0;
                return entry;
            }
        }
    }

    private static List<CollectionEntry> selectPendingPayouts(Connection connection, UUID playerUuid) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM payouts WHERE player_uuid=? AND status='pending' ORDER BY created_at ASC LIMIT ?")) {
            statement.setString(1, playerUuid.toString());
            statement.setInt(2, AuctionConfig.maxPendingPayoutEntries());
            try (ResultSet rs = statement.executeQuery()) {
                List<CollectionEntry> entries = new ArrayList<>();
                while (rs.next()) {
                    CollectionEntry entry = new CollectionEntry();
                    entry.id = rs.getString("id");
                    entry.playerUuid = rs.getString("player_uuid");
                    entry.playerName = rs.getString("player_name");
                    entry.type = "proceeds";
                    entry.amount = rs.getLong("amount");
                    entry.itemStack = "";
                    entry.reason = valueOrDefault(rs.getString("reason"), "payout");
                    entry.createdAt = rs.getLong("created_at");
                    entry.claimedAt = rs.getLong("claimed_at");
                    entry.claimed = false;
                    entries.add(entry);
                }
                return entries;
            }
        }
    }

    private static CollectionEntry selectPendingPayout(Connection connection, UUID playerUuid, String payoutId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM payouts WHERE player_uuid=? AND id=? AND status='pending'")) {
            statement.setString(1, playerUuid.toString());
            statement.setString(2, payoutId);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                CollectionEntry entry = new CollectionEntry();
                entry.id = rs.getString("id");
                entry.playerUuid = rs.getString("player_uuid");
                entry.playerName = rs.getString("player_name");
                entry.type = "proceeds";
                entry.amount = rs.getLong("amount");
                entry.itemStack = "";
                entry.reason = valueOrDefault(rs.getString("reason"), "payout");
                entry.createdAt = rs.getLong("created_at");
                return entry;
            }
        }
    }

    private static void markPayoutClaimed(Connection connection, String payoutId, long timestamp) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("UPDATE payouts SET status='claimed', claimed_at=? WHERE id=? AND status='pending'")) {
            statement.setLong(1, timestamp);
            statement.setString(2, payoutId);
            if (statement.executeUpdate() != 1) {
                throw new SQLException("Payout claim failed for " + payoutId);
            }
        }
    }

    private static void markCollectionClaimed(Connection connection, String collectionId, long timestamp) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("UPDATE collections SET claimed=1, claimed_at=? WHERE id=? AND claimed=0")) {
            statement.setLong(1, timestamp);
            statement.setString(2, collectionId);
            if (statement.executeUpdate() != 1) {
                throw new SQLException("Collection claim failed for " + collectionId);
            }
        }
    }

    private static int countActivePublicListings(UUID sellerUuid) {
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM listings WHERE seller_uuid=? AND category='public' AND status='active' AND (expires_at=0 OR expires_at>?)")) {
            statement.setString(1, sellerUuid.toString());
            statement.setLong(2, now());
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException ex) {
            return Integer.MAX_VALUE;
        }
    }

    private static void insertAudit(Connection connection, @Nullable UUID actorUuid, String actorName, String action, @Nullable String listingId, String details) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO audit_log(id, actor_uuid, actor_name, action, listing_id, details, timestamp) VALUES(?,?,?,?,?,?,?)")) {
            statement.setString(1, "AU-" + UUID.randomUUID());
            statement.setString(2, actorUuid == null ? null : actorUuid.toString());
            statement.setString(3, actorName);
            statement.setString(4, action);
            statement.setString(5, listingId);
            statement.setString(6, details);
            statement.setLong(7, now());
            statement.executeUpdate();
        }
    }

    private static String nextId() {
        return "LM-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
    }

    private static UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static String valueOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private record AuctionOptions(long durationHours, long buyoutPrice, long minIncrement) {
        static AuctionOptions parse(String raw) {
            long duration = AuctionConfig.defaultAuctionDurationHours();
            long buyout = 0L;
            long increment = AuctionConfig.defaultMinBidIncrement();
            if (raw == null || raw.isBlank()) {
                return new AuctionOptions(duration, buyout, increment);
            }
            String[] parts = raw.split("\\|", -1);
            duration = parseLong(parts, 0, duration);
            buyout = parseLong(parts, 1, buyout);
            increment = parseLong(parts, 2, increment);
            return new AuctionOptions(duration, buyout, increment);
        }

        private static long parseLong(String[] parts, int index, long fallback) {
            if (index >= parts.length || parts[index].isBlank()) {
                return fallback;
            }
            try {
                return Long.parseLong(parts[index]);
            } catch (NumberFormatException ex) {
                return Long.MIN_VALUE;
            }
        }
    }

    private static void bind(PreparedStatement statement, List<Object> params) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            Object value = params.get(i);
            if (value instanceof Long number) {
                statement.setLong(i + 1, number);
            } else if (value instanceof Integer number) {
                statement.setInt(i + 1, number);
            } else if (value instanceof Double number) {
                statement.setDouble(i + 1, number);
            } else {
                statement.setString(i + 1, String.valueOf(value));
            }
        }
    }

    private static long scalarLong(Connection connection, String sql, Object... params) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, List.of(params));
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        }
    }

    private static double scalarDouble(Connection connection, String sql, Object... params) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, List.of(params));
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getDouble(1) : 0D;
            }
        }
    }

    private static String trend(double avg24, double avg7) {
        if (avg24 <= 0D || avg7 <= 0D) {
            return "unknown";
        }
        double delta = (avg24 - avg7) / avg7;
        if (delta > 0.05D) {
            return "up";
        }
        if (delta < -0.05D) {
            return "down";
        }
        return "stable";
    }

    private static long now() {
        return System.currentTimeMillis();
    }

    public record OperationResult(boolean success, String message) {
        public static OperationResult success(String message) {
            return new OperationResult(true, message);
        }

        public static OperationResult fail(String message) {
            return new OperationResult(false, message);
        }
    }
}
