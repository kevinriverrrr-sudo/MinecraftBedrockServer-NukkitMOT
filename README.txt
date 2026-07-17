========================================
  Minecraft Bedrock Server
  Based on Nukkit-MOT
  With 7 Custom Plugins + GeyserMC
========================================

CONTENTS:
---------
1. Nukkit-MOT.jar     - Bedrock server core (Nukkit-MOT build)
2. plugins/           - 7 custom plugin JARs
3. geyser/            - GeyserMC proxy for cross-platform play
4. log4j2.xml         - Logging configuration
5. server.properties  - Server settings
6. start.sh           - Start script (Linux)
7. stop.sh            - Stop script (Linux)

PLUGINS:
--------
1. CustomPerms.jar      - Custom permissions system (LuckPerms alternative)
   Commands: /perms group|user|reload
   Features: Groups, inheritance, wildcards, negation, timed groups

2. PvPSystem.jar        - PvP with combos, kill streaks, arenas, ELO
   Commands: /pvp, /pvpstats, /pvpleaderboard
   Features: Combo tracker, kill streaks, custom death messages, ELO rating, arena duels

3. CustomChat.jar       - Custom chat with ranks, channels, filtering
   Commands: /msg, /reply, /ignore, /channel, /nick
   Features: 4 channels, anti-spam, profanity filter, @mentions, nicknames

4. KitSystem.jar        - Kit system with cooldowns and custom items
   Commands: /kit, /kitadmin, /customitem
   Features: 5 kits (starter, pvp, archer, vip, premium), 2 custom items

5. DonationSystem.jar   - Donation ranks with perks and codes
   Commands: /donate, /fly, /heal, /feed, /repair, /hat, /workbench, /enderchest, /back
   Features: 4 ranks (VIP, Premium, Elite, Legend), donation codes, perk commands

6. CustomDungeons.jar   - Dungeon system with bosses and loot
   Commands: /dungeon
   Features: 3 dungeons (Undead Crypt, Fire Temple, Void Fortress), parties, boss fights, loot

7. ServerAesthetics.jar - Server visual polish
   Commands: /spawn, /setspawn, /serverinfo, /scoreboard
   Features: Side scoreboard, tab list, join effects, auto-broadcast

GEYSERMC:
---------
GeyserMC allows Java Edition players to connect to this Bedrock server.
Bedrock players connect on port 19132 (direct).
Java players connect on port 19133 (via GeyserMC proxy).

HOW TO START:
-------------
1. Make sure you have Java 17+ installed
2. Run: chmod +x start.sh && ./start.sh
3. For first run, the server will generate worlds automatically
4. Set yourself as OP: type "op YourName" in the server console

HOW TO CONNECT:
---------------
Bedrock: Server IP + Port 19132
Java:    Server IP + Port 19133

DEFAULT GROUPS (CustomPerms):
-----------------------------
- player   (default, basic permissions)
- vip      (donation rank, fly + hat + workbench)
- premium  (donation rank, +heal +feed +back +enderchest)
- admin    (full admin access)

DONATION RANKS (DonationSystem):
---------------------------------
- VIP      ($5)   - fly, hat, workbench, VIP kit
- Premium  ($10)  - +heal, feed, back, enderchest, Premium kit
- Elite    ($20)  - permanent, +repair, Elite kit
- Legend   ($50)  - permanent, all perks, Legend kit, nick

COMMANDS CHEAT SHEET:
---------------------
/perms group create <name>              - Create permission group
/perms user <player> setgroup <group>   - Set player's group
/perms user <player> addperm <perm>     - Add permission to player
/donategive <player> <rank> [duration]  - Give donation rank
/donategencode <rank> [amount] [dur]    - Generate donation codes
/kit list                               - List available kits
/kit <name>                             - Claim a kit
/pvp arena create <name>                - Create PvP arena
/dungeon enter <name>                   - Enter a dungeon
/dungeon party create                   - Create dungeon party
/spawn                                  - Teleport to spawn
/setspawn                               - Set spawn point (admin)
