package com.server.pvp;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.level.Position;
import cn.nukkit.scheduler.Task;
import cn.nukkit.utils.Config;
import cn.nukkit.utils.ConfigSection;

import java.io.File;
import java.util.*;

/**
 * Manages PvP arenas, queues, and active matches.
 */
public class ArenaManager {

    private final PvPSystemPlugin plugin;
    private final Map<String, Arena> arenas;
    private final Map<String, ArenaMatch> activeMatches;
    private final Map<String, List<Player>> queues;
    private final Map<UUID, ArenaMatch> playerMatches;
    private final Map<UUID, String> spectators;
    private final Map<UUID, Position> wandPos1;
    private final Map<UUID, Position> wandPos2;
    private Config arenasConfig;

    public ArenaManager(PvPSystemPlugin plugin) {
        this.plugin = plugin;
        this.arenas = new LinkedHashMap<>();
        this.activeMatches = new HashMap<>();
        this.queues = new HashMap<>();
        this.playerMatches = new HashMap<>();
        this.spectators = new HashMap<>();
        this.wandPos1 = new HashMap<>();
        this.wandPos2 = new HashMap<>();
    }

    /**
     * Load arenas from the arenas.yml config file.
     */
    public void loadArenas() {
        File arenasFile = new File(plugin.getDataFolder(), "arenas.yml");
        arenasConfig = new Config(arenasFile, Config.YAML);
        arenas.clear();

        for (String arenaName : arenasConfig.getKeys()) {
            ConfigSection section = arenasConfig.getSection(arenaName);
            Arena arena = new Arena(arenaName);

            if (section.exists("pos1")) {
                arena.setPos1(deserializePosition(section.getSection("pos1")));
            }
            if (section.exists("pos2")) {
                arena.setPos2(deserializePosition(section.getSection("pos2")));
            }
            if (section.exists("spawn1")) {
                arena.setSpawn1(deserializePosition(section.getSection("spawn1")));
            }
            if (section.exists("spawn2")) {
                arena.setSpawn2(deserializePosition(section.getSection("spawn2")));
            }
            arena.setMaxPlayers(section.getInt("maxPlayers", 2));

            arenas.put(arenaName.toLowerCase(), arena);
        }

        plugin.getLogger().info("Loaded " + arenas.size() + " arenas.");
    }

    /**
     * Save arenas to the arenas.yml config file.
     */
    public void saveArenas() {
        if (arenasConfig == null) {
            File arenasFile = new File(plugin.getDataFolder(), "arenas.yml");
            arenasConfig = new Config(arenasFile, Config.YAML);
        }

        for (Map.Entry<String, Arena> entry : arenas.entrySet()) {
            String name = entry.getKey();
            Arena arena = entry.getValue();

            if (arena.getPos1() != null) {
                arenasConfig.set(name + ".pos1", serializePosition(arena.getPos1()));
            }
            if (arena.getPos2() != null) {
                arenasConfig.set(name + ".pos2", serializePosition(arena.getPos2()));
            }
            if (arena.getSpawn1() != null) {
                arenasConfig.set(name + ".spawn1", serializePosition(arena.getSpawn1()));
            }
            if (arena.getSpawn2() != null) {
                arenasConfig.set(name + ".spawn2", serializePosition(arena.getSpawn2()));
            }
            arenasConfig.set(name + ".maxPlayers", arena.getMaxPlayers());
        }
        arenasConfig.save();
    }

