package vn.edu.multigame.system.service;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("mysql")
public class DatabaseStartupCheck implements ApplicationRunner {
    private final DatabaseProbe databaseProbe;

    public DatabaseStartupCheck(DatabaseProbe databaseProbe) {
        this.databaseProbe = databaseProbe;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!databaseProbe.check().ready()) {
            throw new IllegalStateException("MySQL startup check failed. Check local DB configuration.");
        }
    }
}
