package com.server.aesthetics;

import cn.nukkit.scheduler.Task;

/**
 * Scheduled task that handles tab list header/footer animation
 * and periodic tab list refresh for all online players.
 */
public class AnimationTask extends Task {

    private final ServerAestheticsPlugin plugin;

    public AnimationTask(ServerAestheticsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onRun(int currentTick) {
        if (!plugin.getConfig().getBoolean("tab.enabled", true)) {
            return;
        }

        // Update all players' tab displays
        // This includes advancing the animation frame and refreshing header/footer
        try {
            plugin.getTabManager().updateAllPlayers();
        } catch (Exception e) {
            plugin.getLogger().debug("Error updating tab list: " + e.getMessage());
        }
    }
}
