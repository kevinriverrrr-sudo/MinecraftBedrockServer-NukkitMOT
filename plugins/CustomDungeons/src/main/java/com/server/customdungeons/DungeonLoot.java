package com.server.customdungeons;

/**
 * Represents a single loot table entry for random loot drops.
 * Each entry has an item identifier, chance of dropping, and amount range.
 */
public class DungeonLoot {

    private final String item;   // format: "item_id:meta:amount" or just "item_id"
    private final int chance;    // percentage chance (0-100)
    private final int minAmount;
    private final int maxAmount;

    public DungeonLoot(String item, int chance, int minAmount, int maxAmount) {
        this.item = item;
        this.chance = chance;
        this.minAmount = minAmount;
        this.maxAmount = maxAmount;
    }

    public DungeonLoot(String item, int chance) {
        this(item, chance, 1, 1);
    }

    public String getItem() {
        return item;
    }

    public int getChance() {
        return chance;
    }

    public int getMinAmount() {
        return minAmount;
    }

    public int getMaxAmount() {
        return maxAmount;
    }

    /**
     * Parse the item string into components: [itemId, meta, amount].
     */
    public String[] parseItem() {
        return item.split(":");
    }

    @Override
    public String toString() {
        return "DungeonLoot{item='" + item + "', chance=" + chance +
                "%, amount=" + minAmount + "-" + maxAmount + "}";
    }
}
