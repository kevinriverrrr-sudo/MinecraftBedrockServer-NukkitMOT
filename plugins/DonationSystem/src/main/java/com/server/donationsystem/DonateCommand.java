package com.server.donationsystem;

import cn.nukkit.Player;
import cn.nukkit.command.Command;
import cn.nukkit.command.CommandSender;
import cn.nukkit.command.data.CommandParamType;
import cn.nukkit.command.data.CommandParameter;

import java.util.Collection;

/**
 * Handles the /donate command with subcommands:
 * - /donate ranks - View available ranks and their perks
 * - /donate info - View your current rank and expiry
 * - /donate activate <code> - Activate a donation code
 */
public class DonateCommand extends Command {

    private final DonationSystemPlugin plugin;

    public DonateCommand(DonationSystemPlugin plugin) {
        super("donate", "Donation commands", "/donate <ranks|info|activate>");
        this.plugin = plugin;

        // Set up command parameters for auto-completion
        this.commandParameters.clear();
        this.commandParameters.put("ranks", new CommandParameter[]{
                CommandParameter.newEnum("action", new String[]{"ranks"})
        });
        this.commandParameters.put("info", new CommandParameter[]{
                CommandParameter.newEnum("action", new String[]{"info"})
        });
        this.commandParameters.put("activate", new CommandParameter[]{
                CommandParameter.newEnum("action", new String[]{"activate"}),
                CommandParameter.newType("code", CommandParamType.STRING)
        });
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "ranks":
                handleRanks(sender);
                break;
            case "info":
                handleInfo(sender);
                break;
            case "activate":
                handleActivate(sender, args);
                break;
            default:
                sendHelp(sender);
                break;
        }

        return true;
    }

    /**
     * Send the help message to the sender.
     */
    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6§l═══════ Donation System ═══════");
        sender.sendMessage("§e/donate ranks §7- View available ranks");
        sender.sendMessage("§e/donate info §7- View your rank info");
        sender.sendMessage("§e/donate activate <code> §7- Redeem a code");
        sender.sendMessage("§6§l═════════════════════════════");
    }

    /**
     * Handle /donate ranks - Display all available ranks and their perks.
     */
    private void handleRanks(CommandSender sender) {
        Collection<DonationRank> ranks = plugin.getDonationManager().getAllRanks();

        sender.sendMessage("§6§l═══════ Available Ranks ═══════");

        for (DonationRank rank : ranks) {
            sender.sendMessage("");
            sender.sendMessage("§l" + rank.getDisplayName() + " §r§7Rank");
            sender.sendMessage("§7Price: §e" + rank.getPriceDisplay());

            // Duration
            if (rank.isPermanent()) {
                sender.sendMessage("§7Duration: §aPermanent");
            } else {
                sender.sendMessage("§7Duration: §b" + DonationManager.formatTime(rank.getDuration()));
            }

            // Perks
            sender.sendMessage("§7Perks:");
            for (String perkLine : rank.getPerkDescription()) {
                sender.sendMessage("  " + perkLine);
            }
        }

        sender.sendMessage("");
        sender.sendMessage("§6§l═════════════════════════════");
        sender.sendMessage("§7Use §e/donate activate <code> §7to redeem a donation code!");
    }

    /**
     * Handle /donate info - Display the player's current rank information.
     */
    private void handleInfo(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cThis command can only be used in-game!");
            return;
        }

        Player player = (Player) sender;
        DonationManager manager = plugin.getDonationManager();

        DonationRank rank = manager.getPlayerRank(player.getName());

        sender.sendMessage("§6§l═══════ Your Donation Info ═══════");

        if (rank == null) {
            sender.sendMessage("§7You don't have a donation rank.");
            sender.sendMessage("§7Use §e/donate ranks §7to see available ranks!");
        } else {
            sender.sendMessage("§7Rank: " + rank.getDisplayName());
            sender.sendMessage("§7Prefix: " + rank.getPrefix() + "§r§7" + player.getName());

            long remaining = manager.getRemainingTime(player.getName());
            if (remaining == -1) {
                sender.sendMessage("§7Expires: §aNever (Permanent)");
            } else if (remaining == 0) {
                sender.sendMessage("§7Expires: §cExpired!");
            } else {
                sender.sendMessage("§7Expires in: §b" + DonationManager.formatTime(remaining));
            }

            // Show active perks
            sender.sendMessage("§7Your perks:");
            String[] perks = rank.getPerkDescription();
            for (String perkLine : perks) {
                sender.sendMessage("  " + perkLine);
            }
        }

        sender.sendMessage("§6§l═════════════════════════════════");
    }

    /**
     * Handle /donate activate <code> - Redeem a donation code.
     */
    private void handleActivate(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cThis command can only be used in-game!");
            return;
        }

        if (args.length < 2) {
            sender.sendMessage("§cUsage: /donate activate <code>");
            return;
        }

        Player player = (Player) sender;
        String codeStr = args[1];
        DonationManager manager = plugin.getDonationManager();

        DonationManager.RedeemResult result = manager.redeemCode(codeStr, player.getName());

        switch (result) {
            case SUCCESS:
                DonationRank newRank = manager.getPlayerRank(player.getName());
                String rankDisplay = newRank != null ? newRank.getDisplayName() : "Unknown";
                player.sendMessage("§a§l═══════ Donation Redeemed! ═══════");
                player.sendMessage("§aYou have received the " + rankDisplay + " §arank!");
                if (newRank != null) {
                    player.sendMessage("§7Perks:");
                    for (String perkLine : newRank.getPerkDescription()) {
                        player.sendMessage("  " + perkLine);
                    }
                }
                player.sendMessage("§a§l═════════════════════════════════");
                // Broadcast
                plugin.getServer().broadcastMessage(
                        "§6§l[Donation] §r§e" + player.getName() + " §ahas redeemed a donation code and received " + rankDisplay + " §arank!"
                );
                break;
            case NOT_FOUND:
                player.sendMessage("§cInvalid donation code! Please check and try again.");
                break;
            case ALREADY_USED:
                player.sendMessage("§cThis donation code has already been used!");
                break;
            case INVALID_RANK:
                player.sendMessage("§cThis donation code is for an invalid rank. Please contact an admin.");
                break;
        }
    }
}
