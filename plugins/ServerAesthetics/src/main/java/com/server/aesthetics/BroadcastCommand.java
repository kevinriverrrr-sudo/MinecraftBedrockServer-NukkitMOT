package com.server.aesthetics;

import cn.nukkit.command.Command;
import cn.nukkit.command.CommandSender;
import cn.nukkit.utils.TextFormat;

/**
 * Handles the /broadcast command.
 * Allows admins to broadcast a custom message to all online players.
 */
public class BroadcastCommand extends Command {

    private final ServerAestheticsPlugin plugin;

    public BroadcastCommand(ServerAestheticsPlugin plugin) {
        super("broadcast", "Broadcast message", "/broadcast <message>");
        this.plugin = plugin;
        this.setPermission("aesthetics.broadcast");
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (!testPermission(sender)) {
            sender.sendMessage(TextFormat.RED + "You don't have permission to use this command.");
            return false;
        }

        if (args.length == 0) {
            sender.sendMessage(TextFormat.RED + "Usage: /broadcast <message>");
            return false;
        }

        // Join all arguments into a single message
        StringBuilder messageBuilder = new StringBuilder();
        for (int i = 0; i < args.length; i++) {
            if (i > 0) {
                messageBuilder.append(" ");
            }
            messageBuilder.append(args[i]);
        }

        String message = messageBuilder.toString();

        // Broadcast the message using the BroadcastManager
        plugin.getBroadcastManager().broadcastRawMessage(message);

        // Confirm to sender
        sender.sendMessage(TextFormat.colorize("§7Message broadcasted."));

        return true;
    }
}
