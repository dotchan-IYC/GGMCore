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
    private final Map<UUID, Scoreboard> playerScoreboards;
    private final SimpleDateFormat timeFormat;

    // 고정 스코어보드 엔트리들 (깜빡거림 방지)
    private final Map<UUID, Map<String, String>> fixedEntries;

    // GGMSurvival 연동을 위한 변수들
    private Plugin ggmSurvival;
    private Object jobManager;
    private Method getPlayerJobMethod;
    private boolean jobSystemAvailable = false;

    public ScoreboardManager(GGMCore plugin) {
        this.plugin = plugin;
        this.economyManager = plugin.getEconomyManager();
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
                    // getPlayerJob 메소드 가져오기
                    Class<?> jobManagerClass = jobManager.getClass();
                    getPlayerJobMethod = jobManagerClass.getMethod("getCachedJob", UUID.class);

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
     * 플레이어의 직업 정보 가져오기
     */
    private String getPlayerJobDisplay(Player player) {
        if (!jobSystemAvailable || jobManager == null || getPlayerJobMethod == null) {
            return "§7직업 없음";
        }

        try {
            Object jobType = getPlayerJobMethod.invoke(jobManager, player.getUniqueId());

            if (jobType != null) {
                // JobType enum의 getDisplayName() 메소드 호출
                Method getDisplayNameMethod = jobType.getClass().getMethod("getDisplayName");
                String displayName = (String) getDisplayNameMethod.invoke(jobType);
                return displayName != null ? displayName : "§7직업 없음";
            }
        } catch (Exception e) {
            plugin.getLogger().warning("직업 정보 조회 실패 (" + player.getName() + "): " + e.getMessage());
        }

        return "§7직업 없음";
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
     * 고정 스코어보드 구조 설정 (깜빡거림 방지)
     */
    private void setupFixedScoreboard(Player player, Objective objective) {
        UUID uuid = player.getUniqueId();
        Map<String, String> entries = fixedEntries.get(uuid);

        // 고정 위치에 빈 엔트리들 생성
        entries.put("blank_top", "§f");
        entries.put("player_name", "§e플레이어: §f" + player.getName());
        entries.put("balance", "§a보유 G: §6로딩중...");
        entries.put("job", "§b직업: §7로딩중...");
        entries.put("blank_mid", "§7");
        entries.put("server", "§d서버: §f" + getServerName());
        entries.put("online", "§c온라인: §f" + Bukkit.getOnlinePlayers().size() + "명");
        entries.put("blank_mid2", "§8");
        entries.put("time", "§7시간: §f" + timeFormat.format(new Date()));
        entries.put("blank_bottom", "§9");
        entries.put("separator", "§6§l━━━━━━━━━━━━━━");
        entries.put("website", "§7ggm.server.com");

        // 고정 스코어 설정 (위에서부터 아래로)
        int score = 11;
        objective.getScore(entries.get("blank_top")).setScore(score--);
        objective.getScore(entries.get("player_name")).setScore(score--);
        objective.getScore(entries.get("balance")).setScore(score--);
        objective.getScore(entries.get("job")).setScore(score--);
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
     * 스코어보드 내용만 업데이트 (위치 고정, 깜빡거림 없음)
     */
    private void updateScoreboardContent(Player player, Objective objective) {
        if (!player.isOnline()) return;

        UUID uuid = player.getUniqueId();
        Map<String, String> entries = fixedEntries.get(uuid);
        if (entries == null) return;

        // G 잔액 비동기로 가져와서 업데이트
        economyManager.getBalance(uuid).thenAccept(balance -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;

                // 잔액 업데이트
                updateScoreEntry(objective, entries, "balance",
                        "§a보유 G: §6" + economyManager.formatMoney(balance) + "G", 9);

                // 직업 정보 업데이트
                String jobDisplay = getPlayerJobDisplay(player);
                updateScoreEntry(objective, entries, "job",
                        "§b직업: " + jobDisplay, 8);

                // 온라인 인원 수 업데이트
                updateScoreEntry(objective, entries, "online",
                        "§c온라인: §f" + Bukkit.getOnlinePlayers().size() + "명", 5);
            });
        }).exceptionally(throwable -> {
            plugin.getLogger().warning("잔액 조회 실패: " + throwable.getMessage());
            return null;
        });
    }

    /**
     * 시간만 업데이트 (1초마다, 깜빡거림 없음)
     */
    public void updateTimeOnly(Player player) {
        if (!player.isOnline()) return;

        UUID uuid = player.getUniqueId();
        Scoreboard scoreboard = playerScoreboards.get(uuid);
        if (scoreboard == null) return;

        Objective objective = scoreboard.getObjective("ggm_info");
        if (objective == null) return;

        Map<String, String> entries = fixedEntries.get(uuid);
        if (entries == null) return;

        // 시간만 업데이트 (고정 위치)
        String currentTime = timeFormat.format(new Date());
        updateScoreEntry(objective, entries, "time",
                "§7시간: §f" + currentTime, 3);
    }

    /**
     * 개별 스코어 엔트리 업데이트 (깜빡거림 방지)
     */
    private void updateScoreEntry(Objective objective, Map<String, String> entries,
                                  String key, String newText, int score) {
        String oldText = entries.get(key);

        if (oldText != null && !oldText.equals(newText)) {
            // 기존 엔트리 제거
            objective.getScoreboard().resetScores(oldText);

            // 새 엔트리 설정
            objective.getScore(newText).setScore(score);

            // 엔트리 맵 업데이트
            entries.put(key, newText);
        }
    }

    /**
     * 플레이어 스코어보드 업데이트 (전체)
     */
    public void updateScoreboard(Player player) {
        if (!player.isOnline()) return;

        Scoreboard scoreboard = playerScoreboards.get(player.getUniqueId());
        if (scoreboard == null) return;

        Objective objective = scoreboard.getObjective("ggm_info");
        if (objective == null) return;

        updateScoreboardContent(player, objective);
    }

    /**
     * 현재 서버 이름 가져오기 (자동 감지)
     */
    private String getServerName() {
        return plugin.getServerDetector().getDisplayName();
    }

    /**
     * 플레이어 스코어보드 제거
     */
    public void removeScoreboard(Player player) {
        UUID uuid = player.getUniqueId();

        if (playerScoreboards.containsKey(uuid)) {
            // 기본 스코어보드로 복원
            player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            playerScoreboards.remove(uuid);
            fixedEntries.remove(uuid);
        }
    }

    /**
     * 모든 플레이어의 온라인 인원 수 업데이트
     */
    public void updateOnlineCount() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            if (playerScoreboards.containsKey(uuid)) {
                Objective objective = playerScoreboards.get(uuid).getObjective("ggm_info");
                Map<String, String> entries = fixedEntries.get(uuid);

                if (objective != null && entries != null) {
                    updateScoreEntry(objective, entries, "online",
                            "§c온라인: §f" + Bukkit.getOnlinePlayers().size() + "명", 5);
                }
            }
        }
    }

    /**
     * 특정 플레이어의 G 잔액 변경 시 스코어보드 즉시 업데이트
     */
    public void updatePlayerBalance(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        if (player != null && player.isOnline()) {
            updateScoreboard(player);
        }
    }

    /**
     * G 변경 알림을 ActionBar + 스코어보드로 전송
     */
    public void notifyBalanceChange(Player player, long newBalance, long change) {
        if (!player.isOnline()) return;

        // ActionBar 메시지 생성
        String changeText;
        String changeColor;
        String actionMessage;

        if (change > 0) {
            changeColor = "§a";
            changeText = "+" + economyManager.formatMoney(change) + "G";
            actionMessage = String.format("§6G: %s §8| %s%s",
                    economyManager.formatMoney(newBalance),
                    changeColor,
                    changeText);
        } else if (change < 0) {
            changeColor = "§c";
            changeText = economyManager.formatMoney(Math.abs(change)) + "G";
            actionMessage = String.format("§6G: %s §8| %s-%s",
                    economyManager.formatMoney(newBalance),
                    changeColor,
                    changeText);
        } else {
            // 변경량이 0이면 현재 잔액만 표시
            actionMessage = "§6G: " + economyManager.formatMoney(newBalance);
        }

        // ActionBar 전송
        sendActionBar(player, actionMessage);

        // 스코어보드 즉시 업데이트 (잔액만)
        UUID uuid = player.getUniqueId();
        Objective objective = playerScoreboards.get(uuid) != null ?
                playerScoreboards.get(uuid).getObjective("ggm_info") : null;
        Map<String, String> entries = fixedEntries.get(uuid);

        if (objective != null && entries != null) {
            updateScoreEntry(objective, entries, "balance",
                    "§a보유 G: §6" + economyManager.formatMoney(newBalance) + "G", 9);
        }

        // 큰 금액 변동시 추가 효과
        if (Math.abs(change) >= 10000) { // 1만G 이상 변동
            if (change > 0) {
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.2f);
            } else {
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_ITEM_BREAK, 0.5f, 0.8f);
            }
        }
    }

    /**
     * ActionBar 메시지 전송
     */
    public void sendActionBar(Player player, String message) {
        try {
            player.sendActionBar(message);
        } catch (Exception e) {
            // ActionBar 전송 실패 시 대체 방법 (채팅)
            player.sendMessage("§8[정보] " + message);
            plugin.getLogger().warning("ActionBar 전송 실패, 채팅으로 대체: " + e.getMessage());
        }
    }

    /**
     * 주기적 업데이트 작업 시작 - 깜빡거림 방지 개선
     */
    private void startUpdateTask() {
        // 시간만 업데이트 (1초마다)
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                UUID uuid = player.getUniqueId();
                if (playerScoreboards.containsKey(uuid)) {
                    updateTimeOnly(player);
                }
            }
        }, 0L, 20L); // 1초마다

        // 전체 정보 업데이트 (30초마다로 변경 - 깜빡거림 최소화)
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                UUID uuid = player.getUniqueId();
                if (playerScoreboards.containsKey(uuid)) {
                    updateScoreboard(player);
                }
            }
        }, 0L, 600L); // 30초마다 (600틱)

        plugin.getLogger().info("스코어보드 시스템 활성화 - 깜빡거림 방지 모드");
    }

    /**
     * 직업이 변경되었을 때 스코어보드 업데이트
     */
    public void updateJobInfo(Player player) {
        if (player.isOnline()) {
            UUID uuid = player.getUniqueId();
            Objective objective = playerScoreboards.get(uuid) != null ?
                    playerScoreboards.get(uuid).getObjective("ggm_info") : null;
            Map<String, String> entries = fixedEntries.get(uuid);

            if (objective != null && entries != null) {
                String jobDisplay = getPlayerJobDisplay(player);
                updateScoreEntry(objective, entries, "job",
                        "§b직업: " + jobDisplay, 8);
            }
        }
    }

    /**
     * 직업 시스템 연동 재시도 (GGMSurvival이 나중에 로드되는 경우)
     */
    public void retryJobSystemIntegration() {
        if (!jobSystemAvailable) {
            initializeJobSystemIntegration();
        }
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
}