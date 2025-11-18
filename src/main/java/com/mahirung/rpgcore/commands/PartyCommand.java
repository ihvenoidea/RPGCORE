package com.mahirung.rpgcore.commands;

import com.mahirung.rpgcore.RPGCore;
import com.mahirung.rpgcore.managers.PartyManager;
import com.mahirung.rpgcore.util.ChatUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class PartyCommand implements CommandExecutor, TabCompleter {

    private final RPGCore plugin;
    private final PartyManager partyManager;

    private static final List<String> SUB_COMMANDS = Arrays.asList(
            "create", "invite", "accept", "deny", "leave", "kick", "promote", "leader", "chat", "list", "help"
    );

    public PartyCommand(RPGCore plugin) {
        this.plugin = plugin;
        this.partyManager = plugin.getPartyManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatUtil.format("&c[RPGCore] &f이 명령어는 플레이어만 사용할 수 있습니다."));
            return true;
        }

        Player player = (Player) sender;

        // 권한 체크
        if (!player.hasPermission("rpgcore.party.use")) {
            player.sendMessage(ChatUtil.format("&c[RPGCore] &f이 명령어를 실행할 권한이 없습니다."));
            return true;
        }

        // --- /p <메시지...> 단축 명령어 처리 ---
        if (label.equalsIgnoreCase("p") && args.length > 0) {
            if (!SUB_COMMANDS.contains(args[0].toLowerCase())) {
                String message = String.join(" ", args);
                partyManager.sendPartyMessage(player, message);
                return true;
            }
        }

        if (args.length == 0) {
            sendHelpMessage(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        try {
            switch (subCommand) {
                case "create":
                    handleCreateSoloParty(player);
                    break;
                case "invite":
                    handleInvite(player, args);
                    break;
                case "accept":
                    handleAccept(player, args);
                    break;
                case "deny":
                case "decline":
                    handleDeny(player, args);
                    break;
                case "leave":
                    partyManager.leaveParty(player);
                    break;
                case "kick":
                    handleKick(player, args);
                    break;
                case "promote":
                case "leader":
                    handlePromote(player, args);
                    break;
                case "chat":
                case "c":
                    handleChat(player, args);
                    break;
                case "list":
                case "info":
                    partyManager.showPartyInfo(player);
                    break;
                case "help":
                default:
                    sendHelpMessage(player);
                    break;
            }
        } catch (Exception e) {
            player.sendMessage(ChatUtil.format("&c[Party] &f명령어 실행 중 오류가 발생했습니다. 관리자에게 문의하세요."));
            plugin.getLogger().severe("[PartyCommand] 오류 발생: " + e.getMessage());
            e.printStackTrace();
        }

        return true;
    }

    // --- 핸들러 메서드 ---

    private void handleCreateSoloParty(Player player) {
        partyManager.createSoloParty(player);
    }
    
    private void handleInvite(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatUtil.format("&c[Party] &f사용법: /party invite <플레이어>"));
            return;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null || !target.isOnline()) {
            player.sendMessage(ChatUtil.format("&c[Party] &f'{0}' 플레이어를 찾을 수 없거나 오프라인입니다.", args[1]));
            return;
        }
        partyManager.invitePlayer(player, target);
    }

    private void handleAccept(Player player, String[] args) {
        if (args.length < 2) {
            partyManager.acceptInvite(player, null);
        } else {
            Player inviter = Bukkit.getPlayer(args[1]);
            if (inviter == null) {
                player.sendMessage(ChatUtil.format("&c[Party] &f'{0}' 플레이어를 찾을 수 없습니다.", args[1]));
                return;
            }
            partyManager.acceptInvite(player, inviter.getName());
        }
    }

    private void handleDeny(Player player, String[] args) {
        if (args.length < 2) {
            partyManager.denyInvite(player, null);
        } else {
            Player inviter = Bukkit.getPlayer(args[1]);
            if (inviter == null) {
                player.sendMessage(ChatUtil.format("&c[Party] &f'{0}' 플레이어를 찾을 수 없습니다.", args[1]));
                return;
            }
            partyManager.denyInvite(player, inviter.getName());
        }
    }

    private void handleKick(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatUtil.format("&c[Party] &f사용법: /party kick <플레이어>"));
            return;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            partyManager.kickPlayer(player, args[1]);
        } else {
            partyManager.kickPlayer(player, target.getName());
        }
    }

    private void handlePromote(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatUtil.format("&c[Party] &f사용법: /party promote <플레이어>"));
            return;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null || !target.isOnline()) {
            player.sendMessage(ChatUtil.format("&c[Party] &f'{0}' 플레이어는 온라인 상태가 아닙니다.", args[1]));
            return;
        }
        partyManager.promotePlayer(player, target);
    }

    private void handleChat(Player player, String[] args) {
        if (args.length == 1) {
            partyManager.togglePartyChat(player);
        } else {
            String message = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
            partyManager.sendPartyMessage(player, message);
        }
    }

    private void sendHelpMessage(Player player) {
        player.sendMessage(ChatUtil.format("&a===== [ RPGCore 파티 도움말 ] ====="));
        player.sendMessage(ChatUtil.format("&e/party create &7- 솔로 던전 입장을 위한 1인 파티를 생성합니다."));
        player.sendMessage(ChatUtil.format("&e/party invite <플레이어> &7- 플레이어를 파티에 초대합니다."));
        player.sendMessage(ChatUtil.format("&e/party accept [플레이어] &7- 파티 초대를 수락합니다."));
        player.sendMessage(ChatUtil.format("&e/party deny [플레이어] &7- 파티 초대를 거절합니다."));
        player.sendMessage(ChatUtil.format("&e/party leave &7- 현재 파티에서 탈퇴합니다."));
        player.sendMessage(ChatUtil.format("&e/party kick <플레이어> &7- 파티원을 추방합니다. (파티장)"));
        player.sendMessage(ChatUtil.format("&e/party promote <플레이어> &7- 파티원을 파티장으로 임명합니다. (파티장)"));
        player.sendMessage(ChatUtil.format("&e/party chat [메시지] &7- 파티 채팅 모드를 켜거나 메시지를 보냅니다."));
        player.sendMessage(ChatUtil.format("&e/p <메시지> &7- 파티 채팅을 보냅니다. (단축키)"));
        player.sendMessage(ChatUtil.format("&e/party list &7- 현재 파티원 목록을 봅니다."));
        player.sendMessage(ChatUtil.format("&a================================="));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (!(sender instanceof Player)) {
            return completions;
        }
        Player player = (Player) sender;

        if (!player.hasPermission("rpgcore.party.use")) {
            return completions;
        }

        if (args.length == 1) {
            StringUtil.copyPartialMatches(args[0], SUB_COMMANDS, completions);
            Collections.sort(completions);
            return completions;
        }

        if (args.length == 2) {
            String subCommand = args[0].toLowerCase();
            switch (subCommand) {
                case "invite":
                    return Bukkit.getOnlinePlayers().stream()
                            .map(Player::getName)
                            .filter(name -> !name.equals(player.getName()) && name.toLowerCase().startsWith(args[1].toLowerCase()))
                            .collect(Collectors.toList());
                case "kick":
                case "promote":
                case "leader":
                    List<String> memberNames = partyManager.getPartyMemberNames(player);
                    memberNames.remove(player.getName());
                    StringUtil.copyPartialMatches(args[1], memberNames, completions);
                    return completions;

                case "accept":
                case "deny":
                    List<String> inviterNames = partyManager.getPendingInviteNames(player);
                    StringUtil.copyPartialMatches(args[1], inviterNames, completions);
                    return completions;
            }
        }
        return completions;
    }
}