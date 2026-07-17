package com.server.donationsystem;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.permission.PermissionAttachment;
import cn.nukkit.utils.Config;
import cn.nukkit.utils.ConfigSection;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Core manager for the donation system.
 * Handles ranks, codes, player donations, and permissions.
 */
public class DonationManager {

    private final DonationSystemPlugin plugin;
    private final Map<String, DonationRank> ranks = new LinkedHashMap<>();
    private final Map<String, DonationCode> codes = new LinkedHashMap<>();
    private final Map<UUID, PermissionAttachment> permissionAttachments = new ConcurrentHashMap<>();

    // Player donation data: playerName -> {rankName, expiryTimestamp}
    private final Map<String, PlayerDonationData> playerDonations = new ConcurrentHashMap<>();

    // Death locations for /back command: player UUID -> location string
    private final Map<UUID, String> deathLocations = new ConcurrentHashMap<>();

    private Config playerDataConfig;
    private Config codesConfig;
    private int codeLength;
    private String codeCharacters;

    public DonationManager(DonationSystemPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Initialize the manager: load ranks from config, load player data and codes.
     */
    public void init() {
        loadRanks();
        loadPlayerData();
        loadCodes();
        loadCodeSettings();
    }

    /**
     * Load rank definitions from the plugin config.
     */
    @SuppressWarnings("unchecked")
    private void loadRanks() {
        ranks.clear();
        Config config = plugin.getConfig();

        if (config.exists("ranks")) {
            Map<String, Object> ranksSection = config.getSection("ranks").getAllMap();
            for (Map.Entry<String, Object> entry : ranksSection.entrySet()) {
                String rankName = entry.getKey().toLowerCase();
                Map<String, Object> rankData = (Map<String, Object>) entry.getValue();
                DonationRank rank = DonationRank.fromConfig(rankName, rankData);
                ranks.put(rankName, rank);
                plugin.getLogger().info("Loaded rank: " + rank.getDisplayName() + " §r(priority: " + rank.getPriority() + ")");
            }
        }

        if (ranks.isEmpty()) {
            plugin.getLogger().warning("No donation ranks configured!");
        }
    }

    /**
     * Load code generation settings from config.
     */
    private void loadCodeSettings() {
        Config config = plugin.getConfig();
        if (config.exists("codes.length")) {
            codeLength = config.getInt("codes.length", 8);
        } else {
            codeLength = 8;
        }
        if (config.exists("codes.characters")) {
            codeCharacters = config.getString("codes.characters", "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789");
        } else {
            codeCharacters = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        }
    }

    /**
     * Load player donation data from playerdata.yml.
     */
    @SuppressWarnings("unchecked")
    private void loadPlayerData() {
        playerDonations.clear();
        File dataFile = new File(plugin.getDataFolder(), "playerdata.yml");
        playerDataConfig = new Config(dataFile, Config.YAML);

        Map<String, Object> allData = playerDataConfig.getAll();
        for (Map.Entry<String, Object> entry : allData.entrySet()) {
            String playerName = entry.getKey();
            if (entry.getValue() instanceof Map) {
                Map<String, Object> data = (Map<String, Object>) entry.getValue();
                String rankName = (String) data.getOrDefault("rank", "");
                long expiryTime = 0;
                if (data.containsKey("expiry")) {
                    expiryTime = ((Number) data.get("expiry")).longValue();
                }
                if (!rankName.isEmpty()) {
                    playerDonations.put(playerName.toLowerCase(), new PlayerDonationData(playerName, rankName, expiryTime));
                }
            }
        }
        plugin.getLogger().info("Loaded donation data for " + playerDonations.size() + " players.");
    }

    /**
     * Save player donation data to playerdata.yml.
     */
    public void savePlayerData() {
        if (playerDataConfig == null) return;
        // Clear config by removing all existing keys
        for (String key : playerDataConfig.getAll().keySet()) {
            playerDataConfig.remove(key);
        }

        for (Map.Entry<String, PlayerDonationData> entry : playerDonations.entrySet()) {
            PlayerDonationData data = entry.getValue();
            ConfigSection section = new ConfigSection();
            section.put("rank", data.getRankName());
            section.put("expiry", data.getExpiryTime());
            section.put("playerName", data.getPlayerName());
            playerDataConfig.set(entry.getKey(), section);
        }
        playerDataConfig.save();
    }

    /**
     * Load donation codes from codes.yml.
     */
    @SuppressWarnings("unchecked")
    private void loadCodes() {
        codes.clear();
        File codesFile = new File(plugin.getDataFolder(), "codes.yml");
        codesConfig = new Config(codesFile, Config.YAML);

        Map<String, Object> allCodes = codesConfig.getAll();
        for (Map.Entry<String, Object> entry : allCodes.entrySet()) {
            String codeStr = entry.getKey();
            if (entry.getValue() instanceof Map) {
                Map<String, Object> data = (Map<String, Object>) entry.getValue();
                String rankName = (String) data.getOrDefault("rank", "");
                long duration = 0;
                if (data.containsKey("duration")) {
                    duration = ((Number) data.get("duration")).longValue();
                }
                DonationCode code = new DonationCode(codeStr, rankName, duration);
                if (data.containsKey("used")) {
                    code.setUsed((Boolean) data.get("used"));
                }
                if (data.containsKey("usedBy")) {
                    code.setUsedBy((String) data.get("usedBy"));
                }
                if (data.containsKey("usedAt")) {
                    code.setUsedAt(((Number) data.get("usedAt")).longValue());
                }
                codes.put(codeStr, code);
            }
        }
        plugin.getLogger().info("Loaded " + codes.size() + " donation codes.");
    }

    /**
     * Save donation codes to codes.yml.
     */
    public void saveCodes() {
        if (codesConfig == null) return;
        // Clear config by removing all existing keys
        for (String key : codesConfig.getAll().keySet()) {
            codesConfig.remove(key);
        }

        for (Map.Entry<String, DonationCode> entry : codes.entrySet()) {
            DonationCode code = entry.getValue();
            ConfigSection section = new ConfigSection();
            section.put("rank", code.getRankName());
            section.put("duration", code.getDuration());
            section.put("used", code.isUsed());
            section.put("usedBy", code.getUsedBy() != null ? code.getUsedBy() : "");
            section.put("usedAt", code.getUsedAt());
            codesConfig.set(entry.getKey(), section);
        }
        codesConfig.save();
    }

    // ==================== Rank Management ====================

    /**
     * Get a rank by name (case-insensitive).
     */
    public DonationRank getRank(String name) {
        return ranks.get(name.toLowerCase());
    }

    /**
     * Get all configured ranks.
     */
    public Collection<DonationRank> getAllRanks() {
        return ranks.values();
    }

    /**
     * Get all rank names.
     */
    public Set<String> getRankNames() {
        return ranks.keySet();
    }

    // ==================== Player Donation Management ====================

    /**
     * Give a donation rank to a player.
     * @param playerName The player name
     * @param rankName The rank to give
     * @param duration Duration in seconds, or 0 for permanent (uses rank default if -1)
     * @return true if successful
     */
    public boolean giveRank(String playerName, String rankName, long duration) {
        DonationRank rank = getRank(rankName);
        if (rank == null) return false;

        // Determine duration
        long effectiveDuration = duration;
        if (effectiveDuration == -1) {
            effectiveDuration = rank.getDuration();
        }

        // Calculate expiry time
        long expiryTime = 0; // 0 = permanent
        if (effectiveDuration > 0) {
            expiryTime = (System.currentTimeMillis() / 1000L) + effectiveDuration;
        }

        // If player already has a rank, remove the old one first
        PlayerDonationData existing = playerDonations.get(playerName.toLowerCase());
        if (existing != null) {
            removeRankFromPlayer(playerName, false);
        }

        // Store donation data
        PlayerDonationData donationData = new PlayerDonationData(playerName, rankName.toLowerCase(), expiryTime);
        playerDonations.put(playerName.toLowerCase(), donationData);
        savePlayerData();

        // Apply rank to online player
        Player player = Server.getInstance().getPlayer(playerName);
        if (player != null) {
            applyRankToPlayer(player, rank);
        }

        // Add to CustomPerms group
        addCustomPermsGroup(playerName, rankName.toLowerCase());

        return true;
    }

    /**
     * Remove a donor rank from a player.
     * @param playerName The player name
     * @param save Whether to save data after removal
     * @return true if the player had a rank to remove
     */
    public boolean removeRankFromPlayer(String playerName, boolean save) {
        PlayerDonationData data = playerDonations.remove(playerName.toLowerCase());
        if (data == null) return false;

        DonationRank rank = getRank(data.getRankName());

        // Remove permissions from online player
        Player player = Server.getInstance().getPlayer(playerName);
        if (player != null) {
            removePermissionsFromPlayer(player);
            removePrefix(player);
            disablePerks(player, rank);
        }

        // Remove from CustomPerms group
        removeCustomPermsGroup(playerName, data.getRankName());

        if (save) {
            savePlayerData();
        }

        return true;
    }

    /**
     * Apply rank benefits to an online player.
     */
    public void applyRankToPlayer(Player player, DonationRank rank) {
        // Grant permissions via PermissionAttachment
        grantPermissions(player, rank);
        // Set chat prefix
        setPrefix(player, rank);
        // Apply flight if applicable
        if (rank.hasFly()) {
            player.setAllowFlight(true);
        }
    }

    /**
     * Grant perk permissions to a player via PermissionAttachment.
     */
    private void grantPermissions(Player player, DonationRank rank) {
        // Remove existing attachment if any
        removePermissionsFromPlayer(player);

        PermissionAttachment attachment = player.addAttachment(plugin);
        for (String perm : rank.getPermissions()) {
            // Handle wildcard permissions
            if (perm.endsWith(".*")) {
                // For wildcards, set the permission node
                attachment.setPermission(perm, true);
            } else {
                attachment.setPermission(perm, true);
            }
        }
        permissionAttachments.put(player.getUniqueId(), attachment);
        player.recalculatePermissions();
    }

    /**
     * Remove all donation permissions from a player.
     */
    private void removePermissionsFromPlayer(Player player) {
        PermissionAttachment attachment = permissionAttachments.remove(player.getUniqueId());
        if (attachment != null) {
            player.removeAttachment(attachment);
        }
        player.recalculatePermissions();
    }

    /**
     * Set the player's display name prefix.
     */
    private void setPrefix(Player player, DonationRank rank) {
        String currentName = player.getName();
        player.setDisplayName(rank.getPrefix() + currentName);
        player.setNameTag(rank.getPrefix() + currentName);
    }

    /**
     * Remove the donation prefix from a player's display name.
     */
    private void removePrefix(Player player) {
        String displayName = player.getDisplayName();
        String name = player.getName();
        // Remove any known rank prefix
        for (DonationRank rank : ranks.values()) {
            if (displayName.startsWith(rank.getPrefix())) {
                displayName = displayName.substring(rank.getPrefix().length());
                break;
            }
        }
        player.setDisplayName(displayName);
        player.setNameTag(displayName);
    }

    /**
     * Disable active perks when a rank is removed.
     */
    private void disablePerks(Player player, DonationRank rank) {
        if (rank != null && rank.hasFly()) {
            player.setAllowFlight(false);
        }
    }

    // ==================== CustomPerms Integration ====================

    /**
     * Add a player to a CustomPerms group.
     */
    private void addCustomPermsGroup(String playerName, String groupName) {
        try {
            Class<?> customPermsClass = Class.forName("com.customperms.CustomPermsPlugin");
            java.lang.reflect.Method getApiMethod = customPermsClass.getMethod("getAPI");
            Object api = getApiMethod.invoke(null);
            java.lang.reflect.Method addGroupMethod = api.getClass().getMethod("addGroup", String.class, String.class);
            addGroupMethod.invoke(api, playerName, groupName);
            plugin.getLogger().info("Added " + playerName + " to CustomPerms group: " + groupName);
        } catch (ClassNotFoundException e) {
            plugin.getLogger().warning("CustomPerms plugin not found. Skipping group assignment.");
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to add player to CustomPerms group: " + e.getMessage());
        }
    }

    /**
     * Remove a player from a CustomPerms group.
     */
    private void removeCustomPermsGroup(String playerName, String groupName) {
        try {
            Class<?> customPermsClass = Class.forName("com.customperms.CustomPermsPlugin");
            java.lang.reflect.Method getApiMethod = customPermsClass.getMethod("getAPI");
            Object api = getApiMethod.invoke(null);
            java.lang.reflect.Method removeGroupMethod = api.getClass().getMethod("removeGroup", String.class, String.class);
            removeGroupMethod.invoke(api, playerName, groupName);
            plugin.getLogger().info("Removed " + playerName + " from CustomPerms group: " + groupName);
        } catch (ClassNotFoundException e) {
            plugin.getLogger().warning("CustomPerms plugin not found. Skipping group removal.");
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to remove player from CustomPerms group: " + e.getMessage());
        }
    }

    // ==================== Donation Codes ====================

    /**
     * Generate donation codes for a specific rank.
     * @param rankName The rank the codes are for
     * @param amount Number of codes to generate
     * @param duration Duration in seconds (0 = use rank default)
     * @return List of generated codes
     */
    public List<String> generateCodes(String rankName, int amount, long duration) {
        DonationRank rank = getRank(rankName);
        if (rank == null) return Collections.emptyList();

        long effectiveDuration = duration;
        if (effectiveDuration == 0) {
            effectiveDuration = rank.getDuration();
        }

        List<String> generatedCodes = new ArrayList<>();
        Random random = new Random();

        for (int i = 0; i < amount; i++) {
            String codeStr;
            // Ensure unique codes
            do {
                StringBuilder sb = new StringBuilder();
                for (int j = 0; j < codeLength; j++) {
                    sb.append(codeCharacters.charAt(random.nextInt(codeCharacters.length())));
                }
                codeStr = sb.toString();
            } while (codes.containsKey(codeStr));

            DonationCode code = new DonationCode(codeStr, rankName.toLowerCase(), effectiveDuration);
            codes.put(codeStr, code);
            generatedCodes.add(codeStr);
        }

        saveCodes();
        return generatedCodes;
    }

    /**
     * Redeem a donation code for a player.
     * @param codeStr The code string
     * @param playerName The player redeeming the code
     * @return Result of the redemption
     */
    public RedeemResult redeemCode(String codeStr, String playerName) {
        DonationCode code = codes.get(codeStr.toUpperCase());
        if (code == null) {
            return RedeemResult.NOT_FOUND;
        }
        if (code.isUsed()) {
            return RedeemResult.ALREADY_USED;
        }
        DonationRank rank = getRank(code.getRankName());
        if (rank == null) {
            return RedeemResult.INVALID_RANK;
        }

        // Mark code as used
        code.redeem(playerName);
        saveCodes();

        // Give the rank to the player
        // Use -1 to indicate we should use the code's stored duration
        giveRank(playerName, code.getRankName(), code.getDuration());

        return RedeemResult.SUCCESS;
    }

    /**
     * Get a donation code by its string.
     */
    public DonationCode getCode(String codeStr) {
        return codes.get(codeStr.toUpperCase());
    }

    /**
     * Get all unused codes.
     */
    public List<DonationCode> getAvailableCodes() {
        List<DonationCode> available = new ArrayList<>();
        for (DonationCode code : codes.values()) {
            if (!code.isUsed()) {
                available.add(code);
            }
        }
        return available;
    }

    // ==================== Player Data Queries ====================

    /**
     * Get the donation data for a player.
     */
    public PlayerDonationData getPlayerDonation(String playerName) {
        return playerDonations.get(playerName.toLowerCase());
    }

    /**
     * Get the rank of a player, or null if they don't have one.
     */
    public DonationRank getPlayerRank(String playerName) {
        PlayerDonationData data = playerDonations.get(playerName.toLowerCase());
        if (data == null) return null;
        return getRank(data.getRankName());
    }

    /**
     * Check if a player has a donation rank.
     */
    public boolean hasDonationRank(String playerName) {
        return playerDonations.containsKey(playerName.toLowerCase());
    }

    /**
     * Check if a player's rank has expired.
     */
    public boolean isRankExpired(String playerName) {
        PlayerDonationData data = playerDonations.get(playerName.toLowerCase());
        if (data == null) return false;
        // Expiry of 0 means permanent
        if (data.getExpiryTime() == 0) return false;
        return (System.currentTimeMillis() / 1000L) > data.getExpiryTime();
    }

    /**
     * Get the remaining time for a player's rank in seconds.
     * Returns -1 if permanent, 0 if expired.
     */
    public long getRemainingTime(String playerName) {
        PlayerDonationData data = playerDonations.get(playerName.toLowerCase());
        if (data == null) return 0;
        if (data.getExpiryTime() == 0) return -1; // permanent
        long remaining = data.getExpiryTime() - (System.currentTimeMillis() / 1000L);
        return Math.max(0, remaining);
    }

    /**
     * Get all players with donation ranks.
     */
    public Map<String, PlayerDonationData> getAllDonors() {
        return new HashMap<>(playerDonations);
    }

    // ==================== Expiry Management ====================

    /**
     * Check all players for expired ranks and remove them.
     * @return Number of expired ranks removed
     */
    public int checkAndRemoveExpiredRanks() {
        int removed = 0;
        long currentTime = System.currentTimeMillis() / 1000L;

        Iterator<Map.Entry<String, PlayerDonationData>> iterator = playerDonations.entrySet().iterator();
        List<String> toRemove = new ArrayList<>();

        while (iterator.hasNext()) {
            Map.Entry<String, PlayerDonationData> entry = iterator.next();
            PlayerDonationData data = entry.getValue();
            // Skip permanent ranks
            if (data.getExpiryTime() == 0) continue;
            // Check if expired
            if (currentTime > data.getExpiryTime()) {
                toRemove.add(entry.getKey());
            }
        }

        for (String playerNameKey : toRemove) {
            PlayerDonationData data = playerDonations.get(playerNameKey);
            if (data != null) {
                String actualPlayerName = data.getPlayerName();
                DonationRank rank = getRank(data.getRankName());

                // Notify online player
                Player player = Server.getInstance().getPlayer(actualPlayerName);
                if (player != null) {
                    String rankDisplay = rank != null ? rank.getDisplayName() : data.getRankName();
                    player.sendMessage("§cYour donation rank " + rankDisplay + " §chas expired!");
                }

                // Remove the rank
                removeRankFromPlayer(actualPlayerName, false);
                removed++;

                plugin.getLogger().info("Expired rank removed for player: " + actualPlayerName);
            }
        }

        if (removed > 0) {
            savePlayerData();
        }

        return removed;
    }

    // ==================== Death Location (for /back) ====================

    /**
     * Store a player's death location.
     */
    public void setDeathLocation(UUID playerUuid, String locationString) {
        deathLocations.put(playerUuid, locationString);
    }

    /**
     * Get a player's death location string.
     */
    public String getDeathLocation(UUID playerUuid) {
        return deathLocations.get(playerUuid);
    }

    /**
     * Remove a player's death location after using /back.
     */
    public void removeDeathLocation(UUID playerUuid) {
        deathLocations.remove(playerUuid);
    }

    // ==================== Player Online Handling ====================

    /**
     * Called when a player joins - reapply rank if they have one.
     */
    public void onPlayerJoin(Player player) {
        String playerName = player.getName();
        PlayerDonationData data = playerDonations.get(playerName.toLowerCase());

        if (data != null) {
            // Check for expiry
            if (data.getExpiryTime() != 0 && (System.currentTimeMillis() / 1000L) > data.getExpiryTime()) {
                // Rank has expired
                DonationRank rank = getRank(data.getRankName());
                String rankDisplay = rank != null ? rank.getDisplayName() : data.getRankName();
                player.sendMessage("§cYour donation rank " + rankDisplay + " §chas expired!");
                removeRankFromPlayer(playerName, true);
                return;
            }

            // Apply rank
            DonationRank rank = getRank(data.getRankName());
            if (rank != null) {
                applyRankToPlayer(player, rank);
            }
        }
    }

    /**
     * Called when a player quits - cleanup.
     */
    public void onPlayerQuit(Player player) {
        permissionAttachments.remove(player.getUniqueId());
    }

    // ==================== Utility ====================

    /**
     * Format remaining time as a human-readable string.
     */
    public static String formatTime(long seconds) {
        if (seconds < 0) return "Permanent";
        if (seconds == 0) return "Expired";

        long days = seconds / 86400;
        long hours = (seconds % 86400) / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;

        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("d ");
        if (hours > 0) sb.append(hours).append("h ");
        if (minutes > 0) sb.append(minutes).append("m ");
        if (secs > 0 && days == 0) sb.append(secs).append("s");

        return sb.toString().trim();
    }

    /**
     * Inner class representing a player's donation data.
     */
    public static class PlayerDonationData {
        private final String playerName;
        private final String rankName;
        private final long expiryTime; // unix timestamp in seconds, 0 = permanent

        public PlayerDonationData(String playerName, String rankName, long expiryTime) {
            this.playerName = playerName;
            this.rankName = rankName;
            this.expiryTime = expiryTime;
        }

        public String getPlayerName() {
            return playerName;
        }

        public String getRankName() {
            return rankName;
        }

        public long getExpiryTime() {
            return expiryTime;
        }
    }

    /**
     * Result of a code redemption attempt.
     */
    public enum RedeemResult {
        SUCCESS,
        NOT_FOUND,
        ALREADY_USED,
        INVALID_RANK
    }
}
