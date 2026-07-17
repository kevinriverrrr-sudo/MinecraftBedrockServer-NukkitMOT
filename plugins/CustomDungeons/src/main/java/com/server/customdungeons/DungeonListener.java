package com.server.customdungeons;

import cn.nukkit.Player;
import cn.nukkit.entity.Entity;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.EventPriority;
import cn.nukkit.event.Listener;
import cn.nukkit.event.entity.EntityDamageByEntityEvent;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.event.entity.EntityDeathEvent;
import cn.nukkit.event.player.PlayerDeathEvent;
import cn.nukkit.event.player.PlayerQuitEvent;
import cn.nukkit.item.Item;

/**
 * Event listener for dungeon-related events.
 * Handles entity deaths (wave progression), player deaths in dungeon,
 * custom mob damage, and player disconnects.
 */
public class DungeonListener implements Listener {

    private final CustomDungeonsPlugin plugin;

    public DungeonListener(CustomDungeonsPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Handle entity death events for dungeon mob tracking.
     * When a dungeon mob dies, notify the dungeon instance for wave progression.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        Entity entity = event.getEntity();

        // Find which dungeon instance this mob belongs to
        DungeonInstance instance = findInstanceForMob(entity);
        if (instance == null) return;

        // Clear default drops for dungeon mobs (loot is handled separately)
        event.setDrops(Item.EMPTY_ARRAY);

        // Notify the dungeon instance about the mob death
        instance.onMobDeath(entity);
    }

    /**
     * Handle entity damage events to apply custom dungeon mob damage.
     * Also prevents dungeon mobs from taking damage from other dungeon mobs (friendly fire).
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        Entity entity = event.getEntity();

        // Handle dungeon mobs dealing custom damage
        if (event instanceof EntityDamageByEntityEvent) {
            EntityDamageByEntityEvent damageEvent = (EntityDamageByEntityEvent) event;
            Entity damager = damageEvent.getDamager();

            // Check if the damager is a dungeon mob
            DungeonInstance damagerInstance = findInstanceForMob(damager);
            if (damagerInstance != null) {
                // Get custom damage from the mob's named tag
                int customDamage = damagerInstance.getMobDamage(damager);
                if (customDamage > 0) {
                    // Apply custom damage to the target
                    event.setDamage(customDamage);
                }

                // Prevent dungeon mobs from damaging each other
                DungeonInstance targetInstance = findInstanceForMob(entity);
                if (targetInstance != null && targetInstance == damagerInstance) {
                    // Same dungeon - prevent friendly fire between dungeon mobs
                    // but allow boss to damage minions and vice versa
                    if (!damagerInstance.isBoss(damager) && !damagerInstance.isBoss(entity)) {
                        event.setCancelled(true);
                        return;
                    }
                }
            }

            // If the target is a player in a dungeon, allow the damage through
            if (entity instanceof Player) {
                Player player = (Player) entity;
                if (plugin.getDungeonManager().isInDungeon(player)) {
                    // Allow dungeon mobs to damage players normally
                    return;
                }
            }
        }

        // Prevent players from damaging other players in the same dungeon
        if (event instanceof EntityDamageByEntityEvent) {
            EntityDamageByEntityEvent damageEvent = (EntityDamageByEntityEvent) event;
            if (entity instanceof Player && damageEvent.getDamager() instanceof Player) {
                Player victim = (Player) entity;
                Player attacker = (Player) damageEvent.getDamager();

                // Check if both are in the same dungeon
                DungeonInstance victimInstance = plugin.getDungeonManager().getPlayerInstance(victim);
                DungeonInstance attackerInstance = plugin.getDungeonManager().getPlayerInstance(attacker);

                if (victimInstance != null && attackerInstance != null &&
                        victimInstance == attackerInstance) {
                    // Same dungeon - prevent PvP between party members
                    event.setCancelled(true);
                    attacker.sendMessage("§cYou cannot attack your party members in a dungeon!");
                }
            }
        }
    }

    /**
     * Handle player death events in dungeons.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();

        DungeonInstance instance = plugin.getDungeonManager().getPlayerInstance(player);
        if (instance == null) return;

        // Clear drops from player death in dungeon (items are managed)
        // but keep inventory if configured
        boolean keepInventory = plugin.getConfig().getBoolean("settings.respawn-in-dungeon", true);
        if (keepInventory) {
            event.setKeepInventory(true);
            event.setKeepExperience(true);
        }

        // Notify the dungeon instance about the player death
        boolean dungeonFailed = instance.onPlayerDeath(player);

        if (!dungeonFailed && keepInventory) {
            // Override death message
            event.setDeathMessage("§c" + player.getName() +
                    " §7was slain in " + instance.getTemplate().getDisplayName());
        }
    }

    /**
     * Handle player quit events.
     * Remove players from dungeons and parties when they disconnect.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        // Handle dungeon
        if (plugin.getDungeonManager().isInDungeon(player)) {
            plugin.getDungeonManager().handlePlayerQuit(player);
        }

        // Handle party
        plugin.getPartyManager().handlePlayerQuit(player);
    }

    /**
     * Find the DungeonInstance that a given entity belongs to.
     */
    private DungeonInstance findInstanceForMob(Entity entity) {
        for (DungeonInstance instance : plugin.getDungeonManager().getActiveInstances()) {
            if (instance.isDungeonMob(entity)) {
                return instance;
            }
        }
        return null;
    }
}
