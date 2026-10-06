package vn.edu.quiz.game.controller;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import vn.edu.quiz.auth.service.AuthService;
import vn.edu.quiz.game.dto.request.StartGameRequest;
import vn.edu.quiz.game.dto.response.*;
import vn.edu.quiz.realtime.session.*;
import vn.edu.quiz.room.controller.RoomRequestDecoder;
@RestController @Profile("mysql")
public class GameController {
    private final GameOperations operations;
    private final GameRuntime runtime;
    private final AuthService users;
    private final RoomRequestDecoder decoder;
    private final vn.edu.quiz.auth.security.AuthSessionRegistry sessions;
    public GameController(GameOperations operations,GameRuntime runtime,AuthService users,RoomRequestDecoder decoder,vn.edu.quiz.auth.security.AuthSessionRegistry sessions) {
        this.operations=operations; this.runtime=runtime; this.users=users; this.decoder=decoder;
        this.sessions=sessions;
    }
    @PostMapping(value="/api/rooms/{roomId}/start",consumes=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<StartGameResponse> start(Authentication auth,@PathVariable long roomId,@RequestBody String body,jakarta.servlet.http.HttpServletRequest request) {
        var validate=sessions.validation(request.getSession(false),users.requireActiveUser(auth).getId());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(operations.start(auth,roomId,decoder.read(body,StartGameRequest.class),validate));
    }
    @GetMapping("/api/games/{gameId}/snapshot")
    public ResponseEntity<GameSnapshot> snapshot(Authentication auth,@PathVariable long gameId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(runtime.snapshot(gameId,users.requireActiveUser(auth).getId()));
    }
    @PostMapping(value="/api/games/{gameId}/cancel",consumes=MediaType.APPLICATION_JSON_VALUE)
    public java.util.concurrent.CompletableFuture<ResponseEntity<CancelGameResponse>> cancel(Authentication auth,
            @PathVariable long gameId,@RequestBody String body,jakarta.servlet.http.HttpServletRequest request) {
        long userId=users.requireActiveUser(auth).getId();
        if(gameId<1) throw new vn.edu.quiz.game.service.GameFailure(HttpStatus.BAD_REQUEST,"INVALID_REQUEST");
        var input=decoder.read(body,vn.edu.quiz.game.dto.request.CancelGameRequest.class);
        var session=request.getSession(false);
        var command=new vn.edu.quiz.realtime.message.command.GameCommand(1,"COMMAND",input.requestId(),"CANCEL_GAME",
                vn.edu.quiz.realtime.message.common.GameTarget.of(gameId),null,com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode());
        return runtime.command(userId,command,() -> {
            try {
                var context=session==null?null:session.getAttribute(org.springframework.security.web.context.HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
                if(!(context instanceof org.springframework.security.core.context.SecurityContext security)
                        || users.requireActiveUser(security.getAuthentication()).getId()!=userId) throw new vn.edu.quiz.auth.service.AuthFailure(HttpStatus.UNAUTHORIZED,"UNAUTHENTICATED","UNAUTHENTICATED");
            } catch(IllegalStateException expired) {throw new vn.edu.quiz.auth.service.AuthFailure(HttpStatus.UNAUTHORIZED,"UNAUTHENTICATED","UNAUTHENTICATED");}
        }).thenApply(ack -> ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new CancelGameResponse(ack.requestId(),gameId,ack.revision(),ack.serverTimeMs(),
                ((vn.edu.quiz.realtime.message.common.GameAck.CancelPayload)ack.payload()).finalSnapshot())));
    }
}
