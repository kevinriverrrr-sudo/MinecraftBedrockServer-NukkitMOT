package com.server.kitsystem;

import cn.nukkit.item.Item;
import cn.nukkit.item.enchantment.Enchantment;
import cn.nukkit.potion.Effect;
import cn.nukkit.utils.Config;
import cn.nukkit.utils.ConfigSection;
import com.server.kitsystem.Kit.EffectEntry;

import java.io.File;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Manages all kits, custom items, and cooldown logic.
 * Loads kits and custom items from config, provides methods to claim kits,
 * and handles item parsing from config strings.
 */
public class KitManager {

    private final KitSystemPlugin plugin;
    private final CooldownManager cooldownManager;
    private final Map<String, Kit> kits;
    private final Map<String, CustomItem> customItems;
    private final Config kitConfig;

    public KitManager(KitSystemPlugin plugin) {
        this.plugin = plugin;
        this.kits = new LinkedHashMap<>();
        this.customItems = new LinkedHashMap<>();
        this.cooldownManager = new CooldownManager(plugin.getDataFolder());
        this.kitConfig = plugin.getConfig();
        loadKits();
        loadCustomItems();
    }

    // ==================== Kit Loading ====================

    /**
     * Load all kits from the config file.
     */
    @SuppressWarnings("unchecked")
    public void loadKits() {
        kits.clear();
        ConfigSection kitsSection = kitConfig.getSection("kits");
        if (kitsSection == null) {
            plugin.getLogger().warning("No kits section found in config!");
            return;
        }

        for (String kitName : kitsSection.getKeys(false)) {
            ConfigSection kitSec = kitsSection.getSection(kitName);
            if (kitSec == null) continue;

            try {
                Kit kit = new Kit(kitName);
                kit.setDisplayName(kitSec.getString("display-name", "§f" + kitName));
                kit.setDescription(kitSec.getString("description", ""));
                kit.setPermission(kitSec.getString("permission", ""));
                kit.setCooldown(kitSec.getLong("cooldown", 0));
                kit.setIcon(kitSec.getString("icon", "stick"));

                // Load items
                List<String> itemStrings = kitSec.getStringList("items");
                if (itemStrings != null) {
                    for (String itemStr : itemStrings) {
                        Item item = parseItemString(itemStr);
                        if (item != null) {
                            kit.addItem(item);
                        }
                    }
                }

                // Load armor
                ConfigSection armorSec = kitSec.getSection("armor");
                if (armorSec != null) {
                    String helmet = armorSec.getString("helmet", "");
                    String chestplate = armorSec.getString("chestplate", "");
                    String leggings = armorSec.getString("leggings", "");
                    String boots = armorSec.getString("boots", "");

                    if (!helmet.isEmpty()) {
                        Item helmItem = parseArmorString(helmet);
                        if (helmItem != null) kit.setHelmet(helmItem);
                    }
                    if (!chestplate.isEmpty()) {
                        Item chestItem = parseArmorString(chestplate);
                        if (chestItem != null) kit.setChestplate(chestItem);
                    }
                    if (!leggings.isEmpty()) {
                        Item legItem = parseArmorString(leggings);
                        if (legItem != null) kit.setLeggings(legItem);
                    }
                    if (!boots.isEmpty()) {
                        Item bootItem = parseArmorString(boots);
                        if (bootItem != null) kit.setBoots(bootItem);
                    }
                }

                // Load effects
                List<String> effectStrings = kitSec.getStringList("effects");
                if (effectStrings != null) {
                    for (String effectStr : effectStrings) {
                        EffectEntry entry = parseEffectString(effectStr);
                        if (entry != null) {
                            kit.addEffect(entry);
                        }
                    }
                }

                kits.put(kitName.toLowerCase(), kit);
                plugin.getLogger().info("Loaded kit: " + kitName + " (cooldown: " + kit.getCooldown() + "s)");

            } catch (Exception e) {
                plugin.getLogger().error("Failed to load kit: " + kitName, e);
            }
        }

        plugin.getLogger().info("Loaded " + kits.size() + " kits.");
    }

