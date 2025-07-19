package com.ggm.core.commands;

import com.ggm.core.GGMCore;
import com.ggm.core.managers.EconomyManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

// OP 전용 인벤토리 열기 명령어
public class OpenInvenCommand implements CommandExecutor {
    private final GGMCore plugin;

    public OpenInvenCommand(GGMCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("이 명령어는 플레이어만 사용할 수 있습니다.");
            return true;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("ggm.openinven")) {
            player.sendMessage("§c권한이 없습니다.");
            return true;
        }

        if (args.length != 1) {
            player.sendMessage("§c사용법: /openinven <플레이어>");
            return true;
        }

        Player targetPlayer = Bukkit.getPlayer(args[0]);
        if (targetPlayer == null || !targetPlayer.isOnline()) {
            player.sendMessage("§c플레이어를 찾을 수 없습니다.");
            return true;
        }

        player.openInventory(targetPlayer.getInventory());
        player.sendMessage("§a" + targetPlayer.getName() + "님의 인벤토리를 열었습니다.");

        plugin.getLogger().info(String.format("[인벤토리열기] %s -> %s",
                player.getName(), targetPlayer.getName()));

        return true;
    }
}
