package com.mahirung.rpgcore.managers;

import com.mahirung.rpgcore.RPGCore;
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
import com.sk89q.worldedit.regions.Region;
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
    private final PartyManager partyManager;

    private Map<String, FileConfiguration> dungeonConfigs = new HashMap<>();
    private final Map<UUID, ActiveDungeonInstance> activeInstances = new HashMap<>();
    private Location lobbyLocation;
    
    private final WorldEdit worldEdit;
    private final WorldEditPlugin worldEditPlugin;

    public DungeonManager(RPGCore plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.partyManager = plugin.getPartyManager();
        
        Plugin wePlugin = Bukkit.getPluginManager().getPlugin("FastAsyncWorldEdit");
        if (wePlugin == null) wePlugin = Bukkit.getPluginManager().getPlugin("WorldEdit");
        if (wePlugin instanceof WorldEditPlugin wep) {
            this.worldEditPlugin = wep;
            this.worldEdit = WorldEdit.getInstance();
        } else {
            this.worldEditPlugin = null;
            this.worldEdit = null;
        }

        loadDungeons();
        loadLobbyLocation();
    }

    public void loadDungeons() {
        dungeonConfigs = configManager.getDungeonConfigs();
    }

    public void loadLobbyLocation() {
        ConfigurationSection sec = configManager.getMainConfig().getConfigurationSection("lobby-location");
        if (sec != null) {
            org.bukkit.World w = Bukkit.getWorld(sec.getString("world", "world"));
            if (w != null) {
                lobbyLocation = new Location(w, sec.getDouble("x"), sec.getDouble("y"), sec.getDouble("z"), (float)sec.getDouble("yaw"), (float)sec.getDouble("pitch"));
            }
        }
    }
    
    public Location getLobbyLocation() { return lobbyLocation; }

    // [Fix] 누락되었던 메소드 복구
    public List<String> getAllDungeonIds() {
        return new ArrayList<>(dungeonConfigs.keySet());
    }

    public void showDungeonList(Player player) {
        player.sendMessage(ChatUtil.format("&a=== 던전 목록 ==="));
        for (String id : dungeonConfigs.keySet()) {
            FileConfiguration c = dungeonConfigs.get(id);
            player.sendMessage(ChatUtil.format("&e- " + c.getString("display-name", id) + " (ID: " + id + ")"));
        }
    }

    public void attemptEnterDungeon(Player player, String dungeonId) {
        if (worldEdit == null) {
            player.sendMessage(ChatUtil.format("&cWorldEdit가 없어 던전을 이용할 수 없습니다."));
            return;
        }
        FileConfiguration dungeonConfig = dungeonConfigs.get(dungeonId);
        if (dungeonConfig == null) {
            player.sendMessage(ChatUtil.format("&c존재하지 않는 던전입니다."));
            return;
        }
        
        Party party = partyManager.getParty(player.getUniqueId());
        if (party == null || !party.isLeader(player.getUniqueId())) {
            player.sendMessage(ChatUtil.format("&c파티장만 입장 신청이 가능합니다."));
            return;
        }
        
        createAndEnterInstance(player, party, dungeonConfig);
    }

    private void createAndEnterInstance(Player leader, Party party, FileConfiguration dungeonConfig) {
        String schematicName = dungeonConfig.getString("schematic-name");
        String instanceWorldName = dungeonConfig.getString("instance-world");
        org.bukkit.World bukkitWorld = Bukkit.getWorld(instanceWorldName);
        
        if (bukkitWorld == null) {
            leader.sendMessage(ChatUtil.format("&c인스턴스 월드를 찾을 수 없습니다."));
            return;
        }

        File schematicFile = new File(worldEditPlugin.getDataFolder(), "schematics/" + schematicName);
        if (!schematicFile.exists()) {
            leader.sendMessage(ChatUtil.format("&c스키매틱 파일을 찾을 수 없습니다: " + schematicName));
            return;
        }

        BlockVector3 pasteVector = BlockVector3.at(new Random().nextInt(1000, 5000), 60, new Random().nextInt(1000, 5000));

        new BukkitRunnable() {
            @Override
            public void run() {
                try (FileInputStream fis = new FileInputStream(schematicFile);
                     ClipboardReader reader = ClipboardFormats.findByFile(schematicFile).getReader(fis)) {

                    Clipboard clipboard = reader.read();
                    World weWorld = BukkitAdapter.adapt(bukkitWorld);

                    try (EditSession editSession = WorldEdit.getInstance().newEditSessionBuilder().world(weWorld).fastMode(true).build()) {
                        ClipboardHolder holder = new ClipboardHolder(clipboard);
                        Operation operation = holder.createPaste(editSession)
                                .to(pasteVector).ignoreAirBlocks(false).build();
                        Operations.complete(operation);

                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                Location spawnPoint = new Location(bukkitWorld, pasteVector.getX()+0.5, pasteVector.getY()+2.5, pasteVector.getZ()+0.5);
                                ActiveDungeonInstance instance = new ActiveDungeonInstance(party.getLeader(), dungeonConfig.getString("id"), instanceWorldName);
                                instance.setPasteLocation(pasteVector);
                                activeInstances.put(party.getLeader(), instance);

                                for (Player member : party.getOnlineMembers()) {
                                    instance.addMember(member.getUniqueId());
                                    member.teleport(spawnPoint);
                                }
                                party.broadcastMessage(ChatUtil.format("&a던전 인스턴스가 생성되었습니다!"));
                            }
                        }.runTask(plugin);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    new BukkitRunnable() {
                        @Override public void run() { leader.sendMessage(ChatUtil.format("&c던전 생성 중 오류 발생!")); }
                    }.runTask(plugin);
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    public void exitDungeon(Player player) {
        ActiveDungeonInstance instance = findInstanceByPlayer(player.getUniqueId());
        if (instance == null) {
            player.sendMessage(ChatUtil.format("&c던전에 있지 않습니다."));
            return;
        }

        Location exitLoc = (lobbyLocation != null) ? lobbyLocation : player.getWorld().getSpawnLocation();
        player.teleport(exitLoc);
        player.sendMessage(ChatUtil.format("&a던전에서 퇴장했습니다."));
        instance.removeMember(player.getUniqueId());

        if (instance.isLeader(player.getUniqueId()) || instance.isEmpty()) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    org.bukkit.World bukkitWorld = Bukkit.getWorld(instance.getInstanceWorldName());
                    if (bukkitWorld == null || worldEdit == null) return;

                    World weWorld = BukkitAdapter.adapt(bukkitWorld);
                    try (EditSession editSession = WorldEdit.getInstance().newEditSessionBuilder().world(weWorld).fastMode(true).build()) {
                        BlockVector3 pasteLoc = instance.getPasteLocation();
                        BlockVector3 min = pasteLoc.add(-50, -10, -50);
                        BlockVector3 max = pasteLoc.add(50, 50, 50);
                        CuboidRegion region = new CuboidRegion(weWorld, min, max);
                        
                        // [Fix] 모호성 해결을 위한 캐스팅
                        editSession.setBlocks((Region) region, new BlockPattern(BlockTypes.AIR.getDefaultState()));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    activeInstances.remove(instance.getLeaderUUID());
                }
            }.runTask(plugin);
        }
    }

    private ActiveDungeonInstance findInstanceByPlayer(UUID uuid) {
        for (ActiveDungeonInstance i : activeInstances.values()) {
            if (i.hasMember(uuid)) return i;
        }
        return null;
    }

    public static class ActiveDungeonInstance {
        private final UUID leaderUUID;
        private final String dungeonId;
        private final String instanceWorldName;
        private final Set<UUID> members = new HashSet<>();
        private BlockVector3 pasteLocation;

        public ActiveDungeonInstance(UUID leader, String id, String world) {
            this.leaderUUID = leader; this.dungeonId = id; this.instanceWorldName = world;
        }
        public void setPasteLocation(BlockVector3 v) { this.pasteLocation = v; }
        public BlockVector3 getPasteLocation() { return pasteLocation; }
        public UUID getLeaderUUID() { return leaderUUID; }
        public boolean isLeader(UUID u) { return leaderUUID.equals(u); }
        public void addMember(UUID u) { members.add(u); }
        public void removeMember(UUID u) { members.remove(u); }
        public boolean hasMember(UUID u) { return members.contains(u); }
        public boolean isEmpty() { return members.isEmpty(); }
        public String getInstanceWorldName() { return instanceWorldName; }
    }
}