package com.ggm.core.managers;

import com.ggm.core.GGMCore;
import com.ggm.core.utils.JobIntegrationHelper;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.Sound;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 직업 경험치 전용 매니저
 * 일반 경험치와 완전 분리된 직업 전용 경험치 시스템
 * GGMSurvival과 연동하여 실제 직업 정보를 가져옴
 */
public class JobExperienceManager implements Listener {

    private final GGMCore plugin;
    private final DatabaseManager databaseManager;
    private final JobIntegrationHelper jobHelper; // GGMSurvival 연동

    // 직업별 경험치 캐시 (UUID -> JobType -> Experience)
    private final Map<UUID, Map<String, Integer>> jobExpCache;

    // 직업별 레벨업 필요 경험치 (레벨 -> 필요 경험치)
    private final Map<Integer, Integer> levelRequirements;

    // 직업별 경험치 획득 배율
    private final Map<String, JobExpConfig> jobConfigs;

    public JobExperienceManager(GGMCore plugin) {
        this.plugin = plugin;
        this.databaseManager = plugin.getDatabaseManager();
        this.jobHelper = new JobIntegrationHelper(plugin); // GGMSurvival 연동 초기화
        this.jobExpCache = new HashMap<>();
        this.levelRequirements = new HashMap<>();
        this.jobConfigs = new HashMap<>();

        // 테이블 생성
        createJobExperienceTable();

        // 설정에서 레벨업 필요 경험치 로드
        loadLevelRequirementsFromConfig();

        // 설정에서 직업별 경험치 획득 설정 로드
        loadJobConfigsFromConfig();

        // 이벤트 등록
        Bukkit.getPluginManager().registerEvents(this, plugin);

        // GGMSurvival 연동 상태 확인
        if (jobHelper.isAvailable()) {
            plugin.getLogger().info("직업 경험치 시스템이 GGMSurvival과 연동하여 초기화되었습니다!");
            jobHelper.printDebugInfo();
        } else {
            plugin.getLogger().warning("GGMSurvival 연동 실패! 직업 경험치 시스템이 제한적으로 작동합니다.");
        }
    }

