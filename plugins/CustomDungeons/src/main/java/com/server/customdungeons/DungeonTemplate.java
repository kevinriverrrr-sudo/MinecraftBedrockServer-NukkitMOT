package com.server.customdungeons;

import cn.nukkit.level.Position;

import java.util.List;

/**
 * Represents a dungeon template loaded from config.
 * Contains all the static configuration for a dungeon type:
 * display name, difficulty, waves, boss, and loot tables.
 */
public class DungeonTemplate {

    private final String id;
    private final String displayName;
    private final String difficulty;
    private final int minLevel;
    private final int maxPlayers;
    private final int cooldown;
    private final String entryFee;
    private final Position spawnPosition;
    private final List<DungeonWave> waves;
    private final DungeonBoss boss;
    private final List<DungeonLoot> guaranteedLoot;
    private final List<DungeonLoot> randomLoot;

    public DungeonTemplate(String id, String displayName, String difficulty, int minLevel,
                           int maxPlayers, int cooldown, String entryFee, Position spawnPosition,
                           List<DungeonWave> waves, DungeonBoss boss,
                           List<DungeonLoot> guaranteedLoot, List<DungeonLoot> randomLoot) {
        this.id = id;
        this.displayName = displayName;
        this.difficulty = difficulty;
        this.minLevel = minLevel;
        this.maxPlayers = maxPlayers;
        this.cooldown = cooldown;
        this.entryFee = entryFee;
        this.spawnPosition = spawnPosition;
        this.waves = waves;
        this.boss = boss;
        this.guaranteedLoot = guaranteedLoot;
        this.randomLoot = randomLoot;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDifficulty() {
        return difficulty;
    }

    public int getMinLevel() {
        return minLevel;
    }

    public int getMaxPlayers() {
        return maxPlayers;
    }

    public int getCooldown() {
        return cooldown;
    }

    public String getEntryFee() {
        return entryFee;
    }

    public Position getSpawnPosition() {
        return spawnPosition;
    }

    public List<DungeonWave> getWaves() {
        return waves;
    }

    public DungeonBoss getBoss() {
        return boss;
    }

    public List<DungeonLoot> getGuaranteedLoot() {
        return guaranteedLoot;
    }

    public List<DungeonLoot> getRandomLoot() {
        return randomLoot;
    }

    /**
     * Get the total number of waves in this dungeon.
     */
    public int getTotalWaves() {
        return waves.size();
    }

    /**
     * Get a formatted info string for display.
     */
    public String getInfoLine() {
        return displayName + " §7[" + difficulty + "] §fWaves: §e" + getTotalWaves() +
                " §f| Boss: §c" + boss.getName() +
                " §f| Max Players: §b" + maxPlayers +
                " §f| Min Level: §a" + minLevel;
    }

    @Override
    public String toString() {
        return "DungeonTemplate{id='" + id + "', displayName='" + displayName +
                "', difficulty='" + difficulty + "', waves=" + waves.size() + "}";
    }
}
