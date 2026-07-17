package com.server.kitsystem;

import cn.nukkit.Player;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.EventPriority;
import cn.nukkit.event.Listener;
import cn.nukkit.event.player.PlayerJoinEvent;
import cn.nukkit.utils.TextFormat;

import java.util.UUID;

/**
 * Event listener for the KitSystem plugin.
 * Handles PlayerJoinEvent for auto-kit on first join.
 */
public class KitListener implements Listener {

    private final KitSystemPlugin plugin;

    public KitListener(KitSystemPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Handle player join events.
     * If auto-kit-on-first-join is enabled, give the configured first-join kit
     * to players who have never played before.
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        if (!plugin.getConfig().getBoolean("auto-kit-on-first-join", true)) {
            return;
        }

        // Check if this is the player's first join
        // Nukkit: hasPlayedBefore() returns false for new players
        if (player.hasPlayedBefore()) {
            return;
        }

        String firstJoinKit = plugin.getConfig().getString("first-join-kit", "starter");
        KitManager kitManager = plugin.getKitManager();

        Kit kit = kitManager.getKit(firstJoinKit);
        if (kit == null) {
            plugin.getLogger().warning("First-join kit '" + firstJoinKit + "' not found in config!");
            return;
        }

        // Check permission
        if (!kit.getPermission().isEmpty() && !player.hasPermission(kit.getPermission())) {
            plugin.getLogger().info("Player " + player.getName() + " doesn't have permission for first-join kit '" +
                    firstJoinKit + "' - skipping auto-kit.");
            return;
        }

        UUID uuid = player.getUniqueId();

        // Check if already claimed (shouldn't be possible for first join, but check anyway)
        if (kitManager.getCooldownManager().hasClaimed(uuid, firstJoinKit)) {
            return;
        }

        // Give the kit with a slight delay to ensure the player is fully initialized
        plugin.getServer().getScheduler().scheduleDelayedTask(plugin, () -> {
            if (!player.isOnline()) return;

            KitCommand kitCommand = plugin.getKitCommand();
            kitCommand.giveKitToPlayer(player, kit);

            // Record the claim
            kitManager.recordClaim(uuid, firstJoinKit);

            player.sendMessage(TextFormat.GREEN + "Welcome! You've received the " +
                    kit.getDisplayName() + TextFormat.GREEN + " kit!");
        }, 20); // 1 second delay (20 ticks)
    }
}
