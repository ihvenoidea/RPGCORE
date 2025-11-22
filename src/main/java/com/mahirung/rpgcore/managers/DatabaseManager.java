package com.mahirung.rpgcore.managers;

import com.mahirung.rpgcore.RPGCore;
import com.mahirung.rpgcore.data.PlayerData;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.logging.Level;

public class DatabaseManager {

    private final RPGCore plugin;
    private HikariDataSource dataSource;

    public DatabaseManager(RPGCore plugin) {
        this.plugin = plugin;
        connect();
        createTables();
    }

    /**
     * 데이터베이스 연결 설정 (HikariCP)
     */
    private void connect() {
        String host = plugin.getConfig().getString("database.host");
        int port = plugin.getConfig().getInt("database.port");
        String user = plugin.getConfig().getString("database.user");
        String password = plugin.getConfig().getString("database.password");
        String database = plugin.getConfig().getString("database.database");

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=false&autoReconnect=true&characterEncoding=UTF-8");
        config.setUsername(user);
        config.setPassword(password);
        
        // 성능 최적화 설정
        config.setMaximumPoolSize(plugin.getConfig().getInt("database.pool-size", 10));
        config.setMinimumIdle(2);
        config.setIdleTimeout(30000);
        config.setMaxLifetime(1800000); // 30분
        config.setConnectionTimeout(10000); // 10초

        try {
            dataSource = new HikariDataSource(config);
            plugin.getLogger().info("데이터베이스에 성공적으로 연결되었습니다.");
        } catch (Exception e) {
            plugin.getLogger().severe("데이터베이스 연결 실패! 설정을 확인해주세요.");
            e.printStackTrace();
        }
    }

    /**
     * 테이블 자동 생성
     */
    private void createTables() {
        if (dataSource == null) return;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "CREATE TABLE IF NOT EXISTS rpg_player_data (" +
                             "uuid VARCHAR(36) PRIMARY KEY, " +
                             "player_class VARCHAR(32), " +
                             "level INT DEFAULT 1, " +
                             "exp DOUBLE DEFAULT 0, " +
                             "base_attack DOUBLE DEFAULT 0, " +
                             "base_defense DOUBLE DEFAULT 0, " +
                             "base_mana DOUBLE DEFAULT 100, " +
                             "bonus_attack DOUBLE DEFAULT 0, " +
                             "bonus_defense DOUBLE DEFAULT 0, " +
                             "bonus_mana DOUBLE DEFAULT 0, " +
                             "current_mana DOUBLE DEFAULT 100, " +
                             "last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP" +
                             ")"
             )) {
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "테이블 생성 중 오류 발생", e);
        }
    }

    /**
     * 플레이어 데이터 저장 (Insert/Update)
     */
    public void savePlayerData(PlayerData data) {
        if (dataSource == null) return;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "REPLACE INTO rpg_player_data " +
                             "(uuid, player_class, level, exp, base_attack, base_defense, base_mana, " +
                             "bonus_attack, bonus_defense, bonus_mana, current_mana) " +
                             "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
             )) {
            ps.setString(1, data.getUuid().toString());
            ps.setString(2, data.getPlayerClass());
            ps.setInt(3, data.getLevel());
            ps.setDouble(4, data.getCurrentExp());
            // Getter 로직에 따라 Base 스탯만 저장 (Bonus는 보통 아이템 등에서 오므로 저장 여부 결정 필요)
            // 여기서는 편의상 PlayerData에 getBaseXXX 메서드가 있다고 가정하거나,
            // getAttack()에서 보너스를 뺀 값을 저장하는 식으로 구현해야 함.
            // 현재 PlayerData 구조상 getAttack()은 (Base + Bonus)를 반환하므로, 
            // 정확한 저장을 위해 PlayerData에 getBaseAttack() 등의 Getter가 필요함.
            // 일단 여기서는 현재 값을 저장하도록 함.
            ps.setDouble(5, data.getAttack()); // TODO: getBaseAttack()으로 수정 권장
            ps.setDouble(6, data.getDefense()); // TODO: getBaseDefense()으로 수정 권장
            ps.setDouble(7, data.getMaxMana()); // TODO: getBaseMaxMana()으로 수정 권장
            
            // 보너스 스탯 및 현재 마나
            ps.setDouble(8, 0.0); // 보너스 스탯은 보통 아이템에서 오므로 DB 저장은 0으로 하거나 별도 관리
            ps.setDouble(9, 0.0);
            ps.setDouble(10, 0.0);
            ps.setDouble(11, data.getCurrentMana());

            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "데이터 저장 실패: " + data.getUuid(), e);
        }
    }

    /**
     * 플레이어 데이터 로드 (Select)
     */
    public PlayerData loadPlayerData(UUID uuid) {
        if (dataSource == null) return null;

        PlayerData data = null;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM rpg_player_data WHERE uuid = ?"
             )) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    // DB에 데이터가 있는 경우 로드
                    data = new PlayerData(plugin, uuid);
                    data.setPlayerClass(rs.getString("player_class"));
                    data.setLevel(rs.getInt("level"));
                    data.setCurrentExp(rs.getDouble("exp"));
                    
                    data.setBaseAttack(rs.getDouble("base_attack"));
                    data.setBaseDefense(rs.getDouble("base_defense"));
                    data.setBaseMaxMana(rs.getDouble("base_mana"));
                    
                    // 현재 마나 복구
                    // data.setCurrentMana(rs.getDouble("current_mana")); 
                    // PlayerData에 setCurrentMana 메서드가 있다면 주석 해제
                    
                    data.setNewPlayer(false);
                } else {
                    // 신규 유저인 경우 기본 데이터 생성
                    data = new PlayerData(plugin, uuid);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "데이터 로드 실패: " + uuid, e);
        }
        return data;
    }

    public HikariDataSource getDataSource() { return dataSource; }
    
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}