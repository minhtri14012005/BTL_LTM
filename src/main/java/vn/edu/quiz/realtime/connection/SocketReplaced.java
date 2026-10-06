package vn.edu.quiz.realtime.connection;

/** An old connection may never reclaim ownership or start another operation. */
public final class SocketReplaced extends RuntimeException {
    public SocketReplaced() { super("SESSION_REPLACED"); }
}
