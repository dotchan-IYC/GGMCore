package com.ggm.core.commands;

import com.ggm.core.GGMCore;
import com.ggm.core.managers.EconomyManager;
import com.ggm.core.managers.InventoryManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

public class ResetCommand implements CommandExecutor {

    private final GGMCore plugin;
    private final EconomyManager economyManager;
    private final InventoryManager inventoryManager;

    public ResetCommand(GGMCore plugin) {
        this.plugin = plugin;
        this.economyManager = plugin.getEconomyManager();
        this.inventoryManager = plugin.getInventoryManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("ggm.reset")) {
            sender.sendMessage("권한이 없습니다.");
            return true;
        }

        if (args.length < 1) {
            sendHelp(sender);
            return true;
        }

        String targetName = args[0];
        String resetType = args.length > 1 ? args[1].toLowerCase() : "all";

        Player targetPlayer = Bukkit.getPlayer(targetName);
        if (targetPlayer == null) {
            sender.sendMessage("플레이어를 찾을 수 없습니다: " + targetName);
            return true;
        }

        switch (resetType) {
            case "all":
                resetAll(sender, targetPlayer);
                break;
            case "inventory":
                resetInventory(sender, targetPlayer);
                break;
            case "economy":
                resetEconomy(sender, targetPlayer);
                break;
            default:
                sendHelp(sender);
                break;
        }

        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("=== 플레이어 초기화 명령어 ===");
        sender.sendMessage("/ggm reset <플레이어> [all|inventory|economy]");
        sender.sendMessage("  all - 모든 데이터 초기화 (기본값)");
        sender.sendMessage("  inventory - 인벤토리만 초기화");
        sender.sendMessage("  economy - 경제 데이터만 초기화");
        sender.sendMessage("================================");
    }

    /**
     * 모든 데이터 초기화
     */
    private void resetAll(CommandSender sender, Player targetPlayer) {
        UUID uuid = targetPlayer.getUniqueId();
        String playerName = targetPlayer.getName();

        sender.sendMessage("[전체 초기화] " + playerName + "의 모든 데이터를 초기화 중...");
        plugin.getLogger().info(String.format("[전체 초기화] %s이(가) %s의 모든 데이터 초기화를 시작했습니다.",
                sender.getName(), playerName));

        // 1. 인벤토리 초기화
        inventoryManager.deletePlayerInventory(uuid)
                .thenCompose(invSuccess -> {
                    sender.sendMessage("[전체 초기화] 인벤토리 데이터 삭제: " + (invSuccess ? "성공" : "실패"));

                    // 2. 경제 데이터 초기화
                    long startingMoney = plugin.getConfig().getLong("economy.starting_money", 1000L);
                    return economyManager.setBalance(uuid, startingMoney);
                })
                .thenAccept(ecoSuccess -> {
                    sender.sendMessage("[전체 초기화] 경제 데이터 초기화: " + (ecoSuccess ? "성공" : "실패"));

                    // 3. 현재 게임 상태 초기화 (메인 스레드에서)
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        clearPlayerState(targetPlayer);

                        targetPlayer.sendMessage("===========================");
                        targetPlayer.sendMessage("관리자에 의해 계정이 완전 초기화되었습니다.");
                        targetPlayer.sendMessage("새로운 플레이어처럼 다시 시작됩니다!");
                        targetPlayer.sendMessage("===========================");

                        sender.sendMessage("[전체 초기화] " + playerName + "의 모든 데이터가 초기화되었습니다.");
                        sender.sendMessage("플레이어가 서버를 재접속하면 신규 플레이어로 처리됩니다.");

                        // 스코어보드 업데이트
                        plugin.getScoreboardManager().updatePlayerBalance(uuid);

                        plugin.getLogger().info(String.format("[전체 초기화] %s의 모든 데이터 초기화 완료 (by %s)",
                                playerName, sender.getName()));
                    });
                })
                .exceptionally(throwable -> {
                    sender.sendMessage("[전체 초기화] 오류 발생: " + throwable.getMessage());
                    plugin.getLogger().severe(String.format("[전체 초기화] %s 초기화 중 오류: %s",
                            playerName, throwable.getMessage()));
                    throwable.printStackTrace();
                    return null;
                });
    }

    /**
     * 인벤토리만 초기화
     */
    private void resetInventory(CommandSender sender, Player targetPlayer) {
        UUID uuid = targetPlayer.getUniqueId();
        String playerName = targetPlayer.getName();

        sender.sendMessage("[인벤토리 초기화] " + playerName + "의 인벤토리를 초기화 중...");

        inventoryManager.deletePlayerInventory(uuid)
                .thenAccept(success -> {
                    if (success) {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            // 현재 인벤토리 비우기
                            targetPlayer.getInventory().clear();
                            targetPlayer.getInventory().setArmorContents(null);
                            targetPlayer.setHealth(targetPlayer.getMaxHealth());
                            targetPlayer.setFoodLevel(20);
                            targetPlayer.setSaturation(5.0f);
                            targetPlayer.setLevel(0);
                            targetPlayer.setExp(0.0f);

                            targetPlayer.sendMessage("관리자에 의해 인벤토리가 초기화되었습니다.");
                            sender.sendMessage("[인벤토리 초기화] " + playerName + "의 인벤토리가 초기화되었습니다.");
                        });
                    } else {
                        sender.sendMessage("[인벤토리 초기화] " + playerName + "의 인벤토리 초기화에 실패했습니다.");
                    }
                })
                .exceptionally(throwable -> {
                    sender.sendMessage("[인벤토리 초기화] 오류: " + throwable.getMessage());
                    return null;
                });
    }

    /**
     * 경제 데이터만 초기화
     */
    private void resetEconomy(CommandSender sender, Player targetPlayer) {
        UUID uuid = targetPlayer.getUniqueId();
        String playerName = targetPlayer.getName();
        long startingMoney = plugin.getConfig().getLong("economy.starting_money", 1000L);

        sender.sendMessage("[경제 초기화] " + playerName + "의 경제 데이터를 초기화 중...");

        economyManager.setBalance(uuid, startingMoney)
                .thenAccept(success -> {
                    if (success) {
                        targetPlayer.sendMessage("관리자에 의해 G 잔액이 " +
                                economyManager.formatMoney(startingMoney) + "G로 초기화되었습니다.");
                        sender.sendMessage("[경제 초기화] " + playerName + "의 G 잔액이 " +
                                economyManager.formatMoney(startingMoney) + "G로 초기화되었습니다.");

                        // 스코어보드 업데이트
                        plugin.getScoreboardManager().updatePlayerBalance(uuid);
                    } else {
                        sender.sendMessage("[경제 초기화] " + playerName + "의 경제 데이터 초기화에 실패했습니다.");
                    }
                })
                .exceptionally(throwable -> {
                    sender.sendMessage("[경제 초기화] 오류: " + throwable.getMessage());
                    return null;
                });
    }

    /**
     * 플레이어 게임 상태 완전 초기화
     */
    private void clearPlayerState(Player player) {
        // 인벤토리 비우기
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);

        // 상태 초기화
        player.setHealth(player.getMaxHealth());
        player.setFoodLevel(20);
        player.setSaturation(5.0f);
        player.setExhaustion(0.0f);

        // 경험치 초기화
        player.setLevel(0);
        player.setExp(0.0f);
        player.setTotalExperience(0);

        // 포션 효과 제거
        player.getActivePotionEffects().forEach(effect ->
                player.removePotionEffect(effect.getType()));

        // 게임 모드를 기본값으로 (설정에 따라)
        if (plugin.getConfig().getBoolean("reset.set_default_gamemode", false)) {
            player.setGameMode(org.bukkit.GameMode.SURVIVAL);
        }

        plugin.getLogger().info(String.format("[상태 초기화] %s의 게임 상태가 완전 초기화되었습니다.",
                player.getName()));
    }
}
