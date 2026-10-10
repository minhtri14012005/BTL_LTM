package vn.edu.multigame.game.service;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.*;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.CannotCreateTransactionException;
import vn.edu.multigame.game.dto.response.GameSnapshot;
import vn.edu.multigame.game.enums.*;
import vn.edu.multigame.game.runtime.*;
import vn.edu.multigame.quiz.enums.Option;

/** One game actor's business state/orchestration. No concrete queue, timer, socket or wire dependency. */
public final class GameLifecycle {
    public record Time(long monotonicMs,long epochMs) {}
    public interface Control {
        Time now();
        void defer(long delayMs,Runnable continuation);
        void enqueue(Runnable action);
        void arm(PhaseWindow window);
        void retire();
        void stopTimers();
        void publish(GameLifecycleEvent event);
        default Set<Long> onlineUsers() { return Set.of(); }
    }
    private record View(CommittedGame data,PhaseWindow window,String mode,boolean cleanupPending) {}
    private volatile View view;
    private QuestionCloseGate gate;
    private long token;
    private java.util.List<Long> hintOffsets=java.util.List.of();
    private final Set<Long> ready=new java.util.HashSet<>();
    public static final long RESULT_DURATION_MS = 1500L;
    private final GameTransactions transactions;
    private final Control control;
    private final SpinSelector spins;
    private static final System.Logger LOG=System.getLogger(GameLifecycle.class.getName());
    public GameLifecycle(GameTransactions transactions,Control control,CommittedGame initial) {
        this(transactions,control,initial,new SpinSelector());
    }
    public GameLifecycle(GameTransactions transactions,Control control,CommittedGame initial,SpinSelector spins) {
        this.transactions=transactions; this.control=control; view=new View(initial,null,"INITIALIZING",false);
        this.spins=spins;
    }
    public void boot() {
        try { emit("GAME_STARTED"); open(1,v2()?Phase.INTRO:Phase.DECISION); }
        catch(RuntimeException failure) { unavailable(); }
    }
    public void timer(int question,Phase phase,long expectedToken) {
        var w=view.window();timer(question,phase,expectedToken,w==null?0:w.deadlineMs());
    }
    public void timer(int question,Phase phase,long expectedToken,long scheduledAtMs) {
        var current=view; var window=current.window();
        if(window==null || !window.matches(question,phase,expectedToken) || !current.mode().equals("READY")) return;
        if(phase==Phase.QUESTION_OPEN && scheduledAtMs<window.deadlineMs()) {
            // An earlier hint wakeup must never close inline ahead of already-ingressed Answers.
            revealHints(current);return;
        }
        if(window.remainingMs(control.now().monotonicMs())>0) return;
        if(phase==Phase.INTRO) beginQuestion(question);
        else if(phase==Phase.DECISION) open(question,Phase.QUESTION_OPEN);
        else if(phase==Phase.QUESTION_OPEN && gate!=null && gate.close()) closed();
        else if(phase==Phase.RESULT) nextQuestion(question+1);
    }
    private boolean v2() { return view.data().publicView().schemaVersion()==2; }
    private void beginQuestion(int index) {open(index,view.data().publicView().stage().mode()==vn.edu.multigame.game.enums.GameMode.QUIZ?Phase.DECISION:Phase.QUESTION_OPEN);}
    private void nextQuestion(int index) {
        if(!v2()) {open(index,Phase.DECISION);return;}
        var next=view.data().publicView().stages().stream().filter(s -> index>=s.firstQuestionIndex() && index<s.firstQuestionIndex()+s.questionCount()).findFirst().orElseThrow();
        open(index,index==next.firstQuestionIndex()?Phase.INTRO:next.mode()==vn.edu.multigame.game.enums.GameMode.QUIZ?Phase.DECISION:Phase.QUESTION_OPEN);
    }
    public void continueStage(long userId,int index,long receivedAt,CompletableFuture<GameSnapshot> reply) {
        var before=view;
        try {
            if(!before.data().players().containsKey(userId)) throw new GameFailure(HttpStatus.FORBIDDEN,"FORBIDDEN");
            if(!v2() || !before.mode().equals("READY") || before.window()==null || before.window().phase()!=Phase.INTRO || before.window().questionIndex()!=index) throw GameFailure.conflict("INVALID_STATE");
            if(!before.window().accepts(receivedAt)) throw GameFailure.conflict("INTRO_CLOSED");
            if(ready.contains(userId)) throw GameFailure.conflict("ALREADY_READY");
            attempt(() -> transactions.ready(id(),userId,index),committed -> {
                ready.add(userId);
                var marked=new CommittedGame(committed.publicView().withReady(ready.stream().sorted().toList()),committed.players(),committed.terminalRoom());
                view=new View(marked,before.window(),"READY",false);reply.complete(snapshot(userId));emit("INTRO_UPDATED");
                if(ready.containsAll(committed.players().keySet())) beginQuestion(index);
            },1,reply);
        } catch(GameFailure failure) {reply.completeExceptionally(failure);}
    }
    private long id() { return view.data().publicView().gameSessionId(); }
    private void open(int index,Phase phase) {
        // A queued next-phase continuation can be overtaken by an earlier queued Cancel.
        if(view.data().publicView().status()!=GameStatus.ACTIVE || view.mode().equals("UNAVAILABLE")) return;
        var onlineAtOpen=new java.util.concurrent.atomic.AtomicReference<Set<Long>>(Set.of());
        attempt(() -> transactions.open(id(),index,phase,duration -> {
            var now=control.now();
            if(v2() && phase==Phase.QUESTION_OPEN) onlineAtOpen.set(control.onlineUsers()); return PhaseWindow.open(index,phase,++token,now.monotonicMs(),now.epochMs(),duration);
        }),opened -> {
            ready.clear();hintOffsets=opened.hintOffsets();view=new View(opened.game(),opened.window(),"READY",false);
            if(phase==Phase.QUESTION_OPEN) {
                Set<Long> eligible=opened.game().players().values().stream().filter(p -> p.state()==PlayerState.PLAYING)
                        .map(GameSnapshot.Player::userId).collect(java.util.stream.Collectors.toUnmodifiableSet());
                if(v2()) eligible=eligible.stream().filter(onlineAtOpen.get()::contains).collect(java.util.stream.Collectors.toUnmodifiableSet());
                gate=new QuestionCloseGate(eligible);
            }
            armCurrent(); emit(phase==Phase.INTRO?"INTRO_STARTED":phase==Phase.DECISION?"DECISION_STARTED":"QUESTION_START");
        },1,null);
    }
    private int releasedHints(View current) {
        var q=current.data().publicView().question();
        return q!=null && q.payload()!=null && q.payload().get("hints") instanceof java.util.List<?> hints?hints.size():0;
    }
    private void armCurrent() {
        var current=view;var w=current.window();
        if(w==null) return;
        int released=releasedHints(current);
        long due=w.phase()==Phase.QUESTION_OPEN && w.remainingMs(control.now().monotonicMs())>0 && released<hintOffsets.size()
                ?Math.addExact(w.openedAtMs(),hintOffsets.get(released)):w.deadlineMs();
        // Keep the full business window in view; only the scheduler wakeup is shortened.
        control.arm(new PhaseWindow(w.questionIndex(),w.phase(),w.token(),w.openedAtMs(),due,w.openedEpochMs(),
                Math.addExact(w.openedEpochMs(),due-w.openedAtMs())));
    }
    private void revealHints(View before) {
        var w=before.window();long elapsed=control.now().monotonicMs()-w.openedAtMs();
        if(elapsed>=w.deadlineMs()-w.openedAtMs()) {control.arm(w);return;}
        int count=(int)hintOffsets.stream().filter(offset -> offset<=elapsed).count();
        if(count<=releasedHints(before)) {armCurrent();return;}
        attempt(() -> transactions.releaseHints(id(),w.questionIndex(),count,() -> control.now().monotonicMs()-w.openedAtMs()),committed -> {
            if(committed!=null) {view=new View(committed,w,"READY",false);emit("CLUES_RELEASED");}
            else view=new View(before.data(),w,"READY",false);
            armCurrent();
        },1,null);
    }
    /** Called by the serial adapter with authenticated identity and authoritative ingress time. */
    public void action(GameplayAction action,long userId,int index,Option option,long receivedAtMs,CompletableFuture<GameSnapshot> reply) {
        actionTyped(action,userId,index,option,null,receivedAtMs,reply);
    }
    public void actionTyped(GameplayAction action,long userId,int index,Option option,vn.edu.multigame.game.dto.TypedAnswer typed,long receivedAtMs,CompletableFuture<GameSnapshot> reply) {
        try {
            var current=view;
            var player=current.data().players().get(userId);
            if(player==null || player.state()!=PlayerState.PLAYING) throw new GameFailure(HttpStatus.FORBIDDEN,"FORBIDDEN");
            if(action==GameplayAction.ANSWER) { answerTyped(userId,index,option,typed,receivedAtMs,reply); return; }
            if(!current.mode().equals("READY")) throw current.mode().equals("UNAVAILABLE")?GameFailure.unavailable():GameFailure.conflict("INVALID_STATE");
            if(current.window()==null || current.window().phase()!=Phase.DECISION || current.window().questionIndex()!=index)
                throw GameFailure.conflict("INVALID_STATE");
            if(!current.window().accepts(receivedAtMs)) throw GameFailure.conflict("DECISION_CLOSED");
            Supplier<CommittedGame> operation;
            if(action==GameplayAction.USE_SPIN) {
                if(player.starSelected()) throw GameFailure.conflict("SPIN_AFTER_STAR");
                if(player.currentSpin()!=null) throw GameFailure.conflict("ALREADY_SPUN");
                if(player.remainingSpins()<=0 || player.remainingSpinPool().isEmpty()) throw GameFailure.conflict("SPIN_NOT_AVAILABLE");
                // Captured once: transaction rollback/retry never draws a replacement effect.
                var rules=v2()?vn.edu.multigame.game.dto.GameplayRulesSnapshot.forGame(10,current.data().publicView().stage().questionDurationMs(),7000):current.data().publicView().config();
                var selected=spins.select(rules,player.remainingSpinPool());
                operation=() -> transactions.useSpin(id(),userId,index,selected);
            } else {
                if(!player.starAvailable()) throw GameFailure.conflict("STAR_NOT_AVAILABLE");
                if(player.currentSpin()==SpinEffect.HARDSHIP) throw GameFailure.conflict("STAR_FORBIDDEN_HARDSHIP");
                operation=() -> transactions.useStar(id(),userId,index);
            }
            attempt(operation,committed -> { view=new View(committed,current.window(),"READY",false); reply.complete(snapshot(userId)); },1,reply);
        } catch(GameFailure failure) { reply.completeExceptionally(failure); }
    }

