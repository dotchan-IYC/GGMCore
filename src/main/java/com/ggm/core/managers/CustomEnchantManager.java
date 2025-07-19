package com.ggm.core.managers;

import com.ggm.core.GGMCore;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class CustomEnchantManager implements Listener {

    private final GGMCore plugin;
    private final Map<String, CustomEnchant> customEnchants;
    private final Random random;

    public CustomEnchantManager(GGMCore plugin) {
        this.plugin = plugin;
        this.customEnchants = new HashMap<>();
        this.random = new Random();

        // 커스텀 인첸트 등록
        registerCustomEnchants();
    }

    /**
     * 커스텀 인첸트들 등록
     */
    private void registerCustomEnchants() {
        // 흡혈 인첸트 (공격 시 체력 회복)
        customEnchants.put("vampire", new CustomEnchant(
                "vampire", "흡혈", "§c흡혈",
                "공격 시 피해량의 일부만큼 체력 회복"
        ));

        // 번개 인첸트 (공격 시 번개 확률)
        customEnchants.put("lightning", new CustomEnchant(
                "lightning", "번개", "§e번개",
                "공격 시 확률적으로 번개 소환"
        ));

        // 자동 수리 인첸트 (시간이 지나면서 내구도 회복)
        customEnchants.put("auto_repair", new CustomEnchant(
                "auto_repair", "자동수리", "§b자동수리",
                "시간이 지날수록 아이템 내구도 자동 회복"
        ));

        // 경험치 증폭 인첸트 (몹 킬 시 경험치 추가)
        customEnchants.put("exp_boost", new CustomEnchant(
                "exp_boost", "경험증폭", "§d경험증폭",
                "몹 처치 시 추가 경험치 획득"
        ));

        // 점프 인첸트 (부츠 착용 시 점프력 증가)
        customEnchants.put("high_jump", new CustomEnchant(
                "high_jump", "점프", "§a점프",
                "착용 시 점프력 증가"
        ));
    }

    /**
     * 아이템에 커스텀 인첸트 적용
     */
    public ItemStack applyCustomEnchant(ItemStack item, String enchantId, int level) {
        if (item == null || !customEnchants.containsKey(enchantId)) {
            return item;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        CustomEnchant enchant = customEnchants.get(enchantId);
        NamespacedKey key = new NamespacedKey(plugin, "custom_enchant_" + enchantId);

        // NBT에 커스텀 인첸트 정보 저장
        meta.getPersistentDataContainer().set(key, PersistentDataType.INTEGER, level);

        // Lore에 커스텀 인첸트 표시
        var lore = meta.hasLore() ? meta.getLore() : new java.util.ArrayList<String>();
        lore.add(enchant.getDisplayName() + " " + level);
        meta.setLore(lore);

        item.setItemMeta(meta);
        return item;
    }

    /**
     * 아이템에서 커스텀 인첸트 레벨 가져오기
     */
    public int getCustomEnchantLevel(ItemStack item, String enchantId) {
        if (item == null || !item.hasItemMeta()) {
            return 0;
        }

        NamespacedKey key = new NamespacedKey(plugin, "custom_enchant_" + enchantId);
        return item.getItemMeta().getPersistentDataContainer()
                .getOrDefault(key, PersistentDataType.INTEGER, 0);
    }

    /**
     * 설정에서 확률 가져오기
     */
    private int getLightningChance(int level) {
        int baseChance = plugin.getConfig().getInt("custom_enchants.lightning.base_chance", 5);
        int chancePerLevel = plugin.getConfig().getInt("custom_enchants.lightning.chance_per_level", 5);
        int maxChance = plugin.getConfig().getInt("custom_enchants.lightning.max_chance", 25);

        int totalChance = baseChance + (level * chancePerLevel);
        return Math.min(totalChance, maxChance);
    }

    private int getAutoRepairChance(int level) {
        int baseChance = plugin.getConfig().getInt("custom_enchants.auto_repair.base_chance", 3);
        int chancePerLevel = plugin.getConfig().getInt("custom_enchants.auto_repair.chance_per_level", 3);
        int maxChance = plugin.getConfig().getInt("custom_enchants.auto_repair.max_chance", 15);

        int totalChance = baseChance + (level * chancePerLevel);
        return Math.min(totalChance, maxChance);
    }

    private double getVampireHealPercentage(int level) {
        double baseHeal = plugin.getConfig().getDouble("custom_enchants.vampire.heal_percentage", 10.0);
        double healPerLevel = plugin.getConfig().getDouble("custom_enchants.vampire.heal_per_level", 10.0);

        return (baseHeal + (level * healPerLevel)) / 100.0; // 퍼센트를 소수로 변환
    }

    private int getExpBoostAmount(int level) {
        int baseExp = plugin.getConfig().getInt("custom_enchants.exp_boost.base_exp", 2);
        int expPerLevel = plugin.getConfig().getInt("custom_enchants.exp_boost.exp_per_level", 2);

        return baseExp + (level * expPerLevel);
    }
    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player)) return;

        Player player = (Player) event.getDamager();
        ItemStack weapon = player.getInventory().getItemInMainHand();

        // 흡혈 인첸트
        int vampireLevel = getCustomEnchantLevel(weapon, "vampire");
        if (vampireLevel > 0) {
            double healAmount = event.getDamage() * 0.1 * vampireLevel; // 10% * 레벨
            player.setHealth(Math.min(player.getMaxHealth(), player.getHealth() + healAmount));

            // 파티클 효과
            player.getWorld().spawnParticle(
                    org.bukkit.Particle.HEART,
                    player.getLocation().add(0, 2, 0),
                    3
            );
        }

        // 번개 인첸트
        int lightningLevel = getCustomEnchantLevel(weapon, "lightning");
        if (lightningLevel > 0) {
            int chance = lightningLevel * 5; // 5% * 레벨
            if (random.nextInt(100) < chance) {
                event.getEntity().getWorld().strikeLightning(event.getEntity().getLocation());
                player.sendMessage("§e⚡ 번개가 적을 강타했습니다!");
            }
        }

        // 경험증폭 인첸트 (몹이 죽었을 때)
        int expLevel = getCustomEnchantLevel(weapon, "exp_boost");
        if (expLevel > 0 && event.getEntity() instanceof org.bukkit.entity.LivingEntity) {
            org.bukkit.entity.LivingEntity livingEntity = (org.bukkit.entity.LivingEntity) event.getEntity();

            // 몹이 죽을 예정인지 확인
            if (livingEntity.getHealth() - event.getDamage() <= 0) {
                int bonusExp = expLevel * 2; // 레벨당 +2 경험치
                player.giveExp(bonusExp);
                player.sendMessage("§d✨ 추가 경험치 +" + bonusExp);
            }
        }
    }

    /**
     * 블록 채굴 시 커스텀 인첸트 효과
     */
    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        ItemStack tool = player.getInventory().getItemInMainHand();

        // 자동수리 인첸트
        int autoRepairLevel = getCustomEnchantLevel(tool, "auto_repair");
        if (autoRepairLevel > 0) {
            int chance = getAutoRepairChance(autoRepairLevel);
            if (random.nextInt(100) < chance) {
                // 내구도 1 회복
                ItemMeta meta = tool.getItemMeta();
                if (meta instanceof org.bukkit.inventory.meta.Damageable) {
                    org.bukkit.inventory.meta.Damageable damageable =
                            (org.bukkit.inventory.meta.Damageable) meta;

                    int currentDamage = damageable.getDamage();
                    if (currentDamage > 0) {
                        damageable.setDamage(currentDamage - 1);
                        tool.setItemMeta(meta);

                        // 수리 효과 표시
                        player.getWorld().spawnParticle(
                                org.bukkit.Particle.VILLAGER_HAPPY,
                                player.getLocation().add(0, 1, 0),
                                5
                        );
                        player.sendMessage("§b🔧 도구가 자동 수리되었습니다! §7(" + chance + "%)");
                    }
                }
            }
        }
    }

    /**
     * 주기적으로 착용 중인 아이템의 커스텀 인첸트 효과 적용
     */
    public void startPeriodicEffects() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                // 점프 인첸트 (부츠)
                ItemStack boots = player.getInventory().getBoots();
                int jumpLevel = getCustomEnchantLevel(boots, "high_jump");
                if (jumpLevel > 0) {
                    player.addPotionEffect(new PotionEffect(
                            PotionEffectType.JUMP, 60, jumpLevel - 1, true, false
                    ));
                }
            }
        }, 20L, 40L); // 2초마다 실행
    }

    /**
     * 커스텀 인첸트 정보 클래스
     */
    public static class CustomEnchant {
        private final String id;
        private final String name;
        private final String displayName;
        private final String description;

        public CustomEnchant(String id, String name, String displayName, String description) {
            this.id = id;
            this.name = name;
            this.displayName = displayName;
            this.description = description;
        }

        public String getId() { return id; }
        public String getName() { return name; }
        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
    }

    /**
     * 등록된 커스텀 인첸트 목록 가져오기
     */
    public Map<String, CustomEnchant> getCustomEnchants() {
        return customEnchants;
    }
}