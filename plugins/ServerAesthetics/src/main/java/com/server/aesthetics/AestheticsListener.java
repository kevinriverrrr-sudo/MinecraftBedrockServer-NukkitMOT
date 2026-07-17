package com.server.aesthetics;

import cn.nukkit.Player;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.EventPriority;
import cn.nukkit.event.Listener;
import cn.nukkit.event.player.PlayerJoinEvent;
import cn.nukkit.event.player.PlayerQuitEvent;
import cn.nukkit.event.player.PlayerRespawnEvent;
import cn.nukkit.utils.TextFormat;

/**
 * Event listener for player join, quit, and respawn events.
 * Integrates with all managers to provide a cohesive player experience.
 */
public class AestheticsListener implements Listener {

    private final ServerAestheticsPlugin plugin;

    public AestheticsListener(ServerAestheticsPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Handles player join events.
     * Sets up scoreboard, tab list, and join effects.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Check if this is the player's first join
        boolean isFirstJoin = plugin.isFirstJoin(player);

        // Cancel the default join message (we'll send our own)
        event.setJoinMessage("");

        // Handle join effects (messages, titles, sounds, fireworks)
        plugin.getJoinEffectManager().handleJoin(player, isFirstJoin);

        // Create scoreboard for the player (delayed slightly so client is ready)
        plugin.getServer().getScheduler().scheduleDelayedTask(plugin, () -> {
            if (player.isOnline()) {
                plugin.getScoreboardManager().createScoreboard(player);
                plugin.getTabManager().updatePlayerTab(player);
            }
        }, 30); // 1.5 second delay for client to be fully ready

        // Mark player as joined (for first-join tracking)
        if (isFirstJoin) {
            plugin.markPlayerJoined(player);

            // Teleport to spawn on first join if configured
            if (plugin.getSpawnManager().shouldTeleportOnFirstJoin()) {
                plugin.getServer().getScheduler().scheduleDelayedTask(plugin, () -> {
                    if (player.isOnline()) {
                        plugin.getSpawnManager().teleportToSpawn(player);
                    }
                }, 40); // 2 second delay
            }
        }
    }

    /**
     * Handles player quit events.
     * Cleans up scoreboard and sends custom quit message.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        // Cancel the default quit message (we'll send our own)
        event.setQuitMessage("");

        // Handle quit effects (custom quit message)
        plugin.getJoinEffectManager().handleQuit(player);

        // Clean up scoreboard
        plugin.getScoreboardManager().removeScoreboard(player);

        // Clean up tab list
        plugin.getTabManager().removePlayer(player);
    }

    /**
     * Handles player respawn events.
     * Teleports player to spawn if configured.
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();

        // Check if we should respawn the player at spawn
        if (plugin.getSpawnManager().getSpawnPosition() != null) {
            // Set the respawn position to spawn
            event.setRespawnPosition(plugin.getSpawnManager().getSpawnPosition());

            // Send respawn message
            plugin.getServer().getScheduler().scheduleDelayedTask(plugin, () -> {
                if (player.isOnline()) {
                    player.sendMessage(TextFormat.colorize("§7You have respawned at spawn."));

                    // Refresh scoreboard after respawn
                    plugin.getScoreboardManager().createScoreboard(player);
                }
            }, 10);
        }
    }
}
