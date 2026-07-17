package com.server.aesthetics;

import cn.nukkit.Player;
import cn.nukkit.plugin.PluginBase;
import cn.nukkit.plugin.Plugin;
import cn.nukkit.utils.TextFormat;

import java.lang.reflect.Method;

public class ServerAestheticsPlugin extends PluginBase {

    private ScoreboardManager scoreboardManager;
    private TabManager tabManager;
    private JoinEffectManager joinEffectManager;
    private BroadcastManager broadcastManager;
    private SpawnManager spawnManager;

    private long serverStartTime;
    private float currentTPS = 20.0f;

    // TPS tracking
    private long lastTickTime;
    private int tickCount;
    private float[] tpsHistory = new float[3];
    private int tpsHistoryIndex = 0;

    @Override
    public void onEnable() {
        serverStartTime = System.currentTimeMillis();
        lastTickTime = System.currentTimeMillis();
        tickCount = 0;

        // Save default config
        saveDefaultConfig();
        reloadConfig();

        // Initialize managers
        scoreboardManager = new ScoreboardManager(this);
        tabManager = new TabManager(this);
        joinEffectManager = new JoinEffectManager(this);
        broadcastManager = new BroadcastManager(this);
        spawnManager = new SpawnManager(this);

        // Register event listeners
        getServer().getPluginManager().registerEvents(new AestheticsListener(this), this);

        // Register commands
        SpawnCommand spawnCmd = new SpawnCommand(this);
        getServer().getCommandMap().register("serveraesthetics", spawnCmd);
        // Register /setspawn as a separate command alias pointing to same handler
        SetSpawnCommand setSpawnCmd = new SetSpawnCommand(this);
        getServer().getCommandMap().register("serveraesthetics", setSpawnCmd);
        getServer().getCommandMap().register("serveraesthetics", new ServerInfoCommand(this));
        getServer().getCommandMap().register("serveraesthetics", new ScoreboardCommand(this));
        getServer().getCommandMap().register("serveraesthetics", new BroadcastCommand(this));

        // Start scheduled tasks
        scoreboardManager.startUpdateTask();
        tabManager.startAnimationTask();
        broadcastManager.startBroadcastTask();

        // Start TPS tracking task
        startTPSTrackingTask();

        // Set up existing players (in case of reload)
        for (Player player : getServer().getOnlinePlayers().values()) {
            scoreboardManager.createScoreboard(player);
            tabManager.updatePlayerTab(player);
        }

        getLogger().info(TextFormat.GREEN + "ServerAesthetics v1.0.0 enabled!");
        getLogger().info(TextFormat.GREEN + "Scoreboard, Tab, Join Effects loaded.");
    }

    @Override
    public void onDisable() {
        // Cancel all scheduled tasks
        getServer().getScheduler().cancelTask(this);

        // Clean up scoreboards for all players
        for (Player player : getServer().getOnlinePlayers().values()) {
            scoreboardManager.removeScoreboard(player);
        }

        getLogger().info(TextFormat.RED + "ServerAesthetics disabled!");
    }

    /**
     * Starts a repeating task that tracks server TPS.
     */
    private void startTPSTrackingTask() {
        getServer().getScheduler().scheduleRepeatingTask(this, () -> {
            long now = System.currentTimeMillis();
            long elapsed = now - lastTickTime;

            if (elapsed > 0) {
                float tps = Math.min(20.0f, 1000.0f / (elapsed / 1.0f));
                tpsHistory[tpsHistoryIndex % tpsHistory.length] = tps;
                tpsHistoryIndex++;
                currentTPS = getAverageTPS();
            }

            lastTickTime = now;
            tickCount++;
        }, 1); // Run every tick
    }

    /**
     * Calculates the average TPS from history.
     */
    private float getAverageTPS() {
        float sum = 0;
        int count = 0;
        for (float tps : tpsHistory) {
            if (tps > 0) {
                sum += tps;
                count++;
            }
        }
        return count > 0 ? sum / count : 20.0f;
    }

    /**
     * Gets the current TPS.
     */
    public float getTPS() {
        // First try the server's built-in TPS method
        try {
            Method method = getServer().getClass().getMethod("getTicksPerSecond");
            float serverTPS = (float) method.invoke(getServer());
            if (serverTPS > 0) {
                return serverTPS;
            }
        } catch (Exception ignored) {
            // Fallback to our own tracking
        }
        return currentTPS;
    }

    /**
     * Gets formatted TPS string with color coding.
     */
    public String getFormattedTPS() {
        float tps = getTPS();
        if (tps >= 18.0f) {
            return TextFormat.GREEN + String.format("%.1f", tps);
        } else if (tps >= 14.0f) {
            return TextFormat.YELLOW + String.format("%.1f", tps);
        } else {
            return TextFormat.RED + String.format("%.1f", tps);
        }
    }

    /**
     * Gets formatted ping string with color coding.
     */
    public String getFormattedPing(Player player) {
        int ping = player.getPing();
        if (ping < 50) {
            return TextFormat.GREEN + String.valueOf(ping);
        } else if (ping < 100) {
            return TextFormat.YELLOW + String.valueOf(ping);
        } else {
            return TextFormat.RED + String.valueOf(ping);
        }
    }

    /**
     * Gets the server uptime as a formatted string.
     */
    public String getUptime() {
        long uptimeMillis = System.currentTimeMillis() - serverStartTime;
        long seconds = uptimeMillis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (days > 0) {
            return String.format("%dd %dh %dm", days, hours % 24, minutes % 60);
        } else if (hours > 0) {
            return String.format("%dh %dm", hours, minutes % 60);
        } else if (minutes > 0) {
            return String.format("%dm %ds", minutes, seconds % 60);
        } else {
            return String.format("%ds", seconds);
        }
    }

