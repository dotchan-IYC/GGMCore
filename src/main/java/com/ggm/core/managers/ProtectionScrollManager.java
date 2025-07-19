package com.ggm.core.managers;

import com.ggm.core.GGMCore;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * 파괴방지권 시스템 - 인첸트 실패 시 아이템 보호
 */
public class ProtectionScrollManager implements Listener {

    private final GGMCore plugin;
    private final NamespacedKey protectionKey;

    public ProtectionScrollManager(GGMCore plugin) {
        this.plugin = plugin;
        this.protectionKey = new NamespacedKey(plugin, "protection_scroll");
    }

    /**
     * 파괴방지권 아이템 생성
     */
    public ItemStack createProtectionScroll(String type) {
        ItemStack scroll = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta meta = scroll.getItemMeta();

        switch (type.toLowerCase()) {
            case "basic":
                meta.setDisplayName("§a기본 파괴방지권");
                List<String> basicLore = new ArrayList<>();
                basicLore.add("§7무기에 우클릭하여 적용");
                basicLore.add("§7인첸트 실패 시 무기 보호");
                basicLore.add("§c실패 시: 경험치만 소모, 무기 보존");
                basicLore.add("§8§l[GGM 파괴방지권]");
                meta.setLore(basicLore);

                // NBT에 파괴방지권 정보 저장
                meta.getPersistentDataContainer().set(protectionKey, PersistentDataType.STRING, "basic");
                break;

            case "premium":
                meta.setDisplayName("§6프리미엄 파괴방지권");
                List<String> premiumLore = new ArrayList<>();
                premiumLore.add("§7무기에 우클릭하여 적용");
                premiumLore.add("§7인첸트 실패 시 무기 + 인첸트북 보호");
                premiumLore.add("§b실패 시: 경험치만 소모, 모든 아이템 보존");
                premiumLore.add("§8§l[GGM 파괴방지권]");
                meta.setLore(premiumLore);

                meta.getPersistentDataContainer().set(protectionKey, PersistentDataType.STRING, "premium");
                break;

            default:
                return null;
        }

        // 글로우 효과
        meta.addEnchant(org.bukkit.enchantments.Enchantment.LURE, 1, true);
        meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);

