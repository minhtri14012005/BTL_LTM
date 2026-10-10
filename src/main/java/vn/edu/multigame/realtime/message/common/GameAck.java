package vn.edu.multigame.realtime.message.common;

import java.util.List;
import vn.edu.multigame.game.enums.SpinEffect;
import vn.edu.multigame.quiz.enums.Option;

public record GameAck(int v,String kind,String requestId,String type,GameTarget target,Integer questionIndex,
        String status,long revision,long serverTimeMs,ReplyPayload payload) {
    public sealed interface ReplyPayload permits Payload,CancelPayload,ContinuePayload {}
    public record ContinuePayload(int stageIndex,List<Long> readyPlayers) implements ReplyPayload {public ContinuePayload {readyPlayers=List.copyOf(readyPlayers);}}
    public record CancelPayload(vn.edu.multigame.game.dto.response.GameSnapshot finalSnapshot) implements ReplyPayload {}
    public record Payload(Option selectedOption,boolean alreadyAnswered,SpinEffect spinEffect,
            boolean starSelected,int remainingSpins,boolean starAvailable,List<SpinEffect> remainingSpinPool,vn.edu.multigame.game.dto.TypedAnswer submittedAnswer) implements ReplyPayload {
        public Payload(Option selectedOption,boolean alreadyAnswered,SpinEffect spinEffect,boolean starSelected,int remainingSpins,boolean starAvailable,List<SpinEffect> remainingSpinPool) {this(selectedOption,alreadyAnswered,spinEffect,starSelected,remainingSpins,starAvailable,remainingSpinPool,null);}
        public Payload { remainingSpinPool=List.copyOf(remainingSpinPool); }
    }
}
