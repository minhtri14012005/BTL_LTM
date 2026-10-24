package vn.edu.multigame.realtime.timer;

import vn.edu.multigame.realtime.timer.Phase;

/** Both session generation and phase token prevent stale callbacks from affecting reused targets. */
public record PhaseTimer(long sessionId, long sessionGeneration, int questionIndex,
        Phase phase, long token, long deadlineMs) {}
