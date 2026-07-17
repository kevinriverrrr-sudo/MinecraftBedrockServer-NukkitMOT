package com.server.donationsystem;

import cn.nukkit.scheduler.Task;

/**
 * Scheduled task that periodically checks for expired donation ranks
 * and removes them from players.
 */
public class ExpiryChecker extends Task {

    private final DonationSystemPlugin plugin;

    public ExpiryChecker(DonationSystemPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onRun(int currentTick) {
        int expired = plugin.getDonationManager().checkAndRemoveExpiredRanks();
        if (expired > 0) {
            plugin.getLogger().info("ExpiryChecker: Removed " + expired + " expired donation rank(s).");
        }
    }
}
