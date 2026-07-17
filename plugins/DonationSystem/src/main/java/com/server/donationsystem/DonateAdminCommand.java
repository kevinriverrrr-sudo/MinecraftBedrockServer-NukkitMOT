package com.server.donationsystem;

import cn.nukkit.Player;
import cn.nukkit.command.Command;
import cn.nukkit.command.CommandSender;

import java.util.List;
import java.util.Map;

/**
 * Handles admin donation commands:
 * - /donategive <player> <rank> [duration] - Give a donation rank to a player
 * - /donateremove <player> - Remove a player's donation rank
 * - /donatelist - List all players with donation ranks
 * - /donategencode <rank> [amount] [duration] - Generate donation codes
 */
public class DonateAdminCommand extends Command {

    private final DonationSystemPlugin plugin;

    public DonateAdminCommand(DonationSystemPlugin plugin) {
        super("donategive", "Give donor rank", "/donategive <player> <rank> [duration]");
        this.plugin = plugin;
        this.setPermission("donate.admin");

        // We register multiple commands via the same class but the server
        // uses the plugin.yml to define separate commands.
        // For proper handling, we'll intercept based on the command name.
    }

    /**
     * Create command instances for each admin sub-command.
     * Since Nukkit registers commands by name, we need separate Command objects.
     * This factory method approach won't work well, so we handle all admin commands
     * through this single class by checking the label.
     */
    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (!testPermission(sender)) {
            return false;
        }

