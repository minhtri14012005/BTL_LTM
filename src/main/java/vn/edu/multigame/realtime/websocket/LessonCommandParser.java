package vn.edu.multigame.realtime.websocket;

import java.util.*;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import org.springframework.stereotype.Component;
import vn.edu.multigame.realtime.message.command.LessonCommand;
import vn.edu.multigame.lesson.service.LessonFailure;
import org.springframework.http.HttpStatus;

@Component
public class LessonCommandParser {
    private final ObjectMapper json;
    public LessonCommandParser(ObjectMapper json) {this.json=json.copy().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);}
    public boolean handles(String text) {
        try {var n=json.readTree(text);return n!=null && (n.path("target").path("kind").asText().equals("LESSON") || n.path("type").asText().contains("LESSON"));}
        catch(Exception malformed) {return false;}
    }
    public LessonCommand read(String text) {
        try {
            var n=json.readTree(text);fields(n,Set.of("v","kind","requestId","type","target","payload"));
            if(!n.path("v").isInt() || n.path("v").intValue()!=1 || !n.path("kind").asText().equals("COMMAND") || !n.path("requestId").asText().matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) throw invalid();
            var target=n.path("target");fields(target,Set.of("kind","id"));
            if(!target.path("kind").asText().equals("LESSON") || !number(target.path("id"),1)) throw invalid();
            String type=n.path("type").asText();var p=n.path("payload");
            switch(type) {
                case "SET_ASSIGNMENT_DEADLINE" -> {fields(p,Set.of("position","token","deadlineAtMs"));position(p);if(!number(p.path("token"),0) || !number(p.path("deadlineAtMs"),1))throw invalid();}
                case "GRADE_ASSIGNMENT" -> {fields(p,Set.of("position","studentId","submissionId","score"));position(p);if(!number(p.path("studentId"),1) || !number(p.path("submissionId"),1) || !p.path("score").isNumber() || p.path("score").decimalValue().signum()<0 || p.path("score").decimalValue().scale()>2)throw invalid();}
                case "FINALIZE_ASSIGNMENT" -> {fields(p,Set.of("position"));position(p);}
                case "SUBSCRIBE_LESSON","RECONNECT_LESSON","UNSUBSCRIBE_LESSON","ATTEND_LESSON" -> fields(p,Set.of());
                case "START_LESSON" -> {fields(p,Set.of("revision"));if(!number(p.path("revision"),0)) throw invalid();}
                case "START_LESSON_GAME" -> {fields(p,Set.of("position","revision"));position(p);if(!number(p.path("revision"),0)) throw invalid();}
                case "PARTICIPATE_LESSON_GAME" -> {fields(p,Set.of("position"));position(p);}
                case "OPEN_LESSON_WINDOW" -> {fields(p,Set.of("position","questionIndex","durationMs"));position(p);if(!number(p.path("questionIndex"),1) || p.path("questionIndex").longValue()>50 || !number(p.path("durationMs"),1) || p.path("durationMs").longValue()>Integer.MAX_VALUE) throw invalid();}
                case "CLOSE_LESSON_WINDOW","CANCEL_LESSON_WINDOW" -> {fields(p,Set.of("token"));if(!number(p.path("token"),1)) throw invalid();}
                case "SUBMIT_LESSON_ACTION" -> {fields(p,Set.of("token","value"));if(!number(p.path("token"),1) || !p.path("value").isTextual() || p.path("value").textValue().length()>5000) throw invalid();}
                default -> throw invalid();
            }
            return new LessonCommand(1,"COMMAND",n.path("requestId").asText(),type,LessonCommand.Target.lesson(target.path("id").longValue()),p.deepCopy());
        } catch(java.io.IOException malformed) {throw invalid();}
    }
    private static void position(JsonNode p) {if(!number(p.path("position"),1) || p.path("position").longValue()>3) throw invalid();}
    private static boolean number(JsonNode n,long min) {return n.isIntegralNumber() && n.canConvertToLong() && n.longValue()>=min;}
    private static void fields(JsonNode n,Set<String> keys) {if(n==null || !n.isObject() || n.size()!=keys.size()) throw invalid();n.fieldNames().forEachRemaining(k->{if(!keys.contains(k)) throw invalid();});}
    private static LessonFailure invalid() {return new LessonFailure(HttpStatus.BAD_REQUEST,"INVALID_MESSAGE");}
}
