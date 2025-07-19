package com.ggm.core.commands;

import com.ggm.core.GGMCore;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class EnchantConfigCommand implements CommandExecutor {

    private final GGMCore plugin;

    public EnchantConfigCommand(GGMCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("ggm.enchantconfig")) {
            sender.sendMessage("§c권한이 없습니다.");
            return true;
        }

        if (args.length == 0) {
            showCurrentConfig(sender);
            return true;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            plugin.reloadConfig();
            sender.sendMessage("§a커스텀 인첸트 설정이 리로드되었습니다!");
            showCurrentConfig(sender);
            return true;
        }

        if (args.length == 4 && args[0].equalsIgnoreCase("set")) {
            return setConfig(sender, args[1], args[2], args[3]);
        }

        sender.sendMessage("§c사용법:");
        sender.sendMessage("§7/enchantconfig - 현재 설정 보기");
        sender.sendMessage("§7/enchantconfig reload - 설정 리로드");
        sender.sendMessage("§7/enchantconfig set <인첸트> <설정> <값>");
        sender.sendMessage("§7예시: /enchantconfig set lightning base_chance 10");

        return true;
    }

    private void showCurrentConfig(CommandSender sender) {
        sender.sendMessage("§6=== 커스텀 인첸트 설정 ===");

        // 번개 인첸트
        sender.sendMessage("§e⚡ 번개:");
        sender.sendMessage("  §7기본 확률: §f" + plugin.getConfig().getInt("custom_enchants.lightning.base_chance", 5) + "%");
        sender.sendMessage("  §7레벨당: §f+" + plugin.getConfig().getInt("custom_enchants.lightning.chance_per_level", 5) + "%");
        sender.sendMessage("  §7최대 확률: §f" + plugin.getConfig().getInt("custom_enchants.lightning.max_chance", 25) + "%");

        // 자동수리 인첸트
        sender.sendMessage("§b🔧 자동수리:");
        sender.sendMessage("  §7기본 확률: §f" + plugin.getConfig().getInt("custom_enchants.auto_repair.base_chance", 3) + "%");
        sender.sendMessage("  §7레벨당: §f+" + plugin.getConfig().getInt("custom_enchants.auto_repair.chance_per_level", 3) + "%");
        sender.sendMessage("  §7최대 확률: §f" + plugin.getConfig().getInt("custom_enchants.auto_repair.max_chance", 15) + "%");

        // 흡혈 인첸트
        sender.sendMessage("§c🧛 흡혈:");
        sender.sendMessage("  §7기본 회복: §f" + plugin.getConfig().getDouble("custom_enchants.vampire.heal_percentage", 10.0) + "%");
        sender.sendMessage("  §7레벨당: §f+" + plugin.getConfig().getDouble("custom_enchants.vampire.heal_per_level", 10.0) + "%");

        // 경험증폭 인첸트
        sender.sendMessage("§d💎 경험증폭:");
        sender.sendMessage("  §7기본 경험치: §f+" + plugin.getConfig().getInt("custom_enchants.exp_boost.base_exp", 2));
        sender.sendMessage("  §7레벨당: §f+" + plugin.getConfig().getInt("custom_enchants.exp_boost.exp_per_level", 2));

        sender.sendMessage("§6========================");
    }

    private boolean setConfig(CommandSender sender, String enchant, String setting, String value) {
        String configPath = "custom_enchants." + enchant.toLowerCase() + "." + setting;

        try {
            // 숫자 값 파싱
            if (setting.contains("percentage") || setting.contains("heal")) {
                double doubleValue = Double.parseDouble(value);
                plugin.getConfig().set(configPath, doubleValue);
            } else {
                int intValue = Integer.parseInt(value);
                plugin.getConfig().set(configPath, intValue);
            }

            plugin.saveConfig();
            sender.sendMessage("§a설정이 변경되었습니다: §f" + configPath + " = " + value);

            // 변경된 설정으로 예시 보여주기
            if (enchant.equalsIgnoreCase("lightning") && setting.equals("base_chance")) {
                int newChance = plugin.getConfig().getInt(configPath);
                sender.sendMessage("§7이제 번개 1레벨 확률: " + newChance + "%");
            } else if (enchant.equalsIgnoreCase("auto_repair") && setting.equals("base_chance")) {
                int newChance = plugin.getConfig().getInt(configPath);
                sender.sendMessage("§7이제 자동수리 1레벨 확률: " + newChance + "%");
            }

            return true;

        } catch (NumberFormatException e) {
            sender.sendMessage("§c잘못된 숫자 형식입니다: " + value);
            return true;
        } catch (Exception e) {
            sender.sendMessage("§c설정 변경 중 오류가 발생했습니다: " + e.getMessage());
            return true;
        }
    }
}