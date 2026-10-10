package vn.edu.multigame.realtime.message.event;
import vn.edu.multigame.game.dto.response.GameSnapshot;
import vn.edu.multigame.realtime.message.common.GameTarget;
public record GameEvent(int v,String kind,String type,GameTarget target,Integer questionIndex,String eventId,long revision,long serverTimeMs,GameSnapshot payload) {}