    /**
     * Load all custom items from the config file.
     */
    @SuppressWarnings("unchecked")
    public void loadCustomItems() {
        customItems.clear();
        ConfigSection ciSection = kitConfig.getSection("custom-items");
        if (ciSection == null) {
            plugin.getLogger().info("No custom items section found in config.");
            return;
        }

        for (String itemId : ciSection.getKeys(false)) {
            ConfigSection itemSec = ciSection.getSection(itemId);
            if (itemSec == null) continue;

            try {
                String material = itemSec.getString("material", "stick");
                String name = itemSec.getString("name", "");
                List<String> lore = itemSec.getStringList("lore");
                List<String> enchStrings = itemSec.getStringList("enchantments");

                List<CustomItem.EnchantmentEntry> enchantments = new ArrayList<>();
                if (enchStrings != null) {
                    for (String enchStr : enchStrings) {
                        String[] parts = enchStr.split(":");
                        if (parts.length == 2) {
                            try {
                                String enchName = parts[0].trim();
                                int level = Integer.parseInt(parts[1].trim());
                                enchantments.add(new CustomItem.EnchantmentEntry(enchName, level));
                            } catch (NumberFormatException e) {
                                plugin.getLogger().warning("Invalid enchantment level in custom item " + itemId + ": " + enchStr);
                            }
                        }
                    }
                }

                CustomItem customItem = new CustomItem(itemId, material, name, lore, enchantments);
                customItems.put(itemId.toLowerCase(), customItem);
                plugin.getLogger().info("Loaded custom item: " + itemId);

            } catch (Exception e) {
                plugin.getLogger().error("Failed to load custom item: " + itemId, e);
            }
        }

        plugin.getLogger().info("Loaded " + customItems.size() + " custom items.");
    }

    // ==================== Item Parsing ====================

    /**
     * Parse an item string in the format: material:meta:count name:Name lore:Lore1|Lore2
     * Examples:
     *   "wooden_sword:0:1 name:&aStarter_Sword lore:&7Given_to_new_players"
     *   "iron_sword:0:1 name:&cWarrior's_Blade"
     *   "wooden_pickaxe:0:1"
     */
    public Item parseItemString(String itemStr) {
        if (itemStr == null || itemStr.trim().isEmpty()) return null;

        try {
            String str = itemStr.trim();

            // Extract name: part
            String customName = null;
            int nameIdx = str.indexOf(" name:");
            if (nameIdx != -1) {
                // Find where name: value ends (before lore: or end of string)
                String afterName = str.substring(nameIdx + 6); // after " name:"
                int loreIdx = afterName.indexOf(" lore:");
                if (loreIdx != -1) {
                    customName = afterName.substring(0, loreIdx).trim();
                } else {
                    customName = afterName.trim();
                }
            }

            // Extract lore: part
            String[] loreLines = null;
            int loreIdx = str.indexOf(" lore:");
            if (loreIdx != -1) {
                String loreStr = str.substring(loreIdx + 6).trim();
                // Lore lines separated by |
                loreLines = loreStr.split("\\|");
            }

            // Extract the material:meta:count part (everything before name: or lore:)
            String materialPart = str;
            if (nameIdx != -1) {
                materialPart = str.substring(0, nameIdx).trim();
            } else if (loreIdx != -1) {
                materialPart = str.substring(0, loreIdx).trim();
            }

            // Parse material:meta:count
            String[] parts = materialPart.split(":");
            String materialName = parts[0].trim();
            int meta = 0;
            int count = 1;

            if (parts.length >= 2) {
                try {
                    meta = Integer.parseInt(parts[1].trim());
                } catch (NumberFormatException e) {
                    meta = 0;
                }
            }
            if (parts.length >= 3) {
                try {
                    count = Integer.parseInt(parts[2].trim());
                } catch (NumberFormatException e) {
                    count = 1;
                }
            }

            // Create item using Item.fromString
            Item item = Item.fromString(materialName);
            item.setDamage(meta);
            item.setCount(count);

            // Set custom name
            if (customName != null && !customName.isEmpty()) {
                String displayName = customName.replace('_', ' ');
                item.setCustomName(translateColorCodes(displayName));
            }

            // Set lore
            if (loreLines != null && loreLines.length > 0) {
                List<String> translatedLore = new ArrayList<>();
                for (String line : loreLines) {
                    String loreLine = line.trim().replace('_', ' ');
                    translatedLore.add(translateColorCodes(loreLine));
                }
                item.setLore(translatedLore.toArray(new String[0]));
            }

            return item;

        } catch (Exception e) {
            plugin.getLogger().warning("Failed to parse item string: " + itemStr + " - " + e.getMessage());
            return null;
        }
    }

