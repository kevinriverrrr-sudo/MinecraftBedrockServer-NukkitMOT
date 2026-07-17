package com.server.aesthetics;

import cn.nukkit.Player;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.item.EntityFirework;
import cn.nukkit.level.Level;
import cn.nukkit.level.Sound;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.ListTag;
import cn.nukkit.utils.TextFormat;

/**
 * Manages join/quit effects including custom messages, titles,
 * action bars, sounds, and first-join fireworks.
 */
public class JoinEffectManager {

    private final ServerAestheticsPlugin plugin;

    public JoinEffectManager(ServerAestheticsPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Handles all effects when a player joins the server.
     * @param player The joining player
     * @param isFirstJoin Whether this is the player's first time joining
     */
    public void handleJoin(Player player, boolean isFirstJoin) {
        // Send join message
        sendJoinMessage(player, isFirstJoin);

        // Send welcome title
        sendWelcomeTitle(player, isFirstJoin);

        // Send action bar message
        sendActionBar(player, isFirstJoin);

        // Play join sound
        playJoinSound(player);

        // First-join special effects
        if (isFirstJoin) {
            handleFirstJoinEffects(player);
        }

        // Welcome back message for returning players
        if (!isFirstJoin) {
            sendWelcomeBackMessage(player);
        }
    }

    /**
     * Handles all effects when a player quits the server.
     * @param player The quitting player
     */
    public void handleQuit(Player player) {
        sendQuitMessage(player);
    }

    /**
     * Sends the join message to all online players.
     * Uses custom formatting with rank prefix.
     */
    private void sendJoinMessage(Player player, boolean isFirstJoin) {
        String messageTemplate;
        if (isFirstJoin) {
            messageTemplate = plugin.getConfig().getString("join.first-join-message",
                "\u00a76\u00a7l\u2605 \u00a7e{player} \u00a77joined for the first time! \u00a76\u00a7l\u2605");
        } else {
            messageTemplate = plugin.getConfig().getString("join.message",
                "\u00a7e{prefix}\u00a7e{player} \u00a77joined the game");
        }

        String message = plugin.replacePlaceholders(messageTemplate, player);
        message = TextFormat.colorize(message);

        // Broadcast to all players
        for (Player online : plugin.getServer().getOnlinePlayers().values()) {
            online.sendMessage(message);
        }
    }

    /**
     * Sends the quit message to all remaining online players.
     */
    private void sendQuitMessage(Player player) {
        String messageTemplate = plugin.getConfig().getString("quit.message",
            "\u00a77{prefix}\u00a77{player} \u00a77left the game");

        String message = plugin.replacePlaceholders(messageTemplate, player);
        message = TextFormat.colorize(message);

        // Broadcast to all remaining players
        for (Player online : plugin.getServer().getOnlinePlayers().values()) {
            if (!online.equals(player)) {
                online.sendMessage(message);
            }
        }
    }

    /**
     * Sends a welcome title and subtitle to the joining player.
     */
    private void sendWelcomeTitle(Player player, boolean isFirstJoin) {
        String titleTemplate = plugin.getConfig().getString("join.title", "\u00a76Welcome!");
        String subtitleTemplate = plugin.getConfig().getString("join.subtitle", "\u00a7e{player}, enjoy your stay!");
        int duration = plugin.getConfig().getInt("join.title-duration", 40);

        String title = plugin.replacePlaceholders(titleTemplate, player);
        String subtitle = plugin.replacePlaceholders(subtitleTemplate, player);

        title = TextFormat.colorize(title);
        subtitle = TextFormat.colorize(subtitle);

        // fadeIn = 10 ticks, stay = duration, fadeOut = 10 ticks
        int fadeIn = 10;
        int stay = duration;
        int fadeOut = 10;

        player.sendTitle(title, subtitle, fadeIn, stay, fadeOut);
    }

    /**
     * Sends an action bar message to the joining player.
     */
    private void sendActionBar(Player player, boolean isFirstJoin) {
        String messageTemplate = plugin.getConfig().getString("join.action-bar",
            "\u00a7eWelcome back, {player}!");

        if (isFirstJoin) {
            messageTemplate = "\u00a76Welcome to the server, {player}!";
        }

        String message = plugin.replacePlaceholders(messageTemplate, player);
        message = TextFormat.colorize(message);

        try {
            player.sendActionBar(message);
        } catch (NoSuchMethodError e) {
            // Fallback to sendTip if sendActionBar is not available
            player.sendTip(message);
        }
    }

    /**
     * Plays a join sound effect at the player's position.
     */
    private void playJoinSound(Player player) {
        String soundName = plugin.getConfig().getString("join.sound", "random.levelup");
        Sound sound = parseSound(soundName);

        if (sound != null) {
            try {
                player.getLevel().addSound(player.getPosition(), sound);
            } catch (Exception e) {
                plugin.getLogger().debug("Could not play join sound: " + soundName);
            }
        }
    }

    /**
     * Handles special first-join effects like fireworks and broadcasts.
     */
    private void handleFirstJoinEffects(Player player) {
        // Spawn firework
        boolean spawnFirework = plugin.getConfig().getBoolean("join.first-join-firework", true);
        if (spawnFirework) {
            // Delay firework by 20 ticks (1 second) so the player can see it
            plugin.getServer().getScheduler().scheduleDelayedTask(plugin, () -> {
                if (player.isOnline()) {
                    spawnFirework(player);
                }
            }, 20);
        }

        // Broadcast first-join announcement to other players
        String broadcastTemplate = "\u00a76\u00a7l\u2605 \u00a7e{player} \u00a77joined the server for the first time! Welcome them! \u00a76\u00a7l\u2605";
        String broadcast = plugin.replacePlaceholders(broadcastTemplate, player);
        broadcast = TextFormat.colorize(broadcast);

        for (Player online : plugin.getServer().getOnlinePlayers().values()) {
            if (!online.equals(player)) {
                online.sendMessage(broadcast);
            }
        }
    }

    /**
     * Sends a "welcome back" message to returning players.
     */
    private void sendWelcomeBackMessage(Player player) {
        // Send a personal welcome back message
        String serverName = plugin.getConfig().getString("server-name", "\u00a76\u00a7lMinecraft \u00a7r\u00a7eBedrock \u00a77Server");
        serverName = TextFormat.colorize(serverName);

        String welcomeBack = "\u00a77Welcome back to " + serverName + "\u00a77!";
        player.sendMessage(TextFormat.colorize(welcomeBack));

        // Show player count
        int online = plugin.getServer().getOnlinePlayers().size();
        player.sendMessage(TextFormat.colorize("\u00a77There are \u00a7f" + online + " \u00a77players online."));
    }

    /**
     * Spawns a firework at the player's position.
     */
    private void spawnFirework(Player player) {
        try {
            Level level = player.getLevel();
            if (level == null) return;

            // Build the firework NBT data
            CompoundTag fireworkNbt = createFireworkNBT(player);

            EntityFirework firework = new EntityFirework(
                level.getChunk(player.getFloorX() >> 4, player.getFloorZ() >> 4),
                fireworkNbt
            );

            firework.spawnToAll();

        } catch (Exception e) {
            plugin.getLogger().debug("Could not spawn firework: " + e.getMessage());

            // Fallback: Try simpler firework spawn
            trySpawnSimpleFirework(player);
        }
    }

    /**
     * Fallback method to spawn a firework using a simpler approach.
     */
    private void trySpawnSimpleFirework(Player player) {
        try {
            Level level = player.getLevel();
            if (level == null) return;

            CompoundTag nbt = Entity.getDefaultNBT(player.add(0.5, 1.0, 0.5));
            nbt.putCompound("FireworksItem", createFireworkItemTag());

            EntityFirework firework = new EntityFirework(
                level.getChunk(player.getFloorX() >> 4, player.getFloorZ() >> 4),
                nbt
            );
            firework.spawnToAll();
        } catch (Exception e) {
            plugin.getLogger().debug("Simple firework spawn also failed: " + e.getMessage());
        }
    }

    /**
     * Creates the NBT data for a firework entity at the player's position.
     */
    private CompoundTag createFireworkNBT(Player player) {
        CompoundTag nbt = Entity.getDefaultNBT(player.add(0.5, 1.0, 0.5));
        nbt.putCompound("FireworksItem", createFireworkItemTag());
        return nbt;
    }

    /**
     * Creates the firework item NBT tag with colorful explosions.
     * This tag is used inside the "FireworksItem" compound.
     */
    private CompoundTag createFireworkItemTag() {
        // Create explosion tags with different colors and types
        CompoundTag explosion1 = new CompoundTag();
        explosion1.putByte("FireworkType", 0);      // Small ball
        explosion1.putInt("FireworkColor", 0xFF5555);  // Red
        explosion1.putInt("FireworkFade", 0xFFAA00);   // Gold fade
        explosion1.putBoolean("FireworkTrail", true);
        explosion1.putBoolean("FireworkFlicker", true);

        CompoundTag explosion2 = new CompoundTag();
        explosion2.putByte("FireworkType", 1);      // Large ball
        explosion2.putInt("FireworkColor", 0x55FFFF);  // Cyan
        explosion2.putInt("FireworkFade", 0xFFFF55);   // Yellow fade
        explosion2.putBoolean("FireworkTrail", true);
        explosion2.putBoolean("FireworkFlicker", false);

        CompoundTag explosion3 = new CompoundTag();
        explosion3.putByte("FireworkType", 4);      // Burst
        explosion3.putInt("FireworkColor", 0x55FF55);  // Green
        explosion3.putInt("FireworkFade", 0xFF55FF);   // Magenta fade
        explosion3.putBoolean("FireworkTrail", true);
        explosion3.putBoolean("FireworkFlicker", true);

        // Create the explosions list
        ListTag explosions = new ListTag("Explosions");
        explosions.add(explosion1);
        explosions.add(explosion2);
        explosions.add(explosion3);

        // Create the Fireworks compound
        CompoundTag fireworksCompound = new CompoundTag();
        fireworksCompound.putList(explosions);
        fireworksCompound.putByte("Flight", 1);  // Flight duration (1-3)

        // Create the item compound
        CompoundTag itemTag = new CompoundTag();
        itemTag.putCompound("Fireworks", fireworksCompound);

        return itemTag;
    }

    /**
     * Parses a sound name string into a Sound enum value.
     * Returns null if the sound name is invalid.
     */
    private Sound parseSound(String soundName) {
        if (soundName == null || soundName.isEmpty()) {
            return null;
        }

        try {
            // Try direct enum value (convert dot notation to underscore)
            return Sound.valueOf(soundName.toUpperCase().replace(".", "_"));
        } catch (IllegalArgumentException e) {
            // Try common mappings for legacy sound names
            switch (soundName.toLowerCase()) {
                case "random.levelup":
                    try { return Sound.RANDOM_LEVELUP; } catch (IllegalArgumentException ex) { return null; }
                case "random.orb":
                    try { return Sound.RANDOM_ORB; } catch (IllegalArgumentException ex) { return null; }
                case "random.pop":
                    try { return Sound.RANDOM_POP; } catch (IllegalArgumentException ex) { return null; }
                case "note.hat":
                    try { return Sound.NOTE_HAT; } catch (IllegalArgumentException ex) { return null; }
                case "note.pling":
                    try { return Sound.NOTE_PLING; } catch (IllegalArgumentException ex) { return null; }
                default:
                    plugin.getLogger().debug("Unknown sound: " + soundName);
                    return null;
            }
        }
    }
}
