package com.server.customperms;

import cn.nukkit.Player;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.EventPriority;
import cn.nukkit.event.Listener;
import cn.nukkit.event.player.PlayerJoinEvent;
import cn.nukkit.event.player.PlayerLoginEvent;
import cn.nukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

/**
 * Event listener for the CustomPerms plugin.
 * Handles player login/join/quit events to manage permissions.
 */
public class PermissionListener implements Listener {

    private final CustomPermsPlugin plugin;
    private final PermissionManager manager;

    public PermissionListener(CustomPermsPlugin plugin) {
        this.plugin = plugin;
        this.manager = plugin.getAPI();
    }

    /**
     * Called when a player logs in (before they fully join).
     * This is where we assign the default group to new players.
     * Using LOWEST priority to ensure permissions are set before
     * other plugins check them.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerLogin(PlayerLoginEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        String name = player.getName();

        // Get or create user data - this assigns the default group to new players
        PermissionUser user = manager.getOrCreateUser(uuid, name);

        if (user != null) {
            plugin.getLogger().debug("Loaded permission data for " + name + " (Primary group: "
                    + (user.getPrimaryGroup() != null ? user.getPrimaryGroup() : "none") + ")");
        }
    }

    /**
     * Called when a player joins the server.
     * This is where we apply all their permissions.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // Update the user's name in case it changed
        PermissionUser user = manager.getUser(uuid);
        if (user != null && !user.getName().equals(player.getName())) {
            user.setName(player.getName());
        }

        // Clean expired timed groups
        if (user != null) {
            int expired = user.cleanExpiredGroups();
            if (expired > 0) {
                plugin.getLogger().info("Expired " + expired + " timed group(s) for " + player.getName());
                manager.saveUsers();
            }
        }

        // Apply all permissions to the player
        manager.applyPermissionsToPlayer(uuid);

        plugin.getLogger().debug("Applied permissions for " + player.getName());
    }

    /**
     * Called when a player quits the server.
     * Save their data and clean up.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // Save user data
        PermissionUser user = manager.getUser(uuid);
        if (user != null) {
            // Update name
            user.setName(player.getName());

            // Clean expired timed groups before saving
            user.cleanExpiredGroups();

            // Save user data
            manager.saveUsers();
        }

        // Clear permissions from the player
        manager.clearPermissionsFromPlayer(uuid);

        plugin.getLogger().debug("Saved and cleared permission data for " + player.getName());
    }
}
