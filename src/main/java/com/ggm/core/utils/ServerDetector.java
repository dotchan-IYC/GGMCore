package com.ggm.core.utils;

import com.ggm.core.GGMCore;
import org.bukkit.Bukkit;

/**
 * 서버 타입을 감지하는 유틸리티 클래스
 */
public class ServerDetector {

    private final GGMCore plugin;
    private String serverType;

    public ServerDetector(GGMCore plugin) {
        this.plugin = plugin;
        detectServerType();
    }

    /**
     * 서버 타입 감지
     */
    private void detectServerType() {
        // 서버 정보나 설정을 기반으로 서버 타입 감지
        String serverInfo = getServerInfo();
        int port = Bukkit.getPort();

        // 포트 번호로 서버 타입 감지 (예시)
        if (serverInfo.contains("lobby") || port == 25565) {
            serverType = "lobby";
        } else if (serverInfo.contains("survival") || port == 25566) {
            serverType = "survival";
        } else if (serverInfo.contains("creative") || port == 25567) {
            serverType = "creative";
        } else if (serverInfo.contains("towny") || port == 25568) {
            serverType = "towny";
        } else {
            // 기본값 또는 설정 파일에서 가져오기
            serverType = plugin.getConfig().getString("server.type", "survival");
        }

        plugin.getLogger().info("서버 타입 감지됨: " + serverType);
    }

    /**
     * 서버 정보 가져오기 (Bukkit.getServerName() 대신)
     */
    private String getServerInfo() {
        try {
            // 서버 모드나 버전 정보로 타입 추정
            String version = Bukkit.getVersion().toLowerCase();
            String name = Bukkit.getName().toLowerCase();

            // 설정 파일에서 직접 지정된 타입 우선 사용
            String configType = plugin.getConfig().getString("server.type", "");
            if (!configType.isEmpty()) {
                return configType.toLowerCase();
            }

            // 서버 소프트웨어나 플러그인 정보로 추정
            return name + " " + version;
        } catch (Exception e) {
            plugin.getLogger().warning("서버 정보 조회 실패: " + e.getMessage());
            return "unknown";
        }
    }

    /**
     * 서버 타입 반환
     */
    public String getServerType() {
        return serverType;
    }

    /**
     * 야생 서버인지 확인
     */
    public boolean isSurvival() {
        return "survival".equalsIgnoreCase(serverType);
    }

    /**
     * 로비 서버인지 확인
     */
    public boolean isLobby() {
        return "lobby".equalsIgnoreCase(serverType);
    }

    /**
     * 건축 서버인지 확인
     */
    public boolean isCreative() {
        return "creative".equalsIgnoreCase(serverType);
    }

    /**
     * 마을 서버인지 확인
     */
    public boolean isTowny() {
        return "towny".equalsIgnoreCase(serverType);
    }

    /**
     * 서버 정보 출력
     */
    public void printServerInfo() {
        plugin.getLogger().info("=== 서버 정보 ===");
        plugin.getLogger().info("서버 소프트웨어: " + Bukkit.getName());
        plugin.getLogger().info("서버 버전: " + Bukkit.getVersion());
        plugin.getLogger().info("서버 포트: " + Bukkit.getPort());
        plugin.getLogger().info("감지된 타입: " + serverType);
        plugin.getLogger().info("야생 서버: " + isSurvival());
    }

    /**
     * 서버 타입 강제 설정 (디버그용)
     */
    public void setServerType(String type) {
        this.serverType = type.toLowerCase();
        plugin.getLogger().info("서버 타입이 강제로 설정됨: " + serverType);
    }

    /**
     * 인첸트 테이블 사용 허용 여부 (서버별 설정)
     */
    public boolean isEnchantTableAllowed() {
        // 야생 서버에서만 인첸트 테이블 사용 허용
        return isSurvival();
    }

    /**
     * 직업 시스템 사용 허용 여부
     */
    public boolean isJobSystemAllowed() {
        // 야생 서버에서만 직업 시스템 사용
        return isSurvival();
    }

    /**
     * 인벤토리 동기화 사용 허용 여부
     */
    public boolean isInventorySyncAllowed() {
        // 모든 서버에서 인벤토리 동기화 허용
        return true;
    }
}