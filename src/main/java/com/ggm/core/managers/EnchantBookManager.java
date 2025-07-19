package com.ggm.core.managers;

import com.ggm.core.GGMCore;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

public class EnchantBookManager {

    private final GGMCore plugin;
    private final NamespacedKey bookTypeKey;
    private final Map<String, List<String>> enchantPools;

    public EnchantBookManager(GGMCore plugin) {
        this.plugin = plugin;
        this.bookTypeKey = new NamespacedKey(plugin, "book_type");
        this.enchantPools = new HashMap<>();

        loadEnchantPools();
    }

    /**
     * 설정 파일에서 인첸트 풀 로딩
     */
    private void loadEnchantPools() {
        enchantPools.clear();

        for (String tier : Arrays.asList("common", "rare", "epic", "ultimate")) {
            List<String> enchants = plugin.getConfig().getStringList("enchant_books." + tier);
            enchantPools.put(tier, enchants);
        }

        plugin.getLogger().info("인첸트 풀 로딩 완료: " + enchantPools.size() + "개 등급");
    }

    /**
     * 랜덤 인첸트북 상자 생성
     */
    public ItemStack createRandomEnchantBox(String tier) {
        if (!enchantPools.containsKey(tier)) {
            return null;
        }

        // 상자 아이템 생성 (책으로)
        ItemStack box = new ItemStack(Material.BOOK);
        ItemMeta meta = box.getItemMeta();

        // 등급별 이름과 색상
        String displayName = getDisplayName(tier);
        meta.setDisplayName(displayName);

        // 설명 추가
        List<String> lore = new ArrayList<>();
        lore.add("§7우클릭하여 랜덤 인첸트북을 획득하세요!");
        lore.add("§7등급: " + getTierColor(tier) + tier.toUpperCase());
        lore.add("§8§l[GGM 랜덤 인첸트북 상자]");
        meta.setLore(lore);

        // NBT 태그로 등급 저장
        meta.getPersistentDataContainer().set(bookTypeKey, PersistentDataType.STRING, tier);

        box.setItemMeta(meta);
        return box;
    }

    /**
     * 랜덤 인첸트북 생성 (바닐라 + 커스텀 인첸트)
     */
    public ItemStack createRandomEnchantBook(String tier) {
        List<String> pool = enchantPools.get(tier);
        if (pool == null || pool.isEmpty()) {
            plugin.getLogger().warning("인첸트 풀이 비어있습니다: " + tier);
            return null;
        }

        // 랜덤으로 인첸트 선택
        Random random = new Random();
        String selectedEnchant = pool.get(random.nextInt(pool.size()));

        plugin.getLogger().info("선택된 인첸트: " + selectedEnchant + " (등급: " + tier + ")");

        // 커스텀 인첸트인지 확인
        if (selectedEnchant.startsWith("CUSTOM:")) {
            plugin.getLogger().info("커스텀 인첸트북 생성 중...");
            return createCustomEnchantBook(selectedEnchant, tier);
        } else {
            plugin.getLogger().info("바닐라 인첸트북 생성 중...");
            return createVanillaEnchantBook(selectedEnchant, tier);
        }
    }

    /**
     * 바닐라 인첸트북 생성
     */
    private ItemStack createVanillaEnchantBook(String enchantString, String tier) {
        // 인첸트 파싱 (형식: "ENCHANTMENT:LEVEL")
        String[] parts = enchantString.split(":");
        if (parts.length != 2) {
            plugin.getLogger().warning("잘못된 인첸트 형식: " + enchantString);
            return null;
        }

        try {
            Enchantment enchantment = Enchantment.getByKey(NamespacedKey.minecraft(parts[0].toLowerCase()));
            int level = Integer.parseInt(parts[1]);

            if (enchantment == null) {
                plugin.getLogger().warning("존재하지 않는 인첸트: " + parts[0]);
                return null;
            }

            // 인첸트북 생성
            ItemStack enchantBook = new ItemStack(Material.ENCHANTED_BOOK);
            EnchantmentStorageMeta meta = (EnchantmentStorageMeta) enchantBook.getItemMeta();

            meta.addStoredEnchant(enchantment, level, true);

            // GGM 인첸트북 식별 태그 추가
            NamespacedKey ggmKey = new NamespacedKey(plugin, "ggm_enchant_book");
            meta.getPersistentDataContainer().set(ggmKey, PersistentDataType.BYTE, (byte) 1);

            // 커스텀 이름과 설명
            String enchantName = getEnchantmentName(enchantment);
            meta.setDisplayName(getTierColor(tier) + enchantName + " " + level + "권");

            List<String> lore = new ArrayList<>();
            lore.add("§7등급: " + getTierColor(tier) + tier.toUpperCase());
            lore.add("§7인첸트: §f" + enchantName + " " + level);
            lore.add("§8§l[GGM 인첸트북]");
            meta.setLore(lore);

            enchantBook.setItemMeta(meta);
            return enchantBook;

        } catch (Exception e) {
            plugin.getLogger().warning("인첸트북 생성 중 오류: " + e.getMessage());
            return null;
        }
    }

