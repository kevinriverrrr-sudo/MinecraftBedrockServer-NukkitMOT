package com.server.aesthetics;

import cn.nukkit.Player;
import cn.nukkit.command.Command;
import cn.nukkit.command.CommandSender;
import cn.nukkit.utils.TextFormat;

/**
 * Handles the /scoreboard command.
 * Allows players to toggle their scoreboard visibility.
 */
public class ScoreboardCommand extends Command {

    private final ServerAestheticsPlugin plugin;

    public ScoreboardCommand(ServerAestheticsPlugin plugin) {
        super("scoreboard", "Toggle scoreboard visibility", "/scoreboard");
        this.plugin = plugin;
        this.setPermission("aesthetics.scoreboard.toggle");
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (!testPermission(sender)) {
            sender.sendMessage(TextFormat.RED + "You don't have permission to use this command.");
            return false;
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage(TextFormat.RED + "This command can only be used in-game.");
            return false;
        }

        Player player = (Player) sender;

        // Toggle scoreboard visibility
        boolean nowVisible = plugin.getScoreboardManager().toggleScoreboard(player);

        if (nowVisible) {
            player.sendMessage(TextFormat.colorize("§aScoreboard enabled!"));
        } else {
            player.sendMessage(TextFormat.colorize("§cScoreboard disabled!"));
        }

        return true;
    }
}
