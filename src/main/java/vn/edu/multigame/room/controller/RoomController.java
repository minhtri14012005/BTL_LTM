package vn.edu.multigame.room.controller;

import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import vn.edu.multigame.auth.service.AuthService;
import vn.edu.multigame.realtime.session.RoomOperations;
import vn.edu.multigame.room.dto.request.*;
import vn.edu.multigame.room.dto.response.*;
import vn.edu.multigame.room.service.RoomService;

@RestController @RequestMapping("/api/rooms") @Profile("mysql")
public class RoomController {
    private final RoomOperations operations;
    private final RoomService rooms;
    private final AuthService users;
    private final RoomRequestDecoder decoder;
    private final vn.edu.multigame.auth.security.AuthSessionRegistry sessions;
    public RoomController(RoomOperations operations,RoomService rooms,AuthService users,RoomRequestDecoder decoder,vn.edu.multigame.auth.security.AuthSessionRegistry sessions) {
        this.operations=operations; this.rooms=rooms; this.users=users; this.decoder=decoder;
        this.sessions=sessions;
    }
    @PostMapping(consumes=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<RoomResponse> create(Authentication auth,@RequestBody String body,jakarta.servlet.http.HttpServletRequest request) {
        var validate=sessions.validation(request.getSession(false),users.requireActiveUser(auth).getId());
        return response(HttpStatus.CREATED,operations.create(auth,decoder.read(body,CreateRoomRequest.class),validate).snapshot());
    }
    @GetMapping public ResponseEntity<RoomListResponse> list(Authentication auth,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return response(HttpStatus.OK,operations.list(auth,page,size));
    }
    @GetMapping("/{id}") public ResponseEntity<RoomResponse> get(Authentication auth,@PathVariable long id) { return response(HttpStatus.OK,operations.get(auth,id)); }
    @GetMapping("/by-code/{code}") public ResponseEntity<RoomPreviewResponse> preview(Authentication auth,@PathVariable String code) { return response(HttpStatus.OK,operations.preview(auth,code)); }
    @PutMapping(value="/{id}",consumes=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<RoomResponse> edit(Authentication auth,@PathVariable long id,@RequestBody String body,jakarta.servlet.http.HttpServletRequest http) {
        EditRoomRequest request=decoder.read(body,EditRoomRequest.class); long uid=users.requireActiveUser(auth).getId();
        var receipt=operations.execute(auth,id,request.requestId(),"EDIT_ROOM",Map.of("revision",request.revision(),"config",request.config()),null,
                sessions.validation(http.getSession(false),uid),()->rooms.edit(uid,id,request.revision(),request.config()),(r,replay)->{});
        return response(HttpStatus.OK,receipt.snapshot());
    }
    @PostMapping(value="/{id}/open",consumes=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<RoomResponse> open(Authentication auth,@PathVariable long id,@RequestBody String body,jakarta.servlet.http.HttpServletRequest http) { return transition(auth,id,body,true,http); }
    @PostMapping(value="/{id}/close",consumes=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<RoomResponse> close(Authentication auth,@PathVariable long id,@RequestBody String body,jakarta.servlet.http.HttpServletRequest http) { return transition(auth,id,body,false,http); }
    private ResponseEntity<RoomResponse> transition(Authentication auth,long id,String body,boolean open,jakarta.servlet.http.HttpServletRequest http) {
        var request=decoder.read(body,RoomRevisionRequest.class); long uid=users.requireActiveUser(auth).getId();
        var receipt=operations.execute(auth,id,request.requestId(),open?"OPEN_ROOM":"CLOSE_ROOM",Map.of("revision",request.revision()),null,
                sessions.validation(http.getSession(false),uid),()->open?rooms.open(uid,id,request.revision()):rooms.close(uid,id,request.revision()),(r,replay)->{});
        return response(HttpStatus.OK,receipt.snapshot());
    }
    private <T> ResponseEntity<T> response(HttpStatus status,T body) { return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(body); }
}
