package com.ggm.core.managers;

import com.ggm.core.GGMCore;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scoreboard.*;

import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ScoreboardManager {

    private final GGMCore plugin;
    private final EconomyManager economyManager;
    private final JobExperienceManager jobExperienceManager; // 추가
    private final Map<UUID, Scoreboard> playerScoreboards;
    private final SimpleDateFormat timeFormat;

    // 고정 스코어보드 엔트리들 (깜빡거림 방지)
    private final Map<UUID, Map<String, String>> fixedEntries;

    // GGMSurvival 연동을 위한 변수들
    private Plugin ggmSurvival;
    private Object jobManager;
    private Method getPlayerJobMethod;
    private Method getJobLevelMethod;
    private boolean jobSystemAvailable = false;

    public ScoreboardManager(GGMCore plugin) {
        this.plugin = plugin;
        this.economyManager = plugin.getEconomyManager();
        this.jobExperienceManager = plugin.getJobExperienceManager(); // 추가
        this.playerScoreboards = new HashMap<>();
        this.fixedEntries = new HashMap<>();
        this.timeFormat = new SimpleDateFormat("HH:mm:ss");

        // GGMSurvival 플러그인 연동 초기화
        initializeJobSystemIntegration();

        // 주기적 업데이트 시작
        startUpdateTask();
    }

    /**
     * GGMSurvival 직업 시스템과의 연동 초기화
     */
    private void initializeJobSystemIntegration() {
        try {
            ggmSurvival = Bukkit.getPluginManager().getPlugin("GGMSurvival");

            if (ggmSurvival != null && ggmSurvival.isEnabled()) {
                // GGMSurvival의 JobManager 가져오기
                Class<?> ggmSurvivalClass = ggmSurvival.getClass();
                Method getJobManagerMethod = ggmSurvivalClass.getMethod("getJobManager");
                jobManager = getJobManagerMethod.invoke(ggmSurvival);

                if (jobManager != null) {
                    // 필요한 메소드들 가져오기
                    Class<?> jobManagerClass = jobManager.getClass();
                    getPlayerJobMethod = jobManagerClass.getMethod("getCachedJob", UUID.class);
                    getJobLevelMethod = jobManagerClass.getMethod("getJobLevel", org.bukkit.entity.Player.class);

                    jobSystemAvailable = true;
                    plugin.getLogger().info("GGMSurvival 직업 시스템과 연동되었습니다!");
                }
            } else {
                plugin.getLogger().info("GGMSurvival 플러그인이 없거나 비활성화되어 있습니다. 직업 정보는 표시되지 않습니다.");
            }
        } catch (Exception e) {
            plugin.getLogger().warning("GGMSurvival 연동 실패: " + e.getMessage());
            jobSystemAvailable = false;
        }
    }

    /**
     * 직업 시스템 재연동 시도 (GGMSurvival에서 호출)
     */
    public void retryJobSystemIntegration() {
        initializeJobSystemIntegration();

        // 이미 접속한 플레이어들의 스코어보드 업데이트
        for (Player player : Bukkit.getOnlinePlayers()) {
            updateScoreboard(player);
        }
    }

    /**
     * 플레이어 직업 정보 가져오기 (기존 직업 레벨 - GGMSurvival에서)
     */
    private String getPlayerJobInfo(Player player) {
        // 야생 서버가 아니면 직업 정보를 표시하지 않음
        if (!plugin.getServerDetector().isSurvival()) {
            return null; // 직업 정보 숨김
        }

        if (!jobSystemAvailable || jobManager == null) {
            return "§7직업 시스템 없음";
        }

        try {
            Object jobData = getPlayerJobMethod.invoke(jobManager, player.getUniqueId());

            if (jobData != null && !jobData.toString().equals("NONE")) {
                // JobType enum의 메소드들 호출
                Method getDisplayNameMethod = jobData.getClass().getMethod("getDisplayName");
                Method getColorMethod = jobData.getClass().getMethod("getColor");

                String jobName = (String) getDisplayNameMethod.invoke(jobData);
                String jobColor = (String) getColorMethod.invoke(jobData);

                // 기존 레벨 정보 가져오기 (GGMSurvival에서 관리)
                int level = (Integer) getJobLevelMethod.invoke(jobManager, player);

                if (level >= 10) {
                    return jobColor + "★" + jobName + " §6만렙";
                } else {
                    return jobColor + jobName + " §fLv." + level;
                }
            } else {
                return "§7직업 없음";
            }
        } catch (Exception e) {
            plugin.getLogger().warning("직업 정보 조회 실패 (" + player.getName() + "): " + e.getMessage());
        }

        return "§7직업 없음";
    }

    /**
     * 플레이어 직업 경험치 정보 가져오기 (새로운 직업 경험치 시스템)
     */
    private String getPlayerJobExpInfo(Player player) {
        // 야생 서버가 아니면 직업 경험치 정보를 표시하지 않음
        if (!plugin.getServerDetector().isSurvival() || jobExperienceManager == null) {
            return null;
        }

        try {
            // 플레이어의 현재 직업 타입 가져오기
            String jobType = getPlayerJobTypeString(player);

            if (jobType != null && !jobType.equals("NONE")) {
                return jobExperienceManager.getJobExpDisplay(player.getUniqueId(), jobType);
            }

        } catch (Exception e) {
            plugin.getLogger().warning("직업 경험치 정보 조회 실패 (" + player.getName() + "): " + e.getMessage());
        }

        return "§7경험치 없음";
    }

    /**
     * 플레이어의 직업 타입을 문자열로 반환
     */
    private String getPlayerJobTypeString(Player player) {
        if (jobExperienceManager != null) {
            return jobExperienceManager.getPlayerJobType(player);
        }

        // JobExperienceManager가 없으면 기존 방식 사용
        if (!jobSystemAvailable || jobManager == null) {
            return "NONE";
        }

        try {
            Object jobData = getPlayerJobMethod.invoke(jobManager, player.getUniqueId());

            if (jobData != null && !jobData.toString().equals("NONE")) {
                return jobData.toString(); // JobType enum의 name() 반환
            }
        } catch (Exception e) {
            plugin.getLogger().warning("직업 타입 조회 실패: " + e.getMessage());
        }

        return "NONE";
    }

    /**
     * 플레이어에게 스코어보드 생성 및 설정 - 고정 위치 방식
     */
    public void createScoreboard(Player player) {
        // 기존 스코어보드 제거
        removeScoreboard(player);

        // 새 스코어보드 생성
        org.bukkit.scoreboard.ScoreboardManager manager = Bukkit.getScoreboardManager();
        Scoreboard scoreboard = manager.getNewScoreboard();

        // Objective 생성 (사이드바에 표시)
        Objective objective = scoreboard.registerNewObjective("ggm_info", "dummy", "§6§l◆ GGM 서버 ◆");
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        // 플레이어에게 스코어보드 적용
        player.setScoreboard(scoreboard);
        playerScoreboards.put(player.getUniqueId(), scoreboard);

        // 고정 엔트리 맵 초기화
        fixedEntries.put(player.getUniqueId(), new HashMap<>());

        // 초기 스코어보드 설정 (고정 위치)
        setupFixedScoreboard(player, objective);

        // 초기 내용 설정
        updateScoreboardContent(player, objective);
    }

    /**
     * 고정 스코어보드 구조 설정 (깜빡거림 방지) - 직업 경험치 추가
     */
    private void setupFixedScoreboard(Player player, Objective objective) {
        UUID uuid = player.getUniqueId();
        Map<String, String> entries = fixedEntries.get(uuid);

        boolean isSurvival = plugin.getServerDetector().isSurvival();

        // 고정 위치에 빈 엔트리들 생성
        entries.put("blank_top", "§f");
        entries.put("player_name", "§e플레이어: §f" + player.getName());
        entries.put("balance", "§a보유 G: §6로딩중...");

        // 야생 서버에서만 직업 정보 표시
        if (isSurvival) {
            entries.put("job", "§b직업: §7로딩중...");
            entries.put("job_exp", "§d직업 EXP: §7로딩중..."); // 새로 추가
            entries.put("blank_mid", "§7");
        } else {
            entries.put("blank_mid", "§7");
        }

        entries.put("server", "§d서버: §f" + getServerName());
        entries.put("online", "§c온라인: §f" + Bukkit.getOnlinePlayers().size() + "명");
        entries.put("blank_mid2", "§8");
        entries.put("time", "§7시간: §f" + timeFormat.format(new Date()));
        entries.put("blank_bottom", "§9");
        entries.put("separator", "§6§l━━━━━━━━━━━━━━");
        entries.put("website", "§7gyeonggigamemeisterhighschool.duckdns.org");

        // 고정 스코어 설정 (위에서부터 아래로) - 직업 경험치 추가로 인한 조정
        int score = isSurvival ? 13 : 11; // 야생 서버는 직업 정보와 직업 경험치 때문에 두 줄 더

        objective.getScore(entries.get("blank_top")).setScore(score--);
        objective.getScore(entries.get("player_name")).setScore(score--);
        objective.getScore(entries.get("balance")).setScore(score--);

        // 야생 서버에서만 직업 정보 추가
        if (isSurvival) {
            objective.getScore(entries.get("job")).setScore(score--);
            objective.getScore(entries.get("job_exp")).setScore(score--); // 직업 경험치 추가
        }

        objective.getScore(entries.get("blank_mid")).setScore(score--);
        objective.getScore(entries.get("server")).setScore(score--);
        objective.getScore(entries.get("online")).setScore(score--);
        objective.getScore(entries.get("blank_mid2")).setScore(score--);
        objective.getScore(entries.get("time")).setScore(score--);
        objective.getScore(entries.get("blank_bottom")).setScore(score--);
        objective.getScore(entries.get("separator")).setScore(score--);
        objective.getScore(entries.get("website")).setScore(score--);
    }

    /**
     * 스코어보드 내용 업데이트 - 직업 경험치 정보 추가
     */
    private void updateScoreboardContent(Player player, Objective objective) {
        UUID uuid = player.getUniqueId();
        Map<String, String> entries = fixedEntries.get(uuid);
        if (entries == null) return;

        boolean isSurvival = plugin.getServerDetector().isSurvival();

        // G 잔액 업데이트 (비동기)
        economyManager.getBalance(uuid).thenAccept(balance -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                updateScoreEntry(objective, entries, "balance",
                        "§a보유 G: §6" + economyManager.formatMoney(balance) + "G",
                        isSurvival ? 11 : 9);
            });
        }).exceptionally(throwable -> {
            plugin.getLogger().warning("잔액 조회 실패 (" + uuid + "): " + throwable.getMessage());
            return null;
        });

        // 야생 서버에서만 직업 정보 업데이트
        if (isSurvival) {
            // 기존 직업 정보 (GGMSurvival에서 관리하는 레벨)
            String jobInfo = getPlayerJobInfo(player);
            if (jobInfo != null) {
                updateScoreEntry(objective, entries, "job",
                        "§b직업: " + jobInfo, 10);
            }

            // 직업 경험치 정보 (새로운 시스템)
            String jobExpInfo = getPlayerJobExpInfo(player);
            if (jobExpInfo != null) {
                updateScoreEntry(objective, entries, "job_exp",
                        "§d직업 EXP: " + jobExpInfo, 9);
            }
        }

        // 온라인 인원 수 업데이트
        updateScoreEntry(objective, entries, "online",
                "§c온라인: §f" + Bukkit.getOnlinePlayers().size() + "명",
                isSurvival ? 6 : 7);

        // 시간 업데이트
        String currentTime = timeFormat.format(new Date());
        updateScoreEntry(objective, entries, "time",
                "§7시간: §f" + currentTime,
                isSurvival ? 5 : 6);
    }

    /**
     * 개별 스코어 엔트리 업데이트 (깜빡거림 방지)
     */
    private void updateScoreEntry(Objective objective, Map<String, String> entries,
                                  String key, String newText, int score) {
        String currentText = entries.get(key);

        if (currentText != null && !currentText.equals(newText)) {
            // 기존 엔트리 제거
            objective.getScoreboard().resetScores(currentText);

            // 새 엔트리 추가
            objective.getScore(newText).setScore(score);

            // 캐시 업데이트
            entries.put(key, newText);
        }
    }

    /**
     * 서버 이름 가져오기
     */
    private String getServerName() {
        String serverType = plugin.getServerDetector().getServerType();
        switch (serverType.toLowerCase()) {
            case "lobby":
                return "§e로비";
            case "survival":
                return "§a야생";
            case "creative":
                return "§b건축";
            case "towny":
                return "§6마을";
            default:
                return "§f" + serverType;
        }
    }

    /**
     * 주기적 업데이트 작업 시작
     */
    private void startUpdateTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                updateScoreboard(player);
            }
        }, 20L, 20L); // 1초마다 업데이트
    }

    /**
     * 플레이어 스코어보드 업데이트
     */
    public void updateScoreboard(Player player) {
        Scoreboard scoreboard = playerScoreboards.get(player.getUniqueId());
        if (scoreboard == null) return;

        Objective objective = scoreboard.getObjective("ggm_info");
        if (objective == null) return;

        updateScoreboardContent(player, objective);
    }

    /**
     * 모든 플레이어의 온라인 인원 수 업데이트
     */
    public void updateOnlineCount() {
        int onlineCount = Bukkit.getOnlinePlayers().size();
        boolean isSurvival = plugin.getServerDetector().isSurvival();

        for (Player player : Bukkit.getOnlinePlayers()) {
            Scoreboard scoreboard = playerScoreboards.get(player.getUniqueId());
            if (scoreboard == null) continue;

            Objective objective = scoreboard.getObjective("ggm_info");
            if (objective == null) continue;

            Map<String, String> entries = fixedEntries.get(player.getUniqueId());
            if (entries == null) continue;

            updateScoreEntry(objective, entries, "online",
                    "§c온라인: §f" + onlineCount + "명",
                    isSurvival ? 6 : 7);
        }
    }

    /**
     * 스코어보드 제거
     */
    public void removeScoreboard(Player player) {
        UUID uuid = player.getUniqueId();
        playerScoreboards.remove(uuid);
        fixedEntries.remove(uuid);

        // 기본 스코어보드로 복원
        player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
    }

    /**
     * 정리 작업
     */
    public void cleanup() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            removeScoreboard(player);
        }
        playerScoreboards.clear();
        fixedEntries.clear();
    }

    /**
     * 플레이어 잔액 업데이트 (EconomyManager에서 호출)
     */
    public void updatePlayerBalance(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        if (player != null && player.isOnline()) {
            updateScoreboard(player);
        }
    }

    /**
     * 잔액 변경 알림 (EconomyManager에서 호출)
     */
    public void notifyBalanceChange(Player player, long newBalance, long change) {
        // 스코어보드 업데이트
        updateScoreboard(player);

        // 액션바로 잔액 변경 알림 (선택적)
        if (change > 0) {
            player.sendActionBar("§a+ " + plugin.getEconomyManager().formatMoney(change) + "G §7(잔액: §6" +
                    plugin.getEconomyManager().formatMoney(newBalance) + "G§7)");
        } else if (change < 0) {
            player.sendActionBar("§c- " + plugin.getEconomyManager().formatMoney(Math.abs(change)) + "G §7(잔액: §6" +
                    plugin.getEconomyManager().formatMoney(newBalance) + "G§7)");
        }
    }
}