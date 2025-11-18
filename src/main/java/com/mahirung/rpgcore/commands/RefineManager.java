package com.mahirung.rpgcore.managers;

import com.mahirung.rpgcore.RPGCore;
import com.mahirung.rpgcore.gui.RefineGUI;
import com.mahirung.rpgcore.util.ChatUtil;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * 재련 시스템 매니저
 * - refining.yml 기반 레시피 관리
 * - 재련 작업 등록/완료/수령
 * - GUI 연동
 */
public class RefineManager {

    private final RPGCore plugin;
    private final ConfigManager configManager;

    private final Map<String, RefineRecipe> recipeCache = new HashMap<>();
    private final Map<UUID, List<ActiveRefineTask>> activeTasks = new HashMap<>();
    private final File tasksFile;
    private final FileConfiguration tasksConfig;

    public RefineManager(RPGCore plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();

        this.tasksFile = new File(plugin.getDataFolder(), "refining_tasks.yml");
        if (!tasksFile.exists()) {
            try {
                tasksFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("refining_tasks.yml 생성 실패!");
            }
        }
        this.tasksConfig = YamlConfiguration.loadConfiguration(tasksFile);

        loadRefiningRecipes();
        loadActiveTasksFromFile();
    }

    /** 레시피 로드 */
    public void loadRefiningRecipes() {
        recipeCache.clear();
        FileConfiguration config = configManager.getRefiningConfig();
        ConfigurationSection recipesSection = config.getConfigurationSection("recipes");
        if (recipesSection == null) {
            plugin.getLogger().warning("refining.yml에 'recipes' 섹션이 없습니다.");
            return;
        }

        for (String recipeId : recipesSection.getKeys(false)) {
            ConfigurationSection recipeConfig = recipesSection.getConfigurationSection(recipeId);
            if (recipeConfig == null) continue;

            try {
                Material inputMat = Material.matchMaterial(recipeConfig.getString("input.material", "STONE"));
                int inputAmount = recipeConfig.getInt("input.amount", 1);

                Material catalystMat = Material.matchMaterial(recipeConfig.getString("catalyst.material", "AIR"));
                int catalystAmount = recipeConfig.getInt("catalyst.amount", 1);

                Material resultMat = Material.matchMaterial(recipeConfig.getString("result.material", "DIRT"));
                int resultAmount = recipeConfig.getInt("result.amount", 1);

                String durationKey = recipeConfig.getString("duration-key");
                long durationSeconds = config.getLong("durations." + durationKey, 60);

                RefineRecipe recipe = new RefineRecipe(
                        recipeId,
                        new ItemStack(inputMat, inputAmount),
                        new ItemStack(catalystMat, catalystAmount),
                        new ItemStack(resultMat, resultAmount),
                        durationSeconds * 1000L
                );
                recipeCache.put(recipeId, recipe);
            } catch (Exception e) {
                plugin.getLogger().warning("레시피 로드 오류: " + recipeId + " (" + e.getMessage() + ")");
            }
        }
        plugin.getLogger().info(recipeCache.size() + "개의 재련 레시피를 로드했습니다.");
    }

    /** 기존 작업 로드 */
    private void loadActiveTasksFromFile() {
        activeTasks.clear();
        ConfigurationSection tasksSection = tasksConfig.getConfigurationSection("tasks");
        if (tasksSection == null) return;

        for (String uuidString : tasksSection.getKeys(false)) {
            UUID uuid = UUID.fromString(uuidString);
            List<?> taskList = tasksSection.getList(uuidString);
            if (taskList == null) continue;

            for (Object taskObj : taskList) {
                if (taskObj instanceof Map<?, ?> taskMap) {
                    String recipeId = (String) taskMap.get("recipeId");
                    Number endNum = (Number) taskMap.get("endTime");
                    long endTime = (endNum != null) ? endNum.longValue() : 0L;
                    if (endTime > 0 && recipeCache.containsKey(recipeId)) {
                        activeTasks.computeIfAbsent(uuid, k -> new ArrayList<>())
                                .add(new ActiveRefineTask(recipeId, endTime));
                    }
                }
            }
        }
        plugin.getLogger().info(activeTasks.size() + "명의 유저로부터 작업을 로드했습니다.");
    }

    /** 작업 저장 */
    public void saveActiveTasksToFile() {
        tasksConfig.set("tasks", null);
        ConfigurationSection tasksSection = tasksConfig.createSection("tasks");

        for (Map.Entry<UUID, List<ActiveRefineTask>> entry : activeTasks.entrySet()) {
            List<Map<String, Object>> taskSaveList = new ArrayList<>();
            for (ActiveRefineTask task : entry.getValue()) {
                Map<String, Object> taskMap = new HashMap<>();
                taskMap.put("recipeId", task.getRecipeId());
                taskMap.put("endTime", task.getEndTime());
                taskSaveList.add(taskMap);
            }
            tasksSection.set(entry.getKey().toString(), taskSaveList);
        }

        try {
            tasksConfig.save(tasksFile);
        } catch (IOException e) {
            plugin.getLogger().severe("refining_tasks.yml 저장 실패!");
        }
    }

