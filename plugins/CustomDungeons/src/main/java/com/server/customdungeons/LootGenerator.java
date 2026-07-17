package com.server.customdungeons;

import cn.nukkit.item.Item;
import cn.nukkit.item.ItemID;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Generates random loot from dungeon loot tables.
 * Handles both guaranteed and random loot drops.
 */
public class LootGenerator {

    private static final int AIR_ID = 0;

    private final Random random;

    public LootGenerator() {
        this.random = new Random();
    }

    /**
     * Generate all loot for a completed dungeon.
     * Includes guaranteed loot and random loot based on chance.
     *
     * @param template The dungeon template containing loot tables
     * @return List of Item objects to distribute
     */
    public List<Item> generateLoot(DungeonTemplate template) {
        List<Item> loot = new ArrayList<>();

        // Add guaranteed loot
        for (DungeonLoot lootEntry : template.getGuaranteedLoot()) {
            Item item = createItemFromLoot(lootEntry);
            if (item != null && item.getId() != AIR_ID) {
                loot.add(item);
            }
        }

        // Add random loot (roll for each entry)
        for (DungeonLoot lootEntry : template.getRandomLoot()) {
            if (rollChance(lootEntry.getChance())) {
                Item item = createItemFromLoot(lootEntry);
                if (item != null && item.getId() != AIR_ID) {
                    loot.add(item);
                }
            }
        }

        return loot;
    }

    /**
     * Roll a chance check (0-100).
     * @param chance percentage chance of success (0-100)
     * @return true if the roll succeeded
     */
    private boolean rollChance(int chance) {
        if (chance >= 100) return true;
        if (chance <= 0) return false;
        return random.nextInt(100) < chance;
    }

    /**
     * Create an Item from a DungeonLoot entry.
     * Parses the item string format and sets amount.
     *
     * @param loot The loot entry
     * @return The created Item, or null if invalid
     */
    private Item createItemFromLoot(DungeonLoot loot) {
        String[] parts = loot.parseItem();

        if (parts.length == 0) return null;

        String itemIdStr = parts[0];
        int meta = 0;
        int amount = 1;

        try {
            if (parts.length >= 2) {
                meta = Integer.parseInt(parts[1]);
            }
        } catch (NumberFormatException e) {
            meta = 0;
        }

        // Determine amount (random between min and max)
        int minAmount = loot.getMinAmount();
        int maxAmount = loot.getMaxAmount();
        if (minAmount == maxAmount) {
            amount = minAmount;
        } else {
            amount = minAmount + random.nextInt(maxAmount - minAmount + 1);
        }

        return createItem(itemIdStr, meta, amount);
    }

    /**
     * Create an Item by its string identifier.
     * Tries to resolve the item by name, then by numeric ID.
     *
     * @param itemIdStr The item identifier string (e.g., "diamond", "iron_sword")
     * @param meta The item metadata/damage value
     * @param amount The item count
     * @return The created Item
     */
    private Item createItem(String itemIdStr, int meta, int amount) {
        Item item = null;

        // Try using Item.fromString which handles both names and IDs
        try {
            item = Item.fromString(itemIdStr);
            if (item != null && item.getId() != AIR_ID) {
                item.setDamage(meta);
                item.setCount(amount);
                return item;
            }
        } catch (Exception ignored) {
        }

        // Fallback: try resolving by well-known item name mapping
        int itemId = resolveItemId(itemIdStr);
        if (itemId != 0) {
            item = Item.get(itemId, meta, amount);
            return item;
        }

        // Last resort: try parsing as numeric ID
        try {
            int numericId = Integer.parseInt(itemIdStr);
            item = Item.get(numericId, meta, amount);
            return item;
        } catch (NumberFormatException e) {
            return Item.get(AIR_ID, 0, 0);
        }
    }

    /**
     * Resolve common item name strings to their numeric IDs.
     * This provides a fallback when Item.fromString doesn't recognize a name.
     */
    private int resolveItemId(String name) {
        switch (name.toLowerCase().replace("_", "")) {
            // Tools & Weapons
            case "diamondsword": return ItemID.DIAMOND_SWORD;
            case "ironsword": return ItemID.IRON_SWORD;
            case "goldsword":
            case "goldensword": return ItemID.GOLDEN_SWORD;
            case "stonesword": return ItemID.STONE_SWORD;
            case "woodsword":
            case "woodensword": return ItemID.WOODEN_SWORD;
            case "diamondpickaxe": return ItemID.DIAMOND_PICKAXE;
            case "ironpickaxe": return ItemID.IRON_PICKAXE;
            case "diamondaxe": return ItemID.DIAMOND_AXE;
            case "ironaxe": return ItemID.IRON_AXE;
            case "bow": return ItemID.BOW;

            // Armor
            case "diamondchestplate": return ItemID.DIAMOND_CHESTPLATE;
            case "diamondhelmet": return ItemID.DIAMOND_HELMET;
            case "diamondleggings": return ItemID.DIAMOND_LEGGINGS;
            case "diamondboots": return ItemID.DIAMOND_BOOTS;
            case "ironchestplate": return ItemID.IRON_CHESTPLATE;
            case "ironhelmet": return ItemID.IRON_HELMET;
            case "ironleggings": return ItemID.IRON_LEGGINGS;
            case "ironboots": return ItemID.IRON_BOOTS;

            // Materials
            case "diamond": return ItemID.DIAMOND;
            case "ironingot": return ItemID.IRON_INGOT;
            case "goldingot": return ItemID.GOLD_INGOT;
            case "emerald": return ItemID.EMERALD;
            case "coal": return ItemID.COAL;
            case "lapislazuli": return ItemID.DYE;
            case "redstone": return ItemID.REDSTONE;
            case "netherstar": return ItemID.NETHER_STAR;
            case "blazerod": return ItemID.BLAZE_ROD;
            case "enderpearl": return ItemID.ENDER_PEARL;
            case "ghasttear": return ItemID.GHAST_TEAR;

            // Food
            case "goldenapple": return ItemID.GOLDEN_APPLE;
            case "apple": return ItemID.APPLE;
            case "bread": return ItemID.BREAD;
            case "cookedbeef":
            case "steak": return ItemID.COOKED_BEEF;

            // Special
            case "enchantedbook": return ItemID.ENCHANTED_BOOK;
            case "elytra": return ItemID.ELYTRA;
            case "totem":
            case "totemofundying": return ItemID.TOTEM;
            case "netheriteingot": return ItemID.NETHERITE_INGOT;

            default: return 0;
        }
    }

    /**
     * Generate a single random loot item from a list of loot entries.
     * Useful for boss-specific drops or bonus rewards.
     *
     * @param lootEntries The loot entries to roll from
     * @return A single Item, or null if no items dropped
     */
    public Item generateSingleLoot(List<DungeonLoot> lootEntries) {
        if (lootEntries == null || lootEntries.isEmpty()) return null;

        for (DungeonLoot loot : lootEntries) {
            if (rollChance(loot.getChance())) {
                Item item = createItemFromLoot(loot);
                if (item != null && item.getId() != AIR_ID) {
                    return item;
                }
            }
        }

        return null;
    }
}
