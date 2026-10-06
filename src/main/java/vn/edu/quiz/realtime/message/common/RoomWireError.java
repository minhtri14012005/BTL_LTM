package vn.edu.quiz.realtime.message.common;
public record RoomWireError(int v,String kind,String requestId,RoomTarget target,String code,String message,boolean retryable,long serverTimeMs) {}
