package vn.edu.multigame.realtime.websocket;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import java.util.*;
import org.springframework.stereotype.Component;
import vn.edu.multigame.realtime.message.command.WaitingRoomCommand;
import vn.edu.multigame.realtime.message.common.RoomTarget;
import vn.edu.multigame.room.service.RoomFailure;

@Component
public class WaitingRoomCommandParser {
    public static class Unsupported extends RuntimeException {}
    private final ObjectMapper json;
    public WaitingRoomCommandParser(ObjectMapper json) {
        this.json=json.copy().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    }
    public WaitingRoomCommand read(String text) {
        JsonNode node=tree(text);
        if(node==null || !node.isObject()) throw invalid();
        String type=node.path("type").asText();
        if(!Set.of("JOIN_ROOM","LEAVE_ROOM","REMOVE_MEMBER","SUBSCRIBE_ROOM","START_GAME").contains(type)) throw new Unsupported();
        fields(node,Set.of("v","kind","requestId","type","target","questionIndex","payload"));
        if(!node.path("v").isIntegralNumber() || !node.path("v").canConvertToInt() || node.path("v").asInt()!=1 || !node.path("kind").asText().equals("COMMAND")) throw invalid();
        String requestId=node.path("requestId").asText();
        if(!requestId.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) throw invalid();
        JsonNode target=node.get("target"); fields(target,Set.of("kind","id"));
        if(!target.path("kind").asText().equals("ROOM") || !positiveLong(target.get("id"))) throw invalid();
        if(node.get("questionIndex")==null || !node.get("questionIndex").isNull()) throw invalid();
        JsonNode payload=node.get("payload");
        switch(type) {
            case "START_GAME" -> {
                fields(payload,Set.of("revision","questionCount"));
                if(!payload.path("revision").isIntegralNumber() || !payload.path("revision").canConvertToLong() || payload.path("revision").asLong()<0
                        || !payload.path("questionCount").isIntegralNumber() || !payload.path("questionCount").canConvertToInt()
                        || payload.path("questionCount").asInt()<1 || payload.path("questionCount").asInt()>50) throw invalid();
            }
            case "JOIN_ROOM" -> {
                fields(payload,Set.of("roomCode","participation"));
                if(!payload.path("roomCode").isTextual() || !payload.path("roomCode").asText().matches("[A-Z2-9]{12}")
                        || !Set.of("PLAYER","SPECTATOR").contains(payload.path("participation").asText())) throw invalid();
            }
            case "REMOVE_MEMBER" -> { fields(payload,Set.of("userId")); if(!positiveLong(payload.get("userId"))) throw invalid(); }
            default -> fields(payload,Set.of());
        }
        return new WaitingRoomCommand(1,"COMMAND",requestId,type,RoomTarget.of(target.get("id").asLong()),node.get("questionIndex"),payload);
    }
    public boolean isGameplay(String text) {
        var node=tree(text);
        return Set.of("ANSWER","USE_SPIN","USE_STAR","RECONNECT","CANCEL_GAME","CONTINUE").contains(node.path("type").asText());
    }
    public boolean isReconnect(String text) { return tree(text).path("type").asText().equals("RECONNECT"); }
    /** The same v1 envelope for Waiting and Game; payload validation stays with its owning adapter. */
    public JsonNode readEnvelope(String text) {
        var node=tree(text); fields(node,Set.of("v","kind","requestId","type","target","questionIndex","payload"));
        if(!node.path("v").isIntegralNumber() || !node.path("v").canConvertToInt() || node.path("v").asInt()!=1
                || !node.path("kind").asText().equals("COMMAND") || !node.path("type").isTextual()
                || !node.path("requestId").asText().matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) throw invalid();
        fields(node.get("target"),Set.of("kind","id"));
        if(!positiveLong(node.path("target").get("id"))) throw invalid();
        return node;
    }
    private JsonNode tree(String text) {
        try { var node=json.readTree(text); if(node==null || !node.isObject()) throw invalid(); return node; }
        catch(java.io.IOException malformed) { throw invalid(); }
    }
    private boolean positiveLong(JsonNode node) { return node!=null && node.isIntegralNumber() && node.canConvertToLong() && node.asLong()>0; }
    private void fields(JsonNode node,Set<String> expected) {
        if(node==null || !node.isObject() || node.size()!=expected.size()) throw invalid();
        node.fieldNames().forEachRemaining(key->{ if(!expected.contains(key)) throw invalid(); });
    }
    private RoomFailure invalid() { return new RoomFailure(org.springframework.http.HttpStatus.BAD_REQUEST,"INVALID_MESSAGE"); }
}
