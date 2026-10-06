package vn.edu.quiz.quiz.dto.response;

public record QuizError(String code, String message, long serverTimeMs) {}
