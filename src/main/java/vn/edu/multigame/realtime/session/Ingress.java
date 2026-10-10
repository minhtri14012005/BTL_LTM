package vn.edu.multigame.realtime.session;

/** Authoritative receipt assigned atomically with sequence and enqueue. No client timestamp input. */
public record Ingress<T>(long gameSessionId, long sessionGeneration, long sequence,
        long receivedAtMs, long serverTimeMs, T value) {}
