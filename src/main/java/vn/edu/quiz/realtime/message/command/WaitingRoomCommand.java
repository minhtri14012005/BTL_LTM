package vn.edu.quiz.realtime.message.command;
import com.fasterxml.jackson.databind.JsonNode;
import vn.edu.quiz.realtime.message.common.RoomTarget;
public record WaitingRoomCommand(int v,String kind,String requestId,String type,RoomTarget target,JsonNode questionIndex,JsonNode payload) {}
