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
    private final org.springframework.beans.factory.ObjectProvider<vn.edu.multigame.realtime.session.LessonRuntime> lessonRuntime;

    public SystemController(DatabaseProbe databaseProbe,org.springframework.beans.factory.ObjectProvider<vn.edu.multigame.realtime.session.LessonRuntime> lessonRuntime) {
        this.databaseProbe = databaseProbe;
        this.lessonRuntime=lessonRuntime;
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
        var runtime=lessonRuntime.getIfAvailable();
        return new SystemStatus("ClassQuest", "UP", databaseProbe.check(), Instant.now().toEpochMilli(),
                runtime==null?"NOT_IMPLEMENTED":"GAMEPLAY_WS");
    }

}