    public void shutdown() {
        saveActiveTasksToFile();
    }

    /** GUI 열기 */
    public void openRefineGUI(Player player) {
        List<ActiveRefineTask> tasks = activeTasks.getOrDefault(player.getUniqueId(), Collections.emptyList());
        RefineGUI gui = new RefineGUI(plugin, tasks);
        gui.open(player);
    }

    /** GUI 클릭 처리 */
    public void handleGUIClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        int slot = event.getSlot();

        if (slot == RefineGUI.INPUT_SLOT || slot == RefineGUI.CATALYST_SLOT) return;
        event.setCancelled(true);

        if (slot == RefineGUI.RESULT_SLOT) handleClaimItem(player);
        else if (slot == RefineGUI.START_BUTTON_SLOT) handleStartRefining(player);
    }

    /** 재련 시작 */
    private void handleStartRefining(Player player) {
        ItemStack inputItem = player.getOpenInventory().getItem(RefineGUI.INPUT_SLOT);
        ItemStack catalystItem = player.getOpenInventory().getItem(RefineGUI.CATALYST_SLOT);

        RefineRecipe recipe = findMatchingRecipe(inputItem, catalystItem);
        if (recipe == null) {
            player.sendMessage(ChatUtil.format("&c[재련] &f일치하는 레시피가 없습니다."));
            return;
        }

        if (inputItem.getAmount() < recipe.getInput().getAmount()
                || catalystItem.getAmount() < recipe.getCatalyst().getAmount()) {
            player.sendMessage(ChatUtil.format("&c[재련] &f재료 수량이 부족합니다."));
            return;
        }

        inputItem.setAmount(inputItem.getAmount() - recipe.getInput().getAmount());
        catalystItem.setAmount(catalystItem.getAmount() - recipe.getCatalyst().getAmount());

        long endTime = System.currentTimeMillis() + recipe.getDurationMillis();
        ActiveRefineTask newTask = new ActiveRefineTask(recipe.getId(), endTime);
        activeTasks.computeIfAbsent(player.getUniqueId(), k -> new ArrayList<>()).add(newTask);

        new BukkitRunnable() {
            @Override
            public void run() {
                saveActiveTasksToFile();
            }
        }.runTaskAsynchronously(plugin);

        player.sendMessage(ChatUtil.format("&a[재련] &f''{0}'' 재련을 시작합니다! (완료까지 {1}분)", recipe.getId(), recipe.getDurationMillis() / 60000));
        player.closeInventory();
    }

    /** 레시피 매칭 헬퍼 메소드 */
    private RefineRecipe findMatchingRecipe(ItemStack input, ItemStack catalyst) {
        if (input == null || catalyst == null) return null;
        for (RefineRecipe recipe : recipeCache.values()) {
            // 재료 타입만 비교 (메타데이터 비교가 필요하면 추가 로직 필요)
            if (recipe.getInput().getType() == input.getType() &&
                recipe.getCatalyst().getType() == catalyst.getType()) {
                return recipe;
            }
        }
        return null;
    }

    /** 결과 수령 */
    private void handleClaimItem(Player player) {
        List<ActiveRefineTask> tasks = activeTasks.get(player.getUniqueId());
        if (tasks == null || tasks.isEmpty()) {
            player.sendMessage(ChatUtil.format("&c[재련] &f진행 중인 작업이 없습니다."));
            return;
        }

        ActiveRefineTask completedTask = tasks.stream().filter(ActiveRefineTask::isComplete).findFirst().orElse(null);
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
        
        // 작업 목록에서 제거 및 저장
        tasks.remove(completedTask);
        new BukkitRunnable() {
            @Override
            public void run() {
                saveActiveTasksToFile();
            }
        }.runTaskAsynchronously(plugin);
        
        player.sendMessage(ChatUtil.format("&a[재련] &f아이템을 수령했습니다."));
    }

    // --- 내부 클래스 (이전 파일에서 잘렸던 부분) ---

    public static class RefineRecipe {
        private final String id;
        private final ItemStack input;
        private final ItemStack catalyst;
        private final ItemStack result;
        private final long durationMillis;

        public RefineRecipe(String id, ItemStack input, ItemStack catalyst, ItemStack result, long durationMillis) {
            this.id = id;
            this.input = input;
            this.catalyst = catalyst;
            this.result = result;
            this.durationMillis = durationMillis;
        }

        public String getId() { return id; }
        public ItemStack getInput() { return input; }
        public ItemStack getCatalyst() { return catalyst; }
        public ItemStack getResult() { return result; }
        public long getDurationMillis() { return durationMillis; }
    }

    public static class ActiveRefineTask {
        private final String recipeId;
        private final long endTime;

        public ActiveRefineTask(String recipeId, long endTime) {
            this.recipeId = recipeId;
            this.endTime = endTime;
        }

        public String getRecipeId() { return recipeId; }
        public long getEndTime() { return endTime; }
        public boolean isComplete() {
            return System.currentTimeMillis() >= endTime;
        }
    }

}