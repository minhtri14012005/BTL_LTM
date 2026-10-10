package vn.edu.multigame.realtime.message.event;

public record AuthReady(String kind, String type, long serverTimeMs, Identity payload) {
    public record Identity(long userId, String username,long connectionGeneration) {}
    public static AuthReady of(long userId, String username,long generation) {
        return new AuthReady("EVENT", "AUTH_READY", System.currentTimeMillis(), new Identity(userId, username,generation));
    }
}
