package com.ggm.core;

import com.ggm.core.commands.*;
import com.ggm.core.listeners.*;
import com.ggm.core.managers.*;
import com.ggm.core.utils.ServerDetector;
import org.bukkit.plugin.java.JavaPlugin;

public final class GGMCore extends JavaPlugin {

    // 매니저들
    private DatabaseManager databaseManager;
    private EconomyManager economyManager;
    private ScoreboardManager scoreboardManager;
    private InventoryManager inventoryManager;
    private EnchantBookManager enchantBookManager;
    private CustomEnchantManager customEnchantManager;
    private EnchantRestrictionManager enchantRestrictionManager;
    private ProtectionScrollManager protectionScrollManager;
    private AnvilEnchantManager anvilEnchantManager;
    private JobExperienceManager jobExperienceManager; // 새로 추가

    // 기타 유틸리티
    private ServerDetector serverDetector;

    @Override
    public void onEnable() {
        try {
            getLogger().info("GGMCore 플러그인을 시작합니다...");

            // 설정 파일 저장
            saveDefaultConfig();

            // 매니저들 초기화
            initializeManagers();

            // 명령어 등록
            registerCommands();

            // 이벤트 리스너 등록
            registerEvents();

            getLogger().info("GGMCore 플러그인이 성공적으로 활성화되었습니다!");
            getLogger().info("새로운 직업 경험치 시스템이 추가되었습니다!");

        } catch (Exception e) {
            getLogger().severe("플러그인 초기화 실패: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void onDisable() {
        try {
            // 스코어보드 정리
            if (scoreboardManager != null) {
                scoreboardManager.cleanup();
            }

            // 데이터베이스 연결 종료
            if (databaseManager != null) {
                databaseManager.closeConnection();
            }

            getLogger().info("GGMCore 플러그인이 비활성화되었습니다!");

        } catch (Exception e) {
            getLogger().warning("플러그인 종료 중 오류: " + e.getMessage());
        }
    }

    private void initializeManagers() {
        try {
            // 서버 타입 감지 (가장 먼저 실행)
            serverDetector = new ServerDetector(this);
            serverDetector.printServerInfo();

            // 데이터베이스 매니저 초기화 (필수)
            databaseManager = new DatabaseManager(this);
            getLogger().info("데이터베이스 매니저 초기화 완료");

            // 경제 매니저 초기화 (필수)
            economyManager = new EconomyManager(this);
            getLogger().info("경제 매니저 초기화 완료");

            // 직업 경험치 매니저 초기화 (새로 추가 - 스코어보드보다 먼저)
            jobExperienceManager = new JobExperienceManager(this);
            getLogger().info("직업 경험치 매니저 초기화 완료");

            // 인첸트북 매니저 초기화
            enchantBookManager = new EnchantBookManager(this);
            getLogger().info("인첸트북 매니저 초기화 완료");

            // 커스텀 인첸트 매니저 초기화 (인첸트 제한보다 먼저 초기화)
            customEnchantManager = new CustomEnchantManager(this);
            getLogger().info("커스텀 인첸트 매니저 초기화 완료 (" +
                    customEnchantManager.getCustomEnchants().size() + "개 인첸트)");

            // 파괴방지권 매니저 초기화
            protectionScrollManager = new ProtectionScrollManager(this);
            getLogger().info("파괴방지권 시스템 초기화 완료");

            // 모루 인첸트 매니저 초기화
            if (getConfig().getBoolean("anvil_enchanting.enabled", true)) {
                anvilEnchantManager = new AnvilEnchantManager(this);
                getLogger().info("모루 인첸트 적용 시스템 초기화 완료");
            } else {
                getLogger().info("모루 인첸트 적용 시스템이 비활성화되어 있습니다.");
                anvilEnchantManager = null;
            }

            // 인첸트 제한 매니저 초기화 (모루 매니저 다음에 초기화)
            enchantRestrictionManager = new EnchantRestrictionManager(this);
            getLogger().info("인첸트 제한 매니저 초기화 완료");

            // 스코어보드 매니저 초기화 (직업 경험치 매니저 다음에 초기화)
            scoreboardManager = new ScoreboardManager(this);
            getLogger().info("스코어보드 매니저 초기화 완료");

            // 인벤토리 매니저 초기화 (안전하게)
            try {
                if (getConfig().getBoolean("inventory_sync.enabled", false)) {
                    inventoryManager = new InventoryManager(this);
                    getLogger().info("인벤토리 매니저 초기화 완료");
                } else {
                    getLogger().info("인벤토리 동기화가 비활성화되어 있습니다.");
                    inventoryManager = null;
                }
            } catch (Exception e) {
                getLogger().warning("인벤토리 매니저 초기화 실패: " + e.getMessage());
                inventoryManager = null;
            }

        } catch (Exception e) {
            getLogger().severe("매니저 초기화 실패: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void registerCommands() {
        try {
            // 기존 명령어들
            getCommand("pay").setExecutor(new PayCommand(this));
            getCommand("oppay").setExecutor(new OpPayCommand(this));
            getCommand("takemoney").setExecutor(new TakeMoneyCommand(this));
            getCommand("openinven").setExecutor(new OpenInvenCommand(this));
            getCommand("givebook").setExecutor(new GiveBookCommand(this));
            getCommand("g").setExecutor(new BalanceCommand(this));
            getCommand("sb").setExecutor(new ScoreboardCommand(this));
            getCommand("ggmreload").setExecutor(new ReloadCommand(this));
            getCommand("actionbar").setExecutor(new ActionBarCommand(this));
            getCommand("enchantconfig").setExecutor(new EnchantConfigCommand(this));
            getCommand("test").setExecutor(new TestCommand(this));
            getCommand("ggmreset").setExecutor(new ResetPlayerCommand(this));

            // 인벤토리 명령어 (조건부)
            if (inventoryManager != null) {
                getCommand("inventory").setExecutor(new InventoryCommand(this));
            }

            // 새로운 직업 경험치 명령어
            getCommand("jobexp").setExecutor(new JobExpCommand(this));

            // 직업 시스템 디버그 명령어 (OP 전용)
            getCommand("jobdebug").setExecutor(new JobDebugCommand(this));

            getLogger().info("명령어 등록 완료");

        } catch (Exception e) {
            getLogger().severe("명령어 등록 실패: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void registerEvents() {
        try {
            // 기본 리스너들
            getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
            getServer().getPluginManager().registerEvents(new EnchantBookListener(this), this);
            getServer().getPluginManager().registerEvents(new CustomEnchantListener(this), this);
            getServer().getPluginManager().registerEvents(new ProtectionScrollListener(this), this);

            // 모루 인첸트 리스너 (조건부)
            if (anvilEnchantManager != null) {
                getServer().getPluginManager().registerEvents(new AnvilEnchantListener(this), this);
            }

            // JobExperienceManager는 자체적으로 이벤트를 등록함 (생성자에서)

            getLogger().info("이벤트 리스너 등록 완료");

        } catch (Exception e) {
            getLogger().severe("이벤트 리스너 등록 실패: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Getter 메소드들
    public DatabaseManager getDatabaseManager() { return databaseManager; }
    public EconomyManager getEconomyManager() { return economyManager; }
    public ScoreboardManager getScoreboardManager() { return scoreboardManager; }
    public InventoryManager getInventoryManager() { return inventoryManager; }
    public EnchantBookManager getEnchantBookManager() { return enchantBookManager; }
    public CustomEnchantManager getCustomEnchantManager() { return customEnchantManager; }
    public EnchantRestrictionManager getEnchantRestrictionManager() { return enchantRestrictionManager; }
    public ProtectionScrollManager getProtectionScrollManager() { return protectionScrollManager; }
    public AnvilEnchantManager getAnvilEnchantManager() { return anvilEnchantManager; }
    public JobExperienceManager getJobExperienceManager() { return jobExperienceManager; }
    public ServerDetector getServerDetector() { return serverDetector; }
}