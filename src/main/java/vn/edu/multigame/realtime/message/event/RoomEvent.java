package vn.edu.multigame.realtime.message.event;
import vn.edu.multigame.realtime.message.common.RoomTarget;
public record RoomEvent(int v,String kind,String type,RoomTarget target,String eventId,long revision,long serverTimeMs,Object payload) {}
