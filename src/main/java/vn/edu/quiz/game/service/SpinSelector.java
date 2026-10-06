package vn.edu.quiz.game.service;

import java.security.SecureRandom;
import java.util.List;
import java.util.function.IntUnaryOperator;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import vn.edu.quiz.game.dto.GameplayRulesSnapshot;
import vn.edu.quiz.game.enums.SpinEffect;

/** Draw once from the player's remaining weighted pool, outside the pure scoring engine. */
@Component @Profile("mysql")
public class SpinSelector {
    private final IntUnaryOperator draw;
    public SpinSelector() { this(new SecureRandom()::nextInt); }
    public SpinSelector(IntUnaryOperator draw) { this.draw=draw; }

    public SpinEffect select(GameplayRulesSnapshot rules,List<SpinEffect> pool) {
        if(pool.isEmpty()) throw GameFailure.conflict("SPIN_NOT_AVAILABLE");
        int total=pool.stream().mapToInt(effect -> rules.spins().get(effect).weight()).sum();
        int selected=draw.applyAsInt(total);
        if(selected<0 || selected>=total) throw new IllegalStateException("Invalid server draw");
        for(var effect:pool) {
            selected-=rules.spins().get(effect).weight();
            if(selected<0) return effect;
        }
        throw new IllegalStateException("Invalid remaining pool");
    }
}