        // Dispatch based on the command label used
        switch (label.toLowerCase()) {
            case "donategive":
                return handleGive(sender, args);
            case "donateremove":
                return handleRemove(sender, args);
            case "donatelist":
                return handleList(sender);
            case "donategencode":
                return handleGenCode(sender, args);
            default:
                // If called as "donategive" (the registered name) but with different intent
                if (args.length > 0) {
                    String subCmd = args[0].toLowerCase();
                    switch (subCmd) {
                        case "give":
                            // /donategive give <player> <rank> [duration]
                            String[] shiftedArgs = new String[args.length - 1];
                            System.arraycopy(args, 1, shiftedArgs, 0, args.length - 1);
                            return handleGive(sender, shiftedArgs);
                        case "remove":
                            shiftedArgs = new String[args.length - 1];
                            System.arraycopy(args, 1, shiftedArgs, 0, args.length - 1);
                            return handleRemove(sender, shiftedArgs);
                        case "list":
                            return handleList(sender);
                        case "gencode":
                            shiftedArgs = new String[args.length - 1];
                            System.arraycopy(args, 1, shiftedArgs, 0, args.length - 1);
                            return handleGenCode(sender, shiftedArgs);
                        default:
                            return handleGive(sender, args);
                    }
                }
                sendAdminHelp(sender);
                return true;
        }
    }

    /**
     * Send admin help message.
     */
    private void sendAdminHelp(CommandSender sender) {
        sender.sendMessage("§6§l═══════ Donation Admin ═══════");
        sender.sendMessage("§e/donategive <player> <rank> [duration] §7- Give rank");
        sender.sendMessage("§e/donateremove <player> §7- Remove rank");
        sender.sendMessage("§e/donatelist §7- List all donors");
        sender.sendMessage("§e/donategencode <rank> [amount] [duration] §7- Generate codes");
        sender.sendMessage("§6§l═════════════════════════════");
    }

    /**
     * /donategive <player> <rank> [duration]
     * Give a donation rank to a player.
     * Duration is in days (default: use rank config duration).
     * Use 0 for permanent.
     */
    private boolean handleGive(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /donategive <player> <rank> [duration_days]");
            sender.sendMessage("§7Duration in days. Use 0 for permanent. Default: rank config duration.");
            return true;
        }

        String targetName = args[0];
        String rankName = args[1].toLowerCase();

        DonationManager manager = plugin.getDonationManager();

        // Validate rank
        DonationRank rank = manager.getRank(rankName);
        if (rank == null) {
            sender.sendMessage("§cUnknown rank: " + rankName);
            sender.sendMessage("§7Available ranks: §e" + String.join("§7, §e", manager.getRankNames()));
            return true;
        }

        // Parse duration (in days)
        long durationSeconds = -1; // -1 means use rank default
        if (args.length >= 3) {
            try {
                long durationDays = Long.parseLong(args[2]);
                if (durationDays < 0) {
                    sender.sendMessage("§cDuration must be 0 or positive!");
                    return true;
                }
                durationSeconds = durationDays * 86400L; // Convert days to seconds
            } catch (NumberFormatException e) {
                sender.sendMessage("§cInvalid duration! Use a number (days).");
                return true;
            }
        }

        // Check if target player exists (online or offline check)
        Player targetPlayer = plugin.getServer().getPlayer(targetName);
        String actualName = targetPlayer != null ? targetPlayer.getName() : targetName;

        // Give the rank
        boolean success = manager.giveRank(actualName, rankName, durationSeconds);
        if (success) {
            String durationStr;
            if (durationSeconds == 0 || (durationSeconds == -1 && rank.isPermanent())) {
                durationStr = "§aPermanent";
            } else if (durationSeconds == -1) {
                durationStr = "§b" + DonationManager.formatTime(rank.getDuration());
            } else {
                durationStr = "§b" + DonationManager.formatTime(durationSeconds);
            }

            sender.sendMessage("§aGiven " + rank.getDisplayName() + " §arank to §e" + actualName);
            sender.sendMessage("§7Duration: " + durationStr);

            // Notify the target if online
            if (targetPlayer != null) {
                targetPlayer.sendMessage("§a§l═══════ Rank Received! ═══════");
                targetPlayer.sendMessage("§aYou have received the " + rank.getDisplayName() + " §arank!");
                targetPlayer.sendMessage("§7Duration: " + durationStr);
                targetPlayer.sendMessage("§a§l═════════════════════════════");
            }
        } else {
            sender.sendMessage("§cFailed to give rank. Check the rank name.");
        }

        return true;
    }

    /**
     * /donateremove <player>
     * Remove a player's donation rank.
     */
    private boolean handleRemove(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage("§cUsage: /donateremove <player>");
            return true;
        }

        String targetName = args[0];
        DonationManager manager = plugin.getDonationManager();

        // Get current rank info before removal
        DonationRank currentRank = manager.getPlayerRank(targetName);
        String rankDisplay = currentRank != null ? currentRank.getDisplayName() : "§cNone";

        boolean removed = manager.removeRankFromPlayer(targetName, true);
        if (removed) {
            sender.sendMessage("§aRemoved donation rank from §e" + targetName);
            sender.sendMessage("§7Previous rank: " + rankDisplay);

            // Notify the target if online
            Player targetPlayer = plugin.getServer().getPlayer(targetName);
            if (targetPlayer != null) {
                targetPlayer.sendMessage("§cYour donation rank has been removed by an admin.");
            }
        } else {
            sender.sendMessage("§cPlayer §e" + targetName + " §cdoes not have a donation rank.");
        }

        return true;
    }

    /**
     * /donatelist
     * List all players with donation ranks.
     */
    private boolean handleList(CommandSender sender) {
        DonationManager manager = plugin.getDonationManager();
        Map<String, DonationManager.PlayerDonationData> donors = manager.getAllDonors();

        sender.sendMessage("§6§l═══════ All Donors ═══════");

        if (donors.isEmpty()) {
            sender.sendMessage("§7No donors found.");
        } else {
            sender.sendMessage("§7Total donors: §e" + donors.size());
            sender.sendMessage("");

            for (Map.Entry<String, DonationManager.PlayerDonationData> entry : donors.entrySet()) {
                DonationManager.PlayerDonationData data = entry.getValue();
                DonationRank rank = manager.getRank(data.getRankName());
                String rankDisplay = rank != null ? rank.getDisplayName() : data.getRankName();

                long remaining = manager.getRemainingTime(data.getPlayerName());
                String expiryStr;
                if (remaining == -1) {
                    expiryStr = "§aPermanent";
                } else if (remaining == 0) {
                    expiryStr = "§cExpired";
                } else {
                    expiryStr = "§b" + DonationManager.formatTime(remaining);
                }

                sender.sendMessage("§7- §e" + data.getPlayerName() + " §7| Rank: " + rankDisplay +
                        " §7| Expires: " + expiryStr);
            }
        }

        sender.sendMessage("§6§l═════════════════════════════");
        return true;
    }

    /**
     * /donategencode <rank> [amount] [duration_days]
     * Generate donation codes for a specific rank.
     */
    private boolean handleGenCode(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage("§cUsage: /donategencode <rank> [amount] [duration_days]");
            sender.sendMessage("§7Duration in days. Use 0 for permanent. Default: rank config duration.");
            return true;
        }

        String rankName = args[0].toLowerCase();
        DonationManager manager = plugin.getDonationManager();

        // Validate rank
        DonationRank rank = manager.getRank(rankName);
        if (rank == null) {
            sender.sendMessage("§cUnknown rank: " + rankName);
            sender.sendMessage("§7Available ranks: §e" + String.join("§7, §e", manager.getRankNames()));
            return true;
        }

        // Parse amount
        int amount = 1;
        if (args.length >= 2) {
            try {
                amount = Integer.parseInt(args[1]);
                if (amount < 1 || amount > 100) {
                    sender.sendMessage("§cAmount must be between 1 and 100!");
                    return true;
                }
            } catch (NumberFormatException e) {
                sender.sendMessage("§cInvalid amount!");
                return true;
            }
        }

        // Parse duration (in days, 0 = permanent, -1 or omitted = use rank default)
        long durationSeconds = 0; // 0 means use rank default in the generateCodes method
        if (args.length >= 3) {
            try {
                long durationDays = Long.parseLong(args[2]);
                durationSeconds = durationDays * 86400L;
            } catch (NumberFormatException e) {
                sender.sendMessage("§cInvalid duration!");
                return true;
            }
        }

        // Generate codes
        List<String> generatedCodes = manager.generateCodes(rankName, amount, durationSeconds);

        if (generatedCodes.isEmpty()) {
            sender.sendMessage("§cFailed to generate codes.");
            return true;
        }

        String durationStr;
        if (durationSeconds == 0) {
            if (rank.isPermanent()) {
                durationStr = "Permanent";
            } else {
                durationStr = DonationManager.formatTime(rank.getDuration());
            }
        } else if (durationSeconds == -86400L || durationSeconds < 0) {
            durationStr = DonationManager.formatTime(rank.getDuration());
        } else {
            durationStr = DonationManager.formatTime(durationSeconds);
        }

        sender.sendMessage("§a§l═══════ Codes Generated ═══════");
        sender.sendMessage("§7Rank: " + rank.getDisplayName());
        sender.sendMessage("§7Duration: §b" + durationStr);
        sender.sendMessage("§7Amount: §e" + generatedCodes.size());
        sender.sendMessage("§7Codes:");
        for (String code : generatedCodes) {
            sender.sendMessage("  §a§l" + code);
        }
        sender.sendMessage("§a§l═══════════════════════════════");
        sender.sendMessage("§7Players can redeem with: §e/donate activate <code>");

        return true;
    }
}
