package com.server.aesthetics;

import cn.nukkit.Player;
import cn.nukkit.command.Command;
import cn.nukkit.command.CommandSender;
import cn.nukkit.utils.TextFormat;

/**
 * Handles the /setspawn command.
 * Sets the server spawn point to the player's current location.
 * Requires aesthetics.admin permission.
 */
public class SetSpawnCommand extends Command {

    private final ServerAestheticsPlugin plugin;

    public SetSpawnCommand(ServerAestheticsPlugin plugin) {
        super("setspawn", "Set spawn point");
        this.plugin = plugin;
        this.setPermission("aesthetics.admin");
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

        // Set spawn to player's current position
        plugin.getSpawnManager().setSpawnLocation(player.getPosition());

        // Notify the player
        player.sendMessage(TextFormat.colorize("§a§lSpawn set! §r§7Location: " +
            String.format("§f%.1f, %.1f, %.1f §7in §f%s",
                player.getX(), player.getY(), player.getZ(),
                player.getLevel().getName())));

        // Notify other admins
        for (Player online : plugin.getServer().getOnlinePlayers().values()) {
            if (online.hasPermission("aesthetics.admin") && !online.equals(player)) {
                online.sendMessage(TextFormat.colorize("§6[Aesthetics] §7Spawn set by §f" +
                    player.getName() + " §7at §f" +
                    String.format("%.1f, %.1f, %.1f", player.getX(), player.getY(), player.getZ())));
            }
        }

        // Play confirmation sound
        try {
            player.getLevel().addSound(player.getPosition(), cn.nukkit.level.Sound.NOTE_PLING);
        } catch (Exception ignored) {
            // Sound might not be available
        }

        return true;
    }
}
