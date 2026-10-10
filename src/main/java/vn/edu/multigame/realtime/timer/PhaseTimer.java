package vn.edu.multigame.realtime.timer;

import vn.edu.multigame.game.enums.Phase;

/** Both session generation and phase token prevent stale callbacks from affecting reused targets. */
public record PhaseTimer(long gameSessionId, long sessionGeneration, int questionIndex,
        Phase phase, long token, long deadlineMs) {}
