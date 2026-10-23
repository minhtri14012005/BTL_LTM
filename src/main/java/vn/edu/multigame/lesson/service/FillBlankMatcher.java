package vn.edu.multigame.lesson.service;
import java.util.List;
import vn.edu.multigame.questionbank.enums.TextMatchPolicy;
/** Only the author-selected case policy applies; whitespace, accents and source answers are preserved. */
public final class FillBlankMatcher {
 private FillBlankMatcher(){}
 public static boolean matches(String value,List<String> accepted,TextMatchPolicy policy){
  if(value==null || accepted==null || policy==null)return false;
  return accepted.stream().anyMatch(answer->policy==TextMatchPolicy.EXACT?answer.equals(value):answer.equalsIgnoreCase(value));
 }
}
