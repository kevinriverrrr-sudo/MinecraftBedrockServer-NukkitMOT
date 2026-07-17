package com.server.customdungeons;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.level.Position;
import cn.nukkit.utils.Config;
import cn.nukkit.utils.ConfigSection;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages dungeon templates and active dungeon instances.
 * Handles loading templates from config, creating instances,
 * player cooldowns, and the leaderboard.
 */
public class DungeonManager {

    private final CustomDungeonsPlugin plugin;
    private final Map<String, DungeonTemplate> templates;     // templateId -> DungeonTemplate
    private final Map<String, DungeonInstance> activeInstances; // templateId -> DungeonInstance
    private final Map<String, DungeonInstance> playerInstances; // playerName (lowercase) -> DungeonInstance
    private final Map<String, Long> cooldowns; // "playerName:templateId" -> cooldownExpiryTimestamp
    private final Map<String, Map<String, Long>> leaderboard; // templateId -> { "player1,player2" -> completionTimeSeconds }

    private Config cooldownConfig;
    private Config leaderboardConfig;

    public DungeonManager(CustomDungeonsPlugin plugin) {
        this.plugin = plugin;
        this.templates = new LinkedHashMap<>();
        this.activeInstances = new ConcurrentHashMap<>();
        this.playerInstances = new ConcurrentHashMap<>();
        this.cooldowns = new ConcurrentHashMap<>();
        this.leaderboard = new ConcurrentHashMap<>();
    }

    /**
     * Load all dungeon templates from the plugin config.
     */
    public void loadTemplates() {
        templates.clear();

        Config config = plugin.getConfig();
        ConfigSection dungeonsSection = config.getSection("dungeons");
        if (dungeonsSection == null) {
            plugin.getLogger().warning("No dungeons found in config!");
            return;
        }

        for (Map.Entry<String, Object> entry : dungeonsSection.entrySet()) {
            String dungeonId = entry.getKey();
            try {
                ConfigSection dungeonData = dungeonsSection.getSection(dungeonId);
                if (dungeonData == null) {
                    plugin.getLogger().warning("Invalid dungeon config for: " + dungeonId);
                    continue;
                }

                DungeonTemplate template = parseTemplate(dungeonId, dungeonData);
                if (template != null) {
                    templates.put(dungeonId, template);
                    plugin.getLogger().info("Loaded dungeon template: " + dungeonId +
                            " (" + template.getDisplayName() + ")");
                }
            } catch (Exception e) {
                plugin.getLogger().error("Failed to load dungeon template: " + dungeonId, e);
            }
        }

        plugin.getLogger().info("Loaded " + templates.size() + " dungeon templates.");
    }

    /**
     * Parse a dungeon template from config section data.
     */
    @SuppressWarnings("unchecked")
    private DungeonTemplate parseTemplate(String id, ConfigSection data) {
        String displayName = data.getString("display-name", id);
        String difficulty = data.getString("difficulty", "Normal");
        int minLevel = data.getInt("min-level", 1);
        int maxPlayers = data.getInt("max-players", 4);
        int cooldown = data.getInt("cooldown", 3600);
        String entryFee = data.getString("entry-fee", "");

        // Parse spawn position
        Position spawnPos = parsePosition(data.getString("spawn-position", "0,64,0,world"));

        // Parse waves
        List<DungeonWave> waves = new ArrayList<>();
        List<Map<String, Object>> wavesData = (List<Map<String, Object>>) data.get("waves");
        if (wavesData != null) {
            for (Map<String, Object> waveData : wavesData) {
                DungeonWave wave = parseWave(waveData);
                if (wave != null) {
                    waves.add(wave);
                }
            }
        }

        // Parse boss
        DungeonBoss boss = null;
        Map<String, Object> bossData = (Map<String, Object>) data.get("boss");
        if (bossData != null) {
            boss = parseBoss(bossData);
        }

        // Parse loot
        List<DungeonLoot> guaranteedLoot = new ArrayList<>();
        List<DungeonLoot> randomLoot = new ArrayList<>();

        Map<String, Object> lootData = (Map<String, Object>) data.get("loot");
        if (lootData != null) {
            // Guaranteed loot
            List<String> guaranteedList = (List<String>) lootData.get("guaranteed");
            if (guaranteedList != null) {
                for (String lootStr : guaranteedList) {
                    DungeonLoot loot = parseGuaranteedLoot(lootStr);
                    if (loot != null) {
                        guaranteedLoot.add(loot);
                    }
                }
            }

            // Random loot
            List<Map<String, Object>> randomList = (List<Map<String, Object>>) lootData.get("random");
            if (randomList != null) {
                for (Map<String, Object> randomData : randomList) {
                    DungeonLoot loot = parseRandomLoot(randomData);
                    if (loot != null) {
                        randomLoot.add(loot);
                    }
                }
            }
        }

        if (boss == null) {
            plugin.getLogger().warning("Dungeon " + id + " has no boss defined, skipping.");
            return null;
        }

        if (waves.isEmpty()) {
            plugin.getLogger().warning("Dungeon " + id + " has no waves defined, skipping.");
            return null;
        }

        return new DungeonTemplate(id, displayName, difficulty, minLevel, maxPlayers,
                cooldown, entryFee, spawnPos, waves, boss, guaranteedLoot, randomLoot);
    }

