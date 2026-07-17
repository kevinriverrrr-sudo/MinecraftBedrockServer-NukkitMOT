package com.server.customdungeons;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.EntityLiving;
import cn.nukkit.item.Item;
import cn.nukkit.level.Position;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.DoubleTag;
import cn.nukkit.nbt.tag.FloatTag;
import cn.nukkit.nbt.tag.ListTag;
import cn.nukkit.potion.Effect;
import cn.nukkit.scheduler.TaskHandler;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents an active dungeon run instance.
 * Tracks players, current wave, alive mobs, boss, start time, and state.
 */
public class DungeonInstance {

    /**
     * The state of a dungeon instance.
     */
    public enum DungeonState {
        WAITING,    // Waiting for players to ready up
        WAVE_ACTIVE,// Mobs are alive and being fought
        WAVE_DELAY, // Between waves, waiting for delay
        BOSS_ACTIVE,// Boss is alive and being fought
        COMPLETED,  // Dungeon completed successfully
        FAILED      // All players died or left
    }

    private final CustomDungeonsPlugin plugin;
    private final DungeonTemplate template;
    private final Set<String> playerNames;
    private final Map<Long, Entity> aliveMobs;    // entityId -> Entity
    private Entity bossEntity;
    private final Set<Long> dungeonMobIds;          // All mob IDs spawned by this dungeon
    private DungeonState state;
    private int currentWave;
    private long startTime;
    private long completionTime;
    private TaskHandler waveDelayTask;
    private TaskHandler bossPhaseTask;
    private final Set<Integer> triggeredPhases;
    private final Map<String, Position> originalPositions; // player -> original position before dungeon

    public DungeonInstance(CustomDungeonsPlugin plugin, DungeonTemplate template) {
        this.plugin = plugin;
        this.template = template;
        this.playerNames = new HashSet<>();
        this.aliveMobs = new ConcurrentHashMap<>();
        this.bossEntity = null;
        this.dungeonMobIds = new HashSet<>();
        this.state = DungeonState.WAITING;
        this.currentWave = 0;
        this.startTime = 0;
        this.completionTime = 0;
        this.triggeredPhases = new HashSet<>();
        this.originalPositions = new HashMap<>();
    }

    /**
     * Add a player to this dungeon instance.
     */
    public boolean addPlayer(Player player) {
        if (playerNames.size() >= template.getMaxPlayers()) {
            return false;
        }
        // Save original position for teleporting back
        originalPositions.put(player.getName().toLowerCase(), player.getPosition());
        playerNames.add(player.getName().toLowerCase());
        return true;
    }

    /**
     * Remove a player from this dungeon instance.
     * If all players are gone, the dungeon fails.
     */
    public void removePlayer(Player player) {
        String playerName = player.getName().toLowerCase();
        playerNames.remove(playerName);

        if (playerNames.isEmpty()) {
            failDungeon();
        }
    }

    /**
     * Check if a player is in this dungeon instance.
     */
    public boolean hasPlayer(String playerName) {
        return playerNames.contains(playerName.toLowerCase());
    }

    /**
     * Check if a player is in this dungeon instance.
     */
    public boolean hasPlayer(Player player) {
        return hasPlayer(player.getName());
    }

    /**
     * Start the dungeon run.
     */
    public void start() {
        this.startTime = System.currentTimeMillis();
        this.state = DungeonState.WAVE_DELAY;
        this.currentWave = 0;

        // Teleport all players to spawn position
        Position spawnPos = template.getSpawnPosition();
        for (String playerName : playerNames) {
            Player player = Server.getInstance().getPlayerExact(playerName);
            if (player != null) {
                player.teleport(spawnPos);
                player.sendMessage("§a§lDungeon Started! §r§a" + template.getDisplayName());
                player.sendMessage("§7Wave §e1 §7of §e" + template.getTotalWaves() + " §7starting soon...");
            }
        }

        // Start first wave after a short delay
        int firstDelay = 5; // 5 seconds before first wave
        waveDelayTask = Server.getInstance().getScheduler().scheduleDelayedTask(plugin, () -> {
            if (state == DungeonState.WAVE_DELAY || state == DungeonState.WAITING) {
                startNextWave();
            }
        }, firstDelay * 20);
    }

