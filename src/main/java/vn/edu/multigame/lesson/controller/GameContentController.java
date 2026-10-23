package vn.edu.multigame.lesson.controller;

import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import vn.edu.multigame.lesson.dto.*;
import vn.edu.multigame.lesson.service.GameContentService;

@RestController @Profile("mysql") @RequestMapping("/api/teaching/lessons/{lesson}/games/{position}/content")
public class GameContentController {
    private final GameContentService service;
    public GameContentController(GameContentService service) {this.service=service;}
    @GetMapping public GameContentView get(Authentication caller,@PathVariable long lesson,@PathVariable int position) {return service.get(caller,lesson,position);}
    @PostMapping public GameContentView select(Authentication caller,@PathVariable long lesson,@PathVariable int position,@Valid @RequestBody GameContentRequests.Select input) {return service.select(caller,lesson,position,input);}
    @PutMapping public GameContentView edit(Authentication caller,@PathVariable long lesson,@PathVariable int position,@Valid @RequestBody GameContentRequests.Edit input) {return service.edit(caller,lesson,position,input);}
}
