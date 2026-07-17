package com.server.aesthetics;

import cn.nukkit.Player;
import cn.nukkit.level.Level;
import cn.nukkit.level.Location;
import cn.nukkit.level.Position;
import cn.nukkit.utils.TextFormat;

/**
 * Manages the spawn point location including saving/loading from config,
 * teleportation, and spawn protection visualization.
 */
public class SpawnManager {

    private final ServerAestheticsPlugin plugin;
    private Location spawnLocation;

    public SpawnManager(ServerAestheticsPlugin plugin) {
        this.plugin = plugin;
        loadSpawnLocation();
    }

    /**
     * Loads the spawn location from the config.
     */
    private void loadSpawnLocation() {
        String locationStr = plugin.getConfig().getString("spawn.location", "world,0,64,0,0,0");
        spawnLocation = parseLocationString(locationStr);
    }

    /**
     * Parses a location string in the format "world,x,y,z,yaw,pitch"
     * into a Location object.
     */
    private Location parseLocationString(String locationStr) {
        if (locationStr == null || locationStr.isEmpty()) {
            return null;
        }

        String[] parts = locationStr.split(",");
        if (parts.length < 4) {
            plugin.getLogger().warning("Invalid spawn location format: " + locationStr);
            return null;
        }

        try {
            String worldName = parts[0].trim();
            double x = Double.parseDouble(parts[1].trim());
            double y = Double.parseDouble(parts[2].trim());
            double z = Double.parseDouble(parts[3].trim());
            double yaw = parts.length > 4 ? Double.parseDouble(parts[4].trim()) : 0;
            double pitch = parts.length > 5 ? Double.parseDouble(parts[5].trim()) : 0;

            Level level = plugin.getServer().getLevelByName(worldName);
            if (level == null) {
                plugin.getLogger().warning("Spawn world not found: " + worldName);
                return null;
            }

            return new Location(x, y, z, yaw, pitch, level);

        } catch (NumberFormatException e) {
            plugin.getLogger().warning("Invalid spawn location coordinates: " + locationStr);
            return null;
        }
    }

    /**
     * Gets the current spawn position.
     * @return The spawn Position (as Location with yaw/pitch), or null if not set
     */
    public Position getSpawnPosition() {
        return spawnLocation;
    }

    /**
     * Gets the current spawn location with rotation data.
     * @return The spawn Location, or null if not set
     */
    public Location getSpawnLocation() {
        return spawnLocation;
    }

    /**
     * Sets a new spawn location and saves it to config.
     * @param position The new spawn position
     */
    public void setSpawnLocation(Position position) {
        if (position instanceof Location) {
            this.spawnLocation = (Location) position;
        } else if (position != null) {
            // Convert Position to Location with default yaw/pitch
            this.spawnLocation = new Location(
                position.getX(),
                position.getY(),
                position.getZ(),
                0, 0,
                position.getLevel()
            );
        }
        saveSpawnLocation(position);
    }

    /**
     * Saves the spawn location to the config file.
     */
    private void saveSpawnLocation(Position position) {
        if (position == null || position.getLevel() == null) {
            return;
        }

        double yaw = 0;
        double pitch = 0;
        if (position instanceof Location) {
            yaw = ((Location) position).yaw;
            pitch = ((Location) position).pitch;
        }

        String locationStr = String.format("%s,%.2f,%.2f,%.2f,%.2f,%.2f",
            position.getLevel().getName(),
            position.getX(),
            position.getY(),
            position.getZ(),
            yaw,
            pitch
        );

        plugin.getConfig().set("spawn.location", locationStr);
        plugin.saveConfig();

        plugin.getLogger().info("Spawn location set to: " + locationStr);
    }

