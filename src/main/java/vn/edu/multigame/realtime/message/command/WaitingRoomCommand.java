package vn.edu.multigame.realtime.message.command;
import com.fasterxml.jackson.databind.JsonNode;
import vn.edu.multigame.realtime.message.common.RoomTarget;
public record WaitingRoomCommand(int v,String kind,String requestId,String type,RoomTarget target,JsonNode questionIndex,JsonNode payload) {}
