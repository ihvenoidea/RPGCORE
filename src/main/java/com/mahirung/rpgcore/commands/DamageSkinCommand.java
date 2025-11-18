package com.mahirung.rpgcore.commands;

import com.mahirung.rpgcore.RPGCore;
import com.mahirung.rpgcore.gui.DamageSkinGUI;
import com.mahirung.rpgcore.managers.DamageSkinManager;
import com.mahirung.rpgcore.util.ChatUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 데미지 스킨 GUI를 여는 명령어 (/damageskin)
 */
public class DamageSkinCommand implements CommandExecutor, TabCompleter {

    private final RPGCore plugin;
    private final DamageSkinManager damageSkinManager;

    public DamageSkinCommand(RPGCore plugin) {
        this.plugin = plugin;
        this.damageSkinManager = plugin.getDamageSkinManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatUtil.format("&c[RPGCore] &f이 명령어는 플레이어만 사용할 수 있습니다."));
            return true;
        }

        Player player = (Player) sender;

        // 권한 체크
        if (!player.hasPermission("rpgcore.damageskin.use")) {
            player.sendMessage(ChatUtil.format("&c[RPGCore] &f이 명령어를 실행할 권한이 없습니다."));
            return true;
        }

        try {
            DamageSkinGUI gui = new DamageSkinGUI(plugin, damageSkinManager.getAllSkins());
            gui.open(player);
        } catch (Exception e) {
            player.sendMessage(ChatUtil.format("&c[RPGCore] &f데미지 스킨 GUI를 여는 중 오류가 발생했습니다."));
            plugin.getLogger().severe("[DamageSkinCommand] GUI 오류: " + e.getMessage());
            e.printStackTrace();
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) return Collections.emptyList();
        Player player = (Player) sender;

        if (!player.hasPermission("rpgcore.damageskin.use")) return Collections.emptyList();

        if (args.length == 1) {
            // 데미지 스킨 ID 자동완성
            return new ArrayList<>(damageSkinManager.getAllSkins().keySet());
        }

        return Collections.emptyList();
    }
}
