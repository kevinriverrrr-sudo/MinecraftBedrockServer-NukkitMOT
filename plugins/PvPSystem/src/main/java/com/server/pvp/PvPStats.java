package com.server.pvp;

/**
 * Represents a player's PvP statistics.
 */
public class PvPStats {

    private String playerName;
    private int kills;
    private int deaths;
    private int highestCombo;
    private int highestKillStreak;
    private int elo;
    private long firstJoinTime;
    private int currentKillStreak;

    public PvPStats(String playerName, int startingElo) {
        this.playerName = playerName;
        this.kills = 0;
        this.deaths = 0;
        this.highestCombo = 0;
        this.highestKillStreak = 0;
        this.elo = startingElo;
        this.firstJoinTime = System.currentTimeMillis();
        this.currentKillStreak = 0;
    }

    public String getPlayerName() {
        return playerName;
    }

    public void setPlayerName(String playerName) {
        this.playerName = playerName;
    }

    public int getKills() {
        return kills;
    }

    public void setKills(int kills) {
        this.kills = kills;
    }

    public void addKill() {
        this.kills++;
    }

    public int getDeaths() {
        return deaths;
    }

    public void setDeaths(int deaths) {
        this.deaths = deaths;
    }

    public void addDeath() {
        this.deaths++;
    }

    public double getKDRatio() {
        if (deaths == 0) {
            return kills;
        }
        return (double) kills / deaths;
    }

    public String getKDRatioString() {
        double kd = getKDRatio();
        if (kd == Math.floor(kd) && !Double.isInfinite(kd)) {
            return String.valueOf((int) kd);
        }
        return String.format("%.2f", kd);
    }

    public int getHighestCombo() {
        return highestCombo;
    }

    public void setHighestCombo(int highestCombo) {
        this.highestCombo = highestCombo;
    }

    public void updateHighestCombo(int combo) {
        if (combo > highestCombo) {
            this.highestCombo = combo;
        }
    }

    public int getHighestKillStreak() {
        return highestKillStreak;
    }

    public void setHighestKillStreak(int highestKillStreak) {
        this.highestKillStreak = highestKillStreak;
    }

    public void updateHighestKillStreak(int streak) {
        if (streak > highestKillStreak) {
            this.highestKillStreak = streak;
        }
    }

    public int getElo() {
        return elo;
    }

    public void setElo(int elo) {
        this.elo = elo;
    }

    public void addElo(int amount) {
        this.elo += amount;
    }

    public void subtractElo(int amount) {
        this.elo -= amount;
    }

    public long getFirstJoinTime() {
        return firstJoinTime;
    }

    public void setFirstJoinTime(long firstJoinTime) {
        this.firstJoinTime = firstJoinTime;
    }

    public int getCurrentKillStreak() {
        return currentKillStreak;
    }

    public void setCurrentKillStreak(int currentKillStreak) {
        this.currentKillStreak = currentKillStreak;
    }

    public void incrementKillStreak() {
        this.currentKillStreak++;
        updateHighestKillStreak(this.currentKillStreak);
    }

    public void resetKillStreak() {
        this.currentKillStreak = 0;
    }

    /**
     * Check if the player is a new player (within protection period).
     * @param protectionDurationMs protection duration in milliseconds
     * @return true if the player is still within the protection period
     */
    public boolean isNewPlayer(long protectionDurationMs) {
        if (protectionDurationMs <= 0) return false;
        return (System.currentTimeMillis() - firstJoinTime) < protectionDurationMs;
    }

    /**
     * Get a formatted stats display string.
     */
    public String getFormattedStats() {
        StringBuilder sb = new StringBuilder();
        sb.append("§e§l--- PvP Stats: §f").append(playerName).append(" §e§l---\n");
        sb.append("§7Kills: §f").append(kills).append("\n");
        sb.append("§7Deaths: §f").append(deaths).append("\n");
        sb.append("§7K/D Ratio: §f").append(getKDRatioString()).append("\n");
        sb.append("§7Highest Combo: §6").append(highestCombo).append("\n");
        sb.append("§7Highest Kill Streak: §c").append(highestKillStreak).append("\n");
        sb.append("§7ELO Rating: §b").append(elo);
        return sb.toString();
    }
}
