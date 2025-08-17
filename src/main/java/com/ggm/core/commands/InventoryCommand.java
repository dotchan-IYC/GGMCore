package com.ggm.core.commands;

import com.ggm.core.GGMCore;
import com.ggm.core.managers.InventoryManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 크로스 서버 인벤토리 관리 명령어
 */
public class InventoryCommand implements CommandExecutor, TabCompleter {

    private final GGMCore plugin;
    private final InventoryManager inventoryManager;

    public InventoryCommand(GGMCore plugin) {
        this.plugin = plugin;
        this.inventoryManager = plugin.getInventoryManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("ggm.inventory")) {
            sender.sendMessage("§c권한이 없습니다!");
            return true;
        }

        // 인벤토리 동기화가 비활성화되어 있으면 사용 불가
        if (!isInventorySyncEnabled()) {
            sender.sendMessage("§c인벤토리 동기화가 비활성화되어 있습니다!");
            return true;
        }

        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "save":
            case "저장":
                return handleSave(sender, args);

            case "load":
            case "로드":
                return handleLoad(sender, args);

            case "sync":
            case "동기화":
                return handleSync(sender, args);

            case "status":
            case "상태":
                return handleStatus(sender);

            default:
                sendUsage(sender);
                return true;
        }
    }

    /**
     * 인벤토리 동기화 활성화 여부 확인
     */
    private boolean isInventorySyncEnabled() {
        return plugin.getConfig().getBoolean("inventory_sync.enabled", false) &&
                inventoryManager != null;
    }

    /**
     * 인벤토리 저장
     */
    private boolean handleSave(CommandSender sender, String[] args) {
        Player target;

        if (args.length > 1) {
            // 다른 플레이어 지정
            target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage("§c플레이어 '" + args[1] + "'을(를) 찾을 수 없습니다.");
                return true;
            }
        } else if (sender instanceof Player) {
            // 자신의 인벤토리
            target = (Player) sender;
        } else {
            sender.sendMessage("§c콘솔에서는 플레이어를 지정해야 합니다: /inventory save <플레이어>");
            return true;
        }

        sender.sendMessage("§e" + target.getName() + "의 인벤토리를 저장하고 있습니다...");

        inventoryManager.savePlayerInventory(target).thenAccept(success -> {
            if (success) {
                sender.sendMessage("§a" + target.getName() + "의 인벤토리가 성공적으로 저장되었습니다!");
                target.sendMessage("§a당신의 인벤토리가 저장되었습니다!");
            } else {
                sender.sendMessage("§c" + target.getName() + "의 인벤토리 저장에 실패했습니다.");
            }
        });

        return true;
    }

    /**
     * 인벤토리 로드
     */
    private boolean handleLoad(CommandSender sender, String[] args) {
        Player target;

        if (args.length > 1) {
            // 다른 플레이어 지정
            target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage("§c플레이어 '" + args[1] + "'을(를) 찾을 수 없습니다.");
                return true;
            }
        } else if (sender instanceof Player) {
            // 자신의 인벤토리
            target = (Player) sender;
        } else {
            sender.sendMessage("§c콘솔에서는 플레이어를 지정해야 합니다: /inventory load <플레이어>");
            return true;
        }

        sender.sendMessage("§e" + target.getName() + "의 인벤토리를 로드하고 있습니다...");

        inventoryManager.loadPlayerInventory(target).thenAccept(success -> {
            if (success) {
                sender.sendMessage("§a" + target.getName() + "의 인벤토리가 성공적으로 로드되었습니다!");
                target.sendMessage("§a당신의 인벤토리가 로드되었습니다!");
            } else {
                sender.sendMessage("§c" + target.getName() + "의 인벤토리 로드에 실패했습니다.");
            }
        });

        return true;
    }

    /**
     * 강제 동기화
     */
    private boolean handleSync(CommandSender sender, String[] args) {
        Player target;

        if (args.length > 1) {
            target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage("§c플레이어 '" + args[1] + "'을(를) 찾을 수 없습니다.");
                return true;
            }
        } else if (sender instanceof Player) {
            target = (Player) sender;
        } else {
            sender.sendMessage("§c콘솔에서는 플레이어를 지정해야 합니다: /inventory sync <플레이어>");
            return true;
        }

        sender.sendMessage("§e" + target.getName() + "의 인벤토리 강제 동기화를 실행합니다...");

        inventoryManager.forceSyncInventory(target);
        sender.sendMessage("§a동기화 명령이 전송되었습니다!");

        return true;
    }

    /**
     * 동기화 상태 확인
     */
    private boolean handleStatus(CommandSender sender) {
        sender.sendMessage("§6§l=== 인벤토리 동기화 상태 ===");
        sender.sendMessage("§b동기화 활성화: §f" + (isInventorySyncEnabled() ? "예" : "아니오"));

        if (isInventorySyncEnabled()) {
            boolean saveOnQuit = plugin.getConfig().getBoolean("inventory_sync.save_on_quit", false);
            boolean loadOnJoin = plugin.getConfig().getBoolean("inventory_sync.load_on_join", false);
            boolean syncExp = plugin.getConfig().getBoolean("inventory_sync.sync_experience", true);

            sender.sendMessage("§b퇴장 시 저장: §f" + (saveOnQuit ? "예" : "아니오"));
            sender.sendMessage("§b접속 시 로드: §f" + (loadOnJoin ? "예" : "아니오"));
            sender.sendMessage("§b경험치 동기화: §f" + (syncExp ? "예" : "아니오"));
        } else {
            sender.sendMessage("§c설정에서 inventory_sync.enabled를 true로 설정하세요.");
        }

        sender.sendMessage("§b현재 서버: §f" + plugin.getServerDetector().getServerType());

        return true;
    }

    /**
     * 사용법 표시
     */
    private void sendUsage(CommandSender sender) {
        sender.sendMessage("§6§l=== 인벤토리 관리 명령어 ===");
        sender.sendMessage("§e/inventory save [플레이어] §7- 인벤토리 저장");
        sender.sendMessage("§e/inventory load [플레이어] §7- 인벤토리 로드");
        sender.sendMessage("§e/inventory sync [플레이어] §7- 강제 동기화");
        sender.sendMessage("§e/inventory status §7- 동기화 상태 확인");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // 첫 번째 인수: 하위 명령어
            List<String> subCommands = Arrays.asList("save", "load", "sync", "status");
            for (String cmd : subCommands) {
                if (cmd.toLowerCase().startsWith(args[0].toLowerCase())) {
                    completions.add(cmd);
                }
            }
        } else if (args.length == 2) {
            // 두 번째 인수: 플레이어 이름
            if (Arrays.asList("save", "load", "sync").contains(args[0].toLowerCase())) {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                        completions.add(player.getName());
                    }
                }
            }
        }

        return completions;
    }
}