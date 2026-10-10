package vn.edu.multigame.game.engine;
import vn.edu.multigame.game.dto.GameplayRulesSnapshot;
import vn.edu.multigame.game.enums.*;
import vn.edu.multigame.game.enums.GameMode;
/** Pure version 2 calculator; Quiz tables/effect order are shared with the version 1 engine. */
public final class MultimodeScoreEngine {
    public ScoringResult calculate(GameMode mode,long duration,boolean lastInStage,ScoringInput input) {
        if(input.player().state()!=PlayerState.PLAYING) throw new IllegalArgumentException("V2 does not eliminate players");
        if(mode==GameMode.QUIZ) return new ScoreEngine(GameplayRulesSnapshot.forGame(10,duration,7000)).calculateV2(input);
        if(input.spin()!=null || input.starSelected()) throw new IllegalArgumentException("Quiz resources cannot be used outside Quiz");
        if(duration<=0 || input.answeredTimeMs()!=null && input.answeredTimeMs()>=duration) throw new IllegalArgumentException("Invalid answer duration");
        var before=input.player(); boolean correct=input.outcome()==AnswerStatus.CORRECT;
        int delta=correct?(lastInStage?20:10):0;
        long elapsed=input.outcome()==AnswerStatus.NO_ANSWER?duration:input.answeredTimeMs();
        var after=new PlayerScore(PlayerState.PLAYING,Math.addExact(before.score(),delta),
            Math.addExact(before.totalAnswerTimeMs(),correct?elapsed:0),before.winStreak(),before.loseStreak(),before.momentum(),before.recovery());
        return new ScoringResult(input.outcome(),delta,delta,elapsed,after,false,false,false,false,false,delta);
    }
}
