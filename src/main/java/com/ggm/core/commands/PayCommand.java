package com.ggm.core.commands;

import com.ggm.core.GGMCore;
import com.ggm.core.managers.EconomyManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class PayCommand implements CommandExecutor {

    private final GGMCore plugin;
    private final EconomyManager economyManager;

    public PayCommand(GGMCore plugin) {
        this.plugin = plugin;
        this.economyManager = plugin.getEconomyManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("이 명령어는 플레이어만 사용할 수 있습니다.");
            return true;
        }

        Player player = (Player) sender;

        if (args.length != 2) {
            player.sendMessage("§c사용법: /pay <플레이어> <금액>");
            return true;
        }

        // 대상 플레이어 확인
        Player targetPlayer = Bukkit.getPlayer(args[0]);
        if (targetPlayer == null) {
            economyManager.sendMessage(player, "player_not_found");
            return true;
        }

        if (targetPlayer.equals(player)) {
            player.sendMessage("§c자기 자신에게는 송금할 수 없습니다.");
            return true;
        }

        // 금액 파싱
        long amount;
        try {
            amount = Long.parseLong(args[1]);
        } catch (NumberFormatException e) {
            economyManager.sendMessage(player, "invalid_amount");
            return true;
        }

        if (amount <= 0) {
            economyManager.sendMessage(player, "invalid_amount");
            return true;
        }

        // 송금 실행 (비동기) - 스코어보드 업데이트 자동 포함
        economyManager.transferMoney(player.getUniqueId(), targetPlayer.getUniqueId(), amount)
                .thenAccept(result -> {
                    // 메인 스레드에서 메시지 전송
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (result.isSuccess()) {
                            // 송금자에게 메시지
                            economyManager.sendMessage(player, "transfer_success",
                                    "player", targetPlayer.getName(),
                                    "amount", economyManager.formatMoney(result.getAmount()),
                                    "fee", economyManager.formatMoney(result.getFee())
                            );

                            // 받는 사람에게 메시지
                            economyManager.sendMessage(targetPlayer, "receive_money",
                                    "player", player.getName(),
                                    "amount", economyManager.formatMoney(result.getAmount())
                            );

                            // 성공 효과음
                            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);
                            targetPlayer.playSound(targetPlayer.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);

                            plugin.getLogger().info(String.format("[송금] %s -> %s: %dG (수수료: %dG)",
                                    player.getName(), targetPlayer.getName(), result.getAmount(), result.getFee()));

                        } else {
                            player.sendMessage("§c" + result.getMessage());
                        }
                    });
                })
                .exceptionally(throwable -> {
                    // 오류 처리
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        player.sendMessage("§c송금 중 오류가 발생했습니다. 관리자에게 문의해주세요.");
                        plugin.getLogger().severe("송금 중 오류 발생: " + throwable.getMessage());
                        throwable.printStackTrace();
                    });
                    return null;
                });

        return true;
    }
}