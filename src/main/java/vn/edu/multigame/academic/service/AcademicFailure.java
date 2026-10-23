package vn.edu.multigame.academic.service;

import org.springframework.http.HttpStatus;

public class AcademicFailure extends RuntimeException {
    private final HttpStatus status;
    public AcademicFailure(HttpStatus status,String code) { super(code); this.status=status; }
    public HttpStatus status() { return status; }
}
