package vn.edu.multigame.lesson.service;
import java.math.*;
public final class QuizScoring {
 private QuizScoring(){}
 public static BigDecimal score(int correct,int total,BigDecimal budget){
  if(total<1 || correct<0 || correct>total || budget==null || budget.signum()<0)throw new IllegalArgumentException("Invalid quiz score");
  return budget.multiply(BigDecimal.valueOf(correct)).divide(BigDecimal.valueOf(total),2,RoundingMode.HALF_UP).min(budget).setScale(2,RoundingMode.HALF_UP);
 }
}
