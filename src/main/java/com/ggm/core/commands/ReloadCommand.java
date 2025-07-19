package com.ggm.core.commands;

import com.ggm.core.GGMCore;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class ReloadCommand implements CommandExecutor {

    private final GGMCore plugin;

    public ReloadCommand(GGMCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("ggm.reload")) {
            sender.sendMessage("§c권한이 없습니다.");
            return true;
        }

        try {
            // 설정 파일 리로드
            plugin.reloadConfig();

            // 인첸트 제한 매니저 설정 리로드
            plugin.getEnchantRestrictionManager().reloadConfig();

            sender.sendMessage("§a[GGM] 설정이 리로드되었습니다!");

            // 현재 설정 상태 표시
            boolean restrictionEnabled = plugin.getConfig().getBoolean("enchant_restrictions.enabled", false);
            String serverName = plugin.getConfig().getString("scoreboard.server_name", "알 수 없음");

            sender.sendMessage("§7- 서버: §f" + serverName);
            sender.sendMessage("§7- 인첸트 제한: " + (restrictionEnabled ? "§a활성화" : "§c비활성화"));

            plugin.getLogger().info(sender.getName() + "이(가) 설정을 리로드했습니다.");

        } catch (Exception e) {
            sender.sendMessage("§c설정 리로드 중 오류가 발생했습니다: " + e.getMessage());
            plugin.getLogger().severe("설정 리로드 실패: " + e.getMessage());
        }

        return true;
    }
}