package vn.edu.multigame.realtime.message.command;

import vn.edu.multigame.realtime.message.common.GameTarget;

public record ReconnectCommand(String requestId,GameTarget target) {}
