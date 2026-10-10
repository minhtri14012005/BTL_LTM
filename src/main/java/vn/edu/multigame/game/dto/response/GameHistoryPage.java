package vn.edu.multigame.game.dto.response;
import java.util.List;
import vn.edu.multigame.game.enums.EndReason;
public record GameHistoryPage(List<Item> items,int page,int size,long totalElements) {
    public record Item(long gameSessionId,long roomId,Long quizId,String quizTitleSnapshot,
            long startedAtMs,long finishedAtMs,EndReason endReason,boolean hasOfficialWinner,int schemaVersion) {
        public Item(long gameSessionId,long roomId,Long quizId,String quizTitleSnapshot,long startedAtMs,long finishedAtMs,EndReason endReason,boolean hasOfficialWinner) {this(gameSessionId,roomId,quizId,quizTitleSnapshot,startedAtMs,finishedAtMs,endReason,hasOfficialWinner,1);}
    }
}
