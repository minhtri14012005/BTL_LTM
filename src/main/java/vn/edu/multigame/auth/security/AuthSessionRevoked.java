package vn.edu.multigame.auth.security;

/** Transport independent notification; auth never depends on a WebSocket handler. */
public record AuthSessionRevoked(String sessionId, String reason) {}
