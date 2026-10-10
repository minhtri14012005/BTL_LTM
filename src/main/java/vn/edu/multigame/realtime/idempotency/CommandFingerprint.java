package vn.edu.multigame.realtime.idempotency;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class CommandFingerprint {
    private final ObjectMapper json;
    public CommandFingerprint(ObjectMapper json) { this.json=json; }
    public String of(String type, String targetKind, long targetId, Object index, Object payload) {
        ObjectNode input=json.createObjectNode(); input.put("type",type);
        input.set("target",json.valueToTree(Map.of("kind",targetKind,"id",targetId)));
        input.set("questionIndex",json.valueToTree(index)); input.set("payload",json.valueToTree(payload));
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(json.writeValueAsString(canonical(input)).getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException("Cannot fingerprint command",e); }
    }
    private JsonNode canonical(JsonNode node) {
        if(node.isObject()) {
            ObjectNode result=json.createObjectNode(); var keys=new TreeSet<String>(); node.fieldNames().forEachRemaining(keys::add);
            for(String key:keys) result.set(key,canonical(node.get(key))); return result;
        }
        if(node.isArray()) { ArrayNode result=json.createArrayNode(); node.forEach(n->result.add(canonical(n))); return result; }
        return node;
    }
}
