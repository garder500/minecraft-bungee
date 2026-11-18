# Translation rules (Skyblock text: items and quests)

Source: `en/<chunk>.json` ({key: English}). Output: `<locale>/<chunk>.json` with exactly the same keys in the same order
and nothing else. UTF-8, real accented characters (é, ü, ß), no HTML entities, `json.dumps(indent=1)` style.

## Hard rules (a script checks them)
- Same keys, no missing key, no extra key, no empty value.
- MiniMessage tags (`<gray>`, `<white>`, `<yellow>`, `</...>`) stay exactly as they are, same tags in the same order.
- Every number of the source appears in the translation (64, 128, 3...). Roman numerals (I, II, IV) stay.
- Commands (`/guide`, `/island end`, `/sbshowcase`...) are never translated.
- `item.*.lore.N` keys are single tooltip lines of one sentence cut over several lines: keep one line per key, balance the
  sentence over the same keys, at most about 42 visible characters per line.
- Quest names are short titles (at most about 28 characters). Quest descriptions keep the same tone: a short line of
  story, then the plain instruction.
- Natural, idiomatic language for players, informal address: French "tu", German "du". No word-for-word calques.
- Vanilla Minecraft things (blocks, items, mobs, Nether, End, crafting table...) use the official Minecraft translation
  of the target language (Cobblestone = Pierres / Bruchstein, Crafting Table = Établi / Werkbank, Furnace = Fourneau /
  Ofen, Oak Log = Bûche de chêne / Eichenstamm, Stone Bricks = Pierre taillée / Steinziegel, Redstone = Redstone).

## Names that are never translated
Aethelgard, Bronzite, Frostium, Sunstone, Voidglass, Starcore, Bastion, Hub, Skyblock, Nether, End, Redstone, Paragon.

