package vn.edu.quiz.realtime.message.common;

import java.util.List;
import vn.edu.quiz.game.enums.SpinEffect;
import vn.edu.quiz.quiz.enums.Option;

public record GameAck(int v,String kind,String requestId,String type,GameTarget target,Integer questionIndex,
        String status,long revision,long serverTimeMs,ReplyPayload payload) {
    public sealed interface ReplyPayload permits Payload,CancelPayload {}
    public record CancelPayload(vn.edu.quiz.game.dto.response.GameSnapshot finalSnapshot) implements ReplyPayload {}
    public record Payload(Option selectedOption,boolean alreadyAnswered,SpinEffect spinEffect,
            boolean starSelected,int remainingSpins,boolean starAvailable,List<SpinEffect> remainingSpinPool) implements ReplyPayload {
        public Payload { remainingSpinPool=List.copyOf(remainingSpinPool); }
    }
}
