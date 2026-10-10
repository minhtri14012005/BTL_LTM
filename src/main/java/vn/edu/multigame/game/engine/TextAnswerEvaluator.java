package vn.edu.multigame.game.engine;
import java.util.List;
import vn.edu.multigame.common.util.TextNormalizer;
/** Exact canonical text/alias comparison, retaining accents and rejecting fuzzy matches. */
public final class TextAnswerEvaluator {
    public String normalize(String input) { return TextNormalizer.normalize(input); }
    public boolean matches(String submitted,List<String> acceptedAnswers) {
        String answer=normalize(submitted);
        return !answer.isEmpty() && acceptedAnswers.stream().map(this::normalize).anyMatch(answer::equals);
    }
}
