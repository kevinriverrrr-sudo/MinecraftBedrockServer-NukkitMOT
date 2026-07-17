package com.server.pvp;

import cn.nukkit.Player;
import cn.nukkit.command.Command;
import cn.nukkit.command.CommandSender;
import cn.nukkit.command.PluginCommand;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemID;
import cn.nukkit.item.enchantment.Enchantment;
import cn.nukkit.utils.TextFormat;

import java.util.*;

/**
 * Handles the /pvp command for arena management, joining, leaving, and spectating.
 */
public class ArenaCommand extends Command {

    private final PvPSystemPlugin plugin;

    public ArenaCommand(PvPSystemPlugin plugin) {
        super("pvp", "PvP arena commands", "/pvp <arena|join|leave|spectate|wand|stats>");
        this.plugin = plugin;
        this.setPermission("pvp.use");
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
            case "arena":
                handleArenaCommand(sender, args);
                break;
            case "join":
                handleJoinCommand(sender, args);
                break;
            case "leave":
                handleLeaveCommand(sender);
                break;
            case "spectate":
                handleSpectateCommand(sender, args);
                break;
            case "wand":
                handleWandCommand(sender);
                break;
            case "stats":
                handleStatsCommand(sender, args);
                break;
            default:
                sendHelp(sender);
                break;
        }

