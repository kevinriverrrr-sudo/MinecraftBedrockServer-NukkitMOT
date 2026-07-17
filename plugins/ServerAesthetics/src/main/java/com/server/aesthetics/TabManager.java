package com.server.aesthetics;

import cn.nukkit.Player;
import cn.nukkit.utils.TextFormat;

import java.util.*;

/**
 * Manages tab list customization including header/footer animation
 * and player rank prefixes in the player list.
 */
public class TabManager {

    private final ServerAestheticsPlugin plugin;
    private int headerFrameIndex = 0;
    private int taskId = -1;

    public TabManager(ServerAestheticsPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Starts the tab list animation task.
     */
    public void startAnimationTask() {
        if (!plugin.getConfig().getBoolean("tab.enabled", true)) {
            return;
        }

        int interval = plugin.getConfig().getInt("tab.update-interval", 40);
        taskId = plugin.getServer().getScheduler().scheduleRepeatingTask(plugin, new AnimationTask(plugin), interval).getTaskId();
    }

    /**
     * Updates the tab list for all online players.
     * Called by the AnimationTask on each interval.
     */
    public void updateAllPlayers() {
        if (!plugin.getConfig().getBoolean("tab.enabled", true)) {
            return;
        }

        // Advance animation frame if enabled
        if (plugin.getConfig().getBoolean("tab.header-animation", true)) {
            advanceHeaderFrame();
        }

        // Update each player's tab display
        for (Player player : plugin.getServer().getOnlinePlayers().values()) {
            updatePlayerTab(player);
        }

        // Sort players by rank if enabled
        if (plugin.getConfig().getBoolean("tab.sort-by-rank", true)) {
            sortPlayersByRank();
        }
    }

    /**
     * Updates the tab list for a specific player.
     * Sends the header/footer and sets the player's display name.
     */
    public void updatePlayerTab(Player player) {
        if (!plugin.getConfig().getBoolean("tab.enabled", true)) {
            return;
        }
        if (!player.isOnline()) {
            return;
        }

        // Get current header
        String header = getCurrentHeader();
        String footer = plugin.getConfig().getString("tab.footer", "");

        // Replace placeholders
        header = plugin.replacePlaceholders(header, player);
        footer = plugin.replacePlaceholders(footer, player);

        // Translate color codes
        header = TextFormat.colorize(header);
        footer = TextFormat.colorize(footer);

        // Send header/footer to player
        sendTabHeaderFooter(player, header, footer);

        // Set player's display name in tab with rank prefix
        updatePlayerDisplayName(player);
    }

    /**
     * Updates a player's display name in the tab list with their rank prefix.
     */
    public void updatePlayerDisplayName(Player player) {
        if (!plugin.getConfig().getBoolean("tab.sort-by-rank", true)) {
            // Just set the display name with prefix, no sorting
            String prefix = plugin.getPlayerPrefix(player);
            String displayName = prefix + player.getName();
            player.setDisplayName(displayName);
            return;
        }

        // Get rank priority for sorting
        int priority = plugin.getPlayerRankPriority(player);
        String sortCode = getSortCode(priority);
        String prefix = plugin.getPlayerPrefix(player);
        String displayName = sortCode + prefix + player.getName();

        player.setDisplayName(displayName);
    }

    /**
     * Sorts all online players in the tab list by rank priority.
     * Uses invisible prefix characters to control sort order.
     */
    private void sortPlayersByRank() {
        List<Player> players = new ArrayList<>(plugin.getServer().getOnlinePlayers().values());

        // Sort by rank priority (lower number = higher priority)
        players.sort((p1, p2) -> {
            int priority1 = plugin.getPlayerRankPriority(p1);
            int priority2 = plugin.getPlayerRankPriority(p2);
            return Integer.compare(priority1, priority2);
        });

        // Apply sorted display names with invisible sort codes
        for (int i = 0; i < players.size(); i++) {
            Player player = players.get(i);
            int priority = plugin.getPlayerRankPriority(player);
            String sortCode = getSortCode(priority);
            String prefix = plugin.getPlayerPrefix(player);
            String displayName = sortCode + prefix + player.getName();
            player.setDisplayName(displayName);
        }
    }

    /**
     * Gets an invisible sort code for the given rank priority.
     * Lower priority numbers get codes that sort earlier alphabetically.
     * Uses §0-§9 followed by §r to create invisible but sort-affecting codes.
     */
    private String getSortCode(int priority) {
        // Map priorities to color codes that sort alphabetically
        // §0 sorts before §1, §1 before §2, etc.
        // §a-§f sort after §9
        // We use multiple characters for wider priority ranges
        if (priority <= 0) {
            return "§0§r";
        } else if (priority <= 5) {
            return "§1§r";
        } else if (priority <= 10) {
            return "§2§r";
        } else if (priority <= 15) {
            return "§3§r";
        } else if (priority <= 20) {
            return "§4§r";
        } else if (priority <= 30) {
            return "§5§r";
        } else if (priority <= 40) {
            return "§6§r";
        } else if (priority <= 50) {
            return "§7§r";
        } else if (priority <= 60) {
            return "§8§r";
        } else if (priority <= 70) {
            return "§9§r";
        } else if (priority <= 80) {
            return "§a§r";
        } else if (priority <= 90) {
            return "§b§r";
        } else {
            return "§c§r";
        }
    }

    /**
     * Sends the tab list header and footer to a player.
     * Uses try-catch to handle different Nukkit-MOT versions.
     */
    private void sendTabHeaderFooter(Player player, String header, String footer) {
        try {
            // Use PlayerListPacket to send header/footer
            cn.nukkit.network.protocol.PlayerListPacket pk = new cn.nukkit.network.protocol.PlayerListPacket();
            // Unfortunately, header/footer requires a different approach in Nukkit-MOT
            // Use the player's dataPacket method with the appropriate packet
            try {
                Class<?> clazz = Class.forName("cn.nukkit.network.protocol.TextPacket");
                cn.nukkit.network.protocol.DataPacket textPk = (cn.nukkit.network.protocol.DataPacket) clazz.getConstructor().newInstance();
                java.lang.reflect.Field typeField = clazz.getDeclaredField("type");
                typeField.setAccessible(true);
                typeField.set(textPk, (byte) 4); // TYPE_TIP
                java.lang.reflect.Field messageField = clazz.getDeclaredField("message");
                messageField.setAccessible(true);
                messageField.set(textPk, header + "\n" + footer);
                player.dataPacket(textPk);
            } catch (Exception e) {
                // Fallback: just send as a popup
                player.sendTip(header);
            }
        } catch (Exception e) {
            // Tab header/footer not supported - silently ignore
        }
    }

    /**
     * Advances the header animation frame.
     */
    public void advanceHeaderFrame() {
        List<String> frames = plugin.getConfig().getStringList("tab.header-frames");
        if (frames == null || frames.isEmpty()) {
            return;
        }
        headerFrameIndex = (headerFrameIndex + 1) % frames.size();
    }

    /**
     * Gets the current header frame text.
     */
    public String getCurrentHeader() {
        List<String> frames = plugin.getConfig().getStringList("tab.header-frames");
        if (frames == null || frames.isEmpty()) {
            return plugin.getConfig().getString("server-name", "§6§lMinecraft Bedrock");
        }
        return frames.get(headerFrameIndex % frames.size());
    }

    /**
     * Removes a player from tab management (on quit).
     */
    public void removePlayer(Player player) {
        // No persistent data to clean up for tab
        // The player's display name will reset when they disconnect
    }

    /**
     * Stops the animation task.
     */
    public void stop() {
        if (taskId != -1) {
            plugin.getServer().getScheduler().cancelTask(taskId);
            taskId = -1;
        }
    }
}
