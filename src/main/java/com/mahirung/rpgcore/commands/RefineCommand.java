package com.mahirung.rpgcore.commands;

import com.mahirung.rpgcore.RPGCore;
import com.mahirung.rpgcore.managers.RefineManager;
import com.mahirung.rpgcore.util.ChatUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 재련 GUI를 여는 명령어 (/refine)
 */
public class RefineCommand implements CommandExecutor, TabCompleter {

    private final RefineManager refineManager;
    private final RPGCore plugin;

    public RefineCommand(RPGCore plugin) {
        this.plugin = plugin;
        this.refineManager = plugin.getRefineManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatUtil.format("&c[RPGCore] &f이 명령어는 플레이어만 사용할 수 있습니다."));
            return true;
        }

        Player player = (Player) sender;

        // 권한 체크
        if (!player.hasPermission("rpgcore.refine.use")) {
            player.sendMessage(ChatUtil.format("&c[RPGCore] &f이 명령어를 실행할 권한이 없습니다."));
            return true;
        }

        try {
            refineManager.openRefineGUI(player);
        } catch (Exception e) {
            player.sendMessage(ChatUtil.format("&c[RPGCore] &f재련 GUI를 여는 중 오류가 발생했습니다."));
            plugin.getLogger().severe("[RefineCommand] GUI 오류: " + e.getMessage());
            e.printStackTrace();
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (!(sender instanceof Player)) {
            return completions;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("rpgcore.refine.use")) {
            return completions;
        }

        if (args.length == 1) {
            // 재련 레시피 ID 자동완성
            List<String> recipeIds = new ArrayList<>(refineManager.getAllRecipeIds()); // Set → List 변환
            StringUtil.copyPartialMatches(args[0], recipeIds, completions);
            Collections.sort(completions);
        }

        return completions;
    }
}
