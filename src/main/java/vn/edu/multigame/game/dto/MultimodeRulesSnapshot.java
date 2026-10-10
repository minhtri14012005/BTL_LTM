package vn.edu.multigame.game.dto;
/** Persisted v2 rules shared by lifecycle, scoring and client snapshots. */
public record MultimodeRulesSnapshot(int schemaVersion, int rulesVersion, int questionCount, int quizQuestionCount,
        int initialScore, int minimumScore, int spinCredits, int starCredits, long decisionDurationMs,
        long introDurationMs, long resultDurationMs, String eliminationRule, String rankingRule) {
    public MultimodeRulesSnapshot {
        if(schemaVersion!=2 || rulesVersion!=2 || questionCount<1 || questionCount>50 || quizQuestionCount<0 || quizQuestionCount>questionCount
                || initialScore!=0 || minimumScore!=0 || spinCredits!=quizQuestionCount/10 || starCredits!=(quizQuestionCount>0?1:0)
                || decisionDurationMs!=7000 || introDurationMs!=10000 || resultDurationMs!=1500 || !"NONE".equals(eliminationRule)
                || !"SCORE_DESC_CORRECT_TIME_ASC_COMPETITION".equals(rankingRule)) throw new IllegalArgumentException("Invalid v2 rules snapshot");
    }
    public static MultimodeRulesSnapshot forGame(int count,int quizCount) {
        return new MultimodeRulesSnapshot(2,2,count,quizCount,0,0,quizCount/10,quizCount>0?1:0,7000,10000,1500,"NONE","SCORE_DESC_CORRECT_TIME_ASC_COMPETITION");
    }
}
