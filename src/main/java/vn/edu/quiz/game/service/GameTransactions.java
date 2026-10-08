package vn.edu.quiz.game.service;

import java.security.SecureRandom;
import java.util.*;
import java.util.function.LongFunction;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.quiz.game.dto.GameplayRulesSnapshot;
import vn.edu.quiz.game.engine.*;
import vn.edu.quiz.game.entity.*;
import vn.edu.quiz.game.enums.*;
import vn.edu.quiz.game.repository.*;
import vn.edu.quiz.game.runtime.PhaseWindow;
import vn.edu.quiz.quiz.entity.Quiz;
import vn.edu.quiz.quiz.enums.Option;
import vn.edu.quiz.quiz.repository.*;
import vn.edu.quiz.quiz.service.QuizAccessPolicy;
import vn.edu.quiz.room.entity.Room;
import vn.edu.quiz.room.enums.*;
import vn.edu.quiz.room.repository.*;
import vn.edu.quiz.room.service.*;
import vn.edu.quiz.user.entity.UserAccount;
import vn.edu.quiz.user.repository.UserRepository;

/** Only persistence/orchestration. All returned copied data must wait for this transaction proxy to commit. */
@Service @Profile("mysql") @Transactional(timeout=5,isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
public class GameTransactions {
    public record Started(RoomMutation room,CommittedGame game) {}
    public record Opened(CommittedGame game,PhaseWindow window) {}
    private final RoomRepository rooms;
    private final RoomMemberRepository roomMembers;
    private final QuizRepository quizzes;
    private final QuestionRepository quizQuestions;
    private final UserRepository users;
    private final GameSessionRepository games;
    private final GameMemberRepository members;
    private final PlayerSessionRepository players;
    private final GameQuestionRepository questions;
    private final AnswerRepository answers;
    private final UserActiveGameRepository active;
    private final QuizAccessPolicy quizPolicy;
    private final RoomService roomService;
    private final GameProjection projection;
    private final SecureRandom random=new SecureRandom();
    @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager entities;
    public GameTransactions(RoomRepository rooms,RoomMemberRepository roomMembers,QuizRepository quizzes,QuestionRepository quizQuestions,
            UserRepository users,GameSessionRepository games,GameMemberRepository members,PlayerSessionRepository players,
            GameQuestionRepository questions,AnswerRepository answers,UserActiveGameRepository active,QuizAccessPolicy quizPolicy,
            RoomService roomService,GameProjection projection) {
        this.rooms=rooms; this.roomMembers=roomMembers; this.quizzes=quizzes; this.quizQuestions=quizQuestions; this.users=users;
        this.games=games; this.members=members; this.players=players; this.questions=questions; this.answers=answers; this.active=active;
        this.quizPolicy=quizPolicy; this.roomService=roomService; this.projection=projection;
    }

    public Started start(long userId,long roomId,long expectedRevision,int count,long epochMs) {
        if(count<10 || count>50) throw GameFailure.conflict("INVALID_QUESTION_COUNT");
        Room room=rooms.findLockedById(roomId).orElseThrow(RoomFailure::missing);
        if(room.getHostUserId()!=userId) throw RoomFailure.forbidden();
        if(room.getStatus()!=RoomStatus.WAITING || games.findByRoomIdAndStatus(roomId,GameStatus.ACTIVE).isPresent()) throw GameFailure.conflict("INVALID_STATE");
        if(room.getRevision()!=expectedRevision) throw GameFailure.conflict("REVISION_CONFLICT");
        Quiz quiz=quizzes.findLockedById(room.getQuizId()).filter(q -> q.getDeletedAtMs()==null).orElseThrow(() -> GameFailure.conflict("QUIZ_UNAVAILABLE"));
        quizPolicy.requireUse(userId,quiz.getOwnerUserId(),quiz.getVisibility());
        var roster=roomMembers.findByRoomIdAndStatus(roomId,MembershipStatus.JOINED).stream().sorted(Comparator.comparing(m -> m.getUserId())).toList();
        long playerCount=roster.stream().filter(m -> m.getParticipation()==Participation.PLAYER).count();
        if(playerCount<3 || playerCount>room.getMaxPlayers() || roster.stream().noneMatch(m -> m.getUserId()==userId)) throw GameFailure.conflict("NOT_ENOUGH_PLAYERS");
        var source=new ArrayList<>(quizQuestions.findByQuizIdAndDeletedAtMsIsNullOrderByOrderIndex(quiz.getId()));
        if(source.size()<count) throw GameFailure.conflict("NOT_ENOUGH_QUESTIONS");
        Map<Long,UserAccount> accounts=new HashMap<>();
        for(var m:roster) {
            var account=users.findLockedById(m.getUserId()).filter(u -> u.getDeletedAtMs()==null).orElseThrow(() -> GameFailure.conflict("USER_UNAVAILABLE"));
            accounts.put(m.getUserId(),account);
            if(active.existsById(m.getUserId())) throw GameFailure.conflict("USER_ACTIVE_GAME");
            quizPolicy.requireParticipation(m.getUserId(),quiz.getOwnerUserId(),m.getParticipation());
        }
        Collections.shuffle(source,random); // Exactly one selection, stored permanently by this Start transaction.
        // New games use the current fixed decision rule; existing game snapshots stay immutable.
        room.setDecisionDurationMs(7000L);
        var config=GameplayRulesSnapshot.forGame(count,room.getQuestionDurationMs(),room.getDecisionDurationMs());
        GameSession game=new GameSession(); game.setRoomId(roomId); game.setQuizId(quiz.getId()); game.setQuizAuthorUserId(quiz.getOwnerUserId());
        game.setQuizTitleSnapshot(quiz.getTitle()); game.setStatus(GameStatus.ACTIVE); game.setPhase(Phase.DECISION);
        game.setQuestionCount(count); game.setConfigSnapshot(config); game.setStartedAtMs(epochMs); game=games.saveAndFlush(game);
        for(var m:roster) {
            GameMember gm=new GameMember(); gm.setGameSessionId(game.getId()); gm.setRoomId(roomId); gm.setUserId(m.getUserId());
            gm.setRole(m.getUserId()==userId?MemberRole.HOST:MemberRole.MEMBER); gm.setParticipation(m.getParticipation());
            gm.setDisplayNameSnapshot(accounts.get(m.getUserId()).getDisplayName()); members.saveAndFlush(gm);
            if(m.getParticipation()==Participation.PLAYER) {
                PlayerSession p=new PlayerSession(); p.setGameSessionId(game.getId()); p.setUserId(m.getUserId()); p.setScore(config.initialScore());
                p.setRemainingSpins(config.spinCredits()); p.setRemainingSpinPool(new ArrayList<>(List.of(SpinEffect.values()))); players.save(p);
            }
            UserActiveGame occupied=new UserActiveGame(); occupied.setUserId(m.getUserId()); occupied.setGameSessionId(game.getId()); active.save(occupied);
        }
        for(int index=0;index<count;index++) {
            var original=source.get(index); GameQuestion q=new GameQuestion(); q.setGameSessionId(game.getId()); q.setSourceQuestionId(original.getId());
            q.setOrderIndex(index+1); q.setContent(original.getContent()); q.setOptionA(original.getOptionA()); q.setOptionB(original.getOptionB());
            q.setOptionC(original.getOptionC()); q.setOptionD(original.getOptionD()); q.setCorrectOption(original.getCorrectOption());
            q.setImageRef(original.getImageRef()); q.setQuestionDurationMs(config.questionDurationMs()); q.setPhase(Phase.DECISION); questions.save(q);
        }
        room.setStatus(RoomStatus.ACTIVE); active.flush(); players.flush(); questions.flush(); rooms.flush();
        return new Started(new RoomMutation(roomService.get(userId,roomId),true,game.getId()),projection.copy(game));
    }

    public Opened open(long gameId,int index,Phase phase,LongFunction<PhaseWindow> opening) {
        GameSession game=locked(gameId);
        requireActive(game);
        if(phase==Phase.DECISION) {
            if(index!=game.getCurrentQuestionIndex()+1 || (game.getCurrentQuestionIndex()>0 && game.getPhase()!=Phase.RESULT)) throw GameFailure.conflict("INVALID_STATE");
        } else if(phase!=Phase.QUESTION_OPEN || game.getPhase()!=Phase.DECISION || index!=game.getCurrentQuestionIndex()) throw GameFailure.conflict("INVALID_STATE");
        GameQuestion q=question(gameId,index);
        for(var p:players.findByGameSessionId(gameId)) if(phase==Phase.DECISION && p.getPlayerState()==PlayerState.PLAYING) {
            p.setCurrentSpin(null); p.setStarSelected(false);
        }
        // Fresh clock is sampled after acquiring DB locks/preparing state, not from the queued command.
        PhaseWindow window=opening.apply(phase==Phase.DECISION?game.getConfigSnapshot().decisionDurationMs():q.getQuestionDurationMs());
        if(window.openedEpochMs()<game.getStartedAtMs()) {
            long epoch=game.getStartedAtMs();
            window=new PhaseWindow(window.questionIndex(),window.phase(),window.token(),window.openedAtMs(),window.deadlineMs(),epoch,
                    Math.addExact(epoch,Math.subtractExact(window.deadlineMs(),window.openedAtMs())));
        }
        game.setPhase(phase); game.setCurrentQuestionIndex(index); game.setPhaseOpenedAtMs(window.openedEpochMs()); game.setPhaseDeadlineAtMs(window.deadlineEpochMs()); q.setPhase(phase);
        if(phase==Phase.QUESTION_OPEN) { q.setOpenedAtMs(window.openedEpochMs()); q.setDeadlineAtMs(window.deadlineEpochMs()); }
        players.flush(); games.flush(); return new Opened(projection.copy(game),window);
    }

    public CommittedGame accept(long gameId,long userId,int index,Option option,long receivedEpochMs,long answerTimeMs) {
        GameSession game=locked(gameId); requireActive(game);
        if(game.getPhase()!=Phase.QUESTION_OPEN || game.getCurrentQuestionIndex()!=index) throw GameFailure.conflict("QUESTION_CLOSED");
        GameQuestion q=question(gameId,index);
        PlayerSession p=players.findByGameSessionIdAndUserId(gameId,userId).filter(row -> row.getPlayerState()==PlayerState.PLAYING)
                .orElseThrow(() -> new GameFailure(HttpStatus.FORBIDDEN,"FORBIDDEN"));
        if(option==null || answerTimeMs<0 || answerTimeMs>=q.getQuestionDurationMs()
                || receivedEpochMs!=Math.addExact(q.getOpenedAtMs(),answerTimeMs)) throw GameFailure.conflict("QUESTION_CLOSED");
        if(answers.findByPlayerSessionIdAndGameQuestionId(p.getId(),q.getId()).isPresent()) throw GameFailure.conflict("ALREADY_ANSWERED");
        Answer a=new Answer(); a.setGameSessionId(gameId); a.setPlayerSessionId(p.getId()); a.setGameQuestionId(q.getId());
        a.setAnswerStatus(AnswerStatus.ACCEPTED_UNSCORED); a.setSelectedOption(option); a.setReceivedAtMs(receivedEpochMs); a.setAnswerTimeMs(answerTimeMs);
        answers.saveAndFlush(a); game.setPhase(game.getPhase()); bump(game); return projection.copy(game);
    }

    public CommittedGame transition(long gameId,int index,Phase expected,Phase next) {
        GameSession game=locked(gameId); requireActive(game);
        if(game.getCurrentQuestionIndex()!=index || game.getPhase()!=expected
                || !((expected==Phase.QUESTION_OPEN && next==Phase.QUESTION_CLOSED) || (expected==Phase.QUESTION_CLOSED && next==Phase.SCORING))) throw GameFailure.conflict("INVALID_STATE");
        game.setPhase(next); question(gameId,index).setPhase(next); games.flush(); return projection.copy(game);
    }

    public CommittedGame useSpin(long gameId,long userId,int index,SpinEffect selected) {
        GameSession game=locked(gameId); PlayerSession player=decisionPlayer(game,userId,index);
        if(player.getStarSelected()) throw GameFailure.conflict("SPIN_AFTER_STAR");
        if(player.getCurrentSpin()!=null) throw GameFailure.conflict("ALREADY_SPUN");
        if(player.getRemainingSpins()<=0 || selected==null || !player.getRemainingSpinPool().contains(selected))
            throw GameFailure.conflict("SPIN_NOT_AVAILABLE");
        var pool=new ArrayList<>(player.getRemainingSpinPool()); pool.remove(selected);
        player.setRemainingSpinPool(pool); player.setRemainingSpins(player.getRemainingSpins()-1); player.setCurrentSpin(selected);
        players.flush(); bump(game); return projection.copy(game);
    }
    public CommittedGame useStar(long gameId,long userId,int index) {
        GameSession game=locked(gameId); PlayerSession player=decisionPlayer(game,userId,index);
        if(!player.getStarAvailable() || player.getStarSelected()) throw GameFailure.conflict("STAR_NOT_AVAILABLE");
        if(player.getCurrentSpin()==SpinEffect.HARDSHIP) throw GameFailure.conflict("STAR_FORBIDDEN_HARDSHIP");
        player.setStarAvailable(false); player.setStarSelected(true);
        players.flush(); bump(game); return projection.copy(game);
    }
    private PlayerSession decisionPlayer(GameSession game,long userId,int index) {
        requireActive(game);
        if(game.getPhase()!=Phase.DECISION || game.getCurrentQuestionIndex()!=index) throw GameFailure.conflict("INVALID_STATE");
        return players.findByGameSessionIdAndUserId(game.getId(),userId).filter(p -> p.getPlayerState()==PlayerState.PLAYING)
                .orElseThrow(() -> new GameFailure(HttpStatus.FORBIDDEN,"FORBIDDEN"));
    }

    public CommittedGame score(long gameId,int index,long epochMs) {
        GameSession game=locked(gameId); GameQuestion q=question(gameId,index);
        if(q.getScoredAtMs()!=null) return projection.copy(game); // Reconcile/repeated close never scores again.
        requireActive(game);
        if(game.getPhase()!=Phase.SCORING || game.getCurrentQuestionIndex()!=index) throw GameFailure.conflict("INVALID_STATE");
        var rows=players.findByGameSessionId(gameId); var engine=new ScoreEngine(game.getConfigSnapshot());
        Map<Long,Answer> accepted=new HashMap<>(); answers.findByGameQuestionId(q.getId()).forEach(a -> accepted.put(a.getPlayerSessionId(),a));
        Map<Long,ScoringResult> calculated=new LinkedHashMap<>();
        long scoringAt=Math.max(epochMs,q.getOpenedAtMs());
        for(var p:rows) if(p.getPlayerState()==PlayerState.PLAYING) {
            var a=accepted.get(p.getId());
            AnswerStatus outcome=a==null?AnswerStatus.NO_ANSWER:a.getSelectedOption()==q.getCorrectOption()?AnswerStatus.CORRECT:AnswerStatus.WRONG;
            var before=new PlayerScore(p.getPlayerState(),p.getScore(),p.getTotalAnswerTimeMs(),p.getWinStreak(),p.getLoseStreak(),p.getHasMomentum(),p.getHasRecovery());
            calculated.put(p.getId(),engine.calculate(new ScoringInput(before,outcome,p.getCurrentSpin(),p.getStarSelected(),a==null?null:a.getAnswerTimeMs())));
            if(a!=null) scoringAt=Math.max(scoringAt,a.getReceivedAtMs());
        }
        // Only after all calculations succeed do we mutate managed entities. Commit is still all-or-nothing.
        for(var p:rows) {
            var result=calculated.get(p.getId()); if(result==null) continue;
            boolean momentumBefore=p.getHasMomentum(), recoveryBefore=p.getHasRecovery();
            var after=result.playerAfter(); p.setScore(after.score()); p.setTotalAnswerTimeMs(after.totalAnswerTimeMs());
            p.setPlayerState(after.state()); p.setWinStreak(after.winStreak()); p.setLoseStreak(after.loseStreak());
            p.setHasMomentum(after.momentum()); p.setHasRecovery(after.recovery());
            if(result.eliminatedNow()) { p.setEliminatedAtMs(scoringAt); p.setEliminatedQuestionIndex(index); }
            Answer a=accepted.get(p.getId());
            if(a==null) { a=new Answer(); a.setGameSessionId(gameId); a.setPlayerSessionId(p.getId()); a.setGameQuestionId(q.getId()); }
            a.setAnswerStatus(result.outcome()); a.setAnswerTimeMs(result.answerTimeMs()); a.setScoredAtMs(scoringAt);
            a.setBaseDelta(result.baseDelta()); a.setScoreDelta(result.scoreDelta()); a.setScoreAfter(after.score());
            var effectSnapshot=new LinkedHashMap<String,Object>(Map.of("hasMomentumBefore",momentumBefore,"hasRecoveryBefore",recoveryBefore,
                    "hasMomentumAfter",after.momentum(),"hasRecoveryAfter",after.recovery(),
                    "momentumConsumed",result.momentumConsumed(),"recoveryConsumed",result.recoveryConsumed(),
                    "momentumGranted",result.momentumGranted(),"recoveryGranted",result.recoveryGranted(),"eliminatedNow",result.eliminatedNow()));
            effectSnapshot.put("winStreak",after.winStreak()); effectSnapshot.put("loseStreak",after.loseStreak());
            effectSnapshot.put("totalAnswerTimeMs",after.totalAnswerTimeMs()); effectSnapshot.put("playerState",after.state().name());
            effectSnapshot.put("eliminatedAtMs",p.getEliminatedAtMs()); effectSnapshot.put("eliminatedQuestionIndex",p.getEliminatedQuestionIndex());
            effectSnapshot.put("spinEffect",p.getCurrentSpin()==null?null:p.getCurrentSpin().name());
            effectSnapshot.put("starSelected",p.getStarSelected());
            a.setResultSnapshot(effectSnapshot); answers.save(a);
        }
        q.setScoredAtMs(scoringAt); q.setPhase(Phase.RESULT); game.setPhase(Phase.RESULT);
        long alive=rows.stream().filter(p -> p.getPlayerState()==PlayerState.PLAYING).count();
        EndReason end=alive==0?EndReason.ALL_ELIMINATED:alive==1?EndReason.ONE_SURVIVOR:index==game.getQuestionCount()?EndReason.COMPLETED:null;
        if(end!=null) finish(game,rows,end,scoringAt);
        answers.flush(); players.flush(); games.flush(); return projection.copy(game);
    }

    public CommittedGame cancel(long gameId,long userId,long epochMs) {
        GameSession game=locked(gameId);
        if(rooms.findById(game.getRoomId()).orElseThrow(RoomFailure::missing).getHostUserId()!=userId
                || members.findByGameSessionIdAndUserId(gameId,userId).isEmpty()) throw RoomFailure.forbidden();
        requireActive(game);
        finish(game,players.findByGameSessionId(gameId),EndReason.CANCELLED,Math.max(epochMs,game.getStartedAtMs()));
        players.flush(); games.flush(); return projection.copy(game);
    }
    public CommittedGame interrupt(long gameId,long epochMs) {
        GameSession game=locked(gameId);
        if(game.getStatus()==GameStatus.ACTIVE) finish(game,players.findByGameSessionId(gameId),EndReason.SERVER_INTERRUPTED,Math.max(epochMs,game.getStartedAtMs()));
        players.flush(); games.flush(); return projection.copy(game);
    }
    public int cleanupAbandoned(long epochMs) {
        var abandoned=games.findByStatus(GameStatus.ACTIVE).stream().sorted(Comparator.comparing(GameSession::getRoomId)).toList();
        for(var game:abandoned) interrupt(game.getId(),epochMs);
        return abandoned.size();
    }
    @Transactional(readOnly=true)
    public CommittedGame read(long gameId,long userId) {
        if(members.findByGameSessionIdAndUserId(gameId,userId).isEmpty()) throw new GameFailure(HttpStatus.FORBIDDEN,"FORBIDDEN");
        return projection.copy(games.findById(gameId).orElseThrow(() -> new GameFailure(HttpStatus.NOT_FOUND,"GAME_NOT_FOUND")));
    }
    private void finish(GameSession game,List<PlayerSession> rows,EndReason reason,long at) {
        // Wall time can move backwards across restart; terminal history cannot precede committed input/results.
        at=Math.max(at,game.getStartedAtMs());
        if(game.getPhaseOpenedAtMs()!=null) at=Math.max(at,game.getPhaseOpenedAtMs());
        for(var answer:answers.findByGameSessionId(game.getId())) {
            if(answer.getReceivedAtMs()!=null) at=Math.max(at,answer.getReceivedAtMs());
            if(answer.getScoredAtMs()!=null) at=Math.max(at,answer.getScoredAtMs());
        }
        // Global order: Room -> Game -> sorted Users. Delete occupancy before changing referenced ACTIVE status.
        members.findByGameSessionId(game.getId()).stream().map(GameMember::getUserId).sorted().forEach(users::findLockedById);
        active.deleteAll(active.findByGameSessionId(game.getId())); active.flush();
        var ranks=new RankingEngine().calculate(rows.stream().map(p -> new RankingEngine.Entry(p.getUserId(),p.getScore(),p.getTotalAnswerTimeMs())).toList());
        for(var rank:ranks) rows.stream().filter(p -> p.getUserId()==rank.player().userId()).findFirst().orElseThrow().setFinalRank(rank.rank());
        game.setStatus(GameStatus.FINISHED); game.setPhase(Phase.FINISHED); game.setEndReason(reason); game.setFinishedAtMs(at);
        game.setPhaseDeadlineAtMs(null);
        var room=rooms.findById(game.getRoomId()).orElseThrow();
        room.setStatus(RoomStatus.WAITING);
        room.setDecisionDurationMs(7000L);
    }
    private GameSession locked(long id) {
        var previous=games.findById(id).orElseThrow(() -> new GameFailure(HttpStatus.NOT_FOUND,"GAME_NOT_FOUND"));
        rooms.findLockedById(previous.getRoomId()).orElseThrow(RoomFailure::missing);
        entities.refresh(previous,jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        return previous;
    }
    private GameQuestion question(long gameId,int index) { return questions.findByGameSessionIdAndOrderIndex(gameId,index).orElseThrow(() -> GameFailure.conflict("INVALID_QUESTION")); }
    private void requireActive(GameSession game) { if(game.getStatus()!=GameStatus.ACTIVE) throw GameFailure.conflict("INVALID_STATE"); }
    private void bump(GameSession game) {
        if(games.touchRevision(game.getId(),game.getRevision())!=1) throw GameFailure.conflict("REVISION_CONFLICT");
        entities.refresh(game);
    }
}
