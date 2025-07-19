package com.ggm.core.managers;

import com.ggm.core.GGMCore;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.enchantment.PrepareItemEnchantEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Iterator;

public class EnchantRestrictionManager implements Listener {

    private final GGMCore plugin;
    private final NamespacedKey ggmBookKey;

    public EnchantRestrictionManager(GGMCore plugin) {
        this.plugin = plugin;
        this.ggmBookKey = new NamespacedKey(plugin, "ggm_enchant_book");
    }

    /**
     * 제한 기능이 활성화되어 있는지 확인
     */
    private boolean isRestrictionEnabled() {
        return plugin.getConfig().getBoolean("enchant_restrictions.enabled", true);
    }

    /**
     * 일반 인첸트북 차단이 활성화되어 있는지 확인
     */
    private boolean isBlockEnchantBooks() {
        return plugin.getConfig().getBoolean("enchant_restrictions.block_enchant_books", true);
    }

    /**
     * 인첸트 테이블 차단이 활성화되어 있는지 확인
     */
    private boolean isBlockEnchantTable() {
        return plugin.getConfig().getBoolean("enchant_restrictions.block_enchant_table", true);
    }

    /**
     * 인첸트북 드롭 차단이 활성화되어 있는지 확인
     */
    private boolean isBlockBookDrops() {
        return plugin.getConfig().getBoolean("enchant_restrictions.block_book_drops", true);
    }

    /**
     * 야생 서버에서 인첸트 테이블 허용하는지 확인 (자동 감지)
     */
    private boolean isAllowEnchantTableOnSurvival() {
        return plugin.getServerDetector().isEnchantTableAllowed();
    }

    /**
     * GGM 인첸트북만 허용하는지 확인
     */
    private boolean isAllowGgmBooksOnly() {
        return plugin.getConfig().getBoolean("enchant_restrictions.allow_ggm_books", true);
    }

