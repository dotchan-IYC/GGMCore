package com.ggm.core.commands;

import com.ggm.core.GGMCore;
import com.ggm.core.managers.JobExperienceManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * 커스텀 인첸트 및 직업 시스템 테스트 명령어
 */
public class TestCommand implements CommandExecutor {

    private final GGMCore plugin;
    private final JobExperienceManager jobExpManager;

    public TestCommand(GGMCore plugin) {
        this.plugin = plugin;
        this.jobExpManager = plugin.getJobExperienceManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("ggm.test")) {
            sender.sendMessage("§c권한이 없습니다!");
            return true;
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage("§c이 명령어는 플레이어만 사용할 수 있습니다!");
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            sendUsage(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "job":
            case "직업":
                return handleJobTest(player, args);

            case "exp":
            case "경험치":
                return handleExpTest(player, args);

            case "system":
            case "시스템":
                return handleSystemTest(player);

            default:
                sendUsage(player);
                return true;
        }
    }

    /**
     * 직업 시스템 테스트
     */
    private boolean handleJobTest(Player player, String[] args) {
        if (jobExpManager == null) {
            player.sendMessage("§c직업 경험치 시스템이 비활성화되어 있습니다!");
            return true;
        }

        String jobType = jobExpManager.getPlayerJobType(player);
        boolean hasJob = jobExpManager.hasJob(player);

        player.sendMessage("§6§l=== 직업 시스템 테스트 ===");
        player.sendMessage("§b현재 직업: §f" + jobType);
        player.sendMessage("§b직업 보유: §f" + (hasJob ? "예" : "아니오"));

        if (!jobType.equals("NONE")) {
            int currentExp = jobExpManager.getCurrentJobExp(player.getUniqueId(), jobType);
            int currentLevel = jobExpManager.getCurrentJobLevel(player.getUniqueId(), jobType);
            String expDisplay = jobExpManager.getJobExpDisplay(player.getUniqueId(), jobType);

            player.sendMessage("§d직업 경험치: §f" + currentExp);
            player.sendMessage("§a직업 레벨: §f" + currentLevel);
            player.sendMessage("§e표시 형식: §f" + expDisplay);
        }

        return true;
    }

    /**
     * 경험치 시스템 테스트
     */
    private boolean handleExpTest(Player player, String[] args) {
        if (jobExpManager == null) {
            player.sendMessage("§c직업 경험치 시스템이 비활성화되어 있습니다!");
            return true;
        }

        String jobType = jobExpManager.getPlayerJobType(player);
        if (jobType.equals("NONE")) {
            player.sendMessage("§c먼저 직업을 가져야 합니다!");
            return true;
        }

        player.sendMessage("§e테스트 경험치를 지급합니다...");

        // 테스트 경험치 지급
        jobExpManager.addJobExperience(player.getUniqueId(), jobType, "ADMIN_TEST", 50)
                .thenAccept(success -> {
                    if (success) {
                        player.sendMessage("§a테스트 경험치 50이 지급되었습니다!");
                    } else {
                        player.sendMessage("§c테스트 경험치 지급에 실패했습니다.");
                    }
                });

        return true;
    }

    /**
     * 전체 시스템 테스트
     */
    private boolean handleSystemTest(Player player) {
        player.sendMessage("§6§l=== 시스템 상태 테스트 ===");

        // 서버 타입 확인
        if (plugin.getServerDetector() != null) {
            player.sendMessage("§a서버 감지기: §f활성화됨");
            player.sendMessage("§b서버 타입: §f" + plugin.getServerDetector().getServerType());
            player.sendMessage("§b야생 서버: §f" + (plugin.getServerDetector().isSurvival() ? "예" : "아니오"));
        } else {
            player.sendMessage("§c서버 감지기: 비활성화됨");
        }

        // 경제 시스템 확인
        if (plugin.getEconomyManager() != null) {
            player.sendMessage("§a경제 시스템: §f활성화됨");
            plugin.getEconomyManager().getBalance(player.getUniqueId())
                    .thenAccept(balance -> {
                        player.sendMessage("§a현재 잔액: §6" + balance + "G");
                    });
        } else {
            player.sendMessage("§c경제 시스템: 비활성화됨");
        }

        // 직업 경험치 시스템 확인
        if (jobExpManager != null) {
            player.sendMessage("§a직업 경험치 시스템: §f활성화됨");
        } else {
            player.sendMessage("§c직업 경험치 시스템: 비활성화됨");
        }

        // 스코어보드 시스템 확인
        if (plugin.getScoreboardManager() != null) {
            player.sendMessage("§a스코어보드 시스템: §f활성화됨");
        } else {
            player.sendMessage("§c스코어보드 시스템: 비활성화됨");
        }

        // 인벤토리 동기화 확인
        if (plugin.getInventoryManager() != null) {
            player.sendMessage("§a인벤토리 동기화: §f활성화됨");
        } else {
            player.sendMessage("§c인벤토리 동기화: 비활성화됨");
        }

        return true;
    }

    /**
     * 사용법 표시
     */
    private void sendUsage(Player player) {
        player.sendMessage("§6§l=== 테스트 명령어 ===");
        player.sendMessage("§e/test job §7- 직업 시스템 테스트");
        player.sendMessage("§e/test exp §7- 경험치 시스템 테스트 (50 경험치 지급)");
        player.sendMessage("§e/test system §7- 전체 시스템 상태 확인");
    }
}