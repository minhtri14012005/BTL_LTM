package vn.edu.quiz.game.engine;

import java.util.Objects;
import vn.edu.quiz.game.dto.GameplayRulesSnapshot;
import vn.edu.quiz.game.enums.AnswerStatus;
import vn.edu.quiz.game.enums.PlayerState;

/** Pure deterministic calculator for one player/question using the supplied immutable game rules. */
public final class ScoreEngine {
    private final GameplayRulesSnapshot rules;

    public ScoreEngine(GameplayRulesSnapshot rules) {
        this.rules = Objects.requireNonNull(rules, "rules");
        // Version 1 is frozen. Do not silently calculate with an unsupported/tampered rule table.
        if (!rules.equals(GameplayRulesSnapshot.forGame(rules.questionCount(), rules.questionDurationMs(), rules.decisionDurationMs()))) {
            throw new IllegalArgumentException("Unsupported version 1 gameplay rules");
        }
    }

    public PlayerScore initialPlayer() {
        return new PlayerScore(PlayerState.PLAYING, rules.initialScore(), 0, 0, 0, false, false);
    }

    public ScoringResult calculate(ScoringInput input) {
        Objects.requireNonNull(input, "input");
        PlayerScore before = input.player();
        if (before.state() == PlayerState.ELIMINATED) {
            throw new IllegalStateException("Eliminated player cannot be scored");
        }
        if (input.answeredTimeMs() != null && input.answeredTimeMs() >= rules.questionDurationMs()) {
            throw new IllegalArgumentException("Answered time must be inside question duration");
        }
        GameplayRulesSnapshot.Score table = table(input);
        int baseDelta = switch (input.outcome()) {
            case CORRECT -> table.correct();
            case WRONG -> table.wrong();
            case NO_ANSWER -> table.noAnswer();
            case ACCEPTED_UNSCORED -> throw new IllegalArgumentException("Unscored answer is not an outcome");
        };

        boolean momentumConsumed = input.outcome() == AnswerStatus.CORRECT && before.momentum();
        boolean recoveryConsumed = input.outcome() != AnswerStatus.CORRECT && baseDelta < 0 && before.recovery();
        int delta = baseDelta;
        if (momentumConsumed) delta = Math.addExact(delta, rules.streaks().momentumBonus());
        if (recoveryConsumed) delta = Math.min(Math.addExact(baseDelta, rules.streaks().recoveryReduction()), rules.streaks().recoveryPenaltyCap());
        int score = Math.addExact(before.score(), delta);
        long answerTime = input.outcome() == AnswerStatus.NO_ANSWER ? rules.questionDurationMs() : input.answeredTimeMs();
        long totalTime = Math.addExact(before.totalAnswerTimeMs(), answerTime);
        boolean momentum = before.momentum() && !momentumConsumed;
        boolean recovery = before.recovery() && !recoveryConsumed;
        boolean eliminated = score < 0;
        int win = before.winStreak(), lose = before.loseStreak();
        boolean momentumGranted = false, recoveryGranted = false;

        // Existing effects have already been consumed. Elimination stops all streak updates/grants.
        if (!eliminated) {
            switch (input.outcome()) {
                case CORRECT -> {
                    lose = 0;
                    if (++win == rules.streaks().threshold()) {
                        win = 0;
                        momentumGranted = !momentum;
                        momentum = true;
                    }
                }
                case WRONG -> {
                    win = 0;
                    if (++lose == rules.streaks().threshold()) {
                        lose = 0;
                        recoveryGranted = !recovery;
                        recovery = true;
                    }
                }
                case NO_ANSWER -> { win = 0; lose = 0; }
                case ACCEPTED_UNSCORED -> throw new IllegalArgumentException("Unscored answer is not an outcome");
            }
        }
        PlayerScore after = new PlayerScore(eliminated ? PlayerState.ELIMINATED : PlayerState.PLAYING,
                score, totalTime, win, lose, momentum, recovery);
        return new ScoringResult(input.outcome(), baseDelta, delta, answerTime, after,
                momentumConsumed, recoveryConsumed, momentumGranted, recoveryGranted, eliminated);
    }

    private GameplayRulesSnapshot.Score table(ScoringInput input) {
        if (input.spin() == null) return input.starSelected() ? rules.starOnly() : rules.normal();
        var spin = rules.spins().get(input.spin());
        if (input.starSelected() && !spin.starAllowed()) {
            throw new IllegalArgumentException("Hope Star is not allowed with this Spin");
        }
        return input.starSelected() ? spin.withStar() : spin.normal();
    }
}
