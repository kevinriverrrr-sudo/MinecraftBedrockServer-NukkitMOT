package com.server.customchat;

import cn.nukkit.command.Command;
import cn.nukkit.command.CommandSender;
import cn.nukkit.plugin.PluginBase;
import cn.nukkit.utils.TextFormat;

/**
 * Main plugin class for CustomChat.
 * Initializes all managers, registers event listeners, and registers commands.
 */
public class CustomChatPlugin extends PluginBase {

    // Managers
    private ChatManager chatManager;
    private ChatFilter chatFilter;
    private AntiSpamManager antiSpamManager;
    private IgnoreManager ignoreManager;
    private PrivateMessageManager privateMessageManager;
    private NickManager nickManager;

    // Whether CustomPerms is available
    private boolean customPermsAvailable;

    @Override
    public void onEnable() {
        // Save default config if it doesn't exist
        saveDefaultConfig();
        reloadConfig();

        // Check if CustomPerms is available
        customPermsAvailable = checkCustomPerms();
        if (customPermsAvailable) {
            getLogger().info("CustomPerms detected! Using permission-based group system.");
        } else {
            getLogger().warning("CustomPerms not found! Falling back to OP-based group detection.");
        }

        // Initialize managers
        chatManager = new ChatManager(this);
        chatFilter = new ChatFilter(this);
        antiSpamManager = new AntiSpamManager(this);
        ignoreManager = new IgnoreManager(this);
        privateMessageManager = new PrivateMessageManager(this);
        nickManager = new NickManager(this);

        // Register event listener
        getServer().getPluginManager().registerEvents(new ChatListener(this), this);

        // Register commands
        registerCommands();

        // Startup message
        getLogger().info(TextFormat.GREEN + "CustomChat v" + getDescription().getVersion() + " enabled!");
        getLogger().info("Channels: " + String.join(", ", chatManager.getChannelNames()));
        getLogger().info("Anti-spam: " + (antiSpamManager.isEnabled() ? "Enabled" : "Disabled"));
        getLogger().info("Chat filter: " + (chatFilter.isEnabled() ? "Enabled" : "Disabled"));
    }

    @Override
    public void onDisable() {
        getLogger().info(TextFormat.YELLOW + "CustomChat disabled!");
    }

    /**
     * Check if the CustomPerms plugin is available on the server.
     *
     * @return true if CustomPerms is loaded and available
     */
    private boolean checkCustomPerms() {
        try {
            Class.forName("com.server.customperms.CustomPermsPlugin");
            return getServer().getPluginManager().getPlugin("CustomPerms") != null;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * Command handler instances.
     */
    private ChatCommand chatCommand;
    private NickCommand nickCommand;

    /**
     * Register all chat commands with the server.
     * Commands are defined in plugin.yml and we handle them via onCommand.
     */
    private void registerCommands() {
        // Create shared handler instances
        chatCommand = new ChatCommand(this);
        nickCommand = new NickCommand(this);
    }

    /**
     * Handle all plugin commands via onCommand override.
     */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String cmdName = command.getName().toLowerCase();
        switch (cmdName) {
            case "channel":
            case "msg":
            case "reply":
            case "ignore":
                return chatCommand.onCommand(sender, command, label, args);
            case "nick":
                return nickCommand.onCommand(sender, command, label, args);
            default:
                return false;
        }
    }

    /**
     * Reload the plugin configuration and all managers.
     */
    public void reload() {
        reloadConfig();
        chatManager.loadConfig();
        chatFilter.loadConfig();
        antiSpamManager.loadConfig();
        nickManager.loadConfig();
        getLogger().info("Configuration reloaded!");
    }

    // Getters for managers

    public ChatManager getChatManager() {
        return chatManager;
    }

    public ChatFilter getChatFilter() {
        return chatFilter;
    }

    public AntiSpamManager getAntiSpamManager() {
        return antiSpamManager;
    }

    public IgnoreManager getIgnoreManager() {
        return ignoreManager;
    }

    public PrivateMessageManager getPrivateMessageManager() {
        return privateMessageManager;
    }

    public NickManager getNickManager() {
        return nickManager;
    }

    /**
     * Check if CustomPerms is available.
     *
     * @return true if CustomPerms is loaded and available
     */
    public boolean isCustomPermsAvailable() {
        return customPermsAvailable;
    }
}
