package com.mahirung.rpgcore.managers;

import com.mahirung.rpgcore.RPGCore;
import com.mahirung.rpgcore.util.ChatUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 파티 시스템 매니저
 * - 파티 생성/초대/수락/거절/탈퇴/추방/위임
 * - 파티 채팅, 경험치 분배, 데미지 로그 관리
 */
public class PartyManager {

    private final RPGCore plugin;
    private final PlayerDataManager playerDataManager;

    private final Map<UUID, Party> parties = new HashMap<>();
    private final Map<UUID, Party> playerPartyCache = new HashMap<>();
    private final Map<UUID, Map<UUID, Long>> pendingInvites = new HashMap<>();
    private final Set<UUID> partyChatToggled = new HashSet<>();
    private final Map<UUID, Map<UUID, Double>> damageLogs = new HashMap<>();

    private static final long INVITE_EXPIRATION_MS = 60 * 1000;
    private static final int MAX_PARTY_SIZE = 4;

    public PartyManager(RPGCore plugin) {
        this.plugin = plugin;
        this.playerDataManager = plugin.getPlayerDataManager();
    }

    /** 1인 파티 생성 */
    public Party createSoloParty(Player player) {
        if (isInParty(player.getUniqueId())) {
            player.sendMessage(ChatUtil.format("&c[Party] &f이미 파티에 소속되어 있습니다."));
            return null;
        }
        Party soloParty = new Party(player.getUniqueId());
        parties.put(player.getUniqueId(), soloParty);
        playerPartyCache.put(player.getUniqueId(), soloParty);
        player.sendMessage(ChatUtil.format("&a[Party] &f솔로 던전 입장을 위한 1인 파티를 생성했습니다."));
        return soloParty;
    }

    /** 파티 초대 */
    public void invitePlayer(Player inviter, Player target) {
        if (inviter.equals(target)) {
            inviter.sendMessage(ChatUtil.format("&c[Party] &f자기 자신을 초대할 수 없습니다."));
            return;
        }
        if (isInParty(target.getUniqueId())) {
            inviter.sendMessage(ChatUtil.format("&c[Party] &f''{0}''님은 이미 다른 파티에 속해있습니다.", target.getName()));
            return;
        }
        Party party = getParty(inviter.getUniqueId());
        if (party == null) party = createParty(inviter);

        if (!party.isLeader(inviter.getUniqueId())) {
            inviter.sendMessage(ChatUtil.format("&c[Party] &f파티장만 초대할 수 있습니다."));
            return;
        }
        if (party.getSize() >= MAX_PARTY_SIZE) {
            inviter.sendMessage(ChatUtil.format("&c[Party] &f파티가 꽉 찼습니다. (최대 {0}명)", MAX_PARTY_SIZE));
            return;
        }

        pendingInvites.computeIfAbsent(target.getUniqueId(), k -> new HashMap<>())
                .put(inviter.getUniqueId(), System.currentTimeMillis() + INVITE_EXPIRATION_MS);

        inviter.sendMessage(ChatUtil.format("&a[Party] &f''{0}''님을 초대했습니다.", target.getName()));
        target.sendMessage(ChatUtil.format("&a[Party] &f''{0}''님이 초대했습니다. 60초 내에 &e/party accept {0} &f또는 &c/party deny {0}", inviter.getName()));
    }

    /** 초대 수락 */
    public void acceptInvite(Player player, String inviterName) {
        Map<UUID, Long> invites = pendingInvites.get(player.getUniqueId());
        if (invites == null || invites.isEmpty()) {
            player.sendMessage(ChatUtil.format("&c[Party] &f받은 초대가 없습니다."));
            return;
        }

        UUID inviterUUID = null;
        if (inviterName != null) {
            Player inviter = Bukkit.getPlayer(inviterName);
            if (inviter != null) inviterUUID = inviter.getUniqueId();
        } else {
            inviterUUID = invites.keySet().stream().findFirst().orElse(null);
        }

        if (inviterUUID == null || !invites.containsKey(inviterUUID) || invites.get(inviterUUID) < System.currentTimeMillis()) {
            player.sendMessage(ChatUtil.format("&c[Party] &f유효하지 않거나 만료된 초대입니다."));
            if (inviterUUID != null) invites.remove(inviterUUID);
            return;
        }

        Party party = getParty(inviterUUID);
        if (party == null || !party.isLeader(inviterUUID)) {
            player.sendMessage(ChatUtil.format("&c[Party] &f파티가 해체되었거나 초대자가 파티장이 아닙니다."));
            invites.remove(inviterUUID);
            return;
        }
        if (party.getSize() >= MAX_PARTY_SIZE) {
            player.sendMessage(ChatUtil.format("&c[Party] &f파티가 꽉 찼습니다."));
            invites.clear();
            return;
        }

        party.addMember(player.getUniqueId());
        playerPartyCache.put(player.getUniqueId(), party);
        pendingInvites.remove(player.getUniqueId());
        party.broadcastMessage(ChatUtil.format("&a[Party] &f''{0}''님이 파티에 참가했습니다.", player.getName()));
    }

