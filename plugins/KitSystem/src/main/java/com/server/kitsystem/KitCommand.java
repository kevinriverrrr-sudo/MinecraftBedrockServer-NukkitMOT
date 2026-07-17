package com.server.kitsystem;

import cn.nukkit.Player;
import cn.nukkit.command.Command;
import cn.nukkit.command.CommandSender;
import cn.nukkit.item.Item;
import cn.nukkit.potion.Effect;
import cn.nukkit.utils.TextFormat;
import com.server.kitsystem.Kit.EffectEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Handles the /kit command.
 * Subcommands:
 *   /kit list - List available kits
 *   /kit <name> - Claim a kit
 *   /kit preview <name> - Preview kit contents
 *   /kit reset <player> <kit> - Reset cooldown (admin)
 */
public class KitCommand extends Command {

    private final KitSystemPlugin plugin;

    public KitCommand(KitSystemPlugin plugin) {
        super("kit", "Kit commands", "/kit <list|preview|<name>>");
        this.plugin = plugin;
        this.setPermission("");
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        String subCmd = args[0].toLowerCase();

        switch (subCmd) {
            case "list":
                handleList(sender);
                break;
            case "preview":
                if (args.length < 2) {
                    sender.sendMessage(TextFormat.RED + "Usage: /kit preview <name>");
                    return true;
                }
                handlePreview(sender, args[1]);
                break;
            case "reset":
                if (args.length < 3) {
                    sender.sendMessage(TextFormat.RED + "Usage: /kit reset <player> <kit>");
                    return true;
                }
                handleReset(sender, args[1], args[2]);
                break;
            default:
                // Try to claim a kit by name
                handleClaim(sender, subCmd);
                break;
        }

        return true;
    }

    /**
     * Send usage info to the sender.
     */
    private void sendUsage(CommandSender sender) {
        sender.sendMessage(TextFormat.GOLD + "=== Kit System ===");
        sender.sendMessage(TextFormat.YELLOW + "/kit list" + TextFormat.GRAY + " - List available kits");
        sender.sendMessage(TextFormat.YELLOW + "/kit <name>" + TextFormat.GRAY + " - Claim a kit");
        sender.sendMessage(TextFormat.YELLOW + "/kit preview <name>" + TextFormat.GRAY + " - Preview kit contents");
        if (sender.hasPermission("kit.admin")) {
            sender.sendMessage(TextFormat.YELLOW + "/kit reset <player> <kit>" + TextFormat.GRAY + " - Reset cooldown");
        }
    }

    /**
     * Handle /kit list - Show all kits with their status.
     * Green = available, Red = on cooldown, Gray = no permission
     */
    private void handleList(CommandSender sender) {
        KitManager kitManager = plugin.getKitManager();
        Map<String, Kit> kits = kitManager.getKits();

        if (kits.isEmpty()) {
            sender.sendMessage(TextFormat.YELLOW + "No kits are currently available.");
            return;
        }

        sender.sendMessage(TextFormat.GOLD + "=== Available Kits ===");

        for (Kit kit : kits.values()) {
            TextFormat statusColor;
            String statusText;

            // Check permission
            if (!kit.getPermission().isEmpty() && !sender.hasPermission(kit.getPermission())) {
                statusColor = TextFormat.GRAY;
                statusText = TextFormat.GRAY + "[Locked]";
            }
            // Check cooldown (only for players)
            else if (sender instanceof Player) {
                Player player = (Player) sender;
                UUID uuid = player.getUniqueId();

                if (kitManager.canClaim(uuid, kit.getName())) {
                    statusColor = TextFormat.GREEN;
                    statusText = TextFormat.GREEN + "[Available]";
                } else {
                    long remaining = kitManager.getRemainingCooldown(uuid, kit.getName());
                    if (remaining == -1) {
                        statusColor = TextFormat.RED;
                        statusText = TextFormat.RED + "[Claimed]";
                    } else {
                        statusColor = TextFormat.RED;
                        statusText = TextFormat.RED + "[Cooldown: " + CooldownManager.formatTime(remaining) + "]";
                    }
                }
            }
            // Console always sees as available
            else {
                statusColor = TextFormat.GREEN;
                statusText = TextFormat.GREEN + "[Available]";
            }

            sender.sendMessage(statusColor + " " + kit.getDisplayName() + " " + statusText +
                    TextFormat.DARK_GRAY + " - " + TextFormat.GRAY + kit.getDescription());
        }
    }

