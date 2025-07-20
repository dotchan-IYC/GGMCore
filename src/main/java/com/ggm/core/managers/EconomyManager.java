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
     * 스코어보드 업데이트 (G 변경 시 호출)
     */
    private void updateScoreboardForPlayer(UUID uuid) {
        try {
            if (plugin.getScoreboardManager() != null) {
                plugin.getScoreboardManager().updatePlayerBalance(uuid);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("스코어보드 업데이트 실패: " + e.getMessage());
        }
    }

    /**
     * 스코어보드 업데이트 + ActionBar 알림
     */
    private void notifyBalanceChange(UUID uuid, long change) {
        try {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                // 새로운 잔액 조회 후 ActionBar 표시
                getBalance(uuid).thenAccept(newBalance -> {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (plugin.getScoreboardManager() != null) {
                            plugin.getScoreboardManager().notifyBalanceChange(player, newBalance, change);
                        }
                    });
                });
            } else {
                // 플레이어가 오프라인이면 스코어보드만 업데이트
                updateScoreboardForPlayer(uuid);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("잔액 변경 알림 실패: " + e.getMessage());
        }
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
     * 플레이어 잔액 설정 (OP 전용) - 스코어보드 업데이트 포함
     */
    public CompletableFuture<Boolean> setBalance(UUID uuid, long amount) {
        if (amount < 0) {
            return CompletableFuture.completedFuture(false);
        }

        return getBalance(uuid).thenCompose(oldBalance -> {
            return databaseManager.setBalance(uuid, amount).thenCompose(success -> {
                if (success) {
                    long change = amount - oldBalance;
                    // 스코어보드 및 ActionBar 업데이트
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        notifyBalanceChange(uuid, change);
                    });
                }
                return CompletableFuture.completedFuture(success);
            });
        });
    }

    /**
     * 플레이어 G 추가 - 스코어보드 업데이트 포함
     */
    public CompletableFuture<Boolean> addMoney(UUID uuid, long amount) {
        if (amount <= 0) {
            return CompletableFuture.completedFuture(false);
        }

        return databaseManager.addBalance(uuid, amount).thenCompose(success -> {
            if (success) {
                // 스코어보드 및 ActionBar 업데이트
                Bukkit.getScheduler().runTask(plugin, () -> {
                    notifyBalanceChange(uuid, amount);
                });
            }
            return CompletableFuture.completedFuture(success);
        });
    }

    /**
     * 플레이어 G 차감 - 스코어보드 업데이트 포함
     */
    public CompletableFuture<Boolean> removeMoney(UUID uuid, long amount) {
        if (amount <= 0) {
            return CompletableFuture.completedFuture(false);
        }

        return getBalance(uuid).thenCompose(balance -> {
            if (balance >= amount) {
                return databaseManager.addBalance(uuid, -amount).thenCompose(success -> {
                    if (success) {
                        // 스코어보드 및 ActionBar 업데이트
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            notifyBalanceChange(uuid, -amount);
                        });
                    }
                    return CompletableFuture.completedFuture(success);
                });
            } else {
                return CompletableFuture.completedFuture(false);
            }
        });
    }

    /**
     * 플레이어 간 송금 (수수료 포함) - 양쪽 스코어보드 업데이트
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
        double feeRate = plugin.getConfig().getDouble("economy.transfer_fee", 0.1);
        long fee = Math.round(amount * feeRate);
        long totalCost = amount + fee;

        return getBalance(fromUuid).thenCompose(senderBalance -> {
            if (senderBalance < totalCost) {
                return CompletableFuture.completedFuture(new TransferResult(false, "잔액이 부족합니다. (필요: " + formatMoney(totalCost) + "G)"));
            }

            // 송금자에서 차감
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

                    // 양쪽 플레이어의 스코어보드 업데이트
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        notifyBalanceChange(fromUuid, -totalCost);  // 송금자: 금액 + 수수료 차감
                        notifyBalanceChange(toUuid, amount);       // 받는자: 금액 증가
                    });

                    return CompletableFuture.completedFuture(new TransferResult(true, "송금이 완료되었습니다.", amount, fee));
                });
            });
        });
    }

    /**
     * OP 전용 무제한 송금 - 스코어보드 업데이트 포함
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

                // 스코어보드 업데이트
                Bukkit.getScheduler().runTask(plugin, () -> {
                    notifyBalanceChange(toUuid, amount);
                });
            }
            return CompletableFuture.completedFuture(success);
        });
    }

    /**
     * OP 전용 G 몰수 - 스코어보드 업데이트 포함
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

                    // 스코어보드 업데이트
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        notifyBalanceChange(fromUuid, -actualAmount);
                    });
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

        // 플레이스홀더 치환
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