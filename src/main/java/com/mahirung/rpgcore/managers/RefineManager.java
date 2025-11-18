package com.mahirung.rpgcore.managers;

import com.mahirung.rpgcore.RPGCore;
import com.mahirung.rpgcore.util.ChatUtil;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/**
 * 재련 시스템 관리 클래스
 */
public class RefineManager {

    private final RPGCore plugin;

    // 레시피 캐시
    private final Map<String, RefineRecipe> recipeCache = new HashMap<>();

    // 플레이어별 진행 중인 작업
    private final Map<UUID, List<ActiveRefineTask>> activeTasks = new HashMap<>();

    public RefineManager(RPGCore plugin) {
        this.plugin = plugin;
        loadRecipes();
    }

    private void loadRecipes() {
        // TODO: config에서 레시피 불러오기
        plugin.getLogger().info("[RefineManager] 레시피를 로드했습니다.");
    }

    public Set<String> getAllRecipeIds() {
        return recipeCache.keySet();
    }

    public void openRefineGUI(Player player) {
        // TODO: GUI 열기 구현
        player.sendMessage(ChatUtil.format("&a[재련] &f재련 GUI를 열었습니다."));
    }

    public void startRefineTask(Player player, String recipeId) {
        RefineRecipe recipe = recipeCache.get(recipeId);
        if (recipe == null) {
            player.sendMessage(ChatUtil.format("&c[재련] &f존재하지 않는 레시피입니다."));
            return;
        }

        ActiveRefineTask task = new ActiveRefineTask(recipeId, System.currentTimeMillis());
        activeTasks.computeIfAbsent(player.getUniqueId(), k -> new ArrayList<>()).add(task);

        player.sendMessage(ChatUtil.format("&a[재련] &f작업을 시작했습니다: {0}", recipeId));
    }

    public void handleClaimItem(Player player) {
        List<ActiveRefineTask> tasks = activeTasks.get(player.getUniqueId());
        if (tasks == null || tasks.isEmpty()) {
            player.sendMessage(ChatUtil.format("&c[재련] &f진행 중인 작업이 없습니다."));
            return;
        }

        ActiveRefineTask completedTask = tasks.stream()
                .filter(ActiveRefineTask::isComplete)
                .findFirst()
                .orElse(null);

        if (completedTask == null) {
            player.sendMessage(ChatUtil.format("&c[재련] &f아직 수령할 아이템이 없습니다."));
            return;
        }

        RefineRecipe recipe = recipeCache.get(completedTask.getRecipeId());
        if (recipe == null) {
            player.sendMessage(ChatUtil.format("&c[재련] &f레시피 정보를 찾을 수 없습니다."));
            return;
        }

        ItemStack result = recipe.getResult().clone();
        if (player.getInventory().firstEmpty() == -1) {
            player.getWorld().dropItemNaturally(player.getLocation(), result);
        } else {
            player.getInventory().addItem(result);
        }

        tasks.remove(completedTask);
        player.sendMessage(ChatUtil.format("&a[재련] &f아이템을 수령했습니다."));
    }

    /**
     * 내부 클래스: 재련 레시피
     */
    public static class RefineRecipe {
        private final String id;
        private final ItemStack result;

        public RefineRecipe(String id, ItemStack result) {
            this.id = id;
            this.result = result;
        }

        public String getId() { return id; }
        public ItemStack getResult() { return result; }
    }

    /**
     * 내부 클래스: 진행 중인 재련 작업
     */
    public static class ActiveRefineTask {
        private final String recipeId;
        private final long startTime;

        public ActiveRefineTask(String recipeId, long startTime) {
            this.recipeId = recipeId;
            this.startTime = startTime;
        }

        public String getRecipeId() { return recipeId; }

        public boolean isComplete() {
            // 예시: 10초 후 완료
            return System.currentTimeMillis() - startTime > 10_000;
        }
    }
}
