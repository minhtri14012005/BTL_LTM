package vn.edu.multigame;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class MultigameApplication {
    public static void main(String[] args) {
        if (java.util.Arrays.asList(args).contains("--provision-admin")) {
            vn.edu.multigame.admin.provision.AdminProvisionCommand.run(args);return;
        }
        SpringApplication.run(MultigameApplication.class, args);
    }
}
