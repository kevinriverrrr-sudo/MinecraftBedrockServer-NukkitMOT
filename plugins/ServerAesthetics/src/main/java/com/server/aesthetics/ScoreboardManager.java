package com.server.aesthetics;

import cn.nukkit.Player;
import cn.nukkit.network.protocol.RemoveObjectivePacket;
import cn.nukkit.network.protocol.SetDisplayObjectivePacket;
import cn.nukkit.network.protocol.SetScorePacket;
import cn.nukkit.network.protocol.types.DisplaySlot;
import cn.nukkit.network.protocol.types.ScorerType;
import cn.nukkit.network.protocol.types.SortOrder;
import cn.nukkit.utils.TextFormat;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages side scoreboards for all online players.
 * Uses the correct Nukkit-MOT packet API for maximum compatibility.
 * 
 * The scoreboard displays server information on the right side of the screen,
 * with animated title and auto-updating content.
 */
public class ScoreboardManager {

    private final ServerAestheticsPlugin plugin;
    private static final String OBJECTIVE_NAME = "sa_sidebar";
    private static final String CRITERIA_NAME = "dummy";

    // Track visibility per player
    private final Map<UUID, Boolean> scoreboardVisible = new ConcurrentHashMap<>();
    // Track current line IDs per player for removal
    private final Map<UUID, Set<Long>> playerLineIds = new ConcurrentHashMap<>();
    // Track current title per player (for animation updates)
    private final Map<UUID, String> playerCurrentTitle = new ConcurrentHashMap<>();
    // Score ID counter
    private long scoreIdCounter = 0;
    // Title animation frame index
    private int titleFrameIndex = 0;

    public ScoreboardManager(ServerAestheticsPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Starts the scoreboard update task.
     */
    public void startUpdateTask() {
        int interval = plugin.getConfig().getInt("scoreboard.update-interval", 20);
        plugin.getServer().getScheduler().scheduleRepeatingTask(plugin, new ScoreboardUpdateTask(plugin), interval);
    }

    /**
     * Creates a new scoreboard for the specified player.
     */
    public void createScoreboard(Player player) {
        if (!plugin.getConfig().getBoolean("scoreboard.enabled", true)) {
            return;
        }

        scoreboardVisible.put(player.getUniqueId(), true);
        String title = getCurrentTitleFrame();
        playerCurrentTitle.put(player.getUniqueId(), title);

        // Step 1: Send the display objective creation packet
        sendDisplayObjectivePacket(player, title);

        // Step 2: Send the scoreboard lines
        updateScoreboardLines(player);
    }

    /**
     * Updates the scoreboard for a specific player.
     * Called periodically by the ScoreboardUpdateTask.
     */
    public void updateScoreboard(Player player) {
        if (!scoreboardVisible.getOrDefault(player.getUniqueId(), false)) {
            return;
        }
        if (!player.isOnline()) {
            return;
        }
        if (!plugin.getConfig().getBoolean("scoreboard.enabled", true)) {
            return;
        }

        boolean titleAnimation = plugin.getConfig().getBoolean("scoreboard.title-animation", true);
        String currentTitle = getCurrentTitleFrame();
        String previousTitle = playerCurrentTitle.getOrDefault(player.getUniqueId(), "");

        // If title changed (animation), we need to recreate the objective
        if (titleAnimation && !currentTitle.equals(previousTitle)) {
            // Remove old objective
            sendRemoveObjectivePacket(player);

            // Create new objective with updated title
            playerCurrentTitle.put(player.getUniqueId(), currentTitle);
            sendDisplayObjectivePacket(player, currentTitle);

            // Clear old line IDs since objective was removed
            playerLineIds.remove(player.getUniqueId());
        }

        // Update scoreboard lines
        updateScoreboardLines(player);
    }

    /**
     * Removes the scoreboard from a player.
     */
    public void removeScoreboard(Player player) {
        sendRemoveObjectivePacket(player);
        scoreboardVisible.remove(player.getUniqueId());
        playerLineIds.remove(player.getUniqueId());
        playerCurrentTitle.remove(player.getUniqueId());
    }

    /**
     * Toggles scoreboard visibility for a player.
     * Returns true if now visible, false if now hidden.
     */
    public boolean toggleScoreboard(Player player) {
        UUID uuid = player.getUniqueId();
        boolean currentlyVisible = scoreboardVisible.getOrDefault(uuid, true);

        if (currentlyVisible) {
            // Hide scoreboard
            removeScoreboard(player);
            scoreboardVisible.put(uuid, false);
            return false;
        } else {
            // Show scoreboard
            createScoreboard(player);
            return true;
        }
    }

    /**
     * Checks if a player's scoreboard is currently visible.
     */
    public boolean isScoreboardVisible(Player player) {
        return scoreboardVisible.getOrDefault(player.getUniqueId(), true);
    }

    /**
     * Advances the title animation frame and returns the current title.
     */
    public void advanceTitleFrame() {
        List<String> frames = plugin.getConfig().getStringList("scoreboard.title-frames");
        if (frames == null || frames.isEmpty()) {
            return;
        }
        titleFrameIndex = (titleFrameIndex + 1) % frames.size();
    }

    /**
     * Gets the current title frame text.
     */
    public String getCurrentTitleFrame() {
        List<String> frames = plugin.getConfig().getStringList("scoreboard.title-frames");
        if (frames == null || frames.isEmpty()) {
            return plugin.getConfig().getString("server-name", "§6§lSERVER");
        }
        return frames.get(titleFrameIndex % frames.size());
    }

    /**
     * Updates all scoreboard lines for a player.
     * Removes old lines first, then adds new ones.
     */
    private void updateScoreboardLines(Player player) {
        // Remove old lines
        Set<Long> oldLineIds = playerLineIds.get(player.getUniqueId());
        if (oldLineIds != null && !oldLineIds.isEmpty()) {
            sendRemoveScoresPacket(player, oldLineIds);
        }

        // Build new lines from config
        List<String> lineTemplates = plugin.getConfig().getStringList("scoreboard.lines");
        if (lineTemplates == null || lineTemplates.isEmpty()) {
            return;
        }

        // Replace placeholders in each line
        List<String> processedLines = new ArrayList<>();
        for (String line : lineTemplates) {
            String processed = plugin.replacePlaceholders(line, player);
            processedLines.add(processed);
        }

        // Make lines unique (for duplicate lines like separators)
        List<String> uniqueLines = makeLinesUnique(processedLines);

        // Send new lines
        Set<Long> newLineIds = new HashSet<>();
        List<SetScorePacket.ScoreInfo> scores = new ArrayList<>();

        int scoreValue = uniqueLines.size(); // Higher score = higher on sidebar
        for (int i = 0; i < uniqueLines.size(); i++) {
            long lineId = nextScoreId();
            newLineIds.add(lineId);

            // Use the ScoreInfo constructor for FAKE scorer type with a name
            SetScorePacket.ScoreInfo info = new SetScorePacket.ScoreInfo(
                lineId,
                OBJECTIVE_NAME,
                scoreValue - i,
                uniqueLines.get(i)  // FAKE scorer with display name
            );

            scores.add(info);
        }

        sendSetScoresPacket(player, scores);
        playerLineIds.put(player.getUniqueId(), newLineIds);
    }

    /**
     * Makes lines unique by appending invisible color codes to duplicates.
     * This is necessary because Bedrock scoreboard requires unique line names.
     */
    private List<String> makeLinesUnique(List<String> lines) {
        List<String> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (seen.contains(line)) {
                // Append invisible characters to make unique
                String uniqueLine = line + TextFormat.RESET + getInvisibleSortCode(i);
                // If still not unique (very unlikely), add more codes
                while (seen.contains(uniqueLine)) {
                    uniqueLine += TextFormat.RESET + getInvisibleSortCode(i + seen.size());
                }
                result.add(uniqueLine);
                seen.add(uniqueLine);
            } else {
                result.add(line);
                seen.add(line);
            }
        }
        return result;
    }

