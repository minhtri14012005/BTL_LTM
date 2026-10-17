package vn.edu.multigame.achievements.model;

import java.util.*;

/** Signed milestones over qualified E and verified historical stage facts. Never mutates score/rank. */
public final class AdvancedAwardEvaluator {
    public List<AwardEvaluator.Award> evaluate(ApprovedAwardPolicy policy,AwardEvaluator.Result base,
            List<AwardEvaluator.Player> players,StageRankEvidence stage) {
        policy.requireSupported();var rules=policy.advanced();
        if(!base.qualified() || rules==null)return List.of();
        var eligible=players.stream().filter(AwardEvaluator.Player::eligible).toList();
        var awards=new ArrayList<AwardEvaluator.Award>();
        for(var p:eligible){
            var modes=p.modes().stream().filter(m->m.correct()>=rules.multiMinCorrect()
                    && 100L*m.correct()>=(long)rules.multiAccuracyPercent()*m.participated()).toList();
            if(modes.stream().map(AwardEvaluator.Metric::mode).distinct().count()>=rules.multiMinModes())
                awards.add(new AwardEvaluator.Award(p.userId(),"MULTITALENT","ALL",Map.of("policyVersion",policy.policyId(),
                        "minModes",rules.multiMinModes(),"minCorrectPerMode",rules.multiMinCorrect(),"minAccuracyPercent",rules.multiAccuracyPercent(),"qualifiedModes",modes)));
        }
        if(stage==null)throw new IllegalStateException("AWARD_STAGE_DATA_INCOMPLETE");
        if(stage.stageCount()>=rules.comebackMinStages()){
            var expected=eligible.stream().map(AwardEvaluator.Player::userId).collect(java.util.stream.Collectors.toSet());
            if(stage.firstStage().size()!=expected.size() || !stage.firstStage().stream().map(StageRankEvidence.Standing::userId).collect(java.util.stream.Collectors.toSet()).equals(expected))throw new IllegalStateException("AWARD_STAGE_DATA_INCOMPLETE");
            int worst=stage.firstStage().stream().mapToInt(StageRankEvidence.Standing::rank).max().orElseThrow();
            var last=stage.firstStage().stream().filter(p->p.rank()==worst).toList();
            var winners=base.ranks().entrySet().stream().filter(e->e.getValue()==1).toList();
            if(last.size()==1 && winners.size()==1 && last.getFirst().userId()==winners.getFirst().getKey()){
                var p=last.getFirst();awards.add(new AwardEvaluator.Award(p.userId(),"COMEBACK","ALL",Map.of("policyVersion",policy.policyId(),
                        "stageCount",stage.stageCount(),"stageOneEndQuestion",stage.firstStageEndQuestion(),"stageOneRank",p.rank(),
                        "stageOneScore",p.score(),"stageOneCorrectTimeMs",p.correctTimeMs(),"finalEligibleRank",1,"eligibleCount",expected.size())));
            }
        }
        return List.copyOf(awards);
    }
}
