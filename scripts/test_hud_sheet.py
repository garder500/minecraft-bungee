#!/usr/bin/env python3
"""Checks the Python port of CustomHud (resource-pack/art/hud_sheet.py) against glyph sequences
derived BY HAND from arena-plugin/src/main/java/win/vampyuyu/werewolf/hud/CustomHud.java.

How each expected list was derived (read CustomHud.of() for the state, then render() for the order):
  of():     palette = dead ? "dead" : bossColor == INK_DIM ? "sleep" : martyr-pause-for-others ? "vote"
            : turnRole(phase) != null ? role.palette : DAY_VOTE/TRIAL/ELECTION ? "vote" : "villager".
            label: FINISHED "game over"; day phases debate/vote/election/defense/trial; a sleeping night
            viewer "sleeping"; a called night actor "<role> awake"; exiled by day "away" wins last.
            INK_DIM (bossColor) is the colour of an exiled day viewer and of a night sleeper.
  render(): frame, icon, round(clamped 0-9), the phase as TEXT, then the clock only if `timed` (the bar, the
            digits of m:ss, seconds clamped to 9:59; at <= 5 s the digits are the red "x" set and, on even
            seconds, the plate is "danger"), then if the role panel is shown: pframe, picon, the head count
            "alive/total" as text, the role and the team as text, one m<i> dot per seat (max 20), one b<i>
            badge (max 4), and, when against >= 0, vframe_calm/hot, the two caption lines as text and the
            ballots' digits (v1 only for a two-digit number, "x" digits when hot).
  text:     the words come from arena-plugin lang/<tag>.yml: hud.phase.<key of the label>, hud.role.<key of
            the role word>, hud.team.<key of the team word>, hud.votes.line1/2 (HudLang.tr: the language, else
            English, else the word). A line is written in capitals, one glyph per character that is not a
            space, at its text element: the phase at "label" in the headline font h, or at "label2" in bold b
            when the headline is wider than the line; pcount, prole and vcap1 in b; pteam and vcap2 in m.
            A glyph's sprite is f<font>_<code point in hex>; its parameter is where its cell starts.
Run: python3 scripts/test_hud_sheet.py   (also part of `make test-pack`)
"""
import pathlib, sys, unittest

ROOT = pathlib.Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "resource-pack" / "art"))
sys.path.insert(0, str(ROOT / "resource-pack"))
import hud_sheet as H  # noqa: E402
import hud_font as F  # noqa: E402
import layout  # noqa: E402


def seq(facts, md=78, lang="en_US"):
    return [f"{e}:{n}" for _, e, n, _ in H.render(H.state_of(facts), md, strict=True, lang=lang)]


def txt(element, font, line):
    """The glyphs of a line written by hand: one per character that is not a space."""
    return [f"{element}:f{font}_{ord(ch):04x}" for ch in line if ch != " "]


