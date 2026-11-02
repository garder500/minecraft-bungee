# Movement and gravity prototypes (GDD 12.5, issue #47)

Code: `survival-plugin/.../movement/` (`MovementMath` pure and unit-tested, `MovementService`, `MovementFeature`).
Staff command `/vmove` (permission `vampyuyu.sb.movement.test`, op) opens each mechanic 4 blocks ahead, after a 2 s telegraph.

| Mechanic | Where it lives | How to try it |
|---|---|---|
| Levitation column | `MovementService` kind LIFT (amp <= 2, 3 s per refresh) | `/vmove lift [amp] [seconds]` |
| Slow Falling zone | kind SLOW_FALL; removed as soon as the player leaves | `/vmove slowfall [seconds]` |
| Jump Boost zone | kind JUMP (amp <= 3) | `/vmove jump [amp] [seconds]` |
| Controlled knockback | `knockback()` / `push()` -> `setVelocity`, strength <= 1.6, up 0.8 | `/vmove knock [strength]` |
| Pull toward a point | kind PULL: dead zone 0.75, falls off to 40 % at the rim, sneak resists 50 %, anchors scale it to 0 | `/vmove pull [strength] [radius] [seconds] [anchors]`, `/vmove anchor <zone>` |
| Moving platforms | already prototyped in `StormRealm` (`PlatformMath`, world-clock driven, players carried) | Tempestuous Archipelago |
| Propulsion pads | already in `StormRealm` (`PadMath`, speeds capped, Slow Falling during flight) | Tempestuous Archipelago |

## Safety rules (all in the service)

- Speeds are capped everywhere: 1.8 b/t horizontal, 1.2 up, 0.9 down (`MovementMath.cap`).
- A player is *guarded* while inside a zone and 10 s after a push. A void fall of a guarded player is cancelled and they return to the
  last solid, dry spot they stood on (recorded every 2 ticks). Fall damage of a guarded player never kills (`safeDamage`).
- Every zone telegraphs first (smoke ring), then acts (end-rod ring), then ends by itself. Effects given by a zone are only
  refreshed 10 ticks at a time and removed on exit; an effect from another source is never overridden.
- Counter-measures: pull = sneak + destroy anchors; knockback is a single capped impulse.

## Not validated (needs the dev server, `make dev-server`)

Nothing was compiled or run in the thread that wrote this (no Paper/Docker). Still to check with a real client:
tick-rate feel of the pull at 100-200 ms latency (the pull adds to the *server-side* velocity every 2 ticks, so it should tolerate lag but
may rubber-band), levitation column height vs. Wings glide, void rescue with a Wing glide, and `BlockDisplay` platforms with barrier
blocks (the Archipelago platforms use real blocks; a smoother `BlockDisplay` version is not built). The numbers (caps, dead zone,
falloff) are first guesses.
