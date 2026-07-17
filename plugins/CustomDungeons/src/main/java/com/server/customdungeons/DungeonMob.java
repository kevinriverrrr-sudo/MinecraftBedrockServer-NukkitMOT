package com.server.customdungeons;

/**
 * Represents a custom mob definition within a dungeon wave.
 * Contains entity type, display name, health, damage, and spawn count.
 */
public class DungeonMob {

    private final String type;
    private final String name;
    private final int health;
    private final int damage;
    private final int count;

    public DungeonMob(String type, String name, int health, int damage, int count) {
        this.type = type;
        this.name = name;
        this.health = health;
        this.damage = damage;
        this.count = count;
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

    public int getCount() {
        return count;
    }

    @Override
    public String toString() {
        return "DungeonMob{type='" + type + "', name='" + name + "', health=" + health +
                ", damage=" + damage + ", count=" + count + "}";
    }
}
