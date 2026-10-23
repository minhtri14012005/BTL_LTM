package vn.edu.multigame.achievements.dto;
import java.util.List;
import java.math.BigDecimal;
public record LearningProfile(long userId,String hoTen,String maSinhVien,String status,Stats stats,List<Award> achievements) {
 public record Stats(long attendedLessons,long completedLessons,long completedGames,BigDecimal totalPoints,long quizQuestions,long quizCorrect){}
 public record Award(long id,Long lessonGameId,Long gameSessionId,String type,String label,long earnedAtMs,Long subjectId,Long academicYearId){}
 public record Game(long gameId,int position,String mode,String status,boolean participating,BigDecimal score){}
 public record Lesson(long lessonId,long classId,String className,long subjectId,String subjectName,long academicYearId,String academicYearName,int number,String title,String status,BigDecimal score,List<Game> games){}
 public record History(List<Lesson> items,int page,int size,long totalElements){}
}
