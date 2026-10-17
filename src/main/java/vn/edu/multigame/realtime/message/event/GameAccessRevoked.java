package vn.edu.multigame.realtime.message.event;
import vn.edu.multigame.realtime.message.common.GameTarget;
/** Control scope, deliberately no gameplay payload or internal admin reason. */
public record GameAccessRevoked(int v,String kind,String type,GameTarget target,String code,long serverTimeMs) {}
