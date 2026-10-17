package vn.edu.multigame.admin.controller;

import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import vn.edu.multigame.admin.dto.*;
import vn.edu.multigame.admin.service.*;
import vn.edu.multigame.realtime.idempotency.CommandFingerprint;

@RestController @Profile("mysql") @RequestMapping("/api/admin/users")
public class AdminSanctionController {
    private final AdminSanctionTransactions transactions;
    private final CommandFingerprint fingerprints;
    public AdminSanctionController(AdminSanctionTransactions transactions,CommandFingerprint fingerprints){this.transactions=transactions;this.fingerprints=fingerprints;}
    @PostMapping("/{id}/temporary-ban") ResponseEntity<SanctionResponse> temporary(Authentication auth,@PathVariable long id,@Valid @RequestBody SanctionRequest request){return apply(auth,id,"TEMP",request);}
    @PostMapping("/{id}/ban") ResponseEntity<SanctionResponse> indefinite(Authentication auth,@PathVariable long id,@Valid @RequestBody SanctionRequest request){return apply(auth,id,"INDEFINITE",request);}
    @PostMapping("/{id}/unban") ResponseEntity<SanctionResponse> unban(Authentication auth,@PathVariable long id,@Valid @RequestBody SanctionRequest request){return apply(auth,id,"UNBAN",request);}
    private ResponseEntity<SanctionResponse> apply(Authentication auth,long id,String kind,SanctionRequest request){
        if(id<=0)throw vn.edu.multigame.game.service.GameFailure.conflict("USER_UNAVAILABLE");
        String hash=fingerprints.of(kind,"ACCOUNT",id,null,request);
        try{return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(transactions.apply(auth,id,kind,request,hash));}
        catch(org.springframework.dao.DuplicateKeyException concurrentRequest){throw vn.edu.multigame.game.service.GameFailure.conflict("REQUEST_ID_REUSED");}
    }
}
