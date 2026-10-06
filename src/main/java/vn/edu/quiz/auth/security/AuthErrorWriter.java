package vn.edu.quiz.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Component;
import vn.edu.quiz.auth.dto.response.AuthError;

@Component
public class AuthErrorWriter {
    private final ObjectMapper json;
    public AuthErrorWriter(ObjectMapper json) { this.json = json; }
    public void write(HttpServletResponse response, int status, String code, String message) throws IOException {
        response.setStatus(status); response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8"); response.setHeader("Cache-Control", "no-store");
        json.writeValue(response.getOutputStream(), new AuthError(code, message, System.currentTimeMillis()));
    }
}
