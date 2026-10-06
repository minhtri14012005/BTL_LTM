package vn.edu.quiz.game.service;

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
        keys(s,"gameSessionId roomId quizId quizAuthorUserId quizTitleSnapshot status phase questionIndex questionCount revision serverTimeMs deadlineEpochMs remainingMs runtimeState cleanupPending endReason winners config members question results player hasOfficialWinner");
        for(String key:List.of("gameSessionId","roomId","quizId","quizAuthorUserId","questionIndex","questionCount","revision","serverTimeMs"))integer(s,key);
        assertThat(s.get("questionCount").asInt()).isBetween(10,50);assertThat(s.get("questionIndex").asInt()).isBetween(0,s.get("questionCount").asInt());
        for(String key:List.of("deadlineEpochMs","remainingMs"))nullableInteger(s,key);
        assertThat(s.get("status").asText()).isIn("ACTIVE","FINISHED");assertThat(s.get("phase").asText()).isIn("DECISION","QUESTION_OPEN","QUESTION_CLOSED","SCORING","RESULT","FINISHED");
        assertThat(s.get("hasOfficialWinner").isBoolean()).isTrue();assertThat(s.get("cleanupPending").isBoolean()).isTrue();
        assertThat(s.get("members").isArray()).isTrue();assertThat(s.get("results").isArray()).isTrue();assertThat(s.get("winners").isArray()).isTrue();assertThat(s.get("config").isObject()).isTrue();
        if(s.get("status").asText().equals("ACTIVE")) {assertThat(s.get("endReason").isNull()).isTrue();assertThat(s.get("hasOfficialWinner").asBoolean()).isFalse();assertThat(s.get("winners")).isEmpty();}
        else {assertThat(s.get("endReason").asText()).isIn("COMPLETED","ONE_SURVIVOR","ALL_ELIMINATED","CANCELLED","SERVER_INTERRUPTED");assertThat(s.get("deadlineEpochMs").isNull()).isTrue();assertThat(s.get("remainingMs").isNull()).isTrue();}
        if(s.get("endReason").asText().equals("CANCELLED") || s.get("endReason").asText().equals("SERVER_INTERRUPTED")) {assertThat(s.get("hasOfficialWinner").asBoolean()).isFalse();assertThat(s.get("winners")).isEmpty();}
        JsonNode self=null;
        for(var m:s.get("members")) {
            keys(m,"userId displayName role participation playerState score totalAnswerTimeMs rank");integer(m,"userId");
            assertThat(m.get("role").asText()).isIn("HOST","MEMBER");assertThat(m.get("participation").asText()).isIn("PLAYER","SPECTATOR");
            for(String key:List.of("score","totalAnswerTimeMs","rank"))nullableInteger(m,key);
            if(m.get("participation").asText().equals("SPECTATOR"))for(String key:List.of("playerState","score","totalAnswerTimeMs","rank"))assertThat(m.get(key).isNull()).as(key).isTrue();
            if(m.get("userId").asLong()==recipient)self=m;
        }
        assertThat(self).as("authenticated recipient must belong to immutable roster").isNotNull();
        var p=s.get("player");
        if(self.get("participation").asText().equals("SPECTATOR"))assertThat(p.isNull()).isTrue();
        else {
            keys(p,"userId state score totalAnswerTimeMs winStreak loseStreak momentum recovery remainingSpins starAvailable remainingSpinPool currentSpin starSelected alreadyAnswered selectedOption eliminatedAtMs eliminatedQuestionIndex");
            assertThat(p.get("userId").asLong()).isEqualTo(recipient);
            for(String key:List.of("userId","score","totalAnswerTimeMs","winStreak","loseStreak","remainingSpins"))integer(p,key);
            for(String key:List.of("momentum","recovery","starAvailable","starSelected","alreadyAnswered"))assertThat(p.get(key).isBoolean()).as(key).isTrue();
            assertThat(p.get("remainingSpinPool").isArray()).isTrue();nullableInteger(p,"eliminatedAtMs");nullableInteger(p,"eliminatedQuestionIndex");
        }
        var q=s.get("question");var phase=s.get("phase").asText();
        if(phase.equals("DECISION"))assertThat(q.isNull()).isTrue();
        if(!q.isNull()) {
            keys(q,"id content options imageRef correctAnswer");integer(q,"id");keys(q.get("options"),"A B C D");assertThat(q.get("content").isTextual()).isTrue();
            if(Set.of("QUESTION_OPEN","QUESTION_CLOSED","SCORING").contains(phase))assertThat(q.get("correctAnswer").isNull()).isTrue();
            assertThat(q.get("imageRef").isNull() || q.get("imageRef").asText().matches("sha256:[0-9a-f]{64}")).isTrue();
        }
        if(Set.of("DECISION","QUESTION_OPEN","QUESTION_CLOSED","SCORING").contains(phase))assertThat(s.get("results")).isEmpty();
        for(var r:s.get("results")) {
            keys(r,"userId outcome baseDelta scoreDelta scoreAfter answerTimeMs totalAnswerTimeMs winStreak loseStreak hasMomentumBefore hasRecoveryBefore hasMomentumAfter hasRecoveryAfter momentumConsumed recoveryConsumed momentumGranted recoveryGranted eliminatedNow playerState eliminatedAtMs eliminatedQuestionIndex");
            assertThat(r.get("outcome").asText()).isIn("CORRECT","WRONG","NO_ANSWER");nullableInteger(r,"eliminatedAtMs");nullableInteger(r,"eliminatedQuestionIndex");
            // No selectedOption/Spin/Star/remaining pool for another Player in public results.
        }
        assertThat(s.toString()).doesNotContain("passwordHash","password_hash");
    }
}
