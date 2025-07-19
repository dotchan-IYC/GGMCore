package com.ggm.core.commands;

import com.ggm.core.GGMCore;
import com.ggm.core.managers.EconomyManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;


// OP 전용 G 몰수 명령어
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

        Player targetPlayer = Bukkit.getPlayer(args[0]);
        if (targetPlayer == null) {
            sender.sendMessage("§c플레이어를 찾을 수 없습니다.");
            return true;
        }

        long amount;
        try {
            amount = Long.parseLong(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage("§c올바른 금액을 입력해주세요.");
            return true;
        }

        if (amount <= 0) {
            sender.sendMessage("§c올바른 금액을 입력해주세요.");
            return true;
        }

        economyManager.adminTakeMoney(targetPlayer.getUniqueId(), amount)
                .thenAccept(success -> {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (success) {
                            sender.sendMessage("§a" + targetPlayer.getName() + "님으로부터 " +
                                    economyManager.formatMoney(amount) + "G를 몰수했습니다.");
                            economyManager.notifyOnlinePlayer(targetPlayer.getUniqueId(),
                                    "§c관리자에 의해 " + economyManager.formatMoney(amount) + "G가 차감되었습니다.");

                            // 스코어보드 업데이트
                            plugin.getScoreboardManager().updatePlayerBalance(targetPlayer.getUniqueId());

                            plugin.getLogger().info(String.format("[OP몰수] %s <- %s: %dG",
                                    sender.getName(), targetPlayer.getName(), amount));
                        } else {
                            sender.sendMessage("§c몰수 중 오류가 발생했습니다.");
                        }
                    });
                });

        return true;
    }
}