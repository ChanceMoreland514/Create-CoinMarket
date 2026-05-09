# Create: CoinMarket

**Create: CoinMarket** is a server-authoritative live market and auction-house mod for **Minecraft NeoForge 1.21.1** SMP servers. It adds fixed-price listings, timed auctions, collection claims, market dashboards, economy analytics, and Create: Numismatics-powered payments using physical coins and verified bank-card accounts.

## Release: 1.2.0 for Minecraft 1.21.1

This release focuses on the production-ready CoinMarket experience: a native NeoForge dashboard UI, safer server-side auction handling, SQLite-backed persistence, Numismatics bank/card integration, and migration support from the earlier internal `auctionhousejs` mod id.

### Compatibility

| Requirement | Version |
|---|---:|
| Minecraft | `1.21.1` |
| NeoForge | `21.1.x` |
| Create | `6.0.x` |
| Create: Numismatics | `1.0.20+` |
| Create: CoinMarket | `1.2.0` |

Create and Create: Numismatics are required dependencies. Missing dependencies fail through NeoForge's normal dependency handling.

## Download / Build

Build the mod with Gradle:

```powershell
.\gradlew.bat clean build --warning-mode all
```

The release jar will be created at:

```text
build/libs/create-coinmarket-1.2.0+mc1.21.1-neoforge.jar
```

Install the jar on **both the server and every client**.

## What This Mod Adds

- Server-authoritative market and auction house
- Fixed-price listings and timed auction listings
- Native NeoForge dashboard UI opened with `/auction open`
- Sell panel, listing browser, collection page, admin controls, market summaries, charts, and leaderboards
- Create: Numismatics physical coin support
- Verified Numismatics bank-card account support
- SQLite-backed database with automatic table creation and migration
- Claim-safe item delivery and payout handling
- Admin audit logging and repair tools
- Migration support from older `auctionhousejs` paths

## Commands

### Player Commands

```text
/auction open
/auction open all
/auction open admin
/auction open public
/auction sell <price>
/auction sell <price> <quantity>
/auction sell hand <price>
/auction auction hand <startBid> <durationHours> [buyout]
/auction bid <listingId> <amount>
/auction buyout <listingId>
/auction cancel <listingId>
/auction collect
/auction collectmoney
/auction balance
/auction history
/auction pricecheck
/auction market <item>
```

### Admin Commands

Requires permission level `2`.

```text
/auction admin addhand <price>
/auction admin add <item> <price>
/auction admin remove <listingId>
/auction admin list
/auction admin clear <admin|public|all>
/auction admin reload
/auction admin save
/auction admin inspect <listingId>
/auction admin repair
/auction resolveexpired
/auction admin endauction <listingId>
/auction admin refundlisting <listingId>
/auction admin returnlisting <listingId>
/auction admin viewclaims <player>
/auction admin forceclaim <player> <claimId>
/auction admin numismaticscheck
```

## Market Dashboard

`/auction open` opens the CoinMarket dashboard. The modern dashboard is a native NeoForge `Screen`, not a chest-style inventory menu.

The dashboard includes:

- Browse, search, and sort listings
- Fixed-price buying
- Auction bidding and buyout actions
- Sell panel
- Collection and proceeds claims
- Economy summaries and charts
- Leaderboards
- Admin controls
- Live notifications for success, error, warning, and bank fallback messages

The legacy chest UI is still available by setting:

```toml
enableLegacyChestUi = true
```

## Auctions and Payouts

Create: CoinMarket supports two listing types:

- **Fixed-price listings** sell immediately at the listed price.
- **Auction listings** reserve the current highest bid in escrow until the auction ends.

Auction behavior:

- New bids must meet the current bid plus the configured minimum increment.
- Outbid players receive pending proceeds/refunds.
- Buyout ends the auction immediately when a buyout price exists.
- Expired auctions with bids create an item claim for the winner and seller proceeds.
- Expired auctions without bids return the item to the seller collection.

Seller proceeds and bidder refunds are handled safely through the payout system. If direct auto-credit succeeds, funds are credited immediately. Otherwise, funds remain claimable through the Collection page or:

```text
/auction collectmoney
```

## Economy Integration

Default Numismatics coin values are configurable:

```text
numismatics:spur=1
numismatics:bevel=8
numismatics:sprocket=16
numismatics:cog=64
numismatics:crown=512
numismatics:sun=4096
```

Supported payment source modes:

```text
coins_only
bank_only
bank_then_coins
coins_then_bank
```

When a bound Numismatics card is present, the dashboard header and `/auction balance` show:

- Physical coin balance
- Bank/card balance
- Total spendable balance
- Active payment source

If Numismatics bank or card reflection cannot be verified but physical coins are enabled, Create: CoinMarket logs the reason and falls back to the physical coin economy.

## Files and Storage

Create: CoinMarket stores config and data in these paths:

```text
config/create_coinmarket-common.toml
world/serverconfig/create_coinmarket-server.toml
world/serverconfig/create_coinmarket/coinmarket.db
world/serverconfig/create_coinmarket/backups/
```

SQLite is the default bundled database mode. MySQL configuration options are present, but this release does **not** bundle a MySQL JDBC driver. Setting `database.mode=mysql` will fail startup clearly until a compatible driver or integration is supplied.

## Startup Validation

On server startup, Create: CoinMarket validates:

- Required mod dependencies
- Configured Numismatics coin item ids
- SQLite JDBC availability
- Database directory write access
- Database open and migration path

Hard failures are reported in the log as:

```text
Create: CoinMarket failed startup validation: ...
```

## Safety Features

- Purchases and collection claims run inside SQLite transactions.
- Listings are re-read and validated server-side before buyers are charged.
- Buyer inventory space is checked before coins are withdrawn.
- Expired or canceled public listing items are returned through collection rows.
- Admin-removed unsold listings return serialized items to the seller mailbox.
- Active auction bids are refunded before an auction is canceled or returned.
- Completed auction winner items are delivered through collection rows if immediate delivery is unsafe.
- GUI data is treated as stale; server state always wins.
- Admin actions are audit logged.

## Troubleshooting

| Problem | Fix |
|---|---|
| Listings do not appear | Run `/auction admin repair` |
| Players cannot pay | Confirm configured currency item ids exist in the registry |
| Need to inspect balance | Run `/auction balance` |
| Numismatics detection issues | Run `/auction admin numismaticscheck` |
| Modern UI is not desired | Set `enableLegacyChestUi = true` |
| Database maintenance needed | Use the Admin page backup button first |

## Release Notes

### 1.2.0

- Added production-ready native NeoForge CoinMarket dashboard
- Added fixed-price and auction listing flows
- Added server-prepared market DTO syncing instead of client-side database access
- Added dashboard notifications for action results and bank fallback messages
- Added SQLite database persistence and migration handling
- Added Numismatics physical coin and verified bank-card payment support
- Added collection-safe item and payout claiming
- Added admin repair, inspection, refund, return, force-claim, and diagnostics commands
- Added startup validation for dependencies, currency ids, database access, and migration path

## License

See `LICENSE`.
