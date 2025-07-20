package com.ggm.core.commands;

import com.ggm.core.GGMCore;
import com.ggm.core.managers.EconomyManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class TakeMoneyCommand implements CommandExecutor {

    private final GGMCore plugin;
    private final EconomyManager economyManager;

    public TakeMoneyCommand(GGMCore plugin) {
        this.plugin = plugin;
        this.economyManager = plugin.getEconomyManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("ggm.takemoney")) {
            sender.sendMessage("§c권한이 없습니다.");
            return true;
        }

        if (args.length != 2) {
            sender.sendMessage("§c사용법: /takemoney <플레이어> <금액>");
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

        // OP 전용 G 몰수 (스코어보드 업데이트 자동 포함)
        economyManager.adminTakeMoney(targetPlayer.getUniqueId(), amount).thenAccept(success -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (success) {
                    // 관리자에게 메시지
                    sender.sendMessage("§a" + targetPlayer.getName() + "님으로부터 " +
                            economyManager.formatMoney(amount) + "G를 차감했습니다.");

                    // 대상 플레이어에게 메시지
                    targetPlayer.sendMessage("§c관리자에 의해 " + economyManager.formatMoney(amount) + "G가 차감되었습니다.");
                    targetPlayer.playSound(targetPlayer.getLocation(), org.bukkit.Sound.ENTITY_ITEM_BREAK, 0.5f, 0.8f);

                    plugin.getLogger().info(String.format("[관리자차감] %s이(가) %s으로부터 %dG 차감",
                            sender.getName(), targetPlayer.getName(), amount));
                } else {
                    sender.sendMessage("§cG 차감에 실패했습니다. (잔액 부족 또는 오류)");
                }
            });
        }).exceptionally(throwable -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                sender.sendMessage("§cG 차감 중 오류가 발생했습니다: " + throwable.getMessage());
                plugin.getLogger().severe("OP 차감 오류: " + throwable.getMessage());
            });
            return null;
        });

        return true;
    }
}