package vn.edu.multigame.leaderboard.service;
import java.util.*;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import vn.edu.multigame.leaderboard.dto.LearningBoard.*;
import static org.assertj.core.api.Assertions.*;
class LearningRanksTest {
 private Student student(int id,int score){return new Student(id,"SV"+id,"Student "+id,BigDecimal.valueOf(score));}
 @Test void tiesAtTenAndElevenGetTheHigherBandByFirstPosition(){var students=new ArrayList<Student>();for(int i=1;i<=12;i++)students.add(student(i,i<10?100-i:80));var rows=LearningRanks.classroom(students,List.of(new Range("Top",1,10),new Range("Next",11,30)));assertThat(rows.subList(9,12)).allSatisfy(r->{assertThat(r.position()).isEqualTo(10);assertThat(r.rank()).isEqualTo("Top");});}
 @Test void classBandsDoNotCountDistinctScoreLevels(){var students=new ArrayList<Student>();for(int i=1;i<=10;i++)students.add(student(i,100));students.add(student(11,90));var rows=LearningRanks.classroom(students,List.of(new Range("Top",1,10),new Range("Next",11,20)));assertThat(rows.get(10).position()).isEqualTo(11);assertThat(rows.get(10).rank()).isEqualTo("Next");}
 @Test void globalUsesDenseRanking(){assertThat(LearningRanks.global(List.of(student(1,950),student(2,950),student(3,950),student(4,920)))).extracting(GlobalRow::position).containsExactly(1,1,1,2);}
 @Test void overlappingOrInvalidRangesAreRejected(){assertThat(LearningRanks.valid(List.of(new Range("A",1,10),new Range("B",10,20)))).isFalse();assertThat(LearningRanks.valid(List.of(new Range("A",0,10)))).isFalse();assertThat(LearningRanks.valid(List.of(new Range(" ",1,10)))).isFalse();assertThat(LearningRanks.valid(List.of(new Range("B",11,20),new Range("A",1,10)))).isTrue();}
}
