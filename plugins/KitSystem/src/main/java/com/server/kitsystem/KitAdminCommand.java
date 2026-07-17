package com.server.kitsystem;

import cn.nukkit.Player;
import cn.nukkit.command.Command;
import cn.nukkit.command.CommandSender;
import cn.nukkit.item.Item;
import cn.nukkit.utils.TextFormat;

import java.util.Map;

/**
 * Handles the /kitadmin command.
 * Subcommands:
 *   /kitadmin create <name> - Create kit from current inventory
 *   /kitadmin delete <name> - Delete a kit
 *   /kitadmin setcooldown <name> <seconds> - Set kit cooldown
 *   /kitadmin setpermission <name> <perm> - Set required permission
 *   /kitadmin give <player> <name> - Force give kit to player
 */
public class KitAdminCommand extends Command {

    private final KitSystemPlugin plugin;

    public KitAdminCommand(KitSystemPlugin plugin) {
        super("kitadmin", "Kit admin commands", "/kitadmin <create|delete|setcooldown|setpermission|give>");
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
            case "create":
                if (args.length < 2) {
                    sender.sendMessage(TextFormat.RED + "Usage: /kitadmin create <name>");
                    return true;
                }
                handleCreate(sender, args[1]);
                break;

            case "delete":
                if (args.length < 2) {
                    sender.sendMessage(TextFormat.RED + "Usage: /kitadmin delete <name>");
                    return true;
                }
                handleDelete(sender, args[1]);
                break;

            case "setcooldown":
                if (args.length < 3) {
                    sender.sendMessage(TextFormat.RED + "Usage: /kitadmin setcooldown <name> <seconds>");
                    return true;
                }
                handleSetCooldown(sender, args[1], args[2]);
                break;

            case "setpermission":
                if (args.length < 3) {
                    sender.sendMessage(TextFormat.RED + "Usage: /kitadmin setpermission <name> <perm>");
                    return true;
                }
                handleSetPermission(sender, args[1], args[2]);
                break;

            case "give":
                if (args.length < 3) {
                    sender.sendMessage(TextFormat.RED + "Usage: /kitadmin give <player> <name>");
                    return true;
                }
                handleGive(sender, args[1], args[2]);
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
        sender.sendMessage(TextFormat.GOLD + "=== Kit Admin Commands ===");
        sender.sendMessage(TextFormat.YELLOW + "/kitadmin create <name>" + TextFormat.GRAY + " - Create kit from inventory");
        sender.sendMessage(TextFormat.YELLOW + "/kitadmin delete <name>" + TextFormat.GRAY + " - Delete a kit");
        sender.sendMessage(TextFormat.YELLOW + "/kitadmin setcooldown <name> <seconds>" + TextFormat.GRAY + " - Set kit cooldown");
        sender.sendMessage(TextFormat.YELLOW + "/kitadmin setpermission <name> <perm>" + TextFormat.GRAY + " - Set required permission");
        sender.sendMessage(TextFormat.YELLOW + "/kitadmin give <player> <name>" + TextFormat.GRAY + " - Force give kit");
    }

