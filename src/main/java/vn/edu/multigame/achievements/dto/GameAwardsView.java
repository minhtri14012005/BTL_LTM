package vn.edu.multigame.achievements.dto;
import java.util.*;
import vn.edu.multigame.achievements.model.AwardEvaluator;
/** Public committed view, deliberately excludes ban/audit/session evidence. */
public record GameAwardsView(long gameSessionId,String resultState,String availability,String awardState,boolean decisionRecorded,
        Long decisionRevision,String policyVersion,Long eligibilityEvaluatedAtMs,Long decidedAtMs,long serverTimeMs,
        Qualification qualification,List<Participant> participants,List<Award> awards) {
    public record Qualification(boolean qualified,List<String> reasonCodes,int initialPlayerCount,int eligiblePlayerCount,
            int configuredCount,int openedCount,int scoredCount,int eligibleCorrectCount) {}
    public record Participant(long userId,String displayNameSnapshot,int historyRank,Integer awardRank,boolean eligible,
            List<String> publicReasonCodes,int officialGameDelta,int officialWinDelta) {}
    public record Award(long awardId,long userId,String type,String modeKey,int value,long earnedAtMs,List<AwardEvaluator.Metric> measuredMetrics,Map<String,Object> evidence) {}
}
