#!/usr/bin/env python3
"""Checks the Python port of CustomHud (resource-pack/art/hud_sheet.py) against glyph sequences
derived BY HAND from arena-plugin/src/main/java/win/vampyuyu/arena/CustomHud.java.

How each expected list was derived (read CustomHud.of() for the state, then render() for the order):
  of():     palette = dead ? "dead" : bossColor == INK_DIM ? "sleep" : martyr-pause-for-others ? "vote"
            : turnRole(phase) != null ? role.palette : DAY_VOTE/TRIAL/ELECTION ? "vote" : "villager".
            label: FINISHED "game over"; day phases debate/vote/election/defense/trial; a sleeping night
            viewer "sleeping"; a called night actor "<role> awake"; exiled by day "away" wins last.
            INK_DIM (bossColor) is the colour of an exiled day viewer and of a night sleeper.
  render(): frame, icon, round(clamped 0-9), label, then the clock only if `timed` (digits of m:ss,
            seconds clamped to 9:59; at <= 5 s the digits are the red "x" set and, on even seconds, the
            plate is "danger"), then if the role panel is shown: pframe, picon, plabel, pteam, alive
            "%2d" (a space is dblank), dslash, total "%2d", one m<i> dot per seat (max 20), one b<i>
            badge (max 4), and, when against >= 0, vframe_calm/hot with two digits ("x" digits when hot).
Run: python3 scripts/test_hud_sheet.py   (also part of `make test-pack`)
"""
import pathlib, sys, unittest

ROOT = pathlib.Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "resource-pack" / "art"))
sys.path.insert(0, str(ROOT / "resource-pack"))
import hud_sheet as H  # noqa: E402
import layout  # noqa: E402


def seq(facts, md=78):
    return [f"{e}:{n}" for _, e, n, _ in H.render(H.state_of(facts), md, strict=True)]