    /** 초대 거절 */
    public void denyInvite(Player player, String inviterName) {
        Map<UUID, Long> invites = pendingInvites.get(player.getUniqueId());
        if (invites == null || invites.isEmpty()) {
            player.sendMessage(ChatUtil.format("&c[Party] &f받은 초대가 없습니다."));
            return;
        }
        UUID inviterUUID = null;
        if (inviterName != null) {
            Player inviter = Bukkit.getPlayer(inviterName);
            if (inviter != null) inviterUUID = inviter.getUniqueId();
        }
        if (inviterUUID != null) invites.remove(inviterUUID);
        else invites.clear();

        player.sendMessage(ChatUtil.format("&e[Party] &f초대를 거절했습니다."));
    }

    /** 파티원 추방 */
    public void kickPlayer(Player leader, String targetName) {
        Party party = getParty(leader.getUniqueId());
        if (party == null || !party.isLeader(leader.getUniqueId())) {
            leader.sendMessage(ChatUtil.format("&c[Party] &f파티장만 추방할 수 있습니다."));
            return;
        }
        Player target = Bukkit.getPlayer(targetName);
        if (target == null || !party.hasMember(target.getUniqueId())) {
            leader.sendMessage(ChatUtil.format("&c[Party] &f해당 플레이어는 파티원이 아닙니다."));
            return;
        }
        party.removeMember(target.getUniqueId());
        playerPartyCache.remove(target.getUniqueId());
        party.broadcastMessage(ChatUtil.format("&c[Party] &f''{0}''님이 파티에서 추방되었습니다.", target.getName()));
    }

    /** 파티장 위임 */
    public void promotePlayer(Player leader, Player target) {
        Party party = getParty(leader.getUniqueId());
        if (party == null || !party.isLeader(leader.getUniqueId())) {
            leader.sendMessage(ChatUtil.format("&c[Party] &f파티장만 위임할 수 있습니다."));
            return;
        }
        if (!party.hasMember(target.getUniqueId())) {
            leader.sendMessage(ChatUtil.format("&c[Party] &f해당 플레이어는 파티원이 아닙니다."));
            return;
        }
        party.setLeader(target.getUniqueId());
        parties.remove(leader.getUniqueId());
        parties.put(target.getUniqueId(), party);
        party.broadcastMessage(ChatUtil.format("&a[Party] &f''{0}''님이 새로운 파티장이 되었습니다.", target.getName()));
    }

    /** 파티 탈퇴 */
    public void leaveParty(Player player) {
        Party party = getParty(player.getUniqueId());
        if (party == null) {
            player.sendMessage(ChatUtil.format("&c[Party] &f속한 파티가 없습니다."));
            return;
        }
        party.broadcastMessage(ChatUtil.format("&c[Party] &f''{0}''님이 파티를 떠났습니다.", player.getName()));
        party.removeMember(player.getUniqueId());
        playerPartyCache.remove(player.getUniqueId());

        if (party.getSize() == 0) {
            parties.remove(party.getLeader());
        } else if (party.isLeader(player.getUniqueId())) {
            UUID newLeader = party.getMembers().iterator().next();
            party.setLeader(newLeader);
            parties.remove(player.getUniqueId());
            parties.put(newLeader, party);
            Player nl = Bukkit.getPlayer(newLeader);
            party.broadcastMessage(ChatUtil.format("&a[Party] &f''{0}''님이 새로운 파티장이 되었습니다.", nl != null ? nl.getName() : "알 수 없음"));
        }
    }

    /** 파티 채팅 */
    public void sendPartyMessage(Player player, String message) {
        Party party = getParty(player.getUniqueId());
        if (party == null) {
            player.sendMessage(ChatUtil.format("&c[Party] &f속한 파티가 없습니다."));
            return;
        }
        String formattedMessage = ChatUtil.format("&9[파티] &f{0}: {1}", player.getName(), message);
        party.broadcastMessage(formattedMessage);
    }

    /** 파티 채팅 토글 */
    public void togglePartyChat(Player player) {
        UUID uuid = player.getUniqueId();
        if (partyChatToggled.contains(uuid)) {
            partyChatToggled.remove(uuid);
            player.sendMessage(ChatUtil.format("&a[Party] &f파티 채팅 모드를 껐습니다."));
        } else {
            partyChatToggled.add(uuid);
            player.sendMessage(ChatUtil.format("&a[Party] &f파티 채팅 모드를 켰습니다. (일반 채팅이 파티 채팅으로 전송됩니다)"));
        }
    }

    public boolean isPartyChatToggled(UUID uuid) {
        return partyChatToggled.contains(uuid);
    }

    /** 데미지 로그 기록 */
    public void logDamage(Player attacker, Entity victim, double damage) {
        if (!isInParty(attacker.getUniqueId())) return;
        Map<UUID, Double> monsterLog = damageLogs.computeIfAbsent(victim.getUniqueId(), k -> new HashMap<>());
        monsterLog.merge(attacker.getUniqueId(), damage, Double::sum);
    }

