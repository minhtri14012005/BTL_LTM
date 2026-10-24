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
    private final vn.edu.multigame.auth.security.AuthSessionRegistry sessions;
    public AdminSanctionController(AdminSanctionTransactions transactions,CommandFingerprint fingerprints,vn.edu.multigame.auth.security.AuthSessionRegistry sessions){this.transactions=transactions;this.fingerprints=fingerprints;this.sessions=sessions;}
    @PostMapping({"/{id}/ban","/{id}/expel"}) ResponseEntity<SanctionResponse> indefinite(Authentication auth,@PathVariable long id,@Valid @RequestBody SanctionRequest request){return apply(auth,id,"INDEFINITE",request);}
    @PostMapping({"/{id}/unban","/{id}/restore"}) ResponseEntity<SanctionResponse> unban(Authentication auth,@PathVariable long id,@Valid @RequestBody SanctionRequest request){return apply(auth,id,"UNBAN",request);}
    private ResponseEntity<SanctionResponse> apply(Authentication auth,long id,String kind,SanctionRequest request){
        if(id<=0)throw vn.edu.multigame.common.util.OperationFailure.conflict("USER_UNAVAILABLE");
        String hash=fingerprints.of(kind,"ACCOUNT",id,null,request);
        try{
            var result=transactions.apply(auth,id,kind,request,hash);
            if(kind.equals("INDEFINITE")) sessions.revokeThrough(id,result.current().epoch(),"ACCOUNT_EXPELLED");
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(result);
        }
        catch(org.springframework.dao.DuplicateKeyException concurrentRequest){throw vn.edu.multigame.common.util.OperationFailure.conflict("REQUEST_ID_REUSED");}
    }
}
