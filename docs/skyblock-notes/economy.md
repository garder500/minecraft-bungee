# Economy (feature `economy`, package `win.vampyuyu.survival.economy`)

Skyblock coins, the bank, NPC merchants, the Bazaar and the Auction House. Contract: `docs/skyblock-design.md`.
All amounts are stored as `long` cents (1 coin = 100 cents, `Money`); the public `EconomyApi` uses `double` coins.

## What players get

* **Purse**: `/bal [player]` (`/coins`, `/purse`). Never negative, capped by `currency.max-balance`; coins over the cap are refused, never destroyed.
* **/pay <player> <amount>**: amounts like `1500`, `1.5k`, `2m`. Over `pay.confirm-over` it needs `/pay confirm` within 30 s.
  Cooldown, per-day send limit (anti alt funnelling), optional tax. `/baltop [page]` is cached (`baltop.cache-seconds`).
* **Death**: killed by a player, the victim loses `death.percent` of the purse (max `death.max`, default 50 percent, up to 1M),
  the bank is never touched; coins are destroyed unless `death.drop-to-killer`. A "coins lost" message is shown.
* **Bank** (`/bank`): GUI with deposit/withdraw (all, half, or click "an amount" and type it in chat), tier upgrades (capacity), history.
  Interest every `bank.interest-period-hours` of REAL time (default 24 h, 0.5 to 1 percent per period by tier, at most 3 periods of offline
  catch-up), capped per period by the tier, computed lazily the next time the account is touched. The defaults are deliberately low: the
  first version paid about 6 to 9 percent a day and would have out-earned playing. Watch `/economy audit` (bank:interest in the flow table)
  before raising it.
* **NPC merchants**: ten shops (Farmer, Miner, Lumberjack, Fisherman, Mob Hunter, Alchemist, Adventurer, Weaponsmith, Builder, Rancher)
  with buy and sell prices from `economy.yml` (vanilla items; custom ids are skipped until another feature registers them).
  Each shop has a "Sell everything" button and a **buyback** list (last sold items, recoverable for `buyback.seconds` at the price paid).
  Optional **sell pressure**: every item sold to an NPC lowers that item's sell price server-wide, the effect decays (half-life).
  Compacted minion items share the pool of their raw item, weighted by the compaction ratio (`pressure.compact-prefixes`), so selling
  133 enchanted wheat counts as 20000 raw wheat and neither form dodges the other's price drop.
  Priced custom goods: compacted minion items, dungeon loot (`dungeon_*`), slayer drops and boss materials (sell only).
* **Bazaar** (`/bz`): instant buy/sell, buy orders, sell orders, order book per product with price-time priority, escrow, claim,
  six categories, search, 24h average/volume and a price-history sparkline. Min tick 0.1 coin, tax on sales, order limits,
  self-trade refused (instant and resting orders), price band against absurd manipulation prices. A product that never traded is
  anchored to the NPC tables (geometric mean of the best NPC sell price and the cheapest NPC buy price, or whichever exists), so the first
  order cannot set a silly reference; the menus suggest prices clamped between the NPC floor and ceiling. Owners of resting orders are told
  in chat (with a sound) when their order fills, and on join when something waits in `/bz claim`. "Sell inventory" (menu button and
  `/bz sellinv`) and "Sell all you carry" sell as much as the buy orders can take without ever using your own orders. Items are identified by registry key
  (vanilla `WHEAT` or custom id), only unmodified ("clean") stacks trade, and goods are always re-created from the key.
* **Auction House** (`/ah`): auctions and Buy It Now, listing fee scaled by duration (burned), bids with a minimum increment and
  anti-snipe extension, categories, search, sort, a filter (everything / Buy It Now only / auctions only), "my auctions", claim,
  expired/cancelled return, sale history ("recent price"). Wings and blocked items are refused also INSIDE shulker boxes, chests and
  bundles, to a nesting depth of 8 (deeper, or an unreadable container, is refused).
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

Menus use click-then-type input (`Prompts`): after clicking "custom price", "search", "an amount" the next chat line (30 s, or `cancel`)
is the answer and is not broadcast (a timeout or a newer prompt just drops it; only typing `cancel` reopens the menu). Vanilla sounds play on success, error and notices.

