package com.server.donationsystem;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Represents a donation rank with its display properties, permissions, and perks.
 */
public class DonationRank {

    private final String name;
    private String displayName;
    private String prefix;
    private int priority;
    private long duration; // duration in seconds, 0 = permanent
    private List<String> permissions;
    private int maxHomes;
    private boolean fly;
    private boolean heal;
    private boolean feed;
    private boolean repair;
    private boolean hat;
    private boolean workbench;
    private boolean enderchest;
    private boolean back;
    private boolean nick;
    private String priceDisplay;

    public DonationRank(String name) {
        this.name = name;
        this.permissions = new ArrayList<>();
        this.displayName = name;
        this.prefix = "[" + name + "] ";
        this.duration = 2592000L; // default 30 days
        this.maxHomes = 1;
        this.priceDisplay = "";
    }

    /**
     * Load rank data from a config section map.
     */
    @SuppressWarnings("unchecked")
    public static DonationRank fromConfig(String name, Map<String, Object> section) {
        DonationRank rank = new DonationRank(name);

        if (section.containsKey("display-name")) {
            rank.setDisplayName((String) section.get("display-name"));
        }
        if (section.containsKey("prefix")) {
            rank.setPrefix((String) section.get("prefix"));
        }
        if (section.containsKey("priority")) {
            rank.setPriority(((Number) section.get("priority")).intValue());
        }
        if (section.containsKey("duration")) {
            rank.setDuration(((Number) section.get("duration")).longValue());
        }
        if (section.containsKey("price-display")) {
            rank.setPriceDisplay((String) section.get("price-display"));
        }
        if (section.containsKey("permissions")) {
            List<String> perms = new ArrayList<>();
            Object permsObj = section.get("permissions");
            if (permsObj instanceof List) {
                for (Object perm : (List<?>) permsObj) {
                    perms.add((String) perm);
                }
            }
            rank.setPermissions(perms);
        }
        if (section.containsKey("perks")) {
            Map<String, Object> perks = (Map<String, Object>) section.get("perks");
            if (perks.containsKey("max-homes")) {
                rank.setMaxHomes(((Number) perks.get("max-homes")).intValue());
            }
            if (perks.containsKey("fly")) {
                rank.setFly((Boolean) perks.get("fly"));
            }
            if (perks.containsKey("heal")) {
                rank.setHeal((Boolean) perks.get("heal"));
            }
            if (perks.containsKey("feed")) {
                rank.setFeed((Boolean) perks.get("feed"));
            }
            if (perks.containsKey("repair")) {
                rank.setRepair((Boolean) perks.get("repair"));
            }
            if (perks.containsKey("hat")) {
                rank.setHat((Boolean) perks.get("hat"));
            }
            if (perks.containsKey("workbench")) {
                rank.setWorkbench((Boolean) perks.get("workbench"));
            }
            if (perks.containsKey("enderchest")) {
                rank.setEnderchest((Boolean) perks.get("enderchest"));
            }
            if (perks.containsKey("back")) {
                rank.setBack((Boolean) perks.get("back"));
            }
            if (perks.containsKey("nick")) {
                rank.setNick((Boolean) perks.get("nick"));
            }
        }

        return rank;
    }

    /**
     * Check if this rank is permanent (duration = 0).
     */
    public boolean isPermanent() {
        return duration == 0;
    }

    /**
     * Get a formatted description of the rank's perks for display.
     */
    public String[] getPerkDescription() {
        List<String> perks = new ArrayList<>();
        if (fly) perks.add("§7- §bFlight");
        if (heal) perks.add("§7- §bHeal");
        if (feed) perks.add("§7- §bFeed");
        if (repair) perks.add("§7- §bRepair");
        if (hat) perks.add("§7- §bHat");
        if (workbench) perks.add("§7- §bWorkbench");
        if (enderchest) perks.add("§7- §bEnder Chest");
        if (back) perks.add("§7- §bBack (death return)");
        if (nick) perks.add("§7- §bNickname");
        perks.add("§7- §b" + maxHomes + " Homes");
        return perks.toArray(new String[0]);
    }

    // Getters and Setters

    public String getName() {
        return name;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public long getDuration() {
        return duration;
    }

    public void setDuration(long duration) {
        this.duration = duration;
    }

    public List<String> getPermissions() {
        return permissions;
    }

    public void setPermissions(List<String> permissions) {
        this.permissions = permissions;
    }

    public int getMaxHomes() {
        return maxHomes;
    }

    public void setMaxHomes(int maxHomes) {
        this.maxHomes = maxHomes;
    }

    public boolean hasFly() {
        return fly;
    }

    public void setFly(boolean fly) {
        this.fly = fly;
    }

    public boolean hasHeal() {
        return heal;
    }

    public void setHeal(boolean heal) {
        this.heal = heal;
    }

    public boolean hasFeed() {
        return feed;
    }

    public void setFeed(boolean feed) {
        this.feed = feed;
    }

    public boolean hasRepair() {
        return repair;
    }

    public void setRepair(boolean repair) {
        this.repair = repair;
    }

    public boolean hasHat() {
        return hat;
    }

    public void setHat(boolean hat) {
        this.hat = hat;
    }

    public boolean hasWorkbench() {
        return workbench;
    }

    public void setWorkbench(boolean workbench) {
        this.workbench = workbench;
    }

    public boolean hasEnderchest() {
        return enderchest;
    }

    public void setEnderchest(boolean enderchest) {
        this.enderchest = enderchest;
    }

    public boolean hasBack() {
        return back;
    }

    public void setBack(boolean back) {
        this.back = back;
    }

    public boolean hasNick() {
        return nick;
    }

    public void setNick(boolean nick) {
        this.nick = nick;
    }

    public String getPriceDisplay() {
        return priceDisplay;
    }

    public void setPriceDisplay(String priceDisplay) {
        this.priceDisplay = priceDisplay;
    }
}
