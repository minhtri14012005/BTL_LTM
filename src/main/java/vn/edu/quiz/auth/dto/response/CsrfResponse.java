package vn.edu.quiz.auth.dto.response;

public record CsrfResponse(String token, String headerName, String parameterName) {}
