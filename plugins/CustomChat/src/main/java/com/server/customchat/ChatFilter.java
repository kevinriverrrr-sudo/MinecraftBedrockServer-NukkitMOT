package com.server.customchat;

import cn.nukkit.Player;
import cn.nukkit.utils.TextFormat;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handles profanity filtering and anti-advertising detection.
 */
public class ChatFilter {

    private final CustomChatPlugin plugin;
    private boolean enabled;
    private List<String> blockedWords;
    private String replacement;
    private boolean antiAdEnabled;
    private boolean blockIps;
    private boolean blockDomains;

    // Regex pattern for IP addresses (e.g., 192.168.1.1, 123.45.67.89:25565)
    private static final Pattern IP_PATTERN = Pattern.compile(
            "\\b(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3})(:\\d{1,5})?\\b"
    );

    // Regex pattern for domain names (e.g., play.example.com, example.net)
    private static final Pattern DOMAIN_PATTERN = Pattern.compile(
            "\\b([a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,}\\b"
    );

    // Known allowed domains that should not be blocked (our own server)
    private List<String> allowedDomains;

    public ChatFilter(CustomChatPlugin plugin) {
        this.plugin = plugin;
        this.blockedWords = new ArrayList<>();
        this.allowedDomains = new ArrayList<>();
        loadConfig();
    }

    /**
     * Load filter settings from the plugin config.
     */
    public void loadConfig() {
        this.enabled = plugin.getConfig().getBoolean("filter.enabled", true);
        this.replacement = plugin.getConfig().getString("filter.replacement", "*");
        this.antiAdEnabled = plugin.getConfig().getBoolean("filter.anti-ad.enabled", true);
        this.blockIps = plugin.getConfig().getBoolean("filter.anti-ad.block-ips", true);
        this.blockDomains = plugin.getConfig().getBoolean("filter.anti-ad.block-domains", true);

        this.blockedWords.clear();
        List<String> words = plugin.getConfig().getStringList("filter.blocked-words");
        if (words != null) {
            for (String word : words) {
                this.blockedWords.add(word.toLowerCase());
            }
        }

        this.allowedDomains.clear();
        // Add common safe domains that shouldn't be blocked
        this.allowedDomains.add("minecraft.net");
        this.allowedDomains.add("mojang.com");
        this.allowedDomains.add("microsoft.com");
    }

    /**
     * Filter a message, replacing blocked words and detecting ads.
     * Returns the filtered message, or null if the message should be blocked entirely.
     *
     * @param message The original message
     * @param player  The player who sent the message (for bypass check)
     * @return Filtered message, or null if blocked
     */
    public String filter(String message, Player player) {
        if (!enabled) {
            return message;
        }

        // Bypass permission check
        if (player != null && player.hasPermission("chat.bypass.filter")) {
            return message;
        }

        String filtered = message;

        // Check for advertising first
        if (antiAdEnabled) {
            String adCheck = checkAdvertising(filtered);
            if (adCheck == null) {
                // Message contains advertising, block it
                return null;
            }
            filtered = adCheck;
        }

        // Filter profanity
        filtered = filterProfanity(filtered);

        return filtered;
    }

    /**
     * Replace blocked words with the replacement character string.
     *
     * @param message The message to filter
     * @return The filtered message with profanity replaced
     */
    private String filterProfanity(String message) {
        String filtered = message;
        // Strip color codes for checking so players can't bypass with §lword§r
        String stripped = TextFormat.clean(filtered).toLowerCase();

        for (String word : blockedWords) {
            if (stripped.contains(word)) {
                // Build replacement string of same length
                String replacementStr = buildReplacement(word.length());
                // Case-insensitive replacement in the original message
                filtered = filtered.replaceAll("(?i)" + Pattern.quote(word), replacementStr);
                // Also update stripped for subsequent checks
                stripped = TextFormat.clean(filtered).toLowerCase();
            }
        }

        return filtered;
    }

