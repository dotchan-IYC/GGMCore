package com.ggm.core.listeners;

import com.ggm.core.GGMCore;
import com.ggm.core.managers.CustomEnchantManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.block.BlockBreakEvent;

/**
 * 커스텀 인첸트 이벤트 리스너
 */
public class CustomEnchantListener implements Listener {

    private final GGMCore plugin;
    private final CustomEnchantManager customEnchantManager;

    public CustomEnchantListener(GGMCore plugin) {
        this.plugin = plugin;
        this.customEnchantManager = plugin.getCustomEnchantManager();
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (customEnchantManager != null) {
            // 커스텀 인첸트 처리는 CustomEnchantManager에서 담당
            // 여기서는 이벤트를 전달만 함
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onEntityDeath(EntityDeathEvent event) {
        if (customEnchantManager != null) {
            // 경험치 부스트 등의 커스텀 인첸트 처리
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onBlockBreak(BlockBreakEvent event) {
        if (customEnchantManager != null) {
            // 자동 제련, 영역 채굴 등의 커스텀 인첸트 처리
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (customEnchantManager != null) {
            // 점프 부스트 등의 이동 관련 커스텀 인첸트 처리
        }
    }
}