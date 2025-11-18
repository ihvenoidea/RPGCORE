package com.mahirung.rpgcore;

import com.mahirung.rpgcore.commands.*;
import com.mahirung.rpgcore.listeners.*;
import com.mahirung.rpgcore.managers.*;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public class RPGCore extends JavaPlugin {

    private static RPGCore instance;

    private ConfigManager configManager;
    private PlayerDataManager playerDataManager;
    private ClassManager classManager;
    private RefineManager refineManager;
    private EnhanceManager enhanceManager;
    private RuneManager runeManager;
    private PartyManager partyManager;
    private DungeonManager dungeonManager;
    private DamageSkinManager damageSkinManager;
    private SkillManager skillManager; // 추가

    @Override
    public void onEnable() {
        instance = this;

        if (!checkDependencies()) {
            getLogger().severe("필수 의존성 플러그인을 찾을 수 없습니다! 플러그인을 비활성화합니다.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        loadManagers();
        registerListeners();
        registerCommands();

        getLogger().info("RPGCore 플러그인이 성공적으로 활성화되었습니다.");
    }

    @Override
    public void onDisable() {
        if (playerDataManager != null) {
            playerDataManager.saveAllPlayerData();
            playerDataManager.shutdown();
        }
        getLogger().info("RPGCore 플러그인이 비활성화되었습니다.");
    }

    public void reloadPlugin() {
        if (configManager != null) {
            configManager.reloadConfigs();
        }
        if (dungeonManager != null) {
            dungeonManager.loadDungeons();
            dungeonManager.loadLobbyLocation();
        }
        if (damageSkinManager != null) {
            damageSkinManager.loadDamageSkins();
        }
        if (refineManager != null) {
            refineManager.loadRefiningRecipes();
        }
        if (enhanceManager != null) {
            enhanceManager.loadEnhanceConfig();
        }
        if (runeManager != null) {
            runeManager.loadRuneConfig();
        }
        if (classManager != null) {
            classManager.loadClasses();
        }
        getLogger().info("RPGCore 설정을 리로드했습니다.");
    }

    private boolean checkDependencies() {
        String[] dependencies = {"MythicMobs", "Vault", "ItemsAdder", "FastAsyncWorldEdit"};
        boolean allFound = true;
        for (String dep : dependencies) {
            Plugin plugin = getServer().getPluginManager().getPlugin(dep);
            if (plugin == null || !plugin.isEnabled()) {
                getLogger().severe("필수 의존성 " + dep + "이(가) 없습니다.");
                allFound = false;
            }
        }
        return allFound;
    }

    private void loadManagers() {
        configManager = new ConfigManager(this);
        playerDataManager = new PlayerDataManager(this);
        classManager = new ClassManager(this);
        refineManager = new RefineManager(this);
        enhanceManager = new EnhanceManager(this);
        runeManager = new RuneManager(this);
        partyManager = new PartyManager(this);
        dungeonManager = new DungeonManager(this);
        damageSkinManager = new DamageSkinManager(this);
        skillManager = new SkillManager(this); // 초기화
    }

    private void registerListeners() {
        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(new PlayerJoinListener(this), this);
        pm.registerEvents(new PlayerQuitListener(this), this);
        pm.registerEvents(new InventoryCloseListener(this), this);
        pm.registerEvents(new InventoryClickListener(this), this);
        pm.registerEvents(new InventoryDragListener(this), this);
        pm.registerEvents(new EntityDamageListener(this), this);
        pm.registerEvents(new EntityDeathListener(this), this);
        pm.registerEvents(new PlayerMoveListener(this), this);
        pm.registerEvents(new PlayerDropItemListener(this), this);
        pm.registerEvents(new PlayerInteractListener(this), this);
        pm.registerEvents(new NPCInteractListener(this), this);
    }

    private void registerCommands() {
        getCommand("class").setExecutor(new ClassCommand(this));
        getCommand("refine").setExecutor(new RefineCommand(this));
        getCommand("enhance").setExecutor(new EnhanceCommand(this));
        getCommand("party").setExecutor(new PartyCommand(this));
        getCommand("dungeon").setExecutor(new DungeonCommand(this));
        getCommand("damageskin").setExecutor(new DamageSkinCommand(this));
        getCommand("rpgcore").setExecutor(new RPGCoreCommand(this));
    }

    // --- Getters ---
    public static RPGCore getInstance() { return instance; }
    public ConfigManager getConfigManager() { return configManager; }
    public PlayerDataManager getPlayerDataManager() { return playerDataManager; }
    public ClassManager getClassManager() { return classManager; }
    public RefineManager getRefineManager() { return refineManager; }
    public EnhanceManager getEnhanceManager() { return enhanceManager; }
    public RuneManager getRuneManager() { return runeManager; }
    public PartyManager getPartyManager() { return partyManager; }
    public DungeonManager getDungeonManager() { return dungeonManager; }
    public DamageSkinManager getDamageSkinManager() { return damageSkinManager; }
    public SkillManager getSkillManager() { return skillManager; } // 추가
}
