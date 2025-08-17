package com.ggm.core.listeners;

import com.ggm.core.GGMCore;
import com.ggm.core.managers.ProtectionScrollManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;

/**
 * 파괴방지권 관련 이벤트 리스너
 */
public class ProtectionScrollListener implements Listener {

    private final GGMCore plugin;
    private final ProtectionScrollManager protectionScrollManager;

    public ProtectionScrollListener(GGMCore plugin) {
        this.plugin = plugin;
        this.protectionScrollManager = plugin.getProtectionScrollManager();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (protectionScrollManager != null) {
            // 파괴방지권 우클릭 사용 처리
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (protectionScrollManager != null) {
            // 인벤토리에서 파괴방지권 사용 처리
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEnchantItem(EnchantItemEvent event) {
        if (protectionScrollManager != null) {
            // 인첸트 실패 시 파괴방지권 효과 처리
        }
    }
}