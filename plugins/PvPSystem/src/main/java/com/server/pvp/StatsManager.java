package com.server.pvp;

import cn.nukkit.utils.Config;
import cn.nukkit.utils.ConfigSection;

import java.io.File;
import java.util.*;

/**
 * Manages player PvP statistics storage and retrieval using YAML files.
 */
public class StatsManager {

    private final File dataFolder;
    private final ELOManager eloManager;
    private final Map<String, PvPStats> statsCache;
    private Config statsConfig;

    public StatsManager(File pluginDataFolder, ELOManager eloManager) {
        this.dataFolder = new File(pluginDataFolder, "stats");
        this.eloManager = eloManager;
        this.statsCache = new HashMap<>();
        if (!this.dataFolder.exists()) {
            this.dataFolder.mkdirs();
        }
    }

    /**
     * Load all stats from the stats.yml file.
     */
    public void loadStats() {
        File statsFile = new File(dataFolder, "stats.yml");
        statsConfig = new Config(statsFile, Config.YAML);
        statsCache.clear();

        for (String key : statsConfig.getKeys()) {
            ConfigSection section = statsConfig.getSection(key);
            PvPStats stats = new PvPStats(key, eloManager.getStartingElo());
            stats.setKills(section.getInt("kills", 0));
            stats.setDeaths(section.getInt("deaths", 0));
            stats.setHighestCombo(section.getInt("highestCombo", 0));
            stats.setHighestKillStreak(section.getInt("highestKillStreak", 0));
            stats.setElo(section.getInt("elo", eloManager.getStartingElo()));
            stats.setFirstJoinTime(section.getLong("firstJoinTime", System.currentTimeMillis()));
            stats.setCurrentKillStreak(section.getInt("currentKillStreak", 0));
            statsCache.put(key.toLowerCase(), stats);
        }
    }

    /**
     * Save all stats to the stats.yml file.
     */
    public void saveStats() {
        if (statsConfig == null) {
            File statsFile = new File(dataFolder, "stats.yml");
            statsConfig = new Config(statsFile, Config.YAML);
        }
        for (Map.Entry<String, PvPStats> entry : statsCache.entrySet()) {
            PvPStats stats = entry.getValue();
            String key = entry.getKey();
            statsConfig.set(key + ".kills", stats.getKills());
            statsConfig.set(key + ".deaths", stats.getDeaths());
            statsConfig.set(key + ".highestCombo", stats.getHighestCombo());
            statsConfig.set(key + ".highestKillStreak", stats.getHighestKillStreak());
            statsConfig.set(key + ".elo", stats.getElo());
            statsConfig.set(key + ".firstJoinTime", stats.getFirstJoinTime());
            statsConfig.set(key + ".currentKillStreak", stats.getCurrentKillStreak());
            statsConfig.set(key + ".playerName", stats.getPlayerName());
        }
        statsConfig.save();
    }

    /**
     * Get or create stats for a player.
     *
     * @param playerName the player's name
     * @return the player's PvP stats
     */
    public PvPStats getStats(String playerName) {
        String key = playerName.toLowerCase();
        if (!statsCache.containsKey(key)) {
            PvPStats stats = new PvPStats(playerName, eloManager.getStartingElo());
            statsCache.put(key, stats);
        }
        return statsCache.get(key);
    }

    /**
     * Record a kill for the attacker and a death for the victim.
     *
     * @param attackerName the killer's name
     * @param victimName   the victim's name
     */
    public void recordKill(String attackerName, String victimName) {
        PvPStats attackerStats = getStats(attackerName);
        PvPStats victimStats = getStats(victimName);

        attackerStats.addKill();
        attackerStats.incrementKillStreak();

        victimStats.addDeath();
        victimStats.resetKillStreak();

        // Update highest combo and kill streak for attacker
        // These are updated in real-time by trackers, but we ensure they're saved
    }

