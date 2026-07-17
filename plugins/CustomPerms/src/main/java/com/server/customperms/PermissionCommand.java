package com.server.customperms;

import cn.nukkit.Player;
import cn.nukkit.command.Command;
import cn.nukkit.command.CommandSender;
import cn.nukkit.utils.TextFormat;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Handles all /perms commands for the CustomPerms plugin.
 * Provides comprehensive group and user management commands.
 */
public class PermissionCommand extends Command {

    private final CustomPermsPlugin plugin;
    private final PermissionManager manager;

    public PermissionCommand(CustomPermsPlugin plugin) {
        super("perms", "Manage permissions", "/perms <group|user|reload>");
        this.plugin = plugin;
        this.manager = plugin.getAPI();
        this.setPermission("customperms.admin");
    }

    @Override
    public boolean execute(CommandSender sender, String commandLabel, String[] args) {
        if (!testPermission(sender)) {
            sender.sendMessage(TextFormat.RED + "You don't have permission to use this command.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "group":
                handleGroup(sender, args);
                break;
            case "user":
                handleUser(sender, args);
                break;
            case "reload":
                handleReload(sender);
                break;
            default:
                sendHelp(sender);
                break;
        }

        return true;
    }

    /**
     * Sends the help message to the sender.
     */
    private void sendHelp(CommandSender sender) {
        sender.sendMessage(TextFormat.GOLD + "=== CustomPerms Help ===");
        sender.sendMessage(TextFormat.YELLOW + "/perms group create <name>" + TextFormat.GRAY + " - Create a group");
        sender.sendMessage(TextFormat.YELLOW + "/perms group delete <name>" + TextFormat.GRAY + " - Delete a group");
        sender.sendMessage(TextFormat.YELLOW + "/perms group list" + TextFormat.GRAY + " - List all groups");
        sender.sendMessage(TextFormat.YELLOW + "/perms group info <name>" + TextFormat.GRAY + " - Show group info");
        sender.sendMessage(TextFormat.YELLOW + "/perms group addperm <name> <perm>" + TextFormat.GRAY + " - Add permission");
        sender.sendMessage(TextFormat.YELLOW + "/perms group delperm <name> <perm>" + TextFormat.GRAY + " - Remove permission");
        sender.sendMessage(TextFormat.YELLOW + "/perms group setprefix <name> <prefix>" + TextFormat.GRAY + " - Set prefix");
        sender.sendMessage(TextFormat.YELLOW + "/perms group setsuffix <name> <suffix>" + TextFormat.GRAY + " - Set suffix");
        sender.sendMessage(TextFormat.YELLOW + "/perms group setpriority <name> <priority>" + TextFormat.GRAY + " - Set priority");
        sender.sendMessage(TextFormat.YELLOW + "/perms group setdefault <name> <true|false>" + TextFormat.GRAY + " - Set default");
        sender.sendMessage(TextFormat.YELLOW + "/perms group parent <name> add <parent>" + TextFormat.GRAY + " - Add parent");
        sender.sendMessage(TextFormat.YELLOW + "/perms group parent <name> remove <parent>" + TextFormat.GRAY + " - Remove parent");
        sender.sendMessage(TextFormat.YELLOW + "/perms user <name> info" + TextFormat.GRAY + " - Show user info");
        sender.sendMessage(TextFormat.YELLOW + "/perms user <name> addperm <perm>" + TextFormat.GRAY + " - Add user perm");
        sender.sendMessage(TextFormat.YELLOW + "/perms user <name> delperm <perm>" + TextFormat.GRAY + " - Remove user perm");
        sender.sendMessage(TextFormat.YELLOW + "/perms user <name> setgroup <group>" + TextFormat.GRAY + " - Set primary group");
        sender.sendMessage(TextFormat.YELLOW + "/perms user <name> addgroup <group> [duration]" + TextFormat.GRAY + " - Add group");
        sender.sendMessage(TextFormat.YELLOW + "/perms user <name> removegroup <group>" + TextFormat.GRAY + " - Remove group");
        sender.sendMessage(TextFormat.YELLOW + "/perms reload" + TextFormat.GRAY + " - Reload config");
    }

    // ==================== Group Commands ====================

    /**
     * Handles /perms group subcommands.
     */
    private void handleGroup(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(TextFormat.RED + "Usage: /perms group <create|delete|list|info|addperm|delperm|setprefix|setsuffix|setpriority|setdefault|parent>");
            return;
        }

        String action = args[1].toLowerCase();

        switch (action) {
            case "create":
                handleGroupCreate(sender, args);
                break;
            case "delete":
                handleGroupDelete(sender, args);
                break;
            case "list":
                handleGroupList(sender);
                break;
            case "info":
                handleGroupInfo(sender, args);
                break;
            case "addperm":
                handleGroupAddPerm(sender, args);
                break;
            case "delperm":
                handleGroupDelPerm(sender, args);
                break;
            case "setprefix":
                handleGroupSetPrefix(sender, args);
                break;
            case "setsuffix":
                handleGroupSetSuffix(sender, args);
                break;
            case "setpriority":
                handleGroupSetPriority(sender, args);
                break;
            case "setdefault":
                handleGroupSetDefault(sender, args);
                break;
            case "parent":
                handleGroupParent(sender, args);
                break;
            default:
                sender.sendMessage(TextFormat.RED + "Unknown group action: " + action);
                break;
        }
    }

