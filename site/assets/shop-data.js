// The only place where offers and prices live. Prices are in euro cents.
// Two stores that never mix: "colony" (lobby, Werewolf, ValoBlock, Creative, Survival)
// and "isles" (Skyblock, isolated: its own looks, nothing crosses over).
// status: "preview" = shown with its price, can go in the cart, checkout not open yet
//         "soon"    = announced, not sold yet (waiting for a legal or owner decision)
// period: "month" = billed every month.
// Names and texts are i18n keys: shop.<id>.name / shop.<id>.desc / shop.<id>.fair (assets/i18n.js).
// Aligned with the debate plan of 2026-11-23 (every price indicative).
window.SHOP = {
  indicative: true,
  // false: every price shows "Price at opening" (owner decision pending, nothing is sold yet).
  showPrices: true,
  stores: {
    colony: [
      { id: "fledgling", group: "support", price: 499 },
      { id: "nightwing", group: "support", price: 999 },
      { id: "elder", group: "support", price: 1999 },
      { id: "seasonpass", group: "season", price: 799 },
      { id: "locker", group: "looks", price: 100, from: true },
      { id: "cosplay", group: "looks", price: 100, from: true, img: "assets/img/pack/cupid.png" },
      { id: "pet", group: "looks", price: 100, from: true },
      { id: "weaponskin", group: "looks", price: 300, from: true, img: "assets/img/vb/flux_rifle.png" },
      { id: "claims", group: "survival", price: 499, period: "month", img: "assets/img/pack/guard.png" },
      { id: "landcosmetic", group: "survival", price: 100, from: true },
      { id: "boxkey", group: "boxes", price: 199, status: "soon" }
    ],
    isles: [
      { id: "wingtint", group: "looks", price: 200, from: true, img: "assets/img/pack/starcore_sword.png" },
      { id: "swordvariant", group: "looks", price: 300, from: true, status: "soon", img: "assets/img/pack/void_sword.png" }
    ]
  },
  // Never sold, in any store. Keys: never.<id> in assets/i18n.js.
  never: ["power", "currency", "queue", "roles", "capes", "trading", "boxloot", "flight"]
};
