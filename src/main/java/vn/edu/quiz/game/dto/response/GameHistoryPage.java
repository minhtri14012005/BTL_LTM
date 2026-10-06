package vn.edu.quiz.game.dto.response;
import java.util.List;
import vn.edu.quiz.game.enums.EndReason;
public record GameHistoryPage(List<Item> items,int page,int size,long totalElements) {
    public record Item(long gameSessionId,long roomId,long quizId,String quizTitleSnapshot,
            long startedAtMs,long finishedAtMs,EndReason endReason,boolean hasOfficialWinner) {}
}
