package vn.edu.multigame.achievements.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Signed versions are immutable: activation never reinterprets an existing Game's policy. */
@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
public record ApprovedAwardPolicy(String policyId,int minInitialPlayers,int minConfiguredQuestions,int minScoredQuestions,
        int minEligiblePlayers,int minEligibleCorrect,int fastMinCorrect,int fastAccuracyPercent,int perfectMinQuestions,String hash,Advanced advanced) {
    public record Advanced(int multiMinModes,int multiMinCorrect,int multiAccuracyPercent,int comebackMinStages) {}
    public ApprovedAwardPolicy(String policyId,int minInitialPlayers,int minConfiguredQuestions,int minScoredQuestions,
            int minEligiblePlayers,int minEligibleCorrect,int fastMinCorrect,int fastAccuracyPercent,int perfectMinQuestions,String hash){
        this(policyId,minInitialPlayers,minConfiguredQuestions,minScoredQuestions,minEligiblePlayers,minEligibleCorrect,fastMinCorrect,fastAccuracyPercent,perfectMinQuestions,hash,null);
    }
    public static ApprovedAwardPolicy approved(){
        String canonical="AWARD_V1|3|10|10|2|1|5|60|10|CUP_FAST_PERFECT|ELIGIBLE_RANKING|NEW_GAMES_ONLY|EVALUATED_SERVER_TIME";
        try{return new ApprovedAwardPolicy("AWARD_V1",3,10,10,2,1,5,60,10,
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8))));}
        catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
    public static ApprovedAwardPolicy active(){
        String canonical="AWARD_V2|3|10|10|2|1|5|60|10|CUP_FAST_PERFECT_MULTITALENT_COMEBACK|MULTI:3:2:50|COMEBACK:2:UNIQUE_LAST_FIRST:SAME_E|ELIGIBLE_RANKING|NEW_GAMES_ONLY|EVALUATED_SERVER_TIME";
        try{return new ApprovedAwardPolicy("AWARD_V2",3,10,10,2,1,5,60,10,
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8))),new Advanced(3,2,50,2));}
        catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
    public void requireSupported(){if(!equals(approved()) && !equals(active()))throw new IllegalStateException("AWARD_POLICY_UNSUPPORTED");}
}
