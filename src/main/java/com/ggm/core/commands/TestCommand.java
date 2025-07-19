package com.ggm.core.commands;

import com.ggm.core.GGMCore;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public class TestCommand implements CommandExecutor {

    private final GGMCore plugin;

    public TestCommand(GGMCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("이 명령어는 플레이어만 사용할 수 있습니다.");
            return true;
        }

        if (!sender.hasPermission("ggm.test")) {
            sender.sendMessage("§c권한이 없습니다.");
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            player.sendMessage("§c사용법:");
            player.sendMessage("§7/test custombook <인첸트ID> <레벨> - 커스텀 인첸트북 생성");
            player.sendMessage("§7/test pools - 인첸트 풀 확인");
            player.sendMessage("§7/test check - 손에 든 아이템의 커스텀 인첸트 확인");
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "custombook":
                if (args.length < 3) {
                    player.sendMessage("§c사용법: /test custombook <인첸트ID> <레벨>");
                    player.sendMessage("§7예시: /test custombook vampire 2");
                    return true;
                }
                createCustomBook(player, args[1], args[2]);
                break;

            case "pools":
                showEnchantPools(player);
                break;

            case "check":
                checkItemEnchants(player);
                break;

            default:
                player.sendMessage("§c알 수 없는 하위 명령어: " + subCommand);
                break;
        }

        return true;
    }

    private void createCustomBook(Player player, String enchantId, String levelStr) {
        try {
            int level = Integer.parseInt(levelStr);

            // 커스텀 인첸트북 직접 생성
            ItemStack enchantBook = new ItemStack(Material.ENCHANTED_BOOK);
            ItemMeta meta = enchantBook.getItemMeta();

            // NBT에 커스텀 인첸트 정보 저장
            NamespacedKey customKey = new NamespacedKey(plugin, "custom_enchant_" + enchantId);
            meta.getPersistentDataContainer().set(customKey, PersistentDataType.INTEGER, level);

            // GGM 인첸트북 식별 태그 추가
            NamespacedKey ggmKey = new NamespacedKey(plugin, "ggm_enchant_book");
            meta.getPersistentDataContainer().set(ggmKey, PersistentDataType.BYTE, (byte) 1);

            // 이름과 Lore 설정
            String displayName = getEnchantDisplayName(enchantId);
            meta.setDisplayName("§6" + displayName + " " + level + "권 §7(테스트)");

            List<String> lore = new ArrayList<>();
            lore.add("§7커스텀 인첸트: " + displayName + " " + level);
            lore.add("§7효과: §f" + getEnchantDescription(enchantId));
            lore.add("§8§l[GGM 테스트 인첸트북]");
            meta.setLore(lore);

            enchantBook.setItemMeta(meta);

            // 플레이어에게 지급
            if (player.getInventory().firstEmpty() == -1) {
                player.sendMessage("§c인벤토리에 공간이 부족합니다.");
                return;
            }

            player.getInventory().addItem(enchantBook);
            player.sendMessage("§a테스트 커스텀 인첸트북을 생성했습니다: §f" + displayName + " " + level);

        } catch (NumberFormatException e) {
            player.sendMessage("§c잘못된 레벨: " + levelStr);
        }
    }

    private void showEnchantPools(Player player) {
        player.sendMessage("§6=== 인첸트 풀 확인 ===");

        for (String tier : List.of("common", "rare", "epic", "ultimate")) {
            List<String> pool = plugin.getConfig().getStringList("enchant_books." + tier);
            player.sendMessage("§e" + tier.toUpperCase() + " (" + pool.size() + "개):");

            for (String enchant : pool) {
                if (enchant.startsWith("CUSTOM:")) {
                    player.sendMessage("  §b" + enchant + " §7(커스텀)");
                } else {
                    player.sendMessage("  §7" + enchant + " §7(바닐라)");
                }
            }
        }

        player.sendMessage("§6===================");
    }

    private void checkItemEnchants(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();

        if (item.getType() == Material.AIR) {
            player.sendMessage("§c손에 아이템을 들어주세요.");
            return;
        }

        if (!item.hasItemMeta()) {
            player.sendMessage("§7이 아이템에는 메타데이터가 없습니다.");
            return;
        }

        ItemMeta meta = item.getItemMeta();
        player.sendMessage("§6=== 아이템 인첸트 정보 ===");
        player.sendMessage("§7아이템: §f" + item.getType().name());

        // GGM 인첸트북인지 확인 (NBT)
        NamespacedKey ggmKey = new NamespacedKey(plugin, "ggm_enchant_book");
        boolean isGgmBook = meta.getPersistentDataContainer().has(ggmKey, PersistentDataType.BYTE);
        player.sendMessage("§7GGM 인첸트북: " + (isGgmBook ? "§a예" : "§c아니오"));

        // 커스텀 인첸트 확인
        boolean hasCustomEnchants = false;
        for (String enchantId : List.of("vampire", "lightning", "auto_repair", "exp_boost", "high_jump")) {
            NamespacedKey key = new NamespacedKey(plugin, "custom_enchant_" + enchantId);
            if (meta.getPersistentDataContainer().has(key, PersistentDataType.INTEGER)) {
                int level = meta.getPersistentDataContainer().get(key, PersistentDataType.INTEGER);
                player.sendMessage("  §b" + getEnchantDisplayName(enchantId) + ": §f" + level + "레벨");
                hasCustomEnchants = true;
            }
        }

        // 바닐라 인첸트 확인
        if (!item.getEnchantments().isEmpty()) {
            player.sendMessage("§7바닐라 인첸트:");
            item.getEnchantments().forEach((enchant, level) -> {
                player.sendMessage("  §7" + enchant.getKey().getKey() + ": §f" + level + "레벨");
            });
        }

        if (!hasCustomEnchants && item.getEnchantments().isEmpty()) {
            player.sendMessage("§7이 아이템에는 인첸트가 없습니다.");
        }

        player.sendMessage("§6========================");
    }

    private String getEnchantDisplayName(String enchantId) {
        return switch (enchantId.toLowerCase()) {
            case "vampire" -> "흡혈";
            case "lightning" -> "번개";
            case "auto_repair" -> "자동수리";
            case "exp_boost" -> "경험증폭";
            case "high_jump" -> "점프";
            default -> enchantId;
        };
    }

    private String getEnchantDescription(String enchantId) {
        return switch (enchantId.toLowerCase()) {
            case "vampire" -> "공격 시 피해량의 일부만큼 체력 회복";
            case "lightning" -> "공격 시 확률적으로 번개 소환";
            case "auto_repair" -> "블록 채굴 시 확률적으로 내구도 회복";
            case "exp_boost" -> "몹 처치 시 추가 경험치 획득";
            case "high_jump" -> "착용 시 점프력 증가";
            default -> "알 수 없는 효과";
        };
    }
}