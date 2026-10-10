package vn.edu.multigame.auth.security;

import java.util.Locale;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.multigame.user.repository.UserRepository;

@Service
@Profile("mysql")
public class AccountUserDetailsService implements UserDetailsService {
    private final UserRepository users;
    public AccountUserDetailsService(UserRepository users) { this.users = users; }
    @Override @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) {
        return users.findByUsername(username.toLowerCase(Locale.ROOT))
                .map(AuthPrincipal::new)
                .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
    }
}
