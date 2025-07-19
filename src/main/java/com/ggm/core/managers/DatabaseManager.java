package com.ggm.core.managers;

import com.ggm.core.GGMCore;
import org.bukkit.configuration.file.FileConfiguration;

import java.sql.*;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class DatabaseManager {

    private final GGMCore plugin;
    private final String host;
    private final int port;
    private final String database;
    private final String username;
    private final String password;
    private final String connectionUrl;

    public DatabaseManager(GGMCore plugin) {
        this.plugin = plugin;
        FileConfiguration config = plugin.getConfig();

        this.host = config.getString("database.host");
        this.port = config.getInt("database.port");
        this.database = config.getString("database.database");
        this.username = config.getString("database.username");
        this.password = config.getString("database.password");

        // 연결 URL 미리 생성 (매번 새 연결 사용)
        this.connectionUrl = "jdbc:mysql://" + host + ":" + port + "/" + database +
                "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC" +
                "&autoReconnect=true&useUnicode=true&characterEncoding=utf8";

        // 초기 연결 테스트 및 테이블 생성
        testConnection();
        createTables();
    }

    /**
     * 새 데이터베이스 연결 생성 (매번 새 연결)
     */
    private Connection createConnection() throws SQLException {
        try {
            Connection conn = DriverManager.getConnection(connectionUrl, username, password);
            plugin.getLogger().finest("새 데이터베이스 연결이 생성되었습니다.");
            return conn;
        } catch (SQLException e) {
            plugin.getLogger().severe("데이터베이스 연결 실패: " + e.getMessage());
            throw e;
        }
    }

    /**
     * 연결 테스트
     */
    private void testConnection() {
        try (Connection conn = createConnection()) {
            plugin.getLogger().info("데이터베이스에 성공적으로 연결되었습니다.");
        } catch (SQLException e) {
            plugin.getLogger().severe("데이터베이스 연결 테스트 실패: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 테이블 생성
     */
    private void createTables() {
        try (Connection conn = createConnection()) {
            // 플레이어 경제 테이블
            String economyTable = """
                CREATE TABLE IF NOT EXISTS ggm_economy (
                    uuid VARCHAR(36) PRIMARY KEY,
                    player_name VARCHAR(16) NOT NULL,
                    balance BIGINT NOT NULL DEFAULT 1000,
                    last_login TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                )
                """;

            // 거래 로그 테이블
            String transactionTable = """
                CREATE TABLE IF NOT EXISTS ggm_transactions (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    from_uuid VARCHAR(36) NOT NULL,
                    to_uuid VARCHAR(36) NOT NULL,
                    amount BIGINT NOT NULL,
                    fee BIGINT NOT NULL DEFAULT 0,
                    transaction_type ENUM('TRANSFER', 'ADMIN_GIVE', 'ADMIN_TAKE') NOT NULL,
                    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """;

            executeUpdate(conn, economyTable);
            executeUpdate(conn, transactionTable);

            plugin.getLogger().info("데이터베이스 테이블이 준비되었습니다.");

        } catch (SQLException e) {
            plugin.getLogger().severe("테이블 생성 실패: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 데이터베이스 연결 반환 (매번 새 연결)
     */
    public Connection getConnection() throws SQLException {
        return createConnection();
    }

    // 비동기로 플레이어 데이터 생성 또는 업데이트
    public CompletableFuture<Void> createOrUpdatePlayer(UUID uuid, String playerName) {
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = createConnection()) {
                String sql = """
                    INSERT INTO ggm_economy (uuid, player_name, balance) 
                    VALUES (?, ?, ?) 
                    ON DUPLICATE KEY UPDATE 
                    player_name = VALUES(player_name), 
                    last_login = CURRENT_TIMESTAMP
                    """;

                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, uuid.toString());
                    stmt.setString(2, playerName);
                    stmt.setLong(3, plugin.getConfig().getLong("economy.starting_money"));
                    stmt.executeUpdate();
                }

            } catch (SQLException e) {
                plugin.getLogger().severe("플레이어 데이터 생성/업데이트 실패: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    // 플레이어 잔액 조회
    public CompletableFuture<Long> getBalance(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = createConnection()) {
                String sql = "SELECT balance FROM ggm_economy WHERE uuid = ?";
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, uuid.toString());
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            return rs.getLong("balance");
                        }
                    }
                }
                return 0L;

            } catch (SQLException e) {
                plugin.getLogger().severe("잔액 조회 실패: " + e.getMessage());
                e.printStackTrace();
                return 0L;
            }
        });
    }

    // 플레이어 잔액 설정
    public CompletableFuture<Boolean> setBalance(UUID uuid, long amount) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = createConnection()) {
                String sql = "UPDATE ggm_economy SET balance = ? WHERE uuid = ?";
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setLong(1, Math.max(0, amount)); // 음수 방지
                    stmt.setString(2, uuid.toString());
                    int result = stmt.executeUpdate();
                    return result > 0;
                }

            } catch (SQLException e) {
                plugin.getLogger().severe("잔액 설정 실패: " + e.getMessage());
                e.printStackTrace();
                return false;
            }
        });
    }

    // 플레이어 잔액 추가/차감
    public CompletableFuture<Boolean> addBalance(UUID uuid, long amount) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = createConnection()) {
                String sql = "UPDATE ggm_economy SET balance = balance + ? WHERE uuid = ?";
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setLong(1, amount);
                    stmt.setString(2, uuid.toString());
                    int result = stmt.executeUpdate();
                    return result > 0;
                }

            } catch (SQLException e) {
                plugin.getLogger().severe("잔액 변경 실패: " + e.getMessage());
                e.printStackTrace();
                return false;
            }
        });
    }

    // 거래 로그 기록
    public CompletableFuture<Void> logTransaction(UUID fromUuid, UUID toUuid, long amount, long fee, String type) {
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = createConnection()) {
                String sql = """
                    INSERT INTO ggm_transactions (from_uuid, to_uuid, amount, fee, transaction_type) 
                    VALUES (?, ?, ?, ?, ?)
                    """;

                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, fromUuid.toString());
                    stmt.setString(2, toUuid.toString());
                    stmt.setLong(3, amount);
                    stmt.setLong(4, fee);
                    stmt.setString(5, type);
                    stmt.executeUpdate();
                }

            } catch (SQLException e) {
                plugin.getLogger().severe("거래 로그 기록 실패: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    // UUID로 플레이어 이름 조회
    public CompletableFuture<String> getPlayerName(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = createConnection()) {
                String sql = "SELECT player_name FROM ggm_economy WHERE uuid = ?";
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, uuid.toString());
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            return rs.getString("player_name");
                        }
                    }
                }
                return null;

            } catch (SQLException e) {
                plugin.getLogger().severe("플레이어 이름 조회 실패: " + e.getMessage());
                e.printStackTrace();
                return null;
            }
        });
    }

    // 연결 종료 (더 이상 사용하지 않음)
    public void closeConnection() {
        plugin.getLogger().info("데이터베이스 매니저가 종료되었습니다.");
    }

    // SQL 실행 헬퍼 메소드
    private void executeUpdate(Connection conn, String sql) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.executeUpdate();
        }
    }
}