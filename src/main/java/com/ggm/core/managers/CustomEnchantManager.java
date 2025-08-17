package com.ggm.core.managers;

import com.ggm.core.GGMCore;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.*;

public class CustomEnchantManager implements Listener {

    private final GGMCore plugin;
    private final Map<String, CustomEnchant> customEnchants;
    private final Map<UUID, Long> playerCooldowns;
    private final Random random;

    public CustomEnchantManager(GGMCore plugin) {
        this.plugin = plugin;
        this.customEnchants = new HashMap<>();
        this.playerCooldowns = new HashMap<>();
        this.random = new Random();

        registerCustomEnchants();
    }

    /**
     * 모든 커스텀 인첸트 등록
     */
    private void registerCustomEnchants() {
        // 기존 인첸트들
        customEnchants.put("vampire", new CustomEnchant("vampire", "흡혈", "§c흡혈", "공격 시 피해량의 일부만큼 체력 회복"));
        customEnchants.put("lightning", new CustomEnchant("lightning", "번개", "§e번개", "공격 시 확률적으로 번개 소환"));
        customEnchants.put("auto_repair", new CustomEnchant("auto_repair", "자동수리", "§b자동수리", "시간이 지날수록 아이템 내구도 자동 회복"));
        customEnchants.put("exp_boost", new CustomEnchant("exp_boost", "경험증폭", "§d경험증폭", "몹 처치 시 추가 경험치 획득"));
        customEnchants.put("high_jump", new CustomEnchant("high_jump", "점프", "§a점프", "착용 시 점프력 증가"));

        // 🏹 활 인첸트들
        customEnchants.put("explosive_arrow", new CustomEnchant("explosive_arrow", "폭발화살", "§4폭발화살", "발사한 화살이 폭발하여 광역 피해"));
        customEnchants.put("piercing_shot", new CustomEnchant("piercing_shot", "관통사격", "§7관통사격", "화살이 여러 적을 관통"));

        // ⛏️ 도구 인첸트들
        customEnchants.put("auto_smelt", new CustomEnchant("auto_smelt", "자동제련", "§6자동제련", "채굴한 광물이 자동으로 제련됨"));
        customEnchants.put("area_mining", new CustomEnchant("area_mining", "광역채굴", "§8광역채굴", "3x3 영역을 한 번에 채굴"));
        customEnchants.put("vein_miner", new CustomEnchant("vein_miner", "광맥채굴", "§9광맥채굴", "같은 종류 블록을 연쇄적으로 채굴"));
        customEnchants.put("tree_feller", new CustomEnchant("tree_feller", "벌목꾼", "§2벌목꾼", "나무를 한 번에 베어냄"));

        // ⚔️ 검 인첸트들
        customEnchants.put("poison_blade", new CustomEnchant("poison_blade", "독날", "§2독날", "공격 시 독 효과 부여"));
        customEnchants.put("freeze", new CustomEnchant("freeze", "빙결", "§b빙결", "공격 시 적을 얼려서 이동 불가"));
        customEnchants.put("soul_reaper", new CustomEnchant("soul_reaper", "영혼수확", "§0영혼수확", "적 처치 시 주변 몹들에게 피해"));
        customEnchants.put("life_steal", new CustomEnchant("life_steal", "생명흡수", "§4생명흡수", "적 처치 시 최대 체력 증가"));
        customEnchants.put("berserker", new CustomEnchant("berserker", "광전사", "§c광전사", "체력이 낮을수록 공격력 증가"));

        // 🥾 부츠 인첸트들
        customEnchants.put("spider_walk", new CustomEnchant("spider_walk", "거미보행", "§8거미보행", "벽면을 기어올라갈 수 있음"));
        customEnchants.put("water_walker", new CustomEnchant("water_walker", "물위걷기", "§9물위걷기", "물 위를 걸을 수 있음"));

        // 🛡️ 방어구 인첸트들
        customEnchants.put("regeneration", new CustomEnchant("regeneration", "재생", "§d재생", "시간이 지나면서 체력 회복"));
        customEnchants.put("immunity", new CustomEnchant("immunity", "면역", "§f면역", "모든 상태이상 무효"));
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

        // Lore에 커스텀 인첸트 표시 (기존 같은 인첸트 제거 후 추가)
        var lore = meta.hasLore() ? meta.getLore() : new ArrayList<String>();
        lore.removeIf(line -> line.contains(enchant.getName()));
        lore.add(enchant.getDisplayName() + " " + level);
        meta.setLore(lore);

        // 글로우 효과 추가
        meta.addEnchant(org.bukkit.enchantments.Enchantment.LURE, 1, true);
        meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);

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
     * 쿨다운 체크
     */
    private boolean isOnCooldown(Player player, String enchantId, long cooldownMs) {
        String key = player.getUniqueId() + "_" + enchantId;
        long currentTime = System.currentTimeMillis();

        if (playerCooldowns.containsKey(UUID.fromString(key.split("_")[0]))) {
            Long lastUse = playerCooldowns.get(player.getUniqueId());
            if (lastUse != null && currentTime - lastUse < cooldownMs) {
                return true;
            }
        }

        playerCooldowns.put(player.getUniqueId(), currentTime);
        return false;
    }

    // ======================== 활 인첸트 이벤트들 ========================

    /**
     * 활 발사 이벤트
     */
    @EventHandler
    public void onBowShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player)) return;

        Player player = (Player) event.getEntity();
        ItemStack bow = event.getBow();

        // 폭발화살
        int explosiveLevel = getCustomEnchantLevel(bow, "explosive_arrow");
        if (explosiveLevel > 0 && event.getProjectile() instanceof Arrow) {
            Arrow arrow = (Arrow) event.getProjectile();
            NamespacedKey key = new NamespacedKey(plugin, "explosive_arrow_level");
            arrow.getPersistentDataContainer().set(key, PersistentDataType.INTEGER, explosiveLevel);

            // 시각 효과
            arrow.getWorld().spawnParticle(Particle.FLAME, arrow.getLocation(), 5);
        }
    }

    /**
     * 투사체 충돌 이벤트
     */
    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Arrow)) return;

        Arrow arrow = (Arrow) event.getEntity();

        // 폭발화살 처리
        NamespacedKey explosiveKey = new NamespacedKey(plugin, "explosive_arrow_level");
        if (arrow.getPersistentDataContainer().has(explosiveKey, PersistentDataType.INTEGER)) {
            int level = arrow.getPersistentDataContainer().get(explosiveKey, PersistentDataType.INTEGER);

            Location loc = arrow.getLocation();
            float power = 1.0f + (level * 0.5f);

            loc.getWorld().createExplosion(loc, power, false, false);
            loc.getWorld().spawnParticle(Particle.EXPLOSION_LARGE, loc, 1);
            loc.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.0f);
        }
    }

    // ======================== 채굴 인첸트 이벤트들 ========================

    /**
     * 블록 채굴 이벤트
     */
    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        ItemStack tool = player.getInventory().getItemInMainHand();
        Block block = event.getBlock();

        // 자동제련 인첸트
        int autoSmeltLevel = getCustomEnchantLevel(tool, "auto_smelt");
        if (autoSmeltLevel > 0) {
            handleAutoSmelt(event, block, autoSmeltLevel);
        }

        // 광역채굴 인첸트
        int areaMiningLevel = getCustomEnchantLevel(tool, "area_mining");
        if (areaMiningLevel > 0 && !isOnCooldown(player, "area_mining", 1000)) {
            handleAreaMining(event, player, tool, block, areaMiningLevel);
        }

        // 벌목꾼 인첸트
        int treeFellerLevel = getCustomEnchantLevel(tool, "tree_feller");
        if (treeFellerLevel > 0 && isWoodBlock(block.getType())) {
            handleTreeFeller(player, block, treeFellerLevel);
        }

        // 기존 자동수리
        int autoRepairLevel = getCustomEnchantLevel(tool, "auto_repair");
        if (autoRepairLevel > 0) {
            int chance = getAutoRepairChance(autoRepairLevel);
            if (random.nextInt(100) < chance) {
                repairTool(tool, player);
            }
        }
    }

    /**
     * 자동제련 처리
     */
    private void handleAutoSmelt(BlockBreakEvent event, Block block, int level) {
        Material blockType = block.getType();
        Material smeltedType = getSmeltedType(blockType);

        if (smeltedType != null) {
            event.setDropItems(false);

            // 제련된 아이템 드롭
            ItemStack smeltedItem = new ItemStack(smeltedType, 1);
            block.getWorld().dropItemNaturally(block.getLocation(), smeltedItem);

            // 시각 효과
            block.getWorld().spawnParticle(Particle.FLAME, block.getLocation().add(0.5, 0.5, 0.5), 5);
            block.getWorld().playSound(block.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 0.5f, 2.0f);
        }
    }

    /**
     * 광역채굴 처리
     */
    private void handleAreaMining(BlockBreakEvent event, Player player, ItemStack tool, Block centerBlock, int level) {
        if (player.isSneaking()) return; // 웅크리면 비활성화

        // 3x3 영역의 블록들 채굴
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    if (x == 0 && y == 0 && z == 0) continue; // 중앙 블록 제외

                    Block block = centerBlock.getRelative(x, y, z);
                    if (canBreakWithTool(tool, block) && block.getType() != Material.AIR) {
                        block.breakNaturally(tool);

                        // 내구도 소모
                        damageTool(tool, 1);
                    }
                }
            }
        }

        // 시각 효과
        centerBlock.getWorld().spawnParticle(Particle.EXPLOSION_NORMAL,
                centerBlock.getLocation().add(0.5, 0.5, 0.5), 10);
        player.sendMessage("§8광역채굴 발동!");
    }

    /**
     * 벌목꾼 처리
     */
    private void handleTreeFeller(Player player, Block startBlock, int level) {
        Set<Block> treeLogs = new HashSet<>();
        Set<Block> leaves = new HashSet<>();

        // 나무 전체 탐지
        findTreeBlocks(startBlock, treeLogs, leaves, 0, 64); // 최대 64개 블록

        // 나무 전체 제거
        for (Block log : treeLogs) {
            log.breakNaturally();
        }

        // 나뭇잎도 일부 제거 (50% 확률)
        for (Block leaf : leaves) {
            if (random.nextBoolean()) {
                leaf.breakNaturally();
            }
        }

        player.sendMessage("§2벌목꾼 발동! " + treeLogs.size() + "개 블록 제거");

        // 시각 효과
        startBlock.getWorld().spawnParticle(Particle.VILLAGER_HAPPY,
                startBlock.getLocation().add(0.5, 0.5, 0.5), 20);
    }

    // ======================== 전투 인첸트 이벤트들 ========================

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player)) return;

        Player player = (Player) event.getDamager();
        ItemStack weapon = player.getInventory().getItemInMainHand();
        Entity target = event.getEntity();

        // 기존 흡혈 인첸트
        int vampireLevel = getCustomEnchantLevel(weapon, "vampire");
        if (vampireLevel > 0) {
            double healAmount = event.getDamage() * getVampireHealPercentage(vampireLevel);
            player.setHealth(Math.min(player.getMaxHealth(), player.getHealth() + healAmount));
            player.getWorld().spawnParticle(Particle.HEART, player.getLocation().add(0, 2, 0), 3);
        }

        // 기존 번개 인첸트
        int lightningLevel = getCustomEnchantLevel(weapon, "lightning");
        if (lightningLevel > 0) {
            int chance = getLightningChance(lightningLevel);
            if (random.nextInt(100) < chance) {
                event.getEntity().getWorld().strikeLightning(event.getEntity().getLocation());
                player.sendMessage("§e번개가 적을 강타했습니다!");
            }
        }

        // 독날
        int poisonLevel = getCustomEnchantLevel(weapon, "poison_blade");
        if (poisonLevel > 0 && target instanceof LivingEntity) {
            LivingEntity livingTarget = (LivingEntity) target;
            livingTarget.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 100 + (poisonLevel * 20), poisonLevel - 1));

            target.getWorld().spawnParticle(Particle.SLIME, target.getLocation().add(0, 1, 0), 10);
            player.sendMessage("§2독날 발동!");
        }

        // 빙결
        int freezeLevel = getCustomEnchantLevel(weapon, "freeze");
        if (freezeLevel > 0 && target instanceof LivingEntity) {
            LivingEntity livingTarget = (LivingEntity) target;
            livingTarget.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 60 + (freezeLevel * 20), 10));
            livingTarget.addPotionEffect(new PotionEffect(PotionEffectType.JUMP, 60 + (freezeLevel * 20), -10));

            target.getWorld().spawnParticle(Particle.SNOWBALL, target.getLocation().add(0, 1, 0), 15);
            target.getWorld().playSound(target.getLocation(), Sound.BLOCK_GLASS_BREAK, 1.0f, 2.0f);
            player.sendMessage("§b빙결 발동!");
        }

        // 경험증폭 (몹 처치 시)
        int expLevel = getCustomEnchantLevel(weapon, "exp_boost");
        if (expLevel > 0 && target instanceof LivingEntity) {
            LivingEntity livingEntity = (LivingEntity) target;
            if (livingEntity.getHealth() - event.getDamage() <= 0) {
                int bonusExp = getExpBoostAmount(expLevel);
                player.giveExp(bonusExp);
                player.sendMessage("§d추가 경험치 +" + bonusExp);
            }
        }
    }

    // ======================== 이동 인첸트 이벤트들 ========================

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        // 이동 관련 인첸트들은 밸런스상 제거됨
        // 필요시 나중에 추가 가능
    }

    /**
     * 주기적으로 착용 중인 아이템의 커스텀 인첸트 효과 적용
     */
    public void startPeriodicEffects() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                // 점프 인첸트 (기존)
                ItemStack boots = player.getInventory().getBoots();
                int jumpLevel = getCustomEnchantLevel(boots, "high_jump");
                if (jumpLevel > 0) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP, 60, jumpLevel - 1, true, false));
                }

                // 재생 인첸트
                ItemStack chestplate = player.getInventory().getChestplate();
                int regenLevel = getCustomEnchantLevel(chestplate, "regeneration");
                if (regenLevel > 0) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 60, regenLevel - 1, true, false));
                }

                // 면역 인첸트
                ItemStack helmet = player.getInventory().getHelmet();
                int immunityLevel = getCustomEnchantLevel(helmet, "immunity");
                if (immunityLevel > 0) {
                    // 모든 나쁜 효과 제거
                    for (PotionEffect effect : player.getActivePotionEffects()) {
                        PotionEffectType type = effect.getType();
                        if (type == PotionEffectType.POISON || type == PotionEffectType.WITHER ||
                                type == PotionEffectType.SLOW || type == PotionEffectType.WEAKNESS) {
                            player.removePotionEffect(type);
                        }
                    }
                }
            }
        }, 20L, 40L); // 2초마다 실행
    }

    // ======================== 헬퍼 메소드들 ========================

    private Material getSmeltedType(Material raw) {
        return switch (raw) {
            case IRON_ORE, DEEPSLATE_IRON_ORE -> Material.IRON_INGOT;
            case GOLD_ORE, DEEPSLATE_GOLD_ORE, NETHER_GOLD_ORE -> Material.GOLD_INGOT;
            case COPPER_ORE, DEEPSLATE_COPPER_ORE -> Material.COPPER_INGOT;
            case ANCIENT_DEBRIS -> Material.NETHERITE_SCRAP;
            case COBBLESTONE -> Material.STONE;
            case SAND -> Material.GLASS;
            case RAW_IRON -> Material.IRON_INGOT;
            case RAW_GOLD -> Material.GOLD_INGOT;
            case RAW_COPPER -> Material.COPPER_INGOT;
            default -> null;
        };
    }

    private boolean isWoodBlock(Material material) {
        String name = material.name();
        return name.contains("LOG") || name.contains("WOOD");
    }

    private boolean canBreakWithTool(ItemStack tool, Block block) {
        // 간단한 도구-블록 호환성 검사
        String toolName = tool.getType().name();
        String blockName = block.getType().name();

        if (toolName.contains("PICKAXE")) {
            return blockName.contains("STONE") || blockName.contains("ORE") ||
                    blockName.contains("IRON") || blockName.contains("GOLD");
        }

        return true; // 기본적으로 허용
    }

    private void findTreeBlocks(Block start, Set<Block> logs, Set<Block> leaves, int depth, int maxBlocks) {
        if (depth > 20 || logs.size() > maxBlocks) return;

        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 2; y++) {
                for (int z = -1; z <= 1; z++) {
                    Block relative = start.getRelative(x, y, z);

                    if (isWoodBlock(relative.getType()) && !logs.contains(relative)) {
                        logs.add(relative);
                        findTreeBlocks(relative, logs, leaves, depth + 1, maxBlocks);
                    } else if (relative.getType().name().contains("LEAVES") && !leaves.contains(relative)) {
                        leaves.add(relative);
                    }
                }
            }
        }
    }

    private void repairTool(ItemStack tool, Player player) {
        if (tool.getItemMeta() instanceof org.bukkit.inventory.meta.Damageable) {
            org.bukkit.inventory.meta.Damageable damageable =
                    (org.bukkit.inventory.meta.Damageable) tool.getItemMeta();

            int currentDamage = damageable.getDamage();
            if (currentDamage > 0) {
                damageable.setDamage(currentDamage - 1);
                tool.setItemMeta((ItemMeta) damageable);

                player.getWorld().spawnParticle(Particle.VILLAGER_HAPPY,
                        player.getLocation().add(0, 1, 0), 5);
            }
        }
    }

    private void damageTool(ItemStack tool, int damage) {
        if (tool.getItemMeta() instanceof org.bukkit.inventory.meta.Damageable) {
            org.bukkit.inventory.meta.Damageable damageable =
                    (org.bukkit.inventory.meta.Damageable) tool.getItemMeta();

            damageable.setDamage(damageable.getDamage() + damage);
            tool.setItemMeta((ItemMeta) damageable);
        }
    }

    // 기존 설정 메소드들 (간소화)
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

        return (baseHeal + (level * healPerLevel)) / 100.0;
    }

    private int getExpBoostAmount(int level) {
        int baseExp = plugin.getConfig().getInt("custom_enchants.exp_boost.base_exp", 2);
        int expPerLevel = plugin.getConfig().getInt("custom_enchants.exp_boost.exp_per_level", 2);

        return baseExp + (level * expPerLevel);
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