    /**
     * Start the next wave of mobs.
     */
    private void startNextWave() {
        if (currentWave >= template.getTotalWaves()) {
            // All waves done, spawn boss
            spawnBoss();
            return;
        }

        DungeonWave wave = template.getWaves().get(currentWave);
        state = DungeonState.WAVE_ACTIVE;

        // Announce wave
        broadcastMessage("§e§lWave " + (currentWave + 1) + "/" + template.getTotalWaves() + " §r§ehas begun!");

        // Spawn all mobs in the wave
        Position spawnPos = template.getSpawnPosition();
        for (DungeonMob mobDef : wave.getMobs()) {
            for (int i = 0; i < mobDef.getCount(); i++) {
                Entity mob = spawnDungeonMob(mobDef, spawnPos);
                if (mob != null) {
                    aliveMobs.put(mob.getId(), mob);
                    dungeonMobIds.add(mob.getId());
                }
            }
        }

        currentWave++;
    }

    /**
     * Spawn a custom dungeon mob at a position near the spawn point.
     */
    private Entity spawnDungeonMob(DungeonMob mobDef, Position basePos) {
        // Add some random offset to spawn position
        double offsetX = (Math.random() - 0.5) * 10;
        double offsetZ = (Math.random() - 0.5) * 10;
        Position spawnPos = Position.fromObject(
                basePos.add(offsetX, 0, offsetZ), basePos.getLevel());

        // Find a safe Y position
        spawnPos.y = basePos.getLevel().getHighestBlockAt(
                (int) spawnPos.x, (int) spawnPos.z) + 1;

        CompoundTag nbt = createEntityNBT(spawnPos);
        Entity entity = Entity.createEntity(getEntityNetworkId(mobDef.getType()),
                spawnPos.getChunk(), nbt);

        if (entity == null) {
            // Fallback: try creating by identifier string
            entity = Entity.createEntity("minecraft:" + mobDef.getType(), spawnPos);
        }

        if (entity != null) {
            entity.setNameTag(mobDef.getName());
            entity.setNameTagVisible(true);
            entity.setNameTagAlwaysVisible(true);

            if (entity instanceof EntityLiving) {
                EntityLiving living = (EntityLiving) entity;
                living.setMaxHealth(mobDef.getHealth());
                living.setHealth(mobDef.getHealth());
            }

            entity.spawnToAll();

            // Store damage info in the entity's named tag for retrieval on damage
            CompoundTag entityNbt = entity.namedTag;
            if (entityNbt == null) {
                entityNbt = new CompoundTag();
            }
            entityNbt.putInt("DungeonDamage", mobDef.getDamage());
            entityNbt.putString("DungeonId", template.getId());
            entity.namedTag = entityNbt;
        }

        return entity;
    }

    /**
     * Spawn the dungeon boss.
     */
    private void spawnBoss() {
        DungeonBoss bossDef = template.getBoss();
        state = DungeonState.BOSS_ACTIVE;

        // Announce boss
        broadcastMessage("§4§l⚠ BOSS ⚠ §r§4" + bossDef.getName() + " §4has appeared!");
        broadcastMessage("§cPrepare yourself!");

        Position spawnPos = template.getSpawnPosition();

        CompoundTag nbt = createEntityNBT(spawnPos);
        Entity entity = Entity.createEntity(getEntityNetworkId(bossDef.getType()),
                spawnPos.getChunk(), nbt);

        if (entity == null) {
            entity = Entity.createEntity("minecraft:" + bossDef.getType(), spawnPos);
        }

        if (entity != null) {
            entity.setNameTag(bossDef.getName());
            entity.setNameTagVisible(true);
            entity.setNameTagAlwaysVisible(true);

            if (entity instanceof EntityLiving) {
                EntityLiving living = (EntityLiving) entity;
                living.setMaxHealth(bossDef.getHealth());
                living.setHealth(bossDef.getHealth());
            }

            entity.spawnToAll();

            bossEntity = entity;
            aliveMobs.put(entity.getId(), entity);
            dungeonMobIds.add(entity.getId());

            // Store boss data in named tag
            CompoundTag entityNbt = entity.namedTag;
            if (entityNbt == null) {
                entityNbt = new CompoundTag();
            }
            entityNbt.putInt("DungeonDamage", bossDef.getDamage());
            entityNbt.putString("DungeonId", template.getId());
            entityNbt.putBoolean("IsBoss", true);
            entity.namedTag = entityNbt;

            // Start boss phase checker
            startBossPhaseChecker();
        }
    }

