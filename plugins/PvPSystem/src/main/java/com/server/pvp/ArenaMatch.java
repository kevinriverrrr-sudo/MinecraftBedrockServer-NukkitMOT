package com.server.pvp;

import cn.nukkit.Player;
import cn.nukkit.level.Position;

import java.util.*;

/**
 * Represents an active arena match with players, countdown, and scores.
 */
public class ArenaMatch {

    public enum MatchState {
        WAITING,
        COUNTDOWN,
        FIGHTING,
        FINISHED
    }

    private final Arena arena;
    private final List<Player> team1;
    private final List<Player> team2;
    private MatchState state;
    private int countdown;
    private int matchDuration;
    private int elapsed;
    private final Map<UUID, Integer> kills;
    private final Map<UUID, Integer> deaths;
    private final Map<UUID, Position> originalPositions;
    private final Map<UUID, Double> originalHealth;
    private final Map<UUID, Integer> originalFood;
    private String winner;

    public ArenaMatch(Arena arena, int countdownSeconds, int matchDurationSeconds) {
        this.arena = arena;
        this.team1 = new ArrayList<>();
        this.team2 = new ArrayList<>();
        this.state = MatchState.WAITING;
        this.countdown = countdownSeconds;
        this.matchDuration = matchDurationSeconds;
        this.elapsed = 0;
        this.kills = new HashMap<>();
        this.deaths = new HashMap<>();
        this.originalPositions = new HashMap<>();
        this.originalHealth = new HashMap<>();
        this.originalFood = new HashMap<>();
        this.winner = null;
    }

    public Arena getArena() {
        return arena;
    }

    public List<Player> getTeam1() {
        return team1;
    }

    public List<Player> getTeam2() {
        return team2;
    }

    public List<Player> getAllPlayers() {
        List<Player> all = new ArrayList<>(team1);
        all.addAll(team2);
        return all;
    }

    public MatchState getState() {
        return state;
    }

    public void setState(MatchState state) {
        this.state = state;
    }

    public int getCountdown() {
        return countdown;
    }

    public void setCountdown(int countdown) {
        this.countdown = countdown;
    }

    public void decrementCountdown() {
        this.countdown--;
    }

    public int getMatchDuration() {
        return matchDuration;
    }

    public void setMatchDuration(int matchDuration) {
        this.matchDuration = matchDuration;
    }

    public int getElapsed() {
        return elapsed;
    }

    public void incrementElapsed() {
        this.elapsed++;
    }

    public int getRemainingTime() {
        return matchDuration - elapsed;
    }

    public String getWinner() {
        return winner;
    }

    public void setWinner(String winner) {
        this.winner = winner;
    }

    public void addKill(UUID playerId) {
        kills.put(playerId, kills.getOrDefault(playerId, 0) + 1);
    }

    public int getKills(UUID playerId) {
        return kills.getOrDefault(playerId, 0);
    }

    public void addDeath(UUID playerId) {
        deaths.put(playerId, deaths.getOrDefault(playerId, 0) + 1);
    }

    public int getDeaths(UUID playerId) {
        return deaths.getOrDefault(playerId, 0);
    }

    public void saveOriginalState(Player player) {
        originalPositions.put(player.getUniqueId(), player.getPosition());
        originalHealth.put(player.getUniqueId(), (double) player.getHealth());
        originalFood.put(player.getUniqueId(), player.getFoodData().getLevel());
    }

    public Position getOriginalPosition(UUID playerId) {
        return originalPositions.get(playerId);
    }

    public Double getOriginalHealth(UUID playerId) {
        return originalHealth.get(playerId);
    }

    public Integer getOriginalFood(UUID playerId) {
        return originalFood.get(playerId);
    }

    /**
     * Check if a player is in this match.
     */
    public boolean hasPlayer(Player player) {
        return team1.contains(player) || team2.contains(player);
    }

    /**
     * Check if a player is in team 1.
     */
    public boolean isTeam1(Player player) {
        return team1.contains(player);
    }

    /**
     * Check if a player is in team 2.
     */
    public boolean isTeam2(Player player) {
        return team2.contains(player);
    }

    /**
     * Get the team number for a player (1 or 2, or 0 if not in match).
     */
    public int getTeamNumber(Player player) {
        if (team1.contains(player)) return 1;
        if (team2.contains(player)) return 2;
        return 0;
    }

    /**
     * Get the teammates of a player.
     */
    public List<Player> getTeammates(Player player) {
        if (team1.contains(player)) {
            List<Player> mates = new ArrayList<>(team1);
            mates.remove(player);
            return mates;
        } else if (team2.contains(player)) {
            List<Player> mates = new ArrayList<>(team2);
            mates.remove(player);
            return mates;
        }
        return Collections.emptyList();
    }

    /**
     * Get the opposing team of a player.
     */
    public List<Player> getOpponents(Player player) {
        if (team1.contains(player)) {
            return new ArrayList<>(team2);
        } else if (team2.contains(player)) {
            return new ArrayList<>(team1);
        }
        return Collections.emptyList();
    }

    /**
     * Count alive players in a team.
     */
    public int getAliveTeam1() {
        int count = 0;
        for (Player p : team1) {
            if (p.isAlive()) count++;
        }
        return count;
    }

    /**
     * Count alive players in a team.
     */
    public int getAliveTeam2() {
        int count = 0;
        for (Player p : team2) {
            if (p.isAlive()) count++;
        }
        return count;
    }

    /**
     * Check if the match is over (one team fully eliminated or time ran out).
     */
    public boolean isMatchOver() {
        if (getAliveTeam1() == 0 || getAliveTeam2() == 0) {
            return true;
        }
        return elapsed >= matchDuration;
    }

    /**
     * Determine the winning team number.
     * Returns 1, 2, or 0 for draw.
     */
    public int determineWinner() {
        int alive1 = getAliveTeam1();
        int alive2 = getAliveTeam2();
        if (alive1 > alive2) return 1;
        if (alive2 > alive1) return 2;
        return 0;
    }

    /**
     * Remove a player from the match.
     */
    public void removePlayer(Player player) {
        team1.remove(player);
        team2.remove(player);
    }
}
