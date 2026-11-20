# État du projet — pause du 2026-11-17 (soir)

## Où on en est
- **Tout est poussé sur `origin/dev`** : merge `5c3e683` (129 commits : refonte lore Skyblock, nouveaux sprites + modèles 3D,
  hub du lore, armures/ailes 3D, galerie `/sbshowcase`, mode **ValoBlock** complet sur le creative).
- Toutes les suites étaient vertes avant le push : arena, survival, network, lobby, `make test-pack`, test HUD,
  `go test ./internal/...`, scripts (70 tests, 1 skip). claims : pas de raccourci de test, pas modifié.
- Le checkout principal est **détaché sur `test/all`** (523a86e). Pour reprendre proprement : `git checkout dev`.
- ~26 worktrees dans `.claude/worktrees/` + leurs branches, **toutes déjà fusionnées dans dev**. Question en suspens :
  les supprimer ou les garder ? (pas encore fait)
- Stack locale arrêtée proprement avant extinction (`docker compose stop`). Relancer : `make up`.
  Elle tourne sur des builds `test/all` (= dev) + un override d'arène creative local (`valoblock.json`,
  spawn -40.5,71,-227.5) qui n'est pas dans git. L'ancienne map cassée est sauvegardée dans
  `state/yuyu-local/creative/broken-maps/vb_arena-20261117`.

## À vérifier en jeu (vu seulement par le client de test)
- Forme des nuages de fumée (le nuage de sable fait un peu « cubique »).
- Scellement de la Seed à main vide.
- Ailes en vue première personne pendant le vol plané.
- Nametags alliés de près ; tests à d'autres échelles d'interface (GUI scale).

## Décisions en attente
- Ailes en 1re personne pendant le vol plané : garder visibles sur les bords (défaut actuel,
  `WingsMath.HIDE_OWN_WHILE_GLIDING = false`) ou cacher (les cache aussi en F5).
- Nettoyage des worktrees/branches fusionnées.

## Problèmes connus / idées
- Deux bots sur le même bloc : deux étiquettes qui se chevauchent.
- Le HUD Werewolf a la même faiblesse de shader (coin basé sur `gl_VertexIndex`) que celle corrigée pour
  ValoBlock (`vbCorner` depuis la position) : à porter.
- Agents ValoBlock : 8 agents originaux (pas de noms/persos Valorant, refus maintenu).

## Prod — à faire AVANT de déployer
- **Sauvegarde obligatoire** : la migration V024 supprime définitivement les tables des fairy souls.
- Pack ValoBlock : servi par le panel (`VALOBLOCK_PACK_FILE`), le cache se recharge tout seul maintenant.
- Clé MineSkin : `secrets/mineskin.key` (hors git) — les skins sont déjà signés dans les JSON des agents.
