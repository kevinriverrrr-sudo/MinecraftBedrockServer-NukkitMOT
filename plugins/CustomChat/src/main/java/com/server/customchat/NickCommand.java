package com.server.customchat;

import cn.nukkit.Player;
import cn.nukkit.command.Command;
import cn.nukkit.command.CommandExecutor;
import cn.nukkit.command.CommandSender;
import cn.nukkit.utils.TextFormat;

/**
 * Handles the /nick command for setting and resetting player nicknames.
 * Implements CommandExecutor for proper Nukkit command integration.
 */
public class NickCommand implements CommandExecutor {

    private final CustomChatPlugin plugin;

    public NickCommand(CustomChatPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(TextFormat.RED + "This command can only be used by players.");
            return true;
        }

        Player player = (Player) sender;

        // Permission check
        if (!player.hasPermission("chat.nick")) {
            player.sendMessage(TextFormat.RED + "You don't have permission to use this command.");
            return true;
        }

        if (args.length == 0) {
            sendUsage(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "set":
                handleSet(player, args);
                break;
            case "reset":
            case "off":
            case "clear":
                handleReset(player);
                break;
            default:
                // If they just type /nick <name>, treat it as /nick set <name>
                if (args.length >= 1) {
                    // Combine all args as the nickname
                    StringBuilder nickBuilder = new StringBuilder();
                    for (int i = 0; i < args.length; i++) {
                        if (i > 0) nickBuilder.append(" ");
                        nickBuilder.append(args[i]);
                    }
                    String nickname = nickBuilder.toString();
                    // Translate color codes
                    nickname = plugin.getNickManager().translateColorCodes(nickname);
                    plugin.getNickManager().setNick(player, nickname);
                } else {
                    sendUsage(player);
                }
                break;
        }

        return true;
    }

    /**
     * Handle /nick set <nickname> - set the player's nickname.
     *
     * @param player The player
     * @param args   Command arguments
     */
    private void handleSet(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(TextFormat.RED + "Usage: /nick set <nickname>");
            player.sendMessage(TextFormat.GRAY + "Use & for color codes. Example: /nick set &bCool&fPlayer");
            return;
        }

        // Build the nickname from remaining arguments
        StringBuilder nickBuilder = new StringBuilder();
        for (int i = 1; i < args.length; i++) {
            if (i > 1) nickBuilder.append(" ");
            nickBuilder.append(args[i]);
        }

        String nickname = nickBuilder.toString();

        // Translate & color codes to §
        nickname = plugin.getNickManager().translateColorCodes(nickname);

        // Set the nickname (NickManager handles validation)
        plugin.getNickManager().setNick(player, nickname);
    }

    /**
     * Handle /nick reset - reset the player's nickname.
     *
     * @param player The player
     */
    private void handleReset(Player player) {
        plugin.getNickManager().resetNick(player);
    }

    /**
     * Send usage information to the player.
     *
     * @param player The player
     */
    private void sendUsage(Player player) {
        player.sendMessage(TextFormat.GOLD + "=== Nickname Commands ===");
        player.sendMessage(TextFormat.WHITE + "/nick set <nickname>" + TextFormat.GRAY + " - Set your nickname");
        player.sendMessage(TextFormat.WHITE + "/nick reset" + TextFormat.GRAY + " - Reset your nickname");
        player.sendMessage(TextFormat.WHITE + "/nick <nickname>" + TextFormat.GRAY + " - Quick set nickname");
        player.sendMessage(TextFormat.GRAY + "Use & for color codes. Example: &b&lCoolPlayer");

        if (plugin.getNickManager().hasNick(player)) {
            player.sendMessage(TextFormat.YELLOW + "Current nickname: " + TextFormat.WHITE + plugin.getNickManager().getNick(player));
        } else {
            player.sendMessage(TextFormat.YELLOW + "You don't have a nickname set.");
        }
    }
}
