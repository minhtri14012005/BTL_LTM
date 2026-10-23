package vn.edu.multigame.leaderboard.dto;
import java.math.BigDecimal;
import java.util.List;
public final class LearningBoard {
 private LearningBoard(){}
 public record Student(long userId,String maSinhVien,String hoTen,BigDecimal score){}
 public record Range(String name,int from,int to){}
 public record Config(long revision,List<Range> ranges){}
 public record Import(long revision,String code){}
 public record ClassRow(long userId,String maSinhVien,String hoTen,BigDecimal score,int position,String rank){}
 public record GlobalRow(long userId,String maSinhVien,String hoTen,BigDecimal score,int position){}
 public record ClassView(long classId,List<ClassRow> students){}
 public record GlobalView(long subjectId,long academicYearId,List<GlobalRow> students){}
}
