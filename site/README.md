# Vampyuyu public site

Static showcase and shop preview: plain HTML, CSS and JS, no build step, nothing loaded from other sites.

- Pages: `index.html` (hero with the roost and the moon, doors to each game, how to join, the Werewolf deck, the
  ValoBlock armory, Creative and Survival, a teaser for the Isles, "never for sale"), `isles.html` (the whole Skyblock:
  Aethelgard, the island start, Hub and Quai des Huit, the eight Realms, Bastions, the Guardians and their effects, the Raid
  of the Devourer, the Astral Interiority, Hell Gao as a teaser, vocations, runes, wings, armour, Catacombs; styles in
  `assets/isles.css`), `valoblock.html` (a round, the eight agents with their portraits, the Armory and Shards,
  the three maps with their tactical plans; `assets/valoblock.css`, text in `assets/i18n-valoblock.js`), `shop.html` ("The Locker":
  preview, two separate shops and carts, never for sale, box odds, parents), `parents.html`, `legal.html`.
  The design comes from the Design canvas "Vampyuyu, refonte de la vitrine".
- Offers and prices: `assets/shop-data.js` only (euro cents, `status: "soon"` = announced, not sold,
  `showPrices: false` shows "Price at opening" everywhere).
- Text: `assets/i18n.js`, English source with French and German on the same keys; the Isles page and the home teaser
  in `assets/i18n-isles.js` (merged into the same table, Skyblock names from `survival-plugin/i18n/GLOSSARY.md`). Language comes from
  `?lang=`, then the saved choice, then the browser language.
- Fonts: Grenze Gotisch (titles) and Figtree (text), self-hosted in `assets/fonts/` (SIL Open Font License,
  `OFL-GrenzeGotisch.txt`, `OFL-Figtree.txt`).
  Do not load Google Fonts from their servers (German courts have fined that under the GDPR).
- Images: `assets/img/`, role cards and weapon sprites copied from the resource packs into `assets/img/pack/`, screenshots cropped from the e2e client screenshots (`.e2e/` is gitignored) and `docs/lobby-preview.png`.
  `assets/img/isles/`: real client captures (e2e `sbhold`, `realms`, `start`, `armorwings`, `vfx`) and three muted
  effect clips (`vfx-*.mp4`, H.264, with a poster each; `app.js` stops autoplay for prefers-reduced-motion).
- Preview: `python3 -m http.server 8765 --directory site` (or the `site` entry in `.claude/launch.json`).

Not done yet (owner decisions):

- Checkout: the button is disabled. Needs a payment provider (VAT, refunds, purchase history) and the legal entity.
- `legal.html`: operator details, privacy and withdrawal texts are placeholders.
- Live player counts and stream status: no public endpoint yet.
- Hosting: not wired to the panel (`internal/panel` serves the admin app at `/`).