    /**
     * Handle /kit <name> - Claim a kit.
     */
    private void handleClaim(CommandSender sender, String kitName) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(TextFormat.RED + "Only players can claim kits!");
            return;
        }

        Player player = (Player) sender;
        KitManager kitManager = plugin.getKitManager();
        Kit kit = kitManager.getKit(kitName);

        if (kit == null) {
            player.sendMessage(TextFormat.RED + "Kit '" + kitName + "' does not exist!");
            return;
        }

        // Check permission
        if (!kit.getPermission().isEmpty() && !player.hasPermission(kit.getPermission())) {
            player.sendMessage(TextFormat.RED + "You don't have permission to claim the " + kit.getDisplayName() + TextFormat.RED + " kit!");
            return;
        }

        UUID uuid = player.getUniqueId();

        // Check cooldown
        if (!kitManager.canClaim(uuid, kitName)) {
            long remaining = kitManager.getRemainingCooldown(uuid, kitName);
            if (remaining == -1) {
                player.sendMessage(TextFormat.RED + "You have already claimed the " + kit.getDisplayName() +
                        TextFormat.RED + " kit! This is a one-time kit.");
            } else {
                player.sendMessage(TextFormat.RED + "The " + kit.getDisplayName() + TextFormat.RED +
                        " kit is on cooldown! Time remaining: " + TextFormat.YELLOW +
                        CooldownManager.formatTime(remaining));
            }
            return;
        }

        // Give items
        giveKitToPlayer(player, kit);

        // Record cooldown
        kitManager.recordClaim(uuid, kitName);

        player.sendMessage(TextFormat.GREEN + "You have claimed the " + kit.getDisplayName() +
                TextFormat.GREEN + " kit!");

        // Show cooldown info for next claim
        if (kit.getCooldown() > 0) {
            player.sendMessage(TextFormat.GRAY + "Next claim available in: " +
                    TextFormat.YELLOW + CooldownManager.formatTime(kit.getCooldown()));
        } else {
            player.sendMessage(TextFormat.GRAY + "This is a one-time kit and cannot be claimed again.");
        }
    }

    /**
     * Give all kit items to a player.
     */
    public void giveKitToPlayer(Player player, Kit kit) {
        // Give inventory items
        for (Item item : kit.getItems()) {
            if (item != null) {
                Item[] overflow = player.getInventory().addItem(item.clone());
                // Drop overflow items
                for (Item drop : overflow) {
                    if (drop != null && drop.getCount() > 0) {
                        player.getLevel().dropItem(player, drop);
                    }
                }
            }
        }

        // Give armor
        if (kit.getHelmet() != null) {
            Item current = player.getInventory().getHelmet();
            if (current == null || current.getId() == Item.AIR) {
                player.getInventory().setHelmet(kit.getHelmet().clone());
            } else {
                Item[] overflow = player.getInventory().addItem(kit.getHelmet().clone());
                for (Item drop : overflow) {
                    if (drop != null && drop.getCount() > 0) {
                        player.getLevel().dropItem(player, drop);
                    }
                }
            }
        }

        if (kit.getChestplate() != null) {
            Item current = player.getInventory().getChestplate();
            if (current == null || current.getId() == Item.AIR) {
                player.getInventory().setChestplate(kit.getChestplate().clone());
            } else {
                Item[] overflow = player.getInventory().addItem(kit.getChestplate().clone());
                for (Item drop : overflow) {
                    if (drop != null && drop.getCount() > 0) {
                        player.getLevel().dropItem(player, drop);
                    }
                }
            }
        }

        if (kit.getLeggings() != null) {
            Item current = player.getInventory().getLeggings();
            if (current == null || current.getId() == Item.AIR) {
                player.getInventory().setLeggings(kit.getLeggings().clone());
            } else {
                Item[] overflow = player.getInventory().addItem(kit.getLeggings().clone());
                for (Item drop : overflow) {
                    if (drop != null && drop.getCount() > 0) {
                        player.getLevel().dropItem(player, drop);
                    }
                }
            }
        }

        if (kit.getBoots() != null) {
            Item current = player.getInventory().getBoots();
            if (current == null || current.getId() == Item.AIR) {
                player.getInventory().setBoots(kit.getBoots().clone());
            } else {
                Item[] overflow = player.getInventory().addItem(kit.getBoots().clone());
                for (Item drop : overflow) {
                    if (drop != null && drop.getCount() > 0) {
                        player.getLevel().dropItem(player, drop);
                    }
                }
            }
        }

        // Give offhand item
        if (kit.getOffhand() != null) {
            Item[] overflow = player.getInventory().addItem(kit.getOffhand().clone());
            for (Item drop : overflow) {
                if (drop != null && drop.getCount() > 0) {
                    player.getLevel().dropItem(player, drop);
                }
            }
        }

        // Apply potion effects
        for (EffectEntry entry : kit.getEffects()) {
            Effect effect = plugin.getKitManager().createEffect(entry);
            if (effect != null) {
                player.addEffect(effect);
            }
        }
    }

    /**
     * Handle /kit preview <name> - Show kit contents in chat.
     */
    private void handlePreview(CommandSender sender, String kitName) {
        KitManager kitManager = plugin.getKitManager();
        Kit kit = kitManager.getKit(kitName);

        if (kit == null) {
            sender.sendMessage(TextFormat.RED + "Kit '" + kitName + "' does not exist!");
            return;
        }

        // Check permission - players without permission can't preview
        if (sender instanceof Player) {
            Player player = (Player) sender;
            if (!kit.getPermission().isEmpty() && !player.hasPermission(kit.getPermission())) {
                player.sendMessage(TextFormat.RED + "You don't have permission to preview the " +
                        kit.getDisplayName() + TextFormat.RED + " kit!");
                return;
            }
        }

        sender.sendMessage(TextFormat.GOLD + "======= " + kit.getDisplayName() + TextFormat.GOLD + " =======");
        sender.sendMessage(TextFormat.YELLOW + "Description: " + TextFormat.WHITE + kit.getDescription());

        // Cooldown info
        String cooldownInfo;
        if (kit.getCooldown() == 0) {
            cooldownInfo = "One-time claim";
        } else {
            cooldownInfo = CooldownManager.formatTime(kit.getCooldown());
        }
        sender.sendMessage(TextFormat.YELLOW + "Cooldown: " + TextFormat.WHITE + cooldownInfo);

        // Items
        sender.sendMessage(TextFormat.YELLOW + "Items:");
        for (Item item : kit.getItems()) {
            String itemName = item.hasCustomName() ? item.getCustomName() : item.getName();
            StringBuilder itemInfo = new StringBuilder();
            itemInfo.append(TextFormat.WHITE).append("  - ").append(itemName);
            itemInfo.append(TextFormat.GRAY).append(" x").append(item.getCount());

            // Show enchantments
            if (item.hasEnchantments()) {
                itemInfo.append(TextFormat.AQUA).append(" [");
                for (cn.nukkit.item.enchantment.Enchantment ench : item.getEnchantments()) {
                    itemInfo.append(ench.getName()).append(" ").append(romanNumeral(ench.getLevel())).append(", ");
                }
                // Remove trailing comma
                if (itemInfo.charAt(itemInfo.length() - 2) == ',') {
                    itemInfo.delete(itemInfo.length() - 2, itemInfo.length());
                }
                itemInfo.append("]");
            }

            sender.sendMessage(itemInfo.toString());
        }

        // Armor
        sender.sendMessage(TextFormat.YELLOW + "Armor:");
        if (kit.getHelmet() != null) sender.sendMessage(TextFormat.WHITE + "  Helmet: " + kit.getHelmet().getName());
        if (kit.getChestplate() != null) sender.sendMessage(TextFormat.WHITE + "  Chestplate: " + kit.getChestplate().getName());
        if (kit.getLeggings() != null) sender.sendMessage(TextFormat.WHITE + "  Leggings: " + kit.getLeggings().getName());
        if (kit.getBoots() != null) sender.sendMessage(TextFormat.WHITE + "  Boots: " + kit.getBoots().getName());
        if (kit.getHelmet() == null && kit.getChestplate() == null &&
                kit.getLeggings() == null && kit.getBoots() == null) {
            sender.sendMessage(TextFormat.GRAY + "  None");
        }

        // Effects
        sender.sendMessage(TextFormat.YELLOW + "Effects:");
        if (kit.getEffects().isEmpty()) {
            sender.sendMessage(TextFormat.GRAY + "  None");
        } else {
            for (EffectEntry entry : kit.getEffects()) {
                String effectName = resolveEffectName(entry.getEffectId());
                sender.sendMessage(TextFormat.WHITE + "  - " + effectName +
                        " " + romanNumeral(entry.getAmplifier() + 1) +
                        TextFormat.GRAY + " (" + entry.getDuration() + "s)");
            }
        }
    }

    /**
     * Handle /kit reset <player> <kit> - Reset cooldown for a player (admin only).
     */
    private void handleReset(CommandSender sender, String playerName, String kitName) {
        if (!sender.hasPermission("kit.admin")) {
            sender.sendMessage(TextFormat.RED + "You don't have permission to reset kit cooldowns!");
            return;
        }

        KitManager kitManager = plugin.getKitManager();
        Kit kit = kitManager.getKit(kitName);

        if (kit == null) {
            sender.sendMessage(TextFormat.RED + "Kit '" + kitName + "' does not exist!");
            return;
        }

        // Find the player (may be offline)
        Player target = plugin.getServer().getPlayer(playerName);
        if (target != null) {
            kitManager.resetCooldown(target.getUniqueId(), kitName);
            sender.sendMessage(TextFormat.GREEN + "Reset cooldown for " + TextFormat.YELLOW + target.getName() +
                    TextFormat.GREEN + " on kit " + kit.getDisplayName());
        } else {
            // Try to find offline player - we need UUID
            sender.sendMessage(TextFormat.RED + "Player '" + playerName + "' is not online! " +
                    TextFormat.GRAY + "(Offline cooldown reset is not supported yet)");
        }
    }

    /**
     * Convert a number to a roman numeral string.
     */
    private String romanNumeral(int number) {
        if (number <= 0) return "0";
        String[] numerals = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        if (number < numerals.length) {
            return numerals[number];
        }
        return String.valueOf(number);
    }

    /**
     * Resolve an effect ID back to a readable name.
     */
    private String resolveEffectName(int id) {
        if (id == Effect.SPEED) return "Speed";
        if (id == Effect.SLOWNESS) return "Slowness";
        if (id == Effect.HASTE) return "Haste";
        if (id == Effect.MINING_FATIGUE) return "Mining Fatigue";
        if (id == Effect.STRENGTH) return "Strength";
        if (id == Effect.HEALING) return "Instant Health";
        if (id == Effect.HARMING) return "Instant Damage";
        if (id == Effect.JUMP_BOOST) return "Jump Boost";
        if (id == Effect.NAUSEA) return "Nausea";
        if (id == Effect.REGENERATION) return "Regeneration";
        if (id == Effect.DAMAGE_RESISTANCE) return "Resistance";
        if (id == Effect.FIRE_RESISTANCE) return "Fire Resistance";
        if (id == Effect.WATER_BREATHING) return "Water Breathing";
        if (id == Effect.INVISIBILITY) return "Invisibility";
        if (id == Effect.BLINDNESS) return "Blindness";
        if (id == Effect.NIGHT_VISION) return "Night Vision";
        if (id == Effect.HUNGER) return "Hunger";
        if (id == Effect.WEAKNESS) return "Weakness";
        if (id == Effect.POISON) return "Poison";
        if (id == Effect.WITHER) return "Wither";
        if (id == Effect.HEALTH_BOOST) return "Health Boost";
        if (id == Effect.ABSORPTION) return "Absorption";
        if (id == Effect.SATURATION) return "Saturation";
        if (id == Effect.LEVITATION) return "Levitation";
        return "Unknown Effect";
    }
}
