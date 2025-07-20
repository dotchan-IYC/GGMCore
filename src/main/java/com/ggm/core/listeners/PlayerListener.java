package com.ggm.core.listeners;

import com.ggm.core.GGMCore;
import com.ggm.core.managers.EconomyManager;
import com.ggm.core.managers.InventoryManager;
import com.ggm.core.managers.ScoreboardManager;
import com.ggm.core.commands.ScoreboardCommand;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerListener implements Listener {

    private final GGMCore plugin;
    private final EconomyManager economyManager;
    private final InventoryManager inventoryManager;
    private final ScoreboardManager scoreboardManager;

    public PlayerListener(GGMCore plugin) {
        this.plugin = plugin;
        this.economyManager = plugin.getEconomyManager();
        this.inventoryManager = plugin.getInventoryManager(); // null일 수 있음
        this.scoreboardManager = plugin.getScoreboardManager();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        try {
            // 플레이어 경제 데이터 초기화
            economyManager.initializePlayer(player).thenRun(() -> {
                plugin.getLogger().info(String.format("[경제] %s의 경제 데이터 초기화 완료", player.getName()));
            }).exceptionally(throwable -> {
                plugin.getLogger().severe(String.format("[경제] %s의 경제 데이터 초기화 실패: %s",
                        player.getName(), throwable.getMessage()));
                return null;
            });

            // 인벤토리 동기화 (활성화된 경우에만)
            if (inventoryManager != null) {
                try {
                    boolean autoLoad = plugin.getConfig().getBoolean("inventory_sync.load_on_join", false);
                    if (autoLoad) {
                        int delay = plugin.getConfig().getInt("inventory_sync.load_delay", 20);

                        Bukkit.getScheduler().runTaskLater(plugin, () -> {
                            inventoryManager.loadPlayerInventory(player).thenAccept(success -> {
                                plugin.getLogger().info(String.format("[인벤토리] %s의 인벤토리 로드 %s",
                                        player.getName(), success ? "성공" : "실패"));
                            }).exceptionally(throwable -> {
                                plugin.getLogger().severe(String.format("[인벤토리] %s의 인벤토리 로드 실패: %s",
                                        player.getName(), throwable.getMessage()));
                                return null;
                            });
                        }, delay);
                    }
                } catch (Exception e) {
                    plugin.getLogger().severe(String.format("[인벤토리] %s의 인벤토리 로드 실패: %s",
                            player.getName(), e.getMessage()));
                    e.printStackTrace();
                }
            }

        } catch (Exception e) {
            plugin.getLogger().severe("플레이어 접속 처리 중 오류: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR) // 다른 플러그인들이 처리된 후 실행
    public void onPlayerJoinForScoreboard(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // 스코어보드 생성 (약간의 지연을 두고)
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                // 스코어보드가 활성화되어 있고, 플레이어가 비활성화하지 않았다면 생성
                if (plugin.getConfig().getBoolean("scoreboard.enabled", true)) {
                    try {
                        // ScoreboardCommand 인스턴스 가져오기 (임시로 새로 생성)
                        ScoreboardCommand scoreboardCommand = new ScoreboardCommand(plugin);
                        if (!scoreboardCommand.isDisabled(player.getUniqueId())) {
                            scoreboardManager.createScoreboard(player);
                        }
                    } catch (Exception e) {
                        plugin.getLogger().warning("스코어보드 생성 실패: " + e.getMessage());
                    }
                }

                // GGMSurvival과의 연동 재시도 (지연 로딩 대응)
                scoreboardManager.retryJobSystemIntegration();
            }
        }, 40L); // 2초 후 생성

        // 모든 플레이어의 온라인 인원 수 업데이트
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            scoreboardManager.updateOnlineCount();
        }, 60L); // 3초 후
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        // 인벤토리 동기화 저장
        if (inventoryManager != null) {
            try {
                boolean autoSave = plugin.getConfig().getBoolean("inventory_sync.save_on_quit", false);
                if (autoSave) {
                    inventoryManager.savePlayerInventory(player).thenAccept(success -> {
                        plugin.getLogger().info(String.format("[인벤토리] %s의 인벤토리 저장 %s",
                                player.getName(), success ? "성공" : "실패"));
                    }).exceptionally(throwable -> {
                        plugin.getLogger().severe(String.format("[인벤토리] %s의 인벤토리 저장 실패: %s",
                                player.getName(), throwable.getMessage()));
                        return null;
                    });
                }
            } catch (Exception e) {
                plugin.getLogger().severe(String.format("[인벤토리] %s의 인벤토리 저장 실패: %s",
                        player.getName(), e.getMessage()));
                e.printStackTrace();
            }
        }

        // 플레이어 스코어보드 제거
        scoreboardManager.removeScoreboard(player);

        // 모든 플레이어의 온라인 인원 수 업데이트 (1초 후)
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            scoreboardManager.updateOnlineCount();
        }, 20L);
    }
}