package com.ggm.core.listeners;

import com.ggm.core.GGMCore;
import com.ggm.core.managers.EconomyManager;
import com.ggm.core.managers.InventoryManager;
import com.ggm.core.managers.ScoreboardManager;
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
        this.inventoryManager = plugin.getInventoryManager();
        this.scoreboardManager = plugin.getScoreboardManager();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // 플레이어 경제 데이터 초기화 (비동기)
        economyManager.initializePlayer(player)
                .thenRun(() -> {
                    plugin.getLogger().info(String.format("플레이어 %s의 경제 데이터가 초기화되었습니다.",
                            player.getName()));

                    // 스코어보드 생성 (메인 스레드에서)
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        scoreboardManager.createScoreboard(player);

                        // 모든 플레이어의 온라인 인원 수 업데이트 (1초 후)
                        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                            scoreboardManager.updateOnlineCount();
                        }, 20L);
                    });
                })
                .exceptionally(throwable -> {
                    plugin.getLogger().severe(String.format("플레이어 %s의 경제 데이터 초기화 실패: %s",
                            player.getName(), throwable.getMessage()));
                    return null;
                });

        // 인벤토리 로드 (설정에 따라)
        if (plugin.getConfig().getBoolean("inventory_sync.enabled", true) &&
                plugin.getConfig().getBoolean("inventory_sync.load_on_join", true)) {

            long loadDelay = plugin.getConfig().getLong("inventory_sync.load_delay", 20L);

            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                inventoryManager.loadPlayerInventory(player)
                        .thenAccept(success -> {
                            if (success) {
                                plugin.getLogger().info(String.format("플레이어 %s의 인벤토리가 로드되었습니다.",
                                        player.getName()));

                                // 플레이어에게 알림
                                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                                    player.sendMessage("§a인벤토리가 동기화되었습니다!");
                                }, 20L); // 1초 후 메시지
                            } else {
                                plugin.getLogger().info(String.format("플레이어 %s: 새로운 인벤토리로 시작합니다.",
                                        player.getName()));
                            }
                        })
                        .exceptionally(throwable -> {
                            plugin.getLogger().severe(String.format("플레이어 %s의 인벤토리 로드 실패: %s",
                                    player.getName(), throwable.getMessage()));
                            return null;
                        });
            }, loadDelay);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        // 인벤토리 저장 (설정에 따라)
        if (plugin.getConfig().getBoolean("inventory_sync.enabled", true) &&
                plugin.getConfig().getBoolean("inventory_sync.save_on_quit", true)) {

            try {
                inventoryManager.savePlayerInventory(player).get(); // 동기 대기
                plugin.getLogger().info(String.format("플레이어 %s의 인벤토리가 저장되었습니다.",
                        player.getName()));
            } catch (Exception e) {
                plugin.getLogger().severe(String.format("플레이어 %s의 인벤토리 저장 실패: %s",
                        player.getName(), e.getMessage()));
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