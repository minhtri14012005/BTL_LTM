package vn.edu.multigame.auth.security;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import vn.edu.multigame.auth.dto.response.UserResponse;
import vn.edu.multigame.user.entity.UserAccount;
import vn.edu.multigame.user.enums.UserRole;
import static org.assertj.core.api.Assertions.*;

class AuthPrincipalRoleTest {
    UserAccount account(){var u=new UserAccount();ReflectionTestUtils.setField(u,"id",9L);u.setUsername("account");u.setDisplayName("Account");u.setPasswordHash("hash");return u;}
    @Test void legacyAndRegisteredAccountsDefaultToUserWithoutHostAuthority(){var p=new AuthPrincipal(account());assertThat(p.getAuthorities()).extracting("authority").containsExactly("ROLE_USER");}
    @Test void adminAuthorityAndAccountResponseComeFromPersistedRole(){var u=account();u.setRole(UserRole.ADMIN);var p=new AuthPrincipal(u);assertThat(p.getAuthorities()).extracting("authority").containsExactly("ROLE_ADMIN");assertThat(UserResponse.from(u).role()).isEqualTo(UserRole.ADMIN);p.eraseCredentials();assertThat(p.getPassword()).isNull();}
}