    /**
     * /perms group create <name>
     */
    private void handleGroupCreate(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(TextFormat.RED + "Usage: /perms group create <name>");
            return;
        }

        String groupName = args[2];
        PermissionGroup group = manager.createGroup(groupName);
        if (group == null) {
            sender.sendMessage(TextFormat.RED + "Group '" + groupName + "' already exists!");
            return;
        }

        sender.sendMessage(TextFormat.GREEN + "Group '" + TextFormat.WHITE + groupName
                + TextFormat.GREEN + "' created successfully.");
    }

    /**
     * /perms group delete <name>
     */
    private void handleGroupDelete(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(TextFormat.RED + "Usage: /perms group delete <name>");
            return;
        }

        String groupName = args[2];
        if (manager.deleteGroup(groupName)) {
            sender.sendMessage(TextFormat.GREEN + "Group '" + TextFormat.WHITE + groupName
                    + TextFormat.GREEN + "' deleted successfully.");
            // Re-apply permissions for online players
            for (Player player : plugin.getServer().getOnlinePlayers().values()) {
                manager.applyPermissionsToPlayer(player.getUniqueId());
            }
        } else {
            sender.sendMessage(TextFormat.RED + "Group '" + groupName + "' not found!");
        }
    }

    /**
     * /perms group list
     */
    private void handleGroupList(CommandSender sender) {
        Map<String, PermissionGroup> groups = manager.getGroups();
        if (groups.isEmpty()) {
            sender.sendMessage(TextFormat.YELLOW + "No groups found.");
            return;
        }

        sender.sendMessage(TextFormat.GOLD + "=== Groups (" + groups.size() + ") ===");
        for (PermissionGroup group : groups.values()) {
            String defaultTag = group.isDefault() ? TextFormat.GREEN + " [DEFAULT]" : "";
            String prefix = group.getPrefix().isEmpty() ? "" : TextFormat.GRAY + " Prefix: " + TextFormat.WHITE + group.getPrefix();
            sender.sendMessage(TextFormat.YELLOW + "  " + group.getName()
                    + TextFormat.GRAY + " (Priority: " + group.getPriority() + ")"
                    + defaultTag + prefix);
        }
    }

    /**
     * /perms group info <name>
     */
    private void handleGroupInfo(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(TextFormat.RED + "Usage: /perms group info <name>");
            return;
        }

        String groupName = args[2];
        PermissionGroup group = manager.getGroup(groupName);
        if (group == null) {
            sender.sendMessage(TextFormat.RED + "Group '" + groupName + "' not found!");
            return;
        }

        sender.sendMessage(TextFormat.GOLD + "=== Group: " + group.getName() + " ===");
        sender.sendMessage(TextFormat.YELLOW + "Prefix: " + TextFormat.WHITE + group.getPrefix());
        sender.sendMessage(TextFormat.YELLOW + "Suffix: " + TextFormat.WHITE + group.getSuffix());
        sender.sendMessage(TextFormat.YELLOW + "Priority: " + TextFormat.WHITE + group.getPriority());
        sender.sendMessage(TextFormat.YELLOW + "Default: " + TextFormat.WHITE + group.isDefault());
        sender.sendMessage(TextFormat.YELLOW + "Parents: " + TextFormat.WHITE
                + (group.getParentGroups().isEmpty() ? "None" : String.join(", ", group.getParentGroups())));

        sender.sendMessage(TextFormat.YELLOW + "Direct Permissions:");
        if (group.getPermissions().isEmpty()) {
            sender.sendMessage(TextFormat.GRAY + "  None");
        } else {
            for (String perm : group.getPermissions()) {
                if (perm.startsWith("-")) {
                    sender.sendMessage(TextFormat.RED + "  - " + perm);
                } else {
                    sender.sendMessage(TextFormat.GREEN + "  + " + perm);
                }
            }
        }

        sender.sendMessage(TextFormat.YELLOW + "All Permissions (including inherited):");
        Set<String> allPerms = group.getAllPermissions(manager);
        if (allPerms.isEmpty()) {
            sender.sendMessage(TextFormat.GRAY + "  None");
        } else {
            for (String perm : allPerms) {
                if (perm.startsWith("-")) {
                    sender.sendMessage(TextFormat.RED + "  - " + perm);
                } else {
                    sender.sendMessage(TextFormat.GREEN + "  + " + perm);
                }
            }
        }
    }

    /**
     * /perms group addperm <name> <permission>
     */
    private void handleGroupAddPerm(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(TextFormat.RED + "Usage: /perms group addperm <name> <permission>");
            return;
        }

        String groupName = args[2];
        String permission = args[3];

        if (manager.addGroupPermission(groupName, permission)) {
            sender.sendMessage(TextFormat.GREEN + "Added permission '" + TextFormat.WHITE + permission
                    + TextFormat.GREEN + "' to group '" + TextFormat.WHITE + groupName
                    + TextFormat.GREEN + "'.");
            // Re-apply permissions for online players in this group
            for (Player player : plugin.getServer().getOnlinePlayers().values()) {
                manager.applyPermissionsToPlayer(player.getUniqueId());
            }
        } else {
            PermissionGroup group = manager.getGroup(groupName);
            if (group == null) {
                sender.sendMessage(TextFormat.RED + "Group '" + groupName + "' not found!");
            } else {
                sender.sendMessage(TextFormat.RED + "Permission '" + permission + "' already exists in group '" + groupName + "'.");
            }
        }
    }

    /**
     * /perms group delperm <name> <permission>
     */
    private void handleGroupDelPerm(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(TextFormat.RED + "Usage: /perms group delperm <name> <permission>");
            return;
        }

        String groupName = args[2];
        String permission = args[3];

        if (manager.removeGroupPermission(groupName, permission)) {
            sender.sendMessage(TextFormat.GREEN + "Removed permission '" + TextFormat.WHITE + permission
                    + TextFormat.GREEN + "' from group '" + TextFormat.WHITE + groupName
                    + TextFormat.GREEN + "'.");
            // Re-apply permissions for online players in this group
            for (Player player : plugin.getServer().getOnlinePlayers().values()) {
                manager.applyPermissionsToPlayer(player.getUniqueId());
            }
        } else {
            PermissionGroup group = manager.getGroup(groupName);
            if (group == null) {
                sender.sendMessage(TextFormat.RED + "Group '" + groupName + "' not found!");
            } else {
                sender.sendMessage(TextFormat.RED + "Permission '" + permission + "' not found in group '" + groupName + "'.");
            }
        }
    }

    /**
     * /perms group setprefix <name> <prefix>
     */
    private void handleGroupSetPrefix(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(TextFormat.RED + "Usage: /perms group setprefix <name> <prefix>");
            return;
        }

        String groupName = args[2];
        // Join remaining args as prefix (prefix might contain spaces)
        String prefix = String.join(" ", Arrays.copyOfRange(args, 3, args.length));

        PermissionGroup group = manager.getGroup(groupName);
        if (group == null) {
            sender.sendMessage(TextFormat.RED + "Group '" + groupName + "' not found!");
            return;
        }

        group.setPrefix(prefix);
        manager.saveGroups();
        sender.sendMessage(TextFormat.GREEN + "Set prefix of '" + TextFormat.WHITE + groupName
                + TextFormat.GREEN + "' to: " + TextFormat.WHITE + prefix);
    }

    /**
     * /perms group setsuffix <name> <suffix>
     */
    private void handleGroupSetSuffix(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(TextFormat.RED + "Usage: /perms group setsuffix <name> <suffix>");
            return;
        }

        String groupName = args[2];
        String suffix = String.join(" ", Arrays.copyOfRange(args, 3, args.length));

        PermissionGroup group = manager.getGroup(groupName);
        if (group == null) {
            sender.sendMessage(TextFormat.RED + "Group '" + groupName + "' not found!");
            return;
        }

        group.setSuffix(suffix);
        manager.saveGroups();
        sender.sendMessage(TextFormat.GREEN + "Set suffix of '" + TextFormat.WHITE + groupName
                + TextFormat.GREEN + "' to: " + TextFormat.WHITE + suffix);
    }

    /**
     * /perms group setpriority <name> <priority>
     */
    private void handleGroupSetPriority(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(TextFormat.RED + "Usage: /perms group setpriority <name> <priority>");
            return;
        }

        String groupName = args[2];
        int priority;
        try {
            priority = Integer.parseInt(args[3]);
        } catch (NumberFormatException e) {
            sender.sendMessage(TextFormat.RED + "Invalid priority number: " + args[3]);
            return;
        }

        PermissionGroup group = manager.getGroup(groupName);
        if (group == null) {
            sender.sendMessage(TextFormat.RED + "Group '" + groupName + "' not found!");
            return;
        }

        group.setPriority(priority);
        manager.saveGroups();
        sender.sendMessage(TextFormat.GREEN + "Set priority of '" + TextFormat.WHITE + groupName
                + TextFormat.GREEN + "' to: " + TextFormat.WHITE + priority);
    }

    /**
     * /perms group setdefault <name> <true|false>
     */
    private void handleGroupSetDefault(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(TextFormat.RED + "Usage: /perms group setdefault <name> <true|false>");
            return;
        }

        String groupName = args[2];
        boolean isDefault;
        if (args[3].equalsIgnoreCase("true")) {
            isDefault = true;
        } else if (args[3].equalsIgnoreCase("false")) {
            isDefault = false;
        } else {
            sender.sendMessage(TextFormat.RED + "Value must be 'true' or 'false'.");
            return;
        }

        PermissionGroup group = manager.getGroup(groupName);
        if (group == null) {
            sender.sendMessage(TextFormat.RED + "Group '" + groupName + "' not found!");
            return;
        }

        // If setting as default, unset other defaults
        if (isDefault) {
            for (PermissionGroup otherGroup : manager.getGroups().values()) {
                if (otherGroup.isDefault() && !otherGroup.getName().equalsIgnoreCase(groupName)) {
                    otherGroup.setDefault(false);
                }
            }
        }

        group.setDefault(isDefault);
        manager.saveGroups();
        sender.sendMessage(TextFormat.GREEN + "Set default flag of '" + TextFormat.WHITE + groupName
                + TextFormat.GREEN + "' to: " + TextFormat.WHITE + isDefault);
    }

    /**
     * /perms group parent <name> add|remove <parent>
     */
    private void handleGroupParent(CommandSender sender, String[] args) {
        if (args.length < 5) {
            sender.sendMessage(TextFormat.RED + "Usage: /perms group parent <name> <add|remove> <parent>");
            return;
        }

        String groupName = args[2];
        String parentAction = args[3].toLowerCase();
        String parentName = args[4];

        PermissionGroup group = manager.getGroup(groupName);
        if (group == null) {
            sender.sendMessage(TextFormat.RED + "Group '" + groupName + "' not found!");
            return;
        }

        // Verify parent group exists
        if (manager.getGroup(parentName) == null) {
            sender.sendMessage(TextFormat.RED + "Parent group '" + parentName + "' not found!");
            return;
        }

        // Check for circular inheritance
        if (parentName.equalsIgnoreCase(groupName)) {
            sender.sendMessage(TextFormat.RED + "A group cannot be its own parent!");
            return;
        }

        switch (parentAction) {
            case "add":
                if (group.addParent(parentName)) {
                    manager.saveGroups();
                    sender.sendMessage(TextFormat.GREEN + "Added parent '" + TextFormat.WHITE + parentName
                            + TextFormat.GREEN + "' to group '" + TextFormat.WHITE + groupName
                            + TextFormat.GREEN + "'.");
                } else {
                    sender.sendMessage(TextFormat.RED + "Group '" + groupName + "' already has parent '" + parentName + "'.");
                }
                break;
            case "remove":
                if (group.removeParent(parentName)) {
                    manager.saveGroups();
                    sender.sendMessage(TextFormat.GREEN + "Removed parent '" + TextFormat.WHITE + parentName
                            + TextFormat.GREEN + "' from group '" + TextFormat.WHITE + groupName
                            + TextFormat.GREEN + "'.");
                } else {
                    sender.sendMessage(TextFormat.RED + "Group '" + groupName + "' doesn't have parent '" + parentName + "'.");
                }
                break;
            default:
                sender.sendMessage(TextFormat.RED + "Unknown parent action: " + parentAction + ". Use 'add' or 'remove'.");
                break;
        }
    }

    // ==================== User Commands ====================

    /**
     * Handles /perms user subcommands.
     */
    private void handleUser(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(TextFormat.RED + "Usage: /perms user <name> <info|addperm|delperm|setgroup|addgroup|removegroup>");
            return;
        }

        String playerName = args[1];
        String userAction = args[2].toLowerCase();

        // Resolve player UUID
        UUID uuid = resolveUUID(sender, playerName);
        if (uuid == null) {
            return;
        }

        // Ensure user exists in the system
        manager.getOrCreateUser(uuid, playerName);

        switch (userAction) {
            case "info":
                handleUserInfo(sender, uuid, playerName);
                break;
            case "addperm":
                handleUserAddPerm(sender, args, uuid, playerName);
                break;
            case "delperm":
                handleUserDelPerm(sender, args, uuid, playerName);
                break;
            case "setgroup":
                handleUserSetGroup(sender, args, uuid, playerName);
                break;
            case "addgroup":
                handleUserAddGroup(sender, args, uuid, playerName);
                break;
            case "removegroup":
                handleUserRemoveGroup(sender, args, uuid, playerName);
                break;
            default:
                sender.sendMessage(TextFormat.RED + "Unknown user action: " + userAction);
                break;
        }
    }

    /**
     * Resolves a player name to a UUID.
     * Checks online players first, then offline data.
     */
    private UUID resolveUUID(CommandSender sender, String playerName) {
        // Check online players first
        for (Player onlinePlayer : plugin.getServer().getOnlinePlayers().values()) {
            if (onlinePlayer.getName().equalsIgnoreCase(playerName)) {
                return onlinePlayer.getUniqueId();
            }
        }

        // Check offline data
        for (Map.Entry<UUID, PermissionUser> entry : manager.getUsers().entrySet()) {
            if (entry.getValue().getName().equalsIgnoreCase(playerName)) {
                return entry.getKey();
            }
        }

        sender.sendMessage(TextFormat.RED + "Player '" + playerName + "' not found (must have joined at least once).");
        return null;
    }

    /**
     * /perms user <name> info
     */
    private void handleUserInfo(CommandSender sender, UUID uuid, String playerName) {
        PermissionUser user = manager.getUser(uuid);
        if (user == null) {
            sender.sendMessage(TextFormat.RED + "User data not found for '" + playerName + "'.");
            return;
        }

        // Clean expired groups
        user.cleanExpiredGroups();

        sender.sendMessage(TextFormat.GOLD + "=== User: " + playerName + " ===");
        sender.sendMessage(TextFormat.YELLOW + "UUID: " + TextFormat.WHITE + uuid.toString());
        sender.sendMessage(TextFormat.YELLOW + "Primary Group: " + TextFormat.WHITE
                + (user.getPrimaryGroup() != null ? user.getPrimaryGroup() : "None"));

        // Show prefix and suffix
        String prefix = user.getEffectivePrefix(manager);
        String suffix = user.getEffectiveSuffix(manager);
        sender.sendMessage(TextFormat.YELLOW + "Effective Prefix: " + TextFormat.WHITE + prefix);
        sender.sendMessage(TextFormat.YELLOW + "Effective Suffix: " + TextFormat.WHITE + suffix);

        // Show group overrides
        Map<String, Long> overrides = user.getGroupOverrides();
        if (overrides.isEmpty()) {
            sender.sendMessage(TextFormat.YELLOW + "Group Overrides: " + TextFormat.GRAY + "None");
        } else {
            sender.sendMessage(TextFormat.YELLOW + "Group Overrides:");
            long now = System.currentTimeMillis() / 1000;
            for (Map.Entry<String, Long> entry : overrides.entrySet()) {
                String group = entry.getKey();
                long expiry = entry.getValue();
                if (expiry == -1L) {
                    sender.sendMessage(TextFormat.GREEN + "  + " + group + TextFormat.GRAY + " (permanent)");
                } else if (expiry > now) {
                    long remaining = expiry - now;
                    sender.sendMessage(TextFormat.GREEN + "  + " + group + TextFormat.GRAY
                            + " (expires in " + PermissionManager.formatDuration(remaining) + ")");
                } else {
                    sender.sendMessage(TextFormat.RED + "  - " + group + TextFormat.GRAY + " (expired)");
                }
            }
        }

        // Show direct permissions
        sender.sendMessage(TextFormat.YELLOW + "Direct Permissions:");
        if (user.getPermissions().isEmpty()) {
            sender.sendMessage(TextFormat.GRAY + "  None");
        } else {
            for (String perm : user.getPermissions()) {
                if (perm.startsWith("-")) {
                    sender.sendMessage(TextFormat.RED + "  - " + perm);
                } else {
                    sender.sendMessage(TextFormat.GREEN + "  + " + perm);
                }
            }
        }

        // Show all effective permissions
        Set<String> allPerms = user.getAllPermissions(manager);
        sender.sendMessage(TextFormat.YELLOW + "All Effective Permissions (" + allPerms.size() + "):");
        if (allPerms.size() <= 20) {
            for (String perm : allPerms) {
                if (perm.startsWith("-")) {
                    sender.sendMessage(TextFormat.RED + "  - " + perm);
                } else {
                    sender.sendMessage(TextFormat.GREEN + "  + " + perm);
                }
            }
        } else {
            // Too many permissions, show count and first 15
            int count = 0;
            for (String perm : allPerms) {
                if (count >= 15) {
                    break;
                }
                if (perm.startsWith("-")) {
                    sender.sendMessage(TextFormat.RED + "  - " + perm);
                } else {
                    sender.sendMessage(TextFormat.GREEN + "  + " + perm);
                }
                count++;
            }
            sender.sendMessage(TextFormat.GRAY + "  ... and " + (allPerms.size() - 15) + " more. Use group info for details.");
        }
    }

    /**
     * /perms user <name> addperm <permission>
     */
    private void handleUserAddPerm(CommandSender sender, String[] args, UUID uuid, String playerName) {
        if (args.length < 4) {
            sender.sendMessage(TextFormat.RED + "Usage: /perms user <name> addperm <permission>");
            return;
        }

        String permission = args[3];
        if (manager.addPermission(uuid, permission)) {
            sender.sendMessage(TextFormat.GREEN + "Added permission '" + TextFormat.WHITE + permission
                    + TextFormat.GREEN + "' to user '" + TextFormat.WHITE + playerName
                    + TextFormat.GREEN + "'.");
            manager.applyPermissionsToPlayer(uuid);
        } else {
            sender.sendMessage(TextFormat.RED + "Could not add permission. Does the user exist, or is the permission already set?");
        }
    }

    /**
     * /perms user <name> delperm <permission>
     */
    private void handleUserDelPerm(CommandSender sender, String[] args, UUID uuid, String playerName) {
        if (args.length < 4) {
            sender.sendMessage(TextFormat.RED + "Usage: /perms user <name> delperm <permission>");
            return;
        }

        String permission = args[3];
        if (manager.removePermission(uuid, permission)) {
            sender.sendMessage(TextFormat.GREEN + "Removed permission '" + TextFormat.WHITE + permission
                    + TextFormat.GREEN + "' from user '" + TextFormat.WHITE + playerName
                    + TextFormat.GREEN + "'.");
            manager.applyPermissionsToPlayer(uuid);
        } else {
            sender.sendMessage(TextFormat.RED + "Could not remove permission. Does the user exist, or is the permission not set?");
        }
    }

    /**
     * /perms user <name> setgroup <group>
     */
    private void handleUserSetGroup(CommandSender sender, String[] args, UUID uuid, String playerName) {
        if (args.length < 4) {
            sender.sendMessage(TextFormat.RED + "Usage: /perms user <name> setgroup <group>");
            return;
        }

        String groupName = args[3];
        if (manager.setUserPrimaryGroup(uuid, groupName)) {
            sender.sendMessage(TextFormat.GREEN + "Set primary group of '" + TextFormat.WHITE + playerName
                    + TextFormat.GREEN + "' to '" + TextFormat.WHITE + groupName
                    + TextFormat.GREEN + "'.");
            manager.applyPermissionsToPlayer(uuid);
        } else {
            sender.sendMessage(TextFormat.RED + "Could not set group. Does the user exist, and does the group '" + groupName + "' exist?");
        }
    }

    /**
     * /perms user <name> addgroup <group> [duration]
     */
    private void handleUserAddGroup(CommandSender sender, String[] args, UUID uuid, String playerName) {
        if (args.length < 4) {
            sender.sendMessage(TextFormat.RED + "Usage: /perms user <name> addgroup <group> [duration]");
            return;
        }

        String groupName = args[3];
        long durationSeconds = 0;

        if (args.length >= 5) {
            durationSeconds = PermissionManager.parseDuration(args[4]);
            if (durationSeconds <= 0) {
                sender.sendMessage(TextFormat.RED + "Invalid duration format: " + args[4]);
                sender.sendMessage(TextFormat.GRAY + "Examples: 30d, 1h, 15m, 1h30m");
                return;
            }
        }

        if (manager.addUserGroupOverride(uuid, groupName, durationSeconds)) {
            String durationMsg = durationSeconds > 0
                    ? TextFormat.GRAY + " for " + PermissionManager.formatDuration(durationSeconds)
                    : "";
            sender.sendMessage(TextFormat.GREEN + "Added group '" + TextFormat.WHITE + groupName
                    + TextFormat.GREEN + "' to user '" + TextFormat.WHITE + playerName
                    + TextFormat.GREEN + "'" + durationMsg + ".");
            manager.applyPermissionsToPlayer(uuid);
        } else {
            sender.sendMessage(TextFormat.RED + "Could not add group. Does the user exist, and does the group '" + groupName + "' exist?");
        }
    }

    /**
     * /perms user <name> removegroup <group>
     */
    private void handleUserRemoveGroup(CommandSender sender, String[] args, UUID uuid, String playerName) {
        if (args.length < 4) {
            sender.sendMessage(TextFormat.RED + "Usage: /perms user <name> removegroup <group>");
            return;
        }

        String groupName = args[3];
        if (manager.removeUserGroupOverride(uuid, groupName)) {
            sender.sendMessage(TextFormat.GREEN + "Removed group '" + TextFormat.WHITE + groupName
                    + TextFormat.GREEN + "' from user '" + TextFormat.WHITE + playerName
                    + TextFormat.GREEN + "'.");
            manager.applyPermissionsToPlayer(uuid);
        } else {
            sender.sendMessage(TextFormat.RED + "Could not remove group override. Does the user exist, or is the group '" + groupName + "' not assigned?");
        }
    }

    // ==================== Reload Command ====================

    /**
     * /perms reload
     */
    private void handleReload(CommandSender sender) {
        sender.sendMessage(TextFormat.YELLOW + "Reloading CustomPerms...");
        manager.reload();
        sender.sendMessage(TextFormat.GREEN + "CustomPerms reloaded successfully!");
    }
}
