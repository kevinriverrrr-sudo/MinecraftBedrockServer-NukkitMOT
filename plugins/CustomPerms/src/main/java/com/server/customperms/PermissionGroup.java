package com.server.customperms;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Represents a permission group with inheritance support.
 * Groups can have parent groups, and permissions are resolved
 * through the inheritance chain with priority ordering.
 */
public class PermissionGroup {

    private String name;
    private String prefix;
    private String suffix;
    private int priority;
    private Set<String> permissions;
    private Set<String> parentGroups;
    private boolean isDefault;

    public PermissionGroup(String name) {
        this.name = name;
        this.prefix = "";
        this.suffix = "";
        this.priority = 0;
        this.permissions = new LinkedHashSet<>();
        this.parentGroups = new LinkedHashSet<>();
        this.isDefault = false;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public String getSuffix() {
        return suffix;
    }

    public void setSuffix(String suffix) {
        this.suffix = suffix;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public Set<String> getPermissions() {
        return permissions;
    }

    public void setPermissions(Set<String> permissions) {
        this.permissions = permissions != null ? permissions : new LinkedHashSet<>();
    }

    public Set<String> getParentGroups() {
        return parentGroups;
    }

    public void setParentGroups(Set<String> parentGroups) {
        this.parentGroups = parentGroups != null ? parentGroups : new LinkedHashSet<>();
    }

    public boolean isDefault() {
        return isDefault;
    }

    public void setDefault(boolean aDefault) {
        isDefault = aDefault;
    }

    /**
     * Resolves all permissions for this group including inherited permissions
     * from parent groups. Permissions are resolved in order of priority:
     * lower priority groups first, then higher priority groups override.
     * This means higher priority group permissions take precedence.
     *
     * @param manager The permission manager to resolve parent groups
     * @return All resolved permissions including inherited ones
     */
    public Set<String> getAllPermissions(PermissionManager manager) {
        Set<String> allPerms = new LinkedHashSet<>();
        resolvePermissions(manager, allPerms, new LinkedHashSet<>());
        return allPerms;
    }

    /**
     * Recursively resolves permissions through parent groups.
     * Uses a visited set to prevent infinite loops from circular inheritance.
     */
    private void resolvePermissions(PermissionManager manager, Set<String> allPerms, Set<String> visited) {
        if (visited.contains(name)) {
            return;
        }
        visited.add(name);

        // First resolve parent groups (lower priority) so their permissions come first
        for (String parentName : parentGroups) {
            PermissionGroup parent = manager.getGroup(parentName);
            if (parent != null) {
                parent.resolvePermissions(manager, allPerms, visited);
            }
        }

        // Then add this group's own permissions (they override parents since added later)
        allPerms.addAll(permissions);
    }

    /**
     * Adds a permission to this group.
     *
     * @param permission The permission node to add
     * @return true if the permission was added (was not already present)
     */
    public boolean addPermission(String permission) {
        return permissions.add(permission);
    }

    /**
     * Removes a permission from this group.
     *
     * @param permission The permission node to remove
     * @return true if the permission was removed (was present)
     */
    public boolean removePermission(String permission) {
        return permissions.remove(permission);
    }

    /**
     * Checks if this group has a specific permission.
     * This only checks direct permissions, not inherited ones.
     *
     * @param permission The permission node to check
     * @return true if the group has this permission directly
     */
    public boolean hasPermission(String permission) {
        return permissions.contains(permission);
    }

    /**
     * Adds a parent group for inheritance.
     *
     * @param parentName The name of the parent group
     * @return true if the parent was added
     */
    public boolean addParent(String parentName) {
        return parentGroups.add(parentName);
    }

    /**
     * Removes a parent group.
     *
     * @param parentName The name of the parent group to remove
     * @return true if the parent was removed
     */
    public boolean removeParent(String parentName) {
        return parentGroups.remove(parentName);
    }

    @Override
    public String toString() {
        return "PermissionGroup{name='" + name + "', prefix='" + prefix
                + "', suffix='" + suffix + "', priority=" + priority
                + ", isDefault=" + isDefault
                + ", permissions=" + permissions
                + ", parents=" + parentGroups + "}";
    }
}
