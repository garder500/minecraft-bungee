# Vampyuyu public site

Static showcase and shop preview: plain HTML, CSS and JS, no build step, nothing loaded from other sites.

- Pages: `index.html` (hero with the roost and the moon, doors to each game, how to join, the Werewolf deck, the
  ValoBlock armory, Creative and Survival, the Isles and their eight Realms, "never for sale"), `shop.html` ("The Locker":
  preview, two separate shops and carts, never for sale, box odds, parents), `parents.html`, `legal.html`.
  The design comes from the Design canvas "Vampyuyu, refonte de la vitrine".
- Offers and prices: `assets/shop-data.js` only (euro cents, `status: "soon"` = announced, not sold,
  `showPrices: false` shows "Price at opening" everywhere).
- Text: `assets/i18n.js`, English source with French and German on the same keys. Language comes from
  `?lang=`, then the saved choice, then the browser language.
- Fonts: Grenze Gotisch (titles) and Figtree (text), self-hosted in `assets/fonts/` (SIL Open Font License,
  `OFL-GrenzeGotisch.txt`, `OFL-Figtree.txt`).
  Do not load Google Fonts from their servers (German courts have fined that under the GDPR).
- Images: `assets/img/`, role cards and weapon sprites copied from the resource packs into `assets/img/pack/`, screenshots cropped from the e2e client screenshots (`.e2e/` is gitignored) and `docs/lobby-preview.png`.
- Preview: `python3 -m http.server 8765 --directory site` (or the `site` entry in `.claude/launch.json`).

Not done yet (owner decisions):

- Checkout: the button is disabled. Needs a payment provider (VAT, refunds, purchase history) and the legal entity.
- `legal.html`: operator details, privacy and withdrawal texts are placeholders.
- Live player counts and stream status: no public endpoint yet.
- Hosting: not wired to the panel (`internal/panel` serves the admin app at `/`).
