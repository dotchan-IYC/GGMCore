package com.ggm.core.commands;

import com.ggm.core.GGMCore;
import com.ggm.core.managers.JobExperienceManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * 직업 시스템 디버그 및 관리용 명령어
 */
public class JobDebugCommand implements CommandExecutor {

    private final GGMCore plugin;
    private final JobExperienceManager jobExpManager;

    public JobDebugCommand(GGMCore plugin) {
        this.plugin = plugin;
        this.jobExpManager = plugin.getJobExperienceManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("ggm.jobdebug")) {
            sender.sendMessage("§c권한이 없습니다!");
            return true;
        }

        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "status":
            case "상태":
                return handleStatus(sender);

            case "retry":
            case "재연결":
                return handleRetry(sender);

            case "player":
            case "플레이어":
                return handlePlayer(sender, args);

            case "init":
            case "초기화":
                return handleInit(sender, args);

            default:
                sendUsage(sender);
                return true;
        }
    }

    /**
     * 연동 상태 확인
     */
    private boolean handleStatus(CommandSender sender) {
        sender.sendMessage("§6§l=== GGMSurvival 연동 상태 ===");

        // GGMSurvival 플러그인 상태
        org.bukkit.plugin.Plugin ggmSurvival = Bukkit.getPluginManager().getPlugin("GGMSurvival");
        if (ggmSurvival != null) {
            sender.sendMessage("§aGGMSurvival: §f발견됨 (v" + ggmSurvival.getDescription().getVersion() + ")");
            sender.sendMessage("§a활성화 상태: §f" + (ggmSurvival.isEnabled() ? "활성화됨" : "비활성화됨"));
        } else {
            sender.sendMessage("§cGGMSurvival: §f플러그인 없음");
        }

        // 직업 경험치 매니저 상태
        if (jobExpManager != null) {
            sender.sendMessage("§a직업 경험치 매니저: §f활성화됨");
        } else {
            sender.sendMessage("§c직업 경험치 매니저: §f비활성화됨");
        }

        // 스코어보드 매니저 상태
        if (plugin.getScoreboardManager() != null) {
            sender.sendMessage("§a스코어보드 매니저: §f활성화됨");
        } else {
            sender.sendMessage("§c스코어보드 매니저: §f비활성화됨");
        }

        // 서버 타입
        sender.sendMessage("§b서버 타입: §f" + plugin.getServerDetector().getServerType());
        sender.sendMessage("§b야생 서버: §f" + (plugin.getServerDetector().isSurvival() ? "예" : "아니오"));

        return true;
    }

    /**
     * 연동 재시도
     */
    private boolean handleRetry(CommandSender sender) {
        sender.sendMessage("§eGGMSurvival 연동을 재시도합니다...");

        try {
            // JobExperienceManager 연동 재시도
            if (jobExpManager != null) {
                jobExpManager.retryIntegration();
            }

            // ScoreboardManager 연동 재시도
            if (plugin.getScoreboardManager() != null) {
                plugin.getScoreboardManager().retryJobSystemIntegration();
            }

            sender.sendMessage("§a연동 재시도가 완료되었습니다!");

        } catch (Exception e) {
            sender.sendMessage("§c연동 재시도 중 오류가 발생했습니다: " + e.getMessage());
            plugin.getLogger().severe("연동 재시도 실패: " + e.getMessage());
        }

        return true;
    }

    /**
     * 특정 플레이어의 직업 정보 확인
     */
    private boolean handlePlayer(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§c사용법: /jobdebug player <플레이어>");
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage("§c플레이어 '" + args[1] + "'을(를) 찾을 수 없습니다.");
            return true;
        }

        sender.sendMessage("§6§l=== " + target.getName() + "의 직업 정보 ===");

        if (jobExpManager != null) {
            String jobType = jobExpManager.getPlayerJobType(target);
            boolean hasJob = jobExpManager.hasJob(target);

            sender.sendMessage("§b직업 타입: §f" + jobType);
            sender.sendMessage("§b직업 보유: §f" + (hasJob ? "예" : "아니오"));

            if (!jobType.equals("NONE")) {
                int currentExp = jobExpManager.getCurrentJobExp(target.getUniqueId(), jobType);
                int currentLevel = jobExpManager.getCurrentJobLevel(target.getUniqueId(), jobType);
                String expDisplay = jobExpManager.getJobExpDisplay(target.getUniqueId(), jobType);

                sender.sendMessage("§d직업 경험치: §f" + currentExp);
                sender.sendMessage("§a직업 레벨: §f" + currentLevel);
                sender.sendMessage("§e표시 형식: §f" + expDisplay);
            }
        } else {
            sender.sendMessage("§c직업 경험치 매니저가 비활성화되어 있습니다.");
        }

        return true;
    }

    /**
     * 플레이어 직업 경험치 초기화
     */
    private boolean handleInit(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§c사용법: /jobdebug init <플레이어> [직업타입]");
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage("§c플레이어 '" + args[1] + "'을(를) 찾을 수 없습니다.");
            return true;
        }

        if (jobExpManager == null) {
            sender.sendMessage("§c직업 경험치 매니저가 비활성화되어 있습니다.");
            return true;
        }

        String jobType;
        if (args.length >= 3) {
            jobType = args[2].toUpperCase();
        } else {
            jobType = jobExpManager.getPlayerJobType(target);
            if (jobType.equals("NONE")) {
                sender.sendMessage("§c해당 플레이어의 직업을 찾을 수 없습니다. 직업타입을 직접 지정해주세요.");
                return true;
            }
        }

        sender.sendMessage("§e" + target.getName() + "의 " + jobType + " 직업 경험치를 초기화합니다...");

        jobExpManager.initializePlayerJobExp(target.getUniqueId(), jobType)
                .thenRun(() -> {
                    sender.sendMessage("§a" + target.getName() + "의 " + jobType + " 직업 경험치 초기화가 완료되었습니다!");
                    target.sendMessage("§e당신의 " + jobType + " 직업 경험치가 초기화되었습니다!");
                })
                .exceptionally(throwable -> {
                    sender.sendMessage("§c초기화 중 오류가 발생했습니다: " + throwable.getMessage());
                    return null;
                });

        return true;
    }

    /**
     * 사용법 표시
     */
    private void sendUsage(CommandSender sender) {
        sender.sendMessage("§6§l=== 직업 시스템 디버그 명령어 ===");
        sender.sendMessage("§e/jobdebug status §7- 연동 상태 확인");
        sender.sendMessage("§e/jobdebug retry §7- 연동 재시도");
        sender.sendMessage("§e/jobdebug player <플레이어> §7- 플레이어 직업 정보");
        sender.sendMessage("§e/jobdebug init <플레이어> [직업] §7- 직업 경험치 초기화");
    }
}