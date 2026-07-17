package com.server.pvp;

import cn.nukkit.level.Position;

/**
 * Represents a PvP arena with boundaries and spawn points.
 */
public class Arena {

    private String name;
    private Position pos1;
    private Position pos2;
    private Position spawn1;
    private Position spawn2;
    private int maxPlayers;
    private boolean active;

    public Arena(String name) {
        this.name = name;
        this.maxPlayers = 2;
        this.active = false;
    }

    public Arena(String name, Position pos1, Position pos2) {
        this.name = name;
        this.pos1 = pos1;
        this.pos2 = pos2;
        this.maxPlayers = 2;
        this.active = false;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Position getPos1() {
        return pos1;
    }

    public void setPos1(Position pos1) {
        this.pos1 = pos1;
    }

    public Position getPos2() {
        return pos2;
    }

    public void setPos2(Position pos2) {
        this.pos2 = pos2;
    }

    public Position getSpawn1() {
        return spawn1;
    }

    public void setSpawn1(Position spawn1) {
        this.spawn1 = spawn1;
    }

    public Position getSpawn2() {
        return spawn2;
    }

    public void setSpawn2(Position spawn2) {
        this.spawn2 = spawn2;
    }

    public int getMaxPlayers() {
        return maxPlayers;
    }

    public void setMaxPlayers(int maxPlayers) {
        this.maxPlayers = maxPlayers;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    /**
     * Check if a position is inside the arena boundaries.
     */
    public boolean isInside(Position pos) {
        if (pos1 == null || pos2 == null) {
            return false;
        }
        if (!pos.getLevel().getName().equals(pos1.getLevel().getName())) {
            return false;
        }
        double minX = Math.min(pos1.getX(), pos2.getX());
        double maxX = Math.max(pos1.getX(), pos2.getX());
        double minY = Math.min(pos1.getY(), pos2.getY());
        double maxY = Math.max(pos1.getY(), pos2.getY());
        double minZ = Math.min(pos1.getZ(), pos2.getZ());
        double maxZ = Math.max(pos1.getZ(), pos2.getZ());

        return pos.getX() >= minX && pos.getX() <= maxX
                && pos.getY() >= minY && pos.getY() <= maxY
                && pos.getZ() >= minZ && pos.getZ() <= maxZ;
    }

    /**
     * Check if both spawn points are set.
     */
    public boolean isReady() {
        return pos1 != null && pos2 != null && spawn1 != null && spawn2 != null;
    }

    /**
     * Serialize arena data to a string for storage.
     */
    public String serialize() {
        StringBuilder sb = new StringBuilder();
        sb.append(name).append(";");
        if (pos1 != null) {
            sb.append(pos1.getLevel().getName()).append(",")
              .append(pos1.getX()).append(",")
              .append(pos1.getY()).append(",")
              .append(pos1.getZ());
        }
        sb.append(";");
        if (pos2 != null) {
            sb.append(pos2.getLevel().getName()).append(",")
              .append(pos2.getX()).append(",")
              .append(pos2.getY()).append(",")
              .append(pos2.getZ());
        }
        sb.append(";");
        if (spawn1 != null) {
            sb.append(spawn1.getLevel().getName()).append(",")
              .append(spawn1.getX()).append(",")
              .append(spawn1.getY()).append(",")
              .append(spawn1.getZ());
        }
        sb.append(";");
        if (spawn2 != null) {
            sb.append(spawn2.getLevel().getName()).append(",")
              .append(spawn2.getX()).append(",")
              .append(spawn2.getY()).append(",")
              .append(spawn2.getZ());
        }
        sb.append(";").append(maxPlayers);
        return sb.toString();
    }

    /**
     * Get a display summary of the arena.
     */
    public String getSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("§eArena: §f").append(name).append("\n");
        sb.append("§7  Status: ").append(active ? "§aIn Use" : "§fAvailable").append("\n");
        sb.append("§7  Max Players: §f").append(maxPlayers).append("\n");
        sb.append("§7  Spawn 1: ").append(spawn1 != null ? "§aSet" : "§cNot Set").append("\n");
        sb.append("§7  Spawn 2: ").append(spawn2 != null ? "§aSet" : "§cNot Set").append("\n");
        sb.append("§7  Ready: ").append(isReady() ? "§aYes" : "§cNo");
        return sb.toString();
    }
}
