package vn.edu.multigame.auth.dto.response;

public record AuthError(String code, String message, long serverTimeMs) {}
