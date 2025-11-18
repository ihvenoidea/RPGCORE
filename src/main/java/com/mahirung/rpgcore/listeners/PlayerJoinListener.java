package com.mahirung.rpgcore.listeners;

import com.mahirung.rpgcore.RPGCore;
import com.mahirung.rpgcore.data.PlayerData;
import com.mahirung.rpgcore.managers.PlayerDataManager;
import com.mahirung.rpgcore.util.ChatUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * 플레이어 접속 이벤트 리스너
 * - 접속 시 PlayerData를 비동기로 로드
 * - 신규/기존 플레이어에 따라 안내 메시지 출력
 */
public class PlayerJoinListener implements Listener {

    private final RPGCore plugin;
    private final PlayerDataManager playerDataManager;

    public PlayerJoinListener(RPGCore plugin) {
        this.plugin = plugin;
        this.playerDataManager = plugin.getPlayerDataManager();
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // PlayerData 비동기 로드
        playerDataManager.loadPlayerDataAsync(player.getUniqueId(), playerData -> {
            if (playerData == null) {
                // 데이터 로드 실패
                player.kickPlayer(ChatUtil.format("&c[RPGCore] &f플레이어 데이터를 불러오는 데 실패했습니다. 다시 접속해주세요."));
                plugin.getLogger().severe(player.getName() + "님의 데이터 로드 실패");
                return;
            }

            if (playerData.isNewPlayer()) {
                // 신규 플레이어
                player.sendMessage(ChatUtil.format("&a[RPGCore] &fRPGCore의 세계에 오신 것을 환영합니다!"));
                player.sendMessage(ChatUtil.format("&e/class &f명령어로 직업을 선택하세요."));
            } else {
                // 기존 플레이어
                player.sendMessage(ChatUtil.format("&a[RPGCore] &f데이터 로드 완료! (Lv." + playerData.getLevel() + ")"));
            }

            // TODO: 스탯, 체력, 마나 적용
            // 예: plugin.getClassManager().applyStats(player, playerData);
        });
    }
}
