-- Living NPCs (GDD 18): first reactions and the relation settings. Numbers are first guesses, to be tuned with play.
INSERT INTO setting (id, type, value, note) VALUES ('npc.enabled', 'bool', 'true', 'NPCs remember players and react');
INSERT INTO setting (id, type, value, note) VALUES ('npc.rep.max', 'int', '100', 'Relation is kept between -max and +max');
INSERT INTO setting (id, type, value, note) VALUES ('npc.rep.visit-gain', 'int', '1', 'Relation gained by talking to an NPC (at most once per cooldown)');
INSERT INTO setting (id, type, value, note) VALUES ('npc.rep.visit-cooldown-seconds', 'int', '600', 'Seconds between two visits that count');
INSERT INTO setting (id, type, value, note) VALUES ('npc.price.min-pct', 'int', '-10', 'Lowest price change a reputation can give (percent)');
INSERT INTO setting (id, type, value, note) VALUES ('npc.price.max-pct', 'int', '10', 'Highest price change (percent)');
INSERT INTO npc_reaction (id, npc, kind, fact, min_value, min_rep, max_rep, priority, price_pct, line) VALUES ('guard_guardian_slayer', '*', 'GREETING', 'defeated.guardian', 1, -1000, 1000, 50, 0, 'The guards salute. "{player}, bane of a Guardian. The Hub is safer for you."');
INSERT INTO npc_reaction (id, npc, kind, fact, min_value, min_rep, max_rep, priority, price_pct, line) VALUES ('breath_aware', '*', 'GREETING', 'breath.survived', 1, -1000, 1000, 40, 0, 'Their eyes linger on you. "You breathed the sky itself, {player}. We have not forgotten."');
INSERT INTO npc_reaction (id, npc, kind, fact, min_value, min_rep, max_rep, priority, price_pct, line) VALUES ('rescued_grateful', '*', 'GREETING', 'rescued', 1, -1000, 1000, 30, 0, 'Their face lights up. "{player}! You are the one who got me out. My stall is yours."');
INSERT INTO npc_reaction (id, npc, kind, fact, min_value, min_rep, max_rep, priority, price_pct, line) VALUES ('friend_greeting', '*', 'GREETING', '', 1, 25, 1000, 10, 0, 'A warm nod. "Good to see you again, {player}."');
INSERT INTO npc_reaction (id, npc, kind, fact, min_value, min_rep, max_rep, priority, price_pct, line) VALUES ('stranger_greeting', '*', 'GREETING', '', 1, -1000, 1000, 0, 0, '"Welcome, {player}. Take a look."');
INSERT INTO npc_reaction (id, npc, kind, fact, min_value, min_rep, max_rep, priority, price_pct, line) VALUES ('price_friend', '*', 'PRICE', '', 1, 25, 1000, 0, -5, '');
INSERT INTO npc_reaction (id, npc, kind, fact, min_value, min_rep, max_rep, priority, price_pct, line) VALUES ('price_ally', '*', 'PRICE', '', 1, 60, 1000, 0, -5, '');
INSERT INTO npc_reaction (id, npc, kind, fact, min_value, min_rep, max_rep, priority, price_pct, line) VALUES ('price_rescued', '*', 'PRICE', 'rescued', 1, -1000, 1000, 0, -5, '');
INSERT INTO npc_reaction (id, npc, kind, fact, min_value, min_rep, max_rep, priority, price_pct, line) VALUES ('price_disliked', '*', 'PRICE', '', 1, -1000, -25, 0, 10, '');
