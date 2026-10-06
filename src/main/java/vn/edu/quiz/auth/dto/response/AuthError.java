package vn.edu.quiz.auth.dto.response;

public record AuthError(String code, String message, long serverTimeMs) {}
