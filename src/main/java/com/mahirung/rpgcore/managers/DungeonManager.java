package com.mahirung.rpgcore.managers;

import com.mahirung.rpgcore.RPGCore;
import com.mahirung.rpgcore.data.PlayerData;
import com.mahirung.rpgcore.managers.PartyManager.Party;
import com.mahirung.rpgcore.util.ChatUtil;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.bukkit.WorldEditPlugin;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.function.pattern.BlockPattern;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldedit.world.block.BlockTypes;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.FileInputStream;
import java.util.*;

public class DungeonManager {

    private final RPGCore plugin;
    private final ConfigManager configManager;
    private final PlayerDataManager playerDataManager;
    private final PartyManager partyManager;

    private Map<String, FileConfiguration> dungeonConfigs = new HashMap<>();
    private final Map<UUID, Long> playerCooldowns = new HashMap<>();
    private final Map<UUID, ActiveDungeonInstance> activeInstances = new HashMap<>();

    private Location lobbyLocation;
    private final WorldEdit worldEdit;
    private final WorldEditPlugin worldEditPlugin;

    private static final long GLOBAL_DUNGEON_COOLDOWN_MS = 30 * 60 * 1000;

    public DungeonManager(RPGCore plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.playerDataManager = plugin.getPlayerDataManager();
        this.partyManager = plugin.getPartyManager();

        Plugin wePlugin = Bukkit.getPluginManager().getPlugin("FastAsyncWorldEdit");
        if (wePlugin == null) wePlugin = Bukkit.getPluginManager().getPlugin("WorldEdit");

        if (wePlugin instanceof WorldEditPlugin wep) {
            this.worldEditPlugin = wep;
            this.worldEdit = WorldEdit.getInstance();
        } else {
            this.worldEditPlugin = null;
            this.worldEdit = null;
            plugin.getLogger().severe("WorldEdit/FAWE 플러그인이 없습니다. 던전 시스템 비활성화.");
        }

        loadDungeons();
        loadLobbyLocation(); // public으로 노출
    }

    public void loadDungeons() {
        dungeonConfigs = configManager.getDungeonConfigs();
        plugin.getLogger().info(dungeonConfigs.size() + "개의 던전 설정을 로드했습니다.");
    }

