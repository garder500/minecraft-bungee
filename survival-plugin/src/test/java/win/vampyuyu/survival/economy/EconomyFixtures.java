package win.vampyuyu.survival.economy;

import win.vampyuyu.survival.data.ContentDomains;
import win.vampyuyu.survival.data.Db;
import win.vampyuyu.survival.data.Seeds;

/** The economy as the tests need it: the shipped seed, or a few hand-written rows. */
final class EconomyFixtures {
    private EconomyFixtures() {}

    /** The defaults shipped in the jar (db/seed/economy.sql). */
    static EconomyConfig shipped() {
        try (Db db = Db.memory()) {
            Seeds.sync(db, ContentDomains.ALL, null);
            return EconomyConfig.load(db);
        }
    }

    /** A few rows in an otherwise empty database. */
    static final class Custom implements AutoCloseable {
        final Db db = Db.memory();
        private int position;

        Custom merchant(String id, String name) {
            db.write(c -> Db.update(c, "INSERT INTO merchant (id, position, name, npc_name) VALUES (?, ?, ?, ?)",
                id, position++, name, "<yellow>" + name));
            return this;
        }

        Custom offer(String merchant, String key, double buy, double sell) {
            db.write(c -> Db.update(c, "INSERT INTO merchant_offer (id, merchant, position, item_key, buy, sell) "
                + "VALUES (?, ?, ?, ?, ?, ?)", merchant + ":" + key, merchant, position++, key, buy, sell));
            return this;
        }

        Custom setting(String path, String type, String value) {
            db.write(c -> Db.update(c, "INSERT INTO setting (id, type, value) VALUES (?, ?, ?)", "economy." + path, type, value));
            return this;
        }

        EconomyConfig build() {
            return EconomyConfig.load(db);
        }

        @Override
        public void close() {
            db.close();
        }
    }
}
