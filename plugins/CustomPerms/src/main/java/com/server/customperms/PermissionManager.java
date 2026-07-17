package com.server.customperms;

import cn.nukkit.permission.PermissionAttachment;
import cn.nukkit.utils.Config;
import cn.nukkit.utils.ConfigSection;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Core permission manager for CustomPerms.
 * Manages groups, users, and permission resolution.
 * Provides the public API that other plugins can use.
 */
public class PermissionManager {

    private final CustomPermsPlugin plugin;
    private final Map<String, PermissionGroup> groups;
    private final Map<UUID, PermissionUser> users;
    private final Map<UUID, PermissionAttachment> attachments;
    private final File dataFolder;
    private Config groupsConfig;
    private Config usersConfig;
    private String defaultGroupName;

    /**
     * Creates a new PermissionManager.
     *
     * @param plugin The main plugin instance
     */
    public PermissionManager(CustomPermsPlugin plugin) {
        this.plugin = plugin;
        this.groups = new LinkedHashMap<>();
        this.users = new HashMap<>();
        this.attachments = new HashMap<>();
        this.dataFolder = plugin.getDataFolder();
    }

    /**
     * Loads all group and user data from YAML files.
     * If files don't exist, creates them from defaults.
     */
    public void loadData() {
        // Ensure data folder exists
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }

        // Load default group name from main config
        defaultGroupName = plugin.getConfig().getString("default-group", "player");

        // Load groups
        loadGroups();

        // Load users
        loadUsers();

