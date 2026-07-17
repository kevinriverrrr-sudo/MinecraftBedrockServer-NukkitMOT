package com.server.customchat;

import cn.nukkit.Player;
import cn.nukkit.utils.TextFormat;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;

/**
 * Manages anti-spam features: cooldown, duplicate message detection,
 * caps limit, and flood detection.
 */
public class AntiSpamManager {

    private final CustomChatPlugin plugin;
    private boolean enabled;
    private int cooldownSeconds;
    private int maxDuplicate;
    private int maxCapsPercent;
    private int maxMessagesPer10s;

    // Player -> last message timestamp (millis)
    private final Map<String, Long> lastMessageTime = new HashMap<>();

    // Player -> last message content
    private final Map<String, String> lastMessageContent = new HashMap<>();

    // Player -> duplicate count
    private final Map<String, Integer> duplicateCount = new HashMap<>();

    // Player -> queue of message timestamps in the last 10 seconds
    private final Map<String, Queue<Long>> messageTimestamps = new HashMap<>();

    public AntiSpamManager(CustomChatPlugin plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    /**
     * Load anti-spam settings from the plugin config.
     */
    public void loadConfig() {
        this.enabled = plugin.getConfig().getBoolean("anti-spam.enabled", true);
        this.cooldownSeconds = plugin.getConfig().getInt("anti-spam.cooldown", 2);
        this.maxDuplicate = plugin.getConfig().getInt("anti-spam.max-duplicate", 3);
        this.maxCapsPercent = plugin.getConfig().getInt("anti-spam.max-caps", 70);
        this.maxMessagesPer10s = plugin.getConfig().getInt("anti-spam.max-messages-per-10s", 8);
    }

    /**
     * Check if a player can send a message. Returns null if allowed,
     * or an error message if blocked.
     *
     * @param player  The player sending the message
     * @param message The message content
     * @return null if allowed, error message if blocked
     */
    public String checkMessage(Player player, String message) {
        if (!enabled) {
            return null;
        }

        // Bypass permission
        if (player.hasPermission("chat.bypass.spam")) {
            return null;
        }

        String playerName = player.getName();

        // 1. Check cooldown
        String cooldownError = checkCooldown(playerName);
        if (cooldownError != null) {
            return cooldownError;
        }

        // 2. Check duplicate messages
        String duplicateError = checkDuplicate(playerName, message);
        if (duplicateError != null) {
            return duplicateError;
        }

        // 3. Check caps percentage
        String capsError = checkCaps(message);
        if (capsError != null) {
            return capsError;
        }

        // 4. Check flood (max messages per time period)
        String floodError = checkFlood(playerName);
        if (floodError != null) {
            return floodError;
        }

        // All checks passed, record this message
        recordMessage(playerName, message);

        return null;
    }

    /**
     * Check if the player is on cooldown.
     *
     * @param playerName The player's name
     * @return Error message or null
     */
    private String checkCooldown(String playerName) {
        long now = System.currentTimeMillis();
        Long lastTime = lastMessageTime.get(playerName);

        if (lastTime != null) {
            long elapsed = now - lastTime;
            long cooldownMs = cooldownSeconds * 1000L;
            if (elapsed < cooldownMs) {
                long remaining = (cooldownMs - elapsed) / 1000;
                if (remaining < 1) remaining = 1;
                return TextFormat.RED + "Slow down! Wait " + remaining + " second" + (remaining > 1 ? "s" : "") + " before sending another message.";
            }
        }

        return null;
    }

    /**
     * Check if the player is sending duplicate messages.
     *
     * @param playerName The player's name
     * @param message    The current message
     * @return Error message or null
     */
    private String checkDuplicate(String playerName, String message) {
        String lastMsg = lastMessageContent.get(playerName);

        if (lastMsg != null && lastMsg.equalsIgnoreCase(message)) {
            int count = duplicateCount.getOrDefault(playerName, 0) + 1;
            if (count > maxDuplicate) {
                return TextFormat.RED + "Stop repeating yourself! You've sent the same message too many times.";
            }
            duplicateCount.put(playerName, count);
        } else {
            // Different message, reset duplicate counter
            duplicateCount.put(playerName, 1);
        }

        return null;
    }

    /**
     * Check if the message has too many capital letters.
     *
     * @param message The message to check
     * @return Error message or null
     */
    private String checkCaps(String message) {
        // Strip color codes first
        String stripped = TextFormat.clean(message);

        // Don't check very short messages
        if (stripped.length() < 4) {
            return null;
        }

        int upperCount = 0;
        int letterCount = 0;

        for (char c : stripped.toCharArray()) {
            if (Character.isLetter(c)) {
                letterCount++;
                if (Character.isUpperCase(c)) {
                    upperCount++;
                }
            }
        }

        if (letterCount == 0) {
            return null;
        }

        int capsPercent = (upperCount * 100) / letterCount;
        if (capsPercent > maxCapsPercent) {
            return TextFormat.RED + "Too many caps! Please lower your voice.";
        }

        return null;
    }

    /**
     * Check if the player is flooding chat (too many messages in a short period).
     *
     * @param playerName The player's name
     * @return Error message or null
     */
    private String checkFlood(String playerName) {
        long now = System.currentTimeMillis();
        Queue<Long> timestamps = messageTimestamps.computeIfAbsent(playerName, k -> new LinkedList<>());

        // Remove timestamps older than 10 seconds
        while (!timestamps.isEmpty() && (now - timestamps.peek()) > 10000) {
            timestamps.poll();
        }

        if (timestamps.size() >= maxMessagesPer10s) {
            return TextFormat.RED + "You're sending messages too fast! Please slow down.";
        }

        return null;
    }

    /**
     * Record that a player has sent a message.
     *
     * @param playerName The player's name
     * @param message    The message content
     */
    private void recordMessage(String playerName, String message) {
        long now = System.currentTimeMillis();

        lastMessageTime.put(playerName, now);
        lastMessageContent.put(playerName, message);

        Queue<Long> timestamps = messageTimestamps.computeIfAbsent(playerName, k -> new LinkedList<>());
        timestamps.add(now);
    }

    /**
     * Clean up data for a player who has disconnected.
     *
     * @param playerName The player's name
     */
    public void removePlayer(String playerName) {
        lastMessageTime.remove(playerName);
        lastMessageContent.remove(playerName);
        duplicateCount.remove(playerName);
        messageTimestamps.remove(playerName);
    }

    /**
     * Get the remaining cooldown time for a player in seconds.
     *
     * @param playerName The player's name
     * @return Remaining cooldown in seconds, 0 if no cooldown
     */
    public long getRemainingCooldown(String playerName) {
        Long lastTime = lastMessageTime.get(playerName);
        if (lastTime == null) {
            return 0;
        }

        long elapsed = System.currentTimeMillis() - lastTime;
        long cooldownMs = cooldownSeconds * 1000L;
        if (elapsed >= cooldownMs) {
            return 0;
        }

        return (cooldownMs - elapsed) / 1000;
    }

    // Getters
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getCooldownSeconds() {
        return cooldownSeconds;
    }

    public void setCooldownSeconds(int cooldownSeconds) {
        this.cooldownSeconds = cooldownSeconds;
    }

    public int getMaxDuplicate() {
        return maxDuplicate;
    }

    public void setMaxDuplicate(int maxDuplicate) {
        this.maxDuplicate = maxDuplicate;
    }

    public int getMaxCapsPercent() {
        return maxCapsPercent;
    }

    public void setMaxCapsPercent(int maxCapsPercent) {
        this.maxCapsPercent = maxCapsPercent;
    }

    public int getMaxMessagesPer10s() {
        return maxMessagesPer10s;
    }

    public void setMaxMessagesPer10s(int maxMessagesPer10s) {
        this.maxMessagesPer10s = maxMessagesPer10s;
    }
}