        scroll.setItemMeta(meta);
        return scroll;
    }

    /**
     * 파괴방지권 우클릭 이벤트
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onProtectionScrollUse(PlayerInteractEvent event) {
        if (!event.getAction().toString().contains("RIGHT_CLICK")) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        // 파괴방지권인지 확인
        if (!isProtectionScroll(item)) {
            return;
        }

        // 대상 아이템 확인 (반대 손)
        ItemStack targetItem = null;
        if (event.getHand() == org.bukkit.inventory.EquipmentSlot.HAND) {
            targetItem = player.getInventory().getItemInOffHand();
        } else {
            targetItem = player.getInventory().getItemInMainHand();
        }

        if (targetItem == null || targetItem.getType() == Material.AIR) {
            player.sendMessage("§c다른 손에 보호할 아이템을 들어주세요!");
            return;
        }

        // 무기/도구인지 확인
        if (!isValidTargetItem(targetItem)) {
            player.sendMessage("§c이 아이템에는 파괴방지권을 적용할 수 없습니다!");
            player.sendMessage("§7무기, 도구, 방어구에만 적용 가능합니다.");
            return;
        }

        // 이미 파괴방지권이 적용되어 있는지 확인
        if (hasProtection(targetItem)) {
            player.sendMessage("§c이미 파괴방지권이 적용된 아이템입니다!");
            return;
        }

        // 파괴방지권 타입 확인
        String protectionType = getProtectionType(item);
        if (protectionType == null) {
            player.sendMessage("§c잘못된 파괴방지권입니다!");
            return;
        }

        // 파괴방지권 적용
        applyProtection(targetItem, protectionType);

        // 파괴방지권 아이템 소모
        item.setAmount(item.getAmount() - 1);

        // 성공 메시지 및 효과
        String protectionName = protectionType.equals("premium") ? "§6프리미엄 파괴방지권" : "§a기본 파괴방지권";
        player.sendMessage("§a" + protectionName + "이 적용되었습니다!");

        if (protectionType.equals("premium")) {
            player.sendMessage("§b§l최고급 보장! §7인첸트 실패 시 모든 아이템이 보호됩니다.");
        } else {
            player.sendMessage("§7이제 인첸트 실패 시 무기가 보호됩니다.");
        }

        // 효과
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.5f);
        player.getWorld().spawnParticle(org.bukkit.Particle.ENCHANTMENT_TABLE,
                player.getLocation().add(0, 1, 0), 20);

        event.setCancelled(true);

        // 로그
        plugin.getLogger().info(String.format("[파괴방지권] %s: %s을 %s에 적용",
                player.getName(), protectionName, targetItem.getType()));
    }

    /**
     * 파괴방지권인지 확인
     */
    public boolean isProtectionScroll(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        return meta.getPersistentDataContainer().has(protectionKey, PersistentDataType.STRING);
    }

    /**
     * 파괴방지권 타입 가져오기
     */
    public String getProtectionType(ItemStack item) {
        if (!isProtectionScroll(item)) {
            return null;
        }

        ItemMeta meta = item.getItemMeta();
        return meta.getPersistentDataContainer().get(protectionKey, PersistentDataType.STRING);
    }

    /**
     * 아이템에 파괴방지 적용
     */
    public void applyProtection(ItemStack item, String protectionType) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        // 파괴방지 정보 저장
        NamespacedKey itemProtectionKey = new NamespacedKey(plugin, "item_protection");
        meta.getPersistentDataContainer().set(itemProtectionKey, PersistentDataType.STRING, protectionType);

        // Lore에 파괴방지 표시 추가
        List<String> lore = meta.hasLore() ? meta.getLore() : new ArrayList<>();

        // 기존 파괴방지 lore 제거
        lore.removeIf(line -> line.contains("파괴방지"));

        // 새 파괴방지 lore 추가
        if (protectionType.equals("premium")) {
            lore.add("§6프리미엄 파괴방지 적용됨");
            lore.add("§7실패 시: 무기 + 인첸트북 보호");
        } else {
            lore.add("§a기본 파괴방지 적용됨");
            lore.add("§7실패 시: 무기만 보호");
        }

        meta.setLore(lore);
        item.setItemMeta(meta);
    }

    /**
     * 아이템에 파괴방지가 적용되어 있는지 확인
     */
    public boolean hasProtection(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }

        NamespacedKey itemProtectionKey = new NamespacedKey(plugin, "item_protection");
        return item.getItemMeta().getPersistentDataContainer()
                .has(itemProtectionKey, PersistentDataType.STRING);
    }

    /**
     * 아이템의 파괴방지 타입 가져오기
     */
    public String getItemProtectionType(ItemStack item) {
        if (!hasProtection(item)) {
            return null;
        }

        NamespacedKey itemProtectionKey = new NamespacedKey(plugin, "item_protection");
        return item.getItemMeta().getPersistentDataContainer()
                .get(itemProtectionKey, PersistentDataType.STRING);
    }

    /**
     * 파괴방지 소모 (인첸트 실패 시 호출)
     */
    public void consumeProtection(ItemStack item) {
        if (!hasProtection(item)) {
            return;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        // 파괴방지 정보 제거
        NamespacedKey itemProtectionKey = new NamespacedKey(plugin, "item_protection");
        meta.getPersistentDataContainer().remove(itemProtectionKey);

        // Lore에서 파괴방지 관련 줄 제거
        if (meta.hasLore()) {
            List<String> lore = meta.getLore();
            lore.removeIf(line -> line.contains("파괴방지") || line.contains("실패 시:"));
            meta.setLore(lore);
        }

        item.setItemMeta(meta);
    }

    /**
     * 파괴방지권을 적용할 수 있는 아이템인지 확인
     */
    private boolean isValidTargetItem(ItemStack item) {
        String typeName = item.getType().name();

        // 무기류
        if (typeName.contains("SWORD") || typeName.contains("BOW") ||
                typeName.contains("CROSSBOW") || typeName.contains("TRIDENT")) {
            return true;
        }

        // 도구류
        if (typeName.contains("PICKAXE") || typeName.contains("AXE") ||
                typeName.contains("SHOVEL") || typeName.contains("HOE")) {
            return true;
        }

        // 방어구류
        if (typeName.contains("HELMET") || typeName.contains("CHESTPLATE") ||
                typeName.contains("LEGGINGS") || typeName.contains("BOOTS")) {
            return true;
        }

        // 낚싯대, 부싯돌 등
        if (item.getType() == Material.FISHING_ROD ||
                item.getType() == Material.FLINT_AND_STEEL ||
                item.getType() == Material.SHEARS) {
            return true;
        }

        return false;
    }

    /**
     * 파괴방지권 정보 조회 (디버깅용)
     */
    public void showProtectionInfo(Player player, ItemStack item) {
        if (!hasProtection(item)) {
            player.sendMessage("§7이 아이템에는 파괴방지가 적용되지 않았습니다.");
            return;
        }

        String protectionType = getItemProtectionType(item);
        String protectionName = protectionType.equals("premium") ? "§6프리미엄 파괴방지" : "§a기본 파괴방지";

        player.sendMessage("§6=== 파괴방지 정보 ===");
        player.sendMessage("§7아이템: §f" + item.getType().name());
        player.sendMessage("§7보호 타입: " + protectionName);

        if (protectionType.equals("premium")) {
            player.sendMessage("§7효과: §b실패 시 무기 + 인첸트북 보호");
            player.sendMessage("§b§l최고급 보장서!");
        } else {
            player.sendMessage("§7효과: §a실패 시 무기만 보호");
        }

        player.sendMessage("§6==================");
    }
}