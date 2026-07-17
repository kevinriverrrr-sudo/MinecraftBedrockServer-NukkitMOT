package com.server.customdungeons;

import cn.nukkit.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Represents a party of players that can enter dungeons together.
 * Contains a leader, members, and pending invites.
 */
public class Party {

    private final String id;
    private String leaderName;
    private final List<String> members;
    private final List<String> pendingInvites;
    private final int maxSize;

    public Party(String leaderName, int maxSize) {
        this.id = UUID.randomUUID().toString().substring(0, 8);
        this.leaderName = leaderName;
        this.maxSize = maxSize;
        this.members = new ArrayList<>();
        this.pendingInvites = new ArrayList<>();
        this.members.add(leaderName);
    }

    public String getId() {
        return id;
    }

    public String getLeaderName() {
        return leaderName;
    }

    public List<String> getMembers() {
        return Collections.unmodifiableList(members);
    }

    public int getMaxSize() {
        return maxSize;
    }

    public int getSize() {
        return members.size();
    }

    /**
     * Check if a player is in this party.
     */
    public boolean isMember(String playerName) {
        return members.contains(playerName.toLowerCase());
    }

    /**
     * Check if a player is the leader of this party.
     */
    public boolean isLeader(String playerName) {
        return leaderName.equals(playerName.toLowerCase());
    }

    /**
     * Add a member to the party.
     * @return true if added, false if party is full or already a member
     */
    public boolean addMember(String playerName) {
        String lowerName = playerName.toLowerCase();
        if (members.size() >= maxSize) return false;
        if (members.contains(lowerName)) return false;
        members.add(lowerName);
        pendingInvites.remove(lowerName);
        return true;
    }

    /**
     * Remove a member from the party.
     * If the leader leaves, a new leader is assigned.
     * @return true if the party should be disbanded (no members left)
     */
    public boolean removeMember(String playerName) {
        String lowerName = playerName.toLowerCase();
        boolean removed = members.remove(lowerName);
        if (!removed) return false;

        // If leader left, assign new leader
        if (leaderName.equals(lowerName)) {
            if (members.isEmpty()) {
                return true; // Disband the party
            }
            leaderName = members.get(0);
        }
        return members.isEmpty(); // Disband if no members left
    }

    /**
     * Invite a player to the party.
     */
    public boolean invite(String playerName) {
        String lowerName = playerName.toLowerCase();
        if (members.size() >= maxSize) return false;
        if (members.contains(lowerName)) return false;
        if (pendingInvites.contains(lowerName)) return false;
        pendingInvites.add(lowerName);
        return true;
    }

    /**
     * Check if a player has a pending invite.
     */
    public boolean hasPendingInvite(String playerName) {
        return pendingInvites.contains(playerName.toLowerCase());
    }

    /**
     * Remove a pending invite (either accepted or expired).
     */
    public void removeInvite(String playerName) {
        pendingInvites.remove(playerName.toLowerCase());
    }

    /**
     * Get list of pending invite names.
     */
    public List<String> getPendingInvites() {
        return Collections.unmodifiableList(pendingInvites);
    }

    @Override
    public String toString() {
        return "Party{id='" + id + "', leader='" + leaderName +
                "', members=" + members + ", invites=" + pendingInvites + "}";
    }
}