    private Map<String, Object> serializePosition(Position pos) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("world", pos.getLevel().getName());
        map.put("x", pos.getX());
        map.put("y", pos.getY());
        map.put("z", pos.getZ());
        return map;
    }

    private Position deserializePosition(ConfigSection section) {
        if (section == null) return null;
        String world = section.getString("world");
        double x = section.getDouble("x");
        double y = section.getDouble("y");
        double z = section.getDouble("z");
        if (Server.getInstance().getLevelByName(world) == null) return null;
        return new Position(x, y, z, Server.getInstance().getLevelByName(world));
    }

    // --- Arena CRUD ---

    public Arena createArena(String name) {
        Arena arena = new Arena(name);
        arenas.put(name.toLowerCase(), arena);
        saveArenas();
        return arena;
    }

    public boolean deleteArena(String name) {
        String key = name.toLowerCase();
        if (!arenas.containsKey(key)) return false;
        if (activeMatches.containsKey(key)) return false;

        arenas.remove(key);
        queues.remove(key);
        arenasConfig.remove(key);
        arenasConfig.save();
        return true;
    }

    public Arena getArena(String name) {
        return arenas.get(name.toLowerCase());
    }

    public Collection<Arena> getArenas() {
        return arenas.values();
    }

    public boolean arenaExists(String name) {
        return arenas.containsKey(name.toLowerCase());
    }

    // --- Wand (position selection) ---

    public void setWandPos1(Player player, Position pos) {
        wandPos1.put(player.getUniqueId(), pos);
    }

    public void setWandPos2(Player player, Position pos) {
        wandPos2.put(player.getUniqueId(), pos);
    }

    public Position getWandPos1(Player player) {
        return wandPos1.get(player.getUniqueId());
    }

    public Position getWandPos2(Player player) {
        return wandPos2.get(player.getUniqueId());
    }

    public boolean hasWandSelection(Player player) {
        return wandPos1.containsKey(player.getUniqueId()) && wandPos2.containsKey(player.getUniqueId());
    }

    public void clearWandSelection(Player player) {
        wandPos1.remove(player.getUniqueId());
        wandPos2.remove(player.getUniqueId());
    }

    // --- Queue System ---

    /**
     * Add a player to an arena queue.
     */
    public boolean joinQueue(Player player, String arenaName) {
        String key = arenaName.toLowerCase();
        if (!arenas.containsKey(key)) return false;

        Arena arena = arenas.get(key);
        if (!arena.isReady()) return false;

        if (isInMatch(player) || isInQueue(player)) return false;

        queues.computeIfAbsent(key, k -> new ArrayList<>());
        List<Player> queue = queues.get(key);

        if (queue.size() >= arena.getMaxPlayers() * 2) return false;

        queue.add(player);
        player.sendMessage("§aYou joined the queue for arena §e" + arenaName + "§a. §7(" + queue.size() + "/" + (arena.getMaxPlayers() * 2) + ")");

        // Check if enough players for a match
        checkQueue(key);
        return true;
    }

    /**
     * Remove a player from any queue.
     */
    public boolean leaveQueue(Player player) {
        for (Map.Entry<String, List<Player>> entry : queues.entrySet()) {
            if (entry.getValue().remove(player)) {
                player.sendMessage("§aYou left the queue for arena §e" + entry.getKey() + "§a.");
                return true;
            }
        }
        return false;
    }

    /**
     * Check if a player is in any queue.
     */
    public boolean isInQueue(Player player) {
        for (List<Player> queue : queues.values()) {
            if (queue.contains(player)) return true;
        }
        return false;
    }

    /**
     * Check if enough players are in the queue to start a match.
     */
    private void checkQueue(String arenaKey) {
        Arena arena = arenas.get(arenaKey);
        if (arena == null) return;

        List<Player> queue = queues.get(arenaKey);
        if (queue == null) return;

        int neededPlayers = arena.getMaxPlayers() * 2;
        if (queue.size() >= neededPlayers) {
            // Start a match with the first neededPlayers players
            List<Player> matchedPlayers = new ArrayList<>();
            for (int i = 0; i < neededPlayers; i++) {
                matchedPlayers.add(queue.get(i));
            }
            queue.removeAll(matchedPlayers);

            startMatch(arena, matchedPlayers);
        }
    }

    // --- Match System ---

    /**
     * Start a match in an arena with the given players.
     */
    public void startMatch(Arena arena, List<Player> players) {
        int countdownSec = plugin.getConfig().getInt("arena.countdown", 5);
        int matchDuration = plugin.getConfig().getInt("arena.match-duration", 300);

        ArenaMatch match = new ArenaMatch(arena, countdownSec, matchDuration);

        // Split players into teams
        int half = players.size() / 2;
        for (int i = 0; i < players.size(); i++) {
            Player p = players.get(i);
            if (i < half) {
                match.getTeam1().add(p);
            } else {
                match.getTeam2().add(p);
            }
            match.saveOriginalState(p);
            playerMatches.put(p.getUniqueId(), match);
        }

        arena.setActive(true);
        activeMatches.put(arena.getName().toLowerCase(), match);
        match.setState(ArenaMatch.MatchState.COUNTDOWN);

        // Teleport players to spawn points
        for (Player p : match.getTeam1()) {
            if (arena.getSpawn1() != null) {
                p.teleport(arena.getSpawn1());
            }
            p.sendMessage("§eMatch starting! You are on §aTeam 1§e.");
        }
        for (Player p : match.getTeam2()) {
            if (arena.getSpawn2() != null) {
                p.teleport(arena.getSpawn2());
            }
            p.sendMessage("§eMatch starting! You are on §cTeam 2§e.");
        }

        // Start countdown task
        startCountdownTask(match);
    }

    /**
     * Start the countdown task for a match.
     */
    private void startCountdownTask(ArenaMatch match) {
        plugin.getServer().getScheduler().scheduleRepeatingTask(plugin, new Task() {
            private int ticks = 0;

            @Override
            public void onRun(int currentTick) {
                if (match.getState() != ArenaMatch.MatchState.COUNTDOWN) {
                    this.cancel();
                    return;
                }

                // Run every second (20 ticks)
                ticks++;
                if (ticks % 20 != 0) return;

                int remaining = match.getCountdown();
                if (remaining <= 0) {
                    match.setState(ArenaMatch.MatchState.FIGHTING);
                    for (Player p : match.getAllPlayers()) {
                        p.sendMessage("§a§lFIGHT!");
                        p.sendTitle("§a§lFIGHT!", "", 5, 20, 10);
                    }
                    this.cancel();
                    startMatchTimerTask(match);
                    return;
                }

                for (Player p : match.getAllPlayers()) {
                    p.sendTitle("§e" + remaining, "", 5, 15, 5);
                    p.sendMessage("§eMatch starts in §c" + remaining + " §eseconds!");
                }
                match.decrementCountdown();
            }
        }, 1, true);
    }

    /**
     * Start the match timer task.
     */
    private void startMatchTimerTask(ArenaMatch match) {
        plugin.getServer().getScheduler().scheduleRepeatingTask(plugin, new Task() {
            private int ticks = 0;

            @Override
            public void onRun(int currentTick) {
                if (match.getState() != ArenaMatch.MatchState.FIGHTING) {
                    this.cancel();
                    return;
                }

                // Run every second (20 ticks)
                ticks++;
                if (ticks % 20 != 0) return;

                match.incrementElapsed();

                // Check if match is over
                if (match.isMatchOver()) {
                    endMatch(match);
                    this.cancel();
                    return;
                }

                // Show time remaining for every 30 seconds
                int remaining = match.getRemainingTime();
                if (remaining <= 30 && remaining % 10 == 0) {
                    for (Player p : match.getAllPlayers()) {
                        p.sendMessage("§e" + remaining + " §eseconds remaining!");
                    }
                }

                // Show action bar timer
                int minutes = remaining / 60;
                int seconds = remaining % 60;
                String timeStr = String.format("§eTime: §f%d:%02d", minutes, seconds);
                for (Player p : match.getAllPlayers()) {
                    p.sendActionBar(timeStr);
                }
            }
        }, 1, true);
    }

    /**
     * End a match and determine the winner.
     */
    public void endMatch(ArenaMatch match) {
        match.setState(ArenaMatch.MatchState.FINISHED);

        int winnerTeam = match.determineWinner();
        String winnerName;
        String loserName;

        List<Player> allPlayers = match.getAllPlayers();

        if (winnerTeam == 1) {
            winnerName = match.getTeam1().isEmpty() ? "Team 1" : match.getTeam1().get(0).getName();
            loserName = match.getTeam2().isEmpty() ? "Team 2" : match.getTeam2().get(0).getName();
            for (Player p : allPlayers) {
                if (match.isTeam1(p)) {
                    p.sendTitle("§a§lVICTORY!", "", 10, 40, 20);
                    p.sendMessage("§a§lYou won the match!");
                } else {
                    p.sendTitle("§c§lDEFEAT", "", 10, 40, 20);
                    p.sendMessage("§c§lYou lost the match!");
                }
            }
        } else if (winnerTeam == 2) {
            winnerName = match.getTeam2().isEmpty() ? "Team 2" : match.getTeam2().get(0).getName();
            loserName = match.getTeam1().isEmpty() ? "Team 1" : match.getTeam1().get(0).getName();
            for (Player p : allPlayers) {
                if (match.isTeam2(p)) {
                    p.sendTitle("§a§lVICTORY!", "", 10, 40, 20);
                    p.sendMessage("§a§lYou won the match!");
                } else {
                    p.sendTitle("§c§lDEFEAT", "", 10, 40, 20);
                    p.sendMessage("§c§lYou lost the match!");
                }
            }
        } else {
            winnerName = null;
            loserName = null;
            for (Player p : allPlayers) {
                p.sendTitle("§e§lDRAW!", "", 10, 40, 20);
                p.sendMessage("§e§lThe match ended in a draw!");
            }
        }

        // Update ELO if we have a clear winner and loser
        if (winnerName != null && loserName != null) {
            int[] eloChanges = plugin.getStatsManager().updateElo(winnerName, loserName);
            for (Player p : allPlayers) {
                if (p.getName().equalsIgnoreCase(winnerName)) {
                    p.sendMessage("§bELO: §a+" + eloChanges[0] + " §7(" + plugin.getStatsManager().getStats(winnerName).getElo() + ")");
                } else if (p.getName().equalsIgnoreCase(loserName)) {
                    p.sendMessage("§bELO: §c" + eloChanges[1] + " §7(" + plugin.getStatsManager().getStats(loserName).getElo() + ")");
                }
            }
        }

        // Teleport players back to original positions after a short delay
        plugin.getServer().getScheduler().scheduleDelayedTask(plugin, new Task() {
            @Override
            public void onRun(int currentTick) {
                for (Player p : match.getAllPlayers()) {
                    Position original = match.getOriginalPosition(p.getUniqueId());
                    if (original != null) {
                        p.teleport(original);
                    }
                    Double health = match.getOriginalHealth(p.getUniqueId());
                    if (health != null) {
                        p.setHealth(health.floatValue());
                    }
                    Integer food = match.getOriginalFood(p.getUniqueId());
                    if (food != null) {
                        p.getFoodData().setLevel(food);
                    }
                    playerMatches.remove(p.getUniqueId());
                }

                // Notify spectators
                for (Map.Entry<UUID, String> entry : new HashMap<>(spectators).entrySet()) {
                    if (entry.getValue().equalsIgnoreCase(match.getArena().getName())) {
                        Player spectator = Server.getInstance().getPlayer(entry.getKey()).orElse(null);
                        if (spectator != null) {
                            spectator.sendMessage("§eThe match in arena §f" + match.getArena().getName() + " §ehas ended.");
                        }
                        spectators.remove(entry.getKey());
                    }
                }

                match.getArena().setActive(false);
                activeMatches.remove(match.getArena().getName().toLowerCase());
            }
        }, 100); // 5 second delay

        // Save stats
        plugin.getStatsManager().saveStats();
    }

    // --- Match Queries ---

    public boolean isInMatch(Player player) {
        return playerMatches.containsKey(player.getUniqueId());
    }

    public ArenaMatch getPlayerMatch(Player player) {
        return playerMatches.get(player.getUniqueId());
    }

    public ArenaMatch getActiveMatch(String arenaName) {
        return activeMatches.get(arenaName.toLowerCase());
    }

    /**
     * Handle a player death in a match.
     */
    public void handleMatchDeath(Player victim, Player killer) {
        ArenaMatch match = playerMatches.get(victim.getUniqueId());
        if (match == null) return;

        match.addDeath(victim.getUniqueId());
        if (killer != null) {
            match.addKill(killer.getUniqueId());
        }

        // Check if match is over
        if (match.isMatchOver()) {
            endMatch(match);
        }
    }

    /**
     * Handle a player leaving during a match.
     */
    public void handlePlayerLeaveMatch(Player player) {
        ArenaMatch match = playerMatches.get(player.getUniqueId());
        if (match == null) return;

        // Count as death
        match.addDeath(player.getUniqueId());
        match.removePlayer(player);
        playerMatches.remove(player.getUniqueId());

        // Notify other players
        for (Player p : match.getAllPlayers()) {
            p.sendMessage("§c" + player.getName() + " §7left the match!");
        }

        // Check if match is over
        if (match.isMatchOver()) {
            endMatch(match);
        }
    }

    // --- Spectator System ---

    /**
     * Add a player as a spectator to an arena match.
     */
    public boolean spectate(Player player, String arenaName) {
        String key = arenaName.toLowerCase();
        if (!activeMatches.containsKey(key)) {
            player.sendMessage("§cNo active match in arena §e" + arenaName + "§c.");
            return false;
        }

        ArenaMatch match = activeMatches.get(key);
        spectators.put(player.getUniqueId(), key);

        // Teleport to the center of the arena
        Arena arena = match.getArena();
        if (arena.getSpawn1() != null) {
            Position center = new Position(
                    (arena.getSpawn1().getX() + arena.getSpawn2().getX()) / 2,
                    Math.max(arena.getSpawn1().getY(), arena.getSpawn2().getY()) + 5,
                    (arena.getSpawn1().getZ() + arena.getSpawn2().getZ()) / 2,
                    arena.getSpawn1().getLevel()
            );
            player.teleport(center);
        }

        player.setGamemode(3); // Spectator mode
        player.sendMessage("§aYou are now spectating the match in §e" + arenaName + "§a.");
        return true;
    }

    /**
     * Remove a player from spectating.
     */
    public void stopSpectating(Player player) {
        if (spectators.containsKey(player.getUniqueId())) {
            spectators.remove(player.getUniqueId());
            player.setGamemode(0); // Survival mode
            player.sendMessage("§aYou are no longer spectating.");
        }
    }

    public boolean isSpectating(Player player) {
        return spectators.containsKey(player.getUniqueId());
    }

    // --- Leave System ---

    /**
     * Handle a player leaving arena/queue/spectating.
     */
    public void leave(Player player) {
        if (isInMatch(player)) {
            handlePlayerLeaveMatch(player);
            player.sendMessage("§aYou left the match.");
            return;
        }
        if (isSpectating(player)) {
            stopSpectating(player);
            return;
        }
        if (isInQueue(player)) {
            leaveQueue(player);
            return;
        }
        player.sendMessage("§cYou are not in a match, queue, or spectating.");
    }

    // --- Cleanup ---

    /**
     * Clean up all matches and queues on disable.
     */
    public void cleanup() {
        for (ArenaMatch match : activeMatches.values()) {
            for (Player p : match.getAllPlayers()) {
                Position original = match.getOriginalPosition(p.getUniqueId());
                if (original != null) {
                    p.teleport(original);
                }
            }
        }
        activeMatches.clear();
        playerMatches.clear();
        queues.clear();
        spectators.clear();
        saveArenas();
    }
}
