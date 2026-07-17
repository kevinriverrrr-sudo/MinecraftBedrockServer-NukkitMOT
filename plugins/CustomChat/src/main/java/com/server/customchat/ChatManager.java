package com.server.customchat;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.level.Position;
import cn.nukkit.utils.TextFormat;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Core chat manager that handles chat formatting, channel management,
 * player mentions, and URL detection.
 */
public class ChatManager {

    private final CustomChatPlugin plugin;

    // Player name (lowercase) -> current channel name
    private final Map<String, String> playerChannels = new HashMap<>();

    // Player name (lowercase) -> last channel message timestamp (for channel cooldowns)
    private final Map<String, Map<String, Long>> playerChannelCooldowns = new HashMap<>();

    // Registered channels
    private final Map<String, ChatChannel> channels = new LinkedHashMap<>();

    // Group formats from config
    private final Map<String, String> groupFormats = new HashMap<>();

    // Mention settings
    private boolean mentionsEnabled;
    private String mentionFormat;
    private String mentionSound;

    // URL detection pattern
    private static final Pattern URL_PATTERN = Pattern.compile(
            "(https?://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+)",
            Pattern.CASE_INSENSITIVE
    );

    public ChatManager(CustomChatPlugin plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    /**
     * Load all chat configuration from config.yml.
     */
    public void loadConfig() {
        // Load group formats
        groupFormats.clear();
        Map<String, Object> formats = plugin.getConfig().getSection("formats");
        if (formats != null) {
            for (Map.Entry<String, Object> entry : formats.entrySet()) {
                groupFormats.put(entry.getKey().toLowerCase(), String.valueOf(entry.getValue()));
            }
        }

        // Load channels
        channels.clear();
        Map<String, Object> channelsSection = plugin.getConfig().getSection("channels");
        if (channelsSection != null) {
            for (Map.Entry<String, Object> entry : channelsSection.entrySet()) {
                String channelName = entry.getKey().toLowerCase();
                Object value = entry.getValue();
                if (value instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> channelData = (Map<String, Object>) value;
                    String format = String.valueOf(channelData.getOrDefault("format", "{group_format}"));
                    String permission = String.valueOf(channelData.getOrDefault("permission", ""));
                    int radius = Integer.parseInt(String.valueOf(channelData.getOrDefault("radius", "-1")));
                    int cooldown = Integer.parseInt(String.valueOf(channelData.getOrDefault("cooldown", "0")));

                    channels.put(channelName, new ChatChannel(channelName, format, permission, radius, cooldown));
                }
            }
        }

        // Load mention settings
        this.mentionsEnabled = plugin.getConfig().getBoolean("mentions.enabled", true);
        this.mentionFormat = plugin.getConfig().getString("mentions.format", "§e@{player}");
        this.mentionSound = plugin.getConfig().getString("mentions.sound", "random.orb");
    }

    /**
     * Format a chat message for a player.
     *
     * @param player  The player sending the message
     * @param message The raw message content
     * @return The formatted message string, or null if the message should be blocked
     */
    public String formatChatMessage(Player player, String message) {
        // Get the player's current channel
        String channelName = getPlayerChannel(player);
        ChatChannel channel = channels.get(channelName);

        if (channel == null) {
            channel = channels.get("global");
            if (channel == null) {
                // Fallback format
                return TextFormat.WHITE + player.getDisplayName() + " §7» §f" + message;
            }
        }

        // Check channel permission
        if (channel.hasPermission() && !player.hasPermission(channel.getPermission())) {
            player.sendMessage(TextFormat.RED + "You don't have permission to use the " + channel.getName() + " channel.");
            return null;
        }

        // Check channel cooldown
        if (channel.hasCooldown()) {
            String cooldownError = checkChannelCooldown(player, channel);
            if (cooldownError != null) {
                player.sendMessage(cooldownError);
                return null;
            }
        }

        // Process color codes in the message for permitted players
        message = processColorCodes(player, message);

        // Process formatting codes (bold, italic, etc.) for permitted players
        message = processFormattingCodes(player, message);

        // Process mentions (@player)
        message = processMentions(message);

        // Process URLs (make them clickable-looking)
        message = processUrls(message);

        // Get the player's group, prefix, and suffix
        String group = getPlayerGroup(player);
        String prefix = getPlayerPrefix(player);
        String suffix = getPlayerSuffix(player);
        String displayName = plugin.getNickManager().getNick(player);

        // Get the group format
        String groupFormat = groupFormats.getOrDefault(group, groupFormats.get("default"));
        if (groupFormat == null) {
            groupFormat = "§7{player} §7» §f{message}";
        }

        // Build the final format from the channel
        String channelFormat = channel.getFormat();

        // Replace {group_format} in channel format with the group format
        String finalFormat;
        if (channelFormat.contains("{group_format}")) {
            finalFormat = channelFormat.replace("{group_format}", groupFormat);
        } else {
            // Channel has its own complete format
            finalFormat = channelFormat;
        }

        // Replace all placeholders
        finalFormat = finalFormat
                .replace("{prefix}", prefix != null ? prefix : "")
                .replace("{suffix}", suffix != null ? suffix : "")
                .replace("{player}", player.getName())
                .replace("{nick}", displayName)
                .replace("{message}", message);

        // Record channel cooldown
        if (channel.hasCooldown()) {
            recordChannelCooldown(player, channel);
        }

        return finalFormat;
    }

    /**
     * Broadcast a formatted chat message to the appropriate recipients
     * based on the player's current channel.
     *
     * @param player  The player sending the message
     * @param message The formatted message
     */
    public void broadcastChatMessage(Player player, String message) {
        String channelName = getPlayerChannel(player);
        ChatChannel channel = channels.get(channelName);

        if (channel == null) {
            // Default: broadcast to all
            plugin.getServer().broadcastMessage(message);
            return;
        }

        if (channel.isLocal()) {
            // Local chat: only players within the radius
            broadcastLocalMessage(player, message, channel.getRadius());
        } else {
            // Global: broadcast to all players in the channel
            // For staff channel, only players with permission see it
            if (channel.hasPermission()) {
                for (Player online : plugin.getServer().getOnlinePlayers().values()) {
                    if (online.hasPermission(channel.getPermission())) {
                        online.sendMessage(message);
                    }
                }
            } else {
                plugin.getServer().broadcastMessage(message);
            }
        }
    }

    /**
     * Broadcast a message to players within a certain radius of the sender.
     *
     * @param sender  The sender
     * @param message The formatted message
     * @param radius  The radius in blocks
     */
    private void broadcastLocalMessage(Player sender, String message, int radius) {
        Position senderPos = sender.getPosition();
        boolean anyoneHeard = false;

        for (Player online : plugin.getServer().getOnlinePlayers().values()) {
            // Check if in the same level
            if (!online.getLevel().equals(sender.getLevel())) {
                continue;
            }

            // Calculate distance
            double distance = online.getPosition().distance(senderPos);
            if (distance <= radius) {
                online.sendMessage(message);
                anyoneHeard = true;
            }
        }

        // If no one heard, tell the sender
        if (!anyoneHeard) {
            sender.sendMessage(TextFormat.YELLOW + "No one is nearby to hear you! (Local chat radius: " + radius + " blocks)");
        }
    }

    /**
     * Process color codes in a message. Only players with chat.color permission
     * can use color codes. Converts & codes to § codes.
     *
     * @param player  The player
     * @param message The message
     * @return The message with color codes processed
     */
    private String processColorCodes(Player player, String message) {
        if (!player.hasPermission("chat.color")) {
            // Strip all color codes from message
            return stripColorCodes(message);
        }

        // Translate & color codes to §
        return translateColorCodes(message);
    }

    /**
     * Process formatting codes (bold, italic, underline, etc.) in a message.
     * Only players with the relevant permissions can use formatting codes.
     *
     * @param player  The player
     * @param message The message
     * @return The message with formatting codes processed
     */
    private String processFormattingCodes(Player player, String message) {
        char[] chars = message.toCharArray();

        for (int i = 0; i < chars.length - 1; i++) {
            if (chars[i] == '&' || chars[i] == '\u00a7') {
                char code = Character.toLowerCase(chars[i + 1]);

                switch (code) {
                    case 'l': // Bold
                        if (!player.hasPermission("chat.bold")) {
                            chars[i] = ' ';
                            chars[i + 1] = ' ';
                        }
                        break;
                    case 'm': // Strikethrough
                        if (!player.hasPermission("chat.bold")) {
                            chars[i] = ' ';
                            chars[i + 1] = ' ';
                        }
                        break;
                    case 'n': // Underline
                        if (!player.hasPermission("chat.bold")) {
                            chars[i] = ' ';
                            chars[i + 1] = ' ';
                        }
                        break;
                    case 'o': // Italic
                        if (!player.hasPermission("chat.italic")) {
                            chars[i] = ' ';
                            chars[i + 1] = ' ';
                        }
                        break;
                    case 'k': // Obfuscated
                        if (!player.hasPermission("chat.bold")) {
                            chars[i] = ' ';
                            chars[i + 1] = ' ';
                        }
                        break;
                    case 'r': // Reset - always allowed
                        break;
                    default:
                        break;
                }
            }
        }

        String result = new String(chars);

        // Now translate & to § for allowed codes
        if (player.hasPermission("chat.color")) {
            result = translateColorCodes(result);
        } else {
            // Strip color codes but keep formatting codes that are allowed
            result = stripColorCodesKeepFormatting(player, result);
        }

        return result;
    }

    /**
     * Process @mentions in a message. Highlights the mentioned player's name
     * and plays a sound to them.
     *
     * @param message The message
     * @return The message with mentions highlighted
     */
    private String processMentions(String message) {
        if (!mentionsEnabled) {
            return message;
        }

        // Find all @playername patterns
        Pattern mentionPattern = Pattern.compile("@(\\w+)");
        Matcher matcher = mentionPattern.matcher(message);

        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String mentionedName = matcher.group(1);
            Player mentionedPlayer = plugin.getServer().getPlayer(mentionedName);

            if (mentionedPlayer != null && mentionedPlayer.isOnline()) {
                // Replace with highlighted mention
                String highlighted = mentionFormat.replace("{player}", mentionedPlayer.getName());
                matcher.appendReplacement(sb, Matcher.quoteReplacement(highlighted));

                // Play sound to the mentioned player
                try {
                    mentionedPlayer.getLevel().addSound(mentionedPlayer.getPosition(), cn.nukkit.level.Sound.valueOf(
                            mentionSound.replace(".", "_").toUpperCase()
                    ));
                } catch (IllegalArgumentException e) {
                    // Fallback to orb sound if the configured sound doesn't exist
                    mentionedPlayer.getLevel().addSound(mentionedPlayer.getPosition(), cn.nukkit.level.Sound.RANDOM_ORB);
                }
            }
        }
        matcher.appendTail(sb);

        return sb.toString();
    }

