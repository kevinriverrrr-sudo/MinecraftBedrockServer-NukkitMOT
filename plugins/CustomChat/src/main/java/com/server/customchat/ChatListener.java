package com.server.customchat;

import cn.nukkit.Player;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.EventPriority;
import cn.nukkit.event.Listener;
import cn.nukkit.event.player.PlayerChatEvent;
import cn.nukkit.event.player.PlayerJoinEvent;
import cn.nukkit.event.player.PlayerQuitEvent;
import cn.nukkit.utils.TextFormat;

/**
 * Event listener for chat, join, and quit events.
 * Handles the main chat pipeline: filtering, formatting, and broadcasting.
 */
public class ChatListener implements Listener {

    private final CustomChatPlugin plugin;

    public ChatListener(CustomChatPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Handle player chat events. We cancel the default chat and re-broadcast
     * with our custom formatting, channel system, and filters.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerChat(PlayerChatEvent event) {
        // Don't handle cancelled events from other plugins unless we need to
        if (event.isCancelled()) {
            return;
        }

        Player player = event.getPlayer();
        String message = event.getMessage();

        // Cancel the default chat event - we'll broadcast manually
        event.setCancelled(true);

        // 1. Anti-spam check
        AntiSpamManager antiSpam = plugin.getAntiSpamManager();
        String spamError = antiSpam.checkMessage(player, message);
        if (spamError != null) {
            player.sendMessage(spamError);
            return;
        }

        // 2. Chat filter (profanity and advertising)
        ChatFilter filter = plugin.getChatFilter();
        String filteredMessage = filter.filter(message, player);
        if (filteredMessage == null) {
            // Message was blocked by the filter (likely advertising)
            player.sendMessage(TextFormat.RED + "Your message was blocked by the chat filter.");
            if (filter.containsProfanity(message)) {
                player.sendMessage(TextFormat.YELLOW + "Reason: Inappropriate language");
            }
            if (filter.containsAdvertising(message)) {
                player.sendMessage(TextFormat.YELLOW + "Reason: Advertising is not allowed");
            }
            return;
        }

        // 3. Format the chat message
        ChatManager chatManager = plugin.getChatManager();
        String formattedMessage = chatManager.formatChatMessage(player, filteredMessage);
        if (formattedMessage == null) {
            // Formatting returned null (e.g., channel permission denied)
            return;
        }

        // 4. Broadcast the formatted message to the appropriate recipients
        chatManager.broadcastChatMessage(player, formattedMessage);

        // 5. Log the chat message to console
        plugin.getLogger().info("[Chat] " + player.getName() + " [" + chatManager.getPlayerChannel(player) + "]: " + TextFormat.clean(message));
    }

    /**
     * Handle player join events. Restore their nickname if they had one.
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Restore nickname if the player had one set
        NickManager nickManager = plugin.getNickManager();
        if (nickManager.hasNick(player)) {
            String nick = nickManager.getNick(player);
            player.setDisplayName(nick);
        }

        // Set default channel if not already set
        ChatManager chatManager = plugin.getChatManager();
        String currentChannel = chatManager.getPlayerChannel(player);
        if (currentChannel == null || currentChannel.isEmpty()) {
            // Default is already "global" via getPlayerChannel defaults
        }

        plugin.getLogger().info("[CustomChat] " + player.getName() + " joined. Channel: " + chatManager.getPlayerChannel(player));
    }

    /**
     * Handle player quit events. Clean up player data from managers.
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        String playerName = player.getName();

        // Clean up anti-spam data
        plugin.getAntiSpamManager().removePlayer(playerName);

        // Note: We keep nicknames, ignore lists, and channel selections
        // persistent across reconnects by not removing them.
        // If desired, uncomment the following lines to clear on disconnect:
        // plugin.getChatManager().removePlayer(playerName);
        // plugin.getIgnoreManager().removePlayer(playerName);
        // plugin.getNickManager().removePlayer(playerName);

        plugin.getLogger().info("[CustomChat] " + playerName + " disconnected.");
    }
}
