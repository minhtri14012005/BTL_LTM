package vn.edu.multigame.system.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Only UI routes fall back to the SPA; API, WebSocket and missing assets never do. */
@Controller
public class SpaController {
    @GetMapping({"/login", "/app", "/app/{*path}"})
    public String index() { return "forward:/index.html"; }
}
