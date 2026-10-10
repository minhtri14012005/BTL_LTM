package vn.edu.multigame.questionbank.service;

import org.springframework.http.HttpStatus;

public class QuestionBankFailure extends RuntimeException {
    private final HttpStatus status;
    private final String code;
    public QuestionBankFailure(HttpStatus status, String code, String message) {
        super(message); this.status = status; this.code = code;
    }
    public HttpStatus status() { return status; }
    public String code() { return code; }
    public static QuestionBankFailure notFound() { return new QuestionBankFailure(HttpStatus.NOT_FOUND, "QUIZ_NOT_FOUND", "Không tìm thấy Quiz."); }
    public static QuestionBankFailure forbidden() { return new QuestionBankFailure(HttpStatus.FORBIDDEN, "FORBIDDEN", "Chỉ Owner được quản lý Quiz."); }
}
