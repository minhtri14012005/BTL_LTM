package vn.edu.quiz.game.service;

import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import vn.edu.quiz.game.dto.response.*;
import vn.edu.quiz.game.entity.PlayerSession;
import vn.edu.quiz.game.enums.*;
import vn.edu.quiz.game.repository.*;
import vn.edu.quiz.quiz.enums.Option;

/** Committed, immutable history only; no runtime, timer, socket or entity serialization. */
@Service @Profile("mysql") @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ,timeout=5)
public class GameHistoryService {
    private final GameSessionRepository games;
    private final GameMemberRepository members;
    private final PlayerSessionRepository players;
    private final GameQuestionRepository questions;
    private final AnswerRepository answers;
    private final GameProjection projection;
    public GameHistoryService(GameSessionRepository games,GameMemberRepository members,PlayerSessionRepository players,
            GameQuestionRepository questions,AnswerRepository answers,GameProjection projection) {
        this.games=games;this.members=members;this.players=players;this.questions=questions;this.answers=answers;this.projection=projection;
    }
    public GameHistoryPage list(long userId,int page,int size) {
        if(page<0 || size<1 || size>100 || (long)page*size>Integer.MAX_VALUE) throw new GameFailure(HttpStatus.BAD_REQUEST,"INVALID_REQUEST");
        var result=games.history(userId,PageRequest.of(page,size));
        return new GameHistoryPage(result.stream().map(g -> new GameHistoryPage.Item(g.getId(),g.getRoomId(),g.getQuizId(),g.getQuizTitleSnapshot(),
                g.getStartedAtMs(),g.getFinishedAtMs(),g.getEndReason(),official(g.getEndReason()))).toList(),page,size,result.getTotalElements());
    }
    private boolean official(EndReason end) { return end==EndReason.COMPLETED || end==EndReason.ONE_SURVIVOR || end==EndReason.ALL_ELIMINATED; }
    public GameHistoryDetail detail(long userId,long gameId) {
        if(gameId<1) throw new GameFailure(HttpStatus.BAD_REQUEST,"INVALID_REQUEST");
        var game=games.findById(gameId).orElseThrow(() -> new GameFailure(HttpStatus.NOT_FOUND,"GAME_NOT_FOUND"));
        if(members.findByGameSessionIdAndUserId(gameId,userId).isEmpty()) throw new GameFailure(HttpStatus.FORBIDDEN,"FORBIDDEN");
        if(game.getStatus()!=GameStatus.FINISHED) throw GameFailure.conflict("HISTORY_NOT_READY");
        var committed=projection.copy(game);
        Map<Long,PlayerSession> byId=new HashMap<>();players.findByGameSessionId(gameId).forEach(p -> byId.put(p.getId(),p));
        var all=answers.findByGameSessionId(gameId);
        var timeline=questions.findByGameSessionIdOrderByOrderIndex(gameId).stream().filter(q -> q.getOpenedAtMs()!=null).map(q -> {
            var entries=all.stream().filter(a -> a.getGameQuestionId().equals(q.getId())).map(a -> {
                var p=byId.get(a.getPlayerSessionId());var effects=a.getResultSnapshot();
                String spin=effects==null?null:(String)effects.get("spinEffect");
                SpinEffect spinEffect=spin==null?null:SpinEffect.valueOf(spin);
                Boolean star=effects==null?null:effects.get("starSelected") instanceof Boolean selected?selected:null;
                if(a.getAnswerStatus()==AnswerStatus.ACCEPTED_UNSCORED && q.getOrderIndex()==game.getCurrentQuestionIndex()) {spinEffect=p.getCurrentSpin();star=p.getStarSelected();}
                return new GameHistoryDetail.Answer(a.getId(),p.getUserId(),a.getAnswerStatus(),a.getSelectedOption(),a.getReceivedAtMs(),a.getAnswerTimeMs(),
                        a.getScoredAtMs(),a.getBaseDelta(),a.getScoreDelta(),a.getScoreAfter(),spinEffect,star,
                        a.getAnswerStatus()==AnswerStatus.ACCEPTED_UNSCORED?null:projection.result(a,p));
            }).sorted(Comparator.comparingLong(GameHistoryDetail.Answer::userId)).toList();
            return new GameHistoryDetail.Question(q.getId(),q.getOrderIndex(),q.getContent(),Map.of(Option.A,q.getOptionA(),Option.B,q.getOptionB(),Option.C,q.getOptionC(),Option.D,q.getOptionD()),
                    q.getImageRef(),q.getScoredAtMs()==null?null:q.getCorrectOption(),q.getQuestionDurationMs(),q.getOpenedAtMs(),q.getDeadlineAtMs(),q.getScoredAtMs(),entries);
        }).toList();
        return new GameHistoryDetail(game.getStartedAtMs(),game.getFinishedAtMs(),committed.publicView().contextual(System.currentTimeMillis(),null,null,"FINISHED",false,committed.players().get(userId)),timeline);
    }
}
