package com.ggm.core;

import com.ggm.core.commands.*;
import com.ggm.core.listeners.EnchantBookListener;
import com.ggm.core.listeners.PlayerListener;
import com.ggm.core.managers.*;
import com.ggm.core.utils.ServerDetector;
import org.bukkit.plugin.java.JavaPlugin;

public class GGMCore extends JavaPlugin {

    private static GGMCore instance;
    private ServerDetector serverDetector;
    private DatabaseManager databaseManager;
    private EconomyManager economyManager;
    private EnchantBookManager enchantBookManager;
    private EnchantRestrictionManager enchantRestrictionManager;
    private CustomEnchantManager customEnchantManager;
    private InventoryManager inventoryManager; // null일 수 있음
    private ScoreboardManager scoreboardManager;

    @Override
    public void onEnable() {
        instance = this;

        try {
            // 설정 파일 생성
            saveDefaultConfig();

            // 매니저 초기화
            initializeManagers();

            // 명령어 등록
            registerCommands();

            // 이벤트 리스너 등록
            registerListeners();

            getLogger().info("GGMCore 플러그인이 활성화되었습니다!");

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

            // 인첸트북 매니저 초기화
            enchantBookManager = new EnchantBookManager(this);
            getLogger().info("인첸트북 매니저 초기화 완료");

            // 인첸트 제한 매니저 초기화
            enchantRestrictionManager = new EnchantRestrictionManager(this);
            getLogger().info("인첸트 제한 매니저 초기화 완료");

            // 커스텀 인첸트 매니저 초기화
            customEnchantManager = new CustomEnchantManager(this);
            getLogger().info("커스텀 인첸트 매니저 초기화 완료");

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
                getLogger().warning("인벤토리 매니저 초기화 실패 - 기본 기능으로 계속: " + e.getMessage());
                inventoryManager = null;
            }

            // 스코어보드 매니저 초기화
            scoreboardManager = new ScoreboardManager(this);
            getLogger().info("스코어보드 매니저 초기화 완료");

        } catch (Exception e) {
            getLogger().severe("매니저 초기화 중 오류 발생: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    private void registerCommands() {
        try {
            // G 시스템 명령어
            safeRegisterCommand("pay", new PayCommand(this));
            safeRegisterCommand("oppay", new OpPayCommand(this));
            safeRegisterCommand("takemoney", new TakeMoneyCommand(this));
            safeRegisterCommand("g", new BalanceCommand(this));
            safeRegisterCommand("openinven", new OpenInvenCommand(this));

            // 인첸트북 명령어
            safeRegisterCommand("givebook", new GiveBookCommand(this));

            // 스코어보드 명령어
            safeRegisterCommand("sb", new ScoreboardCommand(this));

            // ActionBar 명령어
            safeRegisterCommand("actionbar", new ActionBarCommand(this));

            // 커스텀 인첸트 설정 명령어
            safeRegisterCommand("enchantconfig", new EnchantConfigCommand(this));

            // 리로드 명령어
            safeRegisterCommand("ggmreload", new ReloadCommand(this));

            // 테스트 명령어
            safeRegisterCommand("test", new TestCommand(this));

            // 인벤토리 관리 명령어 (안전하게)
            if (inventoryManager != null) {
                safeRegisterCommand("inventory", new InventoryCommand(this));
                getLogger().info("인벤토리 명령어 등록 완료");
            } else {
                getLogger().info("인벤토리 동기화가 비활성화되어 인벤토리 명령어를 등록하지 않습니다.");
            }

        } catch (Exception e) {
            getLogger().warning("명령어 등록 중 오류: " + e.getMessage());
        }
    }

    private void safeRegisterCommand(String commandName, Object executor) {
        try {
            if (getCommand(commandName) != null) {
                getCommand(commandName).setExecutor((org.bukkit.command.CommandExecutor) executor);
                getLogger().info(commandName + " 명령어 등록 완료");
            } else {
                getLogger().warning(commandName + " 명령어 등록 실패 - plugin.yml 확인 필요");
            }
        } catch (Exception e) {
            getLogger().warning(commandName + " 명령어 등록 중 오류: " + e.getMessage());
        }
    }

    private void registerListeners() {
        try {
            // 인첸트북 우클릭 리스너
            getServer().getPluginManager().registerEvents(new EnchantBookListener(this), this);

            // 플레이어 접속 리스너
            getServer().getPluginManager().registerEvents(new PlayerListener(this), this);

            // 인첸트 제한 리스너
            getServer().getPluginManager().registerEvents(enchantRestrictionManager, this);

            // 커스텀 인첸트 리스너
            getServer().getPluginManager().registerEvents(customEnchantManager, this);

            // 커스텀 인첸트 주기적 효과 시작
            customEnchantManager.startPeriodicEffects();

            getLogger().info("이벤트 리스너 등록 완료");

        } catch (Exception e) {
            getLogger().warning("리스너 등록 중 오류: " + e.getMessage());
        }
    }

    // Getter 메소드들
    public static GGMCore getInstance() {
        return instance;
    }

    public ServerDetector getServerDetector() {
        return serverDetector;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public EconomyManager getEconomyManager() {
        return economyManager;
    }

    public EnchantBookManager getEnchantBookManager() {
        return enchantBookManager;
    }

    public EnchantRestrictionManager getEnchantRestrictionManager() {
        return enchantRestrictionManager;
    }

    public CustomEnchantManager getCustomEnchantManager() {
        return customEnchantManager;
    }

    /**
     * 인벤토리 매니저 (null일 수 있음)
     */
    public InventoryManager getInventoryManager() {
        return inventoryManager;
    }

    /**
     * 인벤토리 동기화가 활성화되어 있는지 확인
     */
    public boolean isInventorySyncEnabled() {
        return inventoryManager != null && getConfig().getBoolean("inventory_sync.enabled", false);
    }

    public ScoreboardManager getScoreboardManager() {
        return scoreboardManager;
    }

    // 안전한 설정 가져오기 메소드들
    public String getSafeConfigString(String path, String defaultValue) {
        try {
            return getConfig().getString(path, defaultValue);
        } catch (Exception e) {
            getLogger().warning("설정 읽기 실패: " + path + ", 기본값 사용: " + defaultValue);
            return defaultValue;
        }
    }

    public int getSafeConfigInt(String path, int defaultValue) {
        try {
            return getConfig().getInt(path, defaultValue);
        } catch (Exception e) {
            getLogger().warning("설정 읽기 실패: " + path + ", 기본값 사용: " + defaultValue);
            return defaultValue;
        }
    }

    public boolean getSafeConfigBoolean(String path, boolean defaultValue) {
        try {
            return getConfig().getBoolean(path, defaultValue);
        } catch (Exception e) {
            getLogger().warning("설정 읽기 실패: " + path + ", 기본값 사용: " + defaultValue);
            return defaultValue;
        }
    }
}