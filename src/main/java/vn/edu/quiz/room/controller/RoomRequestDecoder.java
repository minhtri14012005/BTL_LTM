package vn.edu.quiz.room.controller;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import jakarta.validation.Validator;
import org.springframework.stereotype.Component;
import vn.edu.quiz.room.service.RoomFailure;

/** Strict only for new Room APIs, leaving the frozen auth/Quiz parser behavior untouched. */
@Component
public class RoomRequestDecoder {
    private final ObjectMapper json;
    private final Validator validator;
    public RoomRequestDecoder(ObjectMapper json,Validator validator) {
        this.json=json.copy().disable(MapperFeature.ALLOW_COERCION_OF_SCALARS).disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
        this.validator=validator;
    }
    public <T> T read(String input,Class<T> type) {
        try {
            T result=json.readValue(input,type);
            if(result==null || !validator.validate(result).isEmpty()) throw RoomFailure.invalid();
            return result;
        } catch(java.io.IOException e) { throw RoomFailure.invalid(); }
    }
}