    public void loadLobbyLocation() {
        ConfigurationSection lobbySection = configManager.getMainConfig().getConfigurationSection("lobby-location");
        if (lobbySection == null) return;
        try {
            String worldName = lobbySection.getString("world");
            double x = lobbySection.getDouble("x");
            double y = lobbySection.getDouble("y");
            double z = lobbySection.getDouble("z");
            float yaw = (float) lobbySection.getDouble("yaw");
            float pitch = (float) lobbySection.getDouble("pitch");
            org.bukkit.World world = Bukkit.getWorld(worldName);
            if (world != null) {
                this.lobbyLocation = new Location(world, x, y, z, yaw, pitch);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("config.yml의 'lobby-location' 설정 로드 중 오류 발생.");
        }
    }

    public List<String> getAllDungeonIds() {
        return new ArrayList<>(dungeonConfigs.keySet());
    }

    private ActiveDungeonInstance findInstanceByPlayer(UUID playerUUID) {
        for (ActiveDungeonInstance instance : activeInstances.values()) {
            if (instance.hasMember(playerUUID)) {
                return instance;
            }
        }
        return null;
    }

    private void setGlobalCooldown(Player player) {
        playerCooldowns.put(player.getUniqueId(), System.currentTimeMillis() + GLOBAL_DUNGEON_COOLDOWN_MS);
    }

    private long getCooldownLeft(UUID uuid) {
        Long endTime = playerCooldowns.get(uuid);
        if (endTime == null) return 0;
        long timeLeft = endTime - System.currentTimeMillis();
        if (timeLeft <= 0) {
            playerCooldowns.remove(uuid);
            return 0;
        }
        return timeLeft;
    }

    public void attemptEnterDungeon(Player player, String dungeonId) {
        if (worldEdit == null) {
            player.sendMessage(ChatUtil.format("&c[Dungeon] &f던전 시스템이 비활성화 상태입니다."));
            return;
        }

        FileConfiguration dungeonConfig = dungeonConfigs.get(dungeonId);
        if (dungeonConfig == null) {
            player.sendMessage(ChatUtil.format("&c[Dungeon] &f''{0}'' ID의 던전을 찾을 수 없습니다.", dungeonId));
            return;
        }

        Party party = partyManager.getParty(player.getUniqueId());
        if (party == null) {
            player.sendMessage(ChatUtil.format("&c[Dungeon] &f던전은 파티 상태로만 입장할 수 있습니다."));
            return;
        }
        if (!party.isLeader(player.getUniqueId())) {
            player.sendMessage(ChatUtil.format("&c[Dungeon] &f파티장만 입장 신청할 수 있습니다."));
            return;
        }
        if (findInstanceByPlayer(player.getUniqueId()) != null) {
            player.sendMessage(ChatUtil.format("&c[Dungeon] &f이미 던전에 입장해 있습니다."));
            return;
        }

        int minLevel = dungeonConfig.getInt("level-limit.min", 1);
        int maxLevel = dungeonConfig.getInt("level-limit.max", 100);

        for (Player member : party.getOnlineMembers()) {
            PlayerData memberData = playerDataManager.getPlayerData(member.getUniqueId());
            int memberLevel = (memberData != null) ? memberData.getLevel() : 1;
            if (memberLevel < minLevel || memberLevel > maxLevel) {
                party.broadcastMessage(ChatUtil.format("&c[Dungeon] &f''{0}''님이 레벨 제한({1}~{2})을 충족하지 못합니다.", member.getName(), minLevel, maxLevel));
                return;
            }
            long cooldownLeft = getCooldownLeft(member.getUniqueId());
            if (cooldownLeft > 0) {
                party.broadcastMessage(ChatUtil.format("&c[Dungeon] &f''{0}''님이 던전 쿨타임이 ({1}분) 남았습니다.", member.getName(), (cooldownLeft / 60000) + 1));
                return;
            }
        }

        party.broadcastMessage(ChatUtil.format("&a[Dungeon] &f''{0}'' 던전에 입장합니다!", dungeonConfig.getString("display-name", dungeonId)));
        createAndEnterInstance(player, party, dungeonConfig);
    }

    private void createAndEnterInstance(Player leader, Party party, FileConfiguration dungeonConfig) {
        String schematicName = dungeonConfig.getString("schematic-name");
        String instanceWorldName = dungeonConfig.getString("instance-world");

        org.bukkit.World bukkitWorld = Bukkit.getWorld(instanceWorldName);
        if (bukkitWorld == null) {
            party.broadcastMessage(ChatUtil.format("&c[Dungeon] &f인스턴스 월드 ''{0}''을 찾을 수 없습니다.", instanceWorldName));
            return;
        }

        File schematicFile = new File(worldEditPlugin.getDataFolder(), "schematics/" + schematicName);
        if (!schematicFile.exists()) {
            party.broadcastMessage(ChatUtil.format("&c[Dungeon] &f스키매틱 파일 ''{0}''을 찾을 수 없습니다.", schematicName));
            return;
        }

        BlockVector3 pasteVector = BlockVector3.at(
                new Random().nextInt(1000, 5000),
                60,
                new Random().nextInt(1000, 5000)
        );

        new BukkitRunnable() {
            @Override
            public void run() {
                try (FileInputStream fis = new FileInputStream(schematicFile);
                     ClipboardReader reader = ClipboardFormats.findByFile(schematicFile).getReader(fis)) {

                    Clipboard clipboard = reader.read();
                    World weWorld = BukkitAdapter.adapt(bukkitWorld);

                    try (EditSession editSession = WorldEdit.getInstance()
                            .newEditSessionBuilder()
                            .world(weWorld)
                            .fastMode(true)
                            .build()) {

                        ClipboardHolder holder = new ClipboardHolder(clipboard);
                         Operation operation = holder.createPaste(editSession)
                                .to(pasteVector)
                                .ignoreAirBlocks(false)
                                .build();
                        Operations.complete(operation);

                        // 메인 스레드에서 텔레포트/인스턴스 등록
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                Location spawnPoint = new Location(
                                        bukkitWorld,
                                        pasteVector.getX() + 0.5,
                                        pasteVector.getY() + 2.5,
                                        pasteVector.getZ() + 0.5
                                );

                                ActiveDungeonInstance instance = new ActiveDungeonInstance(party.getLeader(), dungeonConfig.getString("id"), instanceWorldName);
                                instance.setPasteLocation(pasteVector);
                                activeInstances.put(party.getLeader(), instance);

                                for (Player member : party.getOnlineMembers()) {
                                    setGlobalCooldown(member);
                                    instance.addMember(member.getUniqueId());
                                    member.teleport(spawnPoint);
                                }
                                party.broadcastMessage(ChatUtil.format("&a[Dungeon] &f던전이 생성되었습니다!"));
                            }
                        }.runTask(plugin);
                    }
                } catch (Exception e) {
                    leader.sendMessage(ChatUtil.format("&c[Dungeon] &f던전 생성 중 치명적인 오류 발생! (콘솔 확인)"));
                    plugin.getLogger().severe("WorldEdit Paste Error: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    public void exitDungeon(Player player) {
        ActiveDungeonInstance instance = findInstanceByPlayer(player.getUniqueId());
        if (instance == null) {
            player.sendMessage(ChatUtil.format("&c[Dungeon] &f현재 던전에 입장한 상태가 아닙니다."));
            return;
        }

        Location exitLoc = (lobbyLocation != null)
                ? lobbyLocation
                : player.getServer().getWorlds().get(0).getSpawnLocation();

        player.teleport(exitLoc);
        player.sendMessage(ChatUtil.format("&a[Dungeon] &f던전에서 퇴장했습니다."));

        instance.removeMember(player.getUniqueId());

        if (instance.isLeader(player.getUniqueId()) || instance.isEmpty()) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    org.bukkit.World bukkitWorld = Bukkit.getWorld(instance.getInstanceWorldName());
                    if (bukkitWorld == null) return;

                    // 남은 파티원 강제 퇴장
                    for (UUID memberUUID : instance.getMembers()) {
                        Player member = Bukkit.getPlayer(memberUUID);
                        if (member != null && member.isOnline()) {
                            member.teleport(exitLoc);
                        }
                    }

                    // 인스턴스 영역 청소
                    if (worldEdit != null) {
                        World weWorld = BukkitAdapter.adapt(bukkitWorld);
                        try (EditSession editSession = WorldEdit.getInstance()
                                .newEditSessionBuilder()
                                .world(weWorld)
                                .fastMode(true)
                                .build()) {

                            BlockVector3 pasteLoc = instance.getPasteLocation();
                            BlockVector3 min = pasteLoc.add(-50, -10, -50);
                            BlockVector3 max = pasteLoc.add(50, 50, 50);

                            CuboidRegion region = new CuboidRegion(weWorld, min, max);
                            editSession.setBlocks(region, new BlockPattern(BlockTypes.AIR.getDefaultState()));
                        } catch (Exception e) {
                            plugin.getLogger().severe("WorldEdit 던전 청소 오류: " + e.getMessage());
                            e.printStackTrace();
                        }
                    }

                    activeInstances.remove(instance.getLeaderUUID());
                    plugin.getLogger().info("던전 인스턴스 " + instance.getInstanceWorldName() + "의 영역을 청소했습니다.");
                }
            }.runTask(plugin);
        }
    }

