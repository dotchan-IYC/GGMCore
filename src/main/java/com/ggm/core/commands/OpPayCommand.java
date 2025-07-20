package com.ggm.core.commands;

import com.ggm.core.GGMCore;
import com.ggm.core.managers.EconomyManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class OpPayCommand implements CommandExecutor {

    private final GGMCore plugin;
    private final EconomyManager economyManager;

    public OpPayCommand(GGMCore plugin) {
        this.plugin = plugin;
        this.economyManager = plugin.getEconomyManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("ggm.oppay")) {
            sender.sendMessage("§c권한이 없습니다.");
            return true;
        }

        if (args.length != 2) {
            sender.sendMessage("§c사용법: /oppay <플레이어> <금액>");
            return true;
        }

        // 대상 플레이어 확인
        Player targetPlayer = Bukkit.getPlayer(args[0]);
        if (targetPlayer == null) {
            sender.sendMessage("§c플레이어를 찾을 수 없습니다: " + args[0]);
            return true;
        }

        // 금액 파싱
        long amount;
        try {
            amount = Long.parseLong(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage("§c올바른 금액을 입력해주세요.");
            return true;
        }

        if (amount <= 0) {
            sender.sendMessage("§c양수만 입력 가능합니다.");
            return true;
        }

        // OP 전용 G 지급 (스코어보드 업데이트 자동 포함)
        economyManager.adminGiveMoney(targetPlayer.getUniqueId(), amount).thenAccept(success -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (success) {
                    // 관리자에게 메시지
                    sender.sendMessage("§a" + targetPlayer.getName() + "님에게 " +
                            economyManager.formatMoney(amount) + "G를 지급했습니다.");

                    // 받는 플레이어에게 메시지
                    targetPlayer.sendMessage("§a관리자로부터 " + economyManager.formatMoney(amount) + "G를 받았습니다!");
                    targetPlayer.playSound(targetPlayer.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);

                    plugin.getLogger().info(String.format("[관리자지급] %s이(가) %s에게 %dG 지급",
                            sender.getName(), targetPlayer.getName(), amount));
                } else {
                    sender.sendMessage("§cG 지급에 실패했습니다.");
                }
            });
        }).exceptionally(throwable -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                sender.sendMessage("§cG 지급 중 오류가 발생했습니다: " + throwable.getMessage());
                plugin.getLogger().severe("OP 지급 오류: " + throwable.getMessage());
            });
            return null;
        });

        return true;
    }
}