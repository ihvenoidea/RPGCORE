package com.mahirung.rpgcore.listeners;

import com.mahirung.rpgcore.RPGCore;
import com.mahirung.rpgcore.managers.DungeonManager;
import com.mahirung.rpgcore.managers.PartyManager;
import com.mahirung.rpgcore.util.ChatUtil;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

/**
 * 플레이어 이동 이벤트 리스너
 * - 던전 입장/퇴장 감지
 * - 특정 지역 제약 처리
 */
public class PlayerMoveListener implements Listener {

    private final RPGCore plugin;
    private final DungeonManager dungeonManager;
    private final PartyManager partyManager;

    public PlayerMoveListener(RPGCore plugin) {
        this.plugin = plugin;
        this.dungeonManager = plugin.getDungeonManager();
        this.partyManager = plugin.getPartyManager();
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Location from = event.getFrom();
        Location to = event.getTo();

        if (to == null) return;

        // 예시: 던전 로비에서 특정 좌표 범위를 벗어나면 자동 퇴장 처리
        if (dungeonManager != null && dungeonManager.getAllDungeonIds().size() > 0) {
            // 로비 위치가 설정돼 있다면 체크
            Location lobby = plugin.getDungeonManager().getLobbyLocation();
            if (lobby != null && from.getWorld().equals(lobby.getWorld())) {
                double distance = to.distance(lobby);
                if (distance > 200) { // 로비 반경 200 블록 이상 벗어나면
                    player.sendMessage(ChatUtil.format("&c[Dungeon] &f로비를 벗어났습니다. 던전 시스템에서 퇴장 처리됩니다."));
                    dungeonManager.exitDungeon(player);
                }
            }
        }

        // 예시: 파티 채팅 모드가 켜져 있을 때 특정 지역 이동 시 알림
        if (partyManager != null && partyManager.isPartyChatToggled(player.getUniqueId())) {
            if (!from.getBlock().equals(to.getBlock())) {
                player.sendMessage(ChatUtil.format("&7[Party] 위치 이동: X={0}, Y={1}, Z={2}",
                        to.getBlockX(), to.getBlockY(), to.getBlockZ()));
            }
        }
    }
}
