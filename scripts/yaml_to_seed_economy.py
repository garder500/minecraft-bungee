#!/usr/bin/env python3
"""One-off conversion of the former economy.yml into db/seed/economy.sql (typed tables, see V003__economy.sql).

    /tmp/v/bin/python scripts/yaml_to_seed_economy.py <economy.yml>

Scalars become 'economy.<yaml path>' rows of the setting table (value written with the YAML spelling), the shops, bank
tiers, Bazaar categories, pressure prefixes and ban lists go to their own tables. Kept to document how the seed was made.
"""
import pathlib
import sys

import yaml

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
from sqlseed import insert, write  # noqa: E402

COLLECTIONS = {"shops", "bazaar.categories", "bank.tiers", "pressure.compact-prefixes", "market.never-traded",
               "auction.blocked-materials", "auction.blocked-items"}

# The comments of the YAML, kept as the note shown in the panel.
NOTES = {
    "currency.max-balance": "No balance (purse or bank) can ever exceed this. Extra coins are refused, never destroyed.",
    "currency.ledger-size": "Purse changes remembered per player (used by /economy ledger and the audit chain check).",
    "currency.ledger-file": "Also append every purse change to plugins/VampyuyuSurvival/economy/ledger/<day>.log (needs a restart).",
    "pay.confirm-over": 'Payments above this need "/pay confirm" within 30 seconds.',
    "pay.daily-limit": "Total a player may send per UTC day (anti alt-funnelling).",
    "pay.tax-percent": "Percent of each payment that disappears (0 disables).",
    "death.cause": "pvp = only when killed by another player, any = every death, never = off.",
    "death.percent": "Share of the purse lost (the bank is never touched).",
    "death.max": "The most that can be lost in one death.",
    "death.min-balance": "Purses below this lose nothing.",
    "death.drop-to-killer": "false = the coins are destroyed (an alt cannot farm a killer reward). true = the killer receives them.",
    "safety.save-player-data": "Save the player's data right after an item is escrowed into the bazaar or auction house, so a crash cannot bring the item back while the order still exists.",
    "bank.interest-period-hours": 'Real time between interest payments, in hours (interest is also paid for the time spent offline). 24 h x 0.5 % is about 0.5 % a day: do not shorten the period or raise the rates without watching "/economy audit".',
    "bank.max-catchup-periods": "Offline catch-up is limited to this many periods; the rest is forfeited.",
    "sell.protect-hotbar": 'true = "Sell everything" and /sellall leave the 9 hotbar slots alone.',
    "buyback.size": "Items sold to a merchant stay buyable for what they paid: how many.",
    "buyback.seconds": "Items sold to a merchant stay buyable for what they paid: for how long.",
    "pressure.enabled": "Server-wide sell pressure: every item sold to an NPC lowers that item's sell price for everybody, and the effect fades. Stops endless farms.",
    "pressure.soft-cap": "multiplier = 1 / (1 + pressure / soft-cap), never below min-multiplier.",
    "pressure.min-multiplier": "The sell price never falls below this share of its base.",
    "bazaar.tax-percent": "Percent of each sale kept by the market (taken from the seller on every fill, instant sells included).",
    "bazaar.max-orders": "Open orders a player may have (buy and sell together).",
    "bazaar.max-amount": "The largest single order.",
    "bazaar.max-price": "Highest price per item, in coins.",
    "bazaar.price-band": "Orders must stay within this factor of the 24h average price (5 = from 1/5 to 5 times; 1 or less disables it). Stops manipulation with absurd prices.",
    "bazaar.history-hours": "Hours of hourly price history kept per product (24..336).",
    "bazaar.persist-delay-ticks": "The market file is written this many ticks after a change; changes in the same window share one write.",
    "auction.max-listings": "Active listings per player.",
    "auction.min-price": "Starting bid / Buy It Now lower bound, in coins.",
    "auction.max-price": "Starting bid / Buy It Now upper bound, in coins.",
    "auction.tax-percent": "Percent of the final price taken from the seller when an auction sells.",
    "auction.bid-increment-percent": "A bid must beat the top bid by this percent, and by at least bid-increment-min coins.",
    "auction.anti-snipe-window-seconds": 'A bid in the last window seconds restarts the clock to "extend" seconds (anti sniping), max-extensions times.',
    "auction.durations-hours": "Hours a listing may last (the menu cycles through these).",
    "auction.default-duration-hours": "Default listing duration.",
    "auction.listing-fee.base-percent": "Listing fee, paid up front and never refunded: price * (base-percent + per-day-percent * hours / 24) %, between min and max coins.",
    "auction.history-size": 'Finished sales kept for /ah history and the "recent price" line.',
    "auction.max-item-chars": "Refuse an item whose serialised form is larger than this (stops giant shulker boxes bloating the file).",
}


