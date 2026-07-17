package com.server.customperms;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Represents a user with permissions and group memberships.
 * Users have a primary group and can have additional group overrides
 * (including timed groups that expire after a set duration).
 */
public class PermissionUser {

    private UUID uuid;
    private String name;
    private String primaryGroup;
    private Set<String> permissions;
    private Map<String, Long> groupOverrides;

    public PermissionUser(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
        this.primaryGroup = null;
        this.permissions = new LinkedHashSet<>();
        this.groupOverrides = new HashMap<>();
    }

    public UUID getUuid() {
        return uuid;
    }

    public void setUuid(UUID uuid) {
        this.uuid = uuid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPrimaryGroup() {
        return primaryGroup;
    }

    public void setPrimaryGroup(String primaryGroup) {
        this.primaryGroup = primaryGroup;
    }

    public Set<String> getPermissions() {
        return permissions;
    }

    public void setPermissions(Set<String> permissions) {
        this.permissions = permissions != null ? permissions : new LinkedHashSet<>();
    }

    public Map<String, Long> getGroupOverrides() {
        return groupOverrides;
    }

    public void setGroupOverrides(Map<String, Long> groupOverrides) {
        this.groupOverrides = groupOverrides != null ? groupOverrides : new HashMap<>();
    }

    /**
     * Adds a permission to this user.
     *
     * @param permission The permission node to add
     * @return true if the permission was added
     */
    public boolean addPermission(String permission) {
        return permissions.add(permission);
    }

    /**
     * Removes a permission from this user.
     *
     * @param permission The permission node to remove
     * @return true if the permission was removed
     */
    public boolean removePermission(String permission) {
        return permissions.remove(permission);
    }

    /**
     * Checks if this user has a specific permission directly (not from groups).
     *
     * @param permission The permission node to check
     * @return true if the user has this permission directly
     */
    public boolean hasDirectPermission(String permission) {
        return permissions.contains(permission);
    }

    /**
     * Adds a group override. If durationSeconds > 0, the group will expire
     * after that many seconds from now.
     *
     * @param groupName       The group name
     * @param durationSeconds Duration in seconds, or 0 for permanent
     */
    public void addGroupOverride(String groupName, long durationSeconds) {
        if (durationSeconds > 0) {
            long expiryTimestamp = System.currentTimeMillis() / 1000 + durationSeconds;
            groupOverrides.put(groupName, expiryTimestamp);
        } else {
            // Use -1 to indicate permanent override
            groupOverrides.put(groupName, -1L);
        }
    }

    /**
     * Removes a group override.
     *
     * @param groupName The group name to remove
     * @return true if the group override was removed
     */
    public boolean removeGroupOverride(String groupName) {
        return groupOverrides.remove(groupName) != null;
    }

    /**
     * Gets all active group overrides, excluding expired timed groups.
     *
     * @return Set of active group names
     */
    public Set<String> getActiveGroupOverrides() {
        Set<String> active = new HashSet<>();
        long now = System.currentTimeMillis() / 1000;
        Iterator<Map.Entry<String, Long>> it = groupOverrides.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Long> entry = it.next();
            long expiry = entry.getValue();
            if (expiry == -1L || expiry > now) {
                active.add(entry.getKey());
            }
            // Expired entries are kept in the map but not returned as active
            // They will be cleaned up on next save
        }
        return active;
    }

    /**
     * Removes all expired timed group overrides.
     *
     * @return The number of expired groups that were cleaned up
     */
    public int cleanExpiredGroups() {
        long now = System.currentTimeMillis() / 1000;
        int cleaned = 0;
        Iterator<Map.Entry<String, Long>> it = groupOverrides.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Long> entry = it.next();
            long expiry = entry.getValue();
            if (expiry != -1L && expiry <= now) {
                it.remove();
                cleaned++;
            }
        }
        return cleaned;
    }

    /**
     * Gets all permissions for this user, combining user-specific permissions
     * with permissions from the primary group, group overrides, and their
     * inherited parent groups.
     * <p>
     * Resolution order (later overrides earlier):
     * 1. Default group permissions (if user has no primary group and is new)
     * 2. Primary group permissions (with inheritance)
     * 3. Group override permissions (with inheritance, sorted by priority)
     * 4. User-specific permissions (highest priority)
     * <p>
     * Negative permissions (starting with "-") always take priority and deny
     * the corresponding permission.
     *
     * @param manager The permission manager to resolve groups
     * @return All resolved permissions for this user
     */
    public Set<String> getAllPermissions(PermissionManager manager) {
        Set<String> allPerms = new LinkedHashSet<>();

        // 1. Get primary group permissions (with inheritance)
        String primary = primaryGroup;
        if (primary != null && !primary.isEmpty()) {
            PermissionGroup primaryGrp = manager.getGroup(primary);
            if (primaryGrp != null) {
                allPerms.addAll(primaryGrp.getAllPermissions(manager));
            }
        } else {
            // Fall back to default group
            PermissionGroup defaultGroup = manager.getDefaultGroup();
            if (defaultGroup != null) {
                allPerms.addAll(defaultGroup.getAllPermissions(manager));
            }
        }

        // 2. Get group override permissions (sorted by priority, lower first)
        Set<String> activeOverrides = getActiveGroupOverrides();
        activeOverrides.stream()
                .map(manager::getGroup)
                .filter(g -> g != null)
                .sorted((a, b) -> Integer.compare(a.getPriority(), b.getPriority()))
                .forEach(g -> allPerms.addAll(g.getAllPermissions(manager)));

        // 3. User-specific permissions override everything
        allPerms.addAll(permissions);

        return allPerms;
    }

    /**
     * Returns the effective prefix for this user based on the highest priority
     * group they belong to (including primary group and group overrides).
     *
     * @param manager The permission manager to resolve groups
     * @return The prefix string, or empty string if none
     */
    public String getEffectivePrefix(PermissionManager manager) {
        PermissionGroup highest = getHighestPriorityGroup(manager);
        return highest != null ? highest.getPrefix() : "";
    }

    /**
     * Returns the effective suffix for this user based on the highest priority
     * group they belong to (including primary group and group overrides).
     *
     * @param manager The permission manager to resolve groups
     * @return The suffix string, or empty string if none
     */
    public String getEffectiveSuffix(PermissionManager manager) {
        PermissionGroup highest = getHighestPriorityGroup(manager);
        return highest != null ? highest.getSuffix() : "";
    }

    /**
     * Gets the highest priority group for this user.
     * Considers the primary group and all active group overrides.
     *
     * @param manager The permission manager
     * @return The highest priority group, or null if none found
     */
    private PermissionGroup getHighestPriorityGroup(PermissionManager manager) {
        PermissionGroup highest = null;

        // Check primary group
        String primary = primaryGroup;
        if (primary != null && !primary.isEmpty()) {
            highest = manager.getGroup(primary);
        }

        // If no primary group, use default
        if (highest == null) {
            highest = manager.getDefaultGroup();
        }

        // Check group overrides for higher priority
        Set<String> activeOverrides = getActiveGroupOverrides();
        for (String groupName : activeOverrides) {
            PermissionGroup group = manager.getGroup(groupName);
            if (group != null) {
                if (highest == null || group.getPriority() > highest.getPriority()) {
                    highest = group;
                }
            }
        }

        return highest;
    }

    @Override
    public String toString() {
        return "PermissionUser{uuid=" + uuid + ", name='" + name
                + "', primaryGroup='" + primaryGroup
                + "', permissions=" + permissions
                + ", groupOverrides=" + groupOverrides + "}";
    }
}
