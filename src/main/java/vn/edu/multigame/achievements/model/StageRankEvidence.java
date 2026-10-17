package vn.edu.multigame.achievements.model;

import java.util.List;

/** Historical facts only. Ranks use the same eligible IDs at both decision boundaries. */
public record StageRankEvidence(int stageCount,int firstStageEndQuestion,List<Standing> firstStage) {
    public record Standing(long userId,int score,long correctTimeMs,int rank) {}
}
