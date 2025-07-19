package com.ggm.core.managers;

import com.ggm.core.GGMCore;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class InventoryManager {

    private final GGMCore plugin;
    private final DatabaseManager databaseManager;

    public InventoryManager(GGMCore plugin) {
        this.plugin = plugin;
        this.databaseManager = plugin.getDatabaseManager();

        // 인벤토리 동기화가 활성화된 경우에만 테이블 생성
        if (plugin.getConfig().getBoolean("inventory_sync.enabled", true)) {
            createInventoryTable();
            plugin.getLogger().info("크로스 서버 인벤토리 동기화가 활성화되었습니다.");
        } else {
            plugin.getLogger().info("크로스 서버 인벤토리 동기화가 비활성화되었습니다.");
        }
    }

    /**
     * 인벤토리 데이터 테이블 생성
     */
    private void createInventoryTable() {
        String sql = """
            CREATE TABLE IF NOT EXISTS ggm_player_inventory (
                uuid VARCHAR(36) PRIMARY KEY,
                player_name VARCHAR(16) NOT NULL,
                inventory_data TEXT NOT NULL,
                armor_data TEXT NOT NULL,
                offhand_data TEXT,
                health DOUBLE DEFAULT 20.0,
                food_level INT DEFAULT 20,
                saturation FLOAT DEFAULT 5.0,
                exp_level INT DEFAULT 0,
                exp_points FLOAT DEFAULT 0.0,
                last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                server_name VARCHAR(20)
            )
            """;

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.executeUpdate();
            plugin.getLogger().info("인벤토리 테이블이 준비되었습니다.");
        } catch (SQLException e) {
            plugin.getLogger().severe("인벤토리 테이블 생성 실패: " + e.getMessage());
        }
    }

    /**
     * 플레이어 인벤토리 저장 (비동기)
     */
    public CompletableFuture<Boolean> savePlayerInventory(Player player) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                UUID uuid = player.getUniqueId();
                String playerName = player.getName();
                String serverName = plugin.getServerDetector().getServerType();

                // 인벤토리 직렬화
                String inventoryData = serializeInventory(player.getInventory().getContents());
                String armorData = serializeInventory(player.getInventory().getArmorContents());
                String offhandData = serializeItemStack(player.getInventory().getItemInOffHand());

                // 플레이어 상태 (설정에 따라)
                double health = plugin.getConfig().getBoolean("inventory_sync.sync_health", true)
                        ? player.getHealth() : 20.0;
                int foodLevel = plugin.getConfig().getBoolean("inventory_sync.sync_hunger", true)
                        ? player.getFoodLevel() : 20;
                float saturation = plugin.getConfig().getBoolean("inventory_sync.sync_hunger", true)
                        ? player.getSaturation() : 5.0f;
                int expLevel = plugin.getConfig().getBoolean("inventory_sync.sync_experience", true)
                        ? player.getLevel() : 0;
                float expPoints = plugin.getConfig().getBoolean("inventory_sync.sync_experience", true)
                        ? player.getExp() : 0.0f;

                String sql = """
                    INSERT INTO ggm_player_inventory 
                    (uuid, player_name, inventory_data, armor_data, offhand_data, 
                     health, food_level, saturation, exp_level, exp_points, server_name) 
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE 
                    player_name = VALUES(player_name),
                    inventory_data = VALUES(inventory_data),
                    armor_data = VALUES(armor_data),
                    offhand_data = VALUES(offhand_data),
                    health = VALUES(health),
                    food_level = VALUES(food_level),
                    saturation = VALUES(saturation),
                    exp_level = VALUES(exp_level),
                    exp_points = VALUES(exp_points),
                    server_name = VALUES(server_name),
                    last_updated = CURRENT_TIMESTAMP
                    """;

                try (Connection conn = databaseManager.getConnection();
                     PreparedStatement stmt = conn.prepareStatement(sql)) {

                    stmt.setString(1, uuid.toString());
                    stmt.setString(2, playerName);
                    stmt.setString(3, inventoryData);
                    stmt.setString(4, armorData);
                    stmt.setString(5, offhandData);
                    stmt.setDouble(6, health);
                    stmt.setInt(7, foodLevel);
                    stmt.setFloat(8, saturation);
                    stmt.setInt(9, expLevel);
                    stmt.setFloat(10, expPoints);
                    stmt.setString(11, serverName);

                    int result = stmt.executeUpdate();

                    if (result > 0) {
                        plugin.getLogger().info(String.format("플레이어 %s의 인벤토리가 저장되었습니다. (서버: %s)",
                                playerName, serverName));
                        return true;
                    }
                }

            } catch (Exception e) {
                plugin.getLogger().severe("인벤토리 저장 실패: " + e.getMessage());
                e.printStackTrace();
            }

            return false;
        });
    }

    /**
     * 플레이어 인벤토리 로드 (비동기)
     */
    public CompletableFuture<Boolean> loadPlayerInventory(Player player) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                UUID uuid = player.getUniqueId();
                String serverName = plugin.getServerDetector().getServerType();

                String sql = "SELECT * FROM ggm_player_inventory WHERE uuid = ?";

                try (Connection conn = databaseManager.getConnection();
                     PreparedStatement stmt = conn.prepareStatement(sql)) {

                    stmt.setString(1, uuid.toString());
                    ResultSet rs = stmt.executeQuery();

                    if (rs.next()) {
                        // 메인 스레드에서 인벤토리 적용
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            try {
                                // 인벤토리 역직렬화 및 적용
                                String inventoryData = rs.getString("inventory_data");
                                String armorData = rs.getString("armor_data");
                                String offhandData = rs.getString("offhand_data");

                                if (inventoryData != null && !inventoryData.isEmpty()) {
                                    ItemStack[] inventory = deserializeInventory(inventoryData);
                                    player.getInventory().setContents(inventory);
                                }

                                if (armorData != null && !armorData.isEmpty()) {
                                    ItemStack[] armor = deserializeInventory(armorData);
                                    player.getInventory().setArmorContents(armor);
                                }

                                if (offhandData != null && !offhandData.isEmpty()) {
                                    ItemStack offhand = deserializeItemStack(offhandData);
                                    player.getInventory().setItemInOffHand(offhand);
                                }

                                // 플레이어 상태 복원 (설정에 따라)
                                if (plugin.getConfig().getBoolean("inventory_sync.sync_health", true)) {
                                    double health = rs.getDouble("health");
                                    player.setHealth(Math.min(health, player.getMaxHealth()));
                                }

                                if (plugin.getConfig().getBoolean("inventory_sync.sync_hunger", true)) {
                                    int foodLevel = rs.getInt("food_level");
                                    float saturation = rs.getFloat("saturation");
                                    player.setFoodLevel(foodLevel);
                                    player.setSaturation(saturation);
                                }

                                if (plugin.getConfig().getBoolean("inventory_sync.sync_experience", true)) {
                                    int expLevel = rs.getInt("exp_level");
                                    float expPoints = rs.getFloat("exp_points");
                                    player.setLevel(expLevel);
                                    player.setExp(expPoints);
                                }

                                plugin.getLogger().info(String.format("플레이어 %s의 인벤토리가 로드되었습니다. (서버: %s)",
                                        player.getName(), serverName));

                            } catch (Exception e) {
                                plugin.getLogger().severe("인벤토리 적용 실패: " + e.getMessage());
                                e.printStackTrace();
                            }
                        });

                        rs.close();
                        return true;
                    }

                    rs.close();
                }

            } catch (Exception e) {
                plugin.getLogger().severe("인벤토리 로드 실패: " + e.getMessage());
                e.printStackTrace();
            }

            return false;
        });
    }

    /**
     * 인벤토리 배열을 Base64 문자열로 직렬화
     */
    private String serializeInventory(ItemStack[] items) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream);

        dataOutput.writeInt(items.length);
        for (ItemStack item : items) {
            dataOutput.writeObject(item);
        }

        dataOutput.close();
        return Base64.getEncoder().encodeToString(outputStream.toByteArray());
    }

    /**
     * Base64 문자열을 인벤토리 배열로 역직렬화
     */
    private ItemStack[] deserializeInventory(String data) throws IOException, ClassNotFoundException {
        ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64.getDecoder().decode(data));
        BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream);

        int length = dataInput.readInt();
        ItemStack[] items = new ItemStack[length];

        for (int i = 0; i < length; i++) {
            items[i] = (ItemStack) dataInput.readObject();
        }

        dataInput.close();
        return items;
    }

    /**
     * 단일 아이템을 Base64 문자열로 직렬화
     */
    private String serializeItemStack(ItemStack item) throws IOException {
        if (item == null) return "";

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream);

        dataOutput.writeObject(item);
        dataOutput.close();

        return Base64.getEncoder().encodeToString(outputStream.toByteArray());
    }

    /**
     * Base64 문자열을 단일 아이템으로 역직렬화
     */
    private ItemStack deserializeItemStack(String data) throws IOException, ClassNotFoundException {
        if (data == null || data.isEmpty()) return null;

        ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64.getDecoder().decode(data));
        BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream);

        ItemStack item = (ItemStack) dataInput.readObject();
        dataInput.close();

        return item;
    }

    /**
     * 플레이어 인벤토리 데이터 삭제
     */
    public CompletableFuture<Boolean> deletePlayerInventory(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String sql = "DELETE FROM ggm_player_inventory WHERE uuid = ?";

                try (Connection conn = databaseManager.getConnection();
                     PreparedStatement stmt = conn.prepareStatement(sql)) {

                    stmt.setString(1, uuid.toString());
                    int result = stmt.executeUpdate();

                    return result > 0;
                }

            } catch (SQLException e) {
                plugin.getLogger().severe("인벤토리 삭제 실패: " + e.getMessage());
                return false;
            }
        });
    }

    /**
     * 인벤토리 동기화 강제 실행 (OP 명령어용)
     */
    public void forceSyncInventory(Player player) {
        savePlayerInventory(player).thenCompose(saved -> {
            if (saved) {
                return loadPlayerInventory(player);
            } else {
                return CompletableFuture.completedFuture(false);
            }
        }).thenAccept(result -> {
            if (result) {
                player.sendMessage("§a인벤토리가 동기화되었습니다!");
            } else {
                player.sendMessage("§c인벤토리 동기화에 실패했습니다.");
            }
        });
    }
}