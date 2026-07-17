package com.server.kitsystem;

import cn.nukkit.item.Item;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Represents a kit with items, armor, effects, cooldown, and permission.
 */
public class Kit {

    private String name;
    private String displayName;
    private String description;
    private String permission;
    private long cooldown; // in seconds, 0 = one-time claim
    private String icon;
    private List<Item> items;
    private Item helmet;
    private Item chestplate;
    private Item leggings;
    private Item boots;
    private Item offhand;
    private List<EffectEntry> effects;

    /**
     * Represents a potion effect entry with id, amplifier, and duration.
     */
    public static class EffectEntry {
        private int effectId;
        private int amplifier;
        private int duration; // in seconds

        public EffectEntry(int effectId, int amplifier, int duration) {
            this.effectId = effectId;
            this.amplifier = amplifier;
            this.duration = duration;
        }

        public int getEffectId() {
            return effectId;
        }

        public void setEffectId(int effectId) {
            this.effectId = effectId;
        }

        public int getAmplifier() {
            return amplifier;
        }

        public void setAmplifier(int amplifier) {
            this.amplifier = amplifier;
        }

        public int getDuration() {
            return duration;
        }

        public void setDuration(int duration) {
            this.duration = duration;
        }
    }

    public Kit(String name) {
        this.name = name;
        this.items = new ArrayList<>();
        this.effects = new ArrayList<>();
        this.cooldown = 0;
    }

    public Kit(String name, String displayName, String description, String permission, long cooldown, String icon) {
        this.name = name;
        this.displayName = displayName;
        this.description = description;
        this.permission = permission;
        this.cooldown = cooldown;
        this.icon = icon;
        this.items = new ArrayList<>();
        this.effects = new ArrayList<>();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getPermission() {
        return permission;
    }

    public void setPermission(String permission) {
        this.permission = permission;
    }

    public long getCooldown() {
        return cooldown;
    }

    public void setCooldown(long cooldown) {
        this.cooldown = cooldown;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public List<Item> getItems() {
        return items;
    }

    public void setItems(List<Item> items) {
        this.items = items;
    }

    public void addItem(Item item) {
        this.items.add(item);
    }

    public Item getHelmet() {
        return helmet;
    }

    public void setHelmet(Item helmet) {
        this.helmet = helmet;
    }

    public Item getChestplate() {
        return chestplate;
    }

    public void setChestplate(Item chestplate) {
        this.chestplate = chestplate;
    }

    public Item getLeggings() {
        return leggings;
    }

    public void setLeggings(Item leggings) {
        this.leggings = leggings;
    }

    public Item getBoots() {
        return boots;
    }

    public void setBoots(Item boots) {
        this.boots = boots;
    }

    public Item getOffhand() {
        return offhand;
    }

    public void setOffhand(Item offhand) {
        this.offhand = offhand;
    }

    public List<EffectEntry> getEffects() {
        return effects;
    }

    public void setEffects(List<EffectEntry> effects) {
        this.effects = effects;
    }

    public void addEffect(EffectEntry effect) {
        this.effects.add(effect);
    }

    /**
     * Returns true if this kit is a one-time claim kit (cooldown == 0).
     */
    public boolean isOneTime() {
        return cooldown == 0;
    }

    /**
     * Get the icon as an Item object for display purposes.
     */
    public Item getIconItem() {
        if (icon != null && !icon.isEmpty()) {
            try {
                return Item.fromString(icon);
            } catch (Exception e) {
                // Fallback to a default item
                return Item.get(287); // stick as fallback
            }
        }
        return Item.get(287); // stick as fallback
    }
}
