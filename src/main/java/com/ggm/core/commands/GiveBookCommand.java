// GiveBookCommand 클래스
package com.ggm.core.commands;

import com.ggm.core.GGMCore;
import com.ggm.core.managers.EnchantBookManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.List;

public class GiveBookCommand implements CommandExecutor {

    private final GGMCore plugin;
    private final EnchantBookManager enchantBookManager;
    private final List<String> validTiers = Arrays.asList("common", "rare", "epic", "ultimate");

    public GiveBookCommand(GGMCore plugin) {
        this.plugin = plugin;
        this.enchantBookManager = plugin.getEnchantBookManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("ggm.givebook")) {
            sender.sendMessage("§c권한이 없습니다.");
            return true;
        }

        if (args.length != 1) {
            sender.sendMessage("§c사용법: /givebook <common|rare|epic|ultimate>");
            return true;
        }

        String tier = args[0].toLowerCase();
        if (!validTiers.contains(tier)) {
            sender.sendMessage("§c올바른 등급을 입력해주세요: common, rare, epic, ultimate");
            return true;
        }

        // 콘솔에서 실행하는 경우 (추후 다른 플레이어에게 지급하는 기능 추가 가능)
        if (!(sender instanceof Player)) {
            sender.sendMessage("이 명령어는 플레이어만 사용할 수 있습니다.");
            return true;
        }

        Player player = (Player) sender;

        // 랜덤 인첸트북 상자 생성
        ItemStack enchantBox = enchantBookManager.createRandomEnchantBox(tier);
        if (enchantBox == null) {
            sender.sendMessage("§c인첸트북 상자 생성에 실패했습니다.");
            return true;
        }

        // 인벤토리에 공간이 있는지 확인
        if (player.getInventory().firstEmpty() == -1) {
            sender.sendMessage("§c인벤토리에 공간이 부족합니다.");
            return true;
        }

        // 아이템 지급
        player.getInventory().addItem(enchantBox);
        sender.sendMessage("§a" + tier.toUpperCase() + " 등급 랜덤 인첸트북 상자를 지급했습니다!");

        plugin.getLogger().info(String.format("[인첸트북지급] %s: %s 등급",
                player.getName(), tier.toUpperCase()));

        return true;
    }
}