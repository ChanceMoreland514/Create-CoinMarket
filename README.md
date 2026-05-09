# Create: CoinMarket

Create: CoinMarket is a server-authoritative live market and auction house for Minecraft NeoForge 1.21.1 SMPs, powered by Create: Numismatics physical coins and verified bank-card accounts.

## Migration Note

Versions before `1.1.1` used the internal development mod id `auctionhousejs`. Create: CoinMarket now uses `create_coinmarket`. On first launch, the mod attempts to copy the old config and database into the new paths without deleting the originals:

- old config: `config/auctionhousejs-common.toml`
- new config: `config/create_coinmarket-common.toml`
- old database: `world/serverconfig/auctionhousejs/auctionhouse.db`
- new database: `world/serverconfig/create_coinmarket/coinmarket.db`

If both old and new databases exist, Create: CoinMarket uses the new database and leaves the old one untouched.

## UI

The modern UI is a pure native NeoForge `Screen`, not a container-backed inventory screen. `/auction open` opens the CoinMarket dashboard without opening the player inventory, and the legacy chest menu is only used when `enableLegacyChestUi = true`.

The dashboard uses the configured dark navy palette:

- background `#07111F`
- panel `#0B1B2E`
- raised panel `#10243A`
- accent `#1E90FF`
- text `#FFFFFF`
- muted text `#B7C5D8`
- danger `#FF4D4D`
- success `#4DFF88`
- warning `#FFD166`

UI Lib was researched for NeoForge 1.21.1. The 1.21.1 NeoForge file found (`uilib-1.0.1-1.21.1-neoforge.jar`) exposed runtime/intermediary Minecraft names when consumed by this Mojmap NeoForge ModDev project, so UI Lib is not required.

Auction success, error, warning, and bank fallback messages are mirrored into bounded dashboard notifications while the screen is open. Toasts are anchored below the header so they do not overlap search or action controls.

## Install

Build:

```powershell
.\gradlew.bat clean build --warning-mode all
```

Install this jar on both server and clients:

```text
build/libs/create-coinmarket-1.2.0+mc1.21.1-neoforge.jar
```

Required on both server and clients:

- NeoForge 21.1.x
- Minecraft 1.21.1
- Create 6.0.x
- Create: Numismatics 1.0.20 or newer for Minecraft 1.21.1

Create and Create: Numismatics are declared as required mod dependencies, so missing installs fail through normal NeoForge dependency handling. Create: CoinMarket also uses a non-optional `1.2` custom payload protocol for client/server compatibility checks.

On server startup, Create: CoinMarket validates the required mods, configured Numismatics coin item ids, SQLite JDBC driver, database directory write access, and database open/migration path. Hard failures are reported as `Create: CoinMarket failed startup validation: ...` in the log instead of surfacing later as vague runtime errors. If Numismatics bank/card reflection cannot be verified but physical coins are enabled, the mod logs the reason and uses the physical coin economy.

## Files

- Config: `config/create_coinmarket-common.toml`
- Server config: `world/serverconfig/create_coinmarket-server.toml`
- Database: `world/serverconfig/create_coinmarket/coinmarket.db`
- Backups: `world/serverconfig/create_coinmarket/backups/`

## Database

SQLite is the default and bundled database mode. The generated server config supports:

- `database.enabled`
- `database.mode = sqlite | mysql`
- `database.host`, `database.port`, `database.name`, `database.schema`
- `database.username`, `database.password`
- `database.jdbcUrl`
- `database.tablePrefix`
- `database.poolSize`
- `database.connectionTimeoutSeconds`
- `database.autoCreateTables`
- `database.autoMigrate`
- `database.logSqlErrors`
- `database.sqlitePath`

When `database.mode=sqlite`, tables and indexes are auto-created in `world/serverconfig/create_coinmarket/coinmarket.db`. MySQL settings are present for hosted SQL configuration, but 1.2.0 does not bundle a MySQL JDBC driver; using `database.mode=mysql` fails startup clearly until a compatible driver or integration is supplied.

## Commands

Player:

