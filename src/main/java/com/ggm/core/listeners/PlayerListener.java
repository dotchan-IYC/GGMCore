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

        // 인벤토리 로드 (설정에 따라) - 개선된 로직
        if (plugin.getConfig().getBoolean("inventory_sync.enabled", true) &&
                plugin.getConfig().getBoolean("inventory_sync.load_on_join", true)) {

            long loadDelay = plugin.getConfig().getLong("inventory_sync.load_delay", 20L);

            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                handleInventorySync(player);
            }, loadDelay);
        }
    }

    /**
     * 개선된 인벤토리 동기화 처리
     */
    private void handleInventorySync(Player player) {
        plugin.getLogger().info(String.format("[인벤토리] %s의 인벤토리 동기화 시작...", player.getName()));

        inventoryManager.loadPlayerInventory(player)
                .thenAccept(success -> {
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        if (success) {
                            player.sendMessage("§a✅ 인벤토리가 동기화되었습니다!");
                            plugin.getLogger().info(String.format("[인벤토리] %s: 동기화 완료 (기존 데이터 로드)",
                                    player.getName()));
                        } else {
                            // 기존 사용자의 경우 현재 인벤토리를 저장
                            plugin.getLogger().info(String.format("[인벤토리] %s: 기존 데이터 없음, 현재 인벤토리 저장 중...",
                                    player.getName()));

                            inventoryManager.savePlayerInventory(player)
                                    .thenAccept(saved -> {
                                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                                            if (saved) {
                                                player.sendMessage("§e📦 인벤토리가 새로 등록되었습니다!");
                                                plugin.getLogger().info(String.format("[인벤토리] %s: 새 데이터 저장 완료",
                                                        player.getName()));
                                            } else {
                                                player.sendMessage("§c❌ 인벤토리 등록에 실패했습니다.");
                                                plugin.getLogger().warning(String.format("[인벤토리] %s: 새 데이터 저장 실패",
                                                        player.getName()));
                                            }
                                        });
                                    })
                                    .exceptionally(saveException -> {
                                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                                            player.sendMessage("§c❌ 인벤토리 저장 중 오류가 발생했습니다.");
                                        });
                                        plugin.getLogger().severe(String.format("[인벤토리] %s 저장 오류: %s",
                                                player.getName(), saveException.getMessage()));
                                        return null;
                                    });
                        }
                    });
                })
                .exceptionally(throwable -> {
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        player.sendMessage("§c❌ 인벤토리 동기화 중 오류가 발생했습니다!");
                        player.sendMessage("§7관리자에게 문의해주세요.");
                    });
                    plugin.getLogger().severe(String.format("[인벤토리] %s 로드 오류: %s",
                            player.getName(), throwable.getMessage()));
                    throwable.printStackTrace();
                    return null;
                });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        // 인벤토리 저장 (설정에 따라) - 개선된 로직
        if (plugin.getConfig().getBoolean("inventory_sync.enabled", true) &&
                plugin.getConfig().getBoolean("inventory_sync.save_on_quit", true)) {

            try {
                plugin.getLogger().info(String.format("[인벤토리] %s의 인벤토리 저장 중...", player.getName()));

                inventoryManager.savePlayerInventory(player).get(); // 동기 대기
                plugin.getLogger().info(String.format("[인벤토리] %s의 인벤토리가 저장되었습니다.",
                        player.getName()));
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
