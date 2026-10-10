package vn.edu.multigame.auth.dto.response;

public record CsrfResponse(String token, String headerName, String parameterName) {}
