package com.mahirung.rpgcore.listeners;

import com.mahirung.rpgcore.RPGCore;
import com.mahirung.rpgcore.managers.DungeonManager;
import com.mahirung.rpgcore.managers.PartyManager;
import com.mahirung.rpgcore.util.ChatUtil;
import com.mahirung.rpgcore.util.ItemUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.ItemStack;

/**
 * 플레이어 아이템 드롭 이벤트 리스너
 * - 특정 아이템 드롭 방지
 * - 파티/던전 시스템과 연동
 */
public class PlayerDropItemListener implements Listener {

    private final RPGCore plugin;
    private final DungeonManager dungeonManager;
    private final PartyManager partyManager;

    public PlayerDropItemListener(RPGCore plugin) {
        this.plugin = plugin;
        this.dungeonManager = plugin.getDungeonManager();
        this.partyManager = plugin.getPartyManager();
    }

    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        ItemStack dropped = event.getItemDrop().getItemStack();

        if (dropped == null) return;

        // 예시 1: 강화/룬 아이템은 드롭 금지
        String enhanceLevel = ItemUtil.getNBTString(dropped, "enhance_level");
        String runeId = ItemUtil.getNBTString(dropped, "rpgcore_runes_list");
        if (enhanceLevel != null || runeId != null) {
            event.setCancelled(true);
            player.sendMessage(ChatUtil.format("&c[RPGCore] &f강화/룬 장비는 버릴 수 없습니다."));
            return;
        }

        // 예시 2: 던전 내부에서는 아이템 드롭 금지
        if (dungeonManager != null && dungeonManager.getAllDungeonIds().size() > 0) {
            if (dungeonManager.getLobbyLocation() != null &&
                player.getWorld().equals(dungeonManager.getLobbyLocation().getWorld())) {
                event.setCancelled(true);
                player.sendMessage(ChatUtil.format("&c[Dungeon] &f던전 로비에서는 아이템을 버릴 수 없습니다."));
                return;
            }
        }

        // 예시 3: 파티 채팅 모드가 켜져 있으면 드롭 로그 알림
        if (partyManager != null && partyManager.isPartyChatToggled(player.getUniqueId())) {
            player.sendMessage(ChatUtil.format("&7[Party] ''{0}'' 아이템을 버렸습니다.", dropped.getType().name()));
        }
    }
}