    /** 경험치 분배 */
    public void distributeExperience(LivingEntity victim, double totalExperience) {
        Party party = getPartyFromDamageLog(victim.getUniqueId());
        if (party == null) return;

        List<Player> nearbyMembers = party.getOnlineMembers().stream()
                .filter(p -> p.getWorld().equals(victim.getWorld()) && p.getLocation().distanceSquared(victim.getLocation()) < 100 * 100)
                .collect(Collectors.toList());
        if (nearbyMembers.isEmpty()) return;

        double expPerPlayer = totalExperience / nearbyMembers.size();
        for (Player member : nearbyMembers) {
            playerDataManager.addExperience(member, expPerPlayer);
        }
    }

    /** 탑 딜러 조회 */
    public Player getTopDealerPlayer(LivingEntity victim) {
        Map<UUID, Double> monsterLog = damageLogs.get(victim.getUniqueId());
        if (monsterLog == null || monsterLog.isEmpty()) return null;

        UUID topDealerUUID = monsterLog.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
        if (topDealerUUID == null) return null;

        Player topDealer = Bukkit.getPlayer(topDealerUUID);
        return (topDealer != null && topDealer.isOnline()) ? topDealer : null;
    }

    /** 데미지 로그 초기화 */
    public void clearDamageLog(LivingEntity victim) {
        damageLogs.remove(victim.getUniqueId());
    }

    /** 파티 여부 */
    public boolean isInParty(UUID uuid) {
        return playerPartyCache.containsKey(uuid);
    }

    /** 파티 조회 */
    public Party getParty(UUID uuid) {
        return playerPartyCache.get(uuid);
    }

    /** 파티 생성 (내부) */
    private Party createParty(Player leader) {
        Party party = new Party(leader.getUniqueId());
        parties.put(leader.getUniqueId(), party);
        playerPartyCache.put(leader.getUniqueId(), party);
        return party;
    }

    /** 데미지 로그에서 파티 찾기 */
    private Party getPartyFromDamageLog(UUID monsterUuid) {
        Map<UUID, Double> log = damageLogs.get(monsterUuid);
        if (log == null || log.isEmpty()) return null;
        UUID anyAttacker = log.keySet().iterator().next();
        return getParty(anyAttacker);
    }

    /** 파티원 이름 목록 */
    public List<String> getPartyMemberNames(Player player) {
        Party party = getParty(player.getUniqueId());
        if (party == null) return Collections.emptyList();
        return party.getOnlineMembers().stream().map(Player::getName).collect(Collectors.toList());
    }

    /** 대기 초대 발신자 이름 목록 */
    public List<String> getPendingInviteNames(Player player) {
        Map<UUID, Long> invites = pendingInvites.get(player.getUniqueId());
        if (invites == null) return Collections.emptyList();
        long now = System.currentTimeMillis();

        return invites.entrySet().stream()
                .filter(e -> e.getValue() > now)
                .map(Map.Entry::getKey)
                .map(uuid -> Bukkit.getOfflinePlayer(uuid).getName())
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /** 파티 정보 표시 */
    public void showPartyInfo(Player player) {
        Party party = getParty(player.getUniqueId());
        if (party == null) {
            player.sendMessage(ChatUtil.format("&c[Party] &f속한 파티가 없습니다."));
            return;
        }
        player.sendMessage(ChatUtil.format("&a--- [파티 정보] ---"));
        for (UUID uuid : party.getMembers()) {
            Player p = Bukkit.getPlayer(uuid);
            String name = (p != null && p.isOnline()) ? p.getName() : "오프라인";
            if (party.isLeader(uuid)) {
                player.sendMessage(ChatUtil.format("&b- {0} (파티장)", name));
            } else {
                player.sendMessage(ChatUtil.format("&7- {0}", name));
            }
        }
    }

    /** 파티 데이터 구조 */
    public static class Party {
        private UUID leader;
        private final Set<UUID> members;

        public Party(UUID leader) {
            this.leader = leader;
            this.members = new HashSet<>();
            this.members.add(leader);
        }

        public UUID getLeader() { return leader; }
        public Set<UUID> getMembers() { return members; }
        public int getSize() { return members.size(); }
        public boolean isLeader(UUID uuid) { return leader.equals(uuid); }
        public boolean hasMember(UUID uuid) { return members.contains(uuid); }
        public void addMember(UUID uuid) { members.add(uuid); }
        public void removeMember(UUID uuid) { members.remove(uuid); }
        public void setLeader(UUID uuid) { this.leader = uuid; }

        public List<Player> getOnlineMembers() {
            return members.stream()
                    .map(Bukkit::getPlayer)
                    .filter(Objects::nonNull)
                    .filter(Player::isOnline)
                    .collect(Collectors.toList());
        }

        public void broadcastMessage(String message) {
            getOnlineMembers().forEach(player -> player.sendMessage(message));
        }
    }
}
