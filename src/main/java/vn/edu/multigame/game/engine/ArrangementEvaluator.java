package vn.edu.multigame.game.engine;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import vn.edu.multigame.common.util.ArrangementIds;
import vn.edu.multigame.game.enums.GameMode;
/** Pure input evaluator. No random, clock, database, entity or transport dependency. */
public final class ArrangementEvaluator {
    public record Item(String id,String text) {}
    public boolean matches(GameMode mode,List<Item> items,List<String> correctOrder,List<String> aliases,List<String> submitted) {
        if(!ArrangementIds.permutation(items.stream().map(Item::id).toList(),submitted)) return false;
        if(mode==GameMode.ORDERING) return correctOrder.equals(submitted);
        if(mode!=GameMode.VIETNAMESE_PUZZLE) throw new IllegalArgumentException("Not an arrangement mode");
        // Concatenate raw piece text: explicit spaces belong to pieces. Normalization happens afterwards.
        Map<String,String> text=items.stream().collect(Collectors.toMap(Item::id,Item::text));
        String joined=submitted.stream().map(text::get).collect(Collectors.joining());
        List<String> accepted=aliases==null?List.of(correctOrder.stream().map(text::get).collect(Collectors.joining())):aliases;
        return new TextAnswerEvaluator().matches(joined,accepted);
    }
}