    /**
     * Parse a wave from config data.
     */
    @SuppressWarnings("unchecked")
    private DungeonWave parseWave(Map<String, Object> waveData) {
        List<Map<String, Object>> mobsData = (List<Map<String, Object>>) waveData.get("mobs");
        int delay = waveData.containsKey("delay") ?
                ((Number) waveData.get("delay")).intValue() : 5;

        List<DungeonMob> mobs = new ArrayList<>();
        if (mobsData != null) {
            for (Map<String, Object> mobData : mobsData) {
                String type = (String) mobData.getOrDefault("type", "zombie");
                String name = (String) mobData.getOrDefault("name", "Dungeon Mob");
                int health = mobData.containsKey("health") ?
                        ((Number) mobData.get("health")).intValue() : 20;
                int damage = mobData.containsKey("damage") ?
                        ((Number) mobData.get("damage")).intValue() : 5;
                int count = mobData.containsKey("count") ?
                        ((Number) mobData.get("count")).intValue() : 1;

                mobs.add(new DungeonMob(type, name, health, damage, count));
            }
        }

        return new DungeonWave(mobs, delay);
    }

    /**
     * Parse a boss from config data.
     */
    @SuppressWarnings("unchecked")
    private DungeonBoss parseBoss(Map<String, Object> bossData) {
        String type = (String) bossData.getOrDefault("type", "zombie");
        String name = (String) bossData.getOrDefault("name", "Dungeon Boss");
        int health = bossData.containsKey("health") ?
                ((Number) bossData.get("health")).intValue() : 100;
        int damage = bossData.containsKey("damage") ?
                ((Number) bossData.get("damage")).intValue() : 10;

        List<String> abilities = (List<String>) bossData.getOrDefault("abilities", new ArrayList<>());

        // Parse phases
        List<DungeonBossPhase> phases = new ArrayList<>();
        List<Map<String, Object>> phasesData = (List<Map<String, Object>>) bossData.get("phases");
        if (phasesData != null) {
            for (Map<String, Object> phaseData : phasesData) {
                int healthPercentage = phaseData.containsKey("health-percentage") ?
                        ((Number) phaseData.get("health-percentage")).intValue() : 50;
                String message = (String) phaseData.getOrDefault("message", "The boss enters a new phase!");
                double damageMultiplier = phaseData.containsKey("damage-multiplier") ?
                        ((Number) phaseData.get("damage-multiplier")).doubleValue() : 1.0;
                String summon = (String) phaseData.getOrDefault("summon", null);

                phases.add(new DungeonBossPhase(healthPercentage, message, damageMultiplier, summon));
            }
        }

        return new DungeonBoss(type, name, health, damage, abilities, phases);
    }

