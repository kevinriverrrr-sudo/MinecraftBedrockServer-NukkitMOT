package com.server.pvp;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.EventPriority;
import cn.nukkit.event.Listener;
import cn.nukkit.event.entity.EntityDamageByEntityEvent;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.event.player.PlayerDeathEvent;
import cn.nukkit.event.player.PlayerJoinEvent;
import cn.nukkit.event.player.PlayerMoveEvent;
import cn.nukkit.event.player.PlayerQuitEvent;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemID;
import cn.nukkit.utils.Config;

import java.util.Map;

/**
 * Event listener for PvP-related events.
 * Handles damage, death, join, quit, and move events.
 */
public class PvPListener implements Listener {

    private final PvPSystemPlugin plugin;

    public PvPListener(PvPSystemPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Handle player vs player damage events.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player)) return;
        if (!(event.getEntity() instanceof Player)) return;

        Player attacker = (Player) event.getDamager();
        Player victim = (Player) event.getEntity();

        // Check spawn protection
        if (isInSpawnProtection(victim)) {
            event.setCancelled(true);
            attacker.sendMessage("§cPvP is not allowed near spawn!");
            return;
        }

        // Check spawn protection for attacker
        if (isInSpawnProtection(attacker)) {
            event.setCancelled(true);
            attacker.sendMessage("§cYou cannot attack from the spawn area!");
            return;
        }

        // Check new player protection for victim
        if (isNewPlayerProtected(victim)) {
            event.setCancelled(true);
            attacker.sendMessage("§cThat player has new player protection!");
            return;
        }

        // Check new player protection for attacker
        if (isNewPlayerProtected(attacker)) {
            event.setCancelled(true);
            attacker.sendMessage("§cYou have new player protection and cannot attack!");
            return;
        }

        // If in a match, don't apply extra restrictions
        ArenaMatch match = plugin.getArenaManager().getPlayerMatch(attacker);
        if (match != null && match.getState() == ArenaMatch.MatchState.FIGHTING) {
            // Only allow damage between players in the same match
            if (!match.hasPlayer(victim)) {
                event.setCancelled(true);
                return;
            }

            // Allow PvP in match
            plugin.getComboTracker().onHit(attacker, victim);
            plugin.getCombatTagManager().tagPlayer(attacker, victim);
            return;
        }

        // If attacker is in a match but victim is not, block
        if (match != null) {
            event.setCancelled(true);
            return;
        }

        // If victim is in a match but attacker is not, block
        ArenaMatch victimMatch = plugin.getArenaManager().getPlayerMatch(victim);
        if (victimMatch != null) {
            event.setCancelled(true);
            attacker.sendMessage("§cThat player is currently in a match!");
            return;
        }

        // Normal PvP - track combo and combat tag
        plugin.getComboTracker().onHit(attacker, victim);
        plugin.getCombatTagManager().tagPlayer(attacker, victim);
    }

    /**
     * Handle player death events.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();

        // Check if this was PvP-related
        EntityDamageEvent lastDamage = victim.getLastDamageCause();
        Player killer = null;

        if (lastDamage instanceof EntityDamageByEntityEvent) {
            EntityDamageByEntityEvent damageEvent = (EntityDamageByEntityEvent) lastDamage;
            if (damageEvent.getDamager() instanceof Player) {
                killer = (Player) damageEvent.getDamager();
            }
        }

        // Handle combo tracker
        plugin.getComboTracker().onDeath(victim);

        // Handle kill streak tracker
        if (killer != null) {
            plugin.getKillStreakTracker().onKill(killer, victim);
            plugin.getKillStreakTracker().onDeath(victim, killer);
        } else {
            plugin.getKillStreakTracker().onDeath(victim, null);
        }

        // Record stats
        if (killer != null) {
            plugin.getStatsManager().recordKill(killer.getName(), victim.getName());

            // Custom death messages
            String deathMessage = getCustomDeathMessage(killer, victim);
            if (deathMessage != null) {
                event.setDeathMessage(deathMessage);
            }

            // Handle arena match death
            if (plugin.getArenaManager().isInMatch(victim)) {
                plugin.getArenaManager().handleMatchDeath(victim, killer);
            }
        } else {
            // Non-PvP death - just record the death
            PvPStats victimStats = plugin.getStatsManager().getStats(victim.getName());
            victimStats.addDeath();
            victimStats.resetKillStreak();

            if (plugin.getArenaManager().isInMatch(victim)) {
                plugin.getArenaManager().handleMatchDeath(victim, null);
            }
        }

        // Remove combat tag
        plugin.getCombatTagManager().untagPlayer(victim);

        // Save stats
        plugin.getStatsManager().saveStats();
    }

    /**
     * Handle player quit events (combat log prevention).
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        // Handle combat tag
        plugin.getCombatTagManager().handleDisconnect(player);

        // Handle arena match
        if (plugin.getArenaManager().isInMatch(player)) {
            plugin.getArenaManager().handlePlayerLeaveMatch(player);
        }

        // Handle spectating
        if (plugin.getArenaManager().isSpectating(player)) {
            plugin.getArenaManager().stopSpectating(player);
        }

        // Leave queue
        if (plugin.getArenaManager().isInQueue(player)) {
            plugin.getArenaManager().leaveQueue(player);
        }

        // Clean up combo tracker
        plugin.getComboTracker().onDeath(player);

        // Save stats
        plugin.getStatsManager().saveStats();
    }

    /**
     * Handle player join events.
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Initialize stats for the player
        PvPStats stats = plugin.getStatsManager().getStats(player.getName());
        stats.setPlayerName(player.getName());

        // Check new player protection
        int protectionSeconds = plugin.getConfig().getInt("pvp.new-player-protection", 1800);
        if (protectionSeconds > 0 && stats.isNewPlayer(protectionSeconds * 1000L)) {
            long remaining = (protectionSeconds * 1000L) - (System.currentTimeMillis() - stats.getFirstJoinTime());
            int remainingSeconds = (int) (remaining / 1000);
            int minutes = remainingSeconds / 60;
            int seconds = remainingSeconds % 60;
            player.sendMessage("§aYou have new player protection for §e" + minutes + "m " + seconds + "s§a!");
        }
    }

    /**
     * Handle player move events (prevent leaving arena during match).
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        // Check if player is in a match
        ArenaMatch match = plugin.getArenaManager().getPlayerMatch(player);
        if (match == null) return;

        // Only check during fighting state
        if (match.getState() != ArenaMatch.MatchState.FIGHTING) return;

        // Check if player is outside arena boundaries
        Arena arena = match.getArena();
        if (arena.getPos1() != null && arena.getPos2() != null) {
            if (!arena.isInside(event.getTo())) {
                // Push player back inside
                event.setCancelled(true);
                player.sendMessage("§cYou cannot leave the arena during a match!");
            }
        }
    }

    // --- Helper Methods ---

    /**
     * Check if a position is within spawn protection.
     */
    private boolean isInSpawnProtection(Player player) {
        int radius = plugin.getConfig().getInt("pvp.spawn-protection", 30);
        if (radius <= 0) return false;

        cn.nukkit.level.Position spawn = player.getLevel().getSpawnLocation();
        cn.nukkit.level.Position playerPos = player.getPosition();

        double distance = Math.sqrt(
                Math.pow(playerPos.getX() - spawn.getX(), 2) +
                Math.pow(playerPos.getZ() - spawn.getZ(), 2)
        );

        return distance <= radius;
    }

    /**
     * Check if a player has new player protection.
     */
    private boolean isNewPlayerProtected(Player player) {
        int protectionSeconds = plugin.getConfig().getInt("pvp.new-player-protection", 1800);
        if (protectionSeconds <= 0) return false;

        PvPStats stats = plugin.getStatsManager().getStats(player.getName());
        return stats.isNewPlayer(protectionSeconds * 1000L);
    }

    /**
     * Get a custom death message based on weapon type, combo, or kill streak.
     */
    private String getCustomDeathMessage(Player killer, Player victim) {
        Config config = plugin.getConfig();

        // Check for combo kill
        int combo = plugin.getComboTracker().getCombo(killer);
        if (combo >= 5) {
            String comboMsg = config.getString("death-messages.combo",
                    "§d{victim} was combo'd into oblivion by {attacker} ({combo} hit combo)");
            return comboMsg
                    .replace("{victim}", victim.getName())
                    .replace("{attacker}", killer.getName())
                    .replace("{combo}", String.valueOf(combo));
        }

        // Check for kill streak break
        PvPStats victimStats = plugin.getStatsManager().getStats(victim.getName());
        if (victimStats.getCurrentKillStreak() >= 3) {
            String streakMsg = config.getString("death-messages.killstreak",
                    "§6{attacker} ended {victim}'s {count} kill streak!");
            return streakMsg
                    .replace("{victim}", victim.getName())
                    .replace("{attacker}", killer.getName())
                    .replace("{count}", String.valueOf(victimStats.getCurrentKillStreak()));
        }

        // Check weapon type
        Item weapon = killer.getInventory().getItemInHand();
        if (weapon != null && weapon.getId() != 0) {
            String weaponKey = getWeaponKey(weapon);
            if (config.exists("death-messages." + weaponKey)) {
                String weaponMsg = config.getString("death-messages." + weaponKey, "");
                if (!weaponMsg.isEmpty()) {
                    return weaponMsg
                            .replace("{victim}", victim.getName())
                            .replace("{attacker}", killer.getName());
                }
            }
        }

        // Default message for fist kills
        if (weapon == null || weapon.getId() == 0) {
            String fistMsg = config.getString("death-messages.fists", "");
            if (!fistMsg.isEmpty()) {
                return fistMsg
                        .replace("{victim}", victim.getName())
                        .replace("{attacker}", killer.getName());
            }
        }

        // Default death message
        String defaultMsg = config.getString("death-messages.default",
                "§c{victim} was slain by {attacker}");
        return defaultMsg
                .replace("{victim}", victim.getName())
                .replace("{attacker}", killer.getName());
    }

    /**
     * Get a config key for the weapon type.
     */
    private String getWeaponKey(Item weapon) {
        int id = weapon.getId();
        if (id == ItemID.DIAMOND_SWORD) return "diamond_sword";
        if (id == ItemID.IRON_SWORD) return "iron_sword";
        if (id == ItemID.GOLDEN_SWORD) return "iron_sword";
        if (id == ItemID.STONE_SWORD) return "iron_sword";
        if (id == ItemID.WOODEN_SWORD) return "iron_sword";
        if (id == ItemID.BOW) return "bow";
        if (id == ItemID.CROSSBOW) return "bow";
        if (id == ItemID.TRIDENT) return "bow";
        if (id == ItemID.DIAMOND_AXE) return "diamond_sword";
        if (id == ItemID.IRON_AXE) return "iron_sword";
        return "default";
    }
}
