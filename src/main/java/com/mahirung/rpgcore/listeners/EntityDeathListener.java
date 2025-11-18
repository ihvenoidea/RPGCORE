package com.mahirung.rpgcore.listeners;

import com.mahirung.rpgcore.RPGCore;
import com.mahirung.rpgcore.managers.ClassManager;
import com.mahirung.rpgcore.managers.PartyManager;
import com.mahirung.rpgcore.managers.PlayerDataManager;
import io.lumine.mythic.api.mobs.MythicMob;
import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.core.mobs.ActiveMob;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 엔티티 사망 이벤트 리스너
 * - 몹 처치 시 경험치 지급
 * - 파티 경험치 분배
 * - 아이템 드롭 처리
 */
public class EntityDeathListener implements Listener {

    private final PlayerDataManager playerDataManager;
    private final PartyManager partyManager;
    private final ClassManager classManager;

    public EntityDeathListener(RPGCore plugin) {
        this.playerDataManager = plugin.getPlayerDataManager();
        this.partyManager = plugin.getPartyManager();
        this.classManager = plugin.getClassManager();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity victim = event.getEntity();
        Player killer = victim.getKiller();

        if (killer == null) {
            // 킬러가 없으면 파티 데미지 로그만 정리
            partyManager.clearDamageLog(victim);
            return;
        }

        // --- 경험치 계산 ---
        double totalExperience;
        Optional<MythicMob> mythicMobOpt = Optional.empty();

        ActiveMob am = MythicBukkit.inst().getMobManager().getMythicMobInstance(victim);
        if (am != null) {
            mythicMobOpt = Optional.ofNullable(am.getType());
        }

        if (mythicMobOpt.isPresent()) {
            totalExperience = classManager.getExperienceFromMythicMob(mythicMobOpt.get());
        } else {
            totalExperience = event.getDroppedExp();
            event.setDroppedExp(0); // 기본 경험치 드롭 제거
        }

        boolean isInParty = partyManager.isInParty(killer.getUniqueId());

        // --- 아이템 분배 ---
        handleItemDistribution(event, killer, isInParty);

        // --- 경험치 분배 ---
        if (isInParty) {
            partyManager.distributeExperience(victim, totalExperience);
        } else {
            playerDataManager.addExperience(killer, totalExperience);
        }

        // --- 데미지 로그 정리 ---
        partyManager.clearDamageLog(victim);
    }

    /** 아이템 드롭 처리 */
    private void handleItemDistribution(EntityDeathEvent event, Player killer, boolean isInParty) {
        List<ItemStack> drops = new ArrayList<>(event.getDrops());
        event.getDrops().clear();

        Player receiver = killer;
        if (isInParty) {
            Player topDealer = partyManager.getTopDealerPlayer(event.getEntity());
            if (topDealer != null) {
                receiver = topDealer;
            }
        }

        if (receiver.isOnline()) {
            // 인벤토리에 넣고 남은 아이템은 땅에 드롭
            receiver.getInventory().addItem(drops.toArray(new ItemStack[0]))
                    .forEach((slot, item) -> receiver.getWorld().dropItemNaturally(receiver.getLocation(), item));
        } else {
            // 오프라인이면 킬러 위치에 드롭
            drops.forEach(item -> killer.getWorld().dropItemNaturally(killer.getLocation(), item));
        }
    }
}
