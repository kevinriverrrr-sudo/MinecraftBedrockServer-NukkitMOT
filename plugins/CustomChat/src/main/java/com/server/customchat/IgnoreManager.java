package com.server.customchat;

import cn.nukkit.Player;
import cn.nukkit.utils.TextFormat;

import java.util.*;

/**
 * Manages the player ignore system. Players can ignore others
 * to stop receiving their messages.
 */
public class IgnoreManager {

    private final CustomChatPlugin plugin;

    // Player name -> Set of ignored player names
    private final Map<String, Set<String>> ignoreLists = new HashMap<>();

    public IgnoreManager(CustomChatPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Ignore a player. The sender will no longer see messages from the target.
     *
     * @param sender   The player doing the ignoring
     * @param target   The player to ignore
     * @return true if the ignore was successful, false if already ignored
     */
    public boolean ignore(Player sender, Player target) {
        if (sender.getName().equalsIgnoreCase(target.getName())) {
            sender.sendMessage(TextFormat.RED + "You cannot ignore yourself!");
            return false;
        }

        String senderName = sender.getName().toLowerCase();
        String targetName = target.getName().toLowerCase();

        Set<String> ignored = ignoreLists.computeIfAbsent(senderName, k -> new HashSet<>());

        if (ignored.contains(targetName)) {
            sender.sendMessage(TextFormat.YELLOW + "You are already ignoring " + TextFormat.WHITE + target.getName());
            return false;
        }

        ignored.add(targetName);
        sender.sendMessage(TextFormat.GREEN + "You are now ignoring " + TextFormat.WHITE + target.getName());
        return true;
    }

    /**
     * Unignore a player. The sender will start seeing messages from the target again.
     *
     * @param sender   The player doing the unignoring
     * @param target   The player to unignore
     * @return true if the unignore was successful, false if not ignored
     */
    public boolean unignore(Player sender, Player target) {
        String senderName = sender.getName().toLowerCase();
        String targetName = target.getName().toLowerCase();

        Set<String> ignored = ignoreLists.get(senderName);
        if (ignored == null || !ignored.contains(targetName)) {
            sender.sendMessage(TextFormat.YELLOW + "You are not ignoring " + TextFormat.WHITE + target.getName());
            return false;
        }

        ignored.remove(targetName);
        sender.sendMessage(TextFormat.GREEN + "You are no longer ignoring " + TextFormat.WHITE + target.getName());
        return true;
    }

    /**
     * Check if a player is ignoring another player.
     *
     * @param sender   The potential ignorer
     * @param target   The potentially ignored player
     * @return true if sender is ignoring target
     */
    public boolean isIgnoring(Player sender, Player target) {
        return isIgnoring(sender.getName(), target.getName());
    }

    /**
     * Check if a player is ignoring another player by name.
     *
     * @param senderName   The potential ignorer's name
     * @param targetName   The potentially ignored player's name
     * @return true if sender is ignoring target
     */
    public boolean isIgnoring(String senderName, String targetName) {
        Set<String> ignored = ignoreLists.get(senderName.toLowerCase());
        if (ignored == null) {
            return false;
        }
        return ignored.contains(targetName.toLowerCase());
    }

    /**
     * Get the list of players that a player is ignoring.
     *
     * @param player The player
     * @return Set of ignored player names (lowercase)
     */
    public Set<String> getIgnoredList(Player player) {
        return ignoreLists.getOrDefault(player.getName().toLowerCase(), Collections.emptySet());
    }

    /**
     * Remove all ignore data for a player (used when they disconnect).
     * Note: We keep the ignore list persistent, so we don't actually remove it.
     * This method is here for future persistence integration.
     *
     * @param playerName The player's name
     */
    public void removePlayer(String playerName) {
        // Keep ignore lists persistent across reconnects
        // If we wanted to clear on disconnect, we would do:
        // ignoreLists.remove(playerName.toLowerCase());
    }

    /**
     * Toggle ignore status for a player.
     *
     * @param sender The player toggling
     * @param target The player to toggle
     * @return true if now ignoring, false if now unignored
     */
    public boolean toggleIgnore(Player sender, Player target) {
        if (isIgnoring(sender, target)) {
            unignore(sender, target);
            return false;
        } else {
            ignore(sender, target);
            return true;
        }
    }
}