    /**
     * Handle /kitadmin create <name> - Create a kit from the player's current inventory.
     */
    private void handleCreate(CommandSender sender, String kitName) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(TextFormat.RED + "Only players can create kits from their inventory!");
            return;
        }

        KitManager kitManager = plugin.getKitManager();

        // Check if kit already exists
        if (kitManager.kitExists(kitName)) {
            sender.sendMessage(TextFormat.RED + "Kit '" + kitName + "' already exists! Delete it first.");
            return;
        }

        Player player = (Player) sender;

        // Create new kit
        Kit kit = new Kit(kitName);
        kit.setDisplayName("§f" + kitName.substring(0, 1).toUpperCase() + kitName.substring(1) + " Kit");
        kit.setDescription("Custom kit created by " + player.getName());
        kit.setPermission("");
        kit.setCooldown(3600); // Default 1 hour cooldown
        kit.setIcon("stick"); // Default icon

        // Copy all items from the player's inventory
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            Item item = player.getInventory().getItem(i);
            if (item != null && item.getId() != Item.AIR && item.getCount() > 0) {
                kit.addItem(item.clone());
            }
        }

        // Copy armor
        Item helmet = player.getInventory().getHelmet();
        if (helmet != null && helmet.getId() != Item.AIR) {
            kit.setHelmet(helmet.clone());
        }

        Item chestplate = player.getInventory().getChestplate();
        if (chestplate != null && chestplate.getId() != Item.AIR) {
            kit.setChestplate(chestplate.clone());
        }

        Item leggings = player.getInventory().getLeggings();
        if (leggings != null && leggings.getId() != Item.AIR) {
            kit.setLeggings(leggings.clone());
        }

        Item boots = player.getInventory().getBoots();
        if (boots != null && boots.getId() != Item.AIR) {
            kit.setBoots(boots.clone());
        }

        // Add kit to manager and save
        kitManager.addKit(kit);

        player.sendMessage(TextFormat.GREEN + "Created kit '" + TextFormat.YELLOW + kitName +
                TextFormat.GREEN + "' from your inventory!");
        player.sendMessage(TextFormat.GRAY + "Items: " + TextFormat.WHITE + kit.getItems().size() +
                TextFormat.GRAY + " | Cooldown: " + TextFormat.WHITE + CooldownManager.formatTime(kit.getCooldown()));
        player.sendMessage(TextFormat.GRAY + "Use /kitadmin setcooldown and /kitadmin setpermission to configure.");
    }

    /**
     * Handle /kitadmin delete <name> - Delete a kit.
     */
    private void handleDelete(CommandSender sender, String kitName) {
        KitManager kitManager = plugin.getKitManager();

        if (!kitManager.kitExists(kitName)) {
            sender.sendMessage(TextFormat.RED + "Kit '" + kitName + "' does not exist!");
            return;
        }

        Kit kit = kitManager.getKit(kitName);
        kitManager.removeKit(kitName);

        sender.sendMessage(TextFormat.GREEN + "Deleted kit '" + TextFormat.YELLOW + kitName +
                TextFormat.GREEN + "' successfully!");
    }

    /**
     * Handle /kitadmin setcooldown <name> <seconds> - Set kit cooldown.
     */
    private void handleSetCooldown(CommandSender sender, String kitName, String secondsStr) {
        KitManager kitManager = plugin.getKitManager();

        if (!kitManager.kitExists(kitName)) {
            sender.sendMessage(TextFormat.RED + "Kit '" + kitName + "' does not exist!");
            return;
        }

        long seconds;
        try {
            seconds = Long.parseLong(secondsStr);
            if (seconds < 0) {
                sender.sendMessage(TextFormat.RED + "Cooldown must be 0 or greater! (0 = one-time kit)");
                return;
            }
        } catch (NumberFormatException e) {
            sender.sendMessage(TextFormat.RED + "Invalid number: " + secondsStr);
            return;
        }

        Kit kit = kitManager.getKit(kitName);
        kitManager.setKitCooldown(kitName, seconds);

        String cooldownText;
        if (seconds == 0) {
            cooldownText = "One-time claim";
        } else {
            cooldownText = CooldownManager.formatTime(seconds);
        }

        sender.sendMessage(TextFormat.GREEN + "Set cooldown for kit '" + TextFormat.YELLOW + kit.getDisplayName() +
                TextFormat.GREEN + "' to " + TextFormat.WHITE + cooldownText);
    }

    /**
     * Handle /kitadmin setpermission <name> <perm> - Set kit permission.
     */
    private void handleSetPermission(CommandSender sender, String kitName, String permission) {
        KitManager kitManager = plugin.getKitManager();

        if (!kitManager.kitExists(kitName)) {
            sender.sendMessage(TextFormat.RED + "Kit '" + kitName + "' does not exist!");
            return;
        }

        Kit kit = kitManager.getKit(kitName);
        kitManager.setKitPermission(kitName, permission);

        String permDisplay = permission.isEmpty() ? "None (public)" : permission;
        sender.sendMessage(TextFormat.GREEN + "Set permission for kit '" + TextFormat.YELLOW + kit.getDisplayName() +
                TextFormat.GREEN + "' to " + TextFormat.WHITE + permDisplay);
    }

    /**
     * Handle /kitadmin give <player> <name> - Force give kit to player (ignores cooldowns and permissions).
     */
    private void handleGive(CommandSender sender, String playerName, String kitName) {
        KitManager kitManager = plugin.getKitManager();

        if (!kitManager.kitExists(kitName)) {
            sender.sendMessage(TextFormat.RED + "Kit '" + kitName + "' does not exist!");
            return;
        }

        Player target = plugin.getServer().getPlayer(playerName);
        if (target == null) {
            sender.sendMessage(TextFormat.RED + "Player '" + playerName + "' is not online!");
            return;
        }

        Kit kit = kitManager.getKit(kitName);

        // Give the kit using the KitCommand helper method
        KitCommand kitCommand = plugin.getKitCommand();
        kitCommand.giveKitToPlayer(target, kit);

        sender.sendMessage(TextFormat.GREEN + "Force-gave kit '" + TextFormat.YELLOW + kit.getDisplayName() +
                TextFormat.GREEN + "' to " + TextFormat.WHITE + target.getName());

        target.sendMessage(TextFormat.GREEN + "An admin gave you the " + kit.getDisplayName() +
                TextFormat.GREEN + " kit!");
    }
}
