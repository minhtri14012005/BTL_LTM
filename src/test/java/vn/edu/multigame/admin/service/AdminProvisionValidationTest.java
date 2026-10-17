package vn.edu.multigame.admin.service;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminProvisionValidationTest {
    @Test void secretIsRequiredAndWeakOrOversizedInputsAreRejected(){
        for(char[] secret:new char[][]{null,new char[0],"x".repeat(32).toCharArray(),("Aa1!".repeat(19)).toCharArray()})
            assertThatThrownBy(()->AdminProvisionService.validate("operator","Operator",secret)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void predictableAccountWordsAreRejected(){assertThatThrownBy(()->AdminProvisionService.validate("operator","Operator",("operator"+UUID.randomUUID()+"!Aa").toCharArray())).isInstanceOf(IllegalArgumentException.class);}
    @Test void rejectedProvisionWipesCallerSecretAndNeverTouchesDatabase(){
        var jdbc=mock(org.springframework.jdbc.core.JdbcTemplate.class);var encoder=mock(org.springframework.security.crypto.password.PasswordEncoder.class);char[] secret="x".repeat(32).toCharArray();
        assertThatThrownBy(()->new AdminProvisionService(jdbc,encoder).provision("operator","Operator",secret)).isInstanceOf(IllegalArgumentException.class);
        assertThat(secret).containsOnly('\0');verifyNoInteractions(jdbc,encoder);
    }
}
