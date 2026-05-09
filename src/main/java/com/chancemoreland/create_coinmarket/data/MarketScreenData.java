package com.chancemoreland.create_coinmarket.data;

import net.minecraft.network.RegistryFriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

public record MarketScreenData(
    String mode,
    int page,
    int maxPage,
    int totalListings,
    String sort,
    String query,
    long playerBalance,
    long coinBalance,
    long bankBalance,
    long totalSpendableBalance,
    String activePaymentSource,
    boolean hasBankCard,
    boolean bankAvailable,
    String bankWarning,
    int collectionCount,
    long pendingProceeds,
    int pendingPayoutCount,
    boolean admin,
    DashboardSummary summary,
    List<ListingView> listings,
    List<CollectionView> collections,
    MarketInsight priceCheck,
    List<ChartPoint> volumeHistory,
    List<ChartPoint> averagePriceHistory,
    List<NamedValue> flowBreakdown,
    List<NamedValue> categoryBreakdown,
    List<NamedValue> topItems,
    List<NamedValue> topSellers,
    List<NamedValue> topBuyers
) {
    public static MarketScreenData empty(String mode, int page) {
        return new MarketScreenData(
            mode,
            page,
            0,
            0,
            "newest",
            "",
            0L,
            0L,
            0L,
            0L,
            "coins",
            false,
            false,
            "",
            0,
            0L,
            0,
            false,
            DashboardSummary.empty(),
            List.of(),
            List.of(),
            MarketInsight.empty(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of()
        );
    }

    public static MarketScreenData read(RegistryFriendlyByteBuf buf) {
        return new MarketScreenData(
            readString(buf),
            buf.readVarInt(),
            buf.readVarInt(),
            buf.readVarInt(),
            readString(buf),
            readString(buf),
            buf.readLong(),
            buf.readLong(),
            buf.readLong(),
            buf.readLong(),
            readString(buf),
            buf.readBoolean(),
            buf.readBoolean(),
            readString(buf),
            buf.readVarInt(),
            buf.readLong(),
            buf.readVarInt(),
            buf.readBoolean(),
            DashboardSummary.read(buf),
            readList(buf, ListingView::read),
            readList(buf, CollectionView::read),
            MarketInsight.read(buf),
            readList(buf, ChartPoint::read),
            readList(buf, ChartPoint::read),
            readList(buf, NamedValue::read),
            readList(buf, NamedValue::read),
            readList(buf, NamedValue::read),
            readList(buf, NamedValue::read),
            readList(buf, NamedValue::read)
        );
    }

    public void write(RegistryFriendlyByteBuf buf) {
        writeString(buf, mode);
        buf.writeVarInt(page);
        buf.writeVarInt(maxPage);
        buf.writeVarInt(totalListings);
        writeString(buf, sort);
        writeString(buf, query);
        buf.writeLong(playerBalance);
        buf.writeLong(coinBalance);
        buf.writeLong(bankBalance);
        buf.writeLong(totalSpendableBalance);
        writeString(buf, activePaymentSource);
        buf.writeBoolean(hasBankCard);
        buf.writeBoolean(bankAvailable);
        writeString(buf, bankWarning);
        buf.writeVarInt(collectionCount);
        buf.writeLong(pendingProceeds);
        buf.writeVarInt(pendingPayoutCount);
        buf.writeBoolean(admin);
        summary.write(buf);
        writeList(buf, listings);
        writeList(buf, collections);
        priceCheck.write(buf);
        writeList(buf, volumeHistory);
        writeList(buf, averagePriceHistory);
        writeList(buf, flowBreakdown);
        writeList(buf, categoryBreakdown);
        writeList(buf, topItems);
        writeList(buf, topSellers);
        writeList(buf, topBuyers);
    }

    private static <T> List<T> readList(RegistryFriendlyByteBuf buf, Reader<T> reader) {
        int size = buf.readVarInt();
        List<T> values = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            values.add(reader.read(buf));
        }
        return values;
    }

    private static <T extends Writable> void writeList(RegistryFriendlyByteBuf buf, List<T> values) {
        buf.writeVarInt(values.size());
        for (T value : values) {
            value.write(buf);
        }
    }

    private static String readString(RegistryFriendlyByteBuf buf) {
        return buf.readUtf(32767);
    }

    private static void writeString(RegistryFriendlyByteBuf buf, String value) {
        buf.writeUtf(value == null ? "" : value);
    }

    private interface Reader<T> {
        T read(RegistryFriendlyByteBuf buf);
    }

    public interface Writable {
        void write(RegistryFriendlyByteBuf buf);
    }

    public record DashboardSummary(
        long totalActiveListings,
        long publicListings,
        long adminListings,
        long volume24h,
        long volume7d,
        long volume30d,
        long allTimeVolume,
        long serverSinkTotal,
        long playerToPlayerSales,
        long averageSalePrice,
        String mostTradedItem
    ) implements Writable {
        public static DashboardSummary empty() {
            return new DashboardSummary(0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, "unknown");
        }

        public static DashboardSummary read(RegistryFriendlyByteBuf buf) {
            return new DashboardSummary(
                buf.readLong(),
                buf.readLong(),
                buf.readLong(),
                buf.readLong(),
                buf.readLong(),
                buf.readLong(),
                buf.readLong(),
                buf.readLong(),
                buf.readLong(),
                buf.readLong(),
                readString(buf)
            );
        }

        @Override
        public void write(RegistryFriendlyByteBuf buf) {
            buf.writeLong(totalActiveListings);
            buf.writeLong(publicListings);
            buf.writeLong(adminListings);
            buf.writeLong(volume24h);
            buf.writeLong(volume7d);
            buf.writeLong(volume30d);
            buf.writeLong(allTimeVolume);
            buf.writeLong(serverSinkTotal);
            buf.writeLong(playerToPlayerSales);
            buf.writeLong(averageSalePrice);
            writeString(buf, mostTradedItem);
        }
    }

    public record ListingView(
        String id,
        String category,
        String sellerName,
        String itemStack,
        String itemId,
        String itemName,
        int itemCount,
        long price,
        long createdAt,
        long expiresAt,
        String timeRemaining,
        long lowestActivePrice,
        long average7d,
        int sold7d,
        String listingType,
        long startPrice,
        long buyoutPrice,
        long currentBid,
        long minIncrement,
        String highestBidderName
    ) implements Writable {
        public static ListingView read(RegistryFriendlyByteBuf buf) {
            return new ListingView(
                readString(buf),
                readString(buf),
                readString(buf),
                readString(buf),
                readString(buf),
                readString(buf),
                buf.readVarInt(),
                buf.readLong(),
                buf.readLong(),
                buf.readLong(),
                readString(buf),
                buf.readLong(),
                buf.readLong(),
                buf.readVarInt(),
                readString(buf),
                buf.readLong(),
                buf.readLong(),
                buf.readLong(),
                buf.readLong(),
                readString(buf)
            );
        }

        @Override
        public void write(RegistryFriendlyByteBuf buf) {
            writeString(buf, id);
            writeString(buf, category);
            writeString(buf, sellerName);
            writeString(buf, itemStack);
            writeString(buf, itemId);
            writeString(buf, itemName);
            buf.writeVarInt(itemCount);
            buf.writeLong(price);
            buf.writeLong(createdAt);
            buf.writeLong(expiresAt);
            writeString(buf, timeRemaining);
            buf.writeLong(lowestActivePrice);
            buf.writeLong(average7d);
            buf.writeVarInt(sold7d);
            writeString(buf, listingType);
            buf.writeLong(startPrice);
            buf.writeLong(buyoutPrice);
            buf.writeLong(currentBid);
            buf.writeLong(minIncrement);
            writeString(buf, highestBidderName);
        }
    }

    public record CollectionView(
        String id,
        String type,
        long amount,
        String itemStack,
        String itemName,
        String reason,
        long createdAt
    ) implements Writable {
        public static CollectionView read(RegistryFriendlyByteBuf buf) {
            return new CollectionView(
                readString(buf),
                readString(buf),
                buf.readLong(),
                readString(buf),
                readString(buf),
                readString(buf),
                buf.readLong()
            );
        }

        @Override
        public void write(RegistryFriendlyByteBuf buf) {
            writeString(buf, id);
            writeString(buf, type);
            buf.writeLong(amount);
            writeString(buf, itemStack);
            writeString(buf, itemName);
            writeString(buf, reason);
            buf.writeLong(createdAt);
        }
    }

    public record MarketInsight(
        String itemId,
        String itemName,
        long lastSalePrice,
        long average24h,
        long average7d,
        long average30d,
        long lowestActivePrice,
        int activeListingCount,
        int sold24h,
        int sold7d,
        String trend
    ) implements Writable {
        public static MarketInsight empty() {
            return new MarketInsight("", "Hold an item", 0L, 0L, 0L, 0L, 0L, 0, 0, 0, "unknown");
        }

        public static MarketInsight read(RegistryFriendlyByteBuf buf) {
            return new MarketInsight(
                readString(buf),
                readString(buf),
                buf.readLong(),
                buf.readLong(),
                buf.readLong(),
                buf.readLong(),
                buf.readLong(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                readString(buf)
            );
        }

        @Override
        public void write(RegistryFriendlyByteBuf buf) {
            writeString(buf, itemId);
            writeString(buf, itemName);
            buf.writeLong(lastSalePrice);
            buf.writeLong(average24h);
            buf.writeLong(average7d);
            buf.writeLong(average30d);
            buf.writeLong(lowestActivePrice);
            buf.writeVarInt(activeListingCount);
            buf.writeVarInt(sold24h);
            buf.writeVarInt(sold7d);
            writeString(buf, trend);
        }
    }

    public record ChartPoint(String label, long value, long secondaryValue) implements Writable {
        public static ChartPoint read(RegistryFriendlyByteBuf buf) {
            return new ChartPoint(readString(buf), buf.readLong(), buf.readLong());
        }

        @Override
        public void write(RegistryFriendlyByteBuf buf) {
            writeString(buf, label);
            buf.writeLong(value);
            buf.writeLong(secondaryValue);
        }
    }

    public record NamedValue(String label, long value, int count, String extra) implements Writable {
        public static NamedValue read(RegistryFriendlyByteBuf buf) {
            return new NamedValue(readString(buf), buf.readLong(), buf.readVarInt(), readString(buf));
        }

        @Override
        public void write(RegistryFriendlyByteBuf buf) {
            writeString(buf, label);
            buf.writeLong(value);
            buf.writeVarInt(count);
            writeString(buf, extra);
        }
    }
}
