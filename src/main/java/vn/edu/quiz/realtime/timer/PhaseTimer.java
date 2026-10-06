package vn.edu.quiz.realtime.timer;

import vn.edu.quiz.game.enums.Phase;

/** Both session generation and phase token prevent stale callbacks from affecting reused targets. */
public record PhaseTimer(long gameSessionId, long sessionGeneration, int questionIndex,
        Phase phase, long token, long deadlineMs) {}