    /**
     * Update ELO ratings after a match.
     *
     * @param winnerName the winner's name
     * @param loserName  the loser's name
     * @return an array of [winnerEloChange, loserEloChange]
     */
    public int[] updateElo(String winnerName, String loserName) {
        PvPStats winnerStats = getStats(winnerName);
        PvPStats loserStats = getStats(loserName);

        int[] newRatings = eloManager.calculateMatchResult(winnerStats.getElo(), loserStats.getElo());
        int winnerChange = newRatings[0] - winnerStats.getElo();
        int loserChange = newRatings[1] - loserStats.getElo();

        winnerStats.setElo(newRatings[0]);
        loserStats.setElo(newRatings[1]);

        return new int[]{winnerChange, loserChange};
    }

    /**
     * Update the highest combo for a player if the current combo exceeds it.
     *
     * @param playerName the player's name
     * @param combo      the current combo count
     */
    public void updateHighestCombo(String playerName, int combo) {
        PvPStats stats = getStats(playerName);
        stats.updateHighestCombo(combo);
    }

    /**
     * Get the leaderboard sorted by a specific stat.
     *
     * @param type the type of leaderboard ("kills", "kd", "elo")
     * @param limit the maximum number of entries to return
     * @return a sorted list of PvPStats
     */
    public List<PvPStats> getLeaderboard(String type, int limit) {
        List<PvPStats> allStats = new ArrayList<>(statsCache.values());

        switch (type.toLowerCase()) {
            case "kills":
                allStats.sort((a, b) -> Integer.compare(b.getKills(), a.getKills()));
                break;
            case "kd":
                allStats.sort((a, b) -> Double.compare(b.getKDRatio(), a.getKDRatio()));
                break;
            case "elo":
                allStats.sort((a, b) -> Integer.compare(b.getElo(), a.getElo()));
                break;
            default:
                allStats.sort((a, b) -> Integer.compare(b.getKills(), a.getKills()));
                break;
        }

        if (allStats.size() > limit) {
            return allStats.subList(0, limit);
        }
        return allStats;
    }

    /**
     * Format a leaderboard for display.
     *
     * @param type  the leaderboard type
     * @param limit the number of entries
     * @return a formatted string for the leaderboard
     */
    public String formatLeaderboard(String type, int limit) {
        String typeName;
        switch (type.toLowerCase()) {
            case "kills":
                typeName = "Most Kills";
                break;
            case "kd":
                typeName = "Highest K/D";
                break;
            case "elo":
                typeName = "Highest ELO";
                break;
            default:
                typeName = "Most Kills";
                break;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("§e§l--- PvP Leaderboard: §f").append(typeName).append(" §e§l---\n");

        List<PvPStats> leaderboard = getLeaderboard(type, limit);
        int rank = 1;
        for (PvPStats stats : leaderboard) {
            String prefix;
            if (rank == 1) prefix = "§6§l#1 ";
            else if (rank == 2) prefix = "§7§l#2 ";
            else if (rank == 3) prefix = "§c§l#3 ";
            else prefix = "§f#" + rank + " ";

            sb.append(prefix).append("§r§f").append(stats.getPlayerName());

            switch (type.toLowerCase()) {
                case "kills":
                    sb.append(" §7- §a").append(stats.getKills()).append(" kills");
                    break;
                case "kd":
                    sb.append(" §7- §b").append(stats.getKDRatioString()).append(" K/D");
                    break;
                case "elo":
                    sb.append(" §7- §d").append(stats.getElo()).append(" ELO");
                    break;
            }

            if (rank < leaderboard.size()) {
                sb.append("\n");
            }
            rank++;
        }

        if (leaderboard.isEmpty()) {
            sb.append("§7No data yet.");
        }

        return sb.toString();
    }

    /**
     * Get all cached stats.
     */
    public Map<String, PvPStats> getAllStats() {
        return statsCache;
    }

    /**
     * Check if a player has stats recorded.
     */
    public boolean hasStats(String playerName) {
        return statsCache.containsKey(playerName.toLowerCase());
    }

    /**
     * Reset a player's stats.
     */
    public void resetStats(String playerName) {
        PvPStats stats = getStats(playerName);
        stats.setKills(0);
        stats.setDeaths(0);
        stats.setHighestCombo(0);
        stats.setHighestKillStreak(0);
        stats.setElo(eloManager.getStartingElo());
        stats.setCurrentKillStreak(0);
    }
}
