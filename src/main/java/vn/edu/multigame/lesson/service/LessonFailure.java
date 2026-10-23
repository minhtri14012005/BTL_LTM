package vn.edu.multigame.lesson.service;

import org.springframework.http.HttpStatus;

public class LessonFailure extends RuntimeException {
    private final HttpStatus status;
    public LessonFailure(HttpStatus status,String code) {super(code);this.status=status;}
    public HttpStatus status() {return status;}
    public static LessonFailure conflict(String code) {return new LessonFailure(HttpStatus.CONFLICT,code);}
}
