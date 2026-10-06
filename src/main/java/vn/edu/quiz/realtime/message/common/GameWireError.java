package vn.edu.quiz.realtime.message.common;

public record GameWireError(int v,String kind,String requestId,GameTarget target,Integer questionIndex,
        String code,String message,boolean retryable,long serverTimeMs) {}
