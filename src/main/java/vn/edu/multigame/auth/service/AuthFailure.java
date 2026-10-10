package vn.edu.multigame.auth.service;

import org.springframework.http.HttpStatus;

public class AuthFailure extends RuntimeException {
    private final HttpStatus status;
    private final String code;
    public AuthFailure(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }
    public HttpStatus status() { return status; }
    public String code() { return code; }
}
