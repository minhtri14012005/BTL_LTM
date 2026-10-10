package vn.edu.multigame.system.service;

import vn.edu.multigame.system.dto.response.DatabaseStatus;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("web-only")
public class WebOnlyDatabaseProbe implements DatabaseProbe {
    @Override
    public DatabaseStatus check() {
        return new DatabaseStatus("NOT_CONFIGURED", "Profile web-only không kết nối database.");
    }
}
