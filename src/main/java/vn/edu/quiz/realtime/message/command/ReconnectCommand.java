package vn.edu.quiz.realtime.message.command;

import vn.edu.quiz.realtime.message.common.GameTarget;

public record ReconnectCommand(String requestId,GameTarget target) {}
