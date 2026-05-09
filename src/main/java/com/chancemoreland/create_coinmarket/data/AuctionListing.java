package com.chancemoreland.create_coinmarket.data;

import java.util.UUID;

public class AuctionListing {
    public static final String CATEGORY_ADMIN = "admin";
    public static final String CATEGORY_PUBLIC = "public";

    public static final String STATUS_ACTIVE = "active";
    public static final String STATUS_SOLD = "sold";
    public static final String STATUS_CANCELED = "canceled";
    public static final String STATUS_EXPIRED = "expired";
    public static final String STATUS_REMOVED = "removed";
    public static final String STATUS_ENDED = "ended";

    public static final String TYPE_FIXED_PRICE = "fixed_price";
    public static final String TYPE_AUCTION = "auction";

    public String id;
    public String category;
    public UUID sellerUuid;
    public String sellerName;
    public String itemStack;
    public String itemId;
    public int itemCount;
    public long price;
    public String listingType = TYPE_FIXED_PRICE;
    public long startPrice;
    public long buyoutPrice;
    public long currentBid;
    public UUID highestBidderUuid;
    public String highestBidderName;
    public long minIncrement;
    public long startsAt;
    public long endsAt;
    public long updatedAt;
    public long createdAt;
    public long expiresAt;
    public String status;
    public UUID buyerUuid;
    public String buyerName;
    public long soldAt;
    public String source;
    public boolean infiniteAdminListing;
    public int version;

    public boolean isAdmin() {
        return CATEGORY_ADMIN.equals(category);
    }

    public boolean isPublic() {
        return CATEGORY_PUBLIC.equals(category);
    }

    public boolean isActive(long now) {
        return STATUS_ACTIVE.equals(status) && (expiresAt <= 0L || expiresAt > now);
    }

    public boolean isAuction() {
        return TYPE_AUCTION.equals(listingType);
    }

    public long displayPrice() {
        return isAuction() ? Math.max(startPrice, currentBid) : price;
    }

    public long effectiveEndsAt() {
        return endsAt > 0L ? endsAt : expiresAt;
    }

    public boolean expires() {
        return effectiveEndsAt() > 0L;
    }
}
