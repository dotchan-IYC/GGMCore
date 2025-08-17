package com.ggm.core.utils;

import com.ggm.core.GGMCore;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * GGMSurvival과의 연동을 담당하는 헬퍼 클래스
 * 직업 정보를 안전하게 가져오고 직업 경험치 시스템과 연결
 */
public class JobIntegrationHelper {

    private final GGMCore plugin;
    private Plugin ggmSurvival;
    private Object jobManager;

    // GGMSurvival의 메소드들
    private Method getPlayerJobMethod;
    private Method getJobLevelMethod;
    private Method setJobLevelMethod;
    private Method hasJobMethod;

    private boolean isAvailable = false;

    public JobIntegrationHelper(GGMCore plugin) {
        this.plugin = plugin;
        initializeIntegration();
    }

    /**
     * GGMSurvival과의 연동 초기화
     */
    private void initializeIntegration() {
        try {
            ggmSurvival = Bukkit.getPluginManager().getPlugin("GGMSurvival");

            if (ggmSurvival != null && ggmSurvival.isEnabled()) {
                // GGMSurvival의 JobManager 가져오기
                Class<?> ggmSurvivalClass = ggmSurvival.getClass();
                Method getJobManagerMethod = ggmSurvivalClass.getMethod("getJobManager");
                jobManager = getJobManagerMethod.invoke(ggmSurvival);

                if (jobManager != null) {
                    // JobManager의 메소드들 가져오기
                    Class<?> jobManagerClass = jobManager.getClass();

                    getPlayerJobMethod = jobManagerClass.getMethod("getCachedJob", UUID.class);
                    getJobLevelMethod = jobManagerClass.getMethod("getJobLevel", Player.class);
                    setJobLevelMethod = jobManagerClass.getMethod("setJobLevel", Player.class, int.class);
                    hasJobMethod = jobManagerClass.getMethod("hasJob", UUID.class);

                    isAvailable = true;
                    plugin.getLogger().info("GGMSurvival JobManager와 연동 성공!");

                    // 연동 확인 테스트
                    plugin.getLogger().info("JobManager 클래스: " + jobManagerClass.getName());
                    plugin.getLogger().info("사용 가능한 메소드 확인 완료");
                }
            } else {
                plugin.getLogger().warning("GGMSurvival 플러그인을 찾을 수 없습니다!");
            }
        } catch (Exception e) {
            plugin.getLogger().severe("GGMSurvival 연동 실패: " + e.getMessage());
            e.printStackTrace();
            isAvailable = false;
        }
    }

    /**
     * 연동 상태 재시도
     */
    public void retryIntegration() {
        plugin.getLogger().info("GGMSurvival 연동 재시도 중...");
        initializeIntegration();

        // 연동 상태 로그
        if (isAvailable()) {
            plugin.getLogger().info("GGMSurvival 연동 재시도 성공!");
        } else {
            plugin.getLogger().warning("GGMSurvival 연동 재시도 실패!");
        }
    }

    /**
     * 연동 가능 여부 확인
     */
    public boolean isAvailable() {
        return isAvailable && ggmSurvival != null && ggmSurvival.isEnabled();
    }

    /**
     * 플레이어의 직업 타입 가져오기
     */
    public String getPlayerJobType(Player player) {
        return getPlayerJobType(player.getUniqueId());
    }

    /**
     * UUID로 플레이어의 직업 타입 가져오기
     */
    public String getPlayerJobType(UUID uuid) {
        if (!isAvailable()) {
            return "NONE";
        }

        try {
            Object jobData = getPlayerJobMethod.invoke(jobManager, uuid);

            if (jobData != null && !jobData.toString().equals("NONE")) {
                return jobData.toString(); // JobType enum의 name() 반환
            }
        } catch (Exception e) {
            plugin.getLogger().warning("직업 타입 조회 실패 (" + uuid + "): " + e.getMessage());
        }

        return "NONE";
    }

    /**
     * 플레이어가 직업을 가지고 있는지 확인
     */
    public boolean hasJob(Player player) {
        return hasJob(player.getUniqueId());
    }

    /**
     * UUID로 플레이어가 직업을 가지고 있는지 확인
     */
    public boolean hasJob(UUID uuid) {
        if (!isAvailable()) {
            return false;
        }

        try {
            return (Boolean) hasJobMethod.invoke(jobManager, uuid);
        } catch (Exception e) {
            plugin.getLogger().warning("직업 보유 확인 실패 (" + uuid + "): " + e.getMessage());
            return false;
        }
    }