    /**
     * Process URLs in a message, making them visually distinct.
     *
     * @param message The message
     * @return The message with URLs highlighted
     */
    private String processUrls(String message) {
        Matcher matcher = URL_PATTERN.matcher(message);
        StringBuffer sb = new StringBuffer();

        while (matcher.find()) {
            String url = matcher.group(1);
            // Highlight the URL with underline and color
            String highlighted = TextFormat.AQUA + TextFormat.UNDERLINE.toString() + url + TextFormat.RESET.toString();
            matcher.appendReplacement(sb, Matcher.quoteReplacement(highlighted));
        }
        matcher.appendTail(sb);

        return sb.toString();
    }

    /**
     * Get the player's group from CustomPerms API, or fall back to OP-based detection.
     *
     * @param player The player
     * @return The player's group name
     */
    public String getPlayerGroup(Player player) {
        try {
            // Try CustomPerms API
            Object api = Class.forName("com.server.customperms.CustomPermsPlugin")
                    .getMethod("getAPI")
                    .invoke(null);

            if (api != null) {
                Object permManager = api.getClass().getMethod("getPermissionManager").invoke(api);
                if (permManager != null) {
                    Object group = permManager.getClass().getMethod("getGroup", String.class)
                            .invoke(permManager, player.getName());
                    if (group != null) {
                        String groupName = (String) group.getClass().getMethod("getName").invoke(group);
                        if (groupName != null && !groupName.isEmpty()) {
                            return groupName.toLowerCase();
                        }
                    }
                }
            }
        } catch (Exception e) {
            // CustomPerms not available, fall through to OP-based detection
        }

        // Fallback: OP-based detection
        if (player.isOp()) {
            return "admin";
        }

        // Check for common permission-based groups
        if (player.hasPermission("group.premium")) {
            return "premium";
        }
        if (player.hasPermission("group.vip")) {
            return "vip";
        }

        return "default";
    }

