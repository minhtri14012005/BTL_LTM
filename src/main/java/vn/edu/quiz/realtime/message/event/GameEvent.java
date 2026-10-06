package vn.edu.quiz.realtime.message.event;
import vn.edu.quiz.game.dto.response.GameSnapshot;
import vn.edu.quiz.realtime.message.common.GameTarget;
public record GameEvent(int v,String kind,String type,GameTarget target,Integer questionIndex,String eventId,long revision,long serverTimeMs,GameSnapshot payload) {}
