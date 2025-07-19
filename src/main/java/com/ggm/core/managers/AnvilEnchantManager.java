package com.ggm.core.managers;

import com.ggm.core.GGMCore;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

/**
 * 모루에서 GGM 커스텀 인첸트북을 아이템에 적용하는 시스템
 */
public class AnvilEnchantManager implements Listener {

    private final GGMCore plugin;
    private final CustomEnchantManager customEnchantManager;

    // 아이템 타입별 호환되는 인첸트들
    private final Map<String, List<String>> itemEnchantCompatibility;

    public AnvilEnchantManager(GGMCore plugin) {
        this.plugin = plugin;
        this.customEnchantManager = plugin.getCustomEnchantManager();
        this.itemEnchantCompatibility = new HashMap<>();

        setupEnchantCompatibility();
    }

    /**
     * 아이템 타입별 호환 인첸트 설정
     */
    private void setupEnchantCompatibility() {
        // 검류
        itemEnchantCompatibility.put("SWORD", Arrays.asList(
                "vampire", "lightning", "poison_blade",
                "freeze", "soul_reaper", "life_steal", "berserker"
        ));

        // 활
        itemEnchantCompatibility.put("BOW", Arrays.asList(
                "explosive_arrow", "piercing_shot"
        ));

        // 곡괭이
        itemEnchantCompatibility.put("PICKAXE", Arrays.asList(
                "auto_repair", "auto_smelt", "area_mining", "vein_miner"
        ));

        // 도끼
        itemEnchantCompatibility.put("AXE", Arrays.asList(
                "auto_repair", "tree_feller", "auto_smelt"
        ));

        // 삽
        itemEnchantCompatibility.put("SHOVEL", Arrays.asList(
                "auto_repair", "area_mining", "auto_smelt"
        ));

        // 부츠
        itemEnchantCompatibility.put("BOOTS", Arrays.asList(
                "high_jump", "spider_walk", "water_walker"
        ));

        // 헬멧
        itemEnchantCompatibility.put("HELMET", Arrays.asList(
                "immunity", "regeneration"
        ));

        // 흉갑
        itemEnchantCompatibility.put("CHESTPLATE", Arrays.asList(
                "regeneration"
        ));

        // 레깅스
        itemEnchantCompatibility.put("LEGGINGS", Arrays.asList(
                "regeneration"
        ));
    }