    /**
     * Get the player's prefix from CustomPerms API.
     *
     * @param player The player
     * @return The player's prefix, or empty string
     */
    public String getPlayerPrefix(Player player) {
        try {
            Object api = Class.forName("com.server.customperms.CustomPermsPlugin")
                    .getMethod("getAPI")
                    .invoke(null);

            if (api != null) {
                Object permManager = api.getClass().getMethod("getPermissionManager").invoke(api);
                if (permManager != null) {
                    Object group = permManager.getClass().getMethod("getGroup", String.class)
                            .invoke(permManager, player.getName());
                    if (group != null) {
                        String prefix = (String) group.getClass().getMethod("getPrefix").invoke(group);
                        if (prefix != null) {
                            return translateColorCodes(prefix);
                        }
                    }
                }
            }
        } catch (Exception e) {
            // CustomPerms not available
        }

        // Fallback: OP prefix
        if (player.isOp()) {
            return "§c[Admin] ";
        }

        return "";
    }

    /**
     * Get the player's suffix from CustomPerms API.
     *
     * @param player The player
     * @return The player's suffix, or empty string
     */
    public String getPlayerSuffix(Player player) {
        try {
            Object api = Class.forName("com.server.customperms.CustomPermsPlugin")
                    .getMethod("getAPI")
                    .invoke(null);

            if (api != null) {
                Object permManager = api.getClass().getMethod("getPermissionManager").invoke(api);
                if (permManager != null) {
                    Object group = permManager.getClass().getMethod("getGroup", String.class)
                            .invoke(permManager, player.getName());
                    if (group != null) {
                        String suffix = (String) group.getClass().getMethod("getSuffix").invoke(group);
                        if (suffix != null) {
                            return translateColorCodes(suffix);
                        }
                    }
                }
            }
        } catch (Exception e) {
            // CustomPerms not available
        }

        return "";
    }

