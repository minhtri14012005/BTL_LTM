package vn.edu.multigame.realtime.websocket;

import java.util.Set;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import vn.edu.multigame.realtime.message.command.GameCommand;
import vn.edu.multigame.realtime.message.common.GameTarget;
import vn.edu.multigame.room.service.RoomFailure;

@Component
public class GameCommandParser {
    private final WaitingRoomCommandParser envelope;
    public GameCommandParser(WaitingRoomCommandParser envelope) { this.envelope=envelope; }
    public boolean isReconnect(String text) { return envelope.isReconnect(text); }
    public vn.edu.multigame.realtime.message.command.ReconnectCommand reconnect(String text) {
        var node=envelope.readEnvelope(text);
        if(!node.path("type").asText().equals("RECONNECT") || !node.path("target").path("kind").asText().equals("GAME")
                || !node.path("questionIndex").isNull() || !node.path("payload").isObject() || !node.path("payload").isEmpty()) throw invalid();
        return new vn.edu.multigame.realtime.message.command.ReconnectCommand(node.path("requestId").asText(),GameTarget.of(node.path("target").path("id").asLong()));
    }
    public GameCommand read(String text) {
        var node=envelope.readEnvelope(text); String type=node.path("type").asText();
        if(!Set.of("ANSWER","USE_SPIN","USE_STAR","CANCEL_GAME","CONTINUE").contains(type)) throw new WaitingRoomCommandParser.Unsupported();
        if(!node.path("target").path("kind").asText().equals("GAME")) throw invalid();
        var index=node.get("questionIndex");
        if(type.equals("CANCEL_GAME") ? !index.isNull() : (!index.isIntegralNumber() || !index.canConvertToInt() || index.asInt()<1 || index.asInt()>50)) throw invalid();
        JsonNode payload=node.get("payload");
        if(!payload.isObject()) throw invalid();
        if(type.equals("ANSWER")) {
            if(payload.size()!=1) throw invalid();
            if(payload.has("option")) {if(!payload.path("option").isTextual() || !Set.of("A","B","C","D").contains(payload.path("option").asText())) throw invalid();}
            else if(payload.has("text")) {if(!payload.path("text").isTextual() || payload.path("text").asText().isBlank() || payload.path("text").asText().length()>300) throw invalid();}
            else if(payload.has("itemIds")) {
                var ids=payload.path("itemIds");if(!ids.isArray() || ids.size()<2 || ids.size()>100) throw invalid();
                var seen=new java.util.HashSet<String>();
                for(var id:ids) if(!id.isTextual() || !id.asText().matches("[A-Za-z0-9_-]{1,64}") || !seen.add(id.asText())) throw invalid();
            }
            else throw invalid();
        } else if(!payload.isEmpty()) throw invalid();
        return new GameCommand(1,"COMMAND",node.path("requestId").asText(),type,GameTarget.of(node.path("target").path("id").asLong()),index.isNull()?null:index.asInt(),payload);
    }
    private RoomFailure invalid() { return new RoomFailure(org.springframework.http.HttpStatus.BAD_REQUEST,"INVALID_MESSAGE"); }
}
