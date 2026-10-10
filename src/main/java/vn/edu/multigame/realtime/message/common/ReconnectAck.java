package vn.edu.multigame.realtime.message.common;

import vn.edu.multigame.game.dto.response.GameSnapshot;

/** Fresh private read, never an action receipt or a second wire envelope. */
public record ReconnectAck(int v,String kind,String requestId,String type,GameTarget target,Integer questionIndex,
        String status,long revision,long serverTimeMs,long connectionGeneration,GameSnapshot payload) {}
