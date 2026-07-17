package com.server.customdungeons;

import cn.nukkit.Player;
import cn.nukkit.Server;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages all parties in the dungeon system.
 * Handles party creation, invites, member management, and lookup.
 */
public class PartyManager {

    private final CustomDungeonsPlugin plugin;
    private final Map<String, Party> playerParties; // playerName (lowercase) -> Party
    private final Map<String, Party> partiesById;   // partyId -> Party
    private final Map<String, Long> inviteTimestamps; // playerName -> timestamp of invite

    public PartyManager(CustomDungeonsPlugin plugin) {
        this.plugin = plugin;
        this.playerParties = new ConcurrentHashMap<>();
        this.partiesById = new ConcurrentHashMap<>();
        this.inviteTimestamps = new ConcurrentHashMap<>();
    }

    /**
     * Create a new party with the given player as leader.
     */
    public Party createParty(Player leader) {
        String leaderName = leader.getName().toLowerCase();

        // Leave existing party first
        if (playerParties.containsKey(leaderName)) {
            leaveParty(leader);
        }

        int maxSize = plugin.getConfig().getInt("settings.max-party-size", 4);
        Party party = new Party(leaderName, maxSize);
        playerParties.put(leaderName, party);
        partiesById.put(party.getId(), party);

        leader.sendMessage("§aParty created! You are the leader.");
        leader.sendMessage("§7Use §e/dungeon party invite <player> §7to invite others.");
        return party;
    }

    /**
     * Invite a player to the leader's party.
     */
    public void invitePlayer(Player inviter, Player target) {
        String inviterName = inviter.getName().toLowerCase();
        String targetName = target.getName().toLowerCase();

        Party party = playerParties.get(inviterName);
        if (party == null) {
            inviter.sendMessage("§cYou are not in a party! Use §e/dungeon party create §cfirst.");
            return;
        }

        if (!party.isLeader(inviterName)) {
            inviter.sendMessage("§cOnly the party leader can invite players!");
            return;
        }

        if (party.isMember(targetName)) {
            inviter.sendMessage("§cThat player is already in your party!");
            return;
        }

        if (playerParties.containsKey(targetName)) {
            inviter.sendMessage("§cThat player is already in another party!");
            return;
        }

        if (party.getSize() >= party.getMaxSize()) {
            inviter.sendMessage("§cYour party is full! Maximum size: " + party.getMaxSize());
            return;
        }

        if (party.hasPendingInvite(targetName)) {
            inviter.sendMessage("§cThat player already has a pending invite!");
            return;
        }

        if (!party.invite(targetName)) {
            inviter.sendMessage("§cCould not invite that player.");
            return;
        }

        inviteTimestamps.put(targetName, System.currentTimeMillis());

        inviter.sendMessage("§aInvited §e" + target.getName() + " §ato your party!");
        target.sendMessage("§e" + inviter.getName() + " §ainvited you to their party!");
        target.sendMessage("§7Use §e/dungeon party accept §7to join.");
    }

    /**
     * Accept a pending party invite.
     */
    public void acceptInvite(Player player) {
        String playerName = player.getName().toLowerCase();

        // Find the party that has an invite for this player
        Party targetParty = null;
        for (Party party : partiesById.values()) {
            if (party.hasPendingInvite(playerName)) {
                targetParty = party;
                break;
            }
        }

        if (targetParty == null) {
            player.sendMessage("§cYou have no pending party invites!");
            return;
        }

        // Check if invite expired (60 seconds)
        Long inviteTime = inviteTimestamps.get(playerName);
        if (inviteTime != null && (System.currentTimeMillis() - inviteTime) > 60000) {
            targetParty.removeInvite(playerName);
            inviteTimestamps.remove(playerName);
            player.sendMessage("§cYour party invite has expired!");
            return;
        }

        if (playerParties.containsKey(playerName)) {
            player.sendMessage("§cYou are already in a party! Leave your current party first.");
            targetParty.removeInvite(playerName);
            return;
        }

        if (targetParty.getSize() >= targetParty.getMaxSize()) {
            player.sendMessage("§cThat party is now full!");
            targetParty.removeInvite(playerName);
            return;
        }

        if (!targetParty.addMember(playerName)) {
            player.sendMessage("§cCould not join the party!");
            return;
        }

        playerParties.put(playerName, targetParty);
        inviteTimestamps.remove(playerName);

        player.sendMessage("§aYou joined the party!");

        // Notify all party members
        notifyPartyMembers(targetParty, "§e" + player.getName() + " §ajoined the party!", player.getName());
    }