def flatten(node, prefix=""):
    for key, value in node.items():
        path = f"{prefix}{key}"
        if path in COLLECTIONS:
            continue
        if isinstance(value, dict):
            yield from flatten(value, path + ".")
        else:
            yield path, value


def setting(path, value):
    sid = "economy." + path
    note = NOTES.get(path, "")
    if isinstance(value, list):
        yield insert("setting", {"id": sid, "type": "list", "value": "", "note": note})
        for i, v in enumerate(value):
            yield insert("setting_item", {"setting_id": sid, "position": i, "value": str(v)})
    elif isinstance(value, bool):
        yield insert("setting", {"id": sid, "type": "bool", "value": "true" if value else "false", "note": note})
    elif isinstance(value, int):
        yield insert("setting", {"id": sid, "type": "int", "value": str(value), "note": note})
    elif isinstance(value, float):
        yield insert("setting", {"id": sid, "type": "real", "value": repr(value), "note": note})
    else:
        yield insert("setting", {"id": sid, "type": "text", "value": str(value), "note": note})


def slug(name):
    return "".join(ch if ch.isalnum() else "_" for ch in name.lower())


def convert(data):
    rows = []
    for path, value in flatten(data):
        rows.extend(setting(path, value))
    for pos, (mid, s) in enumerate(data["shops"].items()):
        name = s.get("name", mid)
        rows.append(insert("merchant", {"id": mid, "position": pos, "name": name, "npc_name": s.get("npc-name", "<yellow>" + name),
                                        "icon": s.get("icon", "CHEST"), "profession": s.get("profession", "NONE"),
                                        "npc_type": s.get("npc-type", "VILLAGER"), "description": s.get("description", "")}))
        for i, (key, price) in enumerate(s["items"].items()):
            rows.append(insert("merchant_offer", {"id": f"{mid}:{key}", "merchant": mid, "position": i, "item_key": key,
                                                  "buy": float(price.get("buy", 0)), "sell": float(price.get("sell", 0))}))
    for pos, tier in enumerate(data["bank"]["tiers"]):
        rows.append(insert("bank_tier", {"id": slug(tier["name"]), "position": pos, "name": tier["name"],
                                         "capacity": float(tier["capacity"]), "rate": float(tier["rate"]),
                                         "max_interest": float(tier["max-interest"]), "cost": float(tier["cost"])}))
    for cat, keys in data["bazaar"]["categories"].items():
        for i, key in enumerate(keys):
            rows.append(insert("bazaar_product", {"id": f"{cat}:{key}", "category": cat, "position": i, "item_key": key}))
    for prefix, ratio in data["pressure"]["compact-prefixes"].items():
        rows.append(insert("price_pressure_prefix", {"prefix": prefix, "ratio": float(ratio)}))
    for pattern in data["market"]["never-traded"]:
        rows.append(insert("market_never_traded", {"pattern": pattern}))
    for kind, path in (("material", "blocked-materials"), ("item", "blocked-items")):
        for value in data["auction"][path]:
            rows.append(insert("auction_blocked_item", {"id": f"{kind}:{value}", "kind": kind, "value": value}))
    return rows


def main():
    with open(sys.argv[1], encoding="utf-8") as f:
        data = yaml.safe_load(f)
    rows = convert(data)
    out = write("economy", ["The economy: merchants and offers, bank tiers, Bazaar products, pressure pools, trade bans and the economy.* settings.",
                            "Converted from the former economy.yml by scripts/yaml_to_seed_economy.py; edit it in the panel."], rows)
    print(f"economy: {len(rows)} rows -> {out}")


if __name__ == "__main__":
    main()
