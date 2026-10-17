package vn.edu.multigame.admin.service;

import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Operator-only bootstrap, never exposed as an HTTP/WS operation. */
@Service @Profile("mysql")
public class AdminProvisionService {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwords;
    public AdminProvisionService(JdbcTemplate jdbc, PasswordEncoder passwords) {this.jdbc=jdbc;this.passwords=passwords;}
    public static void validate(String username, String displayName, char[] secret) {
        if(username==null || !username.matches("[A-Za-z0-9_]{3,32}") || displayName==null || displayName.isBlank() || displayName.length()>100)
            throw new IllegalArgumentException("Supply a valid admin username and display name.");
        if(secret==null || secret.length<16 || StandardCharsets.UTF_8.encode(CharBuffer.wrap(secret)).remaining()>72)
            throw new IllegalArgumentException("Admin password must have at least 16 characters and at most 72 UTF-8 bytes.");
        boolean lower=false,upper=false,digit=false,symbol=false;
        for(char c:secret){lower|=Character.isLowerCase(c);upper|=Character.isUpperCase(c);digit|=Character.isDigit(c);symbol|=!Character.isLetterOrDigit(c);}
        if((lower?1:0)+(upper?1:0)+(digit?1:0)+(symbol?1:0)<3)
            throw new IllegalArgumentException("Use a password-manager secret with at least three character classes.");
        String folded=CharBuffer.wrap(secret).toString().toLowerCase(Locale.ROOT);
        if(folded.contains("password") || folded.contains("changeme") || folded.contains("multigame") || folded.contains(username.toLowerCase(Locale.ROOT)))
            throw new IllegalArgumentException("Admin password must not contain predictable account/application words.");
    }
    @Transactional(timeout=10)
    public long provision(String username, String displayName, char[] secret) {
        try {
            validate(username,displayName,secret);
            String name=username.toLowerCase(Locale.ROOT),hash=passwords.encode(CharBuffer.wrap(secret));
            // Unique username serializes concurrent attempts; never overwrite credentials/role.
            jdbc.update("insert into app_user(username,password_hash,display_name,created_at_ms,role) values(?,?,?,?,'ADMIN') on duplicate key update id=id",
                    name,hash,displayName.strip(),System.currentTimeMillis());
            return jdbc.queryForObject("select id,role,deleted_at_ms from app_user where username=? for update",(rs,row)->{
                if(!"ADMIN".equals(rs.getString("role")) || rs.getObject("deleted_at_ms")!=null)
                    throw new IllegalStateException("Refusing to promote or reactivate an existing account.");
                return rs.getLong("id");
            },name);
        } finally {if(secret!=null)Arrays.fill(secret,'\0');}
    }
}
