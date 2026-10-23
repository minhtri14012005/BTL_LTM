package vn.edu.multigame.lesson.dto;

import java.math.BigDecimal;
import java.util.List;
import vn.edu.multigame.lesson.domain.LessonMode;

public record LessonView(long id,long courseClassId,int number,String title,boolean visible,String status,long revision,
        List<Game> games,boolean attending,BigDecimal score) {
    public record Game(long id,int position,LessonMode mode,String title,BigDecimal budget,String status,
            boolean participating,BigDecimal score) {
        @com.fasterxml.jackson.annotation.JsonProperty("modeLabel")
        public String modeLabel() {
            return switch(mode) {
                case FILL_BLANK -> "Điền từ vào chỗ trống";
                case QUIZ -> "Quiz";
                case ASSIGNMENT -> "Bài tập";
                case LUCKY_WHEEL -> "Vòng quay may mắn";
            };
        }
    }
    public record Progress(int required,int created,int completed) {}
}
