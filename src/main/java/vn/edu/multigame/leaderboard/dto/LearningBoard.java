package vn.edu.multigame.leaderboard.dto;
import java.math.BigDecimal;
import java.util.List;
public final class LearningBoard {
 private LearningBoard(){}
 public record Student(long userId,String maSinhVien,String hoTen,BigDecimal score){}
 public static class RankNumber extends com.fasterxml.jackson.databind.JsonDeserializer<Integer> {
  @Override public Integer deserialize(com.fasterxml.jackson.core.JsonParser parser,com.fasterxml.jackson.databind.DeserializationContext context)throws java.io.IOException {
   if(parser.currentToken()!=com.fasterxml.jackson.core.JsonToken.VALUE_NUMBER_INT)throw com.fasterxml.jackson.databind.JsonMappingException.from(parser,"Rank must be an integer");
   return parser.getIntValue();
  }
 }
 public record Range(String name,@com.fasterxml.jackson.databind.annotation.JsonDeserialize(using=RankNumber.class) int from,@com.fasterxml.jackson.databind.annotation.JsonDeserialize(using=RankNumber.class) Integer to){}
 public record Config(long revision,List<Range> ranges){}
 public record Import(long revision,String code){}
 public record ClassRow(long userId,String maSinhVien,String hoTen,BigDecimal score,int position,String rank){}
 public record GlobalRow(long userId,String maSinhVien,String hoTen,BigDecimal score,int position){}
 public record ClassView(long classId,List<ClassRow> students){}
 public record GlobalView(long subjectId,long academicYearId,List<GlobalRow> students){}
}
