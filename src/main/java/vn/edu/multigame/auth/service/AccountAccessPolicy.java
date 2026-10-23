package vn.edu.multigame.auth.service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.Map;
import java.util.HashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.*;

/**
 * DB authority plus a nonblocking per-account send-start fence. Never performs
 * network I/O.
 */
@Service
@Profile("mysql")
public class AccountAccessPolicy {
    public record State(long userId, String kind, Long untilMs, long version, long epoch, Long deletedAtMs) {
        public boolean banned(long now) {
            return kind.equals("INDEFINITE") || kind.equals("TEMP") && now < untilMs;
        }

        public String code() {
            return kind.equals("TEMP") ? "ACCOUNT_TEMP_BANNED" : "ACCOUNT_INDEFINITE_BANNED";
        }
    }

    private record Fence(long committedCutoff, Map<Object, Long> pending, long issued) {
        long cutoff() {
            return Math.max(committedCutoff, pending.values().stream().mapToLong(Long::longValue).max().orElse(0));
        }
    }

    private final ConcurrentHashMap<Long, AtomicReference<Fence>> fences = new ConcurrentHashMap<>();
    private final JdbcTemplate jdbc;
    private static final ThreadLocal<vn.edu.multigame.auth.security.AuthPrincipal> identity = new ThreadLocal<>();

    public AccountAccessPolicy(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public State state(long user) {
        return read(user, false);
    }

    public State lock(long user) {
        return read(user, true);
    }

    public <T> T as(org.springframework.security.core.Authentication auth, java.util.function.Supplier<T> operation) {
        var previous = identity.get();
        try {
            identity.set((vn.edu.multigame.auth.security.AuthPrincipal) auth.getPrincipal());
            return operation.get();
        } finally {
            if (previous == null)
                identity.remove();
            else
                identity.set(previous);
        }
    }

    public void requireLocked(long user) {
        var state = lock(user);
        requireAvailable(state);
        var principal = identity.get();
        if (principal != null && principal.userId() == user && principal.authEpoch() != state.epoch())
            throw AuthService.unauthorized();
    }

    private State read(long user, boolean lock) {
        var rows = jdbc.query(
                "select id,ban_kind,banned_until_ms,ban_version,auth_epoch,deleted_at_ms from app_user where id=?"
                        + (lock ? " for update" : ""),
                (r, n) -> new State(r.getLong("id"), r.getString("ban_kind"),
                        r.getObject("banned_until_ms", Long.class), r.getLong("ban_version"), r.getLong("auth_epoch"),
                        r.getObject("deleted_at_ms", Long.class)),
                user);
        if (rows.isEmpty())
            throw AuthService.unauthorized();
        return rows.getFirst();
    }

    public void requireAvailable(long user) {
        requireAvailable(state(user));
    }

    public void requireAvailable(State state) {
        if (state.deletedAtMs() != null)
            throw AuthService.unauthorized();
        if (state.banned(System.currentTimeMillis()))
            throw new AuthFailure(HttpStatus.FORBIDDEN, state.code(),
                    state.kind().equals("TEMP")
                            ? "Tài khoản bị khóa tạm thời đến " + java.time.Instant.ofEpochMilli(state.untilMs()) + "."
                            : "Tài khoản bị khóa vô thời hạn.");
    }

    public void requireSession(long user, long epoch) {
        var state = state(user);
        requireAvailable(state);
        if (epoch != state.epoch())
            throw new AuthFailure(HttpStatus.UNAUTHORIZED, "SESSION_REVOKED",
                    "Phiên đã bị thu hồi. Hãy đăng nhập lại.");
    }

    /**
     * Caller already owns target User lock. Rollback restores provisional cutoff.
     */
    public void fenceForCommit(long user, long cutoff) {
        if (!TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("Account fence requires transaction");
        var gate = fences.computeIfAbsent(user, k -> new AtomicReference<>(new Fence(0, Map.of(), 0)));
        Object publication = new Object();
        gate.updateAndGet(s -> {
            var pending = new HashMap<>(s.pending());
            pending.put(publication, cutoff);
            return new Fence(s.committedCutoff(), Map.copyOf(pending), s.issued());
        });
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                gate.updateAndGet(s -> {
                    var pending = new HashMap<>(s.pending());
                    pending.remove(publication);
                    return new Fence(
                            status == STATUS_COMMITTED ? Math.max(cutoff, s.committedCutoff()) : s.committedCutoff(),
                            Map.copyOf(pending), s.issued());
                });
            }
        });
    }

    /**
     * CAS is send-start; no lock is retained while a socket writer performs I/O.
     */
    public boolean beginSend(long user, long epoch) {
        requireSession(user, epoch);
        var gate = fences.computeIfAbsent(user, k -> new AtomicReference<>(new Fence(0, Map.of(), 0)));
        while (true) {
            var before = gate.get();
            if (epoch < before.cutoff())
                return false;
            if (gate.compareAndSet(before, new Fence(before.committedCutoff(), before.pending(), before.issued() + 1)))
                return true;
        }
    }
}