    /**
     * 직업 경험치 테이블 생성
     */
    private void createJobExperienceTable() {
        String sql = """
            CREATE TABLE IF NOT EXISTS ggm_job_experience (
                uuid VARCHAR(36) NOT NULL,
                job_type VARCHAR(20) NOT NULL,
                experience INT DEFAULT 0,
                total_experience INT DEFAULT 0,
                level INT DEFAULT 1,
                last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                PRIMARY KEY (uuid, job_type),
                INDEX idx_uuid (uuid),
                INDEX idx_job_type (job_type)
            )
            """;

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.executeUpdate();
            plugin.getLogger().info("직업 경험치 테이블이 생성되었습니다.");
        } catch (SQLException e) {
            plugin.getLogger().severe("직업 경험치 테이블 생성 실패: " + e.getMessage());
        }
    }

    /**
     * 설정에서 레벨업 필요 경험치 로드
     */
    private void loadLevelRequirementsFromConfig() {
        if (plugin.getConfig().contains("job_experience.level_requirements")) {
            for (String levelStr : plugin.getConfig().getConfigurationSection("job_experience.level_requirements").getKeys(false)) {
                try {
                    int level = Integer.parseInt(levelStr);
                    int requiredExp = plugin.getConfig().getInt("job_experience.level_requirements." + levelStr);
                    levelRequirements.put(level, requiredExp);
                } catch (NumberFormatException e) {
                    plugin.getLogger().warning("잘못된 레벨 설정: " + levelStr);
                }
            }
        } else {
            // 기본값 설정
            setupDefaultLevelRequirements();
        }

        plugin.getLogger().info("직업 레벨 요구사항 로드 완료: " + levelRequirements.size() + "개 레벨");
    }

    /**
     * 설정에서 직업별 경험치 획득 설정 로드
     */
    private void loadJobConfigsFromConfig() {
        if (plugin.getConfig().contains("job_experience.job_multipliers")) {
            for (String jobType : plugin.getConfig().getConfigurationSection("job_experience.job_multipliers").getKeys(false)) {
                Map<String, Integer> expValues = new HashMap<>();

                String basePath = "job_experience.job_multipliers." + jobType;
                for (String action : plugin.getConfig().getConfigurationSection(basePath).getKeys(false)) {
                    int value = plugin.getConfig().getInt(basePath + "." + action);
                    expValues.put(action, value);
                }

                String displayName = jobHelper.getJobDisplayName(jobType);
                jobConfigs.put(jobType.toUpperCase(), new JobExpConfig(expValues, displayName));
            }
        } else {
            // 기본값 설정
            setupDefaultJobConfigs();
        }

        plugin.getLogger().info("직업 경험치 설정 로드 완료: " + jobConfigs.size() + "개 직업");
    }

    /**
     * 기본 레벨업 필요 경험치 설정
     */
    private void setupDefaultLevelRequirements() {
        levelRequirements.put(1, 0);      // 1레벨은 0 경험치
        levelRequirements.put(2, 100);    // 2레벨: 100 경험치
        levelRequirements.put(3, 300);    // 3레벨: 총 300 경험치
        levelRequirements.put(4, 600);    // 4레벨: 총 600 경험치
        levelRequirements.put(5, 1000);   // 5레벨: 총 1000 경험치
        levelRequirements.put(6, 1500);   // 6레벨: 총 1500 경험치
        levelRequirements.put(7, 2100);   // 7레벨: 총 2100 경험치
        levelRequirements.put(8, 2800);   // 8레벨: 총 2800 경험치
        levelRequirements.put(9, 3600);   // 9레벨: 총 3600 경험치
        levelRequirements.put(10, 5000);  // 10레벨(만렙): 총 5000 경험치
    }

    /**
     * 기본 직업별 경험치 획득 설정
     */
    private void setupDefaultJobConfigs() {
        // 탱커: 방어 관련 행동으로 경험치 획득
        jobConfigs.put("TANK", new JobExpConfig(
                Map.of("DAMAGE_TAKEN", 2, "BLOCK_WITH_SHIELD", 5, "KILL_MONSTER", 3),
                "탱커"
        ));

        // 검사: 근접 전투로 경험치 획득
        jobConfigs.put("WARRIOR", new JobExpConfig(
                Map.of("MELEE_KILL", 4, "CRITICAL_HIT", 3, "SWORD_DAMAGE", 2),
                "검사"
        ));

        // 궁수: 원거리 전투로 경험치 획득
        jobConfigs.put("ARCHER", new JobExpConfig(
                Map.of("BOW_KILL", 5, "LONG_SHOT", 7, "HEADSHOT", 10),
                "궁수"
        ));

        // 기본 직업들 추가 (필요 시 확장)
        jobConfigs.put("MINER", new JobExpConfig(
                Map.of("MINE_ORE", 3, "MINE_RARE_ORE", 8, "MINE_STONE", 1),
                "광부"
        ));

        jobConfigs.put("LUMBERJACK", new JobExpConfig(
                Map.of("CUT_TREE", 2, "CUT_RARE_TREE", 5),
                "나무꾼"
        ));

        jobConfigs.put("FISHER", new JobExpConfig(
                Map.of("CATCH_FISH", 3, "CATCH_TREASURE", 10, "CATCH_JUNK", 1),
                "어부"
        ));
    }

    /**
     * 플레이어의 직업 경험치 초기화
     */
    public CompletableFuture<Void> initializePlayerJobExp(UUID uuid, String jobType) {
        return CompletableFuture.runAsync(() -> {
            String sql = """
                INSERT IGNORE INTO ggm_job_experience (uuid, job_type, experience, total_experience, level)
                VALUES (?, ?, 0, 0, 1)
                """;

            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, uuid.toString());
                stmt.setString(2, jobType);
                stmt.executeUpdate();

                // 캐시 초기화
                jobExpCache.computeIfAbsent(uuid, k -> new HashMap<>()).put(jobType, 0);

            } catch (SQLException e) {
                plugin.getLogger().severe("직업 경험치 초기화 실패: " + e.getMessage());
            }
        });
    }

    /**
     * 직업 경험치 획득
     */
    public CompletableFuture<Boolean> addJobExperience(UUID uuid, String jobType, String action, int multiplier) {
        return CompletableFuture.supplyAsync(() -> {
            JobExpConfig config = jobConfigs.get(jobType);
            if (config == null) return false;

            Integer baseExp;

            // 관리자 명령어나 테스트를 위한 특별 액션 처리
            if ("ADMIN_GIVE".equals(action) || "ADMIN_TEST".equals(action)) {
                baseExp = multiplier; // 직접 경험치 값 사용
                multiplier = 1;
            } else {
                baseExp = config.expValues.get(action);
                if (baseExp == null) return false;
            }

            int expToAdd = baseExp * multiplier;

            try {
                // 현재 경험치 조회
                int currentExp = getCurrentJobExp(uuid, jobType);
                int currentLevel = getCurrentJobLevel(uuid, jobType);
                int newExp = currentExp + expToAdd;

                // 레벨업 확인
                int newLevel = calculateLevel(newExp);
                boolean leveledUp = newLevel > currentLevel;

                // 데이터베이스 업데이트
                String sql = """
                    UPDATE ggm_job_experience 
                    SET experience = ?, total_experience = total_experience + ?, level = ?
                    WHERE uuid = ? AND job_type = ?
                    """;

                try (Connection conn = databaseManager.getConnection();
                     PreparedStatement stmt = conn.prepareStatement(sql)) {

                    stmt.setInt(1, newExp);
                    stmt.setInt(2, expToAdd);
                    stmt.setInt(3, newLevel);
                    stmt.setString(4, uuid.toString());
                    stmt.setString(5, jobType);

                    int result = stmt.executeUpdate();

                    if (result > 0) {
                        // 캐시 업데이트
                        jobExpCache.computeIfAbsent(uuid, k -> new HashMap<>()).put(jobType, newExp);

                        // 플레이어에게 알림
                        Player player = Bukkit.getPlayer(uuid);
                        if (player != null) {
                            player.sendMessage(String.format("§e[%s] §f+%d 직업 경험치! §7(%d/%d)",
                                    config.displayName, expToAdd, newExp, getExpRequiredForNextLevel(newLevel)));

                            if (leveledUp) {
                                player.sendMessage(String.format("§6★ [%s] §f레벨업! §e%d레벨 §f→ §e%d레벨",
                                        config.displayName, currentLevel, newLevel));

                                // 레벨업 효과음/파티클 등 추가
                                try {
                                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                                } catch (Exception e) {
                                    // 1.8 이하 버전 호환성을 위한 대체 사운드
                                    try {
                                        player.playSound(player.getLocation(), "entity.player.levelup", 1.0f, 1.0f);
                                    } catch (Exception ex) {
                                        // 사운드 재생 실패 시 무시
                                    }
                                }
                            }
                        }

                        return true;
                    }
                }

            } catch (SQLException e) {
                plugin.getLogger().severe("직업 경험치 추가 실패: " + e.getMessage());
            }

            return false;
        });
    }

    /**
     * 현재 직업 경험치 조회 (캐시 우선)
     */
    public int getCurrentJobExp(UUID uuid, String jobType) {
        // 캐시에서 먼저 확인
        Map<String, Integer> playerCache = jobExpCache.get(uuid);
        if (playerCache != null && playerCache.containsKey(jobType)) {
            return playerCache.get(jobType);
        }

        // 데이터베이스에서 조회
        String sql = "SELECT experience FROM ggm_job_experience WHERE uuid = ? AND job_type = ?";

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, uuid.toString());
            stmt.setString(2, jobType);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                int exp = rs.getInt("experience");
                // 캐시 업데이트
                jobExpCache.computeIfAbsent(uuid, k -> new HashMap<>()).put(jobType, exp);
                return exp;
            }

        } catch (SQLException e) {
            plugin.getLogger().severe("직업 경험치 조회 실패: " + e.getMessage());
        }

        return 0;
    }

    /**
     * 현재 직업 레벨 조회
     */
    public int getCurrentJobLevel(UUID uuid, String jobType) {
        String sql = "SELECT level FROM ggm_job_experience WHERE uuid = ? AND job_type = ?";

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, uuid.toString());
            stmt.setString(2, jobType);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return rs.getInt("level");
            }

        } catch (SQLException e) {
            plugin.getLogger().severe("직업 레벨 조회 실패: " + e.getMessage());
        }

        return 1; // 기본 레벨
    }

    /**
     * 경험치로 레벨 계산
     */
    private int calculateLevel(int totalExp) {
        for (int level = 10; level >= 1; level--) {
            if (totalExp >= levelRequirements.get(level)) {
                return level;
            }
        }
        return 1;
    }

    /**
     * 다음 레벨까지 필요한 경험치
     */
    public int getExpRequiredForNextLevel(int currentLevel) {
        if (currentLevel >= 10) return 0; // 만렙
        return levelRequirements.get(currentLevel + 1);
    }

    /**
     * 직업 경험치 정보 문자열 생성 (스코어보드용)
     */
    public String getJobExpDisplay(UUID uuid, String jobType) {
        int currentExp = getCurrentJobExp(uuid, jobType);
        int currentLevel = getCurrentJobLevel(uuid, jobType);

        if (currentLevel >= 10) {
            return String.format("§6★만렙 §7(총 %d EXP)", currentExp);
        } else {
            int required = getExpRequiredForNextLevel(currentLevel);
            return String.format("§fLv.%d §7(%d/%d)", currentLevel, currentExp, required);
        }
    }

    // ========================= 이벤트 핸들러 =========================

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        String jobType = getPlayerJobType(player);

        if ("MINER".equals(jobType)) {
            String blockType = event.getBlock().getType().name();
            if (blockType.contains("ORE")) {
                if (blockType.contains("DIAMOND") || blockType.contains("EMERALD")) {
                    addJobExperience(player.getUniqueId(), jobType, "MINE_RARE_ORE", 1);
                } else {
                    addJobExperience(player.getUniqueId(), jobType, "MINE_ORE", 1);
                }
            } else if (blockType.contains("STONE") || blockType.contains("COBBLESTONE")) {
                addJobExperience(player.getUniqueId(), jobType, "MINE_STONE", 1);
            }
        } else if ("LUMBERJACK".equals(jobType)) {
            String blockType = event.getBlock().getType().name();
            if (blockType.contains("LOG")) {
                if (blockType.contains("DARK_OAK") || blockType.contains("JUNGLE")) {
                    addJobExperience(player.getUniqueId(), jobType, "CUT_RARE_TREE", 1);
                } else {
                    addJobExperience(player.getUniqueId(), jobType, "CUT_TREE", 1);
                }
            }
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (event.getEntity().getKiller() instanceof Player player) {
            String jobType = getPlayerJobType(player);

            if ("WARRIOR".equals(jobType)) {
                addJobExperience(player.getUniqueId(), jobType, "MELEE_KILL", 1);
            } else if ("ARCHER".equals(jobType)) {
                // 원거리 공격으로 죽였는지 확인 (추가 로직 필요)
                addJobExperience(player.getUniqueId(), jobType, "BOW_KILL", 1);
            } else if ("TANK".equals(jobType)) {
                addJobExperience(player.getUniqueId(), jobType, "KILL_MONSTER", 1);
            }
        }
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            String jobType = getPlayerJobType(player);

            if ("TANK".equals(jobType) && event.getEntity() instanceof Player) {
                // 탱커가 피해를 받을 때 경험치
                addJobExperience(player.getUniqueId(), jobType, "DAMAGE_TAKEN", 1);
            }
        }
    }

    /**
     * 플레이어의 현재 직업 타입 조회 (GGMSurvival 연동)
     */
    private String getPlayerJobType(Player player) {
        // GGMSurvival과 연동하여 플레이어의 직업을 조회
        // 이 부분은 GGMSurvival의 JobManager와 연동 필요
        try {
            // ScoreboardManager에서 사용하는 방식과 동일하게 구현
            // 실제 구현 시 GGMSurvival의 JobManager 참조 필요
            return "WARRIOR"; // 임시 반환값
        } catch (Exception e) {
            return "NONE";
        }
    }

    // ========================= 관리용 메소드 =========================

    /**
     * OP 전용: 직업 경험치 설정
     */
    public CompletableFuture<Boolean> setJobExperience(UUID uuid, String jobType, int experience) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                int newLevel = calculateLevel(experience);

                String sql = """
                    INSERT INTO ggm_job_experience (uuid, job_type, experience, total_experience, level)
                    VALUES (?, ?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE
                    experience = VALUES(experience),
                    total_experience = GREATEST(total_experience, VALUES(experience)),
                    level = VALUES(level)
                    """;

                try (Connection conn = databaseManager.getConnection();
                     PreparedStatement stmt = conn.prepareStatement(sql)) {

                    stmt.setString(1, uuid.toString());
                    stmt.setString(2, jobType);
                    stmt.setInt(3, experience);
                    stmt.setInt(4, experience);
                    stmt.setInt(5, newLevel);

                    int result = stmt.executeUpdate();

                    if (result > 0) {
                        // 캐시 업데이트
                        jobExpCache.computeIfAbsent(uuid, k -> new HashMap<>()).put(jobType, experience);
                        return true;
                    }
                }

            } catch (SQLException e) {
                plugin.getLogger().severe("직업 경험치 설정 실패: " + e.getMessage());
            }

            return false;
        });
    }

    /**
     * 직업별 경험치 설정 클래스
     */
    private static class JobExpConfig {
        final Map<String, Integer> expValues;
        final String displayName;

        JobExpConfig(Map<String, Integer> expValues, String displayName) {
            this.expValues = expValues;
            this.displayName = displayName;
        }
    }
}