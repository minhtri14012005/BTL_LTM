package vn.edu.multigame.realtime.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class GameCommandParserTest {
    final GameCommandParser parser=new GameCommandParser(new WaitingRoomCommandParser(new ObjectMapper()));
    String message(String type,String index,String payload) { return "{\"v\":1,\"kind\":\"COMMAND\",\"requestId\":\"15a1b0be-2ccd-4771-8acf-a717d7f41877\",\"type\":\""+type+"\",\"target\":{\"kind\":\"GAME\",\"id\":12},\"questionIndex\":"+index+",\"payload\":"+payload+"}"; }
    @Test void recognizesAllThreeActionsOnExistingEnvelope() {
        assertThat(parser.read(message("ANSWER","1","{\"option\":\"A\"}")).questionIndex()).isEqualTo(1);
        assertThat(parser.read(message("USE_SPIN","50","{}")).type()).isEqualTo("USE_SPIN");
        assertThat(parser.read(message("USE_STAR","1","{}")).target().id()).isEqualTo(12);
    }
    @Test void arrangementUsesOrderedStableIdsAndRejectsMalformedLists() {
        assertThat(parser.read(message("ANSWER","2","{\"itemIds\":[\"b\",\"a\"]}")).payload().path("itemIds").get(0).asText()).isEqualTo("b");
        for(String payload:new String[]{"{\"itemIds\":[]}","{\"itemIds\":[\"a\"]}","{\"itemIds\":[\"a\",\"a\"]}","{\"itemIds\":[1,2]}","{\"itemIds\":[\"a b\",\"c\"]}","{\"itemIds\":[\"a\",\"b\"],\"text\":\"ab\"}"})
            assertThatThrownBy(() -> parser.read(message("ANSWER","2",payload))).hasMessage("INVALID_MESSAGE");
    }
    @Test void indexMustBeIntegralOneThroughFifty() {
        for(String invalid:new String[]{"null","0","51","1.0","2147483648","\"1\""})
            assertThatThrownBy(() -> parser.read(message("ANSWER",invalid,"{\"option\":\"A\"}"))).hasMessage("INVALID_MESSAGE");
    }
    @Test void reconnectIsStrictReadOnGameTargetWithUnknownCurrentIndex() {
        var text=message("RECONNECT","null","{}");
        assertThat(parser.isReconnect(text)).isTrue();
        assertThat(parser.reconnect(text).target().id()).isEqualTo(12);
        for(String invalid:new String[]{message("RECONNECT","1","{}"),message("RECONNECT","null","{\"userId\":1}"),
                message("RECONNECT","null","null"),text.replace("\"GAME\"","\"ROOM\""),text+"{}"})
            assertThatThrownBy(() -> parser.reconnect(invalid)).hasMessage("INVALID_MESSAGE");
    }
    @Test void cancelUsesNullIndexEmptyPayloadAndExistingEnvelope() {
        assertThat(parser.read(message("CANCEL_GAME","null","{}")).questionIndex()).isNull();
        for(String invalid:new String[]{message("CANCEL_GAME","1","{}"),message("CANCEL_GAME","null","{\"userId\":12}"),
                message("CANCEL_GAME","null","null"),message("CANCEL_GAME","null","{}").replace("\"GAME\"","\"ROOM\"")})
            assertThatThrownBy(() -> parser.read(invalid)).hasMessage("INVALID_MESSAGE");
    }
    @Test void rejectsClientIdentityTimeUnknownFieldsDuplicatesAndBadOption() {
        for(String payload:new String[]{"{}","null","{\"option\":\"E\"}","{\"option\":\"A\",\"userId\":3}","{\"option\":\"A\",\"timestamp\":1}","{\"option\":\"A\",\"option\":\"B\"}"})
            assertThatThrownBy(() -> parser.read(message("ANSWER","1",payload))).hasMessage("INVALID_MESSAGE");
        assertThatThrownBy(() -> parser.read(message("USE_SPIN","1","{\"effect\":\"BONUS\"}"))).hasMessage("INVALID_MESSAGE");
        assertThatThrownBy(() -> parser.read(message("USE_STAR","1","{}")+"{}")).hasMessage("INVALID_MESSAGE");
        assertThatThrownBy(() -> parser.read(message("USE_STAR","1","{}").replace("\"GAME\"","\"ROOM\""))).hasMessage("INVALID_MESSAGE");
    }
}
