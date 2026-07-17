package com.server.pvp;

import cn.nukkit.command.Command;
import cn.nukkit.command.CommandSender;

/**
 * Handles the /pvpstats and /pvpleaderboard commands.
 */
public class StatsCommand extends Command {

    private final PvPSystemPlugin plugin;
    private final String type;

    /**
     * Create a stats command.
     *
     * @param plugin the plugin instance
     * @param type   "stats" for /pvpstats or "leaderboard" for /pvpleaderboard
     */
    public StatsCommand(PvPSystemPlugin plugin, String type) {
        super(
                type.equals("stats") ? "pvpstats" : "pvpleaderboard",
                type.equals("stats") ? "View PvP stats" : "PvP leaderboard",
                type.equals("stats") ? "/pvpstats [player]" : "/pvpleaderboard <kills|kd|elo>"
        );
        this.plugin = plugin;
        this.type = type;
    }

    @Override
    public boolean execute(CommandSender sender, String commandLabel, String[] args) {
        if (type.equals("stats")) {
            return executeStats(sender, args);
        } else {
            return executeLeaderboard(sender, args);
        }
    }

    /**
     * Execute the /pvpstats command.
     */
    private boolean executeStats(CommandSender sender, String[] args) {
        String targetName;

        if (args.length >= 1) {
            targetName = args[0];
        } else if (sender instanceof cn.nukkit.Player) {
            targetName = sender.getName();
        } else {
            sender.sendMessage("§cPlease specify a player name!");
            return true;
        }

        PvPStats stats = plugin.getStatsManager().getStats(targetName);
        sender.sendMessage(stats.getFormattedStats());
        return true;
    }

    /**
     * Execute the /pvpleaderboard command.
     */
    private boolean executeLeaderboard(CommandSender sender, String[] args) {
        String leaderboardType;

        if (args.length >= 1) {
            leaderboardType = args[0].toLowerCase();
        } else {
            leaderboardType = "kills";
        }

        // Validate leaderboard type
        if (!leaderboardType.equals("kills") && !leaderboardType.equals("kd") && !leaderboardType.equals("elo")) {
            sender.sendMessage("§cInvalid leaderboard type! Use: §ekills§c, §ekd§c, or §eelo");
            return true;
        }

        String leaderboard = plugin.getStatsManager().formatLeaderboard(leaderboardType, 10);
        sender.sendMessage(leaderboard);
        return true;
    }
}
