package com.server.aesthetics;

import cn.nukkit.Player;
import cn.nukkit.command.Command;
import cn.nukkit.command.CommandSender;
import cn.nukkit.utils.TextFormat;

/**
 * Handles the /spawn command.
 * Teleports the player to the server spawn point.
 */
public class SpawnCommand extends Command {

    private final ServerAestheticsPlugin plugin;

    public SpawnCommand(ServerAestheticsPlugin plugin) {
        super("spawn", "Teleport to spawn");
        this.plugin = plugin;
        this.setPermission("aesthetics.spawn");
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

        // Check if spawn is set
        if (plugin.getSpawnManager().getSpawnPosition() == null) {
            player.sendMessage(TextFormat.colorize("§cSpawn has not been set yet!"));
            return false;
        }

        // Teleport with warmup message
        player.sendMessage(TextFormat.colorize("§7Teleporting to spawn..."));

        // Teleport the player
        boolean success = plugin.getSpawnManager().teleportToSpawn(player);

        if (success) {
            // Play a teleport sound effect
            try {
                player.getLevel().addSound(player.getPosition(), cn.nukkit.level.Sound.MOB_SHULKER_TELEPORT);
            } catch (Exception ignored) {
                // Sound might not be available
            }
        }

        return success;
    }
}
