package vn.edu.multigame.auth.security;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import vn.edu.multigame.user.entity.UserAccount;

/** Server identity. Host is a Room permission, never a global authority. */
public final class AuthPrincipal implements UserDetails, CredentialsContainer {
    private static final long serialVersionUID = 1L;
    private final long userId;
    private final String username;
    private final boolean enabled;
    private String passwordHash;

    public AuthPrincipal(UserAccount user) {
        userId = user.getId(); username = user.getUsername();
        enabled = user.getDeletedAtMs() == null; passwordHash = user.getPasswordHash();
    }
    public long userId() { return userId; }
    @Override public String getUsername() { return username; }
    @JsonIgnore @Override public String getPassword() { return passwordHash; }
    @Override public boolean isEnabled() { return enabled; }
    @Override public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }
    @Override public void eraseCredentials() { passwordHash = null; }
}
