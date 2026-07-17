package com.server.kitsystem;

import cn.nukkit.Player;
import cn.nukkit.command.Command;
import cn.nukkit.command.CommandSender;
import cn.nukkit.item.Item;
import cn.nukkit.utils.TextFormat;

import java.util.Map;

/**
 * Handles the /customitem command.
 * Subcommands:
 *   /customitem give <player> <itemId> [amount] - Give custom item to player
 *   /customitem list - List all custom items
 */
public class CustomItemCommand extends Command {

    private final KitSystemPlugin plugin;

    public CustomItemCommand(KitSystemPlugin plugin) {
        super("customitem", "Custom item commands", "/customitem <give|list>");
        this.plugin = plugin;
        this.setPermission("kit.admin");
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (!sender.hasPermission("kit.admin")) {
            sender.sendMessage(TextFormat.RED + "You don't have permission to use this command!");
            return true;
        }

        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        String subCmd = args[0].toLowerCase();

        switch (subCmd) {
            case "give":
                if (args.length < 3) {
                    sender.sendMessage(TextFormat.RED + "Usage: /customitem give <player> <itemId> [amount]");
                    return true;
                }
                int amount = 1;
                if (args.length >= 4) {
                    try {
                        amount = Integer.parseInt(args[3]);
                        if (amount < 1) amount = 1;
                        if (amount > 64) amount = 64;
                    } catch (NumberFormatException e) {
                        sender.sendMessage(TextFormat.RED + "Invalid amount: " + args[3]);
                        return true;
                    }
                }
                handleGive(sender, args[1], args[2], amount);
                break;

            case "list":
                handleList(sender);
                break;

            default:
                sendUsage(sender);
                break;
        }

        return true;
    }

    /**
     * Send usage info to the sender.
     */
    private void sendUsage(CommandSender sender) {
        sender.sendMessage(TextFormat.GOLD + "=== Custom Item Commands ===");
        sender.sendMessage(TextFormat.YELLOW + "/customitem give <player> <itemId> [amount]" + TextFormat.GRAY + " - Give custom item");
        sender.sendMessage(TextFormat.YELLOW + "/customitem list" + TextFormat.GRAY + " - List custom items");
    }

    /**
     * Handle /customitem give <player> <itemId> [amount] - Give a custom item to a player.
     */
    private void handleGive(CommandSender sender, String playerName, String itemId, int amount) {
        KitManager kitManager = plugin.getKitManager();

        if (!kitManager.customItemExists(itemId)) {
            sender.sendMessage(TextFormat.RED + "Custom item '" + itemId + "' does not exist!");
            sender.sendMessage(TextFormat.GRAY + "Use /customitem list to see available items.");
            return;
        }

        Player target = plugin.getServer().getPlayer(playerName);
        if (target == null) {
            sender.sendMessage(TextFormat.RED + "Player '" + playerName + "' is not online!");
            return;
        }

        CustomItem customItem = kitManager.getCustomItem(itemId);
        Item item = customItem.createItem(amount);

        // Add item to inventory, drop overflow
        Item[] overflow = target.getInventory().addItem(item);
        for (Item drop : overflow) {
            if (drop != null && drop.getCount() > 0) {
                target.getLevel().dropItem(target, drop);
            }
        }

        sender.sendMessage(TextFormat.GREEN + "Gave " + TextFormat.WHITE + amount + "x " +
                customItem.getName() + TextFormat.GREEN + " to " + TextFormat.WHITE + target.getName());

        target.sendMessage(TextFormat.GREEN + "You received " + TextFormat.WHITE + amount + "x " +
                customItem.getName() + TextFormat.GREEN + "!");
    }

    /**
     * Handle /customitem list - List all custom items.
     */
    private void handleList(CommandSender sender) {
        KitManager kitManager = plugin.getKitManager();
        Map<String, CustomItem> customItems = kitManager.getCustomItems();

        if (customItems.isEmpty()) {
            sender.sendMessage(TextFormat.YELLOW + "No custom items are defined.");
            return;
        }

        sender.sendMessage(TextFormat.GOLD + "=== Custom Items ===");

        for (CustomItem customItem : customItems.values()) {
            StringBuilder info = new StringBuilder();
            info.append(TextFormat.YELLOW).append(customItem.getId());
            info.append(TextFormat.GRAY).append(" - ");
            info.append(customItem.getName());
            info.append(TextFormat.DARK_GRAY).append(" [").append(customItem.getMaterial()).append("]");

            // Show enchantments
            if (!customItem.getEnchantments().isEmpty()) {
                info.append(TextFormat.AQUA).append(" ");
                for (CustomItem.EnchantmentEntry ench : customItem.getEnchantments()) {
                    info.append(ench.getEnchantmentName()).append(romanNumeral(ench.getLevel())).append(" ");
                }
            }

            sender.sendMessage(info.toString());

            // Show lore
            if (!customItem.getLore().isEmpty()) {
                for (String line : customItem.getLore()) {
                    sender.sendMessage(TextFormat.DARK_GRAY + "    " + line);
                }
            }
        }

        sender.sendMessage(TextFormat.GRAY + "Total: " + customItems.size() + " custom items");
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
}
