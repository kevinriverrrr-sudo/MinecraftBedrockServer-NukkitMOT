package com.server.customdungeons;

import java.util.List;

/**
 * Represents a boss definition in a dungeon.
 * Contains the boss's entity type, name, health, damage, abilities, and phase transitions.
 */
public class DungeonBoss {

    private final String type;
    private final String name;
    private final int health;
    private final int damage;
    private final List<String> abilities;
    private final List<DungeonBossPhase> phases;

    public DungeonBoss(String type, String name, int health, int damage,
                       List<String> abilities, List<DungeonBossPhase> phases) {
        this.type = type;
        this.name = name;
        this.health = health;
        this.damage = damage;
        this.abilities = abilities;
        this.phases = phases;
    }

    public String getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public int getHealth() {
        return health;
    }

    public int getDamage() {
        return damage;
    }

    public List<String> getAbilities() {
        return abilities;
    }

    public List<DungeonBossPhase> getPhases() {
        return phases;
    }

    /**
     * Check if the boss has the given ability.
     */
    public boolean hasAbility(String abilityPrefix) {
        for (String ability : abilities) {
            if (ability.startsWith(abilityPrefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Parse an ability string that starts with the given prefix.
     * Returns the parts after splitting by ':'.
     */
    public String[] parseAbility(String abilityPrefix) {
        for (String ability : abilities) {
            if (ability.startsWith(abilityPrefix + ":")) {
                return ability.split(":");
            }
        }
        return new String[0];
    }

    @Override
    public String toString() {
        return "DungeonBoss{type='" + type + "', name='" + name + "', health=" + health +
                ", damage=" + damage + ", abilities=" + abilities.size() +
                ", phases=" + phases.size() + "}";
    }
}
