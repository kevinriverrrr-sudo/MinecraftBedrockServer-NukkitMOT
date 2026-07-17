package com.server.aesthetics;

import cn.nukkit.Player;
import cn.nukkit.command.Command;
import cn.nukkit.command.CommandSender;
import cn.nukkit.utils.TextFormat;

import java.util.List;

/**
 * Handles the /serverinfo command.
 * Displays server information including online count, TPS, uptime, etc.
 */
public class ServerInfoCommand extends Command {

    private final ServerAestheticsPlugin plugin;

    public ServerInfoCommand(ServerAestheticsPlugin plugin) {
        super("serverinfo", "Server information", "/serverinfo", new String[]{"si"});
        this.plugin = plugin;
        this.setPermission("aesthetics.serverinfo");
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (!testPermission(sender)) {
            sender.sendMessage(TextFormat.RED + "You don't have permission to use this command.");
            return false;
        }

        // Get server info lines from config
        List<String> lines = plugin.getConfig().getStringList("serverinfo.lines");
        if (lines == null || lines.isEmpty()) {
            // Default lines if not configured
            sendDefaultServerInfo(sender);
            return true;
        }

        // Process and send each line
        Player player = (sender instanceof Player) ? (Player) sender : null;
        for (String line : lines) {
            String processed = plugin.replacePlaceholders(line, player);
            processed = TextFormat.colorize(processed);
            sender.sendMessage(processed);
        }

        return true;
    }

    /**
     * Sends default server info when config lines are not set.
     */
    private void sendDefaultServerInfo(CommandSender sender) {
        Player player = (sender instanceof Player) ? (Player) sender : null;

        sender.sendMessage(TextFormat.colorize("§6§l━━━ Server Info ━━━"));
        sender.sendMessage(TextFormat.colorize("§eServer: §fMinecraft Bedrock"));
        sender.sendMessage(TextFormat.colorize("§eCore: §fNukkit-MOT"));
        sender.sendMessage(plugin.replacePlaceholders("§eOnline: §f{online}/{max}", player));
        sender.sendMessage(plugin.replacePlaceholders("§eTPS: {tps}", player));
        sender.sendMessage(plugin.replacePlaceholders("§eUptime: {uptime}", player));
        sender.sendMessage(TextFormat.colorize("§eIP: §fplay.example.com:19132"));
        sender.sendMessage(TextFormat.colorize("§6§l━━━━━━━━━━━━━━━━"));
    }
}
