# Economy (feature `economy`, package `win.vampyuyu.survival.economy`)

Skyblock coins, the bank, NPC merchants, the Bazaar and the Auction House. Contract: `docs/skyblock-design.md`.
All amounts are stored as `long` cents (1 coin = 100 cents, `Money`); the public `EconomyApi` uses `double` coins.

## What players get

* **Purse**: `/bal [player]` (`/coins`, `/purse`). Never negative, capped by `currency.max-balance`; coins over the cap are refused, never destroyed.
* **/pay <player> <amount>**: amounts like `1500`, `1.5k`, `2m`. Over `pay.confirm-over` it needs `/pay confirm` within 30 s.
  Cooldown, per-day send limit (anti alt funnelling), optional tax. `/baltop [page]` is cached (`baltop.cache-seconds`).
* **Death**: killed by a player, the victim loses `death.percent` of the purse (max `death.max`, default 50 percent, up to 1M),
  the bank is never touched; coins are destroyed unless `death.drop-to-killer`. A "coins lost" message is shown.
* **Bank** (`/bank`): GUI with deposit/withdraw (all, half), tier upgrades (capacity), history. Interest every
  `bank.interest-period-hours` of REAL time, capped per period by the tier, also for the time spent offline (computed lazily the next
  time the account is touched, at most `bank.max-catchup-periods` periods).
* **NPC merchants**: ten shops (Farmer, Miner, Lumberjack, Fisherman, Mob Hunter, Alchemist, Adventurer, Weaponsmith, Builder, Rancher)
  with buy and sell prices from `economy.yml` (vanilla items; custom ids are skipped until another feature registers them).
  Each shop has a "Sell everything" button and a **buyback** list (last sold items, recoverable for `buyback.seconds` at the price paid).
  Optional **sell pressure**: every item sold to an NPC lowers that item's sell price server-wide, the effect decays (half-life).
* **Bazaar** (`/bz`): instant buy/sell, buy orders, sell orders, order book per product with price-time priority, escrow, claim,
  six categories, search, 24h average/volume and a price-history sparkline. Min tick 0.1 coin, tax on sales, order limits,
  self-trade refused (instant and resting orders), price band against absurd manipulation prices. Items are identified by registry key
  (vanilla `WHEAT` or custom id), only unmodified ("clean") stacks trade, and goods are always re-created from the key.
* **Auction House** (`/ah`): auctions and Buy It Now, listing fee scaled by duration (burned), bids with a minimum increment and
  anti-snipe extension, categories, search, sort, "my auctions", claim, expired/cancelled return, sale history ("recent price").
  The item is stored with `ItemStack.serializeAsBytes` (base64 `b:`; the test mock falls back to a YAML form `y:`), so enchantments,
  names, custom item data and shulker contents survive.

## Commands and permissions

| Command | Permission | Default |
|---|---|---|
| `/balance [player]` (`/bal`, `/coins`, `/purse`) | `vampyuyu.sb.economy.balance` (`.balance.others` for others) | TRUE (both) |
| `/pay`, `/baltop` | `vampyuyu.sb.economy.pay`, `.baltop` | TRUE |
| `/bank` | `vampyuyu.sb.economy.bank` | TRUE |
| `/bz` (`/bazaar`) | `vampyuyu.sb.economy.bazaar` | TRUE |
| `/ah` (`/auctionhouse`, `/auction`) | `vampyuyu.sb.economy.auction` | TRUE |
| `/shop [merchant]` (remote shop) | `vampyuyu.sb.economy.shop.remote` | OP |
| `/sellall` | `vampyuyu.sb.economy.sellall` | OP |
| `/sbnpc create/remove/list/shops/tp/move/rename/shop/refresh` | `vampyuyu.sb.economy.admin` | OP |
| `/ecoadmin give/take/set/reset <player> [amount]` | `vampyuyu.sb.economy.admin` | OP |
| `/economy audit / ledger <player> [n] / reload` | `vampyuyu.sb.economy.admin` | OP |

Players reach shops through the NPCs (right-click). Usage: `/bz buy|sell <item> <amount|all>`, `/bz order buy|sell <item> <amount> <price>`,
`/bz orders|claim|cancel <id>|price <item>`; `/ah sell <price> [bin] [hours]` (holding the item), `/ah bid <id> <amount>`,
`/ah buy <id>`, `/ah mine|claim|cancel <id>|history|browse [category]|search <text>`.

## Config (`economy.yml`, `core.featureConfig("economy")`, `/economy reload` re-reads shops, prices, bank, bazaar, auction)

