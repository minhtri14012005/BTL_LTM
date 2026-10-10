package vn.edu.multigame;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class MultigameApplication {
    public static void main(String[] args) {
        SpringApplication.run(MultigameApplication.class, args);
    }
}