    /**
     * Set a player's current chat channel.
     *
     * @param player      The player
     * @param channelName The channel name
     * @return true if the channel was switched successfully
     */
    public boolean setPlayerChannel(Player player, String channelName) {
        channelName = channelName.toLowerCase();
        ChatChannel channel = channels.get(channelName);

        if (channel == null) {
            player.sendMessage(TextFormat.RED + "Unknown channel: " + TextFormat.WHITE + channelName);
            player.sendMessage(TextFormat.YELLOW + "Available channels: " + TextFormat.WHITE + String.join(", ", channels.keySet()));
            return false;
        }

        // Check permission
        if (channel.hasPermission() && !player.hasPermission(channel.getPermission())) {
            player.sendMessage(TextFormat.RED + "You don't have permission to join the " + channel.getName() + " channel.");
            return false;
        }

        String previousChannel = playerChannels.put(player.getName().toLowerCase(), channelName);
        player.sendMessage(TextFormat.GREEN + "You switched to the " + TextFormat.WHITE + channelName + TextFormat.GREEN + " channel.");

        if (previousChannel != null && !previousChannel.equals(channelName)) {
            player.sendMessage(TextFormat.GRAY + "Previous channel: " + previousChannel);
        }

        return true;
    }

    /**
     * Get a player's current chat channel name.
     *
     * @param player The player
     * @return The channel name (defaults to "global")
     */
    public String getPlayerChannel(Player player) {
        return playerChannels.getOrDefault(player.getName().toLowerCase(), "global");
    }

