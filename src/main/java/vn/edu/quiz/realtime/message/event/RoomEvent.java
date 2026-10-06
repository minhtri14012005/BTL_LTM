package vn.edu.quiz.realtime.message.event;
import vn.edu.quiz.realtime.message.common.RoomTarget;
public record RoomEvent(int v,String kind,String type,RoomTarget target,String eventId,long revision,long serverTimeMs,Object payload) {}