    /**
     * Parse a simple armor item string (just material name, no meta/count).
     */
    public Item parseArmorString(String armorStr) {
        if (armorStr == null || armorStr.trim().isEmpty()) return null;

        try {
            Item item = Item.fromString(armorStr.trim());
            item.setCount(1);
            return item;
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to parse armor string: " + armorStr + " - " + e.getMessage());
            return null;
        }
    }

    /**
     * Parse a potion effect string in the format: effectName:amplifier:duration
     * Example: "speed:1:10", "regeneration:2:60"
     */
    public EffectEntry parseEffectString(String effectStr) {
        if (effectStr == null || effectStr.trim().isEmpty()) return null;

        try {
            String[] parts = effectStr.trim().split(":");
            if (parts.length != 3) {
                plugin.getLogger().warning("Invalid effect format: " + effectStr + " (expected effect:amplifier:duration)");
                return null;
            }

            String effectName = parts[0].trim();
            int amplifier = Integer.parseInt(parts[1].trim());
            int duration = Integer.parseInt(parts[2].trim());

            int effectId = resolveEffectId(effectName);
            if (effectId < 0) {
                plugin.getLogger().warning("Unknown effect: " + effectName);
                return null;
            }

            return new EffectEntry(effectId, amplifier, duration);

        } catch (NumberFormatException e) {
            plugin.getLogger().warning("Invalid effect numbers in: " + effectStr);
            return null;
        }
    }

    /**
     * Resolve a potion effect name to its ID.
     */
    private int resolveEffectId(String name) {
        switch (name.toLowerCase()) {
            case "speed": return Effect.SPEED;
            case "slowness": case "slow": return Effect.SLOWNESS;
            case "haste": return Effect.HASTE;
            case "mining_fatigue": return Effect.MINING_FATIGUE;
            case "strength": return Effect.STRENGTH;
            case "instant_health": case "healing": return Effect.HEALING;
            case "instant_damage": case "harming": return Effect.HARMING;
            case "jump_boost": case "jump": return Effect.JUMP_BOOST;
            case "nausea": case "confusion": return Effect.NAUSEA;
            case "regeneration": case "regen": return Effect.REGENERATION;
            case "damage_resistance": case "resistance": return Effect.DAMAGE_RESISTANCE;
            case "fire_resistance": return Effect.FIRE_RESISTANCE;
            case "water_breathing": return Effect.WATER_BREATHING;
            case "invisibility": return Effect.INVISIBILITY;
            case "blindness": return Effect.BLINDNESS;
            case "night_vision": return Effect.NIGHT_VISION;
            case "hunger": return Effect.HUNGER;
            case "weakness": return Effect.WEAKNESS;
            case "poison": return Effect.POISON;
            case "wither": return Effect.WITHER;
            case "health_boost": return Effect.HEALTH_BOOST;
            case "absorption": return Effect.ABSORPTION;
            case "saturation": return Effect.SATURATION;
            case "levitation": return Effect.LEVITATION;
            default: return -1;
        }
    }

    /**
     * Translate & color codes to § section symbols.
     */
    public static String translateColorCodes(String text) {
        if (text == null) return "";
        return text.replace('&', '\u00a7');
    }

    // ==================== Kit Operations ====================

