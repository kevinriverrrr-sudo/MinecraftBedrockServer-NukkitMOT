package com.server.pvp;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.scheduler.Task;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages combat tagging to prevent combat logging.
 * When a player is in combat, they cannot disconnect without penalty.
 */
public class CombatTagManager {

    private final PvPSystemPlugin plugin;
    private final Map<UUID, Long> taggedPlayers;
    private final Map<UUID, String> taggedBy;
    private final int tagDuration;
    private final boolean killOnQuit;

    public CombatTagManager(PvPSystemPlugin plugin) {
        this.plugin = plugin;
        this.taggedPlayers = new ConcurrentHashMap<>();
        this.taggedBy = new ConcurrentHashMap<>();
        this.tagDuration = plugin.getConfig().getInt("pvp.combat-tag-duration", 15);
        this.killOnQuit = plugin.getConfig().getBoolean("pvp.combat-tag-kill-on-quit", true);

        // Start the tag expiry checker task
        startTagCheckTask();
    }

    /**
     * Start a repeating task that checks for expired combat tags
     * and updates action bar messages.
     */
    private void startTagCheckTask() {
        plugin.getServer().getScheduler().scheduleRepeatingTask(plugin, new Task() {
            @Override
            public void onRun(int currentTick) {
                long now = System.currentTimeMillis();
                long tagDurationMs = tagDuration * 1000L;

                Iterator<Map.Entry<UUID, Long>> it = taggedPlayers.entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry<UUID, Long> entry = it.next();
                    UUID playerId = entry.getKey();
                    long tagTime = entry.getValue();

                    if ((now - tagTime) > tagDurationMs) {
                        // Tag expired
                        it.remove();
                        taggedBy.remove(playerId);

                        Player player = Server.getInstance().getPlayer(playerId).orElse(null);
                        if (player != null) {
                            player.sendActionBar("§aYou are no longer in combat!");
                        }
                    } else {
                        // Still tagged - show remaining time
                        Player player = Server.getInstance().getPlayer(playerId).orElse(null);
                        if (player != null) {
                            int remaining = (int) Math.ceil((tagDurationMs - (now - tagTime)) / 1000.0);
                            player.sendActionBar("§cCombat Tag: §e" + remaining + "s");
                        }
                    }
                }
            }
        }, 10, true); // Check every 0.5 seconds
    }

    /**
     * Tag a player as being in combat.
     *
     * @param player the player to tag
     * @param attacker the player who attacked them
     */
    public void tagPlayer(Player player, Player attacker) {
        UUID playerId = player.getUniqueId();
        taggedPlayers.put(playerId, System.currentTimeMillis());
        taggedBy.put(playerId, attacker.getName());

        // Also tag the attacker
        UUID attackerId = attacker.getUniqueId();
        taggedPlayers.put(attackerId, System.currentTimeMillis());
        taggedBy.put(attackerId, player.getName());
    }

    /**
     * Check if a player is currently combat tagged.
     *
     * @param player the player to check
     * @return true if the player is combat tagged
     */
    public boolean isTagged(Player player) {
        return isTagged(player.getUniqueId());
    }

    /**
     * Check if a player is currently combat tagged.
     *
     * @param playerId the player's UUID
     * @return true if the player is combat tagged
     */
    public boolean isTagged(UUID playerId) {
        if (!taggedPlayers.containsKey(playerId)) return false;

        long tagTime = taggedPlayers.get(playerId);
        long elapsed = System.currentTimeMillis() - tagTime;
        if (elapsed > tagDuration * 1000L) {
            taggedPlayers.remove(playerId);
            taggedBy.remove(playerId);
            return false;
        }
        return true;
    }

    /**
     * Handle a player disconnecting while combat tagged.
     * If killOnQuit is enabled, the player will be killed.
     *
     * @param player the player who disconnected
     */
    public void handleDisconnect(Player player) {
        UUID playerId = player.getUniqueId();

        if (isTagged(playerId) && killOnQuit) {
            // Kill the player for combat logging
            String attackerName = taggedBy.get(playerId);

            // Record the death as a kill for the attacker
            PvPStats victimStats = plugin.getStatsManager().getStats(player.getName());
            victimStats.addDeath();
            victimStats.resetKillStreak();

            if (attackerName != null) {
                PvPStats attackerStats = plugin.getStatsManager().getStats(attackerName);
                attackerStats.addKill();
                attackerStats.incrementKillStreak();

                // Update ELO
                plugin.getStatsManager().updateElo(attackerName, player.getName());

                // Announce the combat log
                String message = "§c" + player.getName() + " §7combat logged while fighting §c" + attackerName + "§7!";
                for (Player p : Server.getInstance().getOnlinePlayers().values()) {
                    if (!p.getUniqueId().equals(playerId)) {
                        p.sendMessage(message);
                    }
                }
            } else {
                String message = "§c" + player.getName() + " §7combat logged!";
                for (Player p : Server.getInstance().getOnlinePlayers().values()) {
                    if (!p.getUniqueId().equals(playerId)) {
                        p.sendMessage(message);
                    }
                }
            }

            // Kill the player
            player.setHealth(0);

            // Save stats
            plugin.getStatsManager().saveStats();
        }

        // Remove tag
        taggedPlayers.remove(playerId);
        taggedBy.remove(playerId);
    }

    /**
     * Get the remaining combat tag duration for a player.
     *
     * @param player the player
     * @return remaining seconds, or 0 if not tagged
     */
    public int getRemainingTagTime(Player player) {
        UUID playerId = player.getUniqueId();
        if (!taggedPlayers.containsKey(playerId)) return 0;

        long tagTime = taggedPlayers.get(playerId);
        long elapsed = System.currentTimeMillis() - tagTime;
        long remaining = (tagDuration * 1000L) - elapsed;

        if (remaining <= 0) return 0;
        return (int) Math.ceil(remaining / 1000.0);
    }

    /**
     * Get the name of the player who tagged this player.
     *
     * @param player the tagged player
     * @return the attacker's name, or null
     */
    public String getTaggedBy(Player player) {
        return taggedBy.get(player.getUniqueId());
    }

    /**
     * Remove a player's combat tag.
     *
     * @param player the player to untag
     */
    public void untagPlayer(Player player) {
        UUID playerId = player.getUniqueId();
        taggedPlayers.remove(playerId);
        taggedBy.remove(playerId);
    }

    /**
     * Clean up when the plugin is disabled.
     */
    public void cleanup() {
        taggedPlayers.clear();
        taggedBy.clear();
    }

    public int getTagDuration() {
        return tagDuration;
    }

    public boolean isKillOnQuit() {
        return killOnQuit;
    }
}
