package com.server.customchat;

import cn.nukkit.Player;
import cn.nukkit.command.Command;
import cn.nukkit.command.CommandExecutor;
import cn.nukkit.command.CommandSender;
import cn.nukkit.utils.TextFormat;

import java.util.Set;

/**
 * Handles chat-related commands: /channel, /msg, /reply, /ignore.
 * Implements CommandExecutor for proper Nukkit command integration.
 */
public class ChatCommand implements CommandExecutor {

    private final CustomChatPlugin plugin;

    public ChatCommand(CustomChatPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(TextFormat.RED + "This command can only be used by players.");
            return true;
        }

        Player player = (Player) sender;

        // Determine which command was called based on the command name
        String cmdName = command.getName().toLowerCase();

        switch (cmdName) {
            case "channel":
                handleChannel(player, args);
                break;
            case "msg":
                handleMsg(player, args);
                break;
            case "reply":
                handleReply(player, args);
                break;
            case "ignore":
                handleIgnore(player, args);
                break;
            default:
                // Also check label for aliases
                String labelLower = label.toLowerCase();
                if (labelLower.equals("ch")) {
                    handleChannel(player, args);
                } else if (labelLower.equals("r")) {
                    handleReply(player, args);
                } else if (labelLower.equals("tell") || labelLower.equals("w") ||
                        labelLower.equals("whisper") || labelLower.equals("pm")) {
                    handleMsg(player, args);
                } else {
                    player.sendMessage(TextFormat.RED + "Unknown command.");
                }
                break;
        }

        return true;
    }

    /**
     * Handle /channel command - switch between chat channels.
     *
     * @param player The player
     * @param args   Command arguments
     */
    private void handleChannel(Player player, String[] args) {
        ChatManager chatManager = plugin.getChatManager();

        if (args.length == 0) {
            // Show current channel and available channels
            String currentChannel = chatManager.getPlayerChannel(player);
            player.sendMessage(TextFormat.GOLD + "=== Chat Channels ===");
            player.sendMessage(TextFormat.YELLOW + "Current channel: " + TextFormat.WHITE + currentChannel);
            player.sendMessage(TextFormat.YELLOW + "Available channels:");

            for (String channelName : chatManager.getChannelNames()) {
                ChatChannel channel = chatManager.getChannel(channelName);
                String permInfo = channel.hasPermission() ? TextFormat.RED + " (requires permission)" : TextFormat.GREEN + " (open)";
                String radiusInfo = channel.isLocal() ? TextFormat.AQUA + " [Local: " + channel.getRadius() + " blocks]" : "";
                String cooldownInfo = channel.hasCooldown() ? TextFormat.LIGHT_PURPLE + " [Cooldown: " + channel.getCooldown() + "s]" : "";
                String current = channelName.equalsIgnoreCase(currentChannel) ? TextFormat.GREEN + " ◄" : "";

                player.sendMessage(TextFormat.WHITE + "  - " + channelName + permInfo + radiusInfo + cooldownInfo + current);
            }

            player.sendMessage(TextFormat.GRAY + "Use /channel <name> to switch channels.");
            return;
        }

        String targetChannel = args[0].toLowerCase();
        chatManager.setPlayerChannel(player, targetChannel);
    }

    /**
     * Handle /msg command - send a private message.
     *
     * @param player The player
     * @param args   Command arguments
     */
    private void handleMsg(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(TextFormat.RED + "Usage: /msg <player> <message>");
            return;
        }

        String targetName = args[0];

        // Check if target is online
        Player target = plugin.getServer().getPlayer(targetName);
        if (target == null) {
            player.sendMessage(TextFormat.RED + "Player " + TextFormat.WHITE + targetName + TextFormat.RED + " is not online.");
            return;
        }

        // Build the message from remaining arguments
        StringBuilder messageBuilder = new StringBuilder();
        for (int i = 1; i < args.length; i++) {
            if (i > 1) messageBuilder.append(" ");
            messageBuilder.append(args[i]);
        }
        String message = messageBuilder.toString();

        // Send the private message
        plugin.getPrivateMessageManager().sendPrivateMessage(player, targetName, message);
    }

    /**
     * Handle /reply command - reply to the last private message.
     *
     * @param player The player
     * @param args   Command arguments
     */
    private void handleReply(Player player, String[] args) {
        if (args.length == 0) {
            player.sendMessage(TextFormat.RED + "Usage: /reply <message>");
            return;
        }

        // Build the message from all arguments
        StringBuilder messageBuilder = new StringBuilder();
        for (int i = 0; i < args.length; i++) {
            if (i > 0) messageBuilder.append(" ");
            messageBuilder.append(args[i]);
        }
        String message = messageBuilder.toString();

        // Reply to the last messenger
        plugin.getPrivateMessageManager().reply(player, message);
    }

    /**
     * Handle /ignore command - ignore or unignore a player.
     *
     * @param player The player
     * @param args   Command arguments
     */
    private void handleIgnore(Player player, String[] args) {
        if (args.length == 0) {
            // Show current ignore list
            Set<String> ignored = plugin.getIgnoreManager().getIgnoredList(player);
            if (ignored.isEmpty()) {
                player.sendMessage(TextFormat.YELLOW + "You are not ignoring anyone.");
            } else {
                player.sendMessage(TextFormat.GOLD + "=== Ignored Players ===");
                for (String name : ignored) {
                    player.sendMessage(TextFormat.WHITE + "  - " + name);
                }
            }
            player.sendMessage(TextFormat.GRAY + "Use /ignore <player> to ignore/unignore a player.");
            return;
        }

        String targetName = args[0];
        Player target = plugin.getServer().getPlayer(targetName);

        if (target == null) {
            player.sendMessage(TextFormat.RED + "Player " + TextFormat.WHITE + targetName + TextFormat.RED + " is not online.");
            return;
        }

        // Toggle ignore
        plugin.getIgnoreManager().toggleIgnore(player, target);
    }
}