    /**
     * Get a kit by name (case-insensitive).
     */
    public Kit getKit(String name) {
        return kits.get(name.toLowerCase());
    }

    /**
     * Get all loaded kits.
     */
    public Map<String, Kit> getKits() {
        return Collections.unmodifiableMap(kits);
    }

    /**
     * Check if a kit exists.
     */
    public boolean kitExists(String name) {
        return kits.containsKey(name.toLowerCase());
    }

    /**
     * Add a new kit to the manager.
     */
    public void addKit(Kit kit) {
        kits.put(kit.getName().toLowerCase(), kit);
        saveKitToConfig(kit);
    }

    /**
     * Remove a kit from the manager.
     */
    public void removeKit(String name) {
        kits.remove(name.toLowerCase());
        kitConfig.remove("kits." + name.toLowerCase());
        kitConfig.save();
    }

    /**
     * Save a kit to the config file.
     */
    private void saveKitToConfig(Kit kit) {
        String basePath = "kits." + kit.getName().toLowerCase();
        kitConfig.set(basePath + ".display-name", kit.getDisplayName());
        kitConfig.set(basePath + ".description", kit.getDescription());
        kitConfig.set(basePath + ".permission", kit.getPermission());
        kitConfig.set(basePath + ".cooldown", kit.getCooldown());
        kitConfig.set(basePath + ".icon", kit.getIcon());

        // Save items as serialized strings
        List<String> itemStrings = new ArrayList<>();
        for (Item item : kit.getItems()) {
            itemStrings.add(serializeItem(item));
        }
        kitConfig.set(basePath + ".items", itemStrings);

        // Save armor
        if (kit.getHelmet() != null) {
            kitConfig.set(basePath + ".armor.helmet", kit.getHelmet().getName());
        }
        if (kit.getChestplate() != null) {
            kitConfig.set(basePath + ".armor.chestplate", kit.getChestplate().getName());
        }
        if (kit.getLeggings() != null) {
            kitConfig.set(basePath + ".armor.leggings", kit.getLeggings().getName());
        }
        if (kit.getBoots() != null) {
            kitConfig.set(basePath + ".armor.boots", kit.getBoots().getName());
        }

        // Save effects
        List<String> effectStrings = new ArrayList<>();
        for (EffectEntry entry : kit.getEffects()) {
            String effectName = resolveEffectName(entry.getEffectId());
            effectStrings.add(effectName + ":" + entry.getAmplifier() + ":" + entry.getDuration());
        }
        kitConfig.set(basePath + ".effects", effectStrings);

        kitConfig.save();
    }

    /**
     * Serialize an Item to a config string.
     */
    private String serializeItem(Item item) {
        StringBuilder sb = new StringBuilder();
        sb.append(item.getName().toLowerCase().replace(' ', '_'));
        sb.append(":").append(item.getDamage());
        sb.append(":").append(item.getCount());

        if (item.hasCustomName()) {
            String customName = item.getCustomName()
                    .replace('\u00a7', '&')
                    .replace(' ', '_');
            sb.append(" name:").append(customName);
        }

        if (item.getLore() != null && item.getLore().length > 0) {
            sb.append(" lore:");
            StringBuilder loreSb = new StringBuilder();
            for (int i = 0; i < item.getLore().length; i++) {
                if (i > 0) loreSb.append("|");
                loreSb.append(item.getLore()[i].replace('\u00a7', '&').replace(' ', '_'));
            }
            sb.append(loreSb);
        }

        return sb.toString();
    }

