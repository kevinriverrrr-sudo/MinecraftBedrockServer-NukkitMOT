package com.server.donationsystem;

import cn.nukkit.Player;
import cn.nukkit.command.Command;
import cn.nukkit.command.CommandSender;
import cn.nukkit.item.Item;
import cn.nukkit.level.Position;

/**
 * Handles individual perk commands: /fly, /heal, /feed, /repair, /hat, /workbench, /enderchest, /back.
 * Each instance represents one perk command.
 */
public class PerkCommand extends Command {

    private final DonationSystemPlugin plugin;
    private final String perkType;

    public PerkCommand(DonationSystemPlugin plugin, String perkType, String description, String permission) {
        super(perkType, description, "/" + perkType);
        this.plugin = plugin;
        this.perkType = perkType;
        this.setPermission(permission);

        // Add aliases for workbench and enderchest
        if ("workbench".equals(perkType)) {
            this.setAliases(new String[]{"wb"});
        } else if ("enderchest".equals(perkType)) {
            this.setAliases(new String[]{"ec"});
        }
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (!testPermission(sender)) {
            return false;
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage("§cThis command can only be used in-game!");
            return false;
        }

        Player player = (Player) sender;

        // Verify the player has an active donation rank with this perk
        DonationRank rank = plugin.getDonationManager().getPlayerRank(player.getName());
        if (rank == null) {
            player.sendMessage("§cYou don't have a donation rank!");
            return false;
        }

        // Check if the player's rank has expired
        if (plugin.getDonationManager().isRankExpired(player.getName())) {
            player.sendMessage("§cYour donation rank has expired!");
            return false;
        }

        switch (perkType) {
            case "fly":
                handleFly(player, rank);
                break;
            case "heal":
                handleHeal(player, rank);
                break;
            case "feed":
                handleFeed(player, rank);
                break;
            case "repair":
                handleRepair(player, rank);
                break;
            case "hat":
                handleHat(player, rank);
                break;
            case "workbench":
                handleWorkbench(player, rank);
                break;
            case "enderchest":
                handleEnderchest(player, rank);
                break;
            case "back":
                handleBack(player, rank);
                break;
            default:
                player.sendMessage("§cUnknown perk command!");
                break;
        }

        return true;
    }

    /**
     * /fly - Toggle flight mode.
     * Available for VIP+ ranks.
     */
    private void handleFly(Player player, DonationRank rank) {
        if (!rank.hasFly()) {
            player.sendMessage("§cYour rank does not include the fly perk!");
            return;
        }

        if (player.getAllowFlight()) {
            player.setAllowFlight(false);
            player.sendMessage("§cFlight disabled.");
        } else {
            player.setAllowFlight(true);
            player.sendMessage("§aFlight enabled! Double-tap jump to fly.");
        }
    }

    /**
     * /heal - Fully heal the player.
     * Available for Premium+ ranks.
     */
    private void handleHeal(Player player, DonationRank rank) {
        if (!rank.hasHeal()) {
            player.sendMessage("§cYour rank does not include the heal perk!");
            return;
        }

        float maxHealth = player.getMaxHealth();
        player.setHealth(maxHealth);
        player.sendMessage("§aYou have been fully healed!");
    }

    /**
     * /feed - Fully feed the player.
     * Available for Premium+ ranks.
     */
    private void handleFeed(Player player, DonationRank rank) {
        if (!rank.hasFeed()) {
            player.sendMessage("§cYour rank does not include the feed perk!");
            return;
        }

        player.getFoodData().setLevel(20);
        player.sendMessage("§aYou have been fully fed!");
    }