- `/auction open`
- `/auction open all`
- `/auction open admin`
- `/auction open public`
- `/auction sell <price>`
- `/auction sell <price> <quantity>`
- `/auction sell hand <price>`
- `/auction auction hand <startBid> <durationHours> [buyout]`
- `/auction bid <listingId> <amount>`
- `/auction buyout <listingId>`
- `/auction cancel <listingId>`
- `/auction collect`
- `/auction collectmoney`
- `/auction balance`
- `/auction history`
- `/auction pricecheck`
- `/auction market <item>`

Admin permission level 2:

- `/auction admin addhand <price>`
- `/auction admin add <item> <price>`
- `/auction admin remove <listingId>`
- `/auction admin list`
- `/auction admin clear <admin|public|all>`
- `/auction admin reload`
- `/auction admin save`
- `/auction admin inspect <listingId>`
- `/auction admin repair`
- `/auction resolveexpired`
- `/auction admin endauction <listingId>`
- `/auction admin refundlisting <listingId>`
- `/auction admin returnlisting <listingId>`
- `/auction admin viewclaims <player>`
- `/auction admin forceclaim <player> <claimId>`
- `/auction admin numismaticscheck`

## Dashboard

The browser syncs prepared DTOs from the server. It does not query SQLite client-side and does not send the whole database. It includes dashboard summaries, listing cards, search, sorting, a Sell panel, collection/proceeds claims, economy charts, leaderboards, and admin controls.

All buy, bid, buyout, sell, cancel, collect, and admin actions are validated on the server.

## Auctions And Payouts

Listings can be fixed-price or auction listings.

- Fixed-price listings sell immediately at the listed price.
- Auction listings reserve each highest bid in escrow.
- A new bid must meet the current bid plus the configured minimum increment.
- Outbid funds become pending proceeds/refunds for the previous bidder.
- Buyout ends an auction immediately when a buyout price exists.
- Expired auctions with bids create an item claim for the winner and pending proceeds for the seller.
- Expired auctions without bids return the item to the seller collection.

Seller proceeds and bidder refunds use the payout system. If direct auto-credit is configured and succeeds, funds are credited immediately. Otherwise they remain safely claimable through the Collection page or `/auction collectmoney`.

## Economy

Default coin values are configurable and use verified Numismatics item ids:

- `numismatics:spur=1`
- `numismatics:bevel=8`
- `numismatics:sprocket=16`
- `numismatics:cog=64`
- `numismatics:crown=512`
- `numismatics:sun=4096`

Bank integration uses the verified Create: Numismatics API surface from 1.0.20:

- bank manager: `dev.ithundxr.createnumismatics.Numismatics.BANK`
- accounts: `GlobalBankManager#getAccount`
- balances: `BankAccount#getBalance`, `deposit`, `deduct`
- cards: `CardItem#get(ItemStack)` and the `card_account_id` component

Payment source is controlled by `preferredPaymentSource`:

- `coins_only`
- `bank_only`
- `bank_then_coins`
- `coins_then_bank`

If a bound card is present, the dashboard header and `/auction balance` show physical coins, bank/card balance, total spendable balance, and the active payment source. Seller payouts prefer a resolvable Numismatics bank account when enabled; otherwise they are stored as pending proceeds for `/auction collectmoney`.

## Dupe Safety

- Purchases and collection claims run in SQLite transactions.
- The server re-reads and validates listings before charging buyers.
- Buyer inventory room is checked before withdrawing coins.
- Expired/canceled public listing items are returned through collection rows.
- Admin-removed unsold listings return the serialized item to the seller mailbox.
- Active auction bids are refunded to pending proceeds before an auction is canceled or returned.
- Completed auction winner items are delivered through collection rows if immediate delivery is unsafe or impossible.
- GUI data is treated as stale; server state wins.
- Admin actions are audit logged.

## Troubleshooting

- If listings do not appear, run `/auction admin repair`.
- If players cannot pay, confirm configured currency item ids exist in the registry.
- Use `/auction balance` to inspect physical and bank/card balance.
- Use `/auction admin numismaticscheck` to inspect Numismatics detection, coin ids, card ids, and bank API availability.
- If the modern UI is undesirable, set `enableLegacyChestUi = true`.
- Before database maintenance, use the Admin page backup button first.
