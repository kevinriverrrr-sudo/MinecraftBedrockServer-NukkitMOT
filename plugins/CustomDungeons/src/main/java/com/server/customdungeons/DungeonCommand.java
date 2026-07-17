package com.server.customdungeons;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.command.Command;
import cn.nukkit.command.CommandSender;

import java.util.List;
import java.util.Map;

/**
 * Handles the /dungeon command and all subcommands.
 * Subcommands: list, enter, leave, party (create/invite/accept/leave/list), leaderboard
 */
public class DungeonCommand extends Command {

    private final CustomDungeonsPlugin plugin;

    public DungeonCommand(CustomDungeonsPlugin plugin) {
        super("dungeon", "Dungeon commands", "/dungeon <list|enter|leave|party|leaderboard>");
        this.plugin = plugin;
        this.setPermission("dungeon.use");
        this.setAliases(new String[]{"dg"});
    }

    @Override
    public boolean execute(CommandSender sender, String commandLabel, String[] args) {
        if (!testPermission(sender)) {
            return false;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "list":
                handleList(sender);
                break;
            case "enter":
                handleEnter(sender, args);
                break;
            case "leave":
                handleLeave(sender);
                break;
            case "party":
                handleParty(sender, args);
                break;
            case "leaderboard":
                handleLeaderboard(sender, args);
                break;
            case "reload":
                handleReload(sender);
                break;
            case "info":
                handleInfo(sender, args);
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
        sender.sendMessage("§e§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        sender.sendMessage("§e§l       Custom Dungeons Commands");
        sender.sendMessage("§e§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        sender.sendMessage("§e/dungeon list §7- View available dungeons");
        sender.sendMessage("§e/dungeon enter <name> §7- Enter a dungeon");
        sender.sendMessage("§e/dungeon leave §7- Leave current dungeon");
        sender.sendMessage("§e/dungeon info <name> §7- View dungeon info");
        sender.sendMessage("§e/dungeon leaderboard [dungeon] §7- Fastest completions");
        sender.sendMessage("§e/dungeon party create §7- Create a party");
        sender.sendMessage("§e/dungeon party invite <player> §7- Invite to party");
        sender.sendMessage("§e/dungeon party accept §7- Accept party invite");
        sender.sendMessage("§e/dungeon party leave §7- Leave party");
        sender.sendMessage("§e/dungeon party list §7- View party members");
        if (sender.hasPermission("dungeon.admin")) {
            sender.sendMessage("§c/dungeon reload §7- Reload dungeon config");
        }
    }

    /**
     * Handle /dungeon list - List all available dungeons.
     */
    private void handleList(CommandSender sender) {
        sender.sendMessage("§e§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        sender.sendMessage("§e§l       Available Dungeons");
        sender.sendMessage("§e§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        for (DungeonTemplate template : plugin.getDungeonManager().getTemplates()) {
            // Show cooldown status for players
            String cooldownMsg = "";
            if (sender instanceof Player) {
                Player player = (Player) sender;
                if (plugin.getDungeonManager().isOnCooldown(player.getName(), template.getId())) {
                    long remaining = plugin.getDungeonManager().getRemainingCooldown(
                            player.getName(), template.getId());
                    cooldownMsg = " §c[Cooldown: " + formatTime(remaining) + "]";
                }
            }

            // Show if dungeon is currently in use
            DungeonInstance activeInstance = null;
            for (DungeonInstance instance : plugin.getDungeonManager().getActiveInstances()) {
                if (instance.getTemplate().getId().equals(template.getId())) {
                    activeInstance = instance;
                    break;
                }
            }
            String statusMsg = activeInstance != null ? " §c[In Use]" : " §a[Available]";

            sender.sendMessage("§7- " + template.getInfoLine() + cooldownMsg + statusMsg);
        }

        sender.sendMessage("§7Use §e/dungeon enter <name> §7to enter a dungeon.");
    }

    /**
     * Handle /dungeon enter <name> - Enter a dungeon.
     */
    private void handleEnter(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cThis command can only be used in-game!");
            return;
        }

        if (args.length < 2) {
            sender.sendMessage("§cUsage: /dungeon enter <name>");
            return;
        }

        Player player = (Player) sender;
        String dungeonId = args[1].toLowerCase();

        plugin.getDungeonManager().enterDungeon(player, dungeonId);
    }

    /**
     * Handle /dungeon leave - Leave the current dungeon.
     */
    private void handleLeave(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cThis command can only be used in-game!");
            return;
        }

        Player player = (Player) sender;
        plugin.getDungeonManager().leaveDungeon(player);
    }

    /**
     * Handle /dungeon info <name> - Show detailed info about a dungeon.
     */
    private void handleInfo(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /dungeon info <name>");
            return;
        }

        String dungeonId = args[1].toLowerCase();
        DungeonTemplate template = plugin.getDungeonManager().getTemplate(dungeonId);
        if (template == null) {
            sender.sendMessage("§cDungeon §e" + dungeonId + " §cdoes not exist!");
            return;
        }

        sender.sendMessage("§e§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        sender.sendMessage("§e§l   " + template.getDisplayName());
        sender.sendMessage("§e§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        sender.sendMessage("§7Difficulty: §f" + template.getDifficulty());
        sender.sendMessage("§7Min Level: §a" + template.getMinLevel());
        sender.sendMessage("§7Max Players: §b" + template.getMaxPlayers());
        sender.sendMessage("§7Cooldown: §f" + formatTime(template.getCooldown()));
        sender.sendMessage("§7Waves: §e" + template.getTotalWaves());
        sender.sendMessage("§7Boss: §c" + template.getBoss().getName() +
                " §7(§c" + template.getBoss().getHealth() + " HP§7)");

        // Show wave details
        sender.sendMessage("§7Wave Details:");
        for (int i = 0; i < template.getWaves().size(); i++) {
            DungeonWave wave = template.getWaves().get(i);
            sender.sendMessage("§8  Wave " + (i + 1) + ": §f" +
                    wave.getTotalMobCount() + " mobs");
            for (DungeonMob mob : wave.getMobs()) {
                sender.sendMessage("§8    - " + mob.getName() + " §7x" +
                        mob.getCount() + " §8(§c" + mob.getHealth() + " HP§8)");
            }
        }

        // Show loot
        sender.sendMessage("§7Guaranteed Loot:");
        for (DungeonLoot loot : template.getGuaranteedLoot()) {
            sender.sendMessage("§a  + " + loot.getItem() + " §7x" + loot.getMaxAmount());
        }

        sender.sendMessage("§7Random Loot:");
        for (DungeonLoot loot : template.getRandomLoot()) {
            sender.sendMessage("§e  ? " + loot.getItem() + " §7x" +
                    loot.getMinAmount() + "-" + loot.getMaxAmount() +
                    " §8(" + loot.getChance() + "% chance)");
        }
    }

    /**
     * Handle /dungeon party subcommands.
     */
    private void handleParty(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cThis command can only be used in-game!");
            return;
        }

        if (args.length < 2) {
            sendPartyHelp(sender);
            return;
        }

        Player player = (Player) sender;
        String partySubCmd = args[1].toLowerCase();

        switch (partySubCmd) {
            case "create":
                plugin.getPartyManager().createParty(player);
                break;
            case "invite":
                if (args.length < 3) {
                    player.sendMessage("§cUsage: /dungeon party invite <player>");
                    return;
                }
                Player target = cn.nukkit.Server.getInstance().getPlayer(args[2]);
                if (target == null) {
                    player.sendMessage("§cPlayer §e" + args[2] + " §cnot found or offline!");
                    return;
                }
                plugin.getPartyManager().invitePlayer(player, target);
                break;
            case "accept":
                plugin.getPartyManager().acceptInvite(player);
                break;
            case "leave":
                plugin.getPartyManager().leaveParty(player);
                break;
            case "list":
                handlePartyList(player);
                break;
            default:
                sendPartyHelp(sender);
                break;
        }
    }

    /**
     * Handle /dungeon party list - Show party members.
     */
    private void handlePartyList(Player player) {
        Party party = plugin.getPartyManager().getPlayerParty(player);
        if (party == null) {
            player.sendMessage("§cYou are not in a party!");
            return;
        }

        player.sendMessage("§e§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage("§e§l       Party Members");
        player.sendMessage("§e§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        for (String memberName : party.getMembers()) {
            String role = party.isLeader(memberName) ? "§6[Leader]" : "§7[Member]";
            Player member = Server.getInstance().getPlayerExact(memberName);
            String status = (member != null && member.isOnline()) ? "§a●" : "§c●";
            player.sendMessage(status + " " + role + " §f" + memberName);
        }

        player.sendMessage("§7Party size: §f" + party.getSize() + "/" + party.getMaxSize());

        if (!party.getPendingInvites().isEmpty()) {
            player.sendMessage("§7Pending invites: §e" +
                    String.join("§7, §e", party.getPendingInvites()));
        }
    }

    /**
     * Send party help message.
     */
    private void sendPartyHelp(CommandSender sender) {
        sender.sendMessage("§e§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        sender.sendMessage("§e§l       Party Commands");
        sender.sendMessage("§e§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        sender.sendMessage("§e/dungeon party create §7- Create a party");
        sender.sendMessage("§e/dungeon party invite <player> §7- Invite to party");
        sender.sendMessage("§e/dungeon party accept §7- Accept party invite");
        sender.sendMessage("§e/dungeon party leave §7- Leave party");
        sender.sendMessage("§e/dungeon party list §7- View party members");
    }

    /**
     * Handle /dungeon leaderboard [dungeon] - Show fastest completion times.
     */
    private void handleLeaderboard(CommandSender sender, String[] args) {
        String dungeonId;

        if (args.length >= 2) {
            dungeonId = args[1].toLowerCase();
        } else {
            // Show overview of all dungeons
            sender.sendMessage("§e§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            sender.sendMessage("§e§l       Dungeon Leaderboard");
            sender.sendMessage("§e§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

            for (DungeonTemplate template : plugin.getDungeonManager().getTemplates()) {
                List<Map.Entry<String, Long>> top = plugin.getDungeonManager()
                        .getTopLeaderboard(template.getId(), 1);
                if (!top.isEmpty()) {
                    Map.Entry<String, Long> best = top.get(0);
                    sender.sendMessage(template.getDisplayName() + " §7- §eBest: §f" +
                            best.getKey() + " §7(" + formatTime(best.getValue()) + ")");
                } else {
                    sender.sendMessage(template.getDisplayName() + " §7- §8No records yet");
                }
            }
            sender.sendMessage("§7Use §e/dungeon leaderboard <name> §7for detailed rankings.");
            return;
        }

        DungeonTemplate template = plugin.getDungeonManager().getTemplate(dungeonId);
        if (template == null) {
            sender.sendMessage("§cDungeon §e" + dungeonId + " §cdoes not exist!");
            return;
        }

        List<Map.Entry<String, Long>> top = plugin.getDungeonManager()
                .getTopLeaderboard(dungeonId, 10);

        sender.sendMessage("§e§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        sender.sendMessage("§e§l   " + template.getDisplayName() + " Leaderboard");
        sender.sendMessage("§e§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        if (top.isEmpty()) {
            sender.sendMessage("§7No completion records yet. Be the first!");
            return;
        }

        int rank = 1;
        for (Map.Entry<String, Long> entry : top) {
            String medal;
            switch (rank) {
                case 1: medal = "§6§l#1"; break;
                case 2: medal = "§7§l#2"; break;
                case 3: medal = "§c§l#3"; break;
                default: medal = "§f#" + rank; break;
            }

            sender.sendMessage(medal + " §r§f" + entry.getKey() +
                    " §7- §e" + formatTime(entry.getValue()));
            rank++;
        }
    }

    /**
     * Handle /dungeon reload - Reload dungeon config (admin only).
     */
    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("dungeon.admin")) {
            sender.sendMessage("§cYou don't have permission to use this command!");
            return;
        }

        // Check if any dungeons are active
        if (plugin.getDungeonManager().getActiveInstanceCount() > 0) {
            sender.sendMessage("§cCannot reload while dungeons are active!");
            return;
        }

        plugin.reloadConfig();
        plugin.getDungeonManager().loadTemplates();
        sender.sendMessage("§aDungeon configuration reloaded!");
        sender.sendMessage("§aLoaded §e" + plugin.getDungeonManager().getTemplates().size() +
                " §adungeon templates.");
    }

    // --- Helper ---

    /**
     * Format seconds into a readable time string.
     */
    private String formatTime(long seconds) {
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;

        if (hours > 0) {
            return hours + "h " + minutes + "m " + secs + "s";
        } else if (minutes > 0) {
            return minutes + "m " + secs + "s";
        } else {
            return secs + "s";
        }
    }

}
