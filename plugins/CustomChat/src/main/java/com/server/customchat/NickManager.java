package com.server.customchat;

import cn.nukkit.Player;
import cn.nukkit.utils.TextFormat;

import java.util.*;

/**
 * Manages player nicknames, including setting, resetting,
 * color support, and blocked name checking.
 */
public class NickManager {

    private final CustomChatPlugin plugin;

    // Player name (lowercase) -> nickname
    private final Map<String, String> nicknames = new HashMap<>();

    // Config values
    private int maxLength;
    private int minLength;
    private boolean allowColors;
    private List<String> blockedNames;

    public NickManager(CustomChatPlugin plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    /**
     * Load nickname settings from the plugin config.
     */
    public void loadConfig() {
        this.maxLength = plugin.getConfig().getInt("nickname.max-length", 16);
        this.minLength = plugin.getConfig().getInt("nickname.min-length", 3);
        this.allowColors = plugin.getConfig().getBoolean("nickname.allow-colors", true);
        this.blockedNames = new ArrayList<>();
        List<String> names = plugin.getConfig().getStringList("nickname.blocked-names");
        if (names != null) {
            for (String name : names) {
                this.blockedNames.add(name.toLowerCase());
            }
        }
    }

    /**
     * Set a player's nickname.
     *
     * @param player   The player
     * @param nickname The desired nickname
     * @return true if the nickname was set successfully
     */
    public boolean setNick(Player player, String nickname) {
        // Permission check
        if (!player.hasPermission("chat.nick")) {
            player.sendMessage(TextFormat.RED + "You don't have permission to set a nickname.");
            return false;
        }

        // Strip color codes for validation
        String cleanNick = TextFormat.clean(nickname);

        // Check minimum length
        if (cleanNick.length() < minLength) {
            player.sendMessage(TextFormat.RED + "Nickname is too short! Minimum " + minLength + " characters.");
            return false;
        }

        // Check maximum length
        if (cleanNick.length() > maxLength) {
            player.sendMessage(TextFormat.RED + "Nickname is too long! Maximum " + maxLength + " characters.");
            return false;
        }

        // Check for blocked names
        if (isBlockedName(cleanNick)) {
            player.sendMessage(TextFormat.RED + "That nickname is not allowed!");
            return false;
        }

        // Check for colors if not allowed
        if (!allowColors && !cleanNick.equals(nickname)) {
            player.sendMessage(TextFormat.RED + "Color codes are not allowed in nicknames.");
            return false;
        }

        // Check if color codes are used without permission
        if (allowColors && containsColorCodes(nickname) && !player.hasPermission("chat.color")) {
            player.sendMessage(TextFormat.RED + "You don't have permission to use color codes in your nickname.");
            return false;
        }

        // Check if the nickname is already taken by another player
        String nickLower = cleanNick.toLowerCase();
        for (Map.Entry<String, String> entry : nicknames.entrySet()) {
            if (!entry.getKey().equalsIgnoreCase(player.getName().toLowerCase())) {
                String existingClean = TextFormat.clean(entry.getValue()).toLowerCase();
                if (existingClean.equals(nickLower)) {
                    player.sendMessage(TextFormat.RED + "That nickname is already taken by another player!");
                    return false;
                }
            }
        }

        // Check if any online player (without nick) has this name
        for (Player online : plugin.getServer().getOnlinePlayers().values()) {
            if (!online.getName().equalsIgnoreCase(player.getName()) &&
                    online.getName().equalsIgnoreCase(cleanNick)) {
                player.sendMessage(TextFormat.RED + "That nickname belongs to another online player!");
                return false;
            }
        }

        // Set the nickname
        nicknames.put(player.getName().toLowerCase(), nickname);

        // Update the player's display name
        player.setDisplayName(nickname);

        player.sendMessage(TextFormat.GREEN + "Your nickname has been set to " + TextFormat.WHITE + nickname);
        return true;
    }

    /**
     * Reset a player's nickname back to their real name.
     *
     * @param player The player
     */
    public void resetNick(Player player) {
        String oldNick = nicknames.remove(player.getName().toLowerCase());

        if (oldNick == null) {
            player.sendMessage(TextFormat.YELLOW + "You don't have a nickname set.");
            return;
        }

        // Reset display name to the real name
        player.setDisplayName(player.getName());

        player.sendMessage(TextFormat.GREEN + "Your nickname has been reset to " + TextFormat.WHITE + player.getName());
    }

    /**
     * Get a player's nickname, or their real name if no nickname is set.
     *
     * @param player The player
     * @return The nickname or real name
     */
    public String getNick(Player player) {
        return nicknames.getOrDefault(player.getName().toLowerCase(), player.getName());
    }

    /**
     * Get a player's nickname by name.
     *
     * @param playerName The player's name
     * @return The nickname or the player name if no nickname is set
     */
    public String getNick(String playerName) {
        return nicknames.getOrDefault(playerName.toLowerCase(), playerName);
    }

    /**
     * Check if a player has a nickname set.
     *
     * @param player The player
     * @return true if the player has a nickname
     */
    public boolean hasNick(Player player) {
        return nicknames.containsKey(player.getName().toLowerCase());
    }

    /**
     * Check if a name is in the blocked list.
     *
     * @param name The name to check
     * @return true if the name is blocked
     */
    private boolean isBlockedName(String name) {
        String nameLower = name.toLowerCase();
        for (String blocked : blockedNames) {
            if (nameLower.contains(blocked)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if a string contains Minecraft color codes (§ or &).
     *
     * @param str The string to check
     * @return true if the string contains color codes
     */
    private boolean containsColorCodes(String str) {
        for (int i = 0; i < str.length() - 1; i++) {
            char c = str.charAt(i);
            if (c == '\u00a7' || c == '&') {
                char next = str.charAt(i + 1);
                if (isColorCodeChar(next)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Check if a character is a valid color code character.
     *
     * @param c The character to check
     * @return true if it's a valid color/format code
     */
    private boolean isColorCodeChar(char c) {
        return "0123456789abcdefklmnorABCDEFKLMNOR".indexOf(c) >= 0;
    }

    /**
     * Translate & color codes to § color codes in a nickname.
     *
     * @param input The input string
     * @return The translated string
     */
    public String translateColorCodes(String input) {
        if (input == null) return null;
        char[] chars = input.toCharArray();
        for (int i = 0; i < chars.length - 1; i++) {
            if (chars[i] == '&') {
                char next = chars[i + 1];
                if (isColorCodeChar(next)) {
                    chars[i] = '\u00a7';
                    chars[i + 1] = Character.toLowerCase(next);
                }
            }
        }
        return new String(chars);
    }

    /**
     * Clean up data for a player who has disconnected.
     * Note: Nicknames are kept persistent across reconnects.
     *
     * @param playerName The player's name
     */
    public void removePlayer(String playerName) {
        // Keep nicknames persistent across reconnects
        // If we wanted to clear on disconnect:
        // nicknames.remove(playerName.toLowerCase());
    }

    /**
     * Get all current nicknames (for admin purposes).
     *
     * @return Unmodifiable map of player names to nicknames
     */
    public Map<String, String> getAllNicknames() {
        return Collections.unmodifiableMap(nicknames);
    }
}
