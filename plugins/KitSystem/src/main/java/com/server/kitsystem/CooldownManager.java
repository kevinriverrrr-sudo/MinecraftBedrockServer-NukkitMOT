package com.server.kitsystem;

import cn.nukkit.utils.Config;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manages kit cooldowns per player.
 * Cooldowns are stored in a YAML file for persistence.
 * Data structure: uuid -> kitName -> lastClaimTimestamp
 */
public class CooldownManager {

    private final Config cooldownConfig;
    private final File dataFile;

    public CooldownManager(File dataFolder) {
        this.dataFile = new File(dataFolder, "cooldowns.yml");
        this.dataFile.getParentFile().mkdirs();
        this.cooldownConfig = new Config(dataFile, Config.YAML);
    }

    /**
     * Check if a player is on cooldown for a specific kit.
     *
     * @param uuid   the player's UUID
     * @param kitName the kit name
     * @param cooldownSeconds the kit's cooldown duration in seconds
     * @return true if the player is still on cooldown
     */
    public boolean isOnCooldown(UUID uuid, String kitName, long cooldownSeconds) {
        if (cooldownSeconds <= 0) {
            // One-time kit: check if already claimed
            return hasClaimed(uuid, kitName);
        }

        long lastClaim = getLastClaimTime(uuid, kitName);
        if (lastClaim <= 0) {
            return false; // Never claimed
        }

        long currentTime = System.currentTimeMillis() / 1000;
        long elapsed = currentTime - lastClaim;
        return elapsed < cooldownSeconds;
    }

    /**
     * Get the remaining cooldown time in seconds for a player's kit.
     *
     * @param uuid   the player's UUID
     * @param kitName the kit name
     * @param cooldownSeconds the kit's cooldown duration in seconds
     * @return remaining seconds, or 0 if not on cooldown
     */
    public long getRemainingCooldown(UUID uuid, String kitName, long cooldownSeconds) {
        if (cooldownSeconds <= 0) {
            // One-time kit
            return hasClaimed(uuid, kitName) ? -1 : 0; // -1 means permanently claimed
        }

        long lastClaim = getLastClaimTime(uuid, kitName);
        if (lastClaim <= 0) {
            return 0;
        }

        long currentTime = System.currentTimeMillis() / 1000;
        long elapsed = currentTime - lastClaim;
        long remaining = cooldownSeconds - elapsed;
        return Math.max(0, remaining);
    }

    /**
     * Record a kit claim for a player.
     *
     * @param uuid   the player's UUID
     * @param kitName the kit name
     */
    public void setCooldown(UUID uuid, String kitName) {
        String uuidStr = uuid.toString();
        long currentTime = System.currentTimeMillis() / 1000;

        // Get the section for this UUID
        Map<String, Object> playerData = new HashMap<>();
        if (cooldownConfig.exists(uuidStr)) {
            Object section = cooldownConfig.getSection(uuidStr);
            if (section instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> existing = (Map<String, Object>) section;
                playerData.putAll(existing);
            }
        }

        playerData.put(kitName, currentTime);
        cooldownConfig.set(uuidStr, playerData);
        cooldownConfig.save();
    }

    /**
     * Reset the cooldown for a specific player and kit.
     *
     * @param uuid   the player's UUID
     * @param kitName the kit name
     */
    public void resetCooldown(UUID uuid, String kitName) {
        String uuidStr = uuid.toString();
        if (cooldownConfig.exists(uuidStr)) {
            String path = uuidStr + "." + kitName;
            cooldownConfig.remove(path);
            // Check if the player section is now empty
            Object section = cooldownConfig.getSection(uuidStr);
            if (section instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) section;
                if (map.isEmpty()) {
                    cooldownConfig.remove(uuidStr);
                }
            }
            cooldownConfig.save();
        }
    }

    /**
     * Check if a player has ever claimed a one-time kit.
     */
    public boolean hasClaimed(UUID uuid, String kitName) {
        String path = uuid.toString() + "." + kitName;
        return cooldownConfig.exists(path);
    }

    /**
     * Get the last claim timestamp (in epoch seconds) for a player's kit.
     */
    private long getLastClaimTime(UUID uuid, String kitName) {
        String path = uuid.toString() + "." + kitName;
        if (cooldownConfig.exists(path)) {
            Object val = cooldownConfig.get(path);
            if (val instanceof Number) {
                return ((Number) val).longValue();
            }
        }
        return 0;
    }

    /**
     * Format remaining seconds into a human-readable string.
     *
     * @param seconds the remaining seconds
     * @return formatted time string
     */
    public static String formatTime(long seconds) {
        if (seconds < 0) {
            return "Already claimed";
        }
        if (seconds == 0) {
            return "Ready";
        }

        long days = seconds / 86400;
        long hours = (seconds % 86400) / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;

        StringBuilder sb = new StringBuilder();
        if (days > 0) {
            sb.append(days).append("d ");
        }
        if (hours > 0) {
            sb.append(hours).append("h ");
        }
        if (minutes > 0) {
            sb.append(minutes).append("m ");
        }
        if (secs > 0 || sb.length() == 0) {
            sb.append(secs).append("s");
        }
        return sb.toString().trim();
    }

    /**
     * Save cooldown data to disk.
     */
    public void save() {
        cooldownConfig.save();
    }

    /**
     * Reload cooldown data from disk.
     */
    public void reload() {
        cooldownConfig.reload();
    }
}