    /**
     * Build a replacement string of the given length using the replacement character.
     *
     * @param length The length of the original word
     * @return A string of replacement characters
     */
    private String buildReplacement(int length) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append(replacement);
        }
        return sb.toString();
    }

    /**
     * Check a message for IP addresses and domain names (advertising).
     * Returns the message if clean, or null if advertising is detected.
     *
     * @param message The message to check
     * @return The original message if clean, null if advertising detected
     */
    private String checkAdvertising(String message) {
        // Strip color codes before checking
        String stripped = TextFormat.clean(message);

        if (blockIps) {
            Matcher ipMatcher = IP_PATTERN.matcher(stripped);
            if (ipMatcher.find()) {
                // Check if it's a localhost/private IP (allow those)
                String ip = ipMatcher.group(1);
                if (!isPrivateIp(ip)) {
                    return null; // Block the message
                }
            }
        }

        if (blockDomains) {
            Matcher domainMatcher = DOMAIN_PATTERN.matcher(stripped);
            while (domainMatcher.find()) {
                String domain = domainMatcher.group().toLowerCase();
                if (!isAllowedDomain(domain)) {
                    return null; // Block the message
                }
            }
        }

        return message;
    }

    /**
     * Check if an IP address is a private/local address that should be allowed.
     *
     * @param ip The IP address to check
     * @return true if the IP is private/localhost
     */
    private boolean isPrivateIp(String ip) {
        if (ip.equals("127.0.0.1") || ip.equals("0.0.0.0") || ip.equals("localhost")) {
            return true;
        }
        // 10.x.x.x
        if (ip.startsWith("10.")) {
            return true;
        }
        // 172.16.x.x - 172.31.x.x
        if (ip.startsWith("172.")) {
            String[] parts = ip.split("\\.");
            if (parts.length >= 2) {
                try {
                    int second = Integer.parseInt(parts[1]);
                    if (second >= 16 && second <= 31) {
                        return true;
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }
        // 192.168.x.x
        if (ip.startsWith("192.168.")) {
            return true;
        }
        return false;
    }

    /**
     * Check if a domain is in the allowed list.
     *
     * @param domain The domain to check
     * @return true if the domain is allowed
     */
    private boolean isAllowedDomain(String domain) {
        for (String allowed : allowedDomains) {
            if (domain.equals(allowed) || domain.endsWith("." + allowed)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if a message contains profanity without filtering it.
     *
     * @param message The message to check
     * @return true if the message contains blocked words
     */
    public boolean containsProfanity(String message) {
        if (!enabled) {
            return false;
        }
        String stripped = TextFormat.clean(message).toLowerCase();
        for (String word : blockedWords) {
            if (stripped.contains(word)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if a message contains advertising without blocking it.
     *
     * @param message The message to check
     * @return true if the message contains advertising
     */
    public boolean containsAdvertising(String message) {
        if (!antiAdEnabled) {
            return false;
        }
        String stripped = TextFormat.clean(message);

        if (blockIps) {
            Matcher ipMatcher = IP_PATTERN.matcher(stripped);
            if (ipMatcher.find()) {
                String ip = ipMatcher.group(1);
                if (!isPrivateIp(ip)) {
                    return true;
                }
            }
        }

        if (blockDomains) {
            Matcher domainMatcher = DOMAIN_PATTERN.matcher(stripped);
            while (domainMatcher.find()) {
                String domain = domainMatcher.group().toLowerCase();
                if (!isAllowedDomain(domain)) {
                    return true;
                }
            }
        }

        return false;
    }

    // Getters and setters
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<String> getBlockedWords() {
        return new ArrayList<>(blockedWords);
    }

    public void addBlockedWord(String word) {
        if (!blockedWords.contains(word.toLowerCase())) {
            blockedWords.add(word.toLowerCase());
        }
    }

    public void removeBlockedWord(String word) {
        blockedWords.remove(word.toLowerCase());
    }

    public boolean isAntiAdEnabled() {
        return antiAdEnabled;
    }

    public void setAntiAdEnabled(boolean antiAdEnabled) {
        this.antiAdEnabled = antiAdEnabled;
    }
}