    /**
     * Start a repeating task that checks boss health and triggers phase changes.
     */
    private void startBossPhaseChecker() {
        if (bossPhaseTask != null) {
            bossPhaseTask.cancel();
        }

        bossPhaseTask = Server.getInstance().getScheduler().scheduleRepeatingTask(plugin, () -> {
            if (bossEntity == null || bossEntity.isClosed() || state != DungeonState.BOSS_ACTIVE) {
                if (bossPhaseTask != null) {
                    bossPhaseTask.cancel();
                }
                return;
            }

            if (!(bossEntity instanceof EntityLiving)) return;
            EntityLiving bossLiving = (EntityLiving) bossEntity;

            float currentHealth = bossLiving.getHealth();
            float maxHealth = bossLiving.getMaxHealth();
            int healthPercent = (int) ((currentHealth / maxHealth) * 100);

            // Check each phase
            for (DungeonBossPhase phase : template.getBoss().getPhases()) {
                int phaseKey = phase.getHealthPercentage();
                if (!triggeredPhases.contains(phaseKey) && healthPercent <= phaseKey) {
                    triggeredPhases.add(phaseKey);
                    triggerBossPhase(phase);
                }
            }
        }, 20); // Check every second
    }

    /**
     * Trigger a boss phase change.
     */
    private void triggerBossPhase(DungeonBossPhase phase) {
        // Announce phase message
        broadcastMessage(phase.getMessage());

        // Apply damage multiplier
        if (phase.getDamageMultiplier() != 1.0 && bossEntity != null && !bossEntity.isClosed()) {
            CompoundTag entityNbt = bossEntity.namedTag;
            if (entityNbt == null) {
                entityNbt = new CompoundTag();
            }
            int baseDamage = entityNbt.getInt("DungeonDamage");
            int newDamage = (int) (baseDamage * phase.getDamageMultiplier());
            entityNbt.putInt("DungeonDamage", newDamage);
            bossEntity.namedTag = entityNbt;
        }

        // Spawn minions if defined
        if (phase.hasSummon()) {
            String[] summonParts = phase.parseSummon();
            if (summonParts.length >= 3) {
                try {
                    String mobType = summonParts[0];
                    int count = Integer.parseInt(summonParts[1]);
                    String mobName = summonParts[2];

                    // Replace underscores in name with spaces for display
                    mobName = mobName.replace("_", " ");

                    Position spawnPos = template.getSpawnPosition();
                    for (int i = 0; i < count; i++) {
                        DungeonMob mobDef = new DungeonMob(mobType,
                                "§c" + mobName, 30, 5, 1);
                        Entity mob = spawnDungeonMob(mobDef, spawnPos);
                        if (mob != null) {
                            aliveMobs.put(mob.getId(), mob);
                            dungeonMobIds.add(mob.getId());
                        }
                    }

                    broadcastMessage("§c§lThe boss summons " + count + " minions!");
                } catch (NumberFormatException e) {
                    plugin.getLogger().warning("Invalid summon format in boss phase: " + phase.getSummon());
                }
            }
        }

        // Execute abilities on phase change
        executeBossAbilities();
    }

