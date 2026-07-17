package com.server.pvp;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.potion.Effect;
import cn.nukkit.utils.Config;

import java.util.*;

/**
 * Tracks kill streaks for players in PvP.
 * Kill streak milestones trigger server-wide announcements and rewards.
 */
public class KillStreakTracker {

    private final PvPSystemPlugin plugin;
    private final Map<Integer, Map<String, String>> milestones;
    private boolean enabled;

    public KillStreakTracker(PvPSystemPlugin plugin) {
        this.plugin = plugin;
        this.enabled = plugin.getConfig().getBoolean("killstreak.enabled", true);
        this.milestones = new LinkedHashMap<>();

        // Load milestones from config
        loadMilestones();
    }

    /**
     * Load kill streak milestones from config.
     */
    private void loadMilestones() {
        Config config = plugin.getConfig();
        if (config.exists("killstreak.milestones")) {
            Map<String, Object> milestoneMap = config.getSection("killstreak.milestones").getAllMap();
            for (Map.Entry<String, Object> entry : milestoneMap.entrySet()) {
                try {
                    int count = Integer.parseInt(entry.getKey());
                    if (entry.getValue() instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> raw = (Map<String, Object>) entry.getValue();
                        Map<String, String> milestoneData = new LinkedHashMap<>();
                        for (Map.Entry<String, Object> e : raw.entrySet()) {
                            milestoneData.put(e.getKey(), String.valueOf(e.getValue()));
                        }
                        milestones.put(count, milestoneData);
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }
    }

    /**
     * Called when a player gets a kill.
     *
     * @param killer the player who killed
     * @param victim the player who was killed
     */
    public void onKill(Player killer, Player victim) {
        if (!enabled) return;

        PvPStats stats = plugin.getStatsManager().getStats(killer.getName());
        stats.incrementKillStreak();
        int streak = stats.getCurrentKillStreak();

        // Check for milestone announcement
        if (milestones.containsKey(streak)) {
            Map<String, String> milestone = milestones.get(streak);
            String title = milestone.getOrDefault("title", "§cKill Streak!");
            String message = milestone.getOrDefault("message", "")
                    .replace("{player}", killer.getName())
                    .replace("{count}", String.valueOf(streak));

            // Broadcast the kill streak
            for (Player p : Server.getInstance().getOnlinePlayers().values()) {
                p.sendMessage(message);
            }

            // Show title to the killer
            killer.sendTitle(title, "§7" + streak + " kill streak", 10, 40, 20);

            // Give kill streak rewards
            giveRewards(killer, streak);
        }
    }

    /**
     * Called when a player dies.
     *
     * @param victim the player who died
     * @param killer the player who killed them (may be null)
     */
    public void onDeath(Player victim, Player killer) {
        if (!enabled) return;

        PvPStats victimStats = plugin.getStatsManager().getStats(victim.getName());
        int streak = victimStats.getCurrentKillStreak();

        if (streak >= 3 && killer != null) {
            // Announce that the kill streak was ended
            String message = plugin.getConfig().getString("death-messages.killstreak",
                    "§6{attacker} ended {victim}'s {count} kill streak!")
                    .replace("{attacker}", killer.getName())
                    .replace("{victim}", victim.getName())
                    .replace("{count}", String.valueOf(streak));

            for (Player p : Server.getInstance().getOnlinePlayers().values()) {
                p.sendMessage(message);
            }
        }

        victimStats.resetKillStreak();
    }

    /**
     * Give rewards to a player for reaching a kill streak milestone.
     *
     * @param player the player
     * @param streak the kill streak count
     */
    private void giveRewards(Player player, int streak) {
        // Speed effect for kill streaks
        if (streak >= 3) {
            Effect speed = Effect.getEffect(Effect.SPEED);
            speed.setDuration(20 * 10); // 10 seconds
            speed.setAmplifier(1);
            player.addEffect(speed);
        }

        if (streak >= 5) {
            // Strength effect for 5+ kill streak
            Effect strength = Effect.getEffect(Effect.STRENGTH);
            strength.setDuration(20 * 8); // 8 seconds
            strength.setAmplifier(0);
            player.addEffect(strength);

            // Give golden apple
            player.getInventory().addItem(cn.nukkit.item.Item.get(cn.nukkit.item.ItemID.GOLDEN_APPLE, 0, 1));
            player.sendMessage("§6You received a Golden Apple for your kill streak!");
        }

        if (streak >= 7) {
            // Resistance effect for 7+ kill streak
            Effect resistance = Effect.getEffect(Effect.RESISTANCE);
            resistance.setDuration(20 * 5); // 5 seconds
            resistance.setAmplifier(0);
            player.addEffect(resistance);

            // Give enchanted golden apple for 7+ kill streak
            player.getInventory().addItem(cn.nukkit.item.Item.get(cn.nukkit.item.ItemID.GOLDEN_APPLE_ENCHANTED, 0, 1));
            player.sendMessage("§6You received an Enchanted Golden Apple for your kill streak!");
        }

        if (streak >= 10) {
            // Regeneration for godlike
            Effect regen = Effect.getEffect(Effect.REGENERATION);
            regen.setDuration(20 * 10); // 10 seconds
            regen.setAmplifier(1);
            player.addEffect(regen);

            // Give diamond
            player.getInventory().addItem(cn.nukkit.item.Item.get(cn.nukkit.item.ItemID.DIAMOND, 0, 1));
            player.sendMessage("§bYou received a Diamond for your GODLIKE kill streak!");
        }
    }

    /**
     * Get the kill streak title for a given count.
     *
     * @param count the kill streak count
     * @return the title string, or null if not a milestone
     */
    public String getStreakTitle(int count) {
        if (milestones.containsKey(count)) {
            return milestones.get(count).getOrDefault("title", "");
        }
        return null;
    }

    /**
     * Get a player's current kill streak.
     *
     * @param player the player
     * @return the current kill streak count
     */
    public int getKillStreak(Player player) {
        PvPStats stats = plugin.getStatsManager().getStats(player.getName());
        return stats.getCurrentKillStreak();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Map<Integer, Map<String, String>> getMilestones() {
        return milestones;
    }
}
