package vn.edu.multigame.game.engine;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import vn.edu.multigame.game.enums.*;
import vn.edu.multigame.game.enums.GameMode;
import static org.assertj.core.api.Assertions.*;
class MultimodeScoreEngineTest {
    final MultimodeScoreEngine engine=new MultimodeScoreEngine();
    static Stream<Arguments> quizTable() {
        // Literal expected values from Overview 8.2/8.6/8.7; never generated from production tables.
        Object[][] table={{null,false,10,-4,-1},{null,true,25,-14,-7},
            {SpinEffect.BONUS,false,15,-4,-1},{SpinEffect.BONUS,true,30,-4,-1},
            {SpinEffect.SAFE,false,8,-2,0},{SpinEffect.SAFE,true,20,-2,0},
            {SpinEffect.BREAKTHROUGH,false,18,-7,-3},{SpinEffect.BREAKTHROUGH,true,30,-12,-6},
            {SpinEffect.SPEED,false,22,-10,-4},{SpinEffect.SPEED,true,35,-16,-8},
            {SpinEffect.DECISIVE,false,28,-15,-7},{SpinEffect.DECISIVE,true,40,-22,-10},
            {SpinEffect.HARDSHIP,false,8,-6,-2}};
        return Stream.of(table).flatMap(row -> Stream.of(Arguments.of(row[0],row[1],AnswerStatus.CORRECT,row[2]),
            Arguments.of(row[0],row[1],AnswerStatus.WRONG,row[3]),Arguments.of(row[0],row[1],AnswerStatus.NO_ANSWER,row[4])));
    }
    PlayerScore player(int score,boolean momentum,boolean recovery) {return new PlayerScore(PlayerState.PLAYING,score,120,0,0,momentum,recovery);}
    @ParameterizedTest @MethodSource("quizTable")
    void preservesQuizTablesWithoutElimination(SpinEffect spin,boolean star,AnswerStatus outcome,int expected) {
        var result=engine.calculate(GameMode.QUIZ,1000,false,new ScoringInput(player(50,false,false),outcome,spin,star,outcome==AnswerStatus.NO_ANSWER?null:200L));
        assertThat(result.baseDelta()).isEqualTo(expected);assertThat(result.scoreDelta()).isEqualTo(expected);
        assertThat(result.playerAfter().score()).isEqualTo(50+expected);assertThat(result.playerAfter().state()).isEqualTo(PlayerState.PLAYING);
        assertThat(result.playerAfter().totalAnswerTimeMs()).isEqualTo(outcome==AnswerStatus.CORRECT?320:120);
    }
    @Test void floorKeepsPlayingUpdatesStreakAndRecordsActualDelta() {
        var result=engine.calculate(GameMode.QUIZ,1000,false,new ScoringInput(player(1,false,false),AnswerStatus.WRONG,null,false,200L));
        assertThat(result.ruleDelta()).isEqualTo(-4);assertThat(result.scoreDelta()).isEqualTo(-1);
        assertThat(result.playerAfter().score()).isZero();assertThat(result.playerAfter().loseStreak()).isEqualTo(1);assertThat(result.eliminatedNow()).isFalse();
    }
    @Test void fifthWrongAtZeroGrantsRecoveryForNextQuestion() {
        var before=new PlayerScore(PlayerState.PLAYING,0,0,0,4,false,false);
        var result=engine.calculate(GameMode.QUIZ,1000,false,new ScoringInput(before,AnswerStatus.WRONG,null,false,100L));
        assertThat(result.ruleDelta()).isEqualTo(-4);assertThat(result.scoreDelta()).isZero();assertThat(result.recoveryGranted()).isTrue();
        var next=engine.calculate(GameMode.QUIZ,1000,false,new ScoringInput(result.playerAfter(),AnswerStatus.WRONG,null,false,100L));
        assertThat(next.ruleDelta()).isEqualTo(-1);assertThat(next.recoveryConsumed()).isTrue();
    }
    @ParameterizedTest @CsvSource({"NONE,false,NO_ANSWER,0","NONE,false,WRONG,-1","NONE,true,NO_ANSWER,-4","DECISIVE,true,WRONG,-19","SAFE,false,NO_ANSWER,0"})
    void recoveryUsesBasePenaltyEvenAtFloor(String spin,boolean star,AnswerStatus outcome,int ruleDelta) {
        var result=engine.calculate(GameMode.QUIZ,1000,false,new ScoringInput(player(0,false,true),outcome,spin.equals("NONE")?null:SpinEffect.valueOf(spin),star,outcome==AnswerStatus.NO_ANSWER?null:100L));
        assertThat(result.ruleDelta()).isEqualTo(ruleDelta);assertThat(result.scoreDelta()).isZero();
        assertThat(result.recoveryConsumed()).isEqualTo(!spin.equals("SAFE"));
    }
    @Test void momentumAndRecoveryCoexistAndNewEffectDoesNotApplyRetroactively() {
        var before=new PlayerScore(PlayerState.PLAYING,0,0,4,0,false,true);
        var fifth=engine.calculate(GameMode.QUIZ,1000,false,new ScoringInput(before,AnswerStatus.CORRECT,null,false,100L));
        assertThat(fifth.scoreDelta()).isEqualTo(10);assertThat(fifth.playerAfter().momentum()).isTrue();assertThat(fifth.playerAfter().recovery()).isTrue();
        var next=engine.calculate(GameMode.QUIZ,1000,false,new ScoringInput(fifth.playerAfter(),AnswerStatus.CORRECT,null,false,100L));
        assertThat(next.scoreDelta()).isEqualTo(13);assertThat(next.momentumConsumed()).isTrue();assertThat(next.playerAfter().recovery()).isTrue();
    }
    @ParameterizedTest @EnumSource(value=GameMode.class,names={"QUIZ"},mode=EnumSource.Mode.EXCLUDE)
    void nonQuizLastBonusAndCorrectTimeOnly(GameMode mode) {
        var correct=engine.calculate(mode,1000,false,new ScoringInput(player(0,false,false),AnswerStatus.CORRECT,null,false,200L));
        var last=engine.calculate(mode,1000,true,new ScoringInput(player(0,false,false),AnswerStatus.CORRECT,null,false,200L));
        var wrong=engine.calculate(mode,1000,true,new ScoringInput(player(0,false,false),AnswerStatus.WRONG,null,false,300L));
        var missing=engine.calculate(mode,1000,true,new ScoringInput(player(0,false,false),AnswerStatus.NO_ANSWER,null,false,null));
        assertThat(correct.scoreDelta()).isEqualTo(10);assertThat(last.scoreDelta()).isEqualTo(20);
        assertThat(wrong.scoreDelta()).isZero();assertThat(missing.scoreDelta()).isZero();assertThat(wrong.playerAfter().totalAnswerTimeMs()).isEqualTo(120);
        assertThat(missing.playerAfter().totalAnswerTimeMs()).isEqualTo(120);assertThat(last.playerAfter().totalAnswerTimeMs()).isEqualTo(320);
    }
    @Test void quizLastHasNoDoubleAndNonQuizRejectsResources() {
        assertThat(engine.calculate(GameMode.QUIZ,1000,true,new ScoringInput(player(0,false,false),AnswerStatus.CORRECT,null,false,1L)).scoreDelta()).isEqualTo(10);
        assertThatThrownBy(() -> engine.calculate(GameMode.RIDDLE,1000,false,new ScoringInput(player(0,false,false),AnswerStatus.CORRECT,null,true,1L))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void nonQuizPreservesQuizStreakAndEffectsWithoutConsumingThem() {
        var before=new PlayerScore(PlayerState.PLAYING,0,40,4,0,true,true);
        var after=engine.calculate(GameMode.RIDDLE,1000,true,new ScoringInput(before,AnswerStatus.CORRECT,null,false,200L));
        assertThat(after.playerAfter()).isEqualTo(new PlayerScore(PlayerState.PLAYING,20,240,4,0,true,true));
        assertThat(after.momentumConsumed()).isFalse();assertThat(after.recoveryConsumed()).isFalse();
        assertThat(after.momentumGranted()).isFalse();assertThat(after.recoveryGranted()).isFalse();
    }
    @Test void tiesUseCorrectOnlyMetricAndCompetitionRanks() {
        var ranks=new RankingEngine().calculate(List.of(new RankingEngine.Entry(1,20,100),new RankingEngine.Entry(2,20,100),new RankingEngine.Entry(3,20,200)));
        assertThat(ranks.stream().map(RankingEngine.RankedPlayer::rank)).containsExactly(1,1,3);
    }
}