    /**
     * GGM 인첸트북인지 확인 (바닐라 + 커스텀 모두, NBT + Lore 검사)
     */
    private boolean isGgmEnchantBook(ItemStack item) {
        if (item == null || item.getType() != Material.ENCHANTED_BOOK) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;

        // 방법 1: NBT 태그로 확인 (가장 확실함)
        if (meta.getPersistentDataContainer().has(ggmBookKey, PersistentDataType.BYTE)) {
            return true;
        }

        // 방법 2: Lore로 확인 (기존 방식 + 커스텀 지원)
        if (meta.hasLore()) {
            for (String lore : meta.getLore()) {
                // 바닐라 GGM 인첸트북 또는 커스텀 GGM 인첸트북 모두 허용
                if (lore.contains("§8§l[GGM 인첸트북]") ||
                        lore.contains("§8§l[GGM 커스텀 인첸트북]")) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * 일반 인첸트북인지 확인
     */
    private boolean isVanillaEnchantBook(ItemStack item) {
        return item != null &&
                item.getType() == Material.ENCHANTED_BOOK &&
                !isGgmEnchantBook(item);
    }

    /**
     * 인첸트 테이블에서 인첸트 시도 차단
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPrepareItemEnchant(PrepareItemEnchantEvent event) {
        if (!isRestrictionEnabled() || !isBlockEnchantTable()) {
            return;
        }

        // 야생 서버에서는 허용 (G 강화 시스템에서 처리)
        if (isAllowEnchantTableOnSurvival()) {
            return;
        }

        Player player = event.getEnchanter();
        event.setCancelled(true);

        sendMessage(player, "§c인첸트 테이블은 사용할 수 없습니다!");
        sendMessage(player, "§7GGM 인첸트북을 모루에서 사용하세요.");
    }

    /**
     * 인첸트 테이블에서 인첸트 완료 차단
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEnchantItem(EnchantItemEvent event) {
        if (!isRestrictionEnabled() || !isBlockEnchantTable()) {
            return;
        }

        // 야생 서버에서는 허용
        if (isAllowEnchantTableOnSurvival()) {
            return;
        }

        event.setCancelled(true);
        sendMessage(event.getEnchanter(), "§c인첸트 테이블은 사용할 수 없습니다!");
    }

    /**
     * 모루에서 인첸트북 사용 제한 (수정: GGM 인첸트북은 허용)
     */
    @EventHandler(priority = EventPriority.NORMAL) // AnvilEnchantManager가 HIGH이므로 NORMAL로 설정
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        if (!isRestrictionEnabled() || !isBlockEnchantBooks()) {
            return;
        }

        AnvilInventory inventory = event.getInventory();
        ItemStack firstItem = inventory.getItem(0);  // 인첸트할 아이템
        ItemStack secondItem = inventory.getItem(1); // 인첸트북

        // 두 번째 슬롯에 일반 인첸트북이 있는지 확인
        if (isVanillaEnchantBook(secondItem)) {
            if (isAllowGgmBooksOnly()) {
                // GGM 인첸트북만 허용하는 경우 결과 차단
                event.setResult(null);

                // 플레이어에게 메시지 전송
                if (event.getViewers().size() > 0 && event.getViewers().get(0) instanceof Player) {
                    Player player = (Player) event.getViewers().get(0);
                    sendMessage(player, "§c일반 인첸트북은 사용할 수 없습니다!");
                    sendMessage(player, "§7GGM 인첸트북만 사용 가능합니다.");
                    sendMessage(player, "§a§l팁: §7/givebook 명령어로 GGM 인첸트북을 얻을 수 있습니다.");
                }
            }
        }
        // GGM 인첸트북인 경우는 AnvilEnchantManager에서 처리하도록 허용
    }

    /**
     * 인벤토리에서 인첸트북 사용 시도 감지
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!isRestrictionEnabled()) {
            return;
        }

        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();

        // 일반 인첸트북 차단
        if (isBlockEnchantBooks()) {
            handleEnchantBookRestriction(event, player);
        }

        // 크리에이티브 모드에서 인첸트북 가져오기 차단
        if (isBlockBookDrops() && player.getGameMode().name().equals("CREATIVE")) {
            ItemStack cursor = event.getCursor();
            ItemStack clicked = event.getCurrentItem();

            if (isVanillaEnchantBook(cursor) || isVanillaEnchantBook(clicked)) {
                event.setCancelled(true);
                sendMessage(player, "§c일반 인첸트북은 가져올 수 없습니다!");
            }
        }
    }

    /**
     * 인첸트북 사용 제한 처리 (수정: GGM 인첸트북 허용)
     */
    private void handleEnchantBookRestriction(InventoryClickEvent event, Player player) {
        // 모루 인벤토리에서의 클릭
        if (event.getInventory() instanceof AnvilInventory) {
            ItemStack cursor = event.getCursor();

            // 일반 인첸트북을 모루에 넣으려 할 때만 차단
            if (event.getSlot() == 1) { // 두 번째 슬롯 (인첸트북 슬롯)
                if (isVanillaEnchantBook(cursor)) {
                    event.setCancelled(true);
                    sendMessage(player, "§c일반 인첸트북은 사용할 수 없습니다!");
                    sendMessage(player, "§7GGM 인첸트북만 사용 가능합니다.");
                    sendMessage(player, "§a§l팁: §7/test custombook 명령어로 테스트 인첸트북을 얻을 수 있습니다.");
                    return;
                }
            }
        }

        // 인첸트 테이블에서 일반 인첸트북 사용 방지
        if (event.getInventory().getType() == InventoryType.ENCHANTING) {
            ItemStack cursor = event.getCursor();

            if (isVanillaEnchantBook(cursor)) {
                event.setCancelled(true);
                sendMessage(player, "§c인첸트 테이블에서 일반 인첸트북은 사용할 수 없습니다!");
            }
        }
    }

    /**
     * 드래그로 인첸트북 이동 방지 (수정: GGM 인첸트북 허용)
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!isRestrictionEnabled() || !isBlockEnchantBooks()) {
            return;
        }

        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        // 모루나 인첸트 테이블에서 일반 인첸트북 드래그 방지
        if (event.getInventory() instanceof AnvilInventory ||
                event.getInventory().getType() == InventoryType.ENCHANTING) {

            ItemStack draggedItem = event.getOldCursor();
            if (isVanillaEnchantBook(draggedItem)) {
                // 제한된 슬롯에 드래그하려는지 확인
                for (int slot : event.getRawSlots()) {
                    if ((event.getInventory() instanceof AnvilInventory && slot == 1) ||
                            (event.getInventory().getType() == InventoryType.ENCHANTING && slot < 2)) {

                        event.setCancelled(true);
                        Player player = (Player) event.getWhoClicked();
                        sendMessage(player, "§c일반 인첸트북은 사용할 수 없습니다!");
                        sendMessage(player, "§7GGM 인첸트북만 사용 가능합니다.");
                        return;
                    }
                }
            }
        }
    }

    /**
     * 몹 드롭에서 인첸트북 제거
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDeath(EntityDeathEvent event) {
        if (!isRestrictionEnabled() || !isBlockBookDrops()) {
            return;
        }

        // 드롭 아이템에서 일반 인첸트북 제거
        Iterator<ItemStack> iterator = event.getDrops().iterator();
        while (iterator.hasNext()) {
            ItemStack item = iterator.next();
            if (isVanillaEnchantBook(item)) {
                iterator.remove();
            }
        }
    }

    /**
     * 낚시에서 인첸트북 획득 차단
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerFish(PlayerFishEvent event) {
        if (!isRestrictionEnabled() || !isBlockBookDrops()) {
            return;
        }

        if (event.getState() == PlayerFishEvent.State.CAUGHT_FISH && event.getCaught() != null) {
            // 잡힌 것이 Item 엔티티인지 확인
            if (event.getCaught() instanceof Item) {
                Item caughtItem = (Item) event.getCaught();
                ItemStack itemStack = caughtItem.getItemStack();

                if (isVanillaEnchantBook(itemStack)) {
                    event.setCancelled(true);
                    sendMessage(event.getPlayer(), "§7(인첸트북 대신 다른 아이템을 낚았습니다)");
                }
            }
        }
    }

    /**
     * 상자/구조물에서 인첸트북 생성 차단
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onLootGenerate(LootGenerateEvent event) {
        if (!isRestrictionEnabled() || !isBlockBookDrops()) {
            return;
        }

        // 생성된 루트에서 일반 인첸트북 제거
        Iterator<ItemStack> iterator = event.getLoot().iterator();
        while (iterator.hasNext()) {
            ItemStack item = iterator.next();
            if (isVanillaEnchantBook(item)) {
                iterator.remove();
            }
        }
    }

    /**
     * 플레이어에게 메시지 전송 (prefix 포함)
     */
    private void sendMessage(Player player, String message) {
        String prefix = plugin.getConfig().getString("messages.prefix", "§6[GGM] §f");
        player.sendMessage(prefix + message);
    }

    /**
     * 제한 설정 리로드
     */
    public void reloadConfig() {
        plugin.reloadConfig();

        String status = isRestrictionEnabled() ? "§a활성화" : "§c비활성화";
        plugin.getLogger().info("인첸트 제한 시스템: " + status);

        if (isRestrictionEnabled()) {
            plugin.getLogger().info("- 일반 인첸트북 차단: " +
                    (isBlockEnchantBooks() ? "§a활성화" : "§c비활성화"));
            plugin.getLogger().info("- 인첸트 테이블 차단: " +
                    (isBlockEnchantTable() ? "§a활성화" : "§c비활성화"));
            plugin.getLogger().info("- 인첸트북 드롭 차단: " +
                    (isBlockBookDrops() ? "§a활성화" : "§c비활성화"));
            plugin.getLogger().info("- GGM 인첸트북만 허용: " +
                    (isAllowGgmBooksOnly() ? "§a활성화" : "§c비활성화"));

            if (isAllowEnchantTableOnSurvival()) {
                plugin.getLogger().info("- 야생 서버 인첸트 테이블: §a허용");
            }

            plugin.getLogger().info("- 모루 GGM 인첸트북 적용: §a허용");
        }
    }
}