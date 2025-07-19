package com.ggm.core.commands;

import com.ggm.core.GGMCore;
import com.ggm.core.managers.InventoryManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

public class InventoryCommand implements CommandExecutor {

    private final GGMCore plugin;
    private final InventoryManager inventoryManager;

    public InventoryCommand(GGMCore plugin) {
        this.plugin = plugin;
        this.inventoryManager = plugin.getInventoryManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("ggm.inventory")) {
            sender.sendMessage("§c권한이 없습니다.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "sync":
                if (!(sender instanceof Player)) {
                    sender.sendMessage("이 명령어는 플레이어만 사용할 수 있습니다.");
                    return true;
                }
                syncInventory((Player) sender);
                break;

            case "save":
                if (args.length < 2) {
                    sender.sendMessage("§c사용법: /inventory save <플레이어>");
                    return true;
                }
                savePlayerInventory(sender, args[1]);
                break;

            case "load":
                if (args.length < 2) {
                    sender.sendMessage("§c사용법: /inventory load <플레이어>");
                    return true;
                }
                loadPlayerInventory(sender, args[1]);
                break;

            case "reset":
                if (args.length < 2) {
                    sender.sendMessage("§c사용법: /inventory reset <플레이어>");
                    return true;
                }
                resetPlayerInventory(sender, args[1]);
                break;

            case "info":
                if (args.length < 2) {
                    sender.sendMessage("§c사용법: /inventory info <플레이어>");
                    return true;
                }
                showInventoryInfo(sender, args[1]);
                break;

            default:
                sendHelp(sender);
                break;
        }

        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6=== 인벤토리 관리 명령어 ===");
        sender.sendMessage("§7/inventory sync - 내 인벤토리 강제 동기화");
        sender.sendMessage("§7/inventory save <플레이어> - 플레이어 인벤토리 저장");
        sender.sendMessage("§7/inventory load <플레이어> - 플레이어 인벤토리 로드");
        sender.sendMessage("§7/inventory reset <플레이어> - 플레이어 인벤토리 초기화");
        sender.sendMessage("§7/inventory info <플레이어> - 플레이어 인벤토리 정보");
        sender.sendMessage("§6==========================");
    }

    private void syncInventory(Player player) {
        player.sendMessage("§e인벤토리 동기화를 시작합니다...");

        inventoryManager.forceSyncInventory(player);

        plugin.getLogger().info(String.format("[인벤토리] %s이(가) 강제 동기화를 실행했습니다.",
                player.getName()));
    }

    private void savePlayerInventory(CommandSender sender, String targetName) {
        Player targetPlayer = Bukkit.getPlayer(targetName);

        if (targetPlayer == null || !targetPlayer.isOnline()) {
            sender.sendMessage("§c플레이어를 찾을 수 없습니다: " + targetName);
            return;
        }

        sender.sendMessage("§e" + targetName + "의 인벤토리를 저장 중...");

        inventoryManager.savePlayerInventory(targetPlayer)
                .thenAccept(success -> {
                    if (success) {
                        sender.sendMessage("§a" + targetName + "의 인벤토리가 저장되었습니다.");
                        plugin.getLogger().info(String.format("[인벤토리] %s이(가) %s의 인벤토리를 저장했습니다.",
                                sender.getName(), targetName));
                    } else {
                        sender.sendMessage("§c" + targetName + "의 인벤토리 저장에 실패했습니다.");
                    }
                });
    }

    private void loadPlayerInventory(CommandSender sender, String targetName) {
        Player targetPlayer = Bukkit.getPlayer(targetName);

        if (targetPlayer == null || !targetPlayer.isOnline()) {
            sender.sendMessage("§c플레이어를 찾을 수 없습니다: " + targetName);
            return;
        }

        sender.sendMessage("§e" + targetName + "의 인벤토리를 로드 중...");

        inventoryManager.loadPlayerInventory(targetPlayer)
                .thenAccept(success -> {
                    if (success) {
                        sender.sendMessage("§a" + targetName + "의 인벤토리가 로드되었습니다.");
                        targetPlayer.sendMessage("§a관리자에 의해 인벤토리가 동기화되었습니다.");
                        plugin.getLogger().info(String.format("[인벤토리] %s이(가) %s의 인벤토리를 로드했습니다.",
                                sender.getName(), targetName));
                    } else {
                        sender.sendMessage("§c" + targetName + "의 인벤토리 로드에 실패했습니다.");
                    }
                });
    }

    private void resetPlayerInventory(CommandSender sender, String targetName) {
        Player targetPlayer = Bukkit.getPlayer(targetName);

        if (targetPlayer == null || !targetPlayer.isOnline()) {
            sender.sendMessage("§c플레이어를 찾을 수 없습니다: " + targetName);
            return;
        }

        sender.sendMessage("§e" + targetName + "의 인벤토리를 초기화 중...");

        // UUID 파싱
        UUID targetUuid = targetPlayer.getUniqueId();

        inventoryManager.deletePlayerInventory(targetUuid)
                .thenAccept(success -> {
                    if (success) {
                        // 메인 스레드에서 인벤토리 클리어
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            targetPlayer.getInventory().clear();
                            targetPlayer.getInventory().setArmorContents(null);
                            targetPlayer.setHealth(targetPlayer.getMaxHealth());
                            targetPlayer.setFoodLevel(20);
                            targetPlayer.setSaturation(5.0f);
                            targetPlayer.setLevel(0);
                            targetPlayer.setExp(0.0f);

                            targetPlayer.sendMessage("§c관리자에 의해 인벤토리가 초기화되었습니다.");
                        });

                        sender.sendMessage("§a" + targetName + "의 인벤토리가 초기화되었습니다.");
                        plugin.getLogger().info(String.format("[인벤토리] %s이(가) %s의 인벤토리를 초기화했습니다.",
                                sender.getName(), targetName));
                    } else {
                        sender.sendMessage("§c" + targetName + "의 인벤토리 초기화에 실패했습니다.");
                    }
                });
    }

    private void showInventoryInfo(CommandSender sender, String targetName) {
        Player targetPlayer = Bukkit.getPlayer(targetName);

        if (targetPlayer == null) {
            sender.sendMessage("§c플레이어를 찾을 수 없습니다: " + targetName);
            return;
        }

        // 현재 인벤토리 정보 표시
        sender.sendMessage("§6=== " + targetName + " 인벤토리 정보 ===");
        sender.sendMessage("§7현재 서버: §f" + plugin.getServerDetector().getDisplayName());

        // 아이템 개수 계산
        int itemCount = 0;
        for (int i = 0; i < 36; i++) { // 일반 인벤토리만
            if (targetPlayer.getInventory().getItem(i) != null) {
                itemCount++;
            }
        }

        int armorCount = 0;
        for (int i = 0; i < 4; i++) {
            if (targetPlayer.getInventory().getArmorContents()[i] != null) {
                armorCount++;
            }
        }

        sender.sendMessage("§7인벤토리 아이템: §f" + itemCount + "/36 슬롯");
        sender.sendMessage("§7방어구: §f" + armorCount + "/4 슬롯");
        sender.sendMessage("§7체력: §f" + Math.round(targetPlayer.getHealth()) + "/" + Math.round(targetPlayer.getMaxHealth()));
        sender.sendMessage("§7배고픔: §f" + targetPlayer.getFoodLevel() + "/20");
        sender.sendMessage("§7경험치 레벨: §f" + targetPlayer.getLevel());
        sender.sendMessage("§6=============================");
    }
}