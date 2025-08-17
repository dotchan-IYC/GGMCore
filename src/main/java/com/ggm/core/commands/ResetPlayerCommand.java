package com.ggm.core.commands;

import com.ggm.core.GGMCore;
import com.ggm.core.managers.EconomyManager;
import com.ggm.core.managers.InventoryManager;
import com.ggm.core.managers.JobExperienceManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * 플레이어 데이터 완전 초기화 명령어
 */
public class ResetPlayerCommand implements CommandExecutor, TabCompleter {

    private final GGMCore plugin;
    private final EconomyManager economyManager;
    private final InventoryManager inventoryManager;
    private final JobExperienceManager jobExpManager;

    public ResetPlayerCommand(GGMCore plugin) {
        this.plugin = plugin;
        this.economyManager = plugin.getEconomyManager();
        this.inventoryManager = plugin.getInventoryManager();
        this.jobExpManager = plugin.getJobExperienceManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("ggm.reset")) {
            sender.sendMessage("§c권한이 없습니다!");
            return true;
        }

        if (args.length < 1) {
            sendUsage(sender);
            return true;
        }

        // 플레이어 확인
        String targetName = args[0];
        Player target = Bukkit.getPlayer(targetName);
        UUID targetUUID;

        if (target != null) {
            targetUUID = target.getUniqueId();
        } else {
            // 오프라인 플레이어 처리 (UUID 조회)
            targetUUID = getOfflinePlayerUUID(targetName);
            if (targetUUID == null) {
                sender.sendMessage("§c플레이어 '" + targetName + "'을(를) 찾을 수 없습니다.");
                return true;
            }
        }

        // 초기화 타입 확인
        String resetType = args.length > 1 ? args[1].toLowerCase() : "all";

        // 확인 메시지
        sender.sendMessage("§e정말로 " + targetName + "의 데이터를 초기화하시겠습니까?");
        sender.sendMessage("§c초기화 타입: " + resetType);
        sender.sendMessage("§c이 작업은 되돌릴 수 없습니다!");

        // 실제 초기화 실행
        executeReset(sender, targetUUID, targetName, resetType);

        return true;
    }

    /**
     * 실제 초기화 실행
     */
    private void executeReset(CommandSender sender, UUID uuid, String playerName, String resetType) {
        sender.sendMessage("§e" + playerName + "의 데이터 초기화를 시작합니다...");

        boolean success = true;

        try {
            switch (resetType) {
                case "all":
                case "전체":
                    success &= resetEconomy(uuid);
                    success &= resetInventory(uuid);
                    success &= resetJobExperience(uuid);
                    break;

                case "economy":
                case "경제":
                    success &= resetEconomy(uuid);
                    break;

                case "inventory":
                case "인벤토리":
                    success &= resetInventory(uuid);
                    break;

                case "job":
                case "직업":
                    success &= resetJobExperience(uuid);
                    break;

                default:
                    sender.sendMessage("§c알 수 없는 초기화 타입: " + resetType);
                    sender.sendMessage("§c사용 가능: all, economy, inventory, job");
                    return;
            }

            if (success) {
                sender.sendMessage("§a" + playerName + "의 " + resetType + " 데이터 초기화가 완료되었습니다!");

                // 온라인 플레이어면 스코어보드 업데이트
                Player target = Bukkit.getPlayer(uuid);
                if (target != null) {
                    target.sendMessage("§e당신의 데이터가 관리자에 의해 초기화되었습니다.");

                    // 스코어보드 업데이트
                    if (plugin.getScoreboardManager() != null) {
                        plugin.getScoreboardManager().updatePlayerBalance(uuid);
                    }
                }

            } else {
                sender.sendMessage("§c데이터 초기화 중 일부 오류가 발생했습니다.");
            }

        } catch (Exception e) {
            sender.sendMessage("§c데이터 초기화 중 오류가 발생했습니다: " + e.getMessage());
            plugin.getLogger().severe("플레이어 데이터 초기화 실패 (" + playerName + "): " + e.getMessage());
        }
    }

    /**
     * 경제 데이터 초기화
     */
    private boolean resetEconomy(UUID uuid) {
        try {
            long startingMoney = plugin.getConfig().getLong("economy.starting_money", 1000);
            economyManager.setBalance(uuid, startingMoney).get();
            plugin.getLogger().info("경제 데이터 초기화 완료: " + uuid);
            return true;
        } catch (Exception e) {
            plugin.getLogger().severe("경제 데이터 초기화 실패: " + e.getMessage());
            return false;
        }
    }

    /**
     * 인벤토리 데이터 초기화
     */
    private boolean resetInventory(UUID uuid) {
        try {
            if (inventoryManager != null) {
                inventoryManager.deletePlayerInventory(uuid).get();
                plugin.getLogger().info("인벤토리 데이터 초기화 완료: " + uuid);
            }
            return true;
        } catch (Exception e) {
            plugin.getLogger().severe("인벤토리 데이터 초기화 실패: " + e.getMessage());
            return false;
        }
    }

    /**
     * 직업 경험치 데이터 초기화
     */
    private boolean resetJobExperience(UUID uuid) {
        try {
            if (jobExpManager != null) {
                // 모든 직업의 경험치 초기화
                String[] jobTypes = {"TANK", "WARRIOR", "ARCHER", "MINER", "LUMBERJACK", "FISHER"};

                for (String jobType : jobTypes) {
                    jobExpManager.setJobExperience(uuid, jobType, 0).get();
                }

                plugin.getLogger().info("직업 경험치 데이터 초기화 완료: " + uuid);
            }
            return true;
        } catch (Exception e) {
            plugin.getLogger().severe("직업 경험치 데이터 초기화 실패: " + e.getMessage());
            return false;
        }
    }

    /**
     * 오프라인 플레이어 UUID 조회 (간단한 구현)
     */
    private UUID getOfflinePlayerUUID(String playerName) {
        try {
            // 이전에 서버에 접속한 적이 있는 플레이어 찾기
            org.bukkit.OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerName);
            if (offlinePlayer.hasPlayedBefore()) {
                return offlinePlayer.getUniqueId();
            }
        } catch (Exception e) {
            plugin.getLogger().warning("오프라인 플레이어 UUID 조회 실패: " + e.getMessage());
        }
        return null;
    }

    /**
     * 사용법 표시
     */
    private void sendUsage(CommandSender sender) {
        sender.sendMessage("§6§l=== 플레이어 데이터 초기화 ===");
        sender.sendMessage("§e/ggmreset <플레이어> [타입]");
        sender.sendMessage("");
        sender.sendMessage("§b초기화 타입:");
        sender.sendMessage("§7• §fall §7- 모든 데이터 초기화");
        sender.sendMessage("§7• §feconomy §7- 경제 데이터만 초기화");
        sender.sendMessage("§7• §finventory §7- 인벤토리 데이터만 초기화");
        sender.sendMessage("§7• §fjob §7- 직업 경험치 데이터만 초기화");
        sender.sendMessage("");
        sender.sendMessage("§c주의: 이 작업은 되돌릴 수 없습니다!");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // 첫 번째 인수: 플레이어 이름
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase().startsWith(args[0].toLowerCase())) {
                    completions.add(player.getName());
                }
            }
        } else if (args.length == 2) {
            // 두 번째 인수: 초기화 타입
            List<String> types = Arrays.asList("all", "economy", "inventory", "job");
            for (String type : types) {
                if (type.toLowerCase().startsWith(args[1].toLowerCase())) {
                    completions.add(type);
                }
            }
        }

        return completions;
    }
}