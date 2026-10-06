package vn.edu.quiz.realtime.session;

import java.util.function.*;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import vn.edu.quiz.auth.service.AuthService;
import vn.edu.quiz.realtime.idempotency.CommandFingerprint;
import vn.edu.quiz.room.dto.request.*;
import vn.edu.quiz.room.dto.response.*;
import vn.edu.quiz.room.service.*;

/** Adapter orchestration shared by REST and WS; Room service owns the transaction. */
@Component @Profile("mysql")
public class RoomOperations {
    private final RoomBoundary boundary;
    private final RoomService rooms;
    private final AuthService users;
    private final CommandFingerprint fingerprints;
    public RoomOperations(RoomBoundary boundary,RoomService rooms,AuthService users,CommandFingerprint fingerprints) {
        this.boundary=boundary; this.rooms=rooms; this.users=users; this.fingerprints=fingerprints;
    }
    public RoomBoundary.Receipt create(Authentication auth,CreateRoomRequest request) {
        return create(auth,request,() -> {});
    }
    public RoomBoundary.Receipt create(Authentication auth,CreateRoomRequest request,Runnable validateConnection) {
        long uid=users.requireActiveUser(auth).getId();
        return boundary.mutate(RoomBoundary.Scope.create(uid),uid,request.requestId(),fingerprints.of("CREATE_ROOM","USER_CREATE_ROOM",uid,null,request.config()),
                ()->{validateConnection.run();users.requireActiveUser(auth);},()->rooms.create(uid,request.config()),(receipt,replay)->{});
    }
    public RoomBoundary.Receipt execute(Authentication auth,long id,String requestId,String type,Object payload,String code,
            Runnable validateConnection,Supplier<RoomMutation> operation,BiConsumer<RoomBoundary.Receipt,Boolean> delivery) {
        long uid=users.requireActiveUser(auth).getId();
        rooms.authorizeReceipt(uid,id,type,code); // Do not allocate runtime scopes for unauthorized/nonexistent targets.
        return boundary.mutate(RoomBoundary.Scope.room(id),uid,requestId,fingerprints.of(type,"ROOM",id,null,payload),()->{
            validateConnection.run(); users.requireActiveUser(auth); rooms.authorizeReceipt(uid,id,type,code);
        },operation,delivery);
    }
    public RoomResponse get(Authentication auth,long id) {
        long uid=users.requireActiveUser(auth).getId(); rooms.authorizeReceipt(uid,id,"SUBSCRIBE_ROOM",null);
        return boundary.read(RoomBoundary.Scope.room(id),()->rooms.get(users.requireActiveUser(auth).getId(),id));
    }
    public RoomListResponse list(Authentication auth,int page,int size) { return rooms.list(users.requireActiveUser(auth).getId(),page,size); }
    public RoomPreviewResponse preview(Authentication auth,String code) { users.requireActiveUser(auth); return rooms.preview(code); }
}
