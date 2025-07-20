package com.ggm.core.commands;

import com.ggm.core.GGMCore;
import com.ggm.core.managers.EconomyManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class BalanceCommand implements CommandExecutor {

    private final GGMCore plugin;
    private final EconomyManager economyManager;

    public BalanceCommand(GGMCore plugin) {
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

        // 다른 플레이어 잔액 조회 (권한 확인)
        if (args.length > 0) {
            if (!player.hasPermission("ggm.balance.others")) {
                player.sendMessage("§c다른 플레이어의 잔액을 조회할 권한이 없습니다.");
                return true;
            }

            Player targetPlayer = Bukkit.getPlayer(args[0]);
            if (targetPlayer == null) {
                player.sendMessage("§c플레이어를 찾을 수 없습니다: " + args[0]);
                return true;
            }

            // 대상 플레이어 잔액 조회
            economyManager.getBalance(targetPlayer.getUniqueId()).thenAccept(balance -> {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                    player.sendMessage("§e§l💰 " + targetPlayer.getName() + "님의 G 잔액");
                    player.sendMessage("");
                    player.sendMessage("§a보유 G: §6" + economyManager.formatMoney(balance) + "G");
                    player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                });
            }).exceptionally(throwable -> {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    player.sendMessage("§c잔액 조회 중 오류가 발생했습니다: " + throwable.getMessage());
                });
                return null;
            });

            return true;
        }

        // 자신의 잔액 조회
        economyManager.getBalance(player.getUniqueId()).thenAccept(balance -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                player.sendMessage("§e§l💰 내 G 잔액");
                player.sendMessage("");
                player.sendMessage("§a보유 G: §6" + economyManager.formatMoney(balance) + "G");

                // 잔액에 따른 추가 정보
                if (balance >= 1000000) {
                    player.sendMessage("§d§l✨ 백만장자! ✨");
                } else if (balance >= 100000) {
                    player.sendMessage("§6§l💎 부자이시네요!");
                } else if (balance >= 10000) {
                    player.sendMessage("§b§l💰 재정이 안정적입니다!");
                } else if (balance < 1000) {
                    player.sendMessage("§c§l🆘 G가 부족합니다!");
                }

                player.sendMessage("");
                player.sendMessage("§7명령어: §f/pay <플레이어> <금액> §7- 송금");
                player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

                // ActionBar로도 표시
                player.sendActionBar("§6💰 G: " + economyManager.formatMoney(balance) + "G");
            });
        }).exceptionally(throwable -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                player.sendMessage("§c잔액 조회 중 오류가 발생했습니다: " + throwable.getMessage());
                plugin.getLogger().severe("잔액 조회 오류 (" + player.getName() + "): " + throwable.getMessage());
            });
            return null;
        });

        return true;
    }
}