    /**
     * Leave the current party.
     */
    public void leaveParty(Player player) {
        String playerName = player.getName().toLowerCase();
        Party party = playerParties.remove(playerName);

        if (party == null) {
            player.sendMessage("§cYou are not in a party!");
            return;
        }

        boolean shouldDisband = party.removeMember(playerName);
        player.sendMessage("§aYou left the party.");

        if (shouldDisband) {
            disbandParty(party);
        } else {
            // Notify remaining members
            notifyPartyMembers(party, "§e" + player.getName() + " §cleft the party.", null);

            // Notify new leader if leader changed
            Player newLeader = Server.getInstance().getPlayerExact(party.getLeaderName());
            if (newLeader != null) {
                newLeader.sendMessage("§aYou are now the party leader!");
            }
        }
    }

    /**
     * Disband a party, removing all members.
     */
    private void disbandParty(Party party) {
        for (String memberName : party.getMembers()) {
            playerParties.remove(memberName);
            Player member = Server.getInstance().getPlayerExact(memberName);
            if (member != null) {
                member.sendMessage("§cThe party has been disbanded.");
            }
        }

        // Remove pending invites
        for (String invitee : party.getPendingInvites()) {
            inviteTimestamps.remove(invitee);
            Player inviteePlayer = Server.getInstance().getPlayerExact(invitee);
            if (inviteePlayer != null) {
                inviteePlayer.sendMessage("§cThe party invite has been cancelled (party disbanded).");
            }
        }

        partiesById.remove(party.getId());
    }

    /**
     * Get the party a player belongs to.
     */
    public Party getPlayerParty(Player player) {
        return playerParties.get(player.getName().toLowerCase());
    }

    /**
     * Get the party a player belongs to by name.
     */
    public Party getPlayerParty(String playerName) {
        return playerParties.get(playerName.toLowerCase());
    }

    /**
     * Check if a player is in a party.
     */
    public boolean isInParty(Player player) {
        return playerParties.containsKey(player.getName().toLowerCase());
    }

    /**
     * Get all party members as Player objects (online only).
     */
    public List<Player> getOnlinePartyMembers(Party party) {
        List<Player> online = new ArrayList<>();
        for (String memberName : party.getMembers()) {
            Player member = Server.getInstance().getPlayerExact(memberName);
            if (member != null && member.isOnline()) {
                online.add(member);
            }
        }
        return online;
    }

    /**
     * Notify all party members with a message, optionally excluding one player.
     */
    public void notifyPartyMembers(Party party, String message, String excludeName) {
        for (String memberName : party.getMembers()) {
            if (excludeName != null && memberName.equals(excludeName.toLowerCase())) continue;
            Player member = Server.getInstance().getPlayerExact(memberName);
            if (member != null && member.isOnline()) {
                member.sendMessage(message);
            }
        }
    }

    /**
     * Clean up expired invites.
     */
    public void cleanupExpiredInvites() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, Long>> it = inviteTimestamps.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Long> entry = it.next();
            if (now - entry.getValue() > 60000) {
                String playerName = entry.getKey();
                for (Party party : partiesById.values()) {
                    if (party.hasPendingInvite(playerName)) {
                        party.removeInvite(playerName);
                    }
                }
                it.remove();
            }
        }
    }

    /**
     * Handle player disconnect - remove from party.
     */
    public void handlePlayerQuit(Player player) {
        String playerName = player.getName().toLowerCase();
        Party party = playerParties.get(playerName);
        if (party == null) return;

        // If in a dungeon instance, the DungeonManager handles removal
        // Just remove from the party tracking
        boolean shouldDisband = party.removeMember(playerName);
        playerParties.remove(playerName);

        if (shouldDisband) {
            disbandParty(party);
        } else {
            notifyPartyMembers(party, "§e" + player.getName() + " §cleft the party (disconnected).", null);
        }
    }

    /**
     * Get the number of active parties.
     */
    public int getActivePartyCount() {
        return partiesById.size();
    }
}