    /**
     * Parse a guaranteed loot entry.
     * Format: "item_id:meta:amount"
     */
    private DungeonLoot parseGuaranteedLoot(String lootStr) {
        String[] parts = lootStr.split(":");
        if (parts.length < 3) return null;

        try {
            int meta = Integer.parseInt(parts[1]);
            int amount = Integer.parseInt(parts[2]);
            return new DungeonLoot(parts[0] + ":" + parts[1], 100, amount, amount);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Parse a random loot entry from config.
     */
    private DungeonLoot parseRandomLoot(Map<String, Object> data) {
        String item = (String) data.getOrDefault("item", "air:0:1");
        int chance = data.containsKey("chance") ?
                ((Number) data.get("chance")).intValue() : 50;

        // Parse item string: "item_id:meta:amount"
        String[] parts = item.split(":");
        if (parts.length >= 3) {
            try {
                int meta = Integer.parseInt(parts[1]);
                int amount = Integer.parseInt(parts[2]);
                return new DungeonLoot(parts[0] + ":" + parts[1], chance, 1, amount);
            } catch (NumberFormatException e) {
                return new DungeonLoot(item, chance);
            }
        }

        return new DungeonLoot(item, chance);
    }

    /**
     * Parse a position string.
     * Format: "x,y,z,levelName"
     */
    private Position parsePosition(String posStr) {
        String[] parts = posStr.split(",");
        if (parts.length < 4) {
            return new Position(0, 64, 0, Server.getInstance().getDefaultLevel());
        }

        try {
            double x = Double.parseDouble(parts[0]);
            double y = Double.parseDouble(parts[1]);
            double z = Double.parseDouble(parts[2]);
            String levelName = parts[3];

            cn.nukkit.level.Level level = Server.getInstance().getLevelByName(levelName);
            if (level == null) {
                level = Server.getInstance().getDefaultLevel();
            }

            return new Position(x, y, z, level);
        } catch (NumberFormatException e) {
            return new Position(0, 64, 0, Server.getInstance().getDefaultLevel());
        }
    }

    /**
     * Get a dungeon template by ID.
     */
    public DungeonTemplate getTemplate(String id) {
        return templates.get(id);
    }

    /**
     * Get all dungeon templates.
     */
    public Collection<DungeonTemplate> getTemplates() {
        return templates.values();
    }

    /**
     * Check if a dungeon template exists.
     */
    public boolean templateExists(String id) {
        return templates.containsKey(id);
    }

    /**
     * Enter a dungeon with a player (and their party).
     * Creates a new DungeonInstance for the party.
     */
    public boolean enterDungeon(Player player, String dungeonId) {
        DungeonTemplate template = templates.get(dungeonId);
        if (template == null) {
            player.sendMessage("§cDungeon §e" + dungeonId + " §cdoes not exist!");
            return false;
        }

        // Check if already in a dungeon
        if (playerInstances.containsKey(player.getName().toLowerCase())) {
            player.sendMessage("§cYou are already in a dungeon! Use §e/dungeon leave §cto leave first.");
            return false;
        }

        // Check if dungeon is already active for this template
        if (activeInstances.containsKey(dungeonId)) {
            player.sendMessage("§cThis dungeon is already being run by another party. Please wait.");
            return false;
        }

        // Check cooldown
        if (isOnCooldown(player.getName(), dungeonId)) {
            long remaining = getRemainingCooldown(player.getName(), dungeonId);
            player.sendMessage("§cYou must wait §e" + formatTime(remaining) +
                    " §cbefore entering this dungeon again!");
            return false;
        }

        // Get the party (if any)
        Party party = plugin.getPartyManager().getPlayerParty(player);
        List<Player> playersToEnter = new ArrayList<>();

        if (party != null) {
            // Only the party leader can start the dungeon
            if (!party.isLeader(player.getName())) {
                player.sendMessage("§cOnly the party leader can start a dungeon!");
                return false;
            }

            // Check if all party members can enter
            for (String memberName : party.getMembers()) {
                Player member = Server.getInstance().getPlayerExact(memberName);
                if (member == null || !member.isOnline()) {
                    player.sendMessage("§cParty member §e" + memberName +
                            " §cis not online!");
                    return false;
                }

                if (playerInstances.containsKey(memberName.toLowerCase())) {
                    player.sendMessage("§cParty member §e" + memberName +
                            " §cis already in a dungeon!");
                    return false;
                }

                if (isOnCooldown(memberName, dungeonId)) {
                    player.sendMessage("§cParty member §e" + memberName +
                            " §cis on cooldown for this dungeon!");
                    return false;
                }

                playersToEnter.add(member);
            }

            // Check max players
            if (playersToEnter.size() > template.getMaxPlayers()) {
                player.sendMessage("§cToo many players! Maximum for this dungeon: §e" +
                        template.getMaxPlayers());
                return false;
            }
        } else {
            // Solo entry
            playersToEnter.add(player);
        }

        // Create the dungeon instance
        DungeonInstance instance = new DungeonInstance(plugin, template);

        // Add all players
        for (Player p : playersToEnter) {
            if (!instance.addPlayer(p)) {
                player.sendMessage("§cFailed to add §e" + p.getName() + " §cto the dungeon!");
                return false;
            }
        }

        // Register the instance
        activeInstances.put(dungeonId, instance);
        for (Player p : playersToEnter) {
            playerInstances.put(p.getName().toLowerCase(), instance);
        }

        // Start the dungeon
        instance.start();

        return true;
    }

    /**
     * Leave the current dungeon.
     */
    public void leaveDungeon(Player player) {
        DungeonInstance instance = playerInstances.remove(player.getName().toLowerCase());
        if (instance == null) {
            player.sendMessage("§cYou are not in a dungeon!");
            return;
        }

        instance.forcePlayerLeave(player);

        // If this was the only player, the instance will fail automatically
        player.sendMessage("§aYou left the dungeon.");
    }

    /**
     * Check if a player is currently in a dungeon.
     */
    public boolean isInDungeon(Player player) {
        return playerInstances.containsKey(player.getName().toLowerCase());
    }

    /**
     * Get the dungeon instance a player is in.
     */
    public DungeonInstance getPlayerInstance(Player player) {
        return playerInstances.get(player.getName().toLowerCase());
    }

    /**
     * Get the dungeon instance a player is in by name.
     */
    public DungeonInstance getPlayerInstance(String playerName) {
        return playerInstances.get(playerName.toLowerCase());
    }

    /**
     * Remove a dungeon instance from tracking.
     */
    public void removeInstance(DungeonInstance instance) {
        String templateId = instance.getTemplate().getId();
        activeInstances.remove(templateId);

        // Remove all player mappings
        for (String playerName : instance.getPlayerNames()) {
            playerInstances.remove(playerName);
        }
    }

    /**
     * Handle a player disconnecting while in a dungeon.
     */
    public void handlePlayerQuit(Player player) {
        DungeonInstance instance = playerInstances.get(player.getName().toLowerCase());
        if (instance == null) return;

        // Remove player from instance
        instance.removePlayer(player);
        playerInstances.remove(player.getName().toLowerCase());

        player.sendMessage("§cYou have been removed from the dungeon due to disconnect.");
    }

    // --- Cooldown Management ---

    /**
     * Set a cooldown for a player on a specific dungeon.
     */
    public void setCooldown(String playerName, String dungeonId, int seconds) {
        String key = playerName.toLowerCase() + ":" + dungeonId;
        cooldowns.put(key, System.currentTimeMillis() + (seconds * 1000L));
        saveCooldowns();
    }

    /**
     * Check if a player is on cooldown for a specific dungeon.
     */
    public boolean isOnCooldown(String playerName, String dungeonId) {
        String key = playerName.toLowerCase() + ":" + dungeonId;
        Long expiry = cooldowns.get(key);
        if (expiry == null) return false;

        if (System.currentTimeMillis() >= expiry) {
            cooldowns.remove(key);
            return false;
        }
        return true;
    }

    /**
     * Get the remaining cooldown time in seconds.
     */
    public long getRemainingCooldown(String playerName, String dungeonId) {
        String key = playerName.toLowerCase() + ":" + dungeonId;
        Long expiry = cooldowns.get(key);
        if (expiry == null) return 0;

        long remaining = (expiry - System.currentTimeMillis()) / 1000;
        return Math.max(0, remaining);
    }

    // --- Leaderboard ---

    /**
     * Record a dungeon completion on the leaderboard.
     */
    public void recordCompletion(String dungeonId, Set<String> playerNames, long completionTimeSeconds) {
        if (!leaderboard.containsKey(dungeonId)) {
            leaderboard.put(dungeonId, new LinkedHashMap<>());
        }

        String playerKey = String.join(",", playerNames);
        Map<String, Long> dungeonLeaderboard = leaderboard.get(dungeonId);

        // Only update if this is a new record or faster time
        if (!dungeonLeaderboard.containsKey(playerKey) ||
                dungeonLeaderboard.get(playerKey) > completionTimeSeconds) {
            dungeonLeaderboard.put(playerKey, completionTimeSeconds);
            saveLeaderboard();
        }
    }

    /**
     * Get the leaderboard for a specific dungeon, sorted by fastest time.
     */
    public List<Map.Entry<String, Long>> getLeaderboard(String dungeonId) {
        Map<String, Long> lb = leaderboard.get(dungeonId);
        if (lb == null) return Collections.emptyList();

        List<Map.Entry<String, Long>> sorted = new ArrayList<>(lb.entrySet());
        sorted.sort(Map.Entry.comparingByValue());
        return sorted;
    }

    /**
     * Get the top N leaderboard entries for a dungeon.
     */
    public List<Map.Entry<String, Long>> getTopLeaderboard(String dungeonId, int count) {
        List<Map.Entry<String, Long>> full = getLeaderboard(dungeonId);
        if (full.size() <= count) return full;
        return full.subList(0, count);
    }

    // --- Persistence ---

    /**
     * Load cooldowns and leaderboard from files.
     */
    public void loadData() {
        // Load cooldowns
        File cooldownFile = new File(plugin.getDataFolder(), "cooldowns.yml");
        cooldownConfig = new Config(cooldownFile, Config.YAML);
        for (String key : cooldownConfig.getKeys(false)) {
            long expiry = cooldownConfig.getLong(key, 0);
            if (expiry > System.currentTimeMillis()) {
                cooldowns.put(key, expiry);
            }
        }

        // Load leaderboard
        File leaderboardFile = new File(plugin.getDataFolder(), "leaderboard.yml");
        leaderboardConfig = new Config(leaderboardFile, Config.YAML);
        for (String dungeonId : leaderboardConfig.getKeys(false)) {
            ConfigSection section = leaderboardConfig.getSection(dungeonId);
            if (section == null) continue;
            Map<String, Long> entries = new LinkedHashMap<>();
            for (String playerKey : section.getKeys(false)) {
                entries.put(playerKey, section.getLong(playerKey, 0));
            }
            leaderboard.put(dungeonId, entries);
        }
    }

    /**
     * Save cooldowns to file.
     */
    public void saveCooldowns() {
        if (cooldownConfig == null) return;
        for (Map.Entry<String, Long> entry : cooldowns.entrySet()) {
            cooldownConfig.set(entry.getKey(), entry.getValue());
        }
        cooldownConfig.save();
    }

    /**
     * Save leaderboard to file.
     */
    public void saveLeaderboard() {
        if (leaderboardConfig == null) return;
        for (Map.Entry<String, Map<String, Long>> entry : leaderboard.entrySet()) {
            for (Map.Entry<String, Long> timeEntry : entry.getValue().entrySet()) {
                leaderboardConfig.set(entry.getKey() + "." + timeEntry.getKey(), timeEntry.getValue());
            }
        }
        leaderboardConfig.save();
    }

    /**
     * Save all data.
     */
    public void saveAll() {
        saveCooldowns();
        saveLeaderboard();
    }

    // --- Helper ---

    /**
     * Format seconds into a readable time string.
     */
    private String formatTime(long seconds) {
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;

        if (hours > 0) {
            return hours + "h " + minutes + "m " + secs + "s";
        } else if (minutes > 0) {
            return minutes + "m " + secs + "s";
        } else {
            return secs + "s";
        }
    }

    /**
     * Get the number of active dungeon instances.
     */
    public int getActiveInstanceCount() {
        return activeInstances.size();
    }

    /**
     * Get all active instances.
     */
    public Collection<DungeonInstance> getActiveInstances() {
        return activeInstances.values();
    }
}
