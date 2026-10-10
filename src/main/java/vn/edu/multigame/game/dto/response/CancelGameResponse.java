package vn.edu.multigame.game.dto.response;
public record CancelGameResponse(String requestId,long gameSessionId,long revision,long serverTimeMs,GameSnapshot finalSnapshot) {}
