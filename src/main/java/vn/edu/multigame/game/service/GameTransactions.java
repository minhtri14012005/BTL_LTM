package vn.edu.multigame.game.service;

import java.security.SecureRandom;
import java.util.*;
import java.util.function.LongFunction;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.multigame.game.dto.GameplayRulesSnapshot;
import vn.edu.multigame.game.engine.*;
import vn.edu.multigame.game.entity.*;
import vn.edu.multigame.game.enums.*;
import vn.edu.multigame.game.repository.*;
import vn.edu.multigame.game.runtime.PhaseWindow;
import vn.edu.multigame.questionbank.entity.Quiz;
import vn.edu.multigame.quiz.enums.Option;
import vn.edu.multigame.questionbank.repository.*;
import vn.edu.multigame.questionbank.service.QuestionBankAccessPolicy;
import vn.edu.multigame.room.entity.Room;
import vn.edu.multigame.room.enums.*;
import vn.edu.multigame.room.repository.*;
import vn.edu.multigame.room.service.*;
import vn.edu.multigame.user.entity.UserAccount;
import vn.edu.multigame.user.repository.UserRepository;

/** Only persistence/orchestration. All returned copied data must wait for this transaction proxy to commit. */
@Service @Profile("mysql") @Transactional(timeout=5,isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
public class GameTransactions {
    public record Started(RoomMutation room,CommittedGame game) {}
    public record Opened(CommittedGame game,PhaseWindow window,List<Long> hintOffsets) {
        public Opened(CommittedGame game,PhaseWindow window) {this(game,window,List.of());}
    }
    private final RoomRepository rooms;
    private final RoomStageRepository roomStages;
    private final GameStageRepository gameStages;
    private final com.fasterxml.jackson.databind.ObjectMapper json;
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
    private final QuestionBankAccessPolicy quizPolicy;
    private final RoomService roomService;
    private final GameProjection projection;
    private final SecureRandom random=new SecureRandom();
    @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager entities;
    public GameTransactions(RoomRepository rooms,RoomMemberRepository roomMembers,QuizRepository quizzes,QuestionRepository quizQuestions,
            UserRepository users,GameSessionRepository games,GameMemberRepository members,PlayerSessionRepository players,
            GameQuestionRepository questions,AnswerRepository answers,UserActiveGameRepository active,QuestionBankAccessPolicy quizPolicy,
            RoomService roomService,GameProjection projection,RoomStageRepository roomStages,GameStageRepository gameStages,com.fasterxml.jackson.databind.ObjectMapper json) {
        this.rooms=rooms; this.roomMembers=roomMembers; this.quizzes=quizzes; this.quizQuestions=quizQuestions; this.users=users;
        this.games=games; this.members=members; this.players=players; this.questions=questions; this.answers=answers; this.active=active;
        this.quizPolicy=quizPolicy; this.roomService=roomService; this.projection=projection;
        this.roomStages=roomStages;this.gameStages=gameStages;this.json=json;
    }

    public Started start(long userId,long roomId,long expectedRevision,int count,long epochMs) {
        if(count<1 || count>50) throw GameFailure.conflict("INVALID_QUESTION_COUNT");
        Room room=rooms.findLockedById(roomId).orElseThrow(RoomFailure::missing);
        if(room.getHostUserId()!=userId) throw RoomFailure.forbidden();
        if(room.getStatus()!=RoomStatus.WAITING || games.findByRoomIdAndStatus(roomId,GameStatus.ACTIVE).isPresent()) throw GameFailure.conflict("INVALID_STATE");
        if(room.getRevision()!=expectedRevision) throw GameFailure.conflict("REVISION_CONFLICT");
        if(room.getConfigVersion()==2) {
            var plan=roomStages.findByRoomIdAndPlanRevisionOrderByOrderIndex(roomId,room.getPlanRevision());

            if(plan.stream().mapToInt(vn.edu.multigame.room.entity.RoomStage::getQuestionCount).sum()!=count) throw GameFailure.conflict("INVALID_QUESTION_COUNT");
            var saved=startMultimodeSnapshot(userId,roomId,expectedRevision,epochMs);
            return new Started(saved,projection.copy(games.findById(saved.gameSessionId()).orElseThrow()));
        }
        if(count<10) throw new GameFailure(HttpStatus.BAD_REQUEST,"INVALID_REQUEST");
        Quiz quiz=quizzes.findLockedById(room.getQuizId()).filter(q -> q.getDeletedAtMs()==null).orElseThrow(() -> GameFailure.conflict("QUIZ_UNAVAILABLE"));
        quizPolicy.requireUse(userId,quiz.getOwnerUserId(),quiz.getVisibility());
        if(quiz.getMode()!=vn.edu.multigame.game.enums.GameMode.QUIZ) throw GameFailure.conflict("MODE_NOT_IMPLEMENTED");
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

    /** Atomic v2 roster/content/config foundation; public Start validates its supported modes before calling this transaction.
     * Caller must enter the existing Room boundary before invoking this transaction proxy. */
    public RoomMutation startMultimodeSnapshot(long userId,long roomId,long expectedRevision,long epochMs) {
        Room room=rooms.findLockedById(roomId).orElseThrow(RoomFailure::missing);
        if(room.getHostUserId()!=userId) throw RoomFailure.forbidden();
        if(room.getStatus()!=RoomStatus.WAITING || games.findByRoomIdAndStatus(roomId,GameStatus.ACTIVE).isPresent()) throw GameFailure.conflict("INVALID_STATE");
        if(room.getRevision()!=expectedRevision) throw GameFailure.conflict("REVISION_CONFLICT");
        if(room.getConfigVersion()!=2) throw GameFailure.conflict("RULES_NOT_IMPLEMENTED");
        var plan=roomStages.findByRoomIdAndPlanRevisionOrderByOrderIndex(roomId,room.getPlanRevision());
        int total=plan.stream().mapToInt(vn.edu.multigame.room.entity.RoomStage::getQuestionCount).sum();
        if(plan.isEmpty() || plan.size()>7 || total<1 || total>50 || plan.stream().map(vn.edu.multigame.room.entity.RoomStage::getMode).distinct().count()!=plan.size())
            throw GameFailure.conflict("INVALID_STAGE_PLAN");
        for(int i=0;i<plan.size();i++) if(plan.get(i).getOrderIndex()!=i+1) throw GameFailure.conflict("INVALID_STAGE_PLAN");
        // One global lock order: Room -> all selected sets by id -> roster Users by id.
        Map<Long,Quiz> selected=new HashMap<>();
        plan.stream().map(vn.edu.multigame.room.entity.RoomStage::getQuizId).distinct().sorted().forEach(id -> {
            var quiz=quizzes.findLockedById(id).filter(q -> q.getDeletedAtMs()==null).orElseThrow(() -> GameFailure.conflict("QUIZ_UNAVAILABLE"));
            quizPolicy.requireUse(userId,quiz.getOwnerUserId(),quiz.getVisibility()); selected.put(id,quiz);
        });
        Map<Long,List<vn.edu.multigame.questionbank.entity.Question>> source=new HashMap<>();
        for(var stage:plan) {
            var quiz=selected.get(stage.getQuizId());
            if(quiz.getMode()!=stage.getMode()) throw GameFailure.conflict("MODE_MISMATCH");
            var data=quizQuestions.findByQuizIdAndDeletedAtMsIsNullOrderByOrderIndex(quiz.getId());
            if(data.size()<stage.getQuestionCount()) throw GameFailure.conflict("NOT_ENOUGH_QUESTIONS");
            if(stage.getMode()==vn.edu.multigame.game.enums.GameMode.CLUES && data.stream().limit(stage.getQuestionCount())
                .anyMatch(q -> !vn.edu.multigame.questionbank.model.ClueTimeline.fits(q.getPayload(),stage.getQuestionDurationMs())))
                throw GameFailure.conflict("INVALID_HINT_TIMELINE");
            source.put(quiz.getId(),data);
        }
        var roster=roomMembers.findByRoomIdAndStatus(roomId,MembershipStatus.JOINED).stream().sorted(Comparator.comparing(m -> m.getUserId())).toList();
        long playerCount=roster.stream().filter(m -> m.getParticipation()==Participation.PLAYER).count();
        if(playerCount<3 || playerCount>room.getMaxPlayers() || roster.stream().noneMatch(m -> m.getUserId()==userId))
            throw GameFailure.conflict("NOT_ENOUGH_PLAYERS");
        Map<Long,UserAccount> accounts=new HashMap<>();
        for(var m:roster) {
            var account=users.findLockedById(m.getUserId()).filter(u -> u.getDeletedAtMs()==null).orElseThrow(() -> GameFailure.conflict("USER_UNAVAILABLE"));
            accounts.put(m.getUserId(),account);
            if(active.existsById(m.getUserId())) throw GameFailure.conflict("USER_ACTIVE_GAME");
            for(var quiz:selected.values()) quizPolicy.requireParticipation(m.getUserId(),quiz.getOwnerUserId(),m.getParticipation());
        }
        int quizCount=plan.stream().filter(s -> s.getMode()==vn.edu.multigame.game.enums.GameMode.QUIZ).mapToInt(vn.edu.multigame.room.entity.RoomStage::getQuestionCount).sum();
        var config=vn.edu.multigame.game.dto.MultimodeRulesSnapshot.forGame(total,quizCount);
        var game=new GameSession();game.setRoomId(roomId);game.setSchemaVersion(2);game.setRulesVersion(2);
        game.setV2ConfigSnapshot(config);game.setQuestionCount(total);game.setStatus(GameStatus.ACTIVE);game.setPhase(Phase.INTRO);game.setStartedAtMs(epochMs);
        game=games.saveAndFlush(game);
        for(var m:roster) {
            var member=new GameMember();member.setGameSessionId(game.getId());member.setRoomId(roomId);member.setUserId(m.getUserId());
            member.setRole(m.getUserId()==userId?MemberRole.HOST:MemberRole.MEMBER);member.setParticipation(m.getParticipation());
            member.setDisplayNameSnapshot(accounts.get(m.getUserId()).getDisplayName());members.saveAndFlush(member);
            if(m.getParticipation()==Participation.PLAYER) {
                var player=new PlayerSession();player.setGameSessionId(game.getId());player.setUserId(m.getUserId());player.setSchemaVersion(2);
                player.setScore(0);player.setTotalCorrectAnswerTimeMs(0L);player.setRemainingSpins(config.spinCredits());player.setStarAvailable(config.starCredits()==1);
                player.setRemainingSpinPool(quizCount==0?List.of():new ArrayList<>(List.of(SpinEffect.values())));players.save(player);
            }
            var occupied=new UserActiveGame();occupied.setUserId(m.getUserId());occupied.setGameSessionId(game.getId());active.save(occupied);
        }
        int global=0;
        for(var planned:plan) {
            var quiz=selected.get(planned.getQuizId());
            var stage=new GameStage();stage.setGameSessionId(game.getId());stage.setOrderIndex(planned.getOrderIndex());stage.setMode(planned.getMode());
            stage.setSourceQuizId(quiz.getId());stage.setAuthorUserId(quiz.getOwnerUserId());stage.setTitleSnapshot(quiz.getTitle());
            stage.setFirstQuestionIndex(global+1);stage.setQuestionCount(planned.getQuestionCount());stage.setQuestionDurationMs(planned.getQuestionDurationMs());
            stage.setConfigSnapshot(stageRules(planned.getMode(),planned.getQuestionDurationMs()));stage=gameStages.saveAndFlush(stage);
            for(int local=1;local<=planned.getQuestionCount();local++) {
                var original=source.get(quiz.getId()).get(local-1);var q=new GameQuestion();
                q.setGameSessionId(game.getId());q.setSourceQuestionId(original.getId());q.setSchemaVersion(2);q.setMode(planned.getMode());
                q.setStageId(stage.getId());q.setStageQuestionIndex(local);q.setOrderIndex(++global);q.setContent(original.getContent());
                q.setOptionA(original.getOptionA());q.setOptionB(original.getOptionB());q.setOptionC(original.getOptionC());q.setOptionD(original.getOptionD());
                q.setCorrectOption(original.getCorrectOption());q.setImageRef(original.getImageRef());
                if(original.getPayload()!=null) q.setPayload(snapshotPayload(original));
                q.setQuestionDurationMs(planned.getQuestionDurationMs());q.setPhase(Phase.DECISION);questions.save(q);
            }
        }
        room.setStatus(RoomStatus.ACTIVE);active.flush();players.flush();questions.flush();rooms.flush();
        return new RoomMutation(roomService.get(userId,roomId),true,game.getId());
    }
    private Map<String,Object> stageRules(vn.edu.multigame.game.enums.GameMode mode,long duration) {
        var snapshot=new LinkedHashMap<String,Object>();
        snapshot.put("schemaVersion",2);snapshot.put("mode",mode.name());snapshot.put("questionDurationMs",duration);
        if(mode==vn.edu.multigame.game.enums.GameMode.QUIZ) {
            var tables=GameplayRulesSnapshot.forGame(10,duration,7000);
            snapshot.put("normal",tables.normal());snapshot.put("starOnly",tables.starOnly());snapshot.put("spins",tables.spins());
            snapshot.put("streaks",tables.streaks());snapshot.put("spinWithoutReplacement",tables.spinWithoutReplacement());
            snapshot.put("spinBeforeStarOnly",tables.spinBeforeStarOnly());snapshot.put("lastQuestionMultiplier",1);
        } else {
            snapshot.put("correctPoints",10);snapshot.put("lastQuestionCorrectPoints",20);
            snapshot.put("wrongPoints",0);snapshot.put("noAnswerPoints",0);
        }
        return json.convertValue(snapshot,new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>(){});
    }

    public Opened open(long gameId,int index,Phase phase,LongFunction<PhaseWindow> opening) {
        GameSession game=locked(gameId);
        requireActive(game);
        if(game.getSchemaVersion()==2) return openMultimode(game,index,phase,opening);
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

    private Opened openMultimode(GameSession game,int index,Phase phase,LongFunction<PhaseWindow> opening) {
        var q=question(game.getId(),index);var stage=gameStages.findById(q.getStageId()).orElseThrow();
        Phase expected=game.getPhase();boolean next=index==game.getCurrentQuestionIndex()+1;
        boolean first=next && (game.getCurrentQuestionIndex()==0 || expected==Phase.RESULT);
        boolean stageFirst=q.getStageQuestionIndex()==1;
        boolean valid=phase==Phase.INTRO?first && stageFirst:
            phase==Phase.DECISION?q.getMode()==vn.edu.multigame.game.enums.GameMode.QUIZ && ((expected==Phase.INTRO && index==game.getCurrentQuestionIndex()) || first && !stageFirst):
            phase==Phase.QUESTION_OPEN && (q.getMode()==vn.edu.multigame.game.enums.GameMode.QUIZ?expected==Phase.DECISION && index==game.getCurrentQuestionIndex():
                (expected==Phase.INTRO && index==game.getCurrentQuestionIndex()) || first && !stageFirst);
        if(!valid) throw GameFailure.conflict("INVALID_STATE");
        if(phase!=Phase.QUESTION_OPEN || expected!=Phase.DECISION) for(var p:players.findByGameSessionId(game.getId())) {p.setCurrentSpin(null);p.setStarSelected(false);}
        long duration=phase==Phase.INTRO?game.getV2ConfigSnapshot().introDurationMs():phase==Phase.DECISION?game.getV2ConfigSnapshot().decisionDurationMs():q.getQuestionDurationMs();
        var window=opening.apply(duration);
        if(window.openedEpochMs()<game.getStartedAtMs()) {long epoch=game.getStartedAtMs();window=new PhaseWindow(index,phase,window.token(),window.openedAtMs(),window.deadlineMs(),epoch,Math.addExact(epoch,duration));}
        game.setPhase(phase);game.setCurrentQuestionIndex(index);game.setCurrentStageIndex(stage.getOrderIndex());
        game.setPhaseOpenedAtMs(window.openedEpochMs());game.setPhaseDeadlineAtMs(window.deadlineEpochMs());
        if(phase!=Phase.INTRO) q.setPhase(phase);
        List<Long> hints=phase==Phase.QUESTION_OPEN && q.getMode()==vn.edu.multigame.game.enums.GameMode.CLUES
                ?vn.edu.multigame.questionbank.model.ClueTimeline.offsets(q.getPayload()):List.of();
        if(phase==Phase.QUESTION_OPEN) {
            q.setOpenedAtMs(window.openedEpochMs());q.setDeadlineAtMs(window.deadlineEpochMs());
            q.setReleasedHintCount(!hints.isEmpty() && hints.getFirst()==0?1:0);
        }
        players.flush();questions.flush();games.flush();return new Opened(projection.copy(game),window,hints);
    }
    /** Called only by the game actor; release cursor and revision commit together. */
    public CommittedGame releaseHints(long gameId,int index,int count,java.util.function.LongSupplier elapsedMs) {
        var game=locked(gameId);requireActive(game);
        if(game.getCurrentQuestionIndex()!=index || game.getPhase()!=Phase.QUESTION_OPEN) throw GameFailure.conflict("QUESTION_CLOSED");
        var q=question(gameId,index);
        if(q.getMode()!=vn.edu.multigame.game.enums.GameMode.CLUES || count<=q.getReleasedHintCount()
                || count>vn.edu.multigame.questionbank.model.ClueTimeline.offsets(q.getPayload()).size()) throw GameFailure.conflict("INVALID_STATE");
        long elapsed=elapsedMs.getAsLong();
        var offsets=vn.edu.multigame.questionbank.model.ClueTimeline.offsets(q.getPayload());
        if(elapsed>=q.getQuestionDurationMs()) return null; // Retry crossed the deadline: no late publication.
        if(elapsed<offsets.get(count-1)) throw GameFailure.conflict("INVALID_STATE");
        q.setReleasedHintCount(count);questions.flush();bump(game);return projection.copy(game);
    }
    public CommittedGame ready(long gameId,long userId,int index) {
        var game=locked(gameId);requireActive(game);
        if(game.getSchemaVersion()!=2 || game.getPhase()!=Phase.INTRO || game.getCurrentQuestionIndex()!=index) throw GameFailure.conflict("INVALID_STATE");
        if(players.findByGameSessionIdAndUserId(gameId,userId).isEmpty()) throw new GameFailure(HttpStatus.FORBIDDEN,"FORBIDDEN");
        bump(game);return projection.copy(game);
    }

    public CommittedGame accept(long gameId,long userId,int index,Option option,long receivedEpochMs,long answerTimeMs) {
        return acceptTyped(gameId,userId,index,option,null,receivedEpochMs,answerTimeMs);
    }
    public CommittedGame acceptTyped(long gameId,long userId,int index,Option option,vn.edu.multigame.game.dto.TypedAnswer typed,long receivedEpochMs,long answerTimeMs) {
        GameSession game=locked(gameId); requireActive(game);
        if(game.getPhase()!=Phase.QUESTION_OPEN || game.getCurrentQuestionIndex()!=index) throw GameFailure.conflict("QUESTION_CLOSED");
        GameQuestion q=question(gameId,index);
        PlayerSession p=players.findByGameSessionIdAndUserId(gameId,userId).filter(row -> row.getPlayerState()==PlayerState.PLAYING)
                .orElseThrow(() -> new GameFailure(HttpStatus.FORBIDDEN,"FORBIDDEN"));
        if(answerTimeMs<0 || answerTimeMs>=q.getQuestionDurationMs()
                || receivedEpochMs!=Math.addExact(q.getOpenedAtMs(),answerTimeMs)) throw GameFailure.conflict("QUESTION_CLOSED");
        if(q.getMode()==vn.edu.multigame.game.enums.GameMode.QUIZ) {
            if(option==null || typed!=null) throw GameFailure.conflict("INVALID_ANSWER_KIND");
        } else if(q.getMode()==vn.edu.multigame.game.enums.GameMode.CLUES || q.getMode()==vn.edu.multigame.game.enums.GameMode.SONG || q.getMode()==vn.edu.multigame.game.enums.GameMode.RIDDLE || q.getMode()==vn.edu.multigame.game.enums.GameMode.IMAGE_WORD) {
            if(option!=null || typed==null || typed.text()==null) throw GameFailure.conflict("INVALID_ANSWER_KIND");
        } else if(arrangement(q.getMode())) {
            if(option!=null || typed==null || typed.itemIds()==null) throw GameFailure.conflict("INVALID_ANSWER_KIND");
            if(!vn.edu.multigame.common.util.ArrangementIds.permutation(arrangementItems(q).stream().map(ArrangementEvaluator.Item::id).toList(),typed.itemIds())) throw GameFailure.conflict("INVALID_ARRANGEMENT");
        } else throw GameFailure.conflict("INVALID_ANSWER_KIND");
        if(answers.findByPlayerSessionIdAndGameQuestionId(p.getId(),q.getId()).isPresent()) throw GameFailure.conflict("ALREADY_ANSWERED");
        Answer a=new Answer(); a.setGameSessionId(gameId); a.setPlayerSessionId(p.getId()); a.setGameQuestionId(q.getId());
        a.setSchemaVersion(game.getSchemaVersion());a.setMode(q.getMode());a.setAnswerKind(answerKind(q.getMode()));a.setAnswerPayload(typed);
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
        if(question(game.getId(),index).getMode()!=vn.edu.multigame.game.enums.GameMode.QUIZ) throw GameFailure.conflict("RESOURCE_NOT_ALLOWED");
        return players.findByGameSessionIdAndUserId(game.getId(),userId).filter(p -> p.getPlayerState()==PlayerState.PLAYING)
                .orElseThrow(() -> new GameFailure(HttpStatus.FORBIDDEN,"FORBIDDEN"));
    }

    public CommittedGame score(long gameId,int index,long epochMs) {
        GameSession game=locked(gameId); GameQuestion q=question(gameId,index);
        if(q.getScoredAtMs()!=null) return projection.copy(game); // Reconcile/repeated close never scores again.
        requireActive(game);
        if(game.getPhase()!=Phase.SCORING || game.getCurrentQuestionIndex()!=index) throw GameFailure.conflict("INVALID_STATE");
        var rows=players.findByGameSessionId(gameId); boolean v2=game.getSchemaVersion()==2;
        var engine=v2?null:new ScoreEngine(game.getConfigSnapshot());
        var stage=v2?gameStages.findById(q.getStageId()).orElseThrow():null;
        Map<Long,Answer> accepted=new HashMap<>(); answers.findByGameQuestionId(q.getId()).forEach(a -> accepted.put(a.getPlayerSessionId(),a));
        Map<Long,ScoringResult> calculated=new LinkedHashMap<>();
        long scoringAt=Math.max(epochMs,q.getOpenedAtMs());
        for(var p:rows) if(p.getPlayerState()==PlayerState.PLAYING) {
            var a=accepted.get(p.getId());
            AnswerStatus outcome=a==null?AnswerStatus.NO_ANSWER:correct(q,a)?AnswerStatus.CORRECT:AnswerStatus.WRONG;
            var before=new PlayerScore(p.getPlayerState(),p.getScore(),v2?p.getTotalCorrectAnswerTimeMs():p.getTotalAnswerTimeMs(),p.getWinStreak(),p.getLoseStreak(),p.getHasMomentum(),p.getHasRecovery());
            var input=new ScoringInput(before,outcome,p.getCurrentSpin(),p.getStarSelected(),a==null?null:a.getAnswerTimeMs());
            calculated.put(p.getId(),v2?new MultimodeScoreEngine().calculate(q.getMode(),q.getQuestionDurationMs(),q.getStageQuestionIndex().equals(stage.getQuestionCount()),input):engine.calculate(input));
            if(a!=null) scoringAt=Math.max(scoringAt,a.getReceivedAtMs());
        }
        // Only after all calculations succeed do we mutate managed entities. Commit is still all-or-nothing.
        for(var p:rows) {
            var result=calculated.get(p.getId()); if(result==null) continue;
            boolean momentumBefore=p.getHasMomentum(), recoveryBefore=p.getHasRecovery();
            var after=result.playerAfter(); p.setScore(after.score()); if(v2) p.setTotalCorrectAnswerTimeMs(after.totalAnswerTimeMs());else p.setTotalAnswerTimeMs(after.totalAnswerTimeMs());
            p.setPlayerState(after.state()); p.setWinStreak(after.winStreak()); p.setLoseStreak(after.loseStreak());
            p.setHasMomentum(after.momentum()); p.setHasRecovery(after.recovery());
            if(result.eliminatedNow()) { p.setEliminatedAtMs(scoringAt); p.setEliminatedQuestionIndex(index); }
            Answer a=accepted.get(p.getId());
            if(a==null) { a=new Answer(); a.setGameSessionId(gameId); a.setPlayerSessionId(p.getId()); a.setGameQuestionId(q.getId()); }
            a.setSchemaVersion(game.getSchemaVersion());a.setMode(q.getMode());a.setAnswerKind(answerKind(q.getMode()));
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
            if(v2) {
                if(q.getMode()!=vn.edu.multigame.game.enums.GameMode.QUIZ) {effectSnapshot.keySet().removeIf(k -> k.startsWith("hasMomentum") || k.startsWith("hasRecovery"));effectSnapshot.put("quizMomentum",after.momentum());effectSnapshot.put("quizRecovery",after.recovery());}
                effectSnapshot.put("schemaVersion",2);effectSnapshot.put("mode",q.getMode().name());effectSnapshot.put("correctAnswerTimeMs",result.outcome()==AnswerStatus.CORRECT?result.answerTimeMs():0L);
                effectSnapshot.put("totalCorrectAnswerTimeMs",after.totalAnswerTimeMs());effectSnapshot.put("totalAnswerTimeMs",p.getTotalAnswerTimeMs());effectSnapshot.put("ruleDelta",result.ruleDelta());
            }
            a.setResultSnapshot(effectSnapshot); answers.save(a);
        }
        q.setScoredAtMs(scoringAt); q.setPhase(Phase.RESULT); game.setPhase(Phase.RESULT);
        long alive=rows.stream().filter(p -> p.getPlayerState()==PlayerState.PLAYING).count();
        EndReason end=v2?(index==game.getQuestionCount()?EndReason.COMPLETED:null):alive==0?EndReason.ALL_ELIMINATED:alive==1?EndReason.ONE_SURVIVOR:index==game.getQuestionCount()?EndReason.COMPLETED:null;
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
        var ranks=new RankingEngine().calculate(rows.stream().map(p -> new RankingEngine.Entry(p.getUserId(),p.getScore(),game.getSchemaVersion()==2?p.getTotalCorrectAnswerTimeMs():p.getTotalAnswerTimeMs())).toList());
        for(var rank:ranks) rows.stream().filter(p -> p.getUserId()==rank.player().userId()).findFirst().orElseThrow().setFinalRank(rank.rank());
        game.setStatus(GameStatus.FINISHED); game.setPhase(Phase.FINISHED); game.setEndReason(reason); game.setFinishedAtMs(at);
        game.setPhaseDeadlineAtMs(null);
        var room=rooms.findById(game.getRoomId()).orElseThrow();
        room.setStatus(RoomStatus.WAITING);
        room.setDecisionDurationMs(7000L);
    }
    private static boolean arrangement(vn.edu.multigame.game.enums.GameMode mode) {return mode==vn.edu.multigame.game.enums.GameMode.VIETNAMESE_PUZZLE || mode==vn.edu.multigame.game.enums.GameMode.ORDERING;}
    private static AnswerKind answerKind(vn.edu.multigame.game.enums.GameMode mode) {return mode==vn.edu.multigame.game.enums.GameMode.QUIZ?AnswerKind.OPTION:arrangement(mode)?AnswerKind.ARRANGEMENT:AnswerKind.TEXT;}
    @SuppressWarnings("unchecked")
    private List<ArrangementEvaluator.Item> arrangementItems(GameQuestion q) {
        var list=(List<Map<String,Object>>)q.getPayload().get(q.getMode()==vn.edu.multigame.game.enums.GameMode.VIETNAMESE_PUZZLE?"pieces":"items");
        return list.stream().map(v -> new ArrangementEvaluator.Item((String)v.get("id"),(String)v.get("text"))).toList();
    }
    @SuppressWarnings("unchecked")
    private Map<String,Object> snapshotPayload(vn.edu.multigame.questionbank.entity.Question original) {
        var copied=json.convertValue(original.getPayload(),new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>(){});
        if(arrangement(original.getMode())) {
            String key=original.getMode()==vn.edu.multigame.game.enums.GameMode.VIETNAMESE_PUZZLE?"pieces":"items";
            var values=new ArrayList<>((List<Map<String,Object>>)copied.get(key));
            Collections.shuffle(values,random);
            var correct=(List<String>)copied.get("correctOrder");
            // Avoid presenting the canonical answer as the initial ordering; persist this permutation once.
            if(values.stream().map(v -> (String)v.get("id")).toList().equals(correct)) Collections.swap(values,0,1);
            copied.put(key,values);
        }
        return copied;
    }
    @SuppressWarnings("unchecked")
    private boolean correct(GameQuestion q,Answer a) {
        if(q.getMode()==vn.edu.multigame.game.enums.GameMode.QUIZ) return a.getSelectedOption()==q.getCorrectOption();
        if(arrangement(q.getMode())) return new ArrangementEvaluator().matches(q.getMode(),arrangementItems(q),
                (List<String>)q.getPayload().get("correctOrder"),(List<String>)q.getPayload().get("acceptedAnswers"),a.getAnswerPayload().itemIds());
        return new TextAnswerEvaluator().matches(a.getAnswerPayload().text(),(List<String>)q.getPayload().get("acceptedAnswers"));
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
