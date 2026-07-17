package com.server.customchat;

/**
 * Represents a chat channel with its configuration properties.
 */
public class ChatChannel {

    private final String name;
    private String format;
    private String permission;
    private int radius;
    private int cooldown;

    public ChatChannel(String name, String format, String permission, int radius, int cooldown) {
        this.name = name;
        this.format = format;
        this.permission = permission;
        this.radius = radius;
        this.cooldown = cooldown;
    }

    public String getName() {
        return name;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public String getPermission() {
        return permission;
    }

    public void setPermission(String permission) {
        this.permission = permission;
    }

    public int getRadius() {
        return radius;
    }

    public void setRadius(int radius) {
        this.radius = radius;
    }

    public int getCooldown() {
        return cooldown;
    }

    public void setCooldown(int cooldown) {
        this.cooldown = cooldown;
    }

    /**
     * Check if this channel has a distance-limited radius.
     * @return true if the channel is local (has a finite radius)
     */
    public boolean isLocal() {
        return radius > 0;
    }

    /**
     * Check if this channel requires a permission to use.
     * @return true if a permission is required
     */
    public boolean hasPermission() {
        return permission != null && !permission.isEmpty();
    }

    /**
     * Check if this channel has a cooldown.
     * @return true if the channel has a non-zero cooldown
     */
    public boolean hasCooldown() {
        return cooldown > 0;
    }
}
