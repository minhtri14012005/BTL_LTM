package vn.edu.multigame.admin.dto;
public record KickResponse(String requestId,long actionId,long gameId,long userId,String status,long effectiveAtMs,
        long revision,boolean occupancyReleased,boolean membershipDetached,boolean replayed) {}
