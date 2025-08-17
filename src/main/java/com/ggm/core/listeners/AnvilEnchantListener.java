package com.ggm.core.listeners;

import com.ggm.core.GGMCore;
import com.ggm.core.managers.AnvilEnchantManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;

/**
 * 모루 인첸트 적용 관련 이벤트 리스너
 */
public class AnvilEnchantListener implements Listener {

    private final GGMCore plugin;
    private final AnvilEnchantManager anvilEnchantManager;

    public AnvilEnchantListener(GGMCore plugin) {
        this.plugin = plugin;
        this.anvilEnchantManager = plugin.getAnvilEnchantManager();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        if (anvilEnchantManager != null) {
            // 모루에서 인첸트북 적용 준비 처리
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (anvilEnchantManager != null) {
            // 모루에서 커스텀 인첸트 적용 처리
        }
    }
}