    /**
     * Gets an invisible sort code for deduplication.
     * Uses color codes that create minimal visual impact when followed by §r.
     */
    private String getInvisibleSortCode(int index) {
        String[] codes = {"0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "a", "b", "c", "d", "e", "f"};
        return "\u00a7" + codes[index % codes.length];
    }

    /**
     * Gets the next score ID.
     */
    private synchronized long nextScoreId() {
        return ++scoreIdCounter;
    }

    // ==================== Packet Sending Methods ====================

    /**
     * Sends a SetDisplayObjectivePacket to create and display the scoreboard objective.
     */
    private void sendDisplayObjectivePacket(Player player, String displayName) {
        SetDisplayObjectivePacket pk = new SetDisplayObjectivePacket();
        pk.displaySlot = DisplaySlot.SIDEBAR;
        pk.objectiveId = OBJECTIVE_NAME;
        pk.displayName = displayName;
        pk.criteria = CRITERIA_NAME;
        pk.sortOrder = SortOrder.ASCENDING;
        player.dataPacket(pk);
    }

    /**
     * Sends a SetScorePacket to add/update score entries.
     */
    private void sendSetScoresPacket(Player player, List<SetScorePacket.ScoreInfo> scores) {
        SetScorePacket pk = new SetScorePacket();
        pk.action = SetScorePacket.Action.SET;
        pk.infos = new ArrayList<>(scores);
        player.dataPacket(pk);
    }

    /**
     * Sends a SetScorePacket to remove score entries.
     */
    private void sendRemoveScoresPacket(Player player, Set<Long> scoreIds) {
        SetScorePacket pk = new SetScorePacket();
        pk.action = SetScorePacket.Action.REMOVE;

        List<SetScorePacket.ScoreInfo> infoList = new ArrayList<>();
        for (long id : scoreIds) {
            SetScorePacket.ScoreInfo info = new SetScorePacket.ScoreInfo(id, OBJECTIVE_NAME, 0);
            infoList.add(info);
        }
        pk.infos = infoList;

        player.dataPacket(pk);
    }

    /**
     * Sends a RemoveObjectivePacket to remove the scoreboard objective.
     */
    private void sendRemoveObjectivePacket(Player player) {
        RemoveObjectivePacket pk = new RemoveObjectivePacket();
        pk.objectiveId = OBJECTIVE_NAME;
        player.dataPacket(pk);
    }
}
