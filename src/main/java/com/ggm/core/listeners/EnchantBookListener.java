// EnchantBookListener 클래스
package com.ggm.core.listeners;

import com.ggm.core.GGMCore;
import com.ggm.core.managers.EnchantBookManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

public class EnchantBookListener implements Listener {

    private final GGMCore plugin;
    private final EnchantBookManager enchantBookManager;

    public EnchantBookListener(GGMCore plugin) {
        this.plugin = plugin;
        this.enchantBookManager = plugin.getEnchantBookManager();
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        // 우클릭 확인
        if (!event.getAction().toString().contains("RIGHT_CLICK")) {
            return;
        }

        // 인첸트북 상자인지 확인
        if (!enchantBookManager.isEnchantBox(item)) {
            return;
        }

        event.setCancelled(true); // 다른 상호작용 방지

        String tier = enchantBookManager.getBoxTier(item);
        if (tier == null) {
            player.sendMessage("§c오류: 인첸트북 상자 등급을 인식할 수 없습니다.");
            return;
        }

        // 랜덤 인첸트북 생성
        ItemStack enchantBook = enchantBookManager.createRandomEnchantBook(tier);
        if (enchantBook == null) {
            player.sendMessage("§c인첸트북 생성에 실패했습니다.");
            return;
        }

        // 인벤토리 공간 확인
        if (player.getInventory().firstEmpty() == -1) {
            player.sendMessage("§c인벤토리에 공간이 부족합니다!");
            return;
        }

        // 상자 제거 및 인첸트북 지급
        item.setAmount(item.getAmount() - 1);
        player.getInventory().addItem(enchantBook);

        // 메시지 전송
        player.sendMessage("§a인첸트북을 얻었습니다! §7(" + tier.toUpperCase() + " 등급)");

        plugin.getLogger().info(String.format("[인첸트북뽑기] %s: %s 등급",
                player.getName(), tier.toUpperCase()));
    }
}
