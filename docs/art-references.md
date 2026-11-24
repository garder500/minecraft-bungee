# Art references and rules (2026-11-24)

Style references the owner chose (inspiration, never copies):
- `docs/art-refs/swords-style.webp`: stylised 3D pixel swords, the standard for Skyblock weapons. Thick stepped or crystalline
  blades, multi-box guards with a gold-framed gem, banded grips, gem pommels, 3-4 hand-painted tones per material, one identity
  colour per sword, gold as the precious accent. Rarity shows in the silhouette and the glow.
- `docs/art-refs/valoblock-guns-style.png`: realistic low-poly guns, the standard for ValoBlock. Real-world archetypes and proportions
  (bolt sniper, big revolver, classic rifle with wood furniture, chrome pistol, tan polymer pistol, SMG with vertical grip) and,
  above all, varied materials: wood, chrome, tan and olive polymer, blued steel, not everything black.

Web research (Blockbench guides; few visual examples could be verified):
- Blockbench Minecraft style guide: https://www.blockbench.net/wiki/guides/minecraft-style-guide
- Emissive textures: https://www.blockbench.net/wiki/guides/emissive-textures-renders
- Overview tips: https://www.blockbench.net/wiki/guides/blockbench-overview-tips
- ModelEngine modelling notes: https://wiki.mythiccraft.io/modelengine/Modeling/Creating-a-Model
- HUD priorities (primary crosshair, health, ammo; secondary minimap, abilities; tertiary hidden): https://rocketbrush.com/blog/designing-practical-and-pretty-hud-in-video-games
- Detail-density benchmark (showcase only, all rights reserved, do not copy): https://blockbenchworkshop.com/model/matijas_workshop/guns (286 elements, one 196x261 texture)

Rules:
1. One texel density for the whole model (pick 1 or 2 texels per model pixel and keep it on every face): no stretched or "mixel" faces.
2. No element thinner than the texel size makes readable; no irregular inflate.
3. Per material: a mid tone, a shadow and a light first, then more; shift the hue along the ramp instead of only darkening.
4. Light from the top left: top and front lighter than bottom and back. Bake it (elements are shade: false here).
5. No pillow shading (darkening only the borders of each face) and no noise dithering; detail must say something (a seam, a screw, wear on an edge).
6. As few elements as readability allows, but enough for the silhouette: a weapon of the reference quality is 40 to 120 boxes.
7. No coplanar overlapping faces. Pivots at the joint.
8. Emissive parts: bright texels (and light_emission on the element) only where something glows.
9. Every texel shown stays above 25 % grey (`scripts/test_valoblock_models.py`): a dark material is a dark grey, not black.
10. Rarity reads at a glance: the rarer, the more elaborate the silhouette, the more precious the trim, the more glow.