`currency`, `pay`, `death`, `baltop`, `safety`, `bank` (tiers), `sell`, `buyback`, `pressure`, `shops.<id>` (name, npc-name, icon, profession,
npc-type, items with `buy`/`sell`), `bazaar` (tax-percent, max-orders, max-amount, max-price, price-band, history-hours, persist-delay-ticks,
`categories.<farming|mining|woods|combat|fishing|misc>` product lists; UPPER CASE vanilla, lower case custom ids such as
`minion_enchanted_coal`, unknown ids skipped), `auction` (max-listings, min/max-price, tax-percent, bid-increment-*, anti-snipe-*, max-extensions,
durations-hours, listing-fee.*, history-size, blocked-materials, blocked-items, max-item-chars, persist-delay-ticks).
`currency.ledger-file` needs a restart.

## Data layout

* `players/<uuid>.json` section `economy` (`EconomyData`): `cents`, `bankCents`, `bankTier`, interest bookkeeping, `ledger` ring (50),
  `bankHistory`, daily pay counters. All changes go through `profiles.edit(...)` (atomic, any thread, offline OK).
* `economy/bazaar.json` (whole market state: orders, per-order claims, price history), `economy/auctions.json` (listings, refunds, sales),
  `economy/pressure.json`, `economy/npcs.json`, optional `economy/ledger/<day>.log`.

## API and events

* Publishes `EconomyApi` (`coins`, `has`, `take`, `add`, `addTransactionListener`) and fires `CoinsChangeEvent` for every purse change
  (async when off-thread) with the reason string (`bazaar:buy:WHEAT`, `auction:bid:12`, `shop:sell:miner`, `pay:...`, `bank:...`, `death`...).
* Consumes `WingsApi` (lazily): Wings cannot be auctioned. Nothing else from other features.
* Custom items of other features are found with `core.items()` ids at use time.

## Crash and shutdown model (write-ahead, `DurableFile`)

Bazaar and Auction House keep their whole state in memory (main thread only) and in ONE JSON file each, written atomically (temp + atomic move,
off the main thread, coalesced over `persist-delay-ticks`). Rules:

1. **Value into the market** (coins/items escrowed): the player's side is made durable first (item removed from the inventory then
   `player.saveData()`, the profile flushed by the barrier that runs before every market write), then the market file records the escrow.
2. **Value out of the market** (claims): the claim is removed from the market state and the file is written FIRST; the player is credited
   only in the callback once the file is durable. A failed write puts the claim back (`restore`); a player who left meanwhile gets it back too.
3. **Clean stop**: `close()` first sets a `closing` flag (a claim callback that fires afterwards delivers nothing), puts every unfinished claim
   back into the state, then writes the final file synchronously. Nothing is lost or duplicated (tests:
   `aClaimThatCloseInterruptsIsNeitherLostNorDelivered`, `aClaimInterruptedByCloseIsKeptNotDuplicated`).
4. **Hard crash / kill -9 / power loss**: honest statement, the guarantee is "never duplicated", NOT "never lost". Between step 1 and the market
   file reaching disk (default 10 ticks), a crash can lose an escrowed item or coins (they left the player but the market never recorded them).
   Between a claim's file write and the player's profile save (profiles are saved at the barrier of the next write, on quit, every 5 minutes), a crash
   can pay a claim that the profile did not persist, i.e. the player loses that credit, never gains it twice. To shrink the window set
   `bazaar.persist-delay-ticks` / `auction.persist-delay-ticks` to 1 and keep `safety.save-player-data: true`.
5. `/economy audit` recomputes purses, banks, bazaar and auction escrow and checks the per-player ledger chain; it reports problems, it does not fix them.

## Known gaps / NOT verified in game

* NOT verified on a real Paper 26.3 server or client: all menus (only built in MockBukkit, `MenuSmokeTest`), item tooltips, the pack icons in game
  (checked as a contact sheet only, `art/economy_icons.py --sheet`), NPC entities (villager type, profession, persistence across chunk unload),
  sound, `ItemStack.serializeAsBytes` round trip (MockBukkit falls back to the YAML form, so the `b:` path is untested here), `player.saveData()` timing.
* Custom items of other features are not listed in the shipped shops; the Bazaar lists only guess ids (`enchanted_*`, `minion_enchanted_*`,
  `minion_super_*`), unknown ones are skipped. Check ids when the items/minions features are final.
* The Bazaar has no NPC liquidity: with no sell orders there is nothing to buy instantly. Seed liquidity is a design decision for the owner.
* No cross-server economy (single survival server, profiles are local files). Concurrent edits from two servers would not be safe.
* Price entry for custom bazaar orders is by command (no sign/anvil input); the menus suggest a price one tick better than the book.
* `/sellall` and `/shop` are OP by default (the intended path is the NPC); change the permission to open them.
* Bazaar order history is not shown per order; only the 24h aggregates and a sparkline.
* The auction browse menu lists up to the whole book on one pager (fine up to a few thousand listings; no server-side paging).
* Auction listings take the whole stack in the main hand (no partial amount).

## NEEDS

* Nothing blocking. Optional: a `Stat`-free hook is not needed. If the items feature wants Bazaar price guidance for its items, it can read
  `economy.yml` categories; no API change requested.