    /**
     * /repair - Repair the item in the player's hand.
     * Available for Elite+ ranks.
     */
    private void handleRepair(Player player, DonationRank rank) {
        if (!rank.hasRepair()) {
            player.sendMessage("§cYour rank does not include the repair perk!");
            return;
        }

        Item handItem = player.getInventory().getItemInHand();
        if (handItem == null || handItem.getId() == Item.AIR) {
            player.sendMessage("§cYou must hold an item to repair!");
            return;
        }

        if (!handItem.hasMeta() || handItem.getDamage() == 0) {
            player.sendMessage("§cThe item in your hand doesn't need repair!");
            return;
        }

        handItem.setDamage(0);
        player.getInventory().setItemInHand(handItem);
        player.sendMessage("§aYour item has been repaired!");
    }

    /**
     * /hat - Wear the held item as a hat (helmet slot).
     * Available for VIP+ ranks.
     */
    private void handleHat(Player player, DonationRank rank) {
        if (!rank.hasHat()) {
            player.sendMessage("§cYour rank does not include the hat perk!");
            return;
        }

        Item handItem = player.getInventory().getItemInHand();
        if (handItem == null || handItem.getId() == Item.AIR) {
            player.sendMessage("§cYou must hold an item to wear as a hat!");
            return;
        }

        Item helmetItem = player.getInventory().getHelmet();
        // Swap: put held item in helmet slot, put helmet in hand
        player.getInventory().setHelmet(handItem);
        player.getInventory().setItemInHand(helmetItem);
        player.sendMessage("§aEnjoy your new hat!");
    }

    /**
     * /workbench (or /wb) - Open a crafting table interface.
     * Available for VIP+ ranks.
     */
    private void handleWorkbench(Player player, DonationRank rank) {
        if (!rank.hasWorkbench()) {
            player.sendMessage("§cYour rank does not include the workbench perk!");
            return;
        }

        // Open a crafting table window for the player
        // Since CraftingInventory doesn't exist, use a simple message approach
        // or try to open via the PlayerUIInventory
        try {
            // Use the crafting inventory from the player's UI inventory
            player.addWindow(player.getCraftingGrid()); // Opens the 2x2 crafting grid
            player.sendMessage("§aCrafting table opened!");
        } catch (Exception e) {
            player.sendMessage("§cCould not open crafting table. Please use a physical crafting table.");
            plugin.getLogger().warning("Failed to open workbench for " + player.getName() + ": " + e.getMessage());
        }
    }

    /**
     * /enderchest (or /ec) - Open the player's ender chest anywhere.
     * Available for Premium+ ranks.
     */
    private void handleEnderchest(Player player, DonationRank rank) {
        if (!rank.hasEnderchest()) {
            player.sendMessage("§cYour rank does not include the ender chest perk!");
            return;
        }

        player.addWindow(player.getEnderChestInventory());
        player.sendMessage("§aEnder chest opened!");
    }

    /**
     * /back - Return to the player's last death location.
     * Available for Premium+ ranks.
     */
    private void handleBack(Player player, DonationRank rank) {
        if (!rank.hasBack()) {
            player.sendMessage("§cYour rank does not include the back perk!");
            return;
        }

        String deathLocationStr = plugin.getDonationManager().getDeathLocation(player.getUniqueId());
        if (deathLocationStr == null) {
            player.sendMessage("§cNo death location found!");
            return;
        }

        // Parse the location string: "levelName:x:y:z"
        String[] parts = deathLocationStr.split(":");
        if (parts.length != 4) {
            player.sendMessage("§cInvalid death location data!");
            return;
        }

        try {
            String levelName = parts[0];
            double x = Double.parseDouble(parts[1]);
            double y = Double.parseDouble(parts[2]);
            double z = Double.parseDouble(parts[3]);

            cn.nukkit.level.Level level = plugin.getServer().getLevelByName(levelName);
            if (level == null) {
                player.sendMessage("§cThe world where you died is not loaded!");
                return;
            }

            Position deathPos = new Position(x, y, z, level);
            player.teleport(deathPos);
            plugin.getDonationManager().removeDeathLocation(player.getUniqueId());
            player.sendMessage("§aTeleported to your death location!");
        } catch (NumberFormatException e) {
            player.sendMessage("§cInvalid death location data!");
        }
    }
}
