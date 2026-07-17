package com.server.donationsystem;

import cn.nukkit.plugin.PluginBase;

/**
 * Main plugin class for the DonationSystem.
 * Initializes the donation manager, registers commands, listeners,
 * and starts the expiry checker task.
 */
public class DonationSystemPlugin extends PluginBase {

    private static DonationSystemPlugin instance;
    private DonationManager donationManager;

    @Override
    public void onLoad() {
        instance = this;
        // Save default config if it doesn't exist
        saveDefaultConfig();
    }

    @Override
    public void onEnable() {
        // Initialize the donation manager
        donationManager = new DonationManager(this);
        donationManager.init();

        // Register commands
        registerCommands();

        // Register event listeners
        getServer().getPluginManager().registerEvents(new DonationListener(this), this);

        // Start expiry checker task
        startExpiryChecker();

        getLogger().info("§aDonationSystem has been enabled!");
        getLogger().info("§7Loaded " + donationManager.getAllRanks().size() + " ranks, " +
                donationManager.getAllDonors().size() + " donors.");
    }

    @Override
    public void onDisable() {
        // Save all data
        if (donationManager != null) {
            donationManager.savePlayerData();
            donationManager.saveCodes();
        }

        getLogger().info("§cDonationSystem has been disabled!");
    }

    /**
     * Register all plugin commands.
     */
    private void registerCommands() {
        // /donate command
        getServer().getCommandMap().register("donatesystem", new DonateCommand(this));

        // Admin commands - register each as a separate command
        DonateAdminCommand adminHandler = new DonateAdminCommand(this);
        getServer().getCommandMap().register("donatesystem", adminHandler);

        // Register additional admin commands by creating wrapper commands
        getServer().getCommandMap().register("donatesystem",
                new AdminCommandWrapper(this, "donateremove", "Remove donor rank",
                        "/donateremove <player>", "donate.admin", adminHandler));
        getServer().getCommandMap().register("donatesystem",
                new AdminCommandWrapper(this, "donatelist", "List donors",
                        "/donatelist", "donate.admin", adminHandler));
        getServer().getCommandMap().register("donatesystem",
                new AdminCommandWrapper(this, "donategencode", "Generate donation codes",
                        "/donategencode <rank> [amount] [duration]", "donate.admin", adminHandler));

        // Perk commands
        getServer().getCommandMap().register("donatesystem", new PerkCommand(this, "fly",
                "Toggle flight", "donate.perk.fly"));
        getServer().getCommandMap().register("donatesystem", new PerkCommand(this, "heal",
                "Heal yourself", "donate.perk.heal"));
        getServer().getCommandMap().register("donatesystem", new PerkCommand(this, "feed",
                "Feed yourself", "donate.perk.feed"));
        getServer().getCommandMap().register("donatesystem", new PerkCommand(this, "repair",
                "Repair held item", "donate.perk.repair"));
        getServer().getCommandMap().register("donatesystem", new PerkCommand(this, "hat",
                "Wear item as hat", "donate.perk.hat"));
        getServer().getCommandMap().register("donatesystem", new PerkCommand(this, "workbench",
                "Open crafting table", "donate.perk.workbench"));
        getServer().getCommandMap().register("donatesystem", new PerkCommand(this, "enderchest",
                "Open ender chest", "donate.perk.enderchest"));
        getServer().getCommandMap().register("donatesystem", new PerkCommand(this, "back",
                "Return to death location", "donate.perk.back"));

        getLogger().info("Registered all commands.");
    }

    /**
     * Start the scheduled task that checks for expired ranks.
     */
    private void startExpiryChecker() {
        int intervalSeconds = getConfig().getInt("expiry-check-interval", 3600);
        long intervalTicks = intervalSeconds * 20L; // Convert seconds to ticks

        getServer().getScheduler().scheduleRepeatingTask(this, new ExpiryChecker(this), (int) intervalTicks, true);

        getLogger().info("Expiry checker started (interval: " + intervalSeconds + "s).");
    }

    /**
     * Get the plugin instance.
     */
    public static DonationSystemPlugin getInstance() {
        return instance;
    }

    /**
     * Get the donation manager.
     */
    public DonationManager getDonationManager() {
        return donationManager;
    }
}