    /**
     * 모루 준비 이벤트 - 인첸트 적용 미리보기
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        AnvilInventory inventory = event.getInventory();
        ItemStack targetItem = inventory.getItem(0);      // 인첸트를 받을 아이템
        ItemStack enchantBook = inventory.getItem(1);     // GGM 인첸트북

        // 둘 다 있는지 확인
        if (targetItem == null || enchantBook == null) {
            return;
        }

        // GGM 인첸트북인지 확인
        if (!isGGMEnchantBook(enchantBook)) {
            return;
        }

        // 인첸트북에서 커스텀 인첸트 정보 추출
        EnchantInfo enchantInfo = extractEnchantFromBook(enchantBook);
        if (enchantInfo == null) {
            return;
        }

        // 아이템과 인첸트 호환성 확인
        if (!isCompatible(targetItem, enchantInfo.enchantId)) {
            // 호환되지 않는 경우 결과 차단
            event.setResult(null);
            return;
        }

        // 이미 같은 인첸트가 있는지 확인
        int currentLevel = customEnchantManager.getCustomEnchantLevel(targetItem, enchantInfo.enchantId);

        // 결과 아이템 생성
        ItemStack result = targetItem.clone();

        // 인첸트 레벨 합성 (기존 + 새로운, 최대 5레벨)
        int newLevel = Math.min(currentLevel + enchantInfo.level, 5);
        result = customEnchantManager.applyCustomEnchant(result, enchantInfo.enchantId, newLevel);

        // 경험치 비용 설정 (레벨당 5 경험치 레벨)
        int expCost = enchantInfo.level * 5 + (currentLevel > 0 ? 5 : 0); // 업그레이드 시 추가 비용
        inventory.setRepairCost(Math.min(expCost, 39)); // 최대 39레벨

        // 결과 아이템에 성공 정보 저장 (클릭 시 확인용)
        ItemMeta resultMeta = result.getItemMeta();
        if (resultMeta != null) {
            NamespacedKey enchantKey = new NamespacedKey(plugin, "pending_enchant_id");
            NamespacedKey levelKey = new NamespacedKey(plugin, "pending_enchant_level");

            resultMeta.getPersistentDataContainer().set(enchantKey, PersistentDataType.STRING, enchantInfo.enchantId);
            resultMeta.getPersistentDataContainer().set(levelKey, PersistentDataType.INTEGER, newLevel);
            result.setItemMeta(resultMeta);
        }

        // 결과 설정
        event.setResult(result);

        // 디버그 로그
        plugin.getLogger().info(String.format("모루 인첸트 준비: %s + %s %d레벨 = %s %d레벨 (비용: %d레벨)",
                targetItem.getType(), enchantInfo.enchantId, enchantInfo.level,
                enchantInfo.enchantId, newLevel, expCost));
    }

    /**
     * 모루 클릭 이벤트 - 실제 인첸트 적용
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onAnvilClick(InventoryClickEvent event) {
        if (!(event.getInventory() instanceof AnvilInventory)) {
            return;
        }

        if (event.getSlot() != 2) { // 결과 슬롯이 아니면 무시
            return;
        }

        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        AnvilInventory anvil = (AnvilInventory) event.getInventory();
        ItemStack result = event.getCurrentItem();

        if (result == null) {
            return;
        }

        // 대기 중인 인첸트 정보 확인
        ItemMeta resultMeta = result.getItemMeta();
        if (resultMeta == null) {
            return;
        }

        NamespacedKey enchantKey = new NamespacedKey(plugin, "pending_enchant_id");
        NamespacedKey levelKey = new NamespacedKey(plugin, "pending_enchant_level");

        if (!resultMeta.getPersistentDataContainer().has(enchantKey, PersistentDataType.STRING)) {
            return;
        }

        String enchantId = resultMeta.getPersistentDataContainer().get(enchantKey, PersistentDataType.STRING);
        int enchantLevel = resultMeta.getPersistentDataContainer().get(levelKey, PersistentDataType.INTEGER);

        // 경험치 레벨 확인
        int requiredLevels = anvil.getRepairCost();
        if (player.getLevel() < requiredLevels) {
            player.sendMessage("§c경험치 레벨이 부족합니다! (필요: " + requiredLevels + "레벨)");
            event.setCancelled(true);
            return;
        }

        // 성공 확률 계산 (높은 레벨일수록 실패 확률 증가)
        int successChance = Math.max(70, 100 - (enchantLevel * 10)); // 1레벨: 90%, 2레벨: 80%, ...
        boolean success = new Random().nextInt(100) < successChance;

        if (!success) {
            // 실패 시 - 파괴방지권 확인
            ItemStack targetItem = anvil.getItem(0);
            ItemStack enchantBook = anvil.getItem(1);

            boolean hasProtection = plugin.getProtectionScrollManager().hasProtection(targetItem);
            String protectionType = plugin.getProtectionScrollManager().getItemProtectionType(targetItem);

            if (hasProtection) {
                // 파괴방지권이 있는 경우
                player.sendMessage("§c인첸트 적용에 실패했습니다! (" + successChance + "% 확률)");

                if ("premium".equals(protectionType)) {
                    // 프리미엄: 무기 + 인첸트북 모두 보호
                    player.sendMessage("§6프리미엄 파괴방지권으로 무기와 인첸트북이 보호되었습니다!");
                    player.sendMessage("§b완벽한 보호! §7경험치 " + requiredLevels + "레벨만 소모되었습니다.");

                    // 파괴방지권만 소모
                    plugin.getProtectionScrollManager().consumeProtection(targetItem);

                    // 아이템들은 그대로 유지, 결과만 제거
                    anvil.setItem(2, null);

                } else {
                    // 기본: 무기만 보호, 인첸트북은 소모
                    player.sendMessage("§a기본 파괴방지권으로 무기가 보호되었습니다!");
                    player.sendMessage("§7인첸트북과 경험치 " + requiredLevels + "레벨이 소모되었습니다.");

                    // 파괴방지권 소모
                    plugin.getProtectionScrollManager().consumeProtection(targetItem);

                    // 인첸트북만 제거, 무기는 보호
                    anvil.setItem(1, null);  // 인첸트북 제거
                    anvil.setItem(2, null);  // 결과 제거
                }

                // 경험치 소모
                player.setLevel(player.getLevel() - requiredLevels);

                // 보호 효과
                player.getWorld().playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.2f);
                player.getWorld().spawnParticle(org.bukkit.Particle.TOTEM,
                        player.getLocation().add(0, 1, 0), 30);

            } else {
                // 파괴방지권이 없는 경우 (기존 로직)
                player.sendMessage("§c인첸트 적용에 실패했습니다! (" + successChance + "% 확률)");
                player.sendMessage("§7아이템과 인첸트북이 모두 사라졌습니다...");
                player.sendMessage("§e팁: 파괴방지권을 사용하면 실패 시 아이템을 보호할 수 있습니다!");

                // 경험치는 절반만 소모
                player.setLevel(player.getLevel() - (requiredLevels / 2));

                // 모든 아이템 제거
                anvil.setItem(0, null);  // 대상 아이템
                anvil.setItem(1, null);  // 인첸트북
                anvil.setItem(2, null);  // 결과 아이템

                // 실패 효과
                player.getWorld().playSound(player.getLocation(), Sound.BLOCK_ANVIL_BREAK, 1.0f, 0.5f);
                player.getWorld().spawnParticle(org.bukkit.Particle.SMOKE_LARGE,
                        player.getLocation().add(0, 1, 0), 20);
            }

            event.setCancelled(true);
            return;
        }

        // 성공 시
        // 경험치 소모
        player.setLevel(player.getLevel() - requiredLevels);

        // 대기 중인 인첸트 정보 제거 (결과 아이템에서)
        resultMeta.getPersistentDataContainer().remove(enchantKey);
        resultMeta.getPersistentDataContainer().remove(levelKey);
        result.setItemMeta(resultMeta);

        // 인벤토리 공간 확인 후 아이템 지급
        if (player.getInventory().firstEmpty() == -1) {
            // 인벤토리가 가득 찬 경우 땅에 드롭
            player.getWorld().dropItemNaturally(player.getLocation(), result);
            player.sendMessage("§7인벤토리가 가득 차서 아이템을 땅에 떨어뜨렸습니다.");
        } else {
            // 인벤토리에 아이템 추가
            player.getInventory().addItem(result);
        }

        // 모루에서 원본 아이템들과 결과 아이템 제거
        anvil.setItem(0, null);  // 대상 아이템
        anvil.setItem(1, null);  // 인첸트북
        anvil.setItem(2, null);  // 결과 아이템

        // 성공 메시지 및 효과
        String enchantName = getEnchantDisplayName(enchantId);
        player.sendMessage("§a인첸트 적용 성공! " + enchantName + " " + enchantLevel + "레벨");
        player.sendMessage("§7경험치 " + requiredLevels + "레벨을 소모했습니다.");

        // 성공 효과
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.2f);
        player.getWorld().spawnParticle(org.bukkit.Particle.ENCHANTMENT_TABLE,
                player.getLocation().add(0, 1, 0), 30);

        // 로그 기록
        plugin.getLogger().info(String.format("[모루인첸트] %s: %s %d레벨 적용 성공 (비용: %d레벨)",
                player.getName(), enchantId, enchantLevel, requiredLevels));

        // 이벤트 취소 (우리가 수동으로 아이템을 관리)
        event.setCancelled(true);
    }

    /**
     * GGM 인첸트북인지 확인
     */
    private boolean isGGMEnchantBook(ItemStack item) {
        if (item == null || item.getType() != Material.ENCHANTED_BOOK) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;

        // NBT 태그로 확인
        NamespacedKey ggmKey = new NamespacedKey(plugin, "ggm_enchant_book");
        if (meta.getPersistentDataContainer().has(ggmKey, PersistentDataType.BYTE)) {
            return true;
        }

        // Lore로 확인 (백업)
        if (meta.hasLore()) {
            for (String lore : meta.getLore()) {
                if (lore.contains("§8§l[GGM 인첸트북]")) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * 인첸트북에서 커스텀 인첸트 정보 추출
     */
    private EnchantInfo extractEnchantFromBook(ItemStack book) {
        ItemMeta meta = book.getItemMeta();
        if (meta == null) return null;

        // 모든 커스텀 인첸트 확인
        for (String enchantId : customEnchantManager.getCustomEnchants().keySet()) {
            NamespacedKey key = new NamespacedKey(plugin, "custom_enchant_" + enchantId);
            if (meta.getPersistentDataContainer().has(key, PersistentDataType.INTEGER)) {
                int level = meta.getPersistentDataContainer().get(key, PersistentDataType.INTEGER);
                return new EnchantInfo(enchantId, level);
            }
        }

        return null;
    }

    /**
     * 아이템과 인첸트 호환성 확인
     */
    private boolean isCompatible(ItemStack item, String enchantId) {
        String itemType = getItemCategory(item.getType());
        if (itemType == null) return false;

        List<String> compatibleEnchants = itemEnchantCompatibility.get(itemType);
        return compatibleEnchants != null && compatibleEnchants.contains(enchantId);
    }

    /**
     * 아이템 타입을 카테고리로 분류
     */
    private String getItemCategory(Material material) {
        String name = material.name();

        if (name.contains("SWORD")) return "SWORD";
        if (name.contains("BOW")) return "BOW";
        if (name.contains("PICKAXE")) return "PICKAXE";
        if (name.contains("AXE") && !name.contains("PICKAXE")) return "AXE";
        if (name.contains("SHOVEL")) return "SHOVEL";
        if (name.contains("BOOTS")) return "BOOTS";
        if (name.contains("HELMET")) return "HELMET";
        if (name.contains("CHESTPLATE")) return "CHESTPLATE";
        if (name.contains("LEGGINGS")) return "LEGGINGS";

        return null;
    }

    /**
     * 인첸트 표시 이름 가져오기
     */
    private String getEnchantDisplayName(String enchantId) {
        CustomEnchantManager.CustomEnchant enchant = customEnchantManager.getCustomEnchants().get(enchantId);
        return enchant != null ? enchant.getDisplayName() : enchantId;
    }

    /**
     * 인첸트 정보 클래스
     */
    private static class EnchantInfo {
        final String enchantId;
        final int level;

        EnchantInfo(String enchantId, int level) {
            this.enchantId = enchantId;
            this.level = level;
        }
    }

    /**
     * 호환 가능한 인첸트 목록 반환 (디버그용)
     */
    public List<String> getCompatibleEnchants(ItemStack item) {
        String itemType = getItemCategory(item.getType());
        return itemType != null ? itemEnchantCompatibility.getOrDefault(itemType, new ArrayList<>()) : new ArrayList<>();
    }

    /**
     * 인첸트 호환성 추가 (플러그인에서 동적으로 추가 가능)
     */
    public void addEnchantCompatibility(String itemType, String enchantId) {
        itemEnchantCompatibility.computeIfAbsent(itemType, k -> new ArrayList<>()).add(enchantId);
    }
}