Players reach shops through the NPCs (right-click). Usage: `/bz buy|sell <item> <amount|all>`, `/bz order buy|sell <item> <amount> <price>`,
`/bz orders|claim|cancel <id>|price <item>|sellinv`; `/ah sell <price> [bin] [hours]` (holding the item), `/ah bid <id> <amount>`,
`/ah buy <id>`, `/ah mine|claim|cancel <id>|history|browse [category]|search <text>`.

## Config (`economy.yml`, `core.featureConfig("economy")`, `/economy reload` re-reads shops, prices, bank, bazaar, auction)

`currency`, `pay`, `death`, `baltop`, `safety`, `bank` (tiers), `sell`, `buyback`, `pressure` (soft-cap, half-life-hours, min-multiplier,
`compact-prefixes`), `shops.<id>` (name, npc-name, icon, profession,
npc-type, items with `buy`/`sell`), `bazaar` (tax-percent, max-orders, max-amount, max-price, price-band, history-hours, persist-delay-ticks,
`categories.<farming|mining|woods|combat|fishing|misc>` product lists; UPPER CASE vanilla, lower case custom ids such as
`minion_enchanted_coal`, unknown ids skipped), `auction` (max-listings, min/max-price, tax-percent, bid-increment-*, anti-snipe-*, max-extensions,
durations-hours, listing-fee.*, history-size, blocked-materials, blocked-items, max-item-chars, persist-delay-ticks).
`currency.ledger-file` needs a restart.

## Data layout

* `players/<uuid>.json` section `economy` (`EconomyData`): `cents`, `bankCents`, `bankTier`, interest bookkeeping, `ledger` ring (50),
  `bankHistory`, daily pay counters. All changes go through `profiles.edit(...)` (atomic, any thread, offline OK).
* `economy/bazaar.json` (whole market state: orders, per-order claims, price history), `economy/auctions.json` (listings, refunds, sales),
  `economy/pressure.json`, `economy/flows.json` (faucet and sink table), `economy/npcs.json`, optional `economy/ledger/<day>.log`.

## API and events

* Publishes `EconomyApi` (`coins`, `has`, `take`, `add`, `addTransactionListener`) and fires `CoinsChangeEvent` for every purse change
  (async when off-thread) with the reason string (`bazaar:buy:WHEAT`, `auction:bid:12`, `shop:sell:miner`, `pay:...`, `bank:...`, `death`...).
* Consumes `WingsApi` (lazily): Wings cannot be auctioned (also not hidden in a container). Nothing else from other features.
* Custom items of other features are found with `core.items()` ids at use time. A custom id only trades when a shop table or a bazaar
  category lists it (`ItemKeys.allowCustom`, rebuilt on enable and reload), because a traded item is re-created from its id and would lose
  any state it carries beyond that id. Never list an item whose value is in per-item data (rolled stats, custom enchants): the custom `enchanted_book` of the items feature
  stores its enchantment in item data, so it is deliberately NOT a bazaar product (books go through the Auction House).

## Crash and shutdown model (write-ahead, `DurableFile`)

Bazaar and Auction House keep their whole state in memory (main thread only) and in ONE JSON file each, written atomically (temp + atomic move,
off the main thread, coalesced over `persist-delay-ticks`). Before each snapshot the MAIN thread queues every dirty player profile
(`PlayerProfiles.saveDirty`, so features that edit their own section on the main thread never race a serialiser on another thread);
the IO thread then waits for those writes (`awaitWrites`) and only afterwards writes the market file. Rules:

