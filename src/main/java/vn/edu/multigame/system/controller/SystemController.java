package vn.edu.multigame.system.controller;

import vn.edu.multigame.system.service.DatabaseProbe;
import vn.edu.multigame.system.dto.response.SystemStatus;

import java.time.Instant;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system")
public class SystemController {
    private final DatabaseProbe databaseProbe;
    private final org.springframework.beans.factory.ObjectProvider<vn.edu.multigame.game.service.GameStartupCleanup> gameStartup;

    public SystemController(DatabaseProbe databaseProbe,org.springframework.beans.factory.ObjectProvider<vn.edu.multigame.game.service.GameStartupCleanup> gameStartup) {
        this.databaseProbe = databaseProbe;
        this.gameStartup=gameStartup;
    }

    @GetMapping("/status")
    public ResponseEntity<SystemStatus> status() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(currentStatus());
    }

    @GetMapping("/ready")
    public ResponseEntity<SystemStatus> ready() {
        SystemStatus status = currentStatus();
        return ResponseEntity.status(status.database().ready() && status.server().equals("UP") ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE)
                .cacheControl(CacheControl.noStore()).body(status);
    }

    private SystemStatus currentStatus() {
        var startup=gameStartup.getIfAvailable(); boolean ready=startup==null || startup.ready();
        return new SystemStatus("multigame", ready?"UP":"STARTING", databaseProbe.check(), Instant.now().toEpochMilli(),
                startup==null?"NOT_IMPLEMENTED":ready?"GAMEPLAY_WS":"LIFECYCLE_INITIALIZING");
    }

}
