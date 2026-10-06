package vn.edu.quiz.realtime.message.command;

import com.fasterxml.jackson.databind.JsonNode;
import vn.edu.quiz.realtime.message.common.GameTarget;

public record GameCommand(int v,String kind,String requestId,String type,GameTarget target,
        Integer questionIndex,JsonNode payload) {}