class HudPortTest(unittest.TestCase):
    def test_1_day_debate_villager(self):
        # DISCUSSION by day, a living villager, 87 s = 1:27, 10 of 12 alive, four seats, no badge.
        # palette: not dead, not dim, turnRole(DISCUSSION)=null, not a vote phase -> villager; label debate;
        # icon sun (not night, not DAY_VOTE); against = -1 (not DAY_VOTE).
        f = H.Facts(phase="DISCUSSION", role="VILLAGER", round=2, seconds=87, alive=10, total=12,
                    seats=("alive", "dead", "alive", "me"))
        self.assertEqual(seq(f), [
            "frame:frame_villager", "icon:icon_sun", "round:s2", *txt("label", "h", "DEBATE"), "fill:fill_villager",
            "t1:d1", "tcolon:dcolon", "t3:d2", "t4:d7",
            "pframe:pframe_villager", "picon:picon_villager", *txt("pcount", "b", "10/12"),
            *txt("prole", "b", "VILLAGER"), *txt("pteam", "m", "VILLAGE"),
            "m0:dot_alive", "m1:dot_dead", "m2:dot_alive", "m3:dot_me"])
        self.assertEqual(seq(f)[3], "label:fh_0044")   # D is U+0044

    def test_2_night_seer_danger_odd_second(self):
        # SEER night call, the seer is the actor, 3 s left. turnRole(SEER)=SEER -> palette seer (seer.json);
        # label "seer awake"; icon moon. 3 <= DANGER_SECONDS: the clock is the red x set "0:03", but 3 is odd
        # so the plate keeps the seer palette (it only flashes on even seconds). The count is "9/12".
        f = H.Facts(phase="SEER", role="SEER", night=True, actor=True, round=1, seconds=3, alive=9, total=12)
        self.assertEqual(seq(f), [
            "frame:frame_seer", "icon:icon_moon", "round:s1", *txt("label", "h", "SEER AWAKE"), "fill:fill_seer",
            "t1:x0", "tcolon:xcolon", "t3:x0", "t4:x3",
            "pframe:pframe_seer", "picon:picon_seer", *txt("pcount", "b", "9/12"),
            *txt("prole", "b", "SEER"), *txt("pteam", "m", "VILLAGE")])

    def test_3_day_vote_witch_hot_with_badges(self):
        # DAY_VOTE, a witch holding both potions, 45 s, 7 of 8 alive, 3 ballots against her.
        # palette: DAY_VOTE -> vote; label vote; icon vote. against = votes(viewer)[viewer] = 3 (> 0: hot):
        # vframe_hot, the caption, then " 3": nothing at v1 and x3 at v2. badges life, death -> b0, b1.
        f = H.Facts(phase="DAY_VOTE", role="WITCH", round=3, seconds=45, alive=7, total=8,
                    seats=("me", "alive"), badges=("life", "death"), votes_against=3)
        self.assertEqual(seq(f), [
            "frame:frame_vote", "icon:icon_vote", "round:s3", *txt("label", "h", "VOTE"), "fill:fill_vote",
            "t1:d0", "tcolon:dcolon", "t3:d4", "t4:d5",
            "pframe:pframe_witch", "picon:picon_witch", *txt("pcount", "b", "7/8"),
            *txt("prole", "b", "WITCH"), *txt("pteam", "m", "VILLAGE"),
            "m0:dot_me", "m1:dot_alive", "b0:badge_life", "b1:badge_death",
            "vframe:vframe_hot", *txt("vcap1", "b", "VOTES"), *txt("vcap2", "m", "AGAINST YOU"), "v2:x3"])
        # 12 ballots: both digits
        f.votes_against = 12
        self.assertEqual(seq(f)[-2:], ["v1:x1", "v2:x2"])
        # none: the calm plate and the white 0
        f.votes_against = 0
        self.assertEqual(seq(f)[-1], "v2:d0")
        self.assertIn("vframe:vframe_calm", seq(f))

    def test_4_dead_viewer_game_over(self):
        # FINISHED: boss() is (VOTE, no timer) -> no clock glyphs; dead -> palette dead everywhere;
        # label "game over"; role icon dead, word fallen, team out; 3 of 12 alive; against stays -1.
        f = H.Facts(phase="FINISHED", role="WEREWOLF", dead=True, round=4, seconds=0, alive=3, total=12)
        self.assertEqual(seq(f), [
            "frame:frame_dead", "icon:icon_sun", "round:s4", *txt("label", "h", "GAME OVER"),
            "pframe:pframe_dead", "picon:picon_dead", *txt("pcount", "b", "3/12"),
            *txt("prole", "b", "FALLEN"), *txt("pteam", "m", "OUT")])

    def test_5_exiled_minion_clock_clamped(self):
        # An exiled villager by day who is the wolves' minion: boss() gives INK_DIM with a timer -> palette
        # sleep, label "away" (the exile override comes last). 600 s clamps to 9:59 -> "959". Round 12
        # clamps to s9. Team word: isMinion -> "wolf side". Seat "mate" dot for a pack mate.
        f = H.Facts(phase="DISCUSSION", role="VILLAGER", exiled=True, minion=True, round=12, seconds=600,
                    alive=6, total=7, seats=("mate",))
        self.assertEqual(seq(f), [
            "frame:frame_sleep", "icon:icon_sun", "round:s9", *txt("label", "h", "AWAY"), "fill:fill_sleep",
            "t1:d9", "tcolon:dcolon", "t3:d5", "t4:d9",
            "pframe:pframe_villager", "picon:picon_villager", *txt("pcount", "b", "6/7"),
            *txt("prole", "b", "VILLAGER"), *txt("pteam", "m", "WOLF SIDE"), "m0:dot_mate"])

    def test_6_the_words_follow_the_language(self):
        # fr_FR.yml: hud.phase.vote VOTE, hud.role.seer VOYANTE, hud.team.village VILLAGE, the caption VOTES /
        # CONTRE TOI. de_DE.yml: ABSTIMMUNG, SEHERIN, DORF, STIMMEN / GEGEN DICH.
        f = H.Facts(phase="DAY_VOTE", role="SEER", round=1, seconds=45, alive=7, total=8, votes_against=1)
        fr, de = seq(f, lang="fr_FR"), seq(f, lang="de_DE")
        self.assertEqual(fr[3:7], txt("label", "h", "VOTE"))
        self.assertEqual(fr[14:], [*txt("pcount", "b", "7/8"), *txt("prole", "b", "VOYANTE"), *txt("pteam", "m", "VILLAGE"),
                                   "vframe:vframe_hot", *txt("vcap1", "b", "VOTES"), *txt("vcap2", "m", "CONTRE TOI"), "v2:x1"])
        self.assertEqual(de[3:13], txt("label", "h", "ABSTIMMUNG"))
        self.assertEqual(de[20:], [*txt("pcount", "b", "7/8"), *txt("prole", "b", "SEHERIN"), *txt("pteam", "m", "DORF"),
                                   "vframe:vframe_hot", *txt("vcap1", "b", "STIMMEN"), *txt("vcap2", "m", "GEGEN DICH"), "v2:x1"])
        # an accented capital is its own glyph: DÉBAT, É is U+00C9
        self.assertEqual(seq(H.Facts(), lang="fr_FR")[3:8], ["label:fh_0044", "label:fh_00c9", "label:fh_0042", "label:fh_0041", "label:fh_0054"])
        # a word no language file knows (a role added by a data override) is written as it is
        self.assertEqual(H.tr("fr_FR", "hud.role.oracle", "ORACLE"), "ORACLE")
        self.assertEqual(H.tr("fr_FR", "hud.role.seer", "?"), "VOYANTE")

    def test_7_a_wide_phase_falls_back_to_the_bold_font(self):
        # de_DE: the guard's call is WÄCHTER ERWACHT: 107 px in the headline font, over the 106 of the line, so it
        # goes to "label2" in bold (104 px).
        self.assertEqual(F.width("h", "WÄCHTER ERWACHT"), 107)
        self.assertEqual(F.width("b", "WÄCHTER ERWACHT"), 104)
        f = H.Facts(phase="GUARD", role="GUARD", night=True, actor=True, seconds=20)
        self.assertEqual(seq(f, lang="de_DE")[3:17], txt("label2", "b", "WÄCHTER ERWACHT"))
        self.assertEqual(seq(f)[3:13], txt("label", "h", "GUARD AWAKE"))

    def test_text_layout(self):
        # normalise: capitals, single spaces, ß -> SS, the typographic apostrophe, an accent the fonts lack is dropped
        self.assertEqual(F.normalise("Sorcière,  à toi "), "SORCIÈRE, À TOI")
        self.assertEqual(F.normalise("Straße"), "STRASSE")
        self.assertEqual(F.normalise("d\u2019ours"), "D'OURS")
        self.assertEqual(F.normalise("c\u0153ur"), "COEUR")
        self.assertEqual(F.normalise("ñandú"), "NANDU")
        self.assertEqual(F.normalise("a@b"), "A?B")
        # bold: V, O, T, E are 6 px of ink + 1; a space is 5. "1" is 4 px of ink, "0" and "2" 6, "/" 5.
        self.assertEqual(F.width("b", "VOTE"), 27)
        self.assertEqual(F.width("b", "A B"), 18)
        self.assertEqual(F.width("b", "10/12"), 5 + 7 + 6 + 5 + 7 - 1)
        self.assertEqual(F.width("b", ""), 0)
        self.assertEqual(F.offsets("b", "A B", "left", 84), [("A", 0), ("B", 12)])
        self.assertEqual(F.offsets("b", "VOTE", "right", 40), [("V", 13), ("O", 20), ("T", 27), ("E", 34)])
        self.assertEqual(F.offsets("b", "VOTE", "centre", 40), [("V", 6), ("O", 13), ("T", 20), ("E", 27)])
        # the cut: "VOTE V." would be 41 px
        self.assertEqual(F.fit("b", "VOTE", 27), "VOTE")
        self.assertEqual(F.fit("b", "VOTE VOTE VOTE", 40), "VOTE.")
        # the regular font is proportional: I is 3 px, M 5
        self.assertEqual((F.advance("m", "I"), F.advance("m", "M"), F.advance("m", " ")), (4, 6, 4))

    def test_every_shipped_word_fits_its_line(self):
        keys = set(H.lang_file("en_US"))
        for tag in H.LANGS:
            self.assertEqual(set(H.lang_file(tag)), keys, tag)
            for key, value in H.lang_file(tag).items():
                line = F.normalise(value)
                self.assertTrue(line and "?" not in line, f"{tag} {key}: a character has no glyph in {value!r}")
                kind = key.split(".")[1]
                if kind == "phase":   # the headline, else bold
                    self.assertLessEqual(F.width("b", line), layout.HUD_TEXT["label2"][2], f"{tag} {key} {value!r}")
                    continue
                element = {"role": "prole", "team": "pteam"}.get(kind) or {"line1": "vcap1", "line2": "vcap2"}[key.split(".")[2]]
                font, _, limit = layout.HUD_TEXT[element]
                self.assertLessEqual(F.width(font, line), limit, f"{tag} {key} {value!r} is too wide for {element}")

    def test_english_has_a_key_for_every_word_of_the_game(self):
        keys = set(H.lang_file("en_US"))
        for rid, r in H.ROLES.items():
            self.assertIn("hud.role." + layout.key(r["word"]), keys, rid)
            self.assertIn("hud.team." + layout.key(layout.team_word(r)), keys, rid)
            if "call" in r:
                self.assertIn("hud.phase." + layout.key(r["call"]["label"]), keys, rid)
        for sc in H.scenarios() + H.role_scenarios():
            st = H.state_of(sc.facts)
            self.assertIn("hud.phase." + layout.key(st.label), keys, sc.sid)
            self.assertIn("hud.role." + layout.key(st.role_word), keys, sc.sid)
            self.assertIn("hud.team." + layout.key(st.team_word), keys, sc.sid)

    def test_glyph_colours_and_modes(self):
        # glyph colour = (mode, 100 + element index, parameter); mode(): auto 78, large 79, medium 80, small 81
        f = H.Facts()
        codes = H.glyph_codes(H.render(H.state_of(f), H.mode("small"), strict=True))
        label = [e[0] for e in layout.HUD_ELEMENTS].index("label")
        self.assertEqual(label, layout.HUD_TEXT_FIRST)                 # the text elements come last
        self.assertEqual(codes[0], (81, 100, 0))                       # frame is element 0
        # DEBATE in the headline font: D, E, B, A, T are 6 px of ink + 1, so the cells start every 7 px
        self.assertEqual(codes[3:9], [(81, 100 + label, x) for x in (0, 7, 14, 21, 28, 35)])
        self.assertEqual([H.mode(m) for m in (None, "auto", "large", "medium", "small")], [78, 78, 79, 80, 81])
        # every parameter is a colour channel
        for sc in H.scenarios() + H.role_scenarios():
            for tag in H.LANGS:
                for code in H.glyph_codes(H.render(H.state_of(sc.facts), 78, strict=True, lang=tag)):
                    self.assertTrue(0 <= code[2] <= 255 and 100 <= code[1] <= 255, (sc.sid, code))

    def test_placement_matches_the_shader(self):
        # fx_shaders.py: anchor (floor(gui.x/2), 0) + floor((box + corner) * f + 0.5); GUI scale 3 -> gui 640x360
        self.assertEqual(H.place("frame", 78, 3), (226, 4, 414, 38))
        self.assertEqual(H.place("frame", 81, 3), (320 - 47, 2, 320 + 47, 19))     # f = 0.5
        self.assertEqual(H.place("pframe", 78, 3), (640 - 102, 180 - 39, 640 - 6, 180 + 39))
        # auto: full size down to a 416 x 234 GUI (fit 0.65), then 0.75, then 0.5 under 288 x 162
        self.assertEqual(H.factor(78, 4), 1.0)                                     # gui 480x270: fit 0.75
        self.assertEqual(H.factor(78, 2), 1.0)
        # the time bar: the blue channel's share of its 104 px
        self.assertEqual(H.place("fill", 78, 3, 128), (263, 27, 315, 30))
        self.assertEqual(H.place("fill", 78, 3, 0), (263, 27, 264, 30))
        # text: the cell starts `parameter` px right of the line's origin (label: -58, 9, a 10 x 14 cell)
        self.assertEqual(H.place("label", 78, 3, 7), (269, 9, 279, 23))
        # at 0.75 the origin and the size are rounded apart: floor(-51 * .75 + .5) = -38, 10 -> 8, 14 -> 11
        self.assertEqual(H.place("label", 80, 3, 7), (282, 7, 290, 18))

    def test_every_state_of_the_sheet_has_its_sprites(self):
        for sc in H.scenarios() + H.role_scenarios():
            for tag in H.LANGS:
                for _, _, name, _ in H.render(H.state_of(sc.facts), 78, strict=True, lang=tag):   # raises on a missing name
                    self.assertTrue((H.SPRITES / (name + ".png")).exists(), name)

    def test_all_roles_use_known_palettes(self):
        for rid, r in H.ROLES.items():
            self.assertIn(r["palette"], layout.HUD_PALETTES, rid)


if __name__ == "__main__":
    unittest.main()
