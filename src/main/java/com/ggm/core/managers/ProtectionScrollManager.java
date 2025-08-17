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

public class ProtectionScrollManager implements Listener {

    private final GGMCore plugin;
    private final NamespacedKey protectionKey;

    public ProtectionScrollManager(GGMCore plugin) {
        this.plugin = plugin;
        this.protectionKey = new NamespacedKey(plugin, "protection_scroll");
    }

    /**
     * 파괴방지권 아이템 생성 (종이로 변경)
     */
    public ItemStack createProtectionScroll(String type) {
        ItemStack scroll = new ItemStack(Material.PAPER); // ENCHANTED_BOOK → PAPER로 변경
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

                meta.getPersistentDataContainer().set(protectionKey, PersistentDataType.STRING, "basic");
                break;

            case "premium":
                meta.setDisplayName("§6프리미엄 파괴방지권");
                List<String> premiumLore = new ArrayList<>();
                premiumLore.add("§7무기에 우클릭하여 적용");
                premiumLore.add("§7인첸트 실패 시 무기 + 인첸트북 보호");
                premiumLore.add("§b실패 시: 경험치만 소모, 모든 아이템 보존");
                premiumLore.add("§6§l최고급 보장서!");
                premiumLore.add("§8§l[GGM 파괴방지권]");
                meta.setLore(premiumLore);

                meta.getPersistentDataContainer().set(protectionKey, PersistentDataType.STRING, "premium");
                break;

            default:
                return null;
        }

        // 종이에는 글로우 효과 제거 (더 자연스러움)
        scroll.setItemMeta(meta);
        return scroll;
    }

    // 나머지 메소드들은 동일...
    @EventHandler(priority = EventPriority.HIGH)
    public void onProtectionScrollUse(PlayerInteractEvent event) {
        if (!event.getAction().toString().contains("RIGHT_CLICK")) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (!isProtectionScroll(item)) {
            return;
        }

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

        if (!isValidTargetItem(targetItem)) {
            player.sendMessage("§c이 아이템에는 파괴방지권을 적용할 수 없습니다!");
            player.sendMessage("§7무기, 도구, 방어구에만 적용 가능합니다.");
            return;
        }

        if (hasProtection(targetItem)) {
            player.sendMessage("§c이미 파괴방지권이 적용된 아이템입니다!");
            return;
        }

        String protectionType = getProtectionType(item);
        if (protectionType == null) {
            player.sendMessage("§c잘못된 파괴방지권입니다!");
            return;
        }

        applyProtection(targetItem, protectionType);
        item.setAmount(item.getAmount() - 1);

        String protectionName = protectionType.equals("premium") ? "§6프리미엄 파괴방지권" : "§a기본 파괴방지권";
        player.sendMessage("§a" + protectionName + "이 적용되었습니다!");

        if (protectionType.equals("premium")) {
            player.sendMessage("§7인첸트 실패 시 모든 아이템이 보호됩니다.");
        } else {
            player.sendMessage("§7이제 인첸트 실패 시 무기가 보호됩니다.");
        }

        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.5f);
        player.getWorld().spawnParticle(org.bukkit.Particle.ENCHANTMENT_TABLE,
                player.getLocation().add(0, 1, 0), 20);

        event.setCancelled(true);

        plugin.getLogger().info(String.format("[파괴방지권] %s: %s을 %s에 적용",
                player.getName(), protectionName, targetItem.getType()));
    }

    // 기존 메소드들 유지...
    public boolean isProtectionScroll(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta.getPersistentDataContainer().has(protectionKey, PersistentDataType.STRING);
    }

    public String getProtectionType(ItemStack item) {
        if (!isProtectionScroll(item)) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        return meta.getPersistentDataContainer().get(protectionKey, PersistentDataType.STRING);
    }

    public void applyProtection(ItemStack item, String protectionType) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        NamespacedKey itemProtectionKey = new NamespacedKey(plugin, "item_protection");
        meta.getPersistentDataContainer().set(itemProtectionKey, PersistentDataType.STRING, protectionType);

        List<String> lore = meta.hasLore() ? meta.getLore() : new ArrayList<>();
        lore.removeIf(line -> line.contains("파괴방지"));

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

    public boolean hasProtection(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        NamespacedKey itemProtectionKey = new NamespacedKey(plugin, "item_protection");
        return item.getItemMeta().getPersistentDataContainer()
                .has(itemProtectionKey, PersistentDataType.STRING);
    }

    public String getItemProtectionType(ItemStack item) {
        if (!hasProtection(item)) {
            return null;
        }
        NamespacedKey itemProtectionKey = new NamespacedKey(plugin, "item_protection");
        return item.getItemMeta().getPersistentDataContainer()
                .get(itemProtectionKey, PersistentDataType.STRING);
    }

    public void consumeProtection(ItemStack item) {
        if (!hasProtection(item)) {
            return;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        NamespacedKey itemProtectionKey = new NamespacedKey(plugin, "item_protection");
        meta.getPersistentDataContainer().remove(itemProtectionKey);

        if (meta.hasLore()) {
            List<String> lore = meta.getLore();
            lore.removeIf(line -> line.contains("파괴방지") || line.contains("실패 시:"));
            meta.setLore(lore);
        }

        item.setItemMeta(meta);
    }

    private boolean isValidTargetItem(ItemStack item) {
        String typeName = item.getType().name();

        if (typeName.contains("SWORD") || typeName.contains("BOW") ||
                typeName.contains("CROSSBOW") || typeName.contains("TRIDENT")) {
            return true;
        }

        if (typeName.contains("PICKAXE") || typeName.contains("AXE") ||
                typeName.contains("SHOVEL") || typeName.contains("HOE")) {
            return true;
        }

        if (typeName.contains("HELMET") || typeName.contains("CHESTPLATE") ||
                typeName.contains("LEGGINGS") || typeName.contains("BOOTS")) {
            return true;
        }

        if (item.getType() == Material.FISHING_ROD ||
                item.getType() == Material.FLINT_AND_STEEL ||
                item.getType() == Material.SHEARS) {
            return true;
        }

        return false;
    }
}