class HudPortTest(unittest.TestCase):
    def test_1_day_debate_villager(self):
        # DISCUSSION by day, a living villager, 87 s = 1:27, 10 of 12 alive, four seats, no badge.
        # palette: not dead, not dim, turnRole(DISCUSSION)=null, not a vote phase -> villager; label debate;
        # icon sun (not night, not DAY_VOTE); against = -1 (not DAY_VOTE).
        f = H.Facts(phase="DISCUSSION", role="VILLAGER", round=2, seconds=87, alive=10, total=12,
                    seats=("alive", "dead", "alive", "me"))
        self.assertEqual(seq(f), [
            "frame:frame_villager", "icon:icon_sun", "round:s2", "label:label_debate",
            "t1:d1", "tcolon:dcolon", "t3:d2", "t4:d7",
            "pframe:pframe_villager", "picon:picon_villager", "plabel:plabel_villager", "pteam:pteam_village",
            "a1:d1", "a2:d0", "aslash:dslash", "a3:d1", "a4:d2",
            "m0:dot_alive", "m1:dot_dead", "m2:dot_alive", "m3:dot_me"])

    def test_2_night_seer_danger_odd_second(self):
        # SEER night call, the seer is the actor, 3 s left. turnRole(SEER)=SEER -> palette seer (seer.json);
        # label "seer awake"; icon moon. 3 <= DANGER_SECONDS: the clock is the red x set "0:03", but 3 is odd
        # so the plate keeps the seer palette (it only flashes on even seconds). alive " 9" -> dblank, d9.
        f = H.Facts(phase="SEER", role="SEER", night=True, actor=True, round=1, seconds=3, alive=9, total=12)
        self.assertEqual(seq(f), [
            "frame:frame_seer", "icon:icon_moon", "round:s1", "label:label_seer_awake",
            "t1:x0", "tcolon:xcolon", "t3:x0", "t4:x3",
            "pframe:pframe_seer", "picon:picon_seer", "plabel:plabel_seer", "pteam:pteam_village",
            "a1:dblank", "a2:d9", "aslash:dslash", "a3:d1", "a4:d2"])

    def test_3_day_vote_witch_hot_with_badges(self):
        # DAY_VOTE, a witch holding both potions, 45 s, 7 of 8 alive, 3 ballots against her.
        # palette: DAY_VOTE -> vote; label vote; icon vote. against = votes(viewer)[viewer] = 3 (> 0: hot):
        # vframe_hot, then " 3" -> dblank (v1) and x3 (v2). badges life, death -> b0, b1.
        f = H.Facts(phase="DAY_VOTE", role="WITCH", round=3, seconds=45, alive=7, total=8,
                    seats=("me", "alive"), badges=("life", "death"), votes_against=3)
        self.assertEqual(seq(f), [
            "frame:frame_vote", "icon:icon_vote", "round:s3", "label:label_vote",
            "t1:d0", "tcolon:dcolon", "t3:d4", "t4:d5",
            "pframe:pframe_witch", "picon:picon_witch", "plabel:plabel_witch", "pteam:pteam_village",
            "a1:dblank", "a2:d7", "aslash:dslash", "a3:dblank", "a4:d8",
            "m0:dot_me", "m1:dot_alive", "b0:badge_life", "b1:badge_death",
            "vframe:vframe_hot", "v1:dblank", "v2:x3"])

    def test_4_dead_viewer_game_over(self):
        # FINISHED: boss() is (VOTE, no timer) -> no clock glyphs; dead -> palette dead everywhere;
        # label "game over"; role icon dead, word fallen, team out; 3 of 12 alive; against stays -1.
        f = H.Facts(phase="FINISHED", role="WEREWOLF", dead=True, round=4, seconds=0, alive=3, total=12)
        self.assertEqual(seq(f), [
            "frame:frame_dead", "icon:icon_sun", "round:s4", "label:label_game_over",
            "pframe:pframe_dead", "picon:picon_dead", "plabel:plabel_fallen", "pteam:pteam_out",
            "a1:dblank", "a2:d3", "aslash:dslash", "a3:d1", "a4:d2"])

    def test_5_exiled_minion_clock_clamped(self):
        # An exiled villager by day who is the wolves' minion: boss() gives INK_DIM with a timer -> palette
        # sleep, label "away" (the exile override comes last). 600 s clamps to 9:59 -> "959". Round 12
        # clamps to s9. Team word: isMinion -> "wolf side". Seat "mate" dot for a pack mate.
        f = H.Facts(phase="DISCUSSION", role="VILLAGER", exiled=True, minion=True, round=12, seconds=600,
                    alive=6, total=7, seats=("mate",))
        self.assertEqual(seq(f), [
            "frame:frame_sleep", "icon:icon_sun", "round:s9", "label:label_away",
            "t1:d9", "tcolon:dcolon", "t3:d5", "t4:d9",
            "pframe:pframe_villager", "picon:picon_villager", "plabel:plabel_villager", "pteam:pteam_wolf_side",
            "a1:dblank", "a2:d6", "aslash:dslash", "a3:dblank", "a4:d7", "m0:dot_mate"])

    def test_glyph_colours_and_modes(self):
        # glyph colour = (mode, 100 + element index, parameter); mode(): auto 78, large 79, medium 80, small 81
        f = H.Facts()
        codes = H.glyph_codes(H.render(H.state_of(f), H.mode("small"), strict=True))
        self.assertEqual(codes[0], (81, 100, 0))                       # frame is element 0
        self.assertEqual(codes[3], (81, 103, 0))                       # label is element 3
        self.assertEqual([H.mode(m) for m in (None, "auto", "large", "medium", "small")], [78, 78, 79, 80, 81])

    def test_placement_matches_the_shader(self):
        # fx_shaders.py: anchor (floor(gui.x/2), 0) + floor((box + corner) * f + 0.5); GUI scale 3 -> gui 640x360
        self.assertEqual(H.place("frame", 78, 3), (195, 4, 445, 48))
        self.assertEqual(H.place("frame", 81, 3), (320 - 62, 2, 320 + 63, 24))     # f = 0.5
        self.assertEqual(H.place("pframe", 78, 3), (640 - 134, 180 - 54, 640 - 6, 180 + 54))
        self.assertEqual(H.factor(78, 4), 0.75)                                    # gui 480x270: fit 0.75
        self.assertEqual(H.factor(78, 2), 1.0)

    def test_every_state_of_the_sheet_has_its_sprites(self):
        for sc in H.scenarios() + H.role_scenarios():
            for _, _, name, _ in H.render(H.state_of(sc.facts), 78, strict=True):   # raises on a missing name
                self.assertTrue((H.SPRITES / (name + ".png")).exists(), name)

    def test_all_roles_use_known_palettes(self):
        for rid, r in H.ROLES.items():
            self.assertIn(r["palette"], layout.HUD_PALETTES, rid)


if __name__ == "__main__":
    unittest.main()
