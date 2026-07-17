package com.server.customdungeons;

import java.util.List;

/**
 * Represents a single wave of mobs in a dungeon.
 * Contains the list of mob definitions and a delay before the wave starts.
 */
public class DungeonWave {

    private final List<DungeonMob> mobs;
    private final int delay; // seconds before this wave starts after the previous one

    public DungeonWave(List<DungeonMob> mobs, int delay) {
        this.mobs = mobs;
        this.delay = delay;
    }

    public List<DungeonMob> getMobs() {
        return mobs;
    }

    public int getDelay() {
        return delay;
    }

    /**
     * Get the total number of mobs in this wave.
     */
    public int getTotalMobCount() {
        int total = 0;
        for (DungeonMob mob : mobs) {
            total += mob.getCount();
        }
        return total;
    }

    @Override
    public String toString() {
        return "DungeonWave{mobs=" + mobs.size() + ", delay=" + delay + ", totalMobs=" + getTotalMobCount() + "}";
    }
}
