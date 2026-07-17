package com.server.customdungeons;

import cn.nukkit.Server;
import cn.nukkit.plugin.PluginBase;
import cn.nukkit.utils.TextFormat;

import java.util.List;

/**
 * Main plugin class for CustomDungeons.
 * Initializes all managers, registers listeners and commands,
 * and handles the plugin lifecycle.
 */
public class CustomDungeonsPlugin extends PluginBase {

    private DungeonManager dungeonManager;
    private PartyManager partyManager;
    private LootGenerator lootGenerator;
    private DungeonListener dungeonListener;

    @Override
    public void onEnable() {
        // Save default config if it doesn't exist
        saveDefaultConfig();
        reloadConfig();

        // Initialize loot generator
        lootGenerator = new LootGenerator();

        // Initialize dungeon manager and load templates
        dungeonManager = new DungeonManager(this);
        dungeonManager.loadTemplates();
        dungeonManager.loadData();

        // Initialize party manager
        partyManager = new PartyManager(this);

        // Register event listeners
        dungeonListener = new DungeonListener(this);
        getServer().getPluginManager().registerEvents(dungeonListener, this);

        // Register commands
        registerCommands();

        // Start periodic cleanup task (expired invites, etc.)
        startCleanupTask();

        getLogger().info(TextFormat.GREEN + "CustomDungeons has been enabled!");
        getLogger().info(TextFormat.GREEN + "Loaded " +
                dungeonManager.getTemplates().size() + " dungeon templates.");

        // Log loaded dungeons
        for (DungeonTemplate template : dungeonManager.getTemplates()) {
            getLogger().info(TextFormat.GREEN + "  - " + template.getId() +
                    " (" + template.getDisplayName() + TextFormat.GREEN + ") " +
                    "[" + template.getDifficulty() + "]");
        }
    }

    @Override
    public void onDisable() {
        // Save all data
        if (dungeonManager != null) {
            dungeonManager.saveAll();
        }

        // Clean up all active dungeon instances
        if (dungeonManager != null) {
            List<DungeonInstance> instances = new java.util.ArrayList<>(dungeonManager.getActiveInstances());
            for (DungeonInstance instance : instances) {
                instance.broadcastMessage("§cServer shutting down. Dungeon run cancelled.");
                instance.cleanup();
            }
        }

        getLogger().info(TextFormat.RED + "CustomDungeons has been disabled!");
    }

    /**
     * Register all plugin commands.
     */
    private void registerCommands() {
        DungeonCommand dungeonCommand = new DungeonCommand(this);
        getServer().getCommandMap().register("dungeon", dungeonCommand);
    }

    /**
     * Start a periodic task to clean up expired party invites
     * and other stale data.
     */
    private void startCleanupTask() {
        Server.getInstance().getScheduler().scheduleRepeatingTask(this, () -> {
            if (partyManager != null) {
                partyManager.cleanupExpiredInvites();
            }
        }, 600); // Run every 30 seconds (600 ticks)
    }

    // --- Getters ---

    public DungeonManager getDungeonManager() {
        return dungeonManager;
    }

    public PartyManager getPartyManager() {
        return partyManager;
    }

    public LootGenerator getLootGenerator() {
        return lootGenerator;
    }

    public DungeonListener getDungeonListener() {
        return dungeonListener;
    }
}
