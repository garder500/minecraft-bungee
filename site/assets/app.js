(function () {
  "use strict";
  var LANGS = ["en", "fr", "de"];
  var IP = "play.vampyuyu.win";
  var DISCORD = "https://discord.gg/F9uXnh3yzs";

  function store(key, value) {
    try {
      if (value === undefined) return localStorage.getItem(key);
      localStorage.setItem(key, value);
    } catch (e) { return null; }
  }

  function pickLang() {
    var q = new URLSearchParams(location.search).get("lang");
    if (LANGS.indexOf(q) >= 0) return q;
    var saved = store("vy.lang");
    if (LANGS.indexOf(saved) >= 0) return saved;
    var nav = (navigator.language || "en").slice(0, 2).toLowerCase();
    return LANGS.indexOf(nav) >= 0 ? nav : "en";
  }

  var lang = pickLang();
  function t(key) {
    var d = window.I18N[lang] || {};
    return d[key] !== undefined ? d[key] : (window.I18N.en[key] !== undefined ? window.I18N.en[key] : key);
  }

  function money(cents) {
    var locale = { en: "en-IE", fr: "fr-FR", de: "de-DE" }[lang];
    return new Intl.NumberFormat(locale, { style: "currency", currency: "EUR" }).format(cents / 100);
  }

  function priceLabel(item) {
    if (window.SHOP.showPrices === false) return t("shop.priceAtOpening");
    var s = money(item.price);
    if (item.from) s = t("shop.from") + " " + s;
    if (item.period === "month") s += " " + t("shop.month");
    return s;
  }

  function el(tag, attrs, children) {
    var n = document.createElement(tag);
    if (attrs) for (var k in attrs) {
      if (k === "text") n.textContent = attrs[k];
      else if (k === "class") n.className = attrs[k];
      else n.setAttribute(k, attrs[k]);
    }
    (children || []).forEach(function (c) { if (c) n.appendChild(c); });
    return n;
  }

  // ---------- shared header and footer ----------
  var page = document.body.getAttribute("data-page");
  var home = page === "home" ? "" : "index.html";
  var NAV = [
    [home + "#games", "nav.games", ""],
    [home + "#isles", "nav.isles", ""],
    [home + "#join", "nav.join", ""],
    ["shop.html", "nav.shop", "shop"],
    ["parents.html", "nav.parents", "parents"]
  ];

  function renderHeader() {
    var host = document.getElementById("site-header");
    if (!host) return;
    host.innerHTML = "";
    var brand = el("a", { href: "index.html", class: "brand" }, [
      el("img", { src: "assets/img/icon.png", alt: "", width: "34", height: "34" }),
      el("span", { text: "Vampyuyu" })
    ]);
    var list = el("ul", { class: "nav-list", id: "nav-list" });
    NAV.forEach(function (n) {
      var a = el("a", { href: n[0], text: t(n[1]) });
      if (n[2] && n[2] === page) a.setAttribute("aria-current", "page");
      list.appendChild(el("li", null, [a]));
    });
    list.appendChild(el("li", null, [el("a", { href: DISCORD, rel: "noopener", text: t("nav.discord") })]));
    var toggle = el("button", { class: "nav-toggle", type: "button", "aria-expanded": "false", "aria-controls": "nav-list", text: t("nav.menu") });
    toggle.addEventListener("click", function () {
      var open = host.classList.toggle("open");
      toggle.setAttribute("aria-expanded", open ? "true" : "false");
    });
    var langs = el("div", { class: "lang", role: "group", "aria-label": t("nav.lang") });
    LANGS.forEach(function (l) {
      var b = el("button", { type: "button", lang: l, "aria-pressed": l === lang ? "true" : "false", text: l.toUpperCase() });
      b.addEventListener("click", function () { setLang(l); });
      langs.appendChild(b);
    });
    host.appendChild(el("nav", { class: "wrap nav", "aria-label": "Main" }, [brand, toggle, list, langs]));
  }

  function renderFooter() {
    var host = document.getElementById("site-footer");
    if (!host) return;
    host.innerHTML = "";
    host.appendChild(el("div", { class: "wrap foot" }, [
      el("span", { class: "foot-brand", text: "Vampyuyu" }),
      el("p", { class: "foot-links" }, [
        el("a", { href: "shop.html", text: t("nav.shop") }),
        el("a", { href: "parents.html", text: t("nav.parents") }),
        el("a", { href: "legal.html#imprint", text: t("foot.legal") }),
        el("a", { href: "legal.html#privacy", text: t("foot.privacy") }),
        el("a", { href: "legal.html#withdrawal", text: t("foot.withdrawal") }),
        el("a", { href: DISCORD, rel: "noopener", text: t("nav.discord") })
      ]),
      el("p", { class: "foot-note", lang: "en", text: t("foot.mojang") }),
      page === "shop" ? el("p", { class: "foot-note", text: t("foot.indicative") }) : null
    ]));
  }

  // ---------- text ----------
  function applyText() {
    document.documentElement.lang = lang;
    document.querySelectorAll("[data-i18n]").forEach(function (n) { n.textContent = t(n.getAttribute("data-i18n")); });
    document.querySelectorAll("[data-i18n-alt]").forEach(function (n) { n.setAttribute("alt", t(n.getAttribute("data-i18n-alt"))); });
    var title = document.body.getAttribute("data-title");
    document.title = title ? t(title) + " | Vampyuyu" : "Vampyuyu";
  }

  function setLang(l) {
    lang = l;
    store("vy.lang", l);
    render();
  }

  // ---------- server address ----------
  function wireCopy() {
    document.querySelectorAll("[data-copy-ip]").forEach(function (b) {
      if (b.dataset.wired) return;
      b.dataset.wired = "1";
      b.addEventListener("click", function () {
        var done = function () {
          b.textContent = t("hero.copied");
          b.classList.add("done");
          setTimeout(function () { b.textContent = t("hero.copy"); b.classList.remove("done"); }, 1800);
        };
        if (navigator.clipboard) navigator.clipboard.writeText(IP).then(done, done); else done();
      });
    });
  }

  // ---------- shop ----------
  var activeStore = "colony";
  function cartKey(s) { return "vy.cart." + s; }
  function readCart(s) {
    try { return JSON.parse(store(cartKey(s)) || "[]"); } catch (e) { return []; }
  }
  function writeCart(s, ids) { store(cartKey(s), JSON.stringify(ids)); }
  function findItem(s, id) { return window.SHOP.stores[s].filter(function (i) { return i.id === id; })[0]; }

  function renderShop() {
    var host = document.getElementById("shop");
    if (!host) return;
    var hash = location.hash.replace("#", "");
    if (hash === "isles" || hash === "colony") activeStore = hash;
    document.body.setAttribute("data-store", activeStore);
    document.querySelectorAll("[data-store-tab]").forEach(function (b) {
      var on = b.getAttribute("data-store-tab") === activeStore;
      b.setAttribute("aria-selected", on ? "true" : "false");
      b.tabIndex = on ? 0 : -1;
      if (!b.dataset.wired) {
        b.dataset.wired = "1";
        b.addEventListener("click", function () {
          activeStore = b.getAttribute("data-store-tab");
          history.replaceState(null, "", "#" + activeStore);
          renderShop();
        });
      }
    });

    var cart = readCart(activeStore).filter(function (id) { return findItem(activeStore, id); });
    var list = document.getElementById("shop-items");
    list.innerHTML = "";
    var groups = [];
    window.SHOP.stores[activeStore].forEach(function (i) { if (groups.indexOf(i.group) < 0) groups.push(i.group); });
    groups.forEach(function (g) {
      var sec = el("section", { class: "shop-group" }, [el("h2", { text: t("shop.group." + g) })]);
      var ul = el("ul", { class: "offers" });
      window.SHOP.stores[activeStore].filter(function (i) { return i.group === g; }).forEach(function (i) {
        var soon = i.status === "soon";
        var inCart = cart.indexOf(i.id) >= 0;
        var btn = el("button", { type: "button", class: "btn " + (soon || inCart ? "btn-off" : "btn-main") });
        if (soon) { btn.textContent = t("shop.soon"); btn.disabled = true; }
        else if (inCart) { btn.textContent = t("shop.added"); btn.disabled = true; }
        else {
          btn.textContent = t("shop.add");
          btn.addEventListener("click", function () {
            var c = readCart(activeStore); c.push(i.id); writeCart(activeStore, c); renderShop();
          });
        }
        ul.appendChild(el("li", { class: "offer" + (soon ? " is-soon" : "") }, [
          el("div", { class: "offer-head" }, [
            el("h3", { text: t("shop." + i.id + ".name") }),
            i.img ? el("img", { src: i.img, alt: "" }) : null
          ]),
          el("p", { class: "price", text: priceLabel(i) }),
          el("p", { class: "desc", text: t("shop." + i.id + ".desc") }),
          el("p", { class: "fair" }, [el("strong", { text: t("shop.fairLabel") + ": " }), document.createTextNode(t("shop." + i.id + ".fair"))]),
          btn
        ]));
      });
      sec.appendChild(ul);
      list.appendChild(sec);
    });

    var box = document.getElementById("cart-lines");
    box.innerHTML = "";
    var total = 0;
    if (!cart.length) box.appendChild(el("p", { class: "empty", text: t("shop.cartEmpty") }));
    cart.forEach(function (id) {
      var i = findItem(activeStore, id);
      total += i.price;
      var name = t("shop." + id + ".name");
      var rm = el("button", { type: "button", class: "link-btn", text: t("shop.remove"), "aria-label": t("shop.remove") + " " + name });
      rm.addEventListener("click", function () { writeCart(activeStore, readCart(activeStore).filter(function (x) { return x !== id; })); renderShop(); });
      box.appendChild(el("div", { class: "cart-line" }, [
        el("span", { text: name }), el("span", { class: "right" }, [el("span", { text: priceLabel(i) }), rm])
      ]));
    });
    document.getElementById("cart-total").textContent = window.SHOP.showPrices === false ? t("shop.priceAtOpening") : money(total);
    document.getElementById("cart-store").textContent = t("shop.tab." + activeStore);
  }

  function renderNever() {
    var ul = document.getElementById("never-list");
    if (!ul) return;
    ul.innerHTML = "";
    window.SHOP.never.forEach(function (k) { ul.appendChild(el("li", { text: t("never." + k) })); });
  }

  function render() {
    renderHeader();
    renderFooter();
    applyText();
    wireCopy();
    renderShop();
    renderNever();
  }

  window.addEventListener("hashchange", renderShop);
  render();
})();