    /**
     * Teleports a player to the spawn location.
     * @param player The player to teleport
     * @return true if teleportation was successful, false otherwise
     */
    public boolean teleportToSpawn(Player player) {
        if (spawnLocation == null) {
            player.sendMessage(TextFormat.colorize("\u00a7cSpawn location has not been set!"));
            return false;
        }

        // Make sure the level is loaded
        Level level = spawnLocation.getLevel();
        if (level == null) {
            // Try to re-resolve the level from config
            String locationStr = plugin.getConfig().getString("spawn.location", "");
            String[] parts = locationStr.split(",");
            if (parts.length > 0) {
                level = plugin.getServer().getLevelByName(parts[0].trim());
                if (level != null) {
                    // Re-create the Location with the resolved level
                    spawnLocation = new Location(
                        spawnLocation.getX(),
                        spawnLocation.getY(),
                        spawnLocation.getZ(),
                        spawnLocation.yaw,
                        spawnLocation.pitch,
                        level
                    );
                }
            }
        }

        if (level == null) {
            player.sendMessage(TextFormat.colorize("\u00a7cSpawn world is not loaded!"));
            return false;
        }

        // Teleport the player using the Location (preserves yaw/pitch)
        player.teleport(spawnLocation);
        player.sendMessage(TextFormat.colorize("\u00a7aTeleported to spawn!"));
        return true;
    }

    /**
     * Checks if a position is within the spawn protection radius.
     * @param position The position to check
     * @return true if the position is within spawn protection
     */
    public boolean isWithinSpawnProtection(Position position) {
        if (spawnLocation == null) {
            return false;
        }

        int radius = plugin.getConfig().getInt("spawn.protection-radius", 30);
        if (radius <= 0) {
            return false;
        }

        // Check if same world
        if (position.getLevel() == null || !position.getLevel().equals(spawnLocation.getLevel())) {
            return false;
        }

        double distance = position.distance(spawnLocation);
        return distance <= radius;
    }

    /**
     * Gets the spawn protection radius.
     * @return The protection radius in blocks
     */
    public int getProtectionRadius() {
        return plugin.getConfig().getInt("spawn.protection-radius", 30);
    }

    /**
     * Shows spawn protection visualization to a player.
     * Sends a message indicating the protection area boundaries.
     * @param player The player to show the visualization to
     */
    public void showProtectionVisualization(Player player) {
        if (spawnLocation == null) {
            player.sendMessage(TextFormat.colorize("\u00a7cSpawn not set!"));
            return;
        }

        int radius = getProtectionRadius();
        player.sendMessage(TextFormat.colorize("\u00a76\u00a7l\u2501\u2501\u2501 Spawn Protection \u2501\u2501\u2501"));
        player.sendMessage(TextFormat.colorize("\u00a7eCenter: \u00a7f" +
            String.format("%.0f, %.0f, %.0f", spawnLocation.getX(), spawnLocation.getY(), spawnLocation.getZ())));
        player.sendMessage(TextFormat.colorize("\u00a7eRadius: \u00a7f" + radius + " blocks"));
        player.sendMessage(TextFormat.colorize("\u00a7eWorld: \u00a7f" + spawnLocation.getLevel().getName()));

        // Show boundary info
        player.sendMessage(TextFormat.colorize("\u00a77Boundaries:"));
        player.sendMessage(TextFormat.colorize("\u00a77  North: \u00a7f" + String.format("%.0f", spawnLocation.getZ() - radius)));
        player.sendMessage(TextFormat.colorize("\u00a77  South: \u00a7f" + String.format("%.0f", spawnLocation.getZ() + radius)));
        player.sendMessage(TextFormat.colorize("\u00a77  West: \u00a7f" + String.format("%.0f", spawnLocation.getX() - radius)));
        player.sendMessage(TextFormat.colorize("\u00a77  East: \u00a7f" + String.format("%.0f", spawnLocation.getX() + radius)));
        player.sendMessage(TextFormat.colorize("\u00a76\u00a7l\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501"));
    }

    /**
     * Checks if a player should be teleported to spawn on first join.
     * @return true if teleport-on-first-join is enabled
     */
    public boolean shouldTeleportOnFirstJoin() {
        return plugin.getConfig().getBoolean("spawn.teleport-on-first-join", true);
    }
}
