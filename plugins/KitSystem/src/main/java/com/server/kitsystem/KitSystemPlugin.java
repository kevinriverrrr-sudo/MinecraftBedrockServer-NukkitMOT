package com.server.kitsystem;

import cn.nukkit.plugin.PluginBase;

/**
 * Main plugin class for the KitSystem plugin.
 * Initializes the kit manager, registers commands, and sets up event listeners.
 */
public class KitSystemPlugin extends PluginBase {

    private KitManager kitManager;
    private KitCommand kitCommand;
    private KitAdminCommand kitAdminCommand;
    private CustomItemCommand customItemCommand;
    private KitListener kitListener;

    @Override
    public void onEnable() {
        // Save default config if it doesn't exist
        saveDefaultConfig();
        getConfig().reload();

        // Initialize the kit manager (loads kits, custom items, and cooldowns)
        kitManager = new KitManager(this);

        // Initialize commands
        kitCommand = new KitCommand(this);
        kitAdminCommand = new KitAdminCommand(this);
        customItemCommand = new CustomItemCommand(this);

        // Register commands
        getServer().getCommandMap().register("kit", kitCommand);
        getServer().getCommandMap().register("kitadmin", kitAdminCommand);
        getServer().getCommandMap().register("customitem", customItemCommand);

        // Initialize and register event listener
        kitListener = new KitListener(this);
        getServer().getPluginManager().registerEvents(kitListener, this);

        getLogger().info("=================================");
        getLogger().info("  KitSystem v" + getDescription().getVersion());
        getLogger().info("  Loaded " + kitManager.getKits().size() + " kits");
        getLogger().info("  Loaded " + kitManager.getCustomItems().size() + " custom items");
        getLogger().info("  Plugin enabled successfully!");
        getLogger().info("=================================");
    }

    @Override
    public void onDisable() {
        // Save cooldown data
        if (kitManager != null) {
            kitManager.getCooldownManager().save();
            getLogger().info("Cooldown data saved.");
        }

        getLogger().info("KitSystem disabled.");
    }

    /**
     * Get the kit manager instance.
     */
    public KitManager getKitManager() {
        return kitManager;
    }

    /**
     * Get the kit command instance (used by KitAdminCommand for giving kits).
     */
    public KitCommand getKitCommand() {
        return kitCommand;
    }

    /**
     * Get the kit admin command instance.
     */
    public KitAdminCommand getKitAdminCommand() {
        return kitAdminCommand;
    }

    /**
     * Get the custom item command instance.
     */
    public CustomItemCommand getCustomItemCommand() {
        return customItemCommand;
    }

    /**
     * Get the kit listener instance.
     */
    public KitListener getKitListener() {
        return kitListener;
    }
}
