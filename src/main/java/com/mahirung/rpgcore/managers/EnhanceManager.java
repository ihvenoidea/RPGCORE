package com.mahirung.rpgcore.managers;

import com.mahirung.rpgcore.RPGCore;
import com.mahirung.rpgcore.gui.EnhanceGUI;
import com.mahirung.rpgcore.util.ChatUtil;
import com.mahirung.rpgcore.util.ItemUtil;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/**
 * 장비 강화 시스템 매니저
 * - enhancing.yml 기반 강화 확률/비용 관리
 * - 장비 강화 및 실패 처리
 * - GUI 연동
 */
public class EnhanceManager {

    private final RPGCore plugin;
    private final ConfigManager configManager;

    private final Map<Integer, EnhanceData> enhanceCache = new HashMap<>();

    public EnhanceManager(RPGCore plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        loadEnhanceConfig();
    }

    /** 강화 설정 로드 */
    public void loadEnhanceConfig() {
        enhanceCache.clear();
        FileConfiguration config = configManager.getEnhancingConfig();
        ConfigurationSection section = config.getConfigurationSection("levels");
        if (section == null) {
            plugin.getLogger().warning("enhancing.yml에 'levels' 섹션이 없습니다.");
            return;
        }

        for (String key : section.getKeys(false)) {
            try {
                int level = Integer.parseInt(key);
                double successRate = section.getDouble(key + ".success-rate", 0.5);
                double destroyRate = section.getDouble(key + ".destroy-rate", 0.0);
                int cost = section.getInt(key + ".cost", 100);

                enhanceCache.put(level, new EnhanceData(level, successRate, destroyRate, cost));
            } catch (NumberFormatException e) {
                plugin.getLogger().warning("enhancing.yml의 잘못된 레벨 키: " + key);
            }
        }
        plugin.getLogger().info(enhanceCache.size() + "개의 강화 레벨 데이터를 로드했습니다.");
    }

    /** GUI 열기 */
    public void openEnhanceGUI(Player player) {
        new EnhanceGUI(plugin).open(player);
    }

    /** GUI 클릭 처리 */
    public void handleGUIClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        int slot = event.getSlot();

        // GUI 제목 확인
        if (!event.getView().getTitle().equals(EnhanceGUI.GUI_TITLE)) return;

        event.setCancelled(true);

        // 강화 버튼 클릭
        if (slot == EnhanceGUI.START_BUTTON_SLOT) {
            ItemStack equipment = event.getInventory().getItem(EnhanceGUI.EQUIPMENT_SLOT);
            if (equipment == null) {
                player.sendMessage(ChatUtil.format("&c[강화] &f강화할 장비를 올려주세요."));
                return;
            }
            ItemStack result = attemptEnhance(player, equipment);
            event.getInventory().setItem(EnhanceGUI.EQUIPMENT_SLOT, result);
        }

        // 전승 버튼 클릭 (추후 구현)
        if (slot == EnhanceGUI.SUCCESSION_BUTTON_SLOT) {
            player.sendMessage(ChatUtil.format("&e[전승] &f능력치 전승 기능은 아직 구현되지 않았습니다."));
        }
    }

    /** 장비 강화 시도 */
    public ItemStack attemptEnhance(Player player, ItemStack item) {
        if (item == null) {
            player.sendMessage(ChatUtil.format("&c[강화] &f강화할 아이템이 없습니다."));
            return null;
        }

        int currentLevel = ItemUtil.getNBTInt(item, "enhance_level");
        EnhanceData data = enhanceCache.get(currentLevel + 1);
        if (data == null) {
            player.sendMessage(ChatUtil.format("&c[강화] &f더 이상 강화할 수 없습니다."));
            return item;
        }

        // TODO: 비용 차감 로직 (예: 골드, 재화 등)
        Random random = new Random();
        double roll = random.nextDouble();

        if (roll <= data.getSuccessRate()) {
            item = ItemUtil.setNBTInt(item, "enhance_level", currentLevel + 1);
            player.sendMessage(ChatUtil.format("&a[강화 성공] &f아이템이 +{0} 레벨로 강화되었습니다!", currentLevel + 1));
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
        } else if (roll <= data.getSuccessRate() + data.getDestroyRate()) {
            player.sendMessage(ChatUtil.format("&c[강화 실패] &f아이템이 파괴되었습니다."));
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 0.8f);
            item = null; // 아이템 파괴
        } else {
            player.sendMessage(ChatUtil.format("&e[강화 실패] &f아이템 강화에 실패했습니다."));
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_BREAK, 1f, 1f);
        }

        return item;
    }

    /** 강화 단계별 데이터 */
    private static class EnhanceData {
        private final int level;
        private final double successRate;
        private final double destroyRate;
        private final int cost;

        public EnhanceData(int level, double successRate, double destroyRate, int cost) {
            this.level = level;
            this.successRate = successRate;
            this.destroyRate = destroyRate;
            this.cost = cost;
        }

        public int getLevel() { return level; }
        public double getSuccessRate() { return successRate; }
        public double getDestroyRate() { return destroyRate; }
        public int getCost() { return cost; }
    }
}