1. **Value into the market** (coins/items escrowed, buy-order escrow, the buyer's coins of an instant buy): the player's side is made
   durable first (item removed from the inventory then `player.saveData()`, the profile flushed by the barrier that precedes every
   market write), then the market file records the escrow.
2. **Value out of the market** (claims, the proceeds of an instant sell, the goods of an instant buy): the change is committed in memory
   and the file is written FIRST; the player is credited / given the goods only in the callback once the file is durable (`park`). Goods that
   no longer fit and coins over the purse cap become a new claim (never dropped on the island). A failed write puts the value back as a
   claim; a player who left meanwhile gets it back as a claim too.
3. **Clean stop**: `close()` first sets a `closing` flag (a callback that fires afterwards delivers nothing), puts every unfinished
   hand-over (claims AND instant-trade proceeds/goods) back into the state, then writes the final file synchronously. Nothing is lost or
   duplicated (tests in `InstantTradeDurabilityTest`, `BazaarServiceTest`).
4. **Hard crash / kill -9 / power loss**: the guarantee is "never duplicated", NOT "never lost". Between the player's side being saved and
   the market file reaching disk (default 10 ticks), a crash can lose an escrowed item or coins, and an instant sell can lose the items (they left
   the saved inventory but the bid is still alive in the old file: the buyer keeps his escrow, the seller gets neither). It cannot pay the
   seller while the old file still shows the bid, because the coins are credited only after the new file is on disk. An instant buy takes the
   coins first; a crash before the file is durable loses those coins but never gives the items twice. Between the file write and the profile
   save of the receiver (barrier of the next write, quit, 5 minutes), a crash can lose a delivered credit, never repeat it. To shrink the windows
   set `bazaar.persist-delay-ticks` / `auction.persist-delay-ticks` to 1 and keep `safety.save-player-data: true`.
5. `/economy audit` recomputes purses, banks, bazaar and auction escrow, checks every ledger chain AND the lifetime invariant
   `purse = totalEarned - totalSpent` (reaches back past the 50 line ledger), copies loaded profiles under their lock before scanning, and
   prints the server-wide **faucet and sink table** (`economy/flows.json`: coins created and destroyed per ledger reason group such as
   `shop:sell`, `bank:interest`, `admin:give`; market and bank movements are marked as transfers) plus the coins burned by bazaar tax and
   auction sale tax (listing fees are purse debits `auction:fee`, so they are already in the table and only listed separately). Use it to tune prices and the bank from real data. It reports problems, it does not fix them.

## Known gaps / NOT verified in game

* NOT verified on a real Paper 26.3 server or client: all menus (only built in MockBukkit, `MenuSmokeTest`), item tooltips, the pack icons in game
  (checked as a contact sheet only, `art/economy_icons.py --sheet`), NPC entities (villager type, profession, persistence across chunk unload),
  the vanilla sounds, the chat prompt on a real `AsyncChatEvent` (the logic is tested through `Prompts.handle`; Paper's event is not
  constructed in tests), `ItemStack.serializeAsBytes` round trip (MockBukkit falls back to the YAML form, so the `b:` path is untested here),
  `player.saveData()` timing, a real crash between the market file and a profile save.
* Custom item ids are taken from the sibling branches (read-only): `minion_enchanted_<material>` / `minion_super_<material>` (feature minions,
  ratio 150 here vs 160 in minions.yml; if `minions.yml` lowers its ratios below 150 these prices must be lowered too, `ShippedPricesTest`
  checks the 150 rule, not the minions config), `dungeon_*` and the boss / slayer material ids (`BossItems`). Priced: sell only, no buy price, and
  only materials. NOT priced on purpose: summon items (`void_sigil`...), weapons (`void_fang`...), the Crypt Merchant consumables
  (`dungeon_spirit_leaf`, `dungeon_healing_flask`, sold by the dungeon itself) and `wing_core` (its use by the wings feature is unknown; if it is
  the bound Wings input put it in `auction.blocked-items`). The sell prices are a first guess; tune them with the flow table.
* The pooled pressure weights (150 and 22500) are config, not derived from the shop prices; they must follow the compaction ratio.
* The audit's lifetime check assumes every purse change went through `applyCredit` / `applyDebit` (true for this feature and `EconomyApi`);
  a profile edited by hand in the JSON file is reported as a problem on purpose.
* No NPC market maker: with no sell orders there is nothing to buy instantly. The price band is now seeded from the NPC tables (so prices are
  sane and suggested), but liquidity itself is a design decision for the owner (a capped virtual bid/ask would be a coin faucet).
* No coin gain flash on the action bar from `CoinsChangeEvent`: only the items feature may write the action bar (design rule); it can show
  one by listening to `CoinsChangeEvent`.
* No cross-server economy (single survival server, profiles are local files). Concurrent edits from two servers would not be safe.
* `/sellall` and `/shop` are OP by default (the intended path is the NPC); change the permission to open them.
* Bazaar order history is not shown per order; only the 24h aggregates and a sparkline.
* The auction browse menu lists up to the whole book on one pager (fine up to a few thousand listings; no server-side paging).
* Auction listings take the whole stack in the main hand (no partial amount).
* Instant trades now take about `persist-delay-ticks` plus one fsync (roughly half a second) to complete: the result message appears when the
  coins or goods arrive, not at the click.

## NEEDS

* A per-player save in `PlayerProfiles` (`save(UUID)`) would let the markets flush only the players involved instead of every dirty profile
  on the main thread before each market write (`saveDirty` serialises all dirty profiles; cheap with few players, measurable with many).
