package com.mahirung.rpgcore.listeners;

import com.mahirung.rpgcore.RPGCore;
import com.mahirung.rpgcore.data.PlayerData;
import com.mahirung.rpgcore.managers.DamageSkinManager;
import com.mahirung.rpgcore.managers.PartyManager;
import com.mahirung.rpgcore.managers.PlayerDataManager;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/**
 * 엔티티 데미지 이벤트 리스너
 * - 플레이어 공격 시 커스텀 스탯 기반 데미지 계산
 * - 치명타, 방어력 반영
 * - 데미지 스킨 표시 및 파티 기여도 기록
 */
public class EntityDamageListener implements Listener {

    private final PlayerDataManager playerDataManager;
    private final PartyManager partyManager;
    private final DamageSkinManager damageSkinManager;

    // 방어력 공식 상수 (config.yml로 추출 가능)
    private static final double DEFENSE_CONSTANT = 100.0;

    public EntityDamageListener(RPGCore plugin) {
        this.playerDataManager = plugin.getPlayerDataManager();
        this.partyManager = plugin.getPartyManager();
        this.damageSkinManager = plugin.getDamageSkinManager();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        // 1. 공격자가 플레이어인지 확인
        if (!(event.getDamager() instanceof Player attacker)) {
            // TODO: MythicMobs 소환수/스킬 데미지 처리 필요 시 추가
            return;
        }

        // 2. 피해자가 LivingEntity인지 확인
        if (!(event.getEntity() instanceof LivingEntity victim)) {
            return;
        }

        // 3. 공격자 데이터 로드
        PlayerData attackerData = playerDataManager.getPlayerData(attacker.getUniqueId());
        if (attackerData == null || !attackerData.hasClass()) {
            // RPGCore 직업이 없는 경우 바닐라 데미지 유지
            return;
        }

        // 4. 피해자 데이터 로드 (PvP)
        PlayerData victimData = (victim instanceof Player)
                ? playerDataManager.getPlayerData(victim.getUniqueId())
                : null;

        // --- 5. 커스텀 데미지 계산 ---
        double baseAttack = attackerData.getAttack();
        double critChance = attackerData.getCritChance();
        double critDamage = attackerData.getCritDamage();

        double victimDefense = (victimData != null) ? victimData.getDefense() : 0.0;
        // TODO: MythicMobs 몬스터 방어력 스탯 연동 필요 시 추가

        // 방어력에 따른 감소율
        double damageReduction = victimDefense / (victimDefense + DEFENSE_CONSTANT);
        double calculatedDamage = baseAttack * (1.0 - damageReduction);

        // 치명타 여부
        boolean isCritical = Math.random() < critChance;
        double finalDamage = isCritical ? calculatedDamage * (1.0 + critDamage) : calculatedDamage;

        // 최소 데미지 보정
        finalDamage = Math.max(0.0, finalDamage);

        // 6. 최종 데미지 적용
        event.setDamage(finalDamage);

        // 7. 데미지 스킨 표시
        damageSkinManager.showDamage(victim, finalDamage, isCritical, attacker);

        // 8. 파티 데미지 기여도 기록 (PvE 전용)
        if (!(victim instanceof Player)) {
            partyManager.logDamage(attacker, victim, finalDamage);
        }
    }
}
