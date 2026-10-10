package vn.edu.multigame.realtime.message.common;
public record RoomAck(int v,String kind,String requestId,String type,RoomTarget target,String status,long revision,long serverTimeMs,Object payload) {}
