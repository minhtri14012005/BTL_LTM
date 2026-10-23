package vn.edu.multigame.realtime.message.command;

import com.fasterxml.jackson.databind.JsonNode;

public record LessonCommand(int v,String kind,String requestId,String type,Target target,JsonNode payload) {
    public record Target(String kind,long id) {public static Target lesson(long id) {return new Target("LESSON",id);}}
}