        plugin.getLogger().info("Loaded " + groups.size() + " groups and " + users.size() + " users.");
    }

    /**
     * Loads groups from groups.yml. If the file doesn't exist,
     * creates it from the default groups in config.yml.
     */
    @SuppressWarnings("unchecked")
    private void loadGroups() {
        File groupsFile = new File(dataFolder, "groups.yml");
        if (!groupsFile.exists()) {
            // First time: load default groups from config.yml
            loadGroupsFromConfig();
            saveGroups();
            return;
        }

        groupsConfig = new Config(groupsFile, Config.YAML);
        groups.clear();

        Map<String, Object> groupMap = groupsConfig.getAll();
        for (Map.Entry<String, Object> entry : groupMap.entrySet()) {
            String groupName = entry.getKey();
            if (!(entry.getValue() instanceof Map)) {
                continue;
            }
            Map<String, Object> data = (Map<String, Object>) entry.getValue();
            PermissionGroup group = deserializeGroup(groupName, data);
            groups.put(groupName.toLowerCase(), group);
        }

        // If no groups loaded, load defaults
        if (groups.isEmpty()) {
            loadGroupsFromConfig();
            saveGroups();
        }
    }

    /**
     * Loads default groups from the plugin's config.yml.
     */
    @SuppressWarnings("unchecked")
    private void loadGroupsFromConfig() {
        cn.nukkit.utils.ConfigSection section = plugin.getConfig().getSection("groups");
        if (section == null) {
            plugin.getLogger().warning("No groups section found in config.yml!");
            return;
        }
        Map<String, Object> configGroups = section.getAllMap();
        for (Map.Entry<String, Object> entry : configGroups.entrySet()) {
            String groupName = entry.getKey();
            if (!(entry.getValue() instanceof Map)) {
                continue;
            }
            Map<String, Object> data = (Map<String, Object>) entry.getValue();
            PermissionGroup group = deserializeGroup(groupName, data);
            groups.put(groupName.toLowerCase(), group);
        }
    }

    /**
     * Deserializes a PermissionGroup from a map.
     */
    @SuppressWarnings("unchecked")
    private PermissionGroup deserializeGroup(String name, Map<String, Object> data) {
        PermissionGroup group = new PermissionGroup(name);

        group.setPrefix(data.containsKey("prefix") ? String.valueOf(data.get("prefix")) : "");
        group.setSuffix(data.containsKey("suffix") ? String.valueOf(data.get("suffix")) : "");
        group.setPriority(data.containsKey("priority") ? toInt(data.get("priority")) : 0);
        group.setDefault(data.containsKey("is-default") && Boolean.TRUE.equals(data.get("is-default")));

        if (data.containsKey("permissions") && data.get("permissions") instanceof List) {
            List<String> permsList = (List<String>) data.get("permissions");
            group.setPermissions(new LinkedHashSet<>(permsList));
        }

        if (data.containsKey("parents") && data.get("parents") instanceof List) {
            List<String> parentsList = (List<String>) data.get("parents");
            group.setParentGroups(new LinkedHashSet<>(parentsList));
        }

        return group;
    }

    /**
     * Loads users from users.yml.
     */
    @SuppressWarnings("unchecked")
    private void loadUsers() {
        File usersFile = new File(dataFolder, "users.yml");
        if (!usersFile.exists()) {
            usersConfig = new Config(usersFile, Config.YAML);
            return;
        }

        usersConfig = new Config(usersFile, Config.YAML);
        users.clear();

        Map<String, Object> userMap = usersConfig.getAll();
        for (Map.Entry<String, Object> entry : userMap.entrySet()) {
            String uuidStr = entry.getKey();
            if (!(entry.getValue() instanceof Map)) {
                continue;
            }
            try {
                UUID uuid = UUID.fromString(uuidStr);
                Map<String, Object> data = (Map<String, Object>) entry.getValue();
                PermissionUser user = deserializeUser(uuid, data);
                users.put(uuid, user);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid UUID in users.yml: " + uuidStr);
            }
        }
    }

    /**
     * Deserializes a PermissionUser from a map.
     */
    @SuppressWarnings("unchecked")
    private PermissionUser deserializeUser(UUID uuid, Map<String, Object> data) {
        String name = data.containsKey("name") ? String.valueOf(data.get("name")) : "";
        PermissionUser user = new PermissionUser(uuid, name);

        user.setPrimaryGroup(data.containsKey("primary-group") ? String.valueOf(data.get("primary-group")) : null);

        if (data.containsKey("permissions") && data.get("permissions") instanceof List) {
            List<String> permsList = (List<String>) data.get("permissions");
            user.setPermissions(new LinkedHashSet<>(permsList));
        }

        if (data.containsKey("group-overrides") && data.get("group-overrides") instanceof Map) {
            Map<String, Object> overridesMap = (Map<String, Object>) data.get("group-overrides");
            Map<String, Long> groupOverrides = new HashMap<>();
            for (Map.Entry<String, Object> ovEntry : overridesMap.entrySet()) {
                String groupName = ovEntry.getKey();
                long expiry = toLong(ovEntry.getValue());
                groupOverrides.put(groupName, expiry);
            }
            user.setGroupOverrides(groupOverrides);
        }

        return user;
    }

    /**
     * Saves all group data to groups.yml.
     */
    public void saveGroups() {
        File groupsFile = new File(dataFolder, "groups.yml");
        groupsConfig = new Config(groupsFile, Config.YAML);

        for (PermissionGroup group : groups.values()) {
            Map<String, Object> data = serializeGroup(group);
            groupsConfig.set(group.getName().toLowerCase(), data);
        }

        groupsConfig.save();
    }

    /**
     * Serializes a PermissionGroup to a map.
     */
    private Map<String, Object> serializeGroup(PermissionGroup group) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("prefix", group.getPrefix());
        data.put("suffix", group.getSuffix());
        data.put("priority", group.getPriority());
        data.put("is-default", group.isDefault());
        data.put("permissions", new ArrayList<>(group.getPermissions()));
        data.put("parents", new ArrayList<>(group.getParentGroups()));
        return data;
    }

    /**
     * Saves all user data to users.yml.
     */
    public void saveUsers() {
        File usersFile = new File(dataFolder, "users.yml");
        usersConfig = new Config(usersFile, Config.YAML);

        for (PermissionUser user : users.values()) {
            Map<String, Object> data = serializeUser(user);
            usersConfig.set(user.getUuid().toString(), data);
        }

        usersConfig.save();
    }

    /**
     * Serializes a PermissionUser to a map.
     */
    private Map<String, Object> serializeUser(PermissionUser user) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("name", user.getName());
        data.put("primary-group", user.getPrimaryGroup() != null ? user.getPrimaryGroup() : "");
        data.put("permissions", new ArrayList<>(user.getPermissions()));

        Map<String, Object> overrides = new LinkedHashMap<>();
        for (Map.Entry<String, Long> entry : user.getGroupOverrides().entrySet()) {
            overrides.put(entry.getKey(), entry.getValue());
        }
        data.put("group-overrides", overrides);

        return data;
    }

    /**
     * Saves all data (groups and users).
     */
    public void saveAll() {
        saveGroups();
        saveUsers();
        plugin.getLogger().info("All permission data saved.");
    }

    // ==================== Group Management ====================

    /**
     * Gets a group by name.
     *
     * @param name The group name (case-insensitive)
     * @return The PermissionGroup, or null if not found
     */
    public PermissionGroup getGroup(String name) {
        if (name == null) {
            return null;
        }
        return groups.get(name.toLowerCase());
    }

    /**
     * Gets all groups.
     *
     * @return Unmodifiable map of all groups
     */
    public Map<String, PermissionGroup> getGroups() {
        return new LinkedHashMap<>(groups);
    }

    /**
     * Gets the default group.
     *
     * @return The default PermissionGroup, or null if none set
     */
    public PermissionGroup getDefaultGroup() {
        // First try by name
        if (defaultGroupName != null) {
            PermissionGroup group = groups.get(defaultGroupName.toLowerCase());
            if (group != null) {
                return group;
            }
        }
        // Fall back to finding any group marked as default
        for (PermissionGroup group : groups.values()) {
            if (group.isDefault()) {
                return group;
            }
        }
        // Last resort: return the first group
        if (!groups.isEmpty()) {
            return groups.values().iterator().next();
        }
        return null;
    }

    /**
     * Creates a new group.
     *
     * @param name The group name
     * @return The created PermissionGroup, or null if a group with that name already exists
     */
    public PermissionGroup createGroup(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        String key = name.toLowerCase();
        if (groups.containsKey(key)) {
            return null;
        }
        PermissionGroup group = new PermissionGroup(name);
        groups.put(key, group);
        saveGroups();
        return group;
    }

    /**
     * Deletes a group.
     *
     * @param name The group name
     * @return true if the group was deleted
     */
    public boolean deleteGroup(String name) {
        if (name == null) {
            return false;
        }
        String key = name.toLowerCase();
        PermissionGroup removed = groups.remove(key);
        if (removed != null) {
            // Remove this group from any parent references
            for (PermissionGroup group : groups.values()) {
                group.removeParent(name);
            }
            // Update users who had this as primary group
            PermissionGroup defaultGroup = getDefaultGroup();
            for (PermissionUser user : users.values()) {
                if (name.equalsIgnoreCase(user.getPrimaryGroup())) {
                    user.setPrimaryGroup(defaultGroup != null ? defaultGroup.getName().toLowerCase() : null);
                }
                user.removeGroupOverride(name);
            }
            saveGroups();
            saveUsers();
            return true;
        }
        return false;
    }

    /**
     * Adds a permission to a group.
     *
     * @param groupName  The group name
     * @param permission The permission node
     * @return true if successful
     */
    public boolean addGroupPermission(String groupName, String permission) {
        PermissionGroup group = getGroup(groupName);
        if (group == null) {
            return false;
        }
        boolean added = group.addPermission(permission);
        if (added) {
            saveGroups();
        }
        return added;
    }

    /**
     * Removes a permission from a group.
     *
     * @param groupName  The group name
     * @param permission The permission node
     * @return true if successful
     */
    public boolean removeGroupPermission(String groupName, String permission) {
        PermissionGroup group = getGroup(groupName);
        if (group == null) {
            return false;
        }
        boolean removed = group.removePermission(permission);
        if (removed) {
            saveGroups();
        }
        return removed;
    }

    // ==================== User Management ====================

    /**
     * Gets a user by UUID.
     *
     * @param uuid The player's UUID
     * @return The PermissionUser, or null if not found
     */
    public PermissionUser getUser(UUID uuid) {
        return users.get(uuid);
    }

    /**
     * Gets or creates a user by UUID and name.
     * If the user doesn't exist, a new one is created with the default group.
     *
     * @param uuid The player's UUID
     * @param name The player's name
     * @return The PermissionUser (never null)
     */
    public PermissionUser getOrCreateUser(UUID uuid, String name) {
        PermissionUser user = users.get(uuid);
        if (user == null) {
            user = new PermissionUser(uuid, name);
            PermissionGroup defaultGroup = getDefaultGroup();
            if (defaultGroup != null) {
                user.setPrimaryGroup(defaultGroup.getName().toLowerCase());
            }
            users.put(uuid, user);
            saveUsers();
        } else {
            // Update name if changed
            if (!user.getName().equals(name)) {
                user.setName(name);
            }
        }
        return user;
    }

    /**
     * Gets all users.
     *
     * @return Map of all users by UUID
     */
    public Map<UUID, PermissionUser> getUsers() {
        return new HashMap<>(users);
    }

    /**
     * Gets all permissions for a user, resolving groups and inheritance.
     *
     * @param uuid The player's UUID
     * @return Set of all effective permissions, or empty set if user not found
     */
    public Set<String> getUserPermissions(UUID uuid) {
        PermissionUser user = users.get(uuid);
        if (user == null) {
            return new LinkedHashSet<>();
        }
        // Clean expired groups first
        user.cleanExpiredGroups();
        return user.getAllPermissions(this);
    }

    /**
     * Checks if a user has a specific permission.
     * This method supports:
     * - Wildcard permissions: "customperms.*" matches "customperms.admin"
     * - Negative permissions: "-customperms.admin" denies "customperms.admin"
     * - Negative permissions take priority over positive ones
     *
     * @param uuid       The player's UUID
     * @param permission The permission node to check
     * @return true if the user has the permission
     */
    public boolean hasPermission(UUID uuid, String permission) {
        PermissionUser user = users.get(uuid);
        if (user == null) {
            // If no user data, check default group
            PermissionGroup defaultGroup = getDefaultGroup();
            if (defaultGroup != null) {
                return checkPermission(defaultGroup.getAllPermissions(this), permission);
            }
            return false;
        }

        // Clean expired groups first
        user.cleanExpiredGroups();

        Set<String> allPerms = user.getAllPermissions(this);
        return checkPermission(allPerms, permission);
    }

    /**
     * Core permission checking logic.
     * Supports wildcards and negation.
     * <p>
     * Algorithm:
     * 1. Collect all negative permissions (those starting with "-")
     * 2. Check if the specific permission is negated
     * 3. Check for direct permission match
     * 4. Check for wildcard matches
     * 5. Negative wildcards (e.g., "-server.admin.*") deny all matching permissions
     *
     * @param allPerms   All permissions the user has (including from groups)
     * @param permission The permission to check
     * @return true if the permission is granted
     */
    private boolean checkPermission(Set<String> allPerms, String permission) {
        if (permission == null || permission.isEmpty()) {
            return true;
        }

        // Separate negative and positive permissions
        Set<String> negations = new LinkedHashSet<>();
        Set<String> positives = new LinkedHashSet<>();

        for (String perm : allPerms) {
            if (perm.startsWith("-")) {
                negations.add(perm.substring(1));
            } else {
                positives.add(perm);
            }
        }

        // Check specific negation (highest priority)
        if (negations.contains(permission)) {
            return false;
        }

        // Check wildcard negation
        for (String negation : negations) {
            if (negation.endsWith(".*")) {
                String prefix = negation.substring(0, negation.length() - 1);
                if (permission.startsWith(prefix)) {
                    return false;
                }
            }
            // Also check if the negation is a parent node of the permission
            if (permission.startsWith(negation + ".")) {
                return false;
            }
        }

        // Check direct positive match
        if (positives.contains(permission)) {
            return true;
        }

        // Check wildcard matches
        for (String positive : positives) {
            if (positive.endsWith(".*")) {
                String prefix = positive.substring(0, positive.length() - 1);
                if (permission.startsWith(prefix)) {
                    return true;
                }
            }
            // Also check if the positive is a parent node of the permission
            if (permission.startsWith(positive + ".")) {
                return true;
            }
        }

        // Check for the special "*" wildcard (matches everything)
        if (positives.contains("*")) {
            return true;
        }

        return false;
    }

    /**
     * Adds a permission to a user.
     *
     * @param uuid       The player's UUID
     * @param permission The permission node to add
     * @return true if successful
     */
    public boolean addPermission(UUID uuid, String permission) {
        PermissionUser user = users.get(uuid);
        if (user == null) {
            return false;
        }
        boolean added = user.addPermission(permission);
        if (added) {
            saveUsers();
        }
        return added;
    }

    /**
     * Removes a permission from a user.
     *
     * @param uuid       The player's UUID
     * @param permission The permission node to remove
     * @return true if successful
     */
    public boolean removePermission(UUID uuid, String permission) {
        PermissionUser user = users.get(uuid);
        if (user == null) {
            return false;
        }
        boolean removed = user.removePermission(permission);
        if (removed) {
            saveUsers();
        }
        return removed;
    }

    /**
     * Sets a user's primary group.
     *
     * @param uuid      The player's UUID
     * @param groupName The group name
     * @return true if successful
     */
    public boolean setUserPrimaryGroup(UUID uuid, String groupName) {
        PermissionUser user = users.get(uuid);
        if (user == null) {
            return false;
        }
        if (groupName != null && getGroup(groupName) == null) {
            return false;
        }
        user.setPrimaryGroup(groupName != null ? groupName.toLowerCase() : null);
        saveUsers();
        return true;
    }

    /**
     * Adds a group override to a user with optional duration.
     *
     * @param uuid            The player's UUID
     * @param groupName       The group name
     * @param durationSeconds Duration in seconds, or 0 for permanent
     * @return true if successful
     */
    public boolean addUserGroupOverride(UUID uuid, String groupName, long durationSeconds) {
        PermissionUser user = users.get(uuid);
        if (user == null) {
            return false;
        }
        if (getGroup(groupName) == null) {
            return false;
        }
        user.addGroupOverride(groupName.toLowerCase(), durationSeconds);
        saveUsers();
        return true;
    }

    /**
     * Removes a group override from a user.
     *
     * @param uuid      The player's UUID
     * @param groupName The group name
     * @return true if successful
     */
    public boolean removeUserGroupOverride(UUID uuid, String groupName) {
        PermissionUser user = users.get(uuid);
        if (user == null) {
            return false;
        }
        boolean removed = user.removeGroupOverride(groupName.toLowerCase());
        if (removed) {
            saveUsers();
        }
        return removed;
    }

    // ==================== Utility Methods ====================

    /**
     * Applies all permissions from groups/user to a Nukkit player.
     * Manages a single PermissionAttachment per player for clean permission handling.
     *
     * @param uuid The player's UUID
     */
    public void applyPermissionsToPlayer(UUID uuid) {
        cn.nukkit.Player player = plugin.getServer().getOnlinePlayers().get(uuid);
        if (player == null) {
            return;
        }

        // Remove existing attachment if present
        PermissionAttachment existingAttachment = attachments.get(uuid);
        if (existingAttachment != null) {
            player.removeAttachment(existingAttachment);
            attachments.remove(uuid);
        }

        // Create a new attachment
        PermissionAttachment attachment = player.addAttachment(plugin);
        attachments.put(uuid, attachment);

        // Get all effective permissions and apply them
        Set<String> effectivePerms = getUserPermissions(uuid);
        for (String perm : effectivePerms) {
            if (perm.startsWith("-")) {
                // Negative permission: explicitly deny
                String negatedPerm = perm.substring(1);
                attachment.setPermission(negatedPerm, false);
            } else {
                // Positive permission: grant
                attachment.setPermission(perm, true);
            }
        }

        // Rebuild the player's effective permissions
        player.recalculatePermissions();
    }

    /**
     * Removes all permissions set by this plugin from a player.
     *
     * @param uuid The player's UUID
     */
    public void clearPermissionsFromPlayer(UUID uuid) {
        cn.nukkit.Player player = plugin.getServer().getOnlinePlayers().get(uuid);
        if (player == null) {
            attachments.remove(uuid);
            return;
        }

        // Remove the tracked attachment
        PermissionAttachment attachment = attachments.remove(uuid);
        if (attachment != null) {
            try {
                player.removeAttachment(attachment);
            } catch (Exception ignored) {
                // Attachment might already be removed
            }
        }

        player.recalculatePermissions();
    }

    /**
     * Reloads all data from disk.
     */
    public void reload() {
        groups.clear();
        users.clear();
        plugin.reloadConfig();
        loadData();
        plugin.getLogger().info("Permission data reloaded.");

        // Re-apply permissions for all online players
        for (cn.nukkit.Player player : plugin.getServer().getOnlinePlayers().values()) {
            applyPermissionsToPlayer(player.getUniqueId());
        }
    }

    /**
     * Parses a duration string like "30d", "1h", "15m" into seconds.
     * Supports: s (seconds), m (minutes), h (hours), d (days), w (weeks)
     * Can combine: "1h30m" = 5400 seconds
     *
     * @param duration The duration string
     * @return Duration in seconds, or 0 if invalid
     */
    public static long parseDuration(String duration) {
        if (duration == null || duration.isEmpty()) {
            return 0;
        }

        long totalSeconds = 0;
        StringBuilder numberBuffer = new StringBuilder();

        for (char c : duration.toCharArray()) {
            if (Character.isDigit(c)) {
                numberBuffer.append(c);
            } else {
                if (numberBuffer.length() > 0) {
                    long value = Long.parseLong(numberBuffer.toString());
                    numberBuffer = new StringBuilder();
                    switch (Character.toLowerCase(c)) {
                        case 's':
                            totalSeconds += value;
                            break;
                        case 'm':
                            totalSeconds += value * 60;
                            break;
                        case 'h':
                            totalSeconds += value * 3600;
                            break;
                        case 'd':
                            totalSeconds += value * 86400;
                            break;
                        case 'w':
                            totalSeconds += value * 604800;
                            break;
                        default:
                            return 0; // Invalid unit
                    }
                }
            }
        }

        return totalSeconds;
    }

    /**
     * Formats a duration in seconds to a human-readable string.
     *
     * @param seconds The duration in seconds
     * @return Formatted string like "30d 5h 10m"
     */
    public static String formatDuration(long seconds) {
        if (seconds <= 0) {
            return "permanent";
        }

        StringBuilder sb = new StringBuilder();
        long weeks = seconds / 604800;
        seconds %= 604800;
        long days = seconds / 86400;
        seconds %= 86400;
        long hours = seconds / 3600;
        seconds %= 3600;
        long minutes = seconds / 60;
        seconds %= 60;

        if (weeks > 0) {
            sb.append(weeks).append("w ");
        }
        if (days > 0) {
            sb.append(days).append("d ");
        }
        if (hours > 0) {
            sb.append(hours).append("h ");
        }
        if (minutes > 0) {
            sb.append(minutes).append("m ");
        }
        if (seconds > 0 || sb.length() == 0) {
            sb.append(seconds).append("s");
        }

        return sb.toString().trim();
    }

    /**
     * Converts an object to an integer safely.
     */
    private int toInt(Object obj) {
        if (obj instanceof Number) {
            return ((Number) obj).intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(obj));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Converts an object to a long safely.
     */
    private long toLong(Object obj) {
        if (obj instanceof Number) {
            return ((Number) obj).longValue();
        }
        try {
            return Long.parseLong(String.valueOf(obj));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
