package vn.edu.multigame.admin.provision;

import java.util.Arrays;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import vn.edu.multigame.admin.service.AdminProvisionService;
import vn.edu.multigame.user.entity.UserAccount;
import vn.edu.multigame.user.repository.UserRepository;

/** Minimal non-web context: no game runtime/startup cleanup, routes, sockets or demo data. */
public final class AdminProvisionCommand {
    private AdminProvisionCommand() {}
    public static void run(String[] args) {
        String name=System.getenv("MULTIGAME_ADMIN_USERNAME"),display=System.getenv("MULTIGAME_ADMIN_DISPLAY_NAME");
        String value=System.getenv("MULTIGAME_ADMIN_PASSWORD");
        char[] secret=value==null?null:value.toCharArray();
        try {
            AdminProvisionService.validate(name,display,secret);
            try(var context=new SpringApplicationBuilder(ProvisionContext.class).web(WebApplicationType.NONE).profiles("mysql")
                    .logStartupInfo(false).properties("spring.main.banner-mode=off").run(args)) {
                long id=context.getBean(AdminProvisionService.class).provision(name,display,secret);
                System.out.println("ADMIN provisioned or already present; userId="+id+". Existing credentials are unchanged.");
            }
        } finally {if(secret!=null)Arrays.fill(secret,'\0');}
    }
    // Deliberately not component-scanned Configuration: selected only by --provision-admin.
    @EnableAutoConfiguration(exclude=UserDetailsServiceAutoConfiguration.class)
    @EntityScan(basePackageClasses=UserAccount.class)
    @EnableJpaRepositories(basePackageClasses=UserRepository.class)
    @Import(AdminProvisionService.class)
    static class ProvisionContext {
        @Bean PasswordEncoder passwordEncoder(){return new BCryptPasswordEncoder(10);}
    }
}