    public void showDungeonList(Player player) {
        player.sendMessage(ChatUtil.format("&a===== [ 던전 목록 ] ====="));
        for (Map.Entry<String, FileConfiguration> entry : dungeonConfigs.entrySet()) {
            String id = entry.getKey();
            FileConfiguration config = entry.getValue();
            String name = config.getString("display-name", id);
            int min = config.getInt("level-limit.min", 1);
            int max = config.getInt("level-limit.max", 100);

            player.sendMessage(ChatUtil.format("&e- {0} &7(ID: {1}) | &aLv.{2} ~ {3}", name, id, min, max));
        }
    }

    public static class ActiveDungeonInstance {
        private final UUID leaderUUID;
        private final String dungeonId;
        private final String instanceWorldName;
        private final Set<UUID> members = new HashSet<>();
        private BlockVector3 pasteLocation;

        public ActiveDungeonInstance(UUID leaderUUID, String dungeonId, String instanceWorldName) {
            this.leaderUUID = leaderUUID;
            this.dungeonId = dungeonId;
            this.instanceWorldName = instanceWorldName;
        }

        public void setPasteLocation(BlockVector3 location) { this.pasteLocation = location; }
        public BlockVector3 getPasteLocation() { return pasteLocation; }

        public UUID getLeaderUUID() { return leaderUUID; }
        public boolean isLeader(UUID uuid) { return leaderUUID.equals(uuid); }
        public Set<UUID> getMembers() { return members; }
        public String getInstanceWorldName() { return instanceWorldName; }
        public void addMember(UUID uuid) { members.add(uuid); }
        public void removeMember(UUID uuid) { members.remove(uuid); }
        public boolean hasMember(UUID uuid) { return members.contains(uuid); }
        public boolean isEmpty() { return members.isEmpty(); }
    }
}