## Glossary (English = French = German)
- pebble = galet = Kiesel
- Fountain = Fontaine = Brunnen
- Spring Stone = Pierre de Source = Quellstein
- generator = générateur = Generator
- ring (of stone bricks) = anneau = Ring
- the Forge = la Forge = die Schmiede
- core (Bronze Core, Ember Core) = Cœur (Cœur de bronze, Cœur de braise) = Kern (Bronzekern, Glutkern)
- Void Lens = Lentille du Vide = Leerenlinse
- tier = palier = Stufe
- Realm = Royaume = Reich
- World Devourer / the Devourer = Dévoreur de Mondes / le Dévoreur = Weltenverschlinger / der Verschlinger
- the Call = l'Appel = der Ruf
- the Rift = la Faille = der Riss
- Echo = Écho = Echo
- fragment (the player's island) = fragment = Fragment
- islet / island = îlot / île = Inselchen / Insel
- stasis field = champ de stase = Stasisfeld
- Bronze / Rime / Ember / Void / Starcore gear = de bronze / de givre / de braise / du Vide / de Starcore =
  Bronze- / Reif- / Glut- / Leeren- / Starcore-
- Rimeblade = Lame de givre = Reifklinge; Cleaver = Fendoir = Spalter
- Cinder / Grove / Frost Starcore = Starcore de cendre / du bosquet / de gel = Asche-/Hain-/Frost-Starcore
- Helmet, Chestplate, Leggings, Boots = Casque, Plastron, Jambières, Bottes = Helm, Brustpanzer, Beinschutz, Stiefel
- Compressed X = X compressé(e) = Komprimierte(s/r) X; Compacted X = X compacté(e) = Verdichtete(s/r) X
- machine, cable, battery, solar panel, miner, coal generator = machine, câble, batterie, panneau solaire, foreuse,
  générateur à charbon = Maschine, Kabel, Batterie, Solarmodul, Bohrer, Kohlegenerator
- coins = pièces = Münzen; market = marché = Markt; quest = quête = Quest; boss = boss = Boss
- survivor = survivant = Überlebender

## French names decided in review
- Gender of the ores: la Bronzite, le Frostium, le Sunstone, le Voidglass, le Starcore
- Starcore Shard = éclat de Starcore (fragment stays the player's island)
- Realm 1 Cracked Ashes = Cendres fissurées; Realm 2 Whispering Grove = Bosquet murmurant (the Grove = le Bosquet);
  Realm 3 Frozen Heights = Cimes gelées (the Heights = les Cimes); Realm 4 Citadel of Golden Sands = Citadelle des
  Sables d'or (the Citadel = la Citadelle); Realm 5 Putrid Marshes = Marais putrides (the Marsh = le Marais)
- Eight Realms = Huit Royaumes; Quai des Huit = Quai des Huit; Gate / Sky Gate / Island Gate = Portail / Portail céleste / Portail de l'île
- Bastion keys: Ashes / Heights / Grove / Abyss Bastion Key = Clé du Bastion des Cendres / des Cimes / du Bosquet /
  de l'Abysse
- Guardian (realm boss) = Gardien / Gardienne (capitalised); Rift Gatekeeper = Portier de la Faille
- Bosses: Lava Colossus = Colosse de lave; Mycelial Queen = Reine mycélienne; Boreal Watcher = Guetteur boréal;
  Mire Abomination = Abomination des fanges; Temporal Pharaoh = Pharaon temporel; Chained Leviathan = Léviathan
  enchaîné; Crypt Lord = Seigneur de la crypte; Echo of the Rift = Écho de la Faille
- Ember Spawn = Progéniture de braise; Void Mites = Mites du Vide
- Echo Key = Clé de l'Écho; Wing Core = Cœur d'ailes; Wings = Ailes; Void Sigil = Sceau du Vide
- Hub NPCs: the Guard / the Archivist / the Merchant = le Garde / l'Archiviste / le Marchand
- Markets: market = marché (/market); Drift Market = Marché de la Dérive (/ah); Sky Exchange = Bourse céleste
- Set bonuses: Ashen Mantle = Manteau de cendre; Verdant Mending = Soin verdoyant; Glacial Poise = Équilibre glacial;
  full set = panoplie complète; Sidegrade = Variante; Heat / Cold resistance = Résistance à la chaleur / au froid (no colon, line length)
- Forge tools: Pick = Pioche (Pioche de bronze...); Bow / Axe / Shovel = Arc / Hache / Pelle
- Machines in running text are lowercase: foreuse I, batterie, panneau solaire, câble de cuivre, générateur à charbon
- Generators: Stone / Dune / Frost / Ember generator = générateur de pierre / des dunes / de givre / de braise
- Gadgets: Skyhook = Grappin céleste; Ore Compass = Boussole à minerai; Cloud Anchor = Ancre de nuage; Rain Totem =
  Totem de pluie; Satchel = Besace; Void Net = Filet du Vide; Crystal Saber Skin = Skin de Sabre de cristal
- Citadel: Time Tide = Marée du temps; Chrono Module = Module chrono; Sandglass = Sablier
- Marshes: Rot Vat = Cuve de pourriture; Rot Resin = Résine de pourriture; Landfill = Décharge; Weeping Mangrove =
  Mangrove pleureuse
- Affinities: Respiration, Growth = Croissance, Warmth = Chaleur, Stasis = Stase, Filtered = Filtré, Reclaimer =
  Récupérateur
- Raise the generator = améliorer le générateur; right-click it on your island = utilise-le (clic droit) sur ton île
- Place (a block or machine) = Pose; sneak = s'accroupir
- Vanilla: Cobblestone = Pierres; Stone = Roche; Jungle Log = Bûche d'acajou; Ender Pearl / Eye of Ender =
  Perle / Œil de l'Ender; sand, gravel, netherrack... counted = blocs de sable, de gravier, de netherrack; Golden Apple / Carrot = Pomme / Carotte dorée; Glowstone Dust = Poudre lumineuse;
  Raw / Cooked Fish = Morue crue / cuite; End Stone Bricks = Briques de l'End; Sapling = Pousse; Stray = Vagabond
- A fallen star = une étoile tombée (not filante); draw a bow = tendre l'arc
- Thousands: 2 000, 1 000 000

## German names decided in review
Realms and places
- Whispering Grove = der Flüsternde Hain (des Flüsternden Hains); Frozen Heights = die Gefrorenen Höhen;
  Putrid Marshes = die Fauligen Sümpfe; Citadel of Golden Sands = die Zitadelle der Goldenen Sande;
  Cracked Ashes = die Rissige Asche; Eight Realms = die Acht Reiche; Quai des Huit = Quai des Huit (kept)
- Sky Gate = Himmelstor; Rift arch / altar = Rissbogen / Rissaltar; Landfill = Müllkippe; Weeping Mangrove = Weinende Mangrove
- Dungeon(s) = Dungeon(s) (not "Verlies"); floor = Etage
Bosses and NPCs
- Guardian (of a Realm) = Wächter; Boreal Watcher = Boreal-Wächter (no declension); Mire Abomination = das Sumpfscheusal;
  Mycelial Queen = Myzelkönigin; Temporal Pharaoh = Zeitpharao; Lava Colossus = Lavakoloss;
  Chained Leviathan = Angeketteter Leviathan; Crypt Lord = Gruftherr; Echo of the Rift = Echo des Risses
- Guard (Hub NPC) = die Wache (not "Wächter"); Rift Gatekeeper = Torhüter des Risses; Archivist = Archivarin; Merchant = Händler
- Ember Spawn = Glutbrut; Void Mites = Leerenmilben
Items, machines, markets
- Bastion keys = Asche- / Höhen- / Hain- / Abgrund-Bastion-Schlüssel
- Starcore Shard = Starcore-Splitter (never "Fragment", reserved for the island); Echo Key = Echoschlüssel; Wing Core = Flügelkern
- Ember / Frost / Dune / Stone generator = Glutgenerator / Frostgenerator / Dünengenerator / Steingenerator (one word)
- Miner I/II = Bohrer I/II; Pick (Bronze Pick...) = Spitzhacke; Rot Resin = Fäulnisharz; Rot Vat = Fäulnisbottich;
  Chrono Module = Chronomodul; Sandglass = Sanduhr; Time Tide = Zeitflut; Skyhook = Himmelshaken
- Drift Market (/ah) = Drift-Markt; Sky Exchange = Himmelsbörse
- Set bonuses: Ashen Mantle = Aschemantel; Verdant Mending = Grüne Heilung; Glacial Poise = Gletscherruhe; Rend = Zerfetzen; Chill = Kälte
- Affinities: Respiration = Atmung, Growth = Wachstum, Warmth = Wärme, Stasis = Stase, Filtered = Gefiltert, Reclaimer = Wiederverwerter
Vanilla terms checked against the game's de_de.json
- nugget = Eisenklumpen / Goldklumpen; ancient debris = Antiker Schrott; concrete powder = Trockenbeton; hay bale = Strohballen;
  stray = Eiswanderer; amethyst cluster = Amethysthaufen; rotten flesh = verrottetes Fleisch; gunpowder = Schwarzpulver;
  jungle log = Tropenbaumstamm; logs = Stämme; stonecutter = Steinsäge; blast furnace = Schmelzofen
Style
- "Collect X from the Forge (/forge)" = "Hol dir X aus der Schmiede (/forge)."; right-click with X on Y = "Mach mit X einen Rechtsklick auf Y"
- repeats = "3-mal"; thousands = 2.000, 1.000.000