    public void answer(long userId,int index,Option option,long receivedAtMs,CompletableFuture<GameSnapshot> reply) {
        answerTyped(userId,index,option,null,receivedAtMs,reply);
    }
    public void answerTyped(long userId,int index,Option option,vn.edu.multigame.game.dto.TypedAnswer typed,long receivedAtMs,CompletableFuture<GameSnapshot> reply) {
        var before=view;
        if(!before.mode().equals("READY") || before.window()==null || before.window().phase()!=Phase.QUESTION_OPEN
                || before.window().questionIndex()!=index || !before.window().accepts(receivedAtMs) || gate.isClosed()) {
            reply.completeExceptionally(before.mode().equals("UNAVAILABLE")?GameFailure.unavailable():GameFailure.conflict("QUESTION_CLOSED")); return;
        }
        long elapsed=receivedAtMs-before.window().openedAtMs();
        long epoch=Math.addExact(before.window().openedEpochMs(),elapsed);
        attempt(() -> typed==null?transactions.accept(id(),userId,index,option,epoch,elapsed):transactions.acceptTyped(id(),userId,index,option,typed,epoch,elapsed),committed -> {
            view=new View(committed,before.window(),"READY",false);
            gate.recordValidAnswer(userId); reply.complete(snapshot(userId));
            if(gate.closeIfAllAnswered()) closed();
        },1,reply);
    }
    private void closed() {
        int index=view.data().publicView().questionIndex();
        attempt(() -> transactions.transition(id(),index,Phase.QUESTION_OPEN,Phase.QUESTION_CLOSED),closed -> {
            view=new View(closed,null,"READY",false); emit("QUESTION_CLOSED");
            attempt(() -> transactions.transition(id(),index,Phase.QUESTION_CLOSED,Phase.SCORING),scoring -> {
                view=new View(scoring,null,"READY",false); emit("SCORING_STARTED");
                attempt(() -> transactions.score(id(),index,control.now().epochMs()),result -> {
                    // Presentation window starts only after the scoring proxy has committed.
                    var now=control.now();
                    var resultWindow=PhaseWindow.open(index,Phase.RESULT,++token,now.monotonicMs(),now.epochMs(),RESULT_DURATION_MS);
                    view=new View(result,resultWindow,result.publicView().status()==GameStatus.FINISHED?"FINISHED":"READY",false);
                    emit("QUESTION_RESULT");
                    if(result.publicView().results().stream().anyMatch(GameSnapshot.Result::eliminatedNow)) emit("PLAYER_ELIMINATED");
                    emit("LEADERBOARD_UPDATED");
                    if(result.publicView().status()==GameStatus.FINISHED) { control.retire(); emit("GAME_END"); }
                    else control.arm(resultWindow);
                },1,null);
            },1,null);
        },1,null);
    }
    private <T> void attempt(Supplier<T> operation,Consumer<T> committed,int attempt,CompletableFuture<?> reply) {
        T result;
        try { result=operation.get(); }
        catch(GameFailure | vn.edu.multigame.room.service.RoomFailure business) {
            if(reply!=null) reply.completeExceptionally(business); else unavailable(); return;
        } catch(RuntimeException failure) {
            if((failure instanceof TransientDataAccessException || failure instanceof CannotCreateTransactionException) && attempt<3) {
                var previous=view; view=new View(previous.data(),previous.window(),"RETRYING",previous.cleanupPending());
                control.defer(attempt==1?100:300,() -> attempt(operation,committed,attempt+1,reply)); return;
            }
            unavailable(); if(reply!=null) reply.completeExceptionally(GameFailure.unavailable()); return;
        }
        try { committed.accept(result); }
        catch(RuntimeException publishFailure) { unavailable(); if(reply!=null) reply.completeExceptionally(GameFailure.unavailable()); }
    }
    public void cancel(long userId,CompletableFuture<GameSnapshot> reply) {
        var current=view;
        if(current.data().publicView().members().stream().noneMatch(m -> m.userId()==userId && m.role()==MemberRole.HOST)) {
            reply.completeExceptionally(new GameFailure(HttpStatus.FORBIDDEN,"FORBIDDEN")); return;
        }
        if(!current.mode().equals("READY") || current.data().publicView().status()!=GameStatus.ACTIVE) {
            reply.completeExceptionally(current.mode().equals("UNAVAILABLE")?GameFailure.unavailable():GameFailure.conflict("INVALID_STATE"));return;
        }
        attempt(() -> transactions.cancel(id(),userId,control.now().epochMs()),committed -> {
            view=new View(committed,null,"FINISHED",false);
            gate=null; control.retire();
            reply.complete(snapshot(userId)); // Receipt/ACK runs after TX commit, before terminal fan-out.
            emit("GAME_END");
        },1,reply);
    }
    public void interrupt() { unavailable(); }
    private void unavailable() {
        var before=view; if(before.mode().equals("UNAVAILABLE")) return;
        boolean pending=before.data().publicView().status()!=GameStatus.FINISHED;
        view=new View(before.data(),null,"UNAVAILABLE",pending);
        if(pending) control.stopTimers(); else control.retire();
        notifyError();
        if(pending) cleanupFailure(1);
    }
    private void cleanupFailure(int attempt) {
        try {
            CommittedGame interrupted=transactions.interrupt(id(),control.now().epochMs());
            view=new View(interrupted,null,"UNAVAILABLE",false);
            control.retire();
            try { emit("GAME_END"); } catch(RuntimeException failure) { LOG.log(System.Logger.Level.ERROR,"Committed interruption notification failed"); }
        } catch(RuntimeException failure) {
            if(attempt<3) control.defer(attempt==1?100:300,() -> cleanupFailure(attempt+1));
            else { notifyError(); LOG.log(System.Logger.Level.ERROR,"Game cleanup pending; restore MySQL and restart Server"); }
        }
    }
    private void notifyError() {
        try { emit("GAME_UNAVAILABLE"); }
        catch(RuntimeException failure) { LOG.log(System.Logger.Level.ERROR,"Runtime error notification failed"); }
    }
    public GameSnapshot snapshot(long userId) {
        View current=view;
        if(!current.data().hasMember(userId)) throw new GameFailure(HttpStatus.FORBIDDEN,"FORBIDDEN");
        return contextual(current,current.data().players().get(userId));
    }
    private GameSnapshot contextual(View current,GameSnapshot.Player self) {
        var now=control.now(); var window=current.window();
        if(window!=null && current.data().publicView().status()==GameStatus.FINISHED && window.remainingMs(now.monotonicMs())==0) window=null;
        return current.data().publicView().contextual(now.epochMs(),window==null?null:window.deadlineEpochMs(),
                window==null?null:window.remainingMs(now.monotonicMs()),current.mode(),current.cleanupPending(),self);
    }
    private void emit(String type) {
        var current=view;
        control.publish(new GameLifecycleEvent(type,contextual(current,null),current.data().players(),
                type.equals("GAME_END")?current.data().terminalRoom():null));
    }
}
