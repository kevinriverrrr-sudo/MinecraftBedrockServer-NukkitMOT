package com.server.customchat;

import cn.nukkit.Player;
import cn.nukkit.utils.TextFormat;

import java.util.HashMap;
import java.util.Map;

/**
 * Manages private messaging between players with reply tracking.
 */
public class PrivateMessageManager {

    private final CustomChatPlugin plugin;

    // Player name (lowercase) -> the name of the last player they received a message from
    private final Map<String, String> lastMessenger = new HashMap<>();

    public PrivateMessageManager(CustomChatPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Send a private message from one player to another.
     *
     * @param sender   The sender
     * @param receiverName The receiver's name
     * @param message  The message content
     */
    public void sendPrivateMessage(Player sender, String receiverName, String message) {
        // Find the receiver
        Player receiver = plugin.getServer().getPlayer(receiverName);
        if (receiver == null) {
            sender.sendMessage(TextFormat.RED + "Player " + TextFormat.WHITE + receiverName + TextFormat.RED + " is not online.");
            return;
        }

        if (receiver.getName().equalsIgnoreCase(sender.getName())) {
            sender.sendMessage(TextFormat.RED + "You cannot send a message to yourself!");
            return;
        }

        // Check if receiver is ignoring the sender
        IgnoreManager ignoreManager = plugin.getIgnoreManager();
        if (ignoreManager.isIgnoring(receiver, sender)) {
            sender.sendMessage(TextFormat.RED + "You cannot send a message to " + TextFormat.WHITE + receiver.getName() + TextFormat.RED + ".");
            return;
        }

        // Check if sender is ignoring the receiver
        if (ignoreManager.isIgnoring(sender, receiver)) {
            sender.sendMessage(TextFormat.RED + "You are ignoring " + TextFormat.WHITE + receiver.getName() + TextFormat.RED + ". Unignore them first.");
            return;
        }

        // Get format strings from config
        String senderFormat = plugin.getConfig().getString("private-message.format-sender",
                "§7[§fYou §7→ §f{receiver}§7] §f{message}");
        String receiverFormat = plugin.getConfig().getString("private-message.format-receiver",
                "§7[§f{sender} §7→ §fYou§7] §f{message}");

        // Apply formatting
        String formattedSender = senderFormat
                .replace("{sender}", sender.getDisplayName())
                .replace("{receiver}", receiver.getDisplayName())
                .replace("{message}", message);

        String formattedReceiver = receiverFormat
                .replace("{sender}", sender.getDisplayName())
                .replace("{receiver}", receiver.getDisplayName())
                .replace("{message}", message);

        // Send messages
        sender.sendMessage(formattedSender);
        receiver.sendMessage(formattedReceiver);

        // Update last messenger for reply tracking
        lastMessenger.put(receiver.getName().toLowerCase(), sender.getName());

        // Log private messages for staff (if they have permission to see social spy)
        for (Player online : plugin.getServer().getOnlinePlayers().values()) {
            if (online.hasPermission("chat.socialspy") &&
                    !online.getName().equalsIgnoreCase(sender.getName()) &&
                    !online.getName().equalsIgnoreCase(receiver.getName())) {
                online.sendMessage(TextFormat.GRAY + "[SocialSpy] " + sender.getName() + " → " + receiver.getName() + ": " + message);
            }
        }
    }

    /**
     * Reply to the last private message received.
     *
     * @param sender  The player sending the reply
     * @param message The reply content
     */
    public void reply(Player sender, String message) {
        String lastSender = lastMessenger.get(sender.getName().toLowerCase());

        if (lastSender == null) {
            sender.sendMessage(TextFormat.RED + "You have no one to reply to!");
            return;
        }

        Player target = plugin.getServer().getPlayer(lastSender);
        if (target == null) {
            sender.sendMessage(TextFormat.RED + "The player you were talking to is no longer online.");
            lastMessenger.remove(sender.getName().toLowerCase());
            return;
        }

        sendPrivateMessage(sender, target.getName(), message);
    }

    /**
     * Get the name of the last player who messaged the given player.
     *
     * @param playerName The player's name
     * @return The name of the last messenger, or null
     */
    public String getLastMessenger(String playerName) {
        return lastMessenger.get(playerName.toLowerCase());
    }

    /**
     * Clean up data for a player who has disconnected.
     *
     * @param playerName The player's name
     */
    public void removePlayer(String playerName) {
        lastMessenger.remove(playerName.toLowerCase());
    }
}
