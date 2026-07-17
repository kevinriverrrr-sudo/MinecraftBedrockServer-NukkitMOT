package com.server.pvp;

import cn.nukkit.Player;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.EventPriority;
import cn.nukkit.event.Listener;
import cn.nukkit.event.block.BlockBreakEvent;
import cn.nukkit.event.player.PlayerInteractEvent;
import cn.nukkit.item.ItemID;
import cn.nukkit.plugin.PluginBase;
import cn.nukkit.utils.Config;
import cn.nukkit.utils.TextFormat;

import java.io.File;

/**
 * Main plugin class for the PvPSystem plugin.
 * Initializes all managers, trackers, listeners, and commands.
 */
public class PvPSystemPlugin extends PluginBase {

    private ELOManager eloManager;
    private StatsManager statsManager;
    private ArenaManager arenaManager;
    private ComboTracker comboTracker;
    private KillStreakTracker killStreakTracker;
    private CombatTagManager combatTagManager;
    private PvPListener pvpListener;

    @Override
    public void onEnable() {
        // Save default config if it doesn't exist
        saveDefaultConfig();
        reloadConfig();

        // Initialize ELO manager
        Config config = getConfig();
        int startingElo = config.getInt("elo.starting", 1000);
        double kFactor = config.getDouble("elo.k-factor", 32.0);
        int minimumElo = config.getInt("elo.minimum", 100);
        eloManager = new ELOManager(startingElo, kFactor, minimumElo);

        // Initialize stats manager
        statsManager = new StatsManager(getDataFolder(), eloManager);
        statsManager.loadStats();

        // Initialize arena manager
        arenaManager = new ArenaManager(this);
        arenaManager.loadArenas();

        // Initialize trackers
        comboTracker = new ComboTracker(this);
        killStreakTracker = new KillStreakTracker(this);
        combatTagManager = new CombatTagManager(this);

        // Register event listeners
        pvpListener = new PvPListener(this);
        getServer().getPluginManager().registerEvents(pvpListener, this);

        // Register wand listener (for arena position selection)
        getServer().getPluginManager().registerEvents(new WandListener(this), this);

        // Register commands
        registerCommands();

        getLogger().info(TextFormat.GREEN + "PvPSystem has been enabled!");
        getLogger().info(TextFormat.GREEN + "Combo System: " + (comboTracker.isEnabled() ? "Enabled" : "Disabled"));
        getLogger().info(TextFormat.GREEN + "Kill Streak System: " + (killStreakTracker.isEnabled() ? "Enabled" : "Disabled"));
        getLogger().info(TextFormat.GREEN + "ELO Starting Rating: " + startingElo);
        getLogger().info(TextFormat.GREEN + "Loaded " + arenaManager.getArenas().size() + " arenas.");
    }

    @Override
    public void onDisable() {
        // Save all data
        if (statsManager != null) {
            statsManager.saveStats();
        }
        if (arenaManager != null) {
            arenaManager.cleanup();
        }
        if (comboTracker != null) {
            comboTracker.cleanup();
        }
        if (combatTagManager != null) {
            combatTagManager.cleanup();
        }

        getLogger().info(TextFormat.RED + "PvPSystem has been disabled!");
    }

    /**
     * Register all plugin commands.
     */
    private void registerCommands() {
        // /pvp command
        ArenaCommand pvpCommand = new ArenaCommand(this);
        getServer().getCommandMap().register("pvp", pvpCommand);

        // /pvpstats command
        StatsCommand statsCommand = new StatsCommand(this, "stats");
        getServer().getCommandMap().register("pvpstats", statsCommand);

        // /pvpleaderboard command
        StatsCommand leaderboardCommand = new StatsCommand(this, "leaderboard");
        getServer().getCommandMap().register("pvpleaderboard", leaderboardCommand);
    }

    // --- Getters ---

    public ELOManager getEloManager() {
        return eloManager;
    }

    public StatsManager getStatsManager() {
        return statsManager;
    }

    public ArenaManager getArenaManager() {
        return arenaManager;
    }

    public ComboTracker getComboTracker() {
        return comboTracker;
    }

    public KillStreakTracker getKillStreakTracker() {
        return killStreakTracker;
    }

    public CombatTagManager getCombatTagManager() {
        return combatTagManager;
    }

    /**
     * Listener for the arena selection wand interactions.
     * Handles left-click (pos1) and right-click (pos2) with the wand item.
     */
    private static class WandListener implements Listener {

        private final PvPSystemPlugin plugin;

        public WandListener(PvPSystemPlugin plugin) {
            this.plugin = plugin;
        }

        @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
        public void onPlayerInteract(PlayerInteractEvent event) {
            Player player = event.getPlayer();

            // Check if player is holding the wand
            if (player.getInventory().getItemInHand() == null) return;
            if (player.getInventory().getItemInHand().getId() != ItemID.STICK) return;

            String itemName = player.getInventory().getItemInHand().getCustomName();
            if (itemName == null || !itemName.contains("Arena Selection Wand")) return;

            // Check permission
            if (!player.hasPermission("pvp.arena.create")) return;

            event.setCancelled(true);

            switch (event.getAction()) {
                case LEFT_CLICK_BLOCK:
                    plugin.getArenaManager().setWandPos1(player, event.getBlock().getLocation());
                    player.sendMessage("§aPosition 1 set: §f" +
                            event.getBlock().getLocation().getX() + ", " +
                            event.getBlock().getLocation().getY() + ", " +
                            event.getBlock().getLocation().getZ());
                    break;
                case RIGHT_CLICK_BLOCK:
                    plugin.getArenaManager().setWandPos2(player, event.getBlock().getLocation());
                    player.sendMessage("§aPosition 2 set: §f" +
                            event.getBlock().getLocation().getX() + ", " +
                            event.getBlock().getLocation().getY() + ", " +
                            event.getBlock().getLocation().getZ());
                    break;
                default:
                    break;
            }

            // Notify if both positions are set
            if (plugin.getArenaManager().hasWandSelection(player)) {
                player.sendMessage("§eBoth positions selected! Use §f/pvp arena create <name> §eto create the arena.");
            }
        }

        @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
        public void onBlockBreak(BlockBreakEvent event) {
            Player player = event.getPlayer();

            // Check if player is holding the wand
            if (player.getInventory().getItemInHand() == null) return;
            if (player.getInventory().getItemInHand().getId() != ItemID.STICK) return;

            String itemName = player.getInventory().getItemInHand().getCustomName();
            if (itemName == null || !itemName.contains("Arena Selection Wand")) return;

            // Check permission
            if (!player.hasPermission("pvp.arena.create")) return;

            event.setCancelled(true);

            // Left-click on block = set position 1
            plugin.getArenaManager().setWandPos1(player, event.getBlock().getLocation());
            player.sendMessage("§aPosition 1 set: §f" +
                    event.getBlock().getLocation().getX() + ", " +
                    event.getBlock().getLocation().getY() + ", " +
                    event.getBlock().getLocation().getZ());

            // Notify if both positions are set
            if (plugin.getArenaManager().hasWandSelection(player)) {
                player.sendMessage("§eBoth positions selected! Use §f/pvp arena create <name> §eto create the arena.");
            }
        }
    }
}
