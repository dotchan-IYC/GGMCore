package com.ggm.core.commands;

import com.ggm.core.GGMCore;
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
 * 직업 경험치 관리 명령어
 */
public class JobExpCommand implements CommandExecutor, TabCompleter {

    private final GGMCore plugin;
    private final JobExperienceManager jobExpManager;

    public JobExpCommand(GGMCore plugin) {
        this.plugin = plugin;
        this.jobExpManager = plugin.getJobExperienceManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // 야생 서버가 아니면 사용 불가
        if (!plugin.getServerDetector().isSurvival()) {
            sender.sendMessage("§c이 명령어는 야생 서버에서만 사용할 수 있습니다!");
            return true;
        }

        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "check":
            case "확인":
                return handleCheck(sender, args);

            case "give":
            case "지급":
                return handleGive(sender, args);

            case "set":
            case "설정":
                return handleSet(sender, args);

            case "add":
            case "추가":
                return handleAdd(sender, args);

            case "info":
            case "정보":
                return handleInfo(sender, args);

            default:
                sendUsage(sender);
                return true;
        }
    }

    /**
     * 직업 경험치 확인
     */
    private boolean handleCheck(CommandSender sender, String[] args) {
        if (args.length < 2) {
            if (sender instanceof Player player) {
                // 자신의 직업 경험치 확인
                showPlayerJobExp(sender, player);
            } else {
                sender.sendMessage("§c콘솔에서는 플레이어를 지정해야 합니다: /jobexp check <플레이어>");
            }
            return true;
        }

        // 다른 플레이어의 직업 경험치 확인
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage("§c플레이어 '" + args[1] + "'을(를) 찾을 수 없습니다.");
            return true;
        }

        showPlayerJobExp(sender, target);
        return true;
    }

    /**
     * 직업 경험치 지급 (OP 전용)
     */
    private boolean handleGive(CommandSender sender, String[] args) {
        if (!sender.hasPermission("ggm.jobexp.admin")) {
            sender.sendMessage("§c권한이 없습니다!");
            return true;
        }

        if (args.length < 4) {
            sender.sendMessage("§c사용법: /jobexp give <플레이어> <직업타입> <경험치>");
            sender.sendMessage("§c직업타입: TANK, WARRIOR, ARCHER, MINER, LUMBERJACK, FISHER");
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage("§c플레이어 '" + args[1] + "'을(를) 찾을 수 없습니다.");
            return true;
        }

        String jobType = args[2].toUpperCase();
        if (!isValidJobType(jobType)) {
            sender.sendMessage("§c잘못된 직업 타입입니다. 사용 가능: TANK, WARRIOR, ARCHER, MINER, LUMBERJACK");
            return true;
        }

        try {
            int expAmount = Integer.parseInt(args[3]);
            if (expAmount <= 0) {
                sender.sendMessage("§c경험치는 1 이상이어야 합니다.");
                return true;
            }

            // 직업 경험치 지급
            jobExpManager.addJobExperience(target.getUniqueId(), jobType, "ADMIN_GIVE", expAmount)
                    .thenAccept(success -> {
                        if (success) {
                            sender.sendMessage(String.format("§a%s님에게 %s 직업 경험치 %d를 지급했습니다!",
                                    target.getName(), getJobDisplayName(jobType), expAmount));
                            target.sendMessage(String.format("§e관리자로부터 %s 직업 경험치 %d를 받았습니다!",
                                    getJobDisplayName(jobType), expAmount));
                        } else {
                            sender.sendMessage("§c직업 경험치 지급에 실패했습니다.");
                        }
                    });

        } catch (NumberFormatException e) {
            sender.sendMessage("§c경험치는 숫자여야 합니다.");
        }

        return true;
    }

    /**
     * 직업 경험치 설정 (OP 전용)
     */
    private boolean handleSet(CommandSender sender, String[] args) {
        if (!sender.hasPermission("ggm.jobexp.admin")) {
            sender.sendMessage("§c권한이 없습니다!");
            return true;
        }

        if (args.length < 4) {
            sender.sendMessage("§c사용법: /jobexp set <플레이어> <직업타입> <경험치>");
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage("§c플레이어 '" + args[1] + "'을(를) 찾을 수 없습니다.");
            return true;
        }

        String jobType = args[2].toUpperCase();
        if (!isValidJobType(jobType)) {
            sender.sendMessage("§c잘못된 직업 타입입니다.");
            return true;
        }

        try {
            int expAmount = Integer.parseInt(args[3]);
            if (expAmount < 0) {
                sender.sendMessage("§c경험치는 0 이상이어야 합니다.");
                return true;
            }

            // 직업 경험치 설정
            jobExpManager.setJobExperience(target.getUniqueId(), jobType, expAmount)
                    .thenAccept(success -> {
                        if (success) {
                            sender.sendMessage(String.format("§a%s님의 %s 직업 경험치를 %d로 설정했습니다!",
                                    target.getName(), getJobDisplayName(jobType), expAmount));
                            target.sendMessage(String.format("§e%s 직업 경험치가 %d로 설정되었습니다!",
                                    getJobDisplayName(jobType), expAmount));
                        } else {
                            sender.sendMessage("§c직업 경험치 설정에 실패했습니다.");
                        }
                    });

        } catch (NumberFormatException e) {
            sender.sendMessage("§c경험치는 숫자여야 합니다.");
        }

        return true;
    }

    /**
     * 직업 경험치 추가 (간편 지급)
     */
    private boolean handleAdd(CommandSender sender, String[] args) {
        if (!sender.hasPermission("ggm.jobexp.admin")) {
            sender.sendMessage("§c권한이 없습니다!");
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage("§c사용법: /jobexp add <플레이어> <경험치> [직업타입]");
            sender.sendMessage("§7직업타입을 생략하면 현재 직업에 지급됩니다.");
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage("§c플레이어 '" + args[1] + "'을(를) 찾을 수 없습니다.");
            return true;
        }

        try {
            int expAmount = Integer.parseInt(args[2]);
            if (expAmount <= 0) {
                sender.sendMessage("§c경험치는 1 이상이어야 합니다.");
                return true;
            }

            String jobType;
            if (args.length >= 4) {
                jobType = args[3].toUpperCase();
                if (!isValidJobType(jobType)) {
                    sender.sendMessage("§c잘못된 직업 타입입니다.");
                    return true;
                }
            } else {
                // 현재 직업 가져오기 (GGMSurvival 연동 필요)
                jobType = getCurrentPlayerJob(target);
                if (jobType == null || jobType.equals("NONE")) {
                    sender.sendMessage("§c해당 플레이어의 직업을 찾을 수 없습니다. 직업타입을 직접 지정해주세요.");
                    return true;
                }
            }

            // 현재 경험치에 추가
            int currentExp = jobExpManager.getCurrentJobExp(target.getUniqueId(), jobType);
            int newExp = currentExp + expAmount;

            jobExpManager.setJobExperience(target.getUniqueId(), jobType, newExp)
                    .thenAccept(success -> {
                        if (success) {
                            sender.sendMessage(String.format("§a%s님에게 %s 직업 경험치 %d를 추가했습니다! (총 %d)",
                                    target.getName(), getJobDisplayName(jobType), expAmount, newExp));
                        } else {
                            sender.sendMessage("§c직업 경험치 추가에 실패했습니다.");
                        }
                    });

        } catch (NumberFormatException e) {
            sender.sendMessage("§c경험치는 숫자여야 합니다.");
        }

        return true;
    }

    /**
     * 직업 경험치 시스템 정보
     */
    private boolean handleInfo(CommandSender sender, String[] args) {
        sender.sendMessage("§6§l=== 직업 경험치 시스템 정보 ===");
        sender.sendMessage("§e일반 경험치: §f인첸트 테이블 사용");
        sender.sendMessage("§d직업 경험치: §f직업 레벨업 전용");
        sender.sendMessage("");
        sender.sendMessage("§b직업별 경험치 획득 방법:");
        sender.sendMessage("§7• §a탱커: §f몬스터 처치, 피해 받기, 방패 사용");
        sender.sendMessage("§7• §c검사: §f근접 공격으로 처치, 크리티컬 히트");
        sender.sendMessage("§7• §e궁수: §f활로 처치, 장거리 사격, 헤드샷");
        sender.sendMessage("§7• §9광부: §f광석 채굴, 돌 채굴");
        sender.sendMessage("§7• §2나무꾼: §f나무 벌목");
        sender.sendMessage("§7• §b어부: §f물고기 낚기, 보물 낚기");
        sender.sendMessage("");
        sender.sendMessage("§6레벨업 필요 경험치:");
        sender.sendMessage("§fLv.1→2: 100 | Lv.2→3: 300 | Lv.3→4: 600");
        sender.sendMessage("§fLv.4→5: 1000 | Lv.5→6: 1500 | Lv.6→7: 2100");
        sender.sendMessage("§fLv.7→8: 2800 | Lv.8→9: 3600 | Lv.9→10: 5000");
        return true;
    }

    /**
     * 플레이어의 직업 경험치 정보 표시
     */
    private void showPlayerJobExp(CommandSender sender, Player target) {
        sender.sendMessage("§6§l=== " + target.getName() + "의 직업 경험치 ===");

        // 현재 직업 확인
        String currentJob = getCurrentPlayerJob(target);
        if (currentJob == null || currentJob.equals("NONE")) {
            sender.sendMessage("§c해당 플레이어는 직업이 없습니다.");
            return;
        }

        UUID uuid = target.getUniqueId();

        // 현재 직업의 경험치 정보
        int currentExp = jobExpManager.getCurrentJobExp(uuid, currentJob);
        int currentLevel = jobExpManager.getCurrentJobLevel(uuid, currentJob);
        String expDisplay = jobExpManager.getJobExpDisplay(uuid, currentJob);

        sender.sendMessage(String.format("§b현재 직업: §f%s", getJobDisplayName(currentJob)));
        sender.sendMessage(String.format("§d직업 경험치: §f%s", expDisplay));
        sender.sendMessage(String.format("§e총 경험치: §f%d", currentExp));
        sender.sendMessage(String.format("§a현재 레벨: §f%d", currentLevel));

        if (currentLevel < 10) {
            int nextLevelExp = jobExpManager.getExpRequiredForNextLevel(currentLevel);
            int neededExp = nextLevelExp - currentExp;
            sender.sendMessage(String.format("§7다음 레벨까지: §f%d 경험치", neededExp));
        } else {
            sender.sendMessage("§6★ 만렙 달성!");
        }
    }

    /**
     * 사용법 표시
     */
    private void sendUsage(CommandSender sender) {
        sender.sendMessage("§6§l=== 직업 경험치 명령어 ===");
        sender.sendMessage("§e/jobexp check [플레이어] §7- 직업 경험치 확인");
        sender.sendMessage("§e/jobexp info §7- 시스템 정보");

        if (sender.hasPermission("ggm.jobexp.admin")) {
            sender.sendMessage("§c관리자 명령어:");
            sender.sendMessage("§e/jobexp give <플레이어> <직업> <경험치> §7- 경험치 지급");
            sender.sendMessage("§e/jobexp set <플레이어> <직업> <경험치> §7- 경험치 설정");
            sender.sendMessage("§e/jobexp add <플레이어> <경험치> [직업] §7- 경험치 추가");
        }
    }

    /**
     * 유효한 직업 타입인지 확인
     */
    private boolean isValidJobType(String jobType) {
        return Arrays.asList("TANK", "WARRIOR", "ARCHER", "MINER", "LUMBERJACK", "FISHER").contains(jobType);
    }

    /**
     * 직업 표시 이름 가져오기
     */
    private String getJobDisplayName(String jobType) {
        switch (jobType) {
            case "TANK": return "탱커";
            case "WARRIOR": return "검사";
            case "ARCHER": return "궁수";
            case "MINER": return "광부";
            case "LUMBERJACK": return "나무꾼";
            case "FISHER": return "어부";
            default: return jobType;
        }
    }

    /**
     * 플레이어의 현재 직업 가져오기 (GGMSurvival 연동)
     */
    private String getCurrentPlayerJob(Player player) {
        return jobExpManager.getPlayerJobType(player);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // 첫 번째 인수: 하위 명령어
            List<String> subCommands = Arrays.asList("check", "info");
            if (sender.hasPermission("ggm.jobexp.admin")) {
                subCommands = Arrays.asList("check", "give", "set", "add", "info");
            }

            for (String cmd : subCommands) {
                if (cmd.toLowerCase().startsWith(args[0].toLowerCase())) {
                    completions.add(cmd);
                }
            }
        } else if (args.length == 2) {
            // 두 번째 인수: 플레이어 이름
            if (Arrays.asList("check", "give", "set", "add").contains(args[0].toLowerCase())) {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                        completions.add(player.getName());
                    }
                }
            }
        } else if (args.length == 3) {
            // 세 번째 인수: 직업 타입 (give, set) 또는 경험치 (add)
            if (Arrays.asList("give", "set").contains(args[0].toLowerCase())) {
                List<String> jobTypes = Arrays.asList("TANK", "WARRIOR", "ARCHER", "MINER", "LUMBERJACK", "FISHER");
                for (String job : jobTypes) {
                    if (job.toLowerCase().startsWith(args[2].toLowerCase())) {
                        completions.add(job);
                    }
                }
            }
        } else if (args.length == 4) {
            // 네 번째 인수: 직업 타입 (add 명령어)
            if ("add".equals(args[0].toLowerCase())) {
                List<String> jobTypes = Arrays.asList("TANK", "WARRIOR", "ARCHER", "MINER", "LUMBERJACK", "FISHER");
                for (String job : jobTypes) {
                    if (job.toLowerCase().startsWith(args[3].toLowerCase())) {
                        completions.add(job);
                    }
                }
            }
        }

        return completions;
    }
}