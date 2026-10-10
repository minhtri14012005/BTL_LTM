package vn.edu.multigame.realtime.message.event;

public record SessionReplaced(String kind,String type,long serverTimeMs,Payload payload) {
    public record Payload(long connectionGeneration,long replacementGeneration) {}
    public static SessionReplaced of(long previous,long replacement) {
        return new SessionReplaced("EVENT","SESSION_REPLACED",System.currentTimeMillis(),new Payload(previous,replacement));
    }
}
