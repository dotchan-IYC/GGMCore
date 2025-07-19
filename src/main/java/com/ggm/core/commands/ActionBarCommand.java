package com.ggm.core.commands;

import com.ggm.core.GGMCore;
import com.ggm.core.managers.ScoreboardManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class ActionBarCommand implements CommandExecutor {

    private final GGMCore plugin;
    private final ScoreboardManager scoreboardManager;
    private final Set<UUID> actionBarEnabled;
    private final SimpleDateFormat timeFormat;

    public ActionBarCommand(GGMCore plugin) {
        this.plugin = plugin;
        this.scoreboardManager = plugin.getScoreboardManager();
        this.actionBarEnabled = new HashSet<>();
        this.timeFormat = new SimpleDateFormat("HH:mm:ss");

        // ActionBar 업데이트 작업 시작
        startActionBarTask();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("이 명령어는 플레이어만 사용할 수 있습니다.");
            return true;
        }

        Player player = (Player) sender;
        UUID uuid = player.getUniqueId();

        if (actionBarEnabled.contains(uuid)) {
            // ActionBar 비활성화
            actionBarEnabled.remove(uuid);
            player.sendActionBar(""); // ActionBar 지우기
            player.sendMessage("§c실시간 ActionBar가 비활성화되었습니다!");
        } else {
            // ActionBar 활성화
            actionBarEnabled.add(uuid);
            player.sendMessage("§a실시간 ActionBar가 활성화되었습니다!");
            player.sendMessage("§7시간과 G 잔액이 ActionBar에 표시됩니다.");
        }

        return true;
    }

    /**
     * ActionBar 업데이트 작업 시작
     */
    private void startActionBarTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            String currentTime = timeFormat.format(new Date());

            for (UUID uuid : actionBarEnabled) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null && player.isOnline()) {
                    // 플레이어의 G 잔액 가져오기
                    plugin.getEconomyManager().getBalance(uuid).thenAccept(balance -> {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            if (player.isOnline()) {
                                String actionBarMessage = String.format(
                                        "§6G: %s §8| §7시간: §f%s §8| §d온라인: §f%d명",
                                        plugin.getEconomyManager().formatMoney(balance),
                                        currentTime,
                                        Bukkit.getOnlinePlayers().size()
                                );
                                player.sendActionBar(actionBarMessage);
                            }
                        });
                    });
                }
            }
        }, 20L, 20L); // 1초마다 실행
    }

    /**
     * 플레이어가 ActionBar를 활성화했는지 확인
     */
    public boolean isEnabled(UUID uuid) {
        return actionBarEnabled.contains(uuid);
    }

    /**
     * 플레이어 퇴장 시 리스트에서 제거
     */
    public void removePlayer(UUID uuid) {
        actionBarEnabled.remove(uuid);
    }
}