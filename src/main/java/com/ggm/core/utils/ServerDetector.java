package com.ggm.core.utils;

import com.ggm.core.GGMCore;
import org.bukkit.Bukkit;

public class ServerDetector {

    private final GGMCore plugin;
    private String serverType;
    private String serverDisplayName;

    public ServerDetector(GGMCore plugin) {
        this.plugin = plugin;
        detectServerType();
    }

    /**
     * 서버 포트를 기반으로 서버 타입 자동 감지
     */
    private void detectServerType() {
        int port = Bukkit.getServer().getPort();

        // BungeeCord 설정과 일치하는 포트 매핑
        switch (port) {
            case 25565:
                serverType = "lobby";
                serverDisplayName = "🏠 로비";
                break;
            case 25566:
                serverType = "build";
                serverDisplayName = "🏗️ 건축";
                break;
            case 25567:
                serverType = "survival";
                serverDisplayName = "⚔️ 야생";
                break;
            case 25568:
                serverType = "village";
                serverDisplayName = "🏘️ 마을";
                break;
            default:
                // 알 수 없는 포트인 경우 설정 파일에서 가져오기
                serverType = plugin.getConfig().getString("server.type", "unknown");
                serverDisplayName = plugin.getConfig().getString("server.display_name", "알 수 없음");
                plugin.getLogger().warning("알 수 없는 포트: " + port + ". config.yml에서 수동 설정을 확인하세요.");
        }

        plugin.getLogger().info("서버 타입 감지 완료: " + serverType + " (" + serverDisplayName + ") - 포트: " + port);
    }

    /**
     * 서버 타입 반환 (lobby, build, survival, village)
     */
    public String getServerType() {
        return serverType;
    }

    /**
     * 스코어보드에 표시될 이름 반환
     */
    public String getDisplayName() {
        return serverDisplayName;
    }

    /**
     * 로비 서버인지 확인
     */
    public boolean isLobby() {
        return "lobby".equals(serverType);
    }

    /**
     * 건축 서버인지 확인
     */
    public boolean isBuild() {
        return "build".equals(serverType);
    }

    /**
     * 야생 서버인지 확인
     */
    public boolean isSurvival() {
        return "survival".equals(serverType);
    }

    /**
     * 마을 서버인지 확인
     */
    public boolean isVillage() {
        return "village".equals(serverType);
    }

    /**
     * 인첸트 테이블 사용 가능한지 확인
     */
    public boolean isEnchantTableAllowed() {
        // 야생 서버에서만 인첸트 테이블 허용
        return isSurvival();
    }

    /**
     * 서버별 특화 기능 확인
     */
    public boolean hasJobSystem() {
        // 야생 서버에만 직업 시스템
        return isSurvival();
    }

    public boolean hasLandSystem() {
        // 건축 서버에만 땅 구매 시스템
        return isBuild();
    }

    public boolean hasTownSystem() {
        // 마을 서버에만 마을 시스템
        return isVillage();
    }

    /**
     * 서버 설정 정보 출력
     */
    public void printServerInfo() {
        plugin.getLogger().info("=== 서버 정보 ===");
        plugin.getLogger().info("타입: " + serverType);
        plugin.getLogger().info("표시명: " + serverDisplayName);
        plugin.getLogger().info("포트: " + Bukkit.getServer().getPort());
        plugin.getLogger().info("인첸트 테이블: " + (isEnchantTableAllowed() ? "허용" : "차단"));
        plugin.getLogger().info("================");
    }
}