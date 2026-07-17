package com.server.customperms;

import cn.nukkit.Server;
import cn.nukkit.plugin.PluginBase;
import cn.nukkit.scheduler.TaskHandler;

/**
 * Main plugin class for CustomPerms - a custom permissions system
 * for Nukkit-MOT servers. This is a self-written alternative to LuckPerms.
 * <p>
 * Provides a full-featured permission management system with:
 * - Group-based permissions with inheritance
 * - User-specific permission overrides
 * - Timed group assignments
 * - Wildcard and negation permission support
 * - Prefix/suffix system based on group priority
 * - YAML-based persistence
 * - Public API for other plugins
 */
public class CustomPermsPlugin extends PluginBase {

    private static CustomPermsPlugin instance;
    private PermissionManager permissionManager;
    private TaskHandler autoSaveTask;
    private int autoSaveInterval;

    @Override
    public void onLoad() {
        instance = this;
    }

    @Override
    public void onEnable() {
        // Save default config if it doesn't exist
        saveDefaultConfig();
        reloadConfig();

        // Initialize the permission manager
        permissionManager = new PermissionManager(this);

        // Load all group and user data
        permissionManager.loadData();

        // Register the command
        PermissionCommand permsCommand = new PermissionCommand(this);
        Server.getInstance().getCommandMap().register("customperms", permsCommand);

        // Register event listeners
        getServer().getPluginManager().registerEvents(new PermissionListener(this), this);

        // Start auto-save task
        autoSaveInterval = getConfig().getInt("auto-save", 300);
        if (autoSaveInterval > 0) {
            startAutoSaveTask();
        }

        // Apply permissions for any players already online (e.g., during reload)
        for (cn.nukkit.Player player : getServer().getOnlinePlayers().values()) {
            permissionManager.getOrCreateUser(player.getUniqueId(), player.getName());
            permissionManager.applyPermissionsToPlayer(player.getUniqueId());
        }

        getLogger().info("CustomPerms enabled! API is available via CustomPermsPlugin.getAPI()");
        getLogger().info("Loaded " + permissionManager.getGroups().size() + " groups.");
    }

    @Override
    public void onDisable() {
        // Stop auto-save task
        if (autoSaveTask != null) {
            autoSaveTask.cancel();
            autoSaveTask = null;
        }

        // Save all data
        if (permissionManager != null) {
            permissionManager.saveAll();
        }

        // Clear permissions for all online players
        if (permissionManager != null) {
            for (cn.nukkit.Player player : getServer().getOnlinePlayers().values()) {
                permissionManager.clearPermissionsFromPlayer(player.getUniqueId());
            }
        }

        getLogger().info("CustomPerms disabled. All data saved.");
    }

    /**
     * Starts the auto-save task that periodically saves all permission data.
     */
    private void startAutoSaveTask() {
        if (autoSaveTask != null) {
            autoSaveTask.cancel();
        }

        autoSaveTask = getServer().getScheduler().scheduleRepeatingTask(this, () -> {
            if (permissionManager != null) {
                permissionManager.saveAll();
                getLogger().debug("Auto-saved permission data.");
            }
        }, autoSaveInterval * 20); // Convert seconds to ticks (20 ticks per second)
    }

    /**
     * Gets the PermissionManager API instance.
     * Other plugins can use this to interact with the permission system.
     * <p>
     * Usage from another plugin:
     * <pre>
     * CustomPermsPlugin permsPlugin = (CustomPermsPlugin) getServer().getPluginManager().getPlugin("CustomPerms");
     * if (permsPlugin != null) {
     *     PermissionManager api = permsPlugin.getAPI();
     *     boolean hasPerm = api.hasPermission(player.getUniqueId(), "some.permission");
     * }
     * </pre>
     * Or using the static method:
     * <pre>
     * PermissionManager api = CustomPermsPlugin.getAPI();
     * if (api != null) {
     *     boolean hasPerm = api.hasPermission(player.getUniqueId(), "some.permission");
     * }
     * </pre>
     *
     * @return The PermissionManager instance, or null if the plugin is not enabled
     */
    public PermissionManager getAPI() {
        return permissionManager;
    }

    /**
     * Static method to get the PermissionManager API from any context.
     * This is the recommended way for other plugins to access the API.
     *
     * @return The PermissionManager instance, or null if the plugin is not loaded
     */
    public static PermissionManager getStaticAPI() {
        if (instance == null) {
            return null;
        }
        return instance.permissionManager;
    }

    /**
     * Gets the plugin instance.
     *
     * @return The CustomPermsPlugin instance, or null if not loaded
     */
    public static CustomPermsPlugin getInstance() {
        return instance;
    }
}
