package com.ggm.core.managers;

import com.ggm.core.GGMCore;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class EconomyManager {

    private final GGMCore plugin;
    private final DatabaseManager databaseManager;

    public EconomyManager(GGMCore plugin) {
        this.plugin = plugin;
        this.databaseManager = plugin.getDatabaseManager();
    }

    /**
     * 플레이어 경제 데이터 초기화 (로그인 시 호출)
     */
    public CompletableFuture<Void> initializePlayer(Player player) {
        return databaseManager.createOrUpdatePlayer(player.getUniqueId(), player.getName());
    }

    /**
     * 플레이어 잔액 조회
     */
    public CompletableFuture<Long> getBalance(UUID uuid) {
        return databaseManager.getBalance(uuid);
    }

    /**
     * 플레이어 잔액 조회 (동기)
     */
    public CompletableFuture<Long> getBalance(Player player) {
        return getBalance(player.getUniqueId());
    }

    /**
     * 플레이어 잔액 설정 (OP 전용)
     */
    public CompletableFuture<Boolean> setBalance(UUID uuid, long amount) {
        if (amount < 0) {
            return CompletableFuture.completedFuture(false);
        }
        return databaseManager.setBalance(uuid, amount);
    }

    /**
     * 플레이어 G 추가
     */
    public CompletableFuture<Boolean> addMoney(UUID uuid, long amount) {
        if (amount <= 0) {
            return CompletableFuture.completedFuture(false);
        }
        return databaseManager.addBalance(uuid, amount);
    }

    /**
     * 플레이어 G 차감
     */
    public CompletableFuture<Boolean> removeMoney(UUID uuid, long amount) {
        if (amount <= 0) {
            return CompletableFuture.completedFuture(false);
        }

        return getBalance(uuid).thenCompose(balance -> {
            if (balance >= amount) {
                return databaseManager.addBalance(uuid, -amount);
            } else {
                return CompletableFuture.completedFuture(false);
            }
        });
    }

    /**
     * 플레이어 간 송금 (수수료 포함)
     */
    public CompletableFuture<TransferResult> transferMoney(UUID fromUuid, UUID toUuid, long amount) {
        // 입력 검증
        if (amount <= 0) {
            return CompletableFuture.completedFuture(new TransferResult(false, "잘못된 금액입니다."));
        }

        if (fromUuid.equals(toUuid)) {
            return CompletableFuture.completedFuture(new TransferResult(false, "자기 자신에게 송금할 수 없습니다."));
        }

        long maxTransfer = plugin.getConfig().getLong("economy.max_transfer");
        if (amount > maxTransfer) {
            return CompletableFuture.completedFuture(new TransferResult(false, "최대 송금 가능 금액은 " + formatMoney(maxTransfer) + "G입니다."));
        }

        // 수수료 계산
        double feeRate = plugin.getConfig().getDouble("economy.transfer_fee");
        long fee = (long) (amount * feeRate);
        long totalCost = amount + fee;

        return getBalance(fromUuid).thenCompose(senderBalance -> {
            if (senderBalance < totalCost) {
                return CompletableFuture.completedFuture(new TransferResult(false, "G가 부족합니다. (필요: " + formatMoney(totalCost) + "G, 보유: " + formatMoney(senderBalance) + "G)"));
            }

            // 받는 사람이 존재하는지 확인
            return databaseManager.getPlayerName(toUuid).thenCompose(receiverName -> {
                if (receiverName == null) {
                    return CompletableFuture.completedFuture(new TransferResult(false, "받는 사람을 찾을 수 없습니다."));
                }

                // 송금 실행
                return executeFundsTransfer(fromUuid, toUuid, amount, fee, totalCost);
            });
        });
    }

    /**
     * 실제 송금 실행
     */
    private CompletableFuture<TransferResult> executeFundsTransfer(UUID fromUuid, UUID toUuid, long amount, long fee, long totalCost) {
        return databaseManager.addBalance(fromUuid, -totalCost).thenCompose(senderSuccess -> {
            if (!senderSuccess) {
                return CompletableFuture.completedFuture(new TransferResult(false, "송금 중 오류가 발생했습니다."));
            }

            return databaseManager.addBalance(toUuid, amount).thenCompose(receiverSuccess -> {
                if (!receiverSuccess) {
                    // 롤백
                    databaseManager.addBalance(fromUuid, totalCost);
                    return CompletableFuture.completedFuture(new TransferResult(false, "송금 중 오류가 발생했습니다."));
                }

                // 거래 로그 기록
                databaseManager.logTransaction(fromUuid, toUuid, amount, fee, "TRANSFER");

                return CompletableFuture.completedFuture(new TransferResult(true, "송금이 완료되었습니다.", amount, fee));
            });
        });
    }

    /**
     * OP 전용 무제한 송금
     */
    public CompletableFuture<Boolean> adminGiveMoney(UUID toUuid, long amount) {
        if (amount <= 0) {
            return CompletableFuture.completedFuture(false);
        }

        return databaseManager.addBalance(toUuid, amount).thenCompose(success -> {
            if (success) {
                // 관리자 로그 기록 (서버 UUID 사용)
                UUID serverUuid = new UUID(0, 0);
                databaseManager.logTransaction(serverUuid, toUuid, amount, 0, "ADMIN_GIVE");
            }
            return CompletableFuture.completedFuture(success);
        });
    }

    /**
     * OP 전용 G 몰수
     */
    public CompletableFuture<Boolean> adminTakeMoney(UUID fromUuid, long amount) {
        if (amount <= 0) {
            return CompletableFuture.completedFuture(false);
        }

        return getBalance(fromUuid).thenCompose(balance -> {
            long actualAmount = Math.min(balance, amount); // 잔액보다 많이 빼려고 하면 잔액만큼만 빼기

            return databaseManager.addBalance(fromUuid, -actualAmount).thenCompose(success -> {
                if (success) {
                    // 관리자 로그 기록
                    UUID serverUuid = new UUID(0, 0);
                    databaseManager.logTransaction(fromUuid, serverUuid, actualAmount, 0, "ADMIN_TAKE");
                }
                return CompletableFuture.completedFuture(success);
            });
        });
    }

    /**
     * 금액 포맷팅 (천 단위 콤마)
     */
    public String formatMoney(long amount) {
        return String.format("%,d", amount);
    }

    /**
     * 플레이어에게 메시지 전송
     */
    public void sendMessage(Player player, String key, Object... args) {
        String prefix = plugin.getConfig().getString("messages.prefix", "§6[GGM] §f");
        String message = plugin.getConfig().getString("messages." + key, "메시지를 찾을 수 없습니다.");

        // 메시지 포맷팅
        for (int i = 0; i < args.length; i += 2) {
            if (i + 1 < args.length) {
                String placeholder = "{" + args[i] + "}";
                String value = String.valueOf(args[i + 1]);
                message = message.replace(placeholder, value);
            }
        }

        player.sendMessage(prefix + message);
    }

    /**
     * 온라인 플레이어에게 알림 전송
     */
    public void notifyOnlinePlayer(UUID uuid, String message) {
        Player player = Bukkit.getPlayer(uuid);
        if (player != null && player.isOnline()) {
            player.sendMessage(plugin.getConfig().getString("messages.prefix", "§6[GGM] §f") + message);
        }
    }

    /**
     * 송금 결과 클래스
     */
    public static class TransferResult {
        private final boolean success;
        private final String message;
        private final long amount;
        private final long fee;

        public TransferResult(boolean success, String message) {
            this(success, message, 0, 0);
        }

        public TransferResult(boolean success, String message, long amount, long fee) {
            this.success = success;
            this.message = message;
            this.amount = amount;
            this.fee = fee;
        }

        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public long getAmount() { return amount; }
        public long getFee() { return fee; }
    }
}