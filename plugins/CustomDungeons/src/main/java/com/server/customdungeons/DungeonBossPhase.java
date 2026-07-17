package com.server.customdungeons;

import java.util.List;

/**
 * Represents a boss phase trigger based on health percentage.
 * When the boss reaches a certain health percentage, this phase activates.
 */
public class DungeonBossPhase {

    private final int healthPercentage;
    private final String message;
    private final double damageMultiplier;
    private final String summon; // format: "type:count:name" or null

    public DungeonBossPhase(int healthPercentage, String message, double damageMultiplier, String summon) {
        this.healthPercentage = healthPercentage;
        this.message = message;
        this.damageMultiplier = damageMultiplier;
        this.summon = summon;
    }

    public int getHealthPercentage() {
        return healthPercentage;
    }

    public String getMessage() {
        return message;
    }

    public double getDamageMultiplier() {
        return damageMultiplier;
    }

    public String getSummon() {
        return summon;
    }

    public boolean hasSummon() {
        return summon != null && !summon.isEmpty();
    }

    /**
     * Parse the summon string into mob components.
     * Format: "type:count:name"
     */
    public String[] parseSummon() {
        if (!hasSummon()) return new String[0];
        return summon.split(":");
    }

    @Override
    public String toString() {
        return "DungeonBossPhase{health=" + healthPercentage + "%, multiplier=" + damageMultiplier + "}";
    }
}
