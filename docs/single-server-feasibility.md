# Étude : creative et survival dans un seul serveur Paper

Statut : étude, rien n'est implémenté. Proposition étudiée : un serveur « jeu » qui porte creative
(overworld, nether, end) et survival (idem) avec des inventaires séparés, à côté du lobby et des
mini-jeux. Les chiffres de RAM sont les défauts de `compose.prod.yaml` ; le `.env` de production n'a
pas été lu, à vérifier avant de décider.

## Ce que ça gagne, ce que ça coûte

Défauts prod : creative 6G de heap (limite 8g), survival 6G (8g), lobby 1G (2g). Fusionner creative et
survival supprime **une base fixe de JVM** (Paper, métaspace, caches, plugins chargés une fois de moins,
environ 0,5 à 1 Go). Les chunks chargés coûtent autant dans un processus que dans deux.

Coûts : un seul thread de tick pour creative et survival (une ferme ou un gros build ralentit l'autre
groupe) ; un crash ou un redémarrage coupe les deux. Le lobby et les mini-jeux restant à part, le reste du
réseau survit à ça.

Alternatives moins chères, à essayer d'abord :
1. Mesurer le heap réellement utilisé (`/spark health` ou `docker stats`) et baisser `CREATIVE_MEMORY` et
   `SURVIVAL_MEMORY` : 6G est probablement large.
2. Fusionner lobby et mini-jeux seulement (peu de chunks, même logique de hub).

## Ce qu'il faudrait changer

- **Inventaires séparés** : `PlayerInventoryStore.file` lit `getWorlds().get(0)`, un seul dossier de données
  joueur. Il faut un snapshot par groupe échangé à chaque changement de groupe (inventaire, armure, main
  secondaire, ender chest, XP, vie, faim, effets, mode de jeu, vol, point de réapparition). Le panneau
  (vue et édition d'inventaire) doit lire ces stores. `PlayerSnapshotManager` (arena) est une base possible.
  Advancements, stats et recettes de vanilla restent globaux par joueur : creative et survival les
  partageraient (à décider).
- **Changement de groupe** : `WorldSelector`, `LobbySelector` et `CrossServerBridge` passent par le canal
  BungeeCord. Entre creative et survival, ça devient une téléportation plus un échange de snapshot, qui doit
  être atomique (pas de fenêtre où l'inventaire creative est dans un monde survival).
- **Dimensions** : chaque dimension est un `World` Bukkit ; `WorldCreator` avec `Environment.NETHER` ou
  `THE_END` en crée autant qu'on veut, sans Multiverse. Il faut rediriger les portails par groupe
  (`PlayerPortalEvent`, `EntityPortalEvent`). Précédent dans le dépôt : `werewolf/sky/SkyWorld.java` crée
  un monde à l'exécution sur 26.3. L'organisation des dossiers de monde personnalisés sur 26.x n'est pas
  vérifiée (les données joueur ont déjà changé d'emplacement sur 26.x).
- **Réglages par monde** : difficulté, PVP, monstres, protection du spawn sont des propriétés de serveur
  (`compose*.yaml`) ; à convertir en règles de monde. Mode de jeu par groupe à imposer à l'entrée.
- **Claims** : SimpleClaimSystem ne tourne que sur creative (`infra/start.sh`) ; à restreindre aux mondes
  creative.
- **SERVER_ID** : lu dans environ 8 fichiers (`NexusArenaEngine`, `CrossServerBridge`, `WorldSelector`,
  `NetworkDisplay`, `ManagementApi`, `CommunityFeatures`...).
- **Lobby** : `LobbySelector.move` et `rescue` s'appliquent à tout monde du serveur ; seulement utile si le
  lobby rejoint le même processus (alternative 2), à limiter alors à son monde.
- **Infra** : déclaration des serveurs dans Velocity, controller (volumes et secrets RCON par serveur),
  panel, `scripts/world_archive.py` (export/import par serveur), labels des conteneurs.
- **Migration** : script hors ligne qui renomme les mondes et convertit les `playerdata` de creative et
  survival en snapshots par groupe, avec `make backup` avant.
- **Test** : le stack docker local et `scripts/e2e_client.py` tournent tous deux en Paper 26.3.

## Recommandation

Gain réel mais modeste (environ une JVM, 1 à 2 Go) pour un refactor large (estimation 2 à 4 semaines, dont
la moitié pour les snapshots et la migration) et une perte d'isolation. Mesurer d'abord (alternative 1) ;
si la RAM reste le problème, envisager la fusion creative + survival, après avoir décidé du partage des
advancements et stats.