    /**
     * Resolve effect ID back to name for serialization.
     */
    private String resolveEffectName(int id) {
        if (id == Effect.SPEED) return "speed";
        if (id == Effect.SLOWNESS) return "slowness";
        if (id == Effect.HASTE) return "haste";
        if (id == Effect.MINING_FATIGUE) return "mining_fatigue";
        if (id == Effect.STRENGTH) return "strength";
        if (id == Effect.HEALING) return "healing";
        if (id == Effect.HARMING) return "harming";
        if (id == Effect.JUMP_BOOST) return "jump_boost";
        if (id == Effect.NAUSEA) return "nausea";
        if (id == Effect.REGENERATION) return "regeneration";
        if (id == Effect.DAMAGE_RESISTANCE) return "damage_resistance";
        if (id == Effect.FIRE_RESISTANCE) return "fire_resistance";
        if (id == Effect.WATER_BREATHING) return "water_breathing";
        if (id == Effect.INVISIBILITY) return "invisibility";
        if (id == Effect.BLINDNESS) return "blindness";
        if (id == Effect.NIGHT_VISION) return "night_vision";
        if (id == Effect.HUNGER) return "hunger";
        if (id == Effect.WEAKNESS) return "weakness";
        if (id == Effect.POISON) return "poison";
        if (id == Effect.WITHER) return "wither";
        if (id == Effect.HEALTH_BOOST) return "health_boost";
        if (id == Effect.ABSORPTION) return "absorption";
        if (id == Effect.SATURATION) return "saturation";
        if (id == Effect.LEVITATION) return "levitation";
        return "unknown";
    }

    /**
     * Update a kit's cooldown in config.
     */
    public void setKitCooldown(String kitName, long cooldownSeconds) {
        Kit kit = getKit(kitName);
        if (kit != null) {
            kit.setCooldown(cooldownSeconds);
            kitConfig.set("kits." + kitName.toLowerCase() + ".cooldown", cooldownSeconds);
            kitConfig.save();
        }
    }

    /**
     * Update a kit's permission in config.
     */
    public void setKitPermission(String kitName, String permission) {
        Kit kit = getKit(kitName);
        if (kit != null) {
            kit.setPermission(permission);
            kitConfig.set("kits." + kitName.toLowerCase() + ".permission", permission);
            kitConfig.save();
        }
    }

    // ==================== Custom Item Operations ====================

    /**
     * Get a custom item by ID (case-insensitive).
     */
    public CustomItem getCustomItem(String id) {
        return customItems.get(id.toLowerCase());
    }

    /**
     * Get all custom items.
     */
    public Map<String, CustomItem> getCustomItems() {
        return Collections.unmodifiableMap(customItems);
    }

    /**
     * Check if a custom item exists.
     */
    public boolean customItemExists(String id) {
        return customItems.containsKey(id.toLowerCase());
    }

    // ==================== Cooldown Operations ====================

    /**
     * Get the cooldown manager.
     */
    public CooldownManager getCooldownManager() {
        return cooldownManager;
    }

    /**
     * Check if a player can claim a kit.
     */
    public boolean canClaim(UUID uuid, String kitName) {
        Kit kit = getKit(kitName);
        if (kit == null) return false;
        return !cooldownManager.isOnCooldown(uuid, kitName, kit.getCooldown());
    }

    /**
     * Record a kit claim.
     */
    public void recordClaim(UUID uuid, String kitName) {
        cooldownManager.setCooldown(uuid, kitName);
    }

    /**
     * Reset a kit cooldown for a player.
     */
    public void resetCooldown(UUID uuid, String kitName) {
        cooldownManager.resetCooldown(uuid, kitName);
    }

    /**
     * Get remaining cooldown for a player's kit.
     */
    public long getRemainingCooldown(UUID uuid, String kitName) {
        Kit kit = getKit(kitName);
        if (kit == null) return 0;
        return cooldownManager.getRemainingCooldown(uuid, kitName, kit.getCooldown());
    }

    /**
     * Create an Effect object from an EffectEntry.
     */
    public Effect createEffect(EffectEntry entry) {
        Effect effect = Effect.getEffect(entry.getEffectId());
        if (effect != null) {
            effect.setAmplifier(entry.getAmplifier());
            effect.setDuration(entry.getDuration() * 20); // Convert to ticks
            effect.setVisible(true);
        }
        return effect;
    }

    /**
     * Reload all kits and custom items from config.
     */
    public void reload() {
        kitConfig.reload();
        loadKits();
        loadCustomItems();
        cooldownManager.reload();
    }
}
