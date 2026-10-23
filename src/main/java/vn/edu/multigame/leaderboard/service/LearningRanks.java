package vn.edu.multigame.leaderboard.service;
import java.util.*;
import java.math.BigDecimal;
import vn.edu.multigame.leaderboard.dto.LearningBoard.*;
public final class LearningRanks {
 private LearningRanks(){}
 public static List<ClassRow> classroom(List<Student> sorted,List<Range> ranges){var result=new ArrayList<ClassRow>();int first=0;BigDecimal previous=null;for(int i=0;i<sorted.size();i++){var s=sorted.get(i);if(previous==null || previous.compareTo(s.score())!=0)first=i+1;String label=null;for(var range:ranges)if(first>=range.from() && first<=range.to()){label=range.name();break;}result.add(new ClassRow(s.userId(),s.maSinhVien(),s.hoTen(),s.score(),first,label));previous=s.score();}return List.copyOf(result);}
 public static List<GlobalRow> global(List<Student> sorted){var result=new ArrayList<GlobalRow>();int rank=0;BigDecimal previous=null;for(var s:sorted){if(previous==null || previous.compareTo(s.score())!=0)rank++;result.add(new GlobalRow(s.userId(),s.maSinhVien(),s.hoTen(),s.score(),rank));previous=s.score();}return List.copyOf(result);}
 public static boolean valid(List<Range> ranges){if(ranges==null || ranges.size()>100 || ranges.stream().anyMatch(Objects::isNull))return false;int end=0;for(var range:ranges.stream().sorted(Comparator.comparingInt(Range::from)).toList()){if(range.name()==null || range.name().isBlank() || range.name().length()>100 || range.from()<=end || range.to()<range.from())return false;end=range.to();}return true;}
}
