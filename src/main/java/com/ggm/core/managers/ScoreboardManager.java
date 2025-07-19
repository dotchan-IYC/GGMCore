package com.ggm.core.managers;

import com.ggm.core.GGMCore;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.*;

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

    public ScoreboardManager(GGMCore plugin) {
        this.plugin = plugin;
        this.economyManager = plugin.getEconomyManager();
        this.playerScoreboards = new HashMap<>();
        this.timeFormat = new SimpleDateFormat("HH:mm:ss");

        // 주기적 업데이트 시작 (1초마다)
        startUpdateTask();
    }

    /**
     * 플레이어에게 스코어보드 생성 및 설정
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

        // 초기 내용 설정
        updateScoreboard(player);
    }

    /**
     * 플레이어 스코어보드 업데이트
     */
    public void updateScoreboard(Player player) {
        if (!player.isOnline()) return;

        Scoreboard scoreboard = playerScoreboards.get(player.getUniqueId());
        if (scoreboard == null) return;

        Objective objective = scoreboard.getObjective("ggm_info");
        if (objective == null) return;

        // 기존 스코어 제거
        for (String entry : scoreboard.getEntries()) {
            scoreboard.resetScores(entry);
        }

        // G 잔액 비동기로 가져오기
        economyManager.getBalance(player.getUniqueId()).thenAccept(balance -> {
            // 메인 스레드에서 스코어보드 업데이트
            Bukkit.getScheduler().runTask(plugin, () -> {
                updateScoreboardContent(player, objective, balance);
            });
        });
    }

    /**
     * 스코어보드 내용 업데이트 (메인 스레드에서 실행)
     */
    private void updateScoreboardContent(Player player, Objective objective, long balance) {
        if (!player.isOnline()) return;

        int line = 15; // 맨 위부터 시작

        // 빈 줄
        objective.getScore("§f").setScore(line--);

        // 플레이어 정보
        objective.getScore("§e플레이어: §f" + player.getName()).setScore(line--);
        objective.getScore("§a보유 G: §6" + economyManager.formatMoney(balance) + "G").setScore(line--);

        // 빈 줄
        objective.getScore("§7").setScore(line--);

        // 서버 정보
        String serverName = getServerName();
        objective.getScore("§b서버: §f" + serverName).setScore(line--);
        objective.getScore("§d온라인: §f" + Bukkit.getOnlinePlayers().size() + "명").setScore(line--);

        // 빈 줄
        objective.getScore("§8").setScore(line--);

        // 시간 정보
        String currentTime = timeFormat.format(new Date());
        objective.getScore("§7시간: §f" + currentTime).setScore(line--);

        // 빈 줄
        objective.getScore("§9").setScore(line--);

        // 하단 정보
        objective.getScore("§6§l━━━━━━━━━━━━━━").setScore(line--);
        objective.getScore("§7ggm.server.com").setScore(line--);
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
        }
    }

    /**
     * 시간 정보만 업데이트 (깜빡임 방지)
     */
    private void updateTimeOnly(Player player) {
        if (!player.isOnline()) return;

        Scoreboard scoreboard = playerScoreboards.get(player.getUniqueId());
        if (scoreboard == null) return;

        Objective objective = scoreboard.getObjective("ggm_info");
        if (objective == null) return;

        // 기존 시간 스코어만 제거
        String currentTime = timeFormat.format(new Date());
        String timeEntry = "§7시간: §f" + currentTime;

        // 기존 시간 엔트리들 제거
        for (String entry : scoreboard.getEntries()) {
            if (entry.startsWith("§7시간: §f")) {
                scoreboard.resetScores(entry);
            }
        }

        // 새 시간 추가
        objective.getScore(timeEntry).setScore(6); // 시간 위치
    }

    /**
     * 온라인 인원 수만 업데이트
     */
    public void updateOnlineCount() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (playerScoreboards.containsKey(player.getUniqueId())) {
                updateOnlineCountForPlayer(player);
            }
        }
    }

    /**
     * 특정 플레이어의 온라인 인원 수 업데이트
     */
    private void updateOnlineCountForPlayer(Player player) {
        if (!player.isOnline()) return;

        Scoreboard scoreboard = playerScoreboards.get(player.getUniqueId());
        if (scoreboard == null) return;

        Objective objective = scoreboard.getObjective("ggm_info");
        if (objective == null) return;

        // 기존 온라인 인원 스코어만 제거
        for (String entry : scoreboard.getEntries()) {
            if (entry.startsWith("§d온라인: §f")) {
                scoreboard.resetScores(entry);
            }
        }

        // 새 온라인 인원 추가
        String onlineEntry = "§d온라인: §f" + Bukkit.getOnlinePlayers().size() + "명";
        objective.getScore(onlineEntry).setScore(9); // 온라인 인원 위치
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
     * 주기적 업데이트 작업 시작
     */
    private void startUpdateTask() {
        // 설정에서 업데이트 간격 가져오기
        long updateInterval = plugin.getConfig().getLong("scoreboard.update_interval", 100L);
        boolean smoothUpdate = plugin.getConfig().getBoolean("scoreboard.smooth_update", true);

        if (smoothUpdate) {
            // 부드러운 업데이트: 시간 정보만 업데이트
            Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (playerScoreboards.containsKey(player.getUniqueId())) {
                        updateTimeOnly(player);
                    }
                }
            }, updateInterval, updateInterval);
        } else {
            // 기존 방식: 전체 스코어보드 업데이트
            Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (playerScoreboards.containsKey(player.getUniqueId())) {
                        updateScoreboard(player);
                    }
                }
            }, updateInterval, updateInterval);
        }

        plugin.getLogger().info("스코어보드 업데이트 간격: " + (updateInterval / 20.0) + "초, 부드러운 업데이트: " + smoothUpdate);
    }

    /**
     * ActionBar 메시지 전송 (추가 기능)
     */
    public void sendActionBar(Player player, String message) {
        player.sendActionBar(message);
    }

    /**
     * G 변경 알림을 ActionBar로 전송
     */
    public void notifyBalanceChange(Player player, long newBalance, long change) {
        String changeText = change > 0 ? "§a+" : "§c";
        String message = String.format("§6G: %s §8| %s%s G",
                economyManager.formatMoney(newBalance),
                changeText,
                economyManager.formatMoney(Math.abs(change)));

        sendActionBar(player, message);

        // 스코어보드도 즉시 업데이트
        updateScoreboard(player);
    }

    /**
     * 플러그인 종료 시 정리
     */
    public void cleanup() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            removeScoreboard(player);
        }
        playerScoreboards.clear();
    }
}