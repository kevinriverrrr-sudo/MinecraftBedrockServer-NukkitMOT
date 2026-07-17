package com.server.kitsystem;

import cn.nukkit.item.Item;
import cn.nukkit.item.enchantment.Enchantment;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a custom item with unique name, lore, enchantments, and NBT data.
 */
public class CustomItem {

    private String id;
    private String material;
    private String name;
    private List<String> lore;
    private List<EnchantmentEntry> enchantments;

    /**
     * Represents an enchantment entry with name and level.
     */
    public static class EnchantmentEntry {
        private String enchantmentName;
        private int level;

        public EnchantmentEntry(String enchantmentName, int level) {
            this.enchantmentName = enchantmentName;
            this.level = level;
        }

        public String getEnchantmentName() {
            return enchantmentName;
        }

        public void setEnchantmentName(String enchantmentName) {
            this.enchantmentName = enchantmentName;
        }

        public int getLevel() {
            return level;
        }

        public void setLevel(int level) {
            this.level = level;
        }
    }

    public CustomItem(String id) {
        this.id = id;
        this.lore = new ArrayList<>();
        this.enchantments = new ArrayList<>();
    }

    public CustomItem(String id, String material, String name, List<String> lore, List<EnchantmentEntry> enchantments) {
        this.id = id;
        this.material = material;
        this.name = name;
        this.lore = lore != null ? lore : new ArrayList<>();
        this.enchantments = enchantments != null ? enchantments : new ArrayList<>();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getMaterial() {
        return material;
    }

    public void setMaterial(String material) {
        this.material = material;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<String> getLore() {
        return lore;
    }

    public void setLore(List<String> lore) {
        this.lore = lore != null ? lore : new ArrayList<>();
    }

    public List<EnchantmentEntry> getEnchantments() {
        return enchantments;
    }

    public void setEnchantments(List<EnchantmentEntry> enchantments) {
        this.enchantments = enchantments != null ? enchantments : new ArrayList<>();
    }

    /**
     * Creates an Item from this custom item definition.
     *
     * @param amount the stack amount
     * @return the configured Item
     */
    public Item createItem(int amount) {
        Item item;
        try {
            item = Item.fromString(material);
        } catch (Exception e) {
            item = Item.get(287); // fallback to stick
        }
        item.setCount(amount);

        if (name != null && !name.isEmpty()) {
            item.setCustomName(translateColorCodes(name));
        }

        if (lore != null && !lore.isEmpty()) {
            List<String> translatedLore = new ArrayList<>();
            for (String line : lore) {
                translatedLore.add(translateColorCodes(line));
            }
            item.setLore(translatedLore.toArray(new String[0]));
        }

        if (enchantments != null) {
            for (EnchantmentEntry entry : enchantments) {
                Enchantment ench = Enchantment.getEnchantment(entry.getEnchantmentName());
                if (ench != null) {
                    ench.setLevel(entry.getLevel());
                    item.addEnchantment(ench);
                }
            }
        }

        return item;
    }

    /**
     * Translate & color codes to § section symbols.
     */
    private String translateColorCodes(String text) {
        if (text == null) return "";
        return text.replace('&', '\u00a7');
    }
}
