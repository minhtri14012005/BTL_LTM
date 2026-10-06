package vn.edu.quiz.realtime.message.common;
public record RoomTarget(String kind,long id) {
    public static RoomTarget of(long id) { return new RoomTarget("ROOM",id); }
}