    /**
     * Check if a player is on cooldown for a specific channel.
     *
     * @param player  The player
     * @param channel The channel
     * @return Error message or null if not on cooldown
     */
    private String checkChannelCooldown(Player player, ChatChannel channel) {
        Map<String, Long> cooldowns = playerChannelCooldowns.get(player.getName().toLowerCase());
        if (cooldowns == null) {
            return null;
        }

        Long lastUsed = cooldowns.get(channel.getName());
        if (lastUsed == null) {
            return null;
        }

        long elapsed = System.currentTimeMillis() - lastUsed;
        long cooldownMs = channel.getCooldown() * 1000L;

        if (elapsed < cooldownMs) {
            long remaining = ((cooldownMs - elapsed) / 1000) + 1;
            return TextFormat.RED + "You must wait " + remaining + " seconds before using the " + channel.getName() + " channel again.";
        }

        return null;
    }

    /**
     * Record that a player used a channel (for cooldown tracking).
     *
     * @param player  The player
     * @param channel The channel
     */
    private void recordChannelCooldown(Player player, ChatChannel channel) {
        Map<String, Long> cooldowns = playerChannelCooldowns.computeIfAbsent(
                player.getName().toLowerCase(), k -> new HashMap<>());
        cooldowns.put(channel.getName(), System.currentTimeMillis());
    }

    /**
     * Translate & color codes to § color codes.
     *
     * @param input The input string
     * @return The translated string
     */
    public String translateColorCodes(String input) {
        if (input == null) return "";
        char[] chars = input.toCharArray();
        for (int i = 0; i < chars.length - 1; i++) {
            if (chars[i] == '&') {
                char next = chars[i + 1];
                if ("0123456789abcdefklmnorABCDEFKLMNOR".indexOf(next) >= 0) {
                    chars[i] = '\u00a7';
                    chars[i + 1] = Character.toLowerCase(next);
                }
            }
        }
        return new String(chars);
    }

    /**
     * Strip all color codes from a string.
     *
     * @param input The input string
     * @return The string with color codes removed
     */
    private String stripColorCodes(String input) {
        if (input == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '\u00a7' || c == '&') {
                // Skip the color code character and the next character
                i++;
                continue;
            }
            sb.append(c);
        }
        return sb.toString();
    }

    /**
     * Strip color codes but keep formatting codes that the player has permission for.
     *
     * @param player The player
     * @param input  The input string
     * @return The processed string
     */
    private String stripColorCodesKeepFormatting(Player player, String input) {
        if (input == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '\u00a7' || c == '&') {
                if (i + 1 < input.length()) {
                    char next = Character.toLowerCase(input.charAt(i + 1));
                    // Keep formatting codes if permitted
                    boolean keep = false;
                    if (next == 'l' && player.hasPermission("chat.bold")) keep = true;
                    if (next == 'o' && player.hasPermission("chat.italic")) keep = true;
                    if ((next == 'n' || next == 'm' || next == 'k') && player.hasPermission("chat.bold")) keep = true;
                    if (next == 'r') keep = true;

                    if (keep) {
                        sb.append('\u00a7').append(next);
                    }
                    i++; // Skip the next character
                }
                continue;
            }
            sb.append(c);
        }
        return sb.toString();
    }

    /**
     * Remove a player's data when they disconnect.
     *
     * @param playerName The player's name
     */
    public void removePlayer(String playerName) {
        playerChannels.remove(playerName.toLowerCase());
        playerChannelCooldowns.remove(playerName.toLowerCase());
    }

    /**
     * Get all registered channel names.
     *
     * @return Set of channel names
     */
    public Set<String> getChannelNames() {
        return channels.keySet();
    }

    /**
     * Get a channel by name.
     *
     * @param name The channel name
     * @return The ChatChannel, or null
     */
    public ChatChannel getChannel(String name) {
        return channels.get(name.toLowerCase());
    }

    /**
     * Get the group format for a specific group.
     *
     * @param group The group name
     * @return The format string, or default format
     */
    public String getGroupFormat(String group) {
        return groupFormats.getOrDefault(group != null ? group.toLowerCase() : "default",
                groupFormats.getOrDefault("default", "§7{player} §7» §f{message}"));
    }
}
