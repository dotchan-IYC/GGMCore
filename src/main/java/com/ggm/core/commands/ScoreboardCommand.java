package com.ggm.core.commands;

import com.ggm.core.GGMCore;
import com.ggm.core.managers.ScoreboardManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class ScoreboardCommand implements CommandExecutor {

    private final GGMCore plugin;
    private final ScoreboardManager scoreboardManager;
    private final Set<UUID> disabledPlayers; // 스코어보드를 끈 플레이어들

    public ScoreboardCommand(GGMCore plugin) {
        this.plugin = plugin;
        this.scoreboardManager = plugin.getScoreboardManager();
        this.disabledPlayers = new HashSet<>();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("이 명령어는 플레이어만 사용할 수 있습니다.");
            return true;
        }

        Player player = (Player) sender;
        UUID uuid = player.getUniqueId();

        if (disabledPlayers.contains(uuid)) {
            // 스코어보드 활성화
            disabledPlayers.remove(uuid);
            scoreboardManager.createScoreboard(player);
            player.sendMessage("§a스코어보드가 활성화되었습니다!");
            player.sendMessage("§7실시간 G 잔액과 직업 정보가 표시됩니다.");
        } else {
            // 스코어보드 비활성화
            disabledPlayers.add(uuid);
            scoreboardManager.removeScoreboard(player);
            player.sendMessage("§c스코어보드가 비활성화되었습니다. §7(/sb 로 다시 켜기)");
        }

        return true;
    }

    /**
     * 플레이어가 스코어보드를 비활성화했는지 확인
     */
    public boolean isDisabled(UUID uuid) {
        return disabledPlayers.contains(uuid);
    }

    /**
     * 플레이어 퇴장 시 리스트에서 제거
     */
    public void removePlayer(UUID uuid) {
        disabledPlayers.remove(uuid);
    }
}