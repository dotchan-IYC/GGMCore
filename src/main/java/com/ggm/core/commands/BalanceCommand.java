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

        if (!player.hasPermission("ggm.balance")) {
            economyManager.sendMessage(player, "no_permission");
            return true;
        }

        economyManager.getBalance(player.getUniqueId())
                .thenAccept(balance -> {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        economyManager.sendMessage(player, "balance",
                                "balance", economyManager.formatMoney(balance));
                    });
                });

        return true;
    }
}
