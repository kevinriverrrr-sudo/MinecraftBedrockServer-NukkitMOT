package com.server.donationsystem;

import cn.nukkit.command.Command;
import cn.nukkit.command.CommandSender;

/**
 * Wrapper command for admin sub-commands (donateremove, donatelist, donategencode).
 * Delegates execution to the main DonateAdminCommand handler.
 */
public class AdminCommandWrapper extends Command {

    private final DonationSystemPlugin plugin;
    private final DonateAdminCommand handler;

    public AdminCommandWrapper(DonationSystemPlugin plugin, String name, String description,
                               String usage, String permission, DonateAdminCommand handler) {
        super(name, description, usage);
        this.plugin = plugin;
        this.handler = handler;
        this.setPermission(permission);
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        // Delegate to the main admin command handler which dispatches by label
        return handler.execute(sender, label, args);
    }
}
