package com.server.aesthetics;

import cn.nukkit.Player;
import cn.nukkit.utils.TextFormat;

import java.util.List;

/**
 * Manages the auto-broadcast system that periodically sends
 * tips, rules, and announcements to all online players.
 */
public class BroadcastManager {

    private final ServerAestheticsPlugin plugin;
    private int currentMessageIndex = 0;
    private int taskId = -1;

    public BroadcastManager(ServerAestheticsPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Starts the auto-broadcast task.
     */
    public void startBroadcastTask() {
        if (!plugin.getConfig().getBoolean("broadcast.enabled", true)) {
            return;
        }

        int intervalSeconds = plugin.getConfig().getInt("broadcast.interval", 120);
        int intervalTicks = intervalSeconds * 20; // Convert seconds to ticks

        taskId = plugin.getServer().getScheduler().scheduleRepeatingTask(plugin, () -> {
            broadcastNext();
        }, intervalTicks).getTaskId();
    }

    /**
     * Broadcasts the next message in the rotation to all online players.
     */
    public void broadcastNext() {
        List<String> messages = plugin.getConfig().getStringList("broadcast.messages");
        if (messages == null || messages.isEmpty()) {
            return;
        }

        if (currentMessageIndex >= messages.size()) {
            currentMessageIndex = 0;
        }

        String message = messages.get(currentMessageIndex);
        currentMessageIndex++;

        broadcastMessage(message);
    }

    /**
     * Broadcasts a specific message to all online players with the configured prefix.
     * @param message The message to broadcast (without prefix)
     */
    public void broadcastMessage(String message) {
        String prefix = plugin.getConfig().getString("broadcast.prefix", "§6§l[!] §r");
        String fullMessage = prefix + message;

        // Replace global placeholders (no player-specific ones)
        fullMessage = plugin.replacePlaceholders(fullMessage);
        fullMessage = TextFormat.colorize(fullMessage);

        for (Player player : plugin.getServer().getOnlinePlayers().values()) {
            player.sendMessage(fullMessage);
        }
    }

    /**
     * Broadcasts a raw message to all online players.
     * Used by the /broadcast command.
     * @param message The raw message to broadcast
     */
    public void broadcastRawMessage(String message) {
        String prefix = plugin.getConfig().getString("broadcast.prefix", "§6§l[!] §r");
        String fullMessage = prefix + message;
        fullMessage = TextFormat.colorize(fullMessage);

        for (Player player : plugin.getServer().getOnlinePlayers().values()) {
            player.sendMessage(fullMessage);
        }
    }

    /**
     * Resets the broadcast message index to the beginning.
     */
    public void resetIndex() {
        currentMessageIndex = 0;
    }

    /**
     * Stops the auto-broadcast task.
     */
    public void stop() {
        if (taskId != -1) {
            plugin.getServer().getScheduler().cancelTask(taskId);
            taskId = -1;
        }
    }
}
