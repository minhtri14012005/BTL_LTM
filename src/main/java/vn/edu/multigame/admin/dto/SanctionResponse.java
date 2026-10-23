package vn.edu.multigame.admin.dto;

import vn.edu.multigame.auth.service.AccountAccessPolicy;
public record SanctionResponse(String requestId,long actionId,long userId,String action,String result,
        String committedKind,Long committedUntilMs,long committedVersion,long effectiveAtMs,
        boolean replayed,AccountAccessPolicy.State current,long serverTimeMs,int pendingEffects) {
    @com.fasterxml.jackson.annotation.JsonProperty("status")
    public String status() { return current.kind().equals("INDEFINITE") ? "EXPELLED" : "ACTIVE"; }
}
