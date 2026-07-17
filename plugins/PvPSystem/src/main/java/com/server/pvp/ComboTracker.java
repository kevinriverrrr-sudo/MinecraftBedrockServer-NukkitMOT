package com.server.pvp;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.level.Sound;
import cn.nukkit.scheduler.Task;
import cn.nukkit.utils.Config;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks hit combos for players in PvP.
 * A combo increases when a player hits another without being hit back.
 * Combos reset when: player gets hit, player misses (timeout), player dies.
 */
public class ComboTracker {

    private final PvPSystemPlugin plugin;
    private final Map<UUID, Integer> comboCounts;
    private final Map<UUID, Long> lastHitTimes;
    private final Map<UUID, UUID> lastHitTarget;
    private final int comboTimeout;
    private final String comboMessage;
    private final Map<Integer, Map<String, String>> milestones;
    private boolean enabled;

    public ComboTracker(PvPSystemPlugin plugin) {
        this.plugin = plugin;
        this.comboCounts = new ConcurrentHashMap<>();
        this.lastHitTimes = new ConcurrentHashMap<>();
        this.lastHitTarget = new ConcurrentHashMap<>();
        this.comboTimeout = plugin.getConfig().getInt("combo.timeout", 3);
        this.comboMessage = plugin.getConfig().getString("combo.message", "§eCombo: §6{combo}§e hits!");
        this.enabled = plugin.getConfig().getBoolean("combo.enabled", true);

        // Load milestones
        this.milestones = new LinkedHashMap<>();
        Config config = plugin.getConfig();
        if (config.exists("combo.milestones")) {
            Map<String, Object> milestoneMap = config.getSection("combo.milestones").getAllMap();
            for (Map.Entry<String, Object> entry : milestoneMap.entrySet()) {
                try {
                    int count = Integer.parseInt(entry.getKey());
                    if (entry.getValue() instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, String> milestoneData = new LinkedHashMap<>();
                        Map<String, Object> raw = (Map<String, Object>) entry.getValue();
                        for (Map.Entry<String, Object> e : raw.entrySet()) {
                            milestoneData.put(e.getKey(), String.valueOf(e.getValue()));
                        }
                        milestones.put(count, milestoneData);
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }

        // Start the timeout checker task
        startTimeoutTask();
    }

    /**
     * Start a repeating task that checks for combo timeouts.
     */
    private void startTimeoutTask() {
        plugin.getServer().getScheduler().scheduleRepeatingTask(plugin, new Task() {
            @Override
            public void onRun(int currentTick) {
                if (!enabled) return;

                long now = System.currentTimeMillis();
                long timeoutMs = comboTimeout * 1000L;

                for (Map.Entry<UUID, Long> entry : lastHitTimes.entrySet()) {
                    UUID playerId = entry.getKey();
                    long lastHit = entry.getValue();

                    if ((now - lastHit) > timeoutMs) {
                        // Combo timed out
                        int combo = comboCounts.getOrDefault(playerId, 0);
                        if (combo > 0) {
                            comboCounts.put(playerId, 0);
                            lastHitTimes.remove(playerId);
                            lastHitTarget.remove(playerId);

                            Player player = Server.getInstance().getPlayer(playerId).orElse(null);
                            if (player != null) {
                                player.sendActionBar("§7Combo expired");
                            }
                        }
                    }
                }
            }
        }, 20, true); // Check every second
    }

    /**
     * Called when a player hits another player.
     *
     * @param attacker the player who attacked
     * @param victim   the player who was hit
     */
    public void onHit(Player attacker, Player victim) {
        if (!enabled) return;

        UUID attackerId = attacker.getUniqueId();
        UUID victimId = victim.getUniqueId();

        // Increment attacker's combo
        int currentCombo = comboCounts.getOrDefault(attackerId, 0) + 1;
        comboCounts.put(attackerId, currentCombo);
        lastHitTimes.put(attackerId, System.currentTimeMillis());
        lastHitTarget.put(attackerId, victimId);

        // Reset victim's combo (they got hit)
        int victimCombo = comboCounts.getOrDefault(victimId, 0);
        if (victimCombo > 0) {
            comboCounts.put(victimId, 0);
            lastHitTimes.remove(victimId);
            lastHitTarget.remove(victimId);
        }

        // Display combo message
        String message = comboMessage.replace("{combo}", String.valueOf(currentCombo));
        attacker.sendActionBar(message);

        // Check for milestone
        if (milestones.containsKey(currentCombo)) {
            Map<String, String> milestone = milestones.get(currentCombo);
            String broadcastMsg = milestone.getOrDefault("message", "")
                    .replace("{player}", attacker.getName())
                    .replace("{combo}", String.valueOf(currentCombo));
            String soundName = milestone.getOrDefault("sound", "");

            // Broadcast milestone message
            for (Player p : Server.getInstance().getOnlinePlayers().values()) {
                p.sendMessage(broadcastMsg);
                if (!soundName.isEmpty()) {
                    try {
                        Sound sound = Sound.valueOf(soundName.toUpperCase().replace(".", "_").replace("RANDOM_", ""));
                        p.getLevel().addSound(p.getPosition(), sound);
                    } catch (IllegalArgumentException e) {
                        // Try as a raw string sound
                        try {
                            p.getLevel().addSound(p.getPosition(), soundName);
                        } catch (Exception ignored) {
                        }
                    }
                }
            }
        }

        // Update highest combo in stats
        plugin.getStatsManager().updateHighestCombo(attacker.getName(), currentCombo);
    }

    /**
     * Called when a player dies.
     *
     * @param player the player who died
     */
    public void onDeath(Player player) {
        UUID playerId = player.getUniqueId();
        comboCounts.remove(playerId);
        lastHitTimes.remove(playerId);
        lastHitTarget.remove(playerId);
    }

    /**
     * Get the current combo count for a player.
     *
     * @param player the player
     * @return the current combo count
     */
    public int getCombo(Player player) {
        return comboCounts.getOrDefault(player.getUniqueId(), 0);
    }

    /**
     * Get the last player that was hit by the given attacker.
     *
     * @param attacker the attacking player
     * @return the victim's UUID, or null
     */
    public UUID getLastHitTarget(Player attacker) {
        return lastHitTarget.get(attacker.getUniqueId());
    }

    /**
     * Reset a player's combo.
     *
     * @param player the player whose combo to reset
     */
    public void resetCombo(Player player) {
        UUID playerId = player.getUniqueId();
        comboCounts.remove(playerId);
        lastHitTimes.remove(playerId);
        lastHitTarget.remove(playerId);
    }

    /**
     * Clean up when the plugin is disabled.
     */
    public void cleanup() {
        comboCounts.clear();
        lastHitTimes.clear();
        lastHitTarget.clear();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