        return true;
    }

    /**
     * Send help message to the sender.
     */
    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§e§l--- PvP System Commands ---");
        sender.sendMessage("§e/pvp arena create <name> §7- Create an arena");
        sender.sendMessage("§e/pvp arena delete <name> §7- Delete an arena");
        sender.sendMessage("§e/pvp arena list §7- List all arenas");
        sender.sendMessage("§e/pvp arena setspawn <name> <1|2> §7- Set arena spawn");
        sender.sendMessage("§e/pvp join <arena> §7- Join an arena queue");
        sender.sendMessage("§e/pvp leave §7- Leave arena/queue");
        sender.sendMessage("§e/pvp spectate <arena> §7- Spectate a match");
        sender.sendMessage("§e/pvp wand §7- Get arena selection wand");
        sender.sendMessage("§e/pvp stats [player] §7- View PvP stats");
    }

    /**
     * Handle /pvp arena subcommands.
     */
    private void handleArenaCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /pvp arena <create|delete|list|setspawn>");
            return;
        }

        String arenaSubCmd = args[1].toLowerCase();

        switch (arenaSubCmd) {
            case "create":
                handleArenaCreate(sender, args);
                break;
            case "delete":
                handleArenaDelete(sender, args);
                break;
            case "list":
                handleArenaList(sender);
                break;
            case "setspawn":
                handleArenaSetSpawn(sender, args);
                break;
            default:
                sender.sendMessage("§cUnknown arena command. Use: create, delete, list, setspawn");
                break;
        }
    }

    /**
     * Handle /pvp arena create <name>
     */
    private void handleArenaCreate(CommandSender sender, String[] args) {
        if (!sender.hasPermission("pvp.arena.create")) {
            sender.sendMessage("§cYou don't have permission to create arenas!");
            return;
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage("§cThis command can only be used in-game!");
            return;
        }

        if (args.length < 3) {
            sender.sendMessage("§cUsage: /pvp arena create <name>");
            return;
        }

        Player player = (Player) sender;
        String arenaName = args[2];

        if (plugin.getArenaManager().arenaExists(arenaName)) {
            sender.sendMessage("§cArena §e" + arenaName + " §calready exists!");
            return;
        }

        // Check if player has wand selection
        if (!plugin.getArenaManager().hasWandSelection(player)) {
            sender.sendMessage("§cYou need to select two positions with the wand first!");
            sender.sendMessage("§7Use §e/pvp wand §7to get the selection tool.");
            return;
        }

        Arena arena = plugin.getArenaManager().createArena(arenaName);
        arena.setPos1(plugin.getArenaManager().getWandPos1(player));
        arena.setPos2(plugin.getArenaManager().getWandPos2(player));

        plugin.getArenaManager().clearWandSelection(player);
        plugin.getArenaManager().saveArenas();

        sender.sendMessage("§aArena §e" + arenaName + " §acreated!");
        sender.sendMessage("§7Set spawn points with §e/pvp arena setspawn " + arenaName + " <1|2>");
    }

    /**
     * Handle /pvp arena delete <name>
     */
    private void handleArenaDelete(CommandSender sender, String[] args) {
        if (!sender.hasPermission("pvp.arena.create")) {
            sender.sendMessage("§cYou don't have permission to delete arenas!");
            return;
        }

        if (args.length < 3) {
            sender.sendMessage("§cUsage: /pvp arena delete <name>");
            return;
        }

        String arenaName = args[2];

        if (!plugin.getArenaManager().arenaExists(arenaName)) {
            sender.sendMessage("§cArena §e" + arenaName + " §cdoes not exist!");
            return;
        }

        if (plugin.getArenaManager().deleteArena(arenaName)) {
            sender.sendMessage("§aArena §e" + arenaName + " §adeleted!");
        } else {
            sender.sendMessage("§cCould not delete arena §e" + arenaName + "§c. It may be in use.");
        }
    }

    /**
     * Handle /pvp arena list
     */
    private void handleArenaList(CommandSender sender) {
        Collection<Arena> arenas = plugin.getArenaManager().getArenas();

        if (arenas.isEmpty()) {
            sender.sendMessage("§eNo arenas have been created yet.");
            return;
        }

        sender.sendMessage("§e§l--- Arenas ---");
        for (Arena arena : arenas) {
            sender.sendMessage(arena.getSummary());
        }
    }

    /**
     * Handle /pvp arena setspawn <name> <1|2>
     */
    private void handleArenaSetSpawn(CommandSender sender, String[] args) {
        if (!sender.hasPermission("pvp.arena.create")) {
            sender.sendMessage("§cYou don't have permission to set arena spawns!");
            return;
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage("§cThis command can only be used in-game!");
            return;
        }

        if (args.length < 4) {
            sender.sendMessage("§cUsage: /pvp arena setspawn <name> <1|2>");
            return;
        }

        Player player = (Player) sender;
        String arenaName = args[2];
        String spawnNum = args[3];

        if (!plugin.getArenaManager().arenaExists(arenaName)) {
            sender.sendMessage("§cArena §e" + arenaName + " §cdoes not exist!");
            return;
        }

        int spawnIndex;
        try {
            spawnIndex = Integer.parseInt(spawnNum);
        } catch (NumberFormatException e) {
            sender.sendMessage("§cSpawn number must be 1 or 2!");
            return;
        }

        if (spawnIndex != 1 && spawnIndex != 2) {
            sender.sendMessage("§cSpawn number must be 1 or 2!");
            return;
        }

        Arena arena = plugin.getArenaManager().getArena(arenaName);
        if (spawnIndex == 1) {
            arena.setSpawn1(player.getPosition());
            sender.sendMessage("§aSpawn 1 set for arena §e" + arenaName + "§a!");
        } else {
            arena.setSpawn2(player.getPosition());
            sender.sendMessage("§aSpawn 2 set for arena §e" + arenaName + "§a!");
        }

        plugin.getArenaManager().saveArenas();

        // Notify if arena is now ready
        if (arena.isReady()) {
            sender.sendMessage("§aArena §e" + arenaName + " §ais now ready for matches!");
        } else {
            if (arena.getSpawn1() == null) sender.sendMessage("§7Spawn 1 still needs to be set.");
            if (arena.getSpawn2() == null) sender.sendMessage("§7Spawn 2 still needs to be set.");
        }
    }

    /**
     * Handle /pvp join <arena>
     */
    private void handleJoinCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cThis command can only be used in-game!");
            return;
        }

        if (args.length < 2) {
            sender.sendMessage("§cUsage: /pvp join <arena>");
            return;
        }

        Player player = (Player) sender;
        String arenaName = args[1];

        if (!plugin.getArenaManager().arenaExists(arenaName)) {
            sender.sendMessage("§cArena §e" + arenaName + " §cdoes not exist!");
            return;
        }

        Arena arena = plugin.getArenaManager().getArena(arenaName);
        if (!arena.isReady()) {
            sender.sendMessage("§cArena §e" + arenaName + " §cis not ready yet! Spawns may not be set.");
            return;
        }

        if (plugin.getArenaManager().isInMatch(player)) {
            sender.sendMessage("§cYou are already in a match!");
            return;
        }

        if (plugin.getArenaManager().isInQueue(player)) {
            sender.sendMessage("§cYou are already in a queue! Use §e/pvp leave §cto leave first.");
            return;
        }

        if (plugin.getArenaManager().isSpectating(player)) {
            sender.sendMessage("§cYou are spectating! Use §e/pvp leave §cfirst.");
            return;
        }

        if (plugin.getCombatTagManager().isTagged(player)) {
            sender.sendMessage("§cYou cannot join a queue while in combat!");
            return;
        }

        plugin.getArenaManager().joinQueue(player, arenaName);
    }

    /**
     * Handle /pvp leave
     */
    private void handleLeaveCommand(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cThis command can only be used in-game!");
            return;
        }

        Player player = (Player) sender;
        plugin.getArenaManager().leave(player);
    }

    /**
     * Handle /pvp spectate <arena>
     */
    private void handleSpectateCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cThis command can only be used in-game!");
            return;
        }

        if (args.length < 2) {
            sender.sendMessage("§cUsage: /pvp spectate <arena>");
            return;
        }

        Player player = (Player) sender;
        String arenaName = args[1];

        if (plugin.getArenaManager().isInMatch(player)) {
            sender.sendMessage("§cYou cannot spectate while in a match!");
            return;
        }

        if (plugin.getArenaManager().isInQueue(player)) {
            sender.sendMessage("§cYou cannot spectate while in a queue! Use §e/pvp leave §cfirst.");
            return;
        }

        if (plugin.getCombatTagManager().isTagged(player)) {
            sender.sendMessage("§cYou cannot spectate while in combat!");
            return;
        }

        plugin.getArenaManager().spectate(player, arenaName);
    }

    /**
     * Handle /pvp wand
     * Gives the player a stick used for arena position selection.
     */
    private void handleWandCommand(CommandSender sender) {
        if (!sender.hasPermission("pvp.arena.create")) {
            sender.sendMessage("§cYou don't have permission to use the wand!");
            return;
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage("§cThis command can only be used in-game!");
            return;
        }

        Player player = (Player) sender;

        Item wand = Item.get(ItemID.STICK);
        wand.setCustomName("§eArena Selection Wand");
        wand.setLore("§7Left-click: Set position 1", "§7Right-click: Set position 2");

        player.getInventory().addItem(wand);
        player.sendMessage("§aYou received the arena selection wand!");
        player.sendMessage("§7Left-click a block to set position 1");
        player.sendMessage("§7Right-click a block to set position 2");
    }

    /**
     * Handle /pvp stats [player]
     */
    private void handleStatsCommand(CommandSender sender, String[] args) {
        String targetName;

        if (args.length >= 2) {
            targetName = args[1];
        } else if (sender instanceof Player) {
            targetName = sender.getName();
        } else {
            sender.sendMessage("§cPlease specify a player name!");
            return;
        }

        PvPStats stats = plugin.getStatsManager().getStats(targetName);
        sender.sendMessage(stats.getFormattedStats());
    }
}