    /**
     * Execute boss abilities.
     */
    private void executeBossAbilities() {
        if (bossEntity == null || bossEntity.isClosed()) return;
        DungeonBoss bossDef = template.getBoss();

        Position bossPos = bossEntity.getPosition();

        for (String ability : bossDef.getAbilities()) {
            if (ability.startsWith("summon:")) {
                // Summon minions ability
                String[] parts = ability.split(":");
                if (parts.length >= 4) {
                    try {
                        String mobType = parts[1];
                        int count = Integer.parseInt(parts[2]);
                        String mobName = parts[3].replace("_", " ");

                        Position spawnPos = template.getSpawnPosition();
                        for (int i = 0; i < count; i++) {
                            DungeonMob mobDef = new DungeonMob(mobType,
                                    "§c" + mobName, 20, 4, 1);
                            Entity mob = spawnDungeonMob(mobDef, spawnPos);
                            if (mob != null) {
                                aliveMobs.put(mob.getId(), mob);
                                dungeonMobIds.add(mob.getId());
                            }
                        }
                    } catch (NumberFormatException e) {
                        plugin.getLogger().warning("Invalid summon ability format: " + ability);
                    }
                }
            } else if (ability.equals("lightning")) {
                // Strike lightning near a random player
                strikeLightningOnRandomPlayer();
            } else if (ability.equals("fire_explosion")) {
                // Create a visual fire effect near the boss
                strikeLightningAt(bossPos);
            } else if (ability.equals("teleport")) {
                // Teleport boss to a random player
                teleportBossToRandomPlayer();
            } else if (ability.equals("wither_effect")) {
                // Apply wither effect to nearby players
                applyWitherToPlayers();
            }
        }
    }

    /**
     * Strike lightning on a random player in the dungeon.
     */
    private void strikeLightningOnRandomPlayer() {
        List<Player> players = getOnlinePlayers();
        if (players.isEmpty()) return;

        Player target = players.get((int) (Math.random() * players.size()));
        strikeLightningAt(target.getPosition());
    }

    /**
     * Strike lightning at a position.
     */
    private void strikeLightningAt(Position pos) {
        // Spawn a lightning bolt entity using network ID 93
        CompoundTag nbt = createEntityNBT(pos);
        Entity lightning = Entity.createEntity(93, pos.getChunk(), nbt);
        if (lightning != null) {
            lightning.spawnToAll();
            // Remove after a short time
            Server.getInstance().getScheduler().scheduleDelayedTask(plugin,
                    lightning::close, 30);
        }
    }

    /**
     * Teleport the boss to a random player.
     */
    private void teleportBossToRandomPlayer() {
        if (bossEntity == null || bossEntity.isClosed()) return;

        List<Player> players = getOnlinePlayers();
        if (players.isEmpty()) return;

        Player target = players.get((int) (Math.random() * players.size()));
        Position targetPos = target.getPosition();

        // Add offset so boss doesn't teleport directly on top of player
        double offsetX = (Math.random() - 0.5) * 6;
        double offsetZ = (Math.random() - 0.5) * 6;
        bossEntity.teleport(Position.fromObject(
                targetPos.add(offsetX, 0, offsetZ), targetPos.getLevel()));

        broadcastMessage("§5The boss teleports!");
    }

    /**
     * Apply wither effect to all players in the dungeon.
     */
    private void applyWitherToPlayers() {
        for (Player player : getOnlinePlayers()) {
            Effect wither = Effect.getEffect(Effect.WITHER);
            wither.setDuration(5 * 20); // 5 seconds
            wither.setAmplifier(1);
            player.addEffect(wither);
        }
    }

    /**
     * Called when an entity dies in this dungeon.
     * Handles wave progression and boss death.
     */
    public void onMobDeath(Entity entity) {
        long entityId = entity.getId();

        if (!dungeonMobIds.contains(entityId)) return;
        dungeonMobIds.remove(entityId);
        aliveMobs.remove(entityId);

        // Check if it was the boss
        if (bossEntity != null && entity.getId() == bossEntity.getId()) {
            onBossDeath();
            return;
        }

        // Check if all mobs in current wave are dead
        if (aliveMobs.isEmpty() && state == DungeonState.WAVE_ACTIVE) {
            onWaveCleared();
        }
    }

