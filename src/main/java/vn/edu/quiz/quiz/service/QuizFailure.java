package vn.edu.quiz.quiz.service;

import org.springframework.http.HttpStatus;

public class QuizFailure extends RuntimeException {
    private final HttpStatus status;
    private final String code;
    public QuizFailure(HttpStatus status, String code, String message) {
        super(message); this.status = status; this.code = code;
    }
    public HttpStatus status() { return status; }
    public String code() { return code; }
    public static QuizFailure notFound() { return new QuizFailure(HttpStatus.NOT_FOUND, "QUIZ_NOT_FOUND", "Không tìm thấy Quiz."); }
    public static QuizFailure forbidden() { return new QuizFailure(HttpStatus.FORBIDDEN, "FORBIDDEN", "Chỉ Owner được quản lý Quiz."); }
}
