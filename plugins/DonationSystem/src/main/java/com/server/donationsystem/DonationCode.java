package com.server.donationsystem;

/**
 * Represents a donation code that can be redeemed for a rank.
 */
public class DonationCode {

    private final String code;
    private final String rankName;
    private final long duration; // duration in seconds, 0 = use rank default
    private boolean used;
    private String usedBy;
    private long usedAt;

    public DonationCode(String code, String rankName, long duration) {
        this.code = code;
        this.rankName = rankName;
        this.duration = duration;
        this.used = false;
        this.usedBy = null;
        this.usedAt = 0;
    }

    /**
     * Mark this code as used by a player.
     */
    public void redeem(String playerName) {
        this.used = true;
        this.usedBy = playerName;
        this.usedAt = System.currentTimeMillis() / 1000L;
    }

    // Getters

    public String getCode() {
        return code;
    }

    public String getRankName() {
        return rankName;
    }

    public long getDuration() {
        return duration;
    }

    public boolean isUsed() {
        return used;
    }

    public void setUsed(boolean used) {
        this.used = used;
    }

    public String getUsedBy() {
        return usedBy;
    }

    public void setUsedBy(String usedBy) {
        this.usedBy = usedBy;
    }

    public long getUsedAt() {
        return usedAt;
    }

    public void setUsedAt(long usedAt) {
        this.usedAt = usedAt;
    }
}
