package vn.edu.multigame.game.dto;
import java.util.List;
import java.util.HashSet;
import com.fasterxml.jackson.annotation.JsonInclude;
/** OPTION keeps selected_option; TEXT or ARRANGEMENT has exactly one typed field. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TypedAnswer(String text,List<String> itemIds) {
    public TypedAnswer {
        if((text==null)==(itemIds==null)) throw new IllegalArgumentException("Exactly one answer shape is required");
        if(text!=null && (text.isBlank() || text.length()>300)) throw new IllegalArgumentException("Invalid text answer");
        if(itemIds!=null) {
            itemIds=List.copyOf(itemIds);
            if(itemIds.size()<2 || itemIds.size()>100 || new HashSet<>(itemIds).size()!=itemIds.size()
                    || itemIds.stream().anyMatch(id->id.isBlank() || id.length()>64)) throw new IllegalArgumentException("Invalid item IDs");
        }
    }
}
