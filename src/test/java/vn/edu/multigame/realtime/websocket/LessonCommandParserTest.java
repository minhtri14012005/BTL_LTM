package vn.edu.multigame.realtime.websocket;

import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import vn.edu.multigame.lesson.service.LessonFailure;
import static org.assertj.core.api.Assertions.*;

class LessonCommandParserTest {
    private final ObjectMapper json=new ObjectMapper();
    private final LessonCommandParser parser=new LessonCommandParser(json);
    private String message(String type,Object payload) throws Exception {
        return json.writeValueAsString(Map.of("v",1,"kind","COMMAND","requestId","00000000-0000-0000-0000-000000000001","type",type,"target",Map.of("kind","LESSON","id",3),"payload",payload));
    }
    @Test void recognizesLessonWithoutHijackingLegacyTargets() throws Exception {
        String message=message("SUBSCRIBE_LESSON",Map.of());assertThat(parser.handles(message)).isTrue();assertThat(parser.read(message).target().id()).isEqualTo(3);
        assertThat(parser.handles("{\"type\":\"ANSWER\",\"target\":{\"kind\":\"GAME\",\"id\":3}}")).isFalse();
    }
    @Test void supportsReleasingLessonSubscriptionsWithoutClientAuthority() throws Exception {
        assertThat(parser.read(message("UNSUBSCRIBE_LESSON",Map.of())).type()).isEqualTo("UNSUBSCRIBE_LESSON");
        assertThatThrownBy(()->parser.read(message("UNSUBSCRIBE_LESSON",Map.of("studentId",4)))).isInstanceOf(LessonFailure.class);
    }
    @Test void allowsOnlyClientActionsWithoutClientAuthority() throws Exception {
        for(String property:new String[]{"score","rank","correctAnswer","deadlineAtMs","receivedAtMs","wheelResult","studentId"})
            assertThatThrownBy(()->parser.read(message("SUBMIT_LESSON_ACTION",Map.of("token",1,"value","A",property,1)))).isInstanceOf(LessonFailure.class);
        var command=parser.read(message("OPEN_LESSON_WINDOW",Map.of("position",2,"questionIndex",1,"durationMs",2000)));
        assertThat(command.payload().path("durationMs").asLong()).isEqualTo(2000);
    }
    @ParameterizedTest @ValueSource(strings={"0","-1","1.5","\"1\"","9223372036854775808","null"})
    void rejectsInvalidWindowTokens(String token) throws Exception {
        String message=message("SUBMIT_LESSON_ACTION",Map.of("token",1,"value","A")).replace("\"token\":1","\"token\":"+token);
        assertThatThrownBy(()->parser.read(message)).isInstanceOf(LessonFailure.class);
    }
    @Test void rejectsAmbiguousAndUnsupportedCommands() throws Exception {
        String base=message("ATTEND_LESSON",Map.of());
        for(String invalid:new String[]{base+" {}",base.replace("\"v\":1","\"v\":1,\"v\":1"),base.replace("ATTEND_LESSON","SET_SCORE"),base.replace("00000000-0000-0000-0000-000000000001","not-an-id")})
            assertThatThrownBy(()->parser.read(invalid)).isInstanceOf(LessonFailure.class);
    }
}
