package com.server.aesthetics;

import cn.nukkit.Player;
import cn.nukkit.scheduler.Task;

/**
 * Scheduled task that updates all player scoreboards periodically.
 * Handles title animation frame advancement and line content refresh.
 */
public class ScoreboardUpdateTask extends Task {

    private final ServerAestheticsPlugin plugin;

    public ScoreboardUpdateTask(ServerAestheticsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onRun(int currentTick) {
        if (!plugin.getConfig().getBoolean("scoreboard.enabled", true)) {
            return;
        }

        // Advance title animation frame if animation is enabled
        if (plugin.getConfig().getBoolean("scoreboard.title-animation", true)) {
            plugin.getScoreboardManager().advanceTitleFrame();
        }

        // Update scoreboard for each online player
        for (Player player : plugin.getServer().getOnlinePlayers().values()) {
            try {
                plugin.getScoreboardManager().updateScoreboard(player);
            } catch (Exception e) {
                plugin.getLogger().debug("Error updating scoreboard for " + player.getName() + ": " + e.getMessage());
            }
        }
    }
}
