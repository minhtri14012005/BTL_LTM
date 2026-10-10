package vn.edu.multigame.game.service;

import java.util.*;
import com.fasterxml.jackson.databind.JsonNode;
import static org.assertj.core.api.Assertions.*;

/** Required/nullability/privacy expectations copied from canonical GameSnapshot contract. */
final class GameSnapshotAssertions {
    private GameSnapshotAssertions() {}
    private static void keys(JsonNode node,String fields) {
        var names=new HashSet<String>();node.fieldNames().forEachRemaining(names::add);
        assertThat(node.isObject()).isTrue();assertThat(names).containsExactlyInAnyOrder(fields.split(" "));
    }
    private static void integer(JsonNode node,String field) {assertThat(node.get(field).isIntegralNumber()).as(field).isTrue();}
    private static void nullableInteger(JsonNode node,String field) {
        assertThat(node.has(field)).as(field+" required even when null").isTrue();assertThat(node.get(field).isNull() || node.get(field).isIntegralNumber()).as(field).isTrue();
    }
    static void snapshot(JsonNode s,long recipient) {
        keys(s,"gameSessionId roomId quizId quizAuthorUserId quizTitleSnapshot status phase questionIndex questionCount revision serverTimeMs deadlineEpochMs remainingMs runtimeState cleanupPending endReason winners config members question results player hasOfficialWinner schemaVersion v2Config stages stageIndex stage readyPlayers");
        integer(s,"schemaVersion");assertThat(s.get("schemaVersion").asInt()).isIn(1,2);boolean v2=s.get("schemaVersion").asInt()==2;
        for(String key:List.of("gameSessionId","roomId","questionIndex","questionCount","revision","serverTimeMs"))integer(s,key);
        assertThat(s.get("questionCount").asInt()).isBetween(v2?1:10,50);assertThat(s.get("questionIndex").asInt()).isBetween(0,s.get("questionCount").asInt());
        for(String key:List.of("deadlineEpochMs","remainingMs"))nullableInteger(s,key);
        assertThat(s.get("status").asText()).isIn("ACTIVE","FINISHED");assertThat(s.get("phase").asText()).isIn("INTRO","DECISION","QUESTION_OPEN","QUESTION_CLOSED","SCORING","RESULT","FINISHED");
        assertThat(s.get("hasOfficialWinner").isBoolean()).isTrue();assertThat(s.get("cleanupPending").isBoolean()).isTrue();
        assertThat(s.get("members").isArray()).isTrue();assertThat(s.get("results").isArray()).isTrue();assertThat(s.get("winners").isArray()).isTrue();assertThat(s.get("stages").isArray()).isTrue();assertThat(s.get("readyPlayers").isArray()).isTrue();nullableInteger(s,"stageIndex");
        if(v2) {
            assertThat(s.get("config").isNull()).isTrue();assertThat(s.get("quizId").isNull()).isTrue();assertThat(s.get("quizAuthorUserId").isNull()).isTrue();assertThat(s.get("quizTitleSnapshot").isNull()).isTrue();
            keys(s.get("v2Config"),"schemaVersion rulesVersion questionCount quizQuestionCount initialScore minimumScore spinCredits starCredits decisionDurationMs introDurationMs resultDurationMs eliminationRule rankingRule");
            assertThat(s.get("v2Config").get("initialScore").asInt()).isZero();assertThat(s.get("v2Config").get("minimumScore").asInt()).isZero();assertThat(s.get("v2Config").get("eliminationRule").asText()).isEqualTo("NONE");
            assertThat(s.get("stages").size()).isBetween(1,7);
            for(var stage:s.get("stages"))keys(stage,"id orderIndex mode sourceQuizId authorUserId title firstQuestionIndex questionCount questionDurationMs");
        } else {
            integer(s,"quizId");integer(s,"quizAuthorUserId");assertThat(s.get("config").isObject()).isTrue();assertThat(s.get("v2Config").isNull()).isTrue();assertThat(s.get("stages")).isEmpty();assertThat(s.get("readyPlayers")).isEmpty();assertThat(s.get("stageIndex").isNull()).isTrue();assertThat(s.get("stage").isNull()).isTrue();
        }
        if(s.get("status").asText().equals("ACTIVE")) {assertThat(s.get("endReason").isNull()).isTrue();assertThat(s.get("hasOfficialWinner").asBoolean()).isFalse();assertThat(s.get("winners")).isEmpty();}
        else {assertThat(s.get("endReason").asText()).isIn("COMPLETED","ONE_SURVIVOR","ALL_ELIMINATED","CANCELLED","SERVER_INTERRUPTED");if(s.get("deadlineEpochMs").isNull())assertThat(s.get("remainingMs").isNull()).isTrue();
            else { assertThat(s.get("hasOfficialWinner").asBoolean()).isTrue(); assertThat(s.get("remainingMs").asLong()).isBetween(0L,1500L); }}
        if(s.get("endReason").asText().equals("CANCELLED") || s.get("endReason").asText().equals("SERVER_INTERRUPTED")) {assertThat(s.get("hasOfficialWinner").asBoolean()).isFalse();assertThat(s.get("winners")).isEmpty();}
        JsonNode self=null;
        for(var m:s.get("members")) {
            keys(m,"userId displayName role participation playerState score totalAnswerTimeMs rank totalCorrectAnswerTimeMs");integer(m,"userId");
            assertThat(m.get("role").asText()).isIn("HOST","MEMBER");assertThat(m.get("participation").asText()).isIn("PLAYER","SPECTATOR");
            for(String key:List.of("score","totalAnswerTimeMs","rank","totalCorrectAnswerTimeMs"))nullableInteger(m,key);
            if(m.get("participation").asText().equals("SPECTATOR"))for(String key:List.of("playerState","score","totalAnswerTimeMs","rank","totalCorrectAnswerTimeMs"))assertThat(m.get(key).isNull()).as(key).isTrue();
            if(!v2)assertThat(m.get("totalCorrectAnswerTimeMs").isNull()).isTrue();
            if(m.get("userId").asLong()==recipient)self=m;
        }
        assertThat(self).as("authenticated recipient must belong to immutable roster").isNotNull();
        var p=s.get("player");
        if(self.get("participation").asText().equals("SPECTATOR"))assertThat(p.isNull()).isTrue();
        else {
            keys(p,"userId state score totalAnswerTimeMs winStreak loseStreak momentum recovery remainingSpins starAvailable remainingSpinPool currentSpin starSelected alreadyAnswered selectedOption eliminatedAtMs eliminatedQuestionIndex totalCorrectAnswerTimeMs submittedAnswer");
            assertThat(p.get("userId").asLong()).isEqualTo(recipient);
            for(String key:List.of("userId","score","totalAnswerTimeMs","winStreak","loseStreak","remainingSpins"))integer(p,key);
            for(String key:List.of("momentum","recovery","starAvailable","starSelected","alreadyAnswered"))assertThat(p.get(key).isBoolean()).as(key).isTrue();
            nullableInteger(p,"totalCorrectAnswerTimeMs");if(v2)integer(p,"totalCorrectAnswerTimeMs");else assertThat(p.get("totalCorrectAnswerTimeMs").isNull()).isTrue();
            if(!p.get("submittedAnswer").isNull()) {keys(p.get("submittedAnswer"),"text");assertThat(p.get("submittedAnswer").get("text").isTextual()).isTrue();}
            assertThat(p.get("remainingSpinPool").isArray()).isTrue();nullableInteger(p,"eliminatedAtMs");nullableInteger(p,"eliminatedQuestionIndex");
        }
        var q=s.get("question");var phase=s.get("phase").asText();
        if(Set.of("INTRO","DECISION").contains(phase))assertThat(q.isNull()).isTrue();
        if(!q.isNull()) {
            keys(q,"id content options imageRef correctAnswer mode stageQuestionIndex payload");integer(q,"id");nullableInteger(q,"stageQuestionIndex");if(q.get("mode").asText().equals("QUIZ")){keys(q.get("options"),"A B C D");assertThat(q.get("payload").isNull()).isTrue();}else {assertThat(q.get("options")).isEmpty();assertThat(q.get("payload").isObject()).isTrue();if(Set.of("QUESTION_OPEN","QUESTION_CLOSED","SCORING").contains(phase))assertThat(q.get("payload")).isEmpty();}assertThat(q.get("content").isTextual()).isTrue();
            if(Set.of("QUESTION_OPEN","QUESTION_CLOSED","SCORING").contains(phase))assertThat(q.get("correctAnswer").isNull()).isTrue();
            assertThat(q.get("imageRef").isNull() || q.get("imageRef").asText().matches("sha256:[0-9a-f]{64}")).isTrue();
        }
        if(Set.of("INTRO","DECISION","QUESTION_OPEN","QUESTION_CLOSED","SCORING").contains(phase))assertThat(s.get("results")).isEmpty();
        for(var r:s.get("results")) {
            keys(r,"userId outcome baseDelta scoreDelta scoreAfter answerTimeMs totalAnswerTimeMs winStreak loseStreak hasMomentumBefore hasRecoveryBefore hasMomentumAfter hasRecoveryAfter momentumConsumed recoveryConsumed momentumGranted recoveryGranted eliminatedNow playerState eliminatedAtMs eliminatedQuestionIndex totalCorrectAnswerTimeMs ruleDelta");
            assertThat(r.get("outcome").asText()).isIn("CORRECT","WRONG","NO_ANSWER");nullableInteger(r,"eliminatedAtMs");nullableInteger(r,"eliminatedQuestionIndex");
            nullableInteger(r,"totalCorrectAnswerTimeMs");nullableInteger(r,"ruleDelta");if(!v2){assertThat(r.get("totalCorrectAnswerTimeMs").isNull()).isTrue();assertThat(r.get("ruleDelta").isNull()).isTrue();}else {integer(r,"totalCorrectAnswerTimeMs");integer(r,"ruleDelta");assertThat(r.get("eliminatedNow").asBoolean()).isFalse();}
            // No selectedOption/Spin/Star/remaining pool for another Player in public results.
        }
        assertThat(s.toString()).doesNotContain("passwordHash","password_hash");
    }
}