    /**
     * Called when a wave is cleared (all mobs dead).
     */
    private void onWaveCleared() {
        broadcastMessage("§a§lWave " + currentWave + " cleared!");

        if (currentWave >= template.getTotalWaves()) {
            // All waves done, spawn boss after delay
            state = DungeonState.WAVE_DELAY;
            broadcastMessage("§eThe boss approaches in 5 seconds...");

            waveDelayTask = Server.getInstance().getScheduler().scheduleDelayedTask(plugin, () -> {
                if (state == DungeonState.WAVE_DELAY) {
                    spawnBoss();
                }
            }, 5 * 20);
        } else {
            // Start next wave after delay
            state = DungeonState.WAVE_DELAY;
            int delay = template.getWaves().get(currentWave - 1).getDelay();

            broadcastMessage("§7Next wave in §e" + delay + " §7seconds...");

            waveDelayTask = Server.getInstance().getScheduler().scheduleDelayedTask(plugin, () -> {
                if (state == DungeonState.WAVE_DELAY) {
                    startNextWave();
                }
            }, delay * 20);
        }
    }

    /**
     * Called when the boss dies.
     */
    private void onBossDeath() {
        state = DungeonState.COMPLETED;
        completionTime = System.currentTimeMillis();
        long duration = (completionTime - startTime) / 1000;

        // Cancel boss phase task
        if (bossPhaseTask != null) {
            bossPhaseTask.cancel();
            bossPhaseTask = null;
        }

        bossEntity = null;

        // Announce completion
        broadcastMessage("§a§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        broadcastMessage("§a§l        DUNGEON COMPLETED!");
        broadcastMessage("§a§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        broadcastMessage("§eDungeon: §f" + template.getDisplayName());
        broadcastMessage("§eTime: §f" + formatDuration(duration));

        // Broadcast completion if enabled
        if (plugin.getConfig().getBoolean("settings.completion-broadcast", true)) {
            String playerList = String.join(", ", playerNames);
            Server.getInstance().broadcastMessage(
                    "§6§l[CustomDungeons] §r§e" + playerList +
                            " §acompleted §e" + template.getDisplayName() +
                            " §ain §e" + formatDuration(duration) + "§a!");
        }

        // Generate and distribute loot
        List<Item> lootItems = plugin.getLootGenerator().generateLoot(template);
        distributeLoot(lootItems);

        // Record completion in leaderboard
        plugin.getDungeonManager().recordCompletion(template.getId(), playerNames, duration);

        // Set cooldowns for all players
        int cooldownSeconds = template.getCooldown();
        for (String playerName : playerNames) {
            plugin.getDungeonManager().setCooldown(playerName, template.getId(), cooldownSeconds);
        }

        // Teleport players back after a delay
        Server.getInstance().getScheduler().scheduleDelayedTask(plugin, () -> {
            teleportPlayersBack();
            cleanup();
        }, 10 * 20); // 10 seconds to admire victory
    }

    /**
     * Fail the dungeon (all players died or left).
     */
    private void failDungeon() {
        if (state == DungeonState.COMPLETED || state == DungeonState.FAILED) return;

        state = DungeonState.FAILED;
        broadcastMessage("§c§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        broadcastMessage("§c§l        DUNGEON FAILED!");
        broadcastMessage("§c§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        broadcastMessage("§cAll players have been defeated.");

        // Kill remaining mobs
        killAllMobs();

        // Teleport players back after a short delay
        Server.getInstance().getScheduler().scheduleDelayedTask(plugin, () -> {
            teleportPlayersBack();
            cleanup();
        }, 5 * 20);
    }

    /**
     * Handle a player death in the dungeon.
     * @return true if the dungeon should fail (all players dead)
     */
    public boolean onPlayerDeath(Player player) {
        String playerName = player.getName().toLowerCase();

        if (!playerNames.contains(playerName)) return false;

        boolean respawnInDungeon = plugin.getConfig().getBoolean("settings.respawn-in-dungeon", true);

        if (respawnInDungeon) {
            // Respawn player in dungeon after a delay
            Server.getInstance().getScheduler().scheduleDelayedTask(plugin, () -> {
                if (player.isOnline() && playerNames.contains(playerName)) {
                    player.teleport(template.getSpawnPosition());
                    player.setHealth(player.getMaxHealth());
                    player.sendMessage("§eYou respawned in the dungeon!");
                }
            }, 3 * 20);
            return false;
        } else {
            // Remove player from dungeon on death
            playerNames.remove(playerName);

            if (playerNames.isEmpty()) {
                failDungeon();
                return true;
            }

            player.sendMessage("§cYou have been eliminated from the dungeon!");
            return false;
        }
    }

    /**
     * Kill all remaining mobs in the dungeon.
     */
    private void killAllMobs() {
        for (Entity mob : aliveMobs.values()) {
            if (mob != null && !mob.isClosed()) {
                mob.close();
            }
        }
        aliveMobs.clear();
        dungeonMobIds.clear();
        bossEntity = null;
    }

    /**
     * Distribute loot items to players.
     */
    private void distributeLoot(List<Item> lootItems) {
        if (lootItems.isEmpty()) return;

        List<Player> players = getOnlinePlayers();
        if (players.isEmpty()) return;

        broadcastMessage("§a§lLoot Drops:");

        for (Item item : lootItems) {
            // Give to a random player
            Player recipient = players.get((int) (Math.random() * players.size()));

            String itemName = item.hasCustomName() ? item.getCustomName() :
                    item.getName();
            broadcastMessage("§7- §f" + itemName + " §7x" + item.getCount() +
                    " §a→ " + recipient.getName());

            // Drop item at the player's location or give directly
            if (recipient.getInventory().canAddItem(item)) {
                recipient.getInventory().addItem(item);
            } else {
                recipient.getLevel().dropItem(recipient.getPosition(), item);
                recipient.sendMessage("§cYour inventory is full! Item dropped on the ground.");
            }
        }
    }

    /**
     * Teleport all players back to their original positions.
     */
    private void teleportPlayersBack() {
        for (String playerName : playerNames) {
            Player player = Server.getInstance().getPlayerExact(playerName);
            if (player != null && player.isOnline()) {
                Position originalPos = originalPositions.get(playerName);
                if (originalPos != null) {
                    player.teleport(originalPos);
                } else {
                    player.teleport(player.getLevel().getSpawnLocation());
                }
                player.sendMessage("§eYou have left the dungeon.");
            }
        }
    }

    /**
     * Clean up this dungeon instance.
     */
    public void cleanup() {
        // Cancel tasks
        if (waveDelayTask != null) {
            waveDelayTask.cancel();
            waveDelayTask = null;
        }
        if (bossPhaseTask != null) {
            bossPhaseTask.cancel();
            bossPhaseTask = null;
        }

        // Kill remaining mobs
        killAllMobs();

        // Remove from dungeon manager
        plugin.getDungeonManager().removeInstance(this);
    }

    /**
     * Force remove a player and teleport them back.
     */
    public void forcePlayerLeave(Player player) {
        String playerName = player.getName().toLowerCase();
        playerNames.remove(playerName);

        Position originalPos = originalPositions.get(playerName);
        if (originalPos != null) {
            player.teleport(originalPos);
        } else {
            player.teleport(player.getLevel().getSpawnLocation());
        }
        player.sendMessage("§eYou have left the dungeon.");

        originalPositions.remove(playerName);

        if (playerNames.isEmpty()) {
            failDungeon();
        }
    }

    /**
     * Broadcast a message to all players in the dungeon.
     */
    public void broadcastMessage(String message) {
        for (String playerName : playerNames) {
            Player player = Server.getInstance().getPlayerExact(playerName);
            if (player != null && player.isOnline()) {
                player.sendMessage(message);
            }
        }
    }

    /**
     * Get all online players in this dungeon instance.
     */
    public List<Player> getOnlinePlayers() {
        List<Player> players = new ArrayList<>();
        for (String playerName : playerNames) {
            Player player = Server.getInstance().getPlayerExact(playerName);
            if (player != null && player.isOnline()) {
                players.add(player);
            }
        }
        return players;
    }

    /**
     * Check if an entity belongs to this dungeon.
     */
    public boolean isDungeonMob(Entity entity) {
        return dungeonMobIds.contains(entity.getId());
    }

    /**
     * Check if this instance is the boss entity.
     */
    public boolean isBoss(Entity entity) {
        return bossEntity != null && entity.getId() == bossEntity.getId();
    }

    /**
     * Get the entity's custom damage value for this dungeon.
     */
    public int getMobDamage(Entity entity) {
        CompoundTag nbt = entity.namedTag;
        if (nbt != null && nbt.contains("DungeonDamage")) {
            return nbt.getInt("DungeonDamage");
        }
        return 0;
    }

    // --- Helper Methods ---

    /**
     * Create default NBT for entity spawning.
     */
    private CompoundTag createEntityNBT(Position pos) {
        return new CompoundTag()
                .putList(new ListTag<DoubleTag>("Pos")
                        .add(new DoubleTag("", pos.x))
                        .add(new DoubleTag("", pos.y))
                        .add(new DoubleTag("", pos.z)))
                .putList(new ListTag<DoubleTag>("Motion")
                        .add(new DoubleTag("", 0))
                        .add(new DoubleTag("", 0))
                        .add(new DoubleTag("", 0)))
                .putList(new ListTag<FloatTag>("Rotation")
                        .add(new FloatTag("", (float) (Math.random() * 360)))
                        .add(new FloatTag("", 0)));
    }

    /**
     * Get the entity network ID for a given entity type string.
     */
    private int getEntityNetworkId(String type) {
        switch (type.toLowerCase()) {
            case "zombie":
            case "crypt_zombie":
            case "crypt_brute":
            case "berserk_minion":
            case "crypt_minion":
                return 32; // EntityZombie.NETWORK_ID
            case "skeleton":
            case "crypt_archer":
                return 34; // EntitySkeleton.NETWORK_ID
            case "wither_skeleton":
                return 48; // EntityWitherSkeleton.NETWORK_ID
            case "blaze":
            case "temple_blaze":
            case "blaze_elite":
            case "fire_minion":
            case "inferno_minion":
                return 43; // EntityBlaze.NETWORK_ID
            case "magma_cube":
            case "magma_guardian":
                return 42; // EntityMagmaCube.NETWORK_ID
            case "ghast":
            case "fire_spirit":
                return 41; // EntityGhast.NETWORK_ID
            case "enderman":
            case "void_walker":
            case "void_sentinel":
            case "void_stalker":
            case "void_echo":
            case "rift_echo":
                return 38; // EntityEnderman.NETWORK_ID
            case "endermite":
            case "void_parasite":
            case "void_swarm":
            case "despair_spawn":
                return 55; // EntityEndermite.NETWORK_ID
            case "shulker":
            case "fortress_guard":
                return 54; // EntityShulker.NETWORK_ID
            default:
                return 32; // Default to zombie
        }
    }

    /**
     * Format a duration in seconds to a readable string.
     */
    private String formatDuration(long seconds) {
        long minutes = seconds / 60;
        long secs = seconds % 60;
        if (minutes > 0) {
            return minutes + "m " + secs + "s";
        }
        return secs + "s";
    }

    // --- Getters ---

    public DungeonTemplate getTemplate() {
        return template;
    }

    public Set<String> getPlayerNames() {
        return Collections.unmodifiableSet(playerNames);
    }

    public DungeonState getState() {
        return state;
    }

    public int getCurrentWave() {
        return currentWave;
    }

    public long getStartTime() {
        return startTime;
    }

    public long getCompletionTime() {
        return completionTime;
    }

    public boolean isCompleted() {
        return state == DungeonState.COMPLETED;
    }

    public boolean isFailed() {
        return state == DungeonState.FAILED;
    }

    public boolean isActive() {
        return state != DungeonState.COMPLETED && state != DungeonState.FAILED;
    }

    public int getAliveMobCount() {
        return aliveMobs.size();
    }

    @Override
    public String toString() {
        return "DungeonInstance{template='" + template.getId() +
                "', players=" + playerNames.size() +
                ", state=" + state +
                ", wave=" + currentWave + "/" + template.getTotalWaves() + "}";
    }
}