    /**
     * 플레이어의 직업 레벨 가져오기 (GGMSurvival에서 관리)
     */
    public int getJobLevel(Player player) {
        if (!isAvailable()) {
            return 1;
        }

        try {
            return (Integer) getJobLevelMethod.invoke(jobManager, player);
        } catch (Exception e) {
            plugin.getLogger().warning("직업 레벨 조회 실패 (" + player.getName() + "): " + e.getMessage());
            return 1;
        }
    }

    /**
     * 플레이어의 직업 레벨 설정 (GGMSurvival에서 관리)
     */
    public boolean setJobLevel(Player player, int level) {
        if (!isAvailable()) {
            return false;
        }

        try {
            setJobLevelMethod.invoke(jobManager, player, level);
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("직업 레벨 설정 실패 (" + player.getName() + "): " + e.getMessage());
            return false;
        }
    }

    /**
     * 직업 정보를 포맷된 문자열로 반환 (스코어보드용)
     */
    public String getFormattedJobInfo(Player player) {
        if (!isAvailable()) {
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

                // GGMSurvival에서 관리하는 레벨 정보
                int level = getJobLevel(player);

                if (level >= 10) {
                    return jobColor + "★" + jobName + " §6만렙";
                } else {
                    return jobColor + jobName + " §fLv." + level;
                }
            } else {
                return "§7직업 없음";
            }
        } catch (Exception e) {
            plugin.getLogger().warning("직업 정보 포맷 실패 (" + player.getName() + "): " + e.getMessage());
        }

        return "§7직업 없음";
    }

    /**
     * 직업 타입을 한글 이름으로 변환
     */
    public String getJobDisplayName(String jobType) {
        if (jobType == null) return "없음";

        switch (jobType.toUpperCase()) {
            case "TANK": return "탱커";
            case "WARRIOR": return "검사";
            case "ARCHER": return "궁수";
            case "MINER": return "광부";
            case "LUMBERJACK": return "나무꾼";
            case "FARMER": return "농부";
            case "FISHER": return "어부";
            case "HUNTER": return "사냥꾼";
            case "BUILDER": return "건축가";
            case "ENCHANTER": return "마법사";
            default: return jobType;
        }
    }

    /**
     * 직업 타입의 색상 코드 반환
     */
    public String getJobColor(String jobType) {
        if (jobType == null) return "§7";

        switch (jobType.toUpperCase()) {
            case "TANK": return "§9"; // 파란색
            case "WARRIOR": return "§c"; // 빨간색
            case "ARCHER": return "§e"; // 노란색
            case "MINER": return "§8"; // 회색
            case "LUMBERJACK": return "§2"; // 초록색
            case "FARMER": return "§a"; // 연두색
            case "FISHER": return "§b"; // 하늘색
            case "HUNTER": return "§6"; // 주황색
            case "BUILDER": return "§d"; // 분홍색
            case "ENCHANTER": return "§5"; // 보라색
            default: return "§f"; // 흰색
        }
    }

    /**
     * 현재 연동된 GGMSurvival 버전 정보
     */
    public String getGGMSurvivalVersion() {
        if (ggmSurvival != null) {
            return ggmSurvival.getDescription().getVersion();
        }
        return "연동 안됨";
    }

    /**
     * 연동 상태 디버그 정보
     */
    public void printDebugInfo() {
        plugin.getLogger().info("=== GGMSurvival 연동 상태 ===");
        plugin.getLogger().info("GGMSurvival 플러그인: " + (ggmSurvival != null ? "발견됨" : "없음"));
        plugin.getLogger().info("JobManager: " + (jobManager != null ? "연동됨" : "없음"));
        plugin.getLogger().info("연동 상태: " + (isAvailable ? "성공" : "실패"));

        if (ggmSurvival != null) {
            plugin.getLogger().info("GGMSurvival 버전: " + ggmSurvival.getDescription().getVersion());
            plugin.getLogger().info("GGMSurvival 활성화: " + ggmSurvival.isEnabled());
        }

        if (jobManager != null) {
            plugin.getLogger().info("JobManager 클래스: " + jobManager.getClass().getName());
        }
    }
}