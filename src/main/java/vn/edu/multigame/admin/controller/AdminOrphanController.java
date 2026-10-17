package vn.edu.multigame.admin.controller;

import jakarta.validation.Valid;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import vn.edu.multigame.admin.dto.OrphanCloseRequest;
import vn.edu.multigame.admin.service.OrphanCloseTransactions;
import vn.edu.multigame.realtime.idempotency.CommandFingerprint;
import vn.edu.multigame.realtime.session.RoomBoundary;
import vn.edu.multigame.room.service.RoomChanged;

@RestController @Profile("mysql")
public class AdminOrphanController {
    private final RoomBoundary rooms;
    private final OrphanCloseTransactions transactions;
    private final CommandFingerprint fingerprints;
    private final ApplicationEventPublisher events;
    public AdminOrphanController(RoomBoundary rooms,OrphanCloseTransactions transactions,CommandFingerprint fingerprints,ApplicationEventPublisher events){this.rooms=rooms;this.transactions=transactions;this.fingerprints=fingerprints;this.events=events;}
    @PostMapping("/api/admin/rooms/{id}/close-orphan") ResponseEntity<OrphanCloseTransactions.Receipt> close(Authentication auth,@PathVariable long id,@Valid @RequestBody OrphanCloseRequest request){
        var receipt=rooms.read(RoomBoundary.Scope.room(id),()->{
            var committed=transactions.close(auth,id,request,fingerprints.of("CLOSE_ORPHAN","ROOM",id,null,request));
            if(committed.changed()!=null)events.publishEvent(new RoomChanged(committed.changed()));return committed.receipt();
        });return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(receipt);
    }
}
