package com.mahirung.rpgcore.managers;

import com.mahirung.rpgcore.RPGCore;
import com.mahirung.rpgcore.data.PlayerData;
import com.mahirung.rpgcore.util.ChatUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * 플레이어 데이터 매니저
 * - 플레이어 데이터 로드/저장/캐싱
 * - 마나 회복 스케줄링
 * - 경험치 처리
 */
public class PlayerDataManager {

    private final RPGCore plugin;
    private final File playerDataFolder;
    private final Map<UUID, PlayerData> playerDataCache = new HashMap<>();

    private double manaRegenRate;
    private BukkitTask regenTask;

    public PlayerDataManager(RPGCore plugin) {
        this.plugin = plugin;
        this.playerDataFolder = new File(plugin.getDataFolder(), "playerdata");

        if (!playerDataFolder.exists()) {
            playerDataFolder.mkdirs();
        }
        startRegenTask();
    }

    /** 마나 회복 스케줄러 시작 */
    private void startRegenTask() {
        this.manaRegenRate = plugin.getConfigManager().getMainConfig().getDouble("mana-regeneration.rate-per-tick", 0.5);
        int regenInterval = plugin.getConfigManager().getMainConfig().getInt("mana-regeneration.interval-ticks", 20);

        if (regenTask != null && !regenTask.isCancelled()) regenTask.cancel();

        this.regenTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (UUID uuid : playerDataCache.keySet()) {
                    Player player = Bukkit.getPlayer(uuid);
                    PlayerData data = playerDataCache.get(uuid);

                    if (player != null && data != null && data.hasClass()) {
                        data.regenMana(manaRegenRate);
                        // TODO: 파티클/액션바 업데이트
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, regenInterval);
    }

    /** 플러그인 종료 시 스케줄러 중단 */
    public void shutdown() {
        if (regenTask != null) regenTask.cancel();
    }

    /** 비동기 데이터 로드 */
    public void loadPlayerDataAsync(UUID uuid, Consumer<PlayerData> callback) {
        if (playerDataCache.containsKey(uuid)) {
            callback.accept(playerDataCache.get(uuid));
            return;
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                File playerFile = new File(playerDataFolder, uuid + ".yml");
                PlayerData playerData = playerFile.exists()
                        ? new PlayerData(plugin, uuid, playerFile)
                        : new PlayerData(plugin, uuid);

                playerData.setNewPlayer(!playerFile.exists());

                new BukkitRunnable() {
                    @Override
                    public void run() {
                        playerDataCache.put(uuid, playerData);
                        callback.accept(playerData);
                    }
                }.runTask(plugin);
            }
        }.runTaskAsynchronously(plugin);
    }

    /** 비동기 데이터 저장 */
    public void savePlayerDataAsync(UUID uuid, Consumer<Boolean> callback) {
        PlayerData playerData = playerDataCache.get(uuid);
        if (playerData == null) {
            plugin.getLogger().warning(uuid + " 데이터 저장 시 캐시에 없음");
            callback.accept(false);
            return;
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    File playerFile = new File(playerDataFolder, uuid + ".yml");
                    playerData.saveToFile(playerFile);
                    callback.accept(true);
                } catch (IOException e) {
                    plugin.getLogger().log(Level.SEVERE, uuid + " 데이터 저장 중 오류 발생!", e);
                    callback.accept(false);
                } finally {
                    playerDataCache.remove(uuid); // 저장 후 캐시 제거
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    /** 모든 플레이어 데이터 저장 */
    public void saveAllPlayerData() {
        if (playerDataCache.isEmpty()) return;

        plugin.getLogger().info(playerDataCache.size() + "명의 플레이어 데이터를 저장합니다...");
        for (Map.Entry<UUID, PlayerData> entry : playerDataCache.entrySet()) {
            try {
                File playerFile = new File(playerDataFolder, entry.getKey() + ".yml");
                entry.getValue().saveToFile(playerFile);
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, entry.getKey() + " 데이터 저장 중 오류 발생!", e);
            }
        }
        playerDataCache.clear();
    }

    /** 캐시에서 데이터 조회 */
    public PlayerData getPlayerData(UUID uuid) {
        return playerDataCache.get(uuid);
    }

    /** 경험치 추가 */
    public void addExperience(Player player, double exp) {
        PlayerData playerData = getPlayerData(player.getUniqueId());
        if (playerData == null || !playerData.hasClass()) return;

        boolean leveledUp = playerData.addExperience(exp);
        if (leveledUp) {
            plugin.getClassManager().handleLevelUp(player, playerData);
        } else {
            player.sendMessage(ChatUtil.format("§a+%.2f EXP §7(§e%.2f §7/ §e%.2f§7)",
                    exp, playerData.getCurrentExp(), playerData.getRequiredExp()));
        }
    }

    /** 플레이어 데이터 언로드 (외부에서 호출 가능) */
    public void unloadPlayerData(UUID uuid) {
        PlayerData data = playerDataCache.remove(uuid);
        if (data != null) {
            try {
                File playerFile = new File(playerDataFolder, uuid + ".yml");
                data.saveToFile(playerFile);
                plugin.getLogger().info(uuid + " 플레이어 데이터를 언로드했습니다.");
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, uuid + " 데이터 언로드 중 저장 오류 발생!", e);
            }
        }
    }
}
