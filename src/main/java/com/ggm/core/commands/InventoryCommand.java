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

            // 새로 추가된 디버그 명령어들
            case "debug":
                if (args.length < 2) {
                    sender.sendMessage("§c사용법: /inventory debug <플레이어>");
                    return true;
                }
                debugInventoryStatus(sender, args[1]);
                break;

            case "force-save":
                if (args.length < 2) {
                    sender.sendMessage("§c사용법: /inventory force-save <플레이어>");
                    return true;
                }
                forceSaveInventory(sender, args[1]);
                break;

            case "force-load":
                if (args.length < 2) {
                    sender.sendMessage("§c사용법: /inventory force-load <플레이어>");
                    return true;
                }
                forceLoadInventory(sender, args[1]);
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
        sender.sendMessage("§e=== 디버그 명령어 ===");
        sender.sendMessage("§7/inventory debug <플레이어> - DB 상태 확인");
        sender.sendMessage("§7/inventory force-save <플레이어> - 강제 저장");
        sender.sendMessage("§7/inventory force-load <플레이어> - 강제 로드");
        sender.sendMessage("§6==========================");
    }

    // 기존 메소드들...
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

        UUID targetUuid = targetPlayer.getUniqueId();

        inventoryManager.deletePlayerInventory(targetUuid)
                .thenAccept(success -> {
                    if (success) {
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

        sender.sendMessage("§6=== " + targetName + " 인벤토리 정보 ===");
        sender.sendMessage("§7현재 서버: §f" + plugin.getServerDetector().getDisplayName());

        int itemCount = 0;
        for (int i = 0; i < 36; i++) {
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

    // 새로 추가된 디버그 메소드들
    private void debugInventoryStatus(CommandSender sender, String targetName) {
        Player targetPlayer = Bukkit.getPlayer(targetName);

        if (targetPlayer == null) {
            sender.sendMessage("§c플레이어를 찾을 수 없습니다: " + targetName);
            return;
        }

        sender.sendMessage("§6=== " + targetName + " 디버그 정보 ===");
        sender.sendMessage("§7플레이어 UUID: §f" + targetPlayer.getUniqueId());
        sender.sendMessage("§7현재 서버: §f" + plugin.getServerDetector().getDisplayName());
        sender.sendMessage("§7인벤토리 동기화: §f" + (plugin.isInventorySyncEnabled() ? "§a활성화" : "§c비활성화"));

        // DB 연결 상태 확인
        try {
            plugin.getDatabaseManager().getConnection().close();
            sender.sendMessage("§7DB 연결: §a정상");
        } catch (Exception e) {
            sender.sendMessage("§7DB 연결: §c오류 (" + e.getMessage() + ")");
        }

        sender.sendMessage("§e=== 테스트 시도 ===");
        sender.sendMessage("§7저장 테스트 중...");

        inventoryManager.savePlayerInventory(targetPlayer)
                .thenCompose(saveSuccess -> {
                    sender.sendMessage("§7저장 결과: " + (saveSuccess ? "§a성공" : "§c실패"));
                    sender.sendMessage("§7로드 테스트 중...");
                    return inventoryManager.loadPlayerInventory(targetPlayer);
                })
                .thenAccept(loadSuccess -> {
                    sender.sendMessage("§7로드 결과: " + (loadSuccess ? "§a성공" : "§c실패"));
                    sender.sendMessage("§6=== 디버그 완료 ===");
                })
                .exceptionally(throwable -> {
                    sender.sendMessage("§c테스트 중 오류: " + throwable.getMessage());
                    return null;
                });
    }

    private void forceSaveInventory(CommandSender sender, String targetName) {
        Player targetPlayer = Bukkit.getPlayer(targetName);

        if (targetPlayer == null || !targetPlayer.isOnline()) {
            sender.sendMessage("§c플레이어를 찾을 수 없습니다: " + targetName);
            return;
        }

        sender.sendMessage("§e[강제 저장] " + targetName + "의 인벤토리를 강제 저장 중...");

        inventoryManager.savePlayerInventory(targetPlayer)
                .thenAccept(success -> {
                    if (success) {
                        sender.sendMessage("§a[강제 저장] " + targetName + "의 인벤토리가 강제 저장되었습니다.");
                        targetPlayer.sendMessage("§e관리자에 의해 인벤토리가 강제 저장되었습니다.");
                    } else {
                        sender.sendMessage("§c[강제 저장] " + targetName + "의 인벤토리 강제 저장에 실패했습니다.");
                    }
                })
                .exceptionally(throwable -> {
                    sender.sendMessage("§c[강제 저장] 오류: " + throwable.getMessage());
                    return null;
                });
    }

    private void forceLoadInventory(CommandSender sender, String targetName) {
        Player targetPlayer = Bukkit.getPlayer(targetName);

        if (targetPlayer == null || !targetPlayer.isOnline()) {
            sender.sendMessage("§c플레이어를 찾을 수 없습니다: " + targetName);
            return;
        }

        sender.sendMessage("§e[강제 로드] " + targetName + "의 인벤토리를 강제 로드 중...");

        inventoryManager.loadPlayerInventory(targetPlayer)
                .thenAccept(success -> {
                    if (success) {
                        sender.sendMessage("§a[강제 로드] " + targetName + "의 인벤토리가 강제 로드되었습니다.");
                        targetPlayer.sendMessage("§e관리자에 의해 인벤토리가 강제 로드되었습니다.");
                    } else {
                        sender.sendMessage("§c[강제 로드] " + targetName + "의 인벤토리 강제 로드에 실패했습니다.");
                        sender.sendMessage("§7DB에 해당 플레이어의 인벤토리 데이터가 없을 수 있습니다.");
                    }
                })
                .exceptionally(throwable -> {
                    sender.sendMessage("§c[강제 로드] 오류: " + throwable.getMessage());
                    return null;
                });
    }
}