    /**
     * 커스텀 인첸트북 생성
     */
    private ItemStack createCustomEnchantBook(String enchantString, String tier) {
        // 커스텀 인첸트 파싱 (형식: "CUSTOM:enchant_id:LEVEL")
        String[] parts = enchantString.split(":");
        if (parts.length != 3) {
            plugin.getLogger().warning("잘못된 커스텀 인첸트 형식: " + enchantString);
            return null;
        }

        try {
            String customEnchantId = parts[1];
            int level = Integer.parseInt(parts[2]);

            plugin.getLogger().info("커스텀 인첸트북 생성 시도: " + customEnchantId + " 레벨 " + level);

            // 커스텀 인첸트북 생성 (일반 인첸트북으로 시작)
            ItemStack enchantBook = new ItemStack(Material.ENCHANTED_BOOK);
            ItemMeta meta = enchantBook.getItemMeta();

            // 커스텀 인첸트 정보를 NBT에 저장
            NamespacedKey customKey = new NamespacedKey(plugin, "custom_enchant_" + customEnchantId);
            meta.getPersistentDataContainer().set(customKey, PersistentDataType.INTEGER, level);

            // GGM 인첸트북 식별 태그 추가
            NamespacedKey ggmKey = new NamespacedKey(plugin, "ggm_enchant_book");
            meta.getPersistentDataContainer().set(ggmKey, PersistentDataType.BYTE, (byte) 1);

            // 이름 설정
            String enchantDisplayName = getCustomEnchantDisplayName(customEnchantId);
            meta.setDisplayName(getTierColor(tier) + enchantDisplayName + " " + level + "권");

            // Lore 설정
            List<String> lore = new ArrayList<>();
            lore.add("§7등급: " + getTierColor(tier) + tier.toUpperCase());
            lore.add("§7커스텀: " + enchantDisplayName + " " + level);
            lore.add("§7효과: §f" + getCustomEnchantDescription(customEnchantId));
            lore.add("§8§l[GGM 인첸트북]");
            meta.setLore(lore);

            enchantBook.setItemMeta(meta);

            plugin.getLogger().info("커스텀 인첸트북 생성 완료: " + enchantDisplayName);
            return enchantBook;

        } catch (Exception e) {
            plugin.getLogger().warning("커스텀 인첸트북 생성 중 오류: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 커스텀 인첸트 표시 이름 가져오기 (업데이트됨)
     */
    private String getCustomEnchantDisplayName(String enchantId) {
        return switch (enchantId.toLowerCase()) {
            case "vampire" -> "§c흡혈";
            case "lightning" -> "§e번개";
            case "auto_repair" -> "§b자동수리";
            case "exp_boost" -> "§d경험증폭";
            case "high_jump" -> "§a점프";
            case "explosive_arrow" -> "§4폭발화살";
            case "piercing_shot" -> "§7관통사격";
            case "auto_smelt" -> "§6자동제련";
            case "area_mining" -> "§8광역채굴";
            case "vein_miner" -> "§9광맥채굴";
            case "tree_feller" -> "§2벌목꾼";
            case "poison_blade" -> "§2독날";
            case "freeze" -> "§b빙결";
            case "soul_reaper" -> "§0영혼수확";
            case "life_steal" -> "§4생명흡수";
            case "berserker" -> "§c광전사";
            case "spider_walk" -> "§8거미보행";
            case "water_walker" -> "§9물위걷기";
            case "regeneration" -> "§d재생";
            case "immunity" -> "§f면역";
            default -> "§7" + enchantId;
        };
    }

    /**
     * 커스텀 인첸트 설명 가져오기 (업데이트됨)
     */
    private String getCustomEnchantDescription(String enchantId) {
        return switch (enchantId.toLowerCase()) {
            case "vampire" -> "공격 시 피해량의 일부만큼 체력 회복";
            case "lightning" -> "공격 시 확률적으로 번개 소환";
            case "auto_repair" -> "블록 채굴 시 확률적으로 내구도 회복";
            case "exp_boost" -> "몹 처치 시 추가 경험치 획득";
            case "high_jump" -> "착용 시 점프력 증가";
            case "explosive_arrow" -> "발사한 화살이 폭발하여 광역 피해";
            case "piercing_shot" -> "화살이 여러 적을 관통";
            case "auto_smelt" -> "채굴한 광물이 자동으로 제련됨";
            case "area_mining" -> "3x3 영역을 한 번에 채굴";
            case "vein_miner" -> "같은 종류 블록을 연쇄적으로 채굴";
            case "tree_feller" -> "나무를 한 번에 베어냄";
            case "poison_blade" -> "공격 시 독 효과 부여";
            case "freeze" -> "공격 시 적을 얼려서 이동 불가";
            case "soul_reaper" -> "적 처치 시 주변 몹들에게 피해";
            case "life_steal" -> "적 처치 시 최대 체력 증가";
            case "berserker" -> "체력이 낮을수록 공격력 증가";
            case "spider_walk" -> "벽면을 기어올라갈 수 있음";
            case "water_walker" -> "물 위를 걸을 수 있음";
            case "regeneration" -> "시간이 지나면서 체력 회복";
            case "immunity" -> "모든 상태이상 무효";
            default -> "알 수 없는 효과";
        };
    }

    /**
     * 인첸트북 상자인지 확인
     */
    public boolean isEnchantBox(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }

        return item.getItemMeta().getPersistentDataContainer()
                .has(bookTypeKey, PersistentDataType.STRING);
    }

    /**
     * 인첸트북 상자의 등급 가져오기
     */
    public String getBoxTier(ItemStack item) {
        if (!isEnchantBox(item)) {
            return null;
        }

        return item.getItemMeta().getPersistentDataContainer()
                .get(bookTypeKey, PersistentDataType.STRING);
    }

    /**
     * 등급별 표시 이름
     */
    private String getDisplayName(String tier) {
        return switch (tier.toLowerCase()) {
            case "common" -> "§f일반 랜덤 인첸트북 상자";
            case "rare" -> "§9희귀 랜덤 인첸트북 상자";
            case "epic" -> "§5영웅 랜덤 인첸트북 상자";
            case "ultimate" -> "§6전설 랜덤 인첸트북 상자";
            default -> "§7알 수 없는 인첸트북 상자";
        };
    }

    /**
     * 등급별 색상
     */
    private String getTierColor(String tier) {
        return switch (tier.toLowerCase()) {
            case "common" -> "§f";
            case "rare" -> "§9";
            case "epic" -> "§5";
            case "ultimate" -> "§6";
            default -> "§7";
        };
    }

    /**
     * 인첸트 이름 한글화
     */
    private String getEnchantmentName(Enchantment enchantment) {
        String key = enchantment.getKey().getKey();
        return switch (key) {
            case "protection" -> "보호";
            case "fire_protection" -> "화염 보호";
            case "projectile_protection" -> "투사체 보호";
            case "blast_protection" -> "폭발 보호";
            case "sharpness" -> "날카로움";
            case "smite" -> "강타";
            case "bane_of_arthropods" -> "절지동물 특효";
            case "efficiency" -> "효율성";
            case "unbreaking" -> "내구성";
            case "fortune" -> "행운";
            case "looting" -> "약탈";
            case "mending" -> "수선";
            case "power" -> "힘";
            case "punch" -> "밀어내기";
            case "flame" -> "화염";
            case "infinity" -> "무한";
            default -> enchantment.getKey().getKey();
        };
    }
}