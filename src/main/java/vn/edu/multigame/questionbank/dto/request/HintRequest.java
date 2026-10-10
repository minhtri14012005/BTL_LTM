package vn.edu.multigame.questionbank.dto.request;
import jakarta.validation.constraints.*;
public record HintRequest(@NotNull @PositiveOrZero
        @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using=HintRequest.OffsetDeserializer.class) Long offsetMs,
        @NotBlank @Size(max=2000) String text) {
    /** Do not silently truncate fractional milliseconds or coerce a string into a Server timeline. */
    public static final class OffsetDeserializer extends com.fasterxml.jackson.databind.JsonDeserializer<Long> {
        @Override public Long deserialize(com.fasterxml.jackson.core.JsonParser parser,
                com.fasterxml.jackson.databind.DeserializationContext context) throws java.io.IOException {
            if(parser.currentToken()!=com.fasterxml.jackson.core.JsonToken.VALUE_NUMBER_INT)
                throw com.fasterxml.jackson.databind.JsonMappingException.from(parser,"offsetMs must be an int64");
            return parser.getLongValue();
        }
    }
}
