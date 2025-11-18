package com.mahirung.rpgcore.managers;

import com.mahirung.rpgcore.RPGCore;
import com.mahirung.rpgcore.util.ChatUtil;
import com.mahirung.rpgcore.util.ItemUtil;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/**
 * 룬 시스템 매니저
 * - runes.yml 기반 룬 데이터 로드
 * - 장비에 룬 장착/해제
 * - 룬 스탯 적용/제거
 */
public class RuneManager {

    private final RPGCore plugin;
    private final ConfigManager configManager;

    private final Map<String, RuneData> runeCache = new HashMap<>();
    private String runeRemovalItemId = null;

    public static final String RUNE_LIST_NBT_KEY = "rpgcore_runes_list";
    public static final String RUNE_STATS_NBT_KEY = "rpgcore_rune_stats";
    public static final String ITEMSADDER_NBT_KEY = "itemsadder_id";

    public RuneManager(RPGCore plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        loadRuneConfig();
    }

    /** 룬 설정 로드 */
    public void loadRuneConfig() {
        runeCache.clear();
        FileConfiguration config = configManager.getRunesConfig();

        ConfigurationSection runesSection = config.getConfigurationSection("runes");
        if (runesSection != null) {
            for (String runeIdKey : runesSection.getKeys(false)) {
                ConfigurationSection runeConfig = runesSection.getConfigurationSection(runeIdKey);

                String itemsAdderId = runeConfig.getString("itemsadder-id");
                if (itemsAdderId == null || itemsAdderId.isEmpty()) {
                    plugin.getLogger().warning("runes.yml의 " + runeIdKey + "에 'itemsadder-id'가 없습니다.");
                    continue;
                }

                List<String> applicableTo = runeConfig.getStringList("applicable-to");
                Map<String, Double> stats = new HashMap<>();
                ConfigurationSection statsSection = runeConfig.getConfigurationSection("stats");
                if (statsSection != null) {
                    for (String statKey : statsSection.getKeys(false)) {
                        stats.put(statKey, statsSection.getDouble(statKey));
                    }
                }

                RuneData runeData = new RuneData(itemsAdderId, applicableTo, stats);
                runeCache.put(itemsAdderId, runeData);
            }
        }

        runeRemovalItemId = config.getString("removal-item.itemsadder-id");
        if (runeRemovalItemId == null) {
            plugin.getLogger().warning("runes.yml에 'removal-item.itemsadder-id'가 설정되지 않았습니다.");
        }

        plugin.getLogger().info(runeCache.size() + "개의 룬과 1개의 룬 해제 아이템을 로드했습니다.");
    }

    /** 아이템이 룬인지 확인 */
    public boolean isRune(ItemStack item) {
        return getRuneData(item) != null;
    }

    /** 룬 데이터 가져오기 */
    public RuneData getRuneData(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return null;
        String itemsAdderId = ItemUtil.getNBTString(item, ITEMSADDER_NBT_KEY);
        return (itemsAdderId != null) ? runeCache.get(itemsAdderId) : null;
    }

    /** 룬 해제 아이템인지 확인 */
    public boolean isRuneRemovalItem(ItemStack item) {
        if (item == null || runeRemovalItemId == null) return false;
        String itemsAdderId = ItemUtil.getNBTString(item, ITEMSADDER_NBT_KEY);
        return runeRemovalItemId.equals(itemsAdderId);
    }

    /** 룬 장착 */
    public ItemStack applyRune(Player player, ItemStack runeItem, ItemStack equipmentItem) {
        RuneData runeData = getRuneData(runeItem);
        if (runeData == null) return null;

        if (equipmentItem == null || equipmentItem.getType().isAir()) {
            player.sendMessage(ChatUtil.format("&c[룬] &f룬을 장착할 장비가 아닙니다."));
            return null;
        }

        boolean canApply = runeData.getApplicableTo().stream()
                .anyMatch(materialName -> equipmentItem.getType().name().contains(materialName.toUpperCase()));
        if (!canApply) {
            player.sendMessage(ChatUtil.format("&c[룬] &f이 룬은 해당 장비에 장착할 수 없습니다."));
            return null;
        }

        List<String> currentRunes = ItemUtil.getNBTStringList(equipmentItem, RUNE_LIST_NBT_KEY);
        if (currentRunes.size() >= 2) {
            player.sendMessage(ChatUtil.format("&c[룬] &f룬 슬롯이 꽉 찼습니다. (최대 2개)"));
            return null;
        }

        currentRunes.add(runeData.getId());
        equipmentItem = ItemUtil.setNBTStringList(equipmentItem, RUNE_LIST_NBT_KEY, currentRunes);

        // TODO: 스탯 적용 로직 (ItemStatHandler 같은 유틸로 통합 가능)

        player.sendMessage(ChatUtil.format("&a[룬] &f{0} 룬을 장착했습니다!", runeData.getId()));
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 1.5f);

        return equipmentItem;
    }

    /** 룬 해제 */
    public ItemStack removeRune(Player player, ItemStack removalItem, ItemStack equipmentItem) {
        if (!isRuneRemovalItem(removalItem)) return null;

        List<String> currentRunes = ItemUtil.getNBTStringList(equipmentItem, RUNE_LIST_NBT_KEY);
        if (currentRunes.isEmpty()) {
            player.sendMessage(ChatUtil.format("&c[룬] &f장비에 장착된 룬이 없습니다."));
            return null;
        }

        String removedRuneId = currentRunes.remove(currentRunes.size() - 1);
        equipmentItem = ItemUtil.setNBTStringList(equipmentItem, RUNE_LIST_NBT_KEY, currentRunes);

        // TODO: 스탯 제거 로직 (ItemStatHandler 같은 유틸로 통합 가능)

        player.sendMessage(ChatUtil.format("&e[룬] &f{0} 룬을 제거했습니다.", removedRuneId));
        player.playSound(player.getLocation(), Sound.BLOCK_GRINDSTONE_USE, 1.0f, 1.0f);

        return equipmentItem;
    }

    /** 룬 데이터 구조 */
    private static class RuneData {
        private final String id;
        private final List<String> applicableTo;
        private final Map<String, Double> stats;

        public RuneData(String id, List<String> applicableTo, Map<String, Double> stats) {
            this.id = id;
            this.applicableTo = applicableTo;
            this.stats = stats;
        }

        public String getId() { return id; }
        public List<String> getApplicableTo() { return applicableTo; }
        public Map<String, Double> getStatsMap() { return stats; }
    }
}
