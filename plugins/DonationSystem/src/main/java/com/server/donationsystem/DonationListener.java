package com.server.donationsystem;

import cn.nukkit.Player;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.EventPriority;
import cn.nukkit.event.Listener;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.event.player.PlayerDeathEvent;
import cn.nukkit.event.player.PlayerJoinEvent;
import cn.nukkit.event.player.PlayerMoveEvent;
import cn.nukkit.event.player.PlayerQuitEvent;

/**
 * Event listener for the donation system.
 * Handles:
 * - PlayerJoinEvent: Reapply rank, check expiry, notify players
 * - PlayerQuitEvent: Cleanup permission attachments
 * - PlayerDeathEvent: Store death location for /back command
 * - PlayerMoveEvent: Check if flying players should have flight disabled
 */
public class DonationListener implements Listener {

    private final DonationSystemPlugin plugin;

    public DonationListener(DonationSystemPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * When a player joins, reapply their donation rank and check for expiry.
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        DonationManager manager = plugin.getDonationManager();

        // Let the manager handle rank application and expiry checking
        manager.onPlayerJoin(player);

        // If the player has an active rank, send a welcome message
        DonationRank rank = manager.getPlayerRank(player.getName());
        if (rank != null && !manager.isRankExpired(player.getName())) {
            // Delayed message so it appears after the join message
            plugin.getServer().getScheduler().scheduleDelayedTask(plugin, () -> {
                if (player.isOnline()) {
                    long remaining = manager.getRemainingTime(player.getName());
                    String timeStr;
                    if (remaining == -1) {
                        timeStr = "§aPermanent";
                    } else {
                        timeStr = "§b" + DonationManager.formatTime(remaining);
                    }
                    player.sendMessage("§6[Donation] §7Your rank: " + rank.getDisplayName() +
                            " §7| Expires: " + timeStr);
                }
            }, 40); // 2 seconds delay
        }
    }

    /**
     * When a player quits, clean up their permission attachment.
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        DonationManager manager = plugin.getDonationManager();
        manager.onPlayerQuit(player);
    }

    /**
     * When a player dies, store their death location for the /back command.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        DonationManager manager = plugin.getDonationManager();

        // Check if the player has back perk
        DonationRank rank = manager.getPlayerRank(player.getName());
        if (rank != null && rank.hasBack() && !manager.isRankExpired(player.getName())) {
            // Store death location as "levelName:x:y:z"
            String locationString = player.getLevel().getName() + ":" +
                    player.x + ":" + player.y + ":" + player.z;
            manager.setDeathLocation(player.getUniqueId(), locationString);

            // Notify the player
            plugin.getServer().getScheduler().scheduleDelayedTask(plugin, () -> {
                if (player.isOnline()) {
                    player.sendMessage("§7You died! Use §e/back §7to return to your death location.");
                }
            }, 20); // 1 second delay
        }
    }

    /**
     * Check if a flying player should still have flight enabled.
     * This handles edge cases where flight might not be properly disabled
     * after rank expiry or removal.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        // Only check if the player has flight enabled
        if (!player.getAllowFlight()) {
            return;
        }

        // Check if the player should be allowed to fly
        DonationManager manager = plugin.getDonationManager();
        DonationRank rank = manager.getPlayerRank(player.getName());

        // If no rank or rank doesn't have fly perk or rank is expired, disable flight
        if (rank == null || !rank.hasFly() || manager.isRankExpired(player.getName())) {
            player.setAllowFlight(false);
            player.sendMessage("§cYour flight has been disabled. Your rank does not include flight or has expired.");
        }
    }
}
