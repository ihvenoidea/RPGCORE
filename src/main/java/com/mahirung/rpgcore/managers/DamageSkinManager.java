package com.mahirung.rpgcore.managers;

import com.mahirung.rpgcore.RPGCore;
import com.mahirung.rpgcore.data.PlayerData;
import com.mahirung.rpgcore.util.ChatUtil;
import com.mahirung.rpgcore.util.ItemUtil;
import dev.lone.itemsadder.api.CustomStack;
import dev.lone.itemsadder.api.ItemsAdder;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

/**
 * 데미지 스킨 매니저
 * - 플레이어별 데미지 스킨 선택
 * - ItemsAdder 커스텀 아이템을 사용해 데미지 숫자 표시
 */
public class DamageSkinManager {

    private final RPGCore plugin;
    private final ConfigManager configManager;
    private final PlayerDataManager playerDataManager;

    private final Map<String, DamageSkinData> skinCache = new HashMap<>();
    private final Random random = new Random();

    // config.yml 값
    private final double fontOffsetX;
    private final double fontOffsetY;
    private final int fontDisplayDurationTicks;

    public static final String PLAYER_SKIN_NBT_KEY = "rpgcore_damage_skin";

    public DamageSkinManager(RPGCore plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.playerDataManager = plugin.getPlayerDataManager();

        fontOffsetX = configManager.getMainConfig().getDouble("damage-skins.font-offset-x", 0.25);
        fontOffsetY = configManager.getMainConfig().getDouble("damage-skins.font-offset-y", 0.5);
        fontDisplayDurationTicks = configManager.getMainConfig().getInt("damage-skins.display-duration-ticks", 20);

        loadSkins();
    }

    /** 외부에서 호출 가능한 스킨 로드 */
    public void loadDamageSkins() {
        loadSkins();
    }

    /** 데미지 스킨 로드 */
    private void loadSkins() {
        skinCache.clear();
        // TODO: config에서 스킨 정보 로드
        // 기본 스킨 등록
        skinCache.put("default", new DamageSkinData("default", "기본 스킨", null, null, "damage_font_"));
    }

    /** 데미지 스킨 활성화 여부 */
    public boolean isDamageSkinEnabled() {
        return configManager.getMainConfig().getBoolean("damage-skins.enable", true);
    }

    /** 데미지 표시 (기존 시그니처) */
    public void showDamage(Player attacker, LivingEntity victim, double damage) {
        showDamage(victim, damage, false, attacker);
    }

    /** 데미지 표시 (확장 시그니처) */
    public void showDamage(LivingEntity victim, double damage, boolean critical, Player attacker) {
        if (!isDamageSkinEnabled()) return;

        PlayerData data = playerDataManager.getPlayerData(attacker.getUniqueId());
        String selectedSkinId = "default";

        if (data != null) {
            String savedSkinId = data.getCustomNBT().getString(PLAYER_SKIN_NBT_KEY);
            if (savedSkinId != null && !savedSkinId.isEmpty() && skinCache.containsKey(savedSkinId)) {
                selectedSkinId = savedSkinId;
            }
        }

        DamageSkinData skin = skinCache.getOrDefault(selectedSkinId, skinCache.get("default"));
        String fontPrefix = critical ? skin.getCriticalFormat() : skin.getNormalFormat();

        String damageString = String.valueOf((int) Math.round(damage));
        Location baseLoc = victim.getEyeLocation().add(randomOffset(), fontOffsetY, randomOffset());

        List<ArmorStand> spawnedArmorStands = new ArrayList<>();
        double currentX = baseLoc.getX() - (damageString.length() * fontOffsetX / 2);

        for (int i = 0; i < damageString.length(); i++) {
            char digitChar = damageString.charAt(i);
            String itemIdentifier = fontPrefix + digitChar;

            CustomStack cs = ItemsAdder.getCustomStack(itemIdentifier);
            ItemStack fontItem = (cs != null) ? cs.getItemStack() : null;
            if (fontItem == null) {
                plugin.getLogger().warning("ItemsAdder 커스텀 아이템 " + itemIdentifier + "를 찾을 수 없습니다.");
                continue;
            }

            Location spawnLoc = baseLoc.clone();
            spawnLoc.setX(currentX + (i * fontOffsetX));

            ArmorStand armorStand = victim.getWorld().spawn(spawnLoc, ArmorStand.class, as -> {
                as.setMarker(true);
                as.setVisible(false);
                as.setGravity(false);
                as.setSmall(true);
                as.setPersistent(false);
                as.setItemInHand(fontItem);
                as.setCustomNameVisible(false);
                as.setInvulnerable(true);
                as.setMetadata("rpgcore_damage_font", new FixedMetadataValue(plugin, true));
            });
            spawnedArmorStands.add(armorStand);
        }

        // 일정 시간 후 제거
        new BukkitRunnable() {
            @Override
            public void run() {
                spawnedArmorStands.forEach(as -> {
                    if (!as.isDead()) as.remove();
                });
                spawnedArmorStands.clear();
            }
        }.runTaskLater(plugin, fontDisplayDurationTicks);
    }

    private double randomOffset() {
        return (random.nextDouble() - 0.5) * 0.5;
    }

    /** GUI 클릭 처리 */
    public void handleGUIClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null) return;

        String skinId = ItemUtil.getNBTString(clicked, "rpgcore_skin_id");
        if (skinId == null || !skinCache.containsKey(skinId)) return;

        PlayerData data = playerDataManager.getPlayerData(player.getUniqueId());
        if (data == null) return;

        data.getCustomNBT().set(PLAYER_SKIN_NBT_KEY, skinId);
        player.sendMessage(ChatUtil.format("&a[스킨] &f스킨 ''{0}''을 적용했습니다.", skinId));
        player.closeInventory();
    }

    /** 전체 스킨 데이터 반환 */
    public Map<String, DamageSkinData> getAllSkins() {
        return Collections.unmodifiableMap(skinCache);
    }

    /** 데미지 스킨 데이터 */
    public static class DamageSkinData {
        private final String id;
        private final String displayName;
        private final ItemStack guiIcon;
        private final String ticketId;
        private final String itemPrefix;

        public DamageSkinData(String id, String displayName, ItemStack guiIcon, String ticketId, String itemPrefix) {
            this.id = id;
            this.displayName = displayName;
            this.guiIcon = guiIcon;
            this.ticketId = ticketId;
            this.itemPrefix = itemPrefix;
        }

        public String getId() { return id; }
        public String getDisplayName() { return displayName; }
        public ItemStack getGuiIcon() { return guiIcon; }
        public String getTicketId() { return ticketId; }
        public String getItemPrefix() { return itemPrefix; }

        // GUI에서 요구하는 포맷 메서드
        public String getNormalFormat() {
            return itemPrefix + "n_";
        }

        public String getCriticalFormat() {
            return itemPrefix + "c_";
        }
    }
}
