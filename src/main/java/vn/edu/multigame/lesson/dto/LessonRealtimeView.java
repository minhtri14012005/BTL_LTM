package vn.edu.multigame.lesson.dto;

import vn.edu.multigame.questionbank.dto.response.StudentQuestion;

public record LessonRealtimeView(LessonView lesson,Window window,long serverTimeMs,com.fasterxml.jackson.databind.JsonNode wheel,java.util.Map<String,java.util.List<java.util.Map<String,Object>>> quiz,java.util.Map<String,java.util.List<java.util.Map<String,Object>>> fillBlank,java.util.Map<String,vn.edu.multigame.lesson.repository.AssignmentStore.View> assignment,java.util.List<Participant> participants,java.util.Map<String,Integer> questionCounts) {
    public LessonRealtimeView(LessonView lesson,Window window,long serverTimeMs){this(lesson,window,serverTimeMs,null,java.util.Map.of(),java.util.Map.of(),java.util.Map.of(),java.util.List.of(),java.util.Map.of());}
    public record Participant(long userId,String hoTen,String maSinhVien,boolean attending,boolean eligible,java.util.List<Integer> games,java.util.Map<Integer,java.math.BigDecimal> scores,java.util.Map<Integer,Integer> correct) {}
    public record Window(long gameId,int position,long token,int questionIndex,String status,long openedAtMs,long deadlineAtMs,
            boolean submitted,StudentQuestion question,int questionCount) {}
}