    /**
     * Replaces all placeholders in a string with actual values.
     */
    public String replacePlaceholders(String text, Player player) {
        if (text == null) return "";
        if (player != null) {
            text = text.replace("{player}", player.getName());
            text = text.replace("{display}", player.getDisplayName());
            text = text.replace("{prefix}", getPlayerPrefix(player));
            text = text.replace("{suffix}", getPlayerSuffix(player));
            text = text.replace("{rank}", getPlayerRank(player));
            text = text.replace("{ping}", getFormattedPing(player));
            text = text.replace("{tps}", getFormattedTPS());
            text = text.replace("{world}", player.getLevel() != null ? player.getLevel().getName() : "unknown");
        }
        text = text.replace("{online}", String.valueOf(getServer().getOnlinePlayers().size()));
        text = text.replace("{max}", String.valueOf(getServer().getMaxPlayers()));
        text = text.replace("{uptime}", getUptime());
        return text;
    }

    /**
     * Replaces placeholders without player context.
     */
    public String replacePlaceholders(String text) {
        return replacePlaceholders(text, null);
    }

    /**
     * Gets a player's rank prefix from CustomPerms.
     * Falls back to "§7" if CustomPerms is not available.
     */
    public String getPlayerPrefix(Player player) {
        try {
            Plugin permsPlugin = getServer().getPluginManager().getPlugin("CustomPerms");
            if (permsPlugin != null) {
                Method getApiMethod = permsPlugin.getClass().getMethod("getAPI");
                Object api = getApiMethod.invoke(permsPlugin);
                Method getPrefixMethod = api.getClass().getMethod("getPlayerPrefix", Player.class);
                String prefix = (String) getPrefixMethod.invoke(api, player);
                if (prefix != null && !prefix.isEmpty()) {
                    return prefix;
                }
            }
        } catch (Exception ignored) {
            // CustomPerms not available or API mismatch
        }
        return "§7";
    }

    /**
     * Gets a player's rank suffix from CustomPerms.
     * Falls back to "" if CustomPerms is not available.
     */
    public String getPlayerSuffix(Player player) {
        try {
            Plugin permsPlugin = getServer().getPluginManager().getPlugin("CustomPerms");
            if (permsPlugin != null) {
                Method getApiMethod = permsPlugin.getClass().getMethod("getAPI");
                Object api = getApiMethod.invoke(permsPlugin);
                Method getSuffixMethod = api.getClass().getMethod("getPlayerSuffix", Player.class);
                String suffix = (String) getSuffixMethod.invoke(api, player);
                if (suffix != null && !suffix.isEmpty()) {
                    return suffix;
                }
            }
        } catch (Exception ignored) {
            // CustomPerms not available or API mismatch
        }
        return "";
    }

    /**
     * Gets a player's rank name from CustomPerms.
     * Falls back to "Member" if CustomPerms is not available.
     */
    public String getPlayerRank(Player player) {
        try {
            Plugin permsPlugin = getServer().getPluginManager().getPlugin("CustomPerms");
            if (permsPlugin != null) {
                Method getApiMethod = permsPlugin.getClass().getMethod("getAPI");
                Object api = getApiMethod.invoke(permsPlugin);
                Method getRankMethod = api.getClass().getMethod("getPlayerRank", Player.class);
                String rank = (String) getRankMethod.invoke(api, player);
                if (rank != null && !rank.isEmpty()) {
                    return rank;
                }
            }
        } catch (Exception ignored) {
            // CustomPerms not available or API mismatch
        }
        return "Member";
    }

    /**
     * Gets a player's rank priority from CustomPerms.
     * Lower number = higher priority.
     * Falls back to 99 if CustomPerms is not available.
     */
    public int getPlayerRankPriority(Player player) {
        try {
            Plugin permsPlugin = getServer().getPluginManager().getPlugin("CustomPerms");
            if (permsPlugin != null) {
                Method getApiMethod = permsPlugin.getClass().getMethod("getAPI");
                Object api = getApiMethod.invoke(permsPlugin);
                Method getPriorityMethod = api.getClass().getMethod("getRankPriority", String.class);
                String rank = getPlayerRank(player);
                Object priority = getPriorityMethod.invoke(api, rank);
                if (priority instanceof Integer) {
                    return (Integer) priority;
                }
            }
        } catch (Exception ignored) {
            // CustomPerms not available or API mismatch
        }
        return 99;
    }

    /**
     * Checks if a player has joined for the first time.
     * Uses a config-based tracking system.
     */
    public boolean isFirstJoin(Player player) {
        return !getConfig().exists("players." + player.getUniqueId().toString());
    }

    /**
     * Marks a player as having joined before.
     */
    public void markPlayerJoined(Player player) {
        getConfig().set("players." + player.getUniqueId().toString() + ".name", player.getName());
        getConfig().set("players." + player.getUniqueId().toString() + ".first-join", System.currentTimeMillis());
        saveConfig();
    }

    // Getters for managers
    public ScoreboardManager getScoreboardManager() {
        return scoreboardManager;
    }

    public TabManager getTabManager() {
        return tabManager;
    }

    public JoinEffectManager getJoinEffectManager() {
        return joinEffectManager;
    }

    public BroadcastManager getBroadcastManager() {
        return broadcastManager;
    }

    public SpawnManager getSpawnManager() {
        return spawnManager;
    }

    public long getServerStartTime() {
        return serverStartTime;
    }
}
