package vn.edu.multigame.realtime.session;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import jakarta.annotation.PreDestroy;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import vn.edu.multigame.game.dto.response.GameSnapshot;
import vn.edu.multigame.game.enums.GameStatus;
import vn.edu.multigame.game.enums.GameplayAction;
import vn.edu.multigame.realtime.idempotency.*;
import vn.edu.multigame.realtime.message.command.GameCommand;
import vn.edu.multigame.realtime.message.common.*;
import vn.edu.multigame.game.runtime.PhaseWindow;
import vn.edu.multigame.game.service.*;
import vn.edu.multigame.quiz.enums.Option;
import vn.edu.multigame.realtime.timer.*;

/** Queue/time adapter only. GameLifecycle owns phase, deadline policy, closing and transaction retries. */
@Component @Profile("mysql")
public class GameRuntime {
    private record Job(Consumer<Ingress<Job>> work) {}
    private static final class Node {
        final Reservation reservation;
        SessionQueue.SessionKey key;
        GameLifecycle lifecycle;
        final GameReplayCache replay;
        java.util.Set<Long> players;
        Node(Reservation reservation,Semaphore budget) { this.reservation=reservation; this.replay=new GameReplayCache(budget); }
    }
    public static final class Reservation implements AutoCloseable {
        private final Semaphore slots;
        private final AtomicBoolean released=new AtomicBoolean();
        Reservation(Semaphore slots) { this.slots=slots; }
        @Override public void close() { if(released.compareAndSet(false,true)) slots.release(); }
    }
    private final ConcurrentHashMap<Long,Node> nodes=new ConcurrentHashMap<>();
    private final Semaphore slots=new Semaphore(512);
    private final Semaphore receiptBudget=new Semaphore(GameReplayCache.SERVER_LIMIT);
    private final SessionQueue<Job> queue;
    private final ServerClock clock;
    private final GameTransactions transactions;
    private final GameStartupCleanup startup;
    private final ApplicationEventPublisher events;
    private final SpinSelector spins;
    private final CommandFingerprint fingerprints;
    private final vn.edu.multigame.realtime.connection.AuthenticatedSocketRegistry sockets;
    private volatile boolean closed;
    private final Consumer<vn.edu.multigame.realtime.connection.AuthenticatedSocketRegistry.PresenceChange> presenceListener=this::presence;
    public GameRuntime(GameTransactions transactions,GameStartupCleanup startup,ApplicationEventPublisher events,
            ServerClock clock,TimerScheduler scheduler,SpinSelector spins,CommandFingerprint fingerprints,vn.edu.multigame.realtime.connection.AuthenticatedSocketRegistry sockets) {
        this.transactions=transactions; this.startup=startup; this.events=events; this.clock=clock;
        this.spins=spins; this.fingerprints=fingerprints;this.sockets=sockets;
        queue=new SessionQueue<>(clock,scheduler,4,512,64,32);
        sockets.onPresence(presenceListener);
    }
    public Reservation reserve() {
        cleanup();
        if(closed || !startup.ready() || !slots.tryAcquire()) throw GameFailure.unavailable();
        return new Reservation(slots);
    }
    public synchronized void install(CommittedGame data,Reservation reservation) {
        long id=data.publicView().gameSessionId(); Node node=new Node(reservation,receiptBudget);
        node.lifecycle=new GameLifecycle(transactions,new GameLifecycle.Control() {
            public GameLifecycle.Time now() { var time=clock.sample(); return new GameLifecycle.Time(time.monotonicMs(),time.epochMs()); }
            public void defer(long delay,Runnable action) { queue.defer(node.key,delay,action); }
            public void enqueue(Runnable action) { queue.submitControl(node.key,new Job(ignored -> action.run())); }
            public void arm(PhaseWindow window) { queue.armTimer(node.key,window); }
            public void retire() { queue.retire(node.key); }
            public void stopTimers() { queue.stopTimer(node.key); }
            public void publish(GameLifecycleEvent event) { events.publishEvent(event); }
        },data,spins);
        node.key=queue.register(id,new SessionQueue.Handler<>() {
            public void onCommand(Ingress<Job> command) { command.value().work().accept(command); }
            public void onTimer(Ingress<PhaseTimer> timer) {
                var identity=timer.value(); node.lifecycle.timer(identity.questionIndex(),identity.phase(),identity.token(),identity.deadlineMs());
            }
        });
        node.players=data.publicView().schemaVersion()==2?java.util.Set.copyOf(data.players().keySet()):java.util.Set.of();
        sockets.withPresence(online -> {
            node.lifecycle.initialPresence(online);
            nodes.put(id,node); submit(node,ignored -> node.lifecycle.boot());
        });
    }
    private void presence(vn.edu.multigame.realtime.connection.AuthenticatedSocketRegistry.PresenceChange changed) {
        if(closed) return;
        nodes.values().stream().filter(node -> node.players.contains(changed.userId())).forEach(node -> {
            try {queue.submitControl(node.key,new Job(ignored -> node.lifecycle.presence(changed.userId(),changed.connected())));}
            catch(RejectedExecutionException expired) { /* Disposed/failed actors cannot advance gameplay. */ }
        });
    }
    public void failedInstallation(CommittedGame data) {
        publishHandoff("GAME_UNAVAILABLE",data,true);
        try {
            var interrupted=startup.interruptCommittedStart(data.publicView().gameSessionId(),clock.sample().epochMs());
            publishHandoff("GAME_END",interrupted,false);
        } catch(RuntimeException failure) {
            System.getLogger(GameRuntime.class.getName()).log(System.Logger.Level.ERROR,
                    "Committed Start cleanup pending; restore MySQL and restart Server");
        }
    }
    private void publishHandoff(String type,CommittedGame data,boolean pending) {
        try { events.publishEvent(new GameLifecycleEvent(type,data.publicView().contextual(
                clock.sample().epochMs(),null,null,"UNAVAILABLE",pending,null),data.players(),type.equals("GAME_END")?data.terminalRoom():null)); }
        catch(RuntimeException failure) { System.getLogger(GameRuntime.class.getName()).log(System.Logger.Level.ERROR,"Handoff error notification failed"); }
    }
    /** Trusted port for Task 9: identity from auth and timestamp from the queue, never client fields. */
    public CompletableFuture<GameAck> command(long authenticatedUserId,GameCommand command,Runnable validateIdentity) {
        return command(authenticatedUserId,command,validateIdentity,() -> {});
    }
    public CompletableFuture<GameAck> command(long authenticatedUserId,GameCommand command,Runnable validateIdentity,Runnable beforeEffect) {
        var reply=new CompletableFuture<GameAck>();
        try {
            Node node=node(command.target().id());
            var processed=submit(node,ingress -> {
                GameReplayCache.Slot slot=null;
                try {
                    validateIdentity.run(); node.lifecycle.snapshot(authenticatedUserId); // Membership even for terminal replay.
                    String fingerprint=fingerprints.of(command.type(),"GAME",command.target().id(),command.questionIndex(),command.payload());
                    var cached=node.replay.lookup(authenticatedUserId,command.requestId(),fingerprint);
                    if(cached!=null) { reply.complete(cached); return; }
                    beforeEffect.run(); // Capacity/subscription cannot prevent an already committed replay.
                    slot=node.replay.reserve(authenticatedUserId,command.requestId(),fingerprint);
                    var reserved=slot;
                    var accepted=new CompletableFuture<GameSnapshot>();
                    accepted.whenComplete((snapshot,failure) -> {
                        if(failure!=null) { reserved.close(); reply.completeExceptionally(failure); return; }
                        try {
                            var p=snapshot.player();
                            var ack=new GameAck(1,"ACK",command.requestId(),command.type(),command.target(),command.questionIndex(),"ACCEPTED",
                                    snapshot.revision(),snapshot.serverTimeMs(),command.type().equals("CANCEL_GAME")?new GameAck.CancelPayload(snapshot):command.type().equals("CONTINUE")?new GameAck.ContinuePayload(snapshot.stageIndex(),snapshot.readyPlayers()):new GameAck.Payload(
                                    command.type().equals("ANSWER")?p.selectedOption():null,p.alreadyAnswered(),p.currentSpin(),p.starSelected(),
                                    p.remainingSpins(),p.starAvailable(),p.remainingSpinPool(),command.type().equals("ANSWER")?p.submittedAnswer():null));
                            reserved.commit(ack); reply.complete(ack);
                        } catch(RuntimeException rejected) { reserved.close(); reply.completeExceptionally(rejected); }
                    });
                    if(command.type().equals("CANCEL_GAME")) node.lifecycle.cancel(authenticatedUserId,accepted);
                    else if(command.type().equals("CONTINUE")) node.lifecycle.continueStage(authenticatedUserId,command.questionIndex(),ingress.receivedAtMs(),accepted);
                    else {
                        var option=command.type().equals("ANSWER") && command.payload().has("option")?Option.valueOf(command.payload().path("option").asText()):null;
                        var typed=command.type().equals("ANSWER") && command.payload().has("text")?new vn.edu.multigame.game.dto.TypedAnswer(command.payload().path("text").asText(),null):null;
                        if(command.type().equals("ANSWER") && command.payload().has("itemIds")) {
                            var ids=new java.util.ArrayList<String>();command.payload().path("itemIds").forEach(v -> ids.add(v.asText()));
                            typed=new vn.edu.multigame.game.dto.TypedAnswer(null,ids);
                        }
                        node.lifecycle.actionTyped(GameplayAction.valueOf(command.type()),authenticatedUserId,command.questionIndex(),option,typed,ingress.receivedAtMs(),accepted);
                    }
                } catch(RuntimeException failure) { if(slot!=null) slot.close(); reply.completeExceptionally(failure); }
            });
            processed.whenComplete((ignored,failure) -> { if(failure!=null) reply.completeExceptionally(failure); });
        } catch(RuntimeException failure) { reply.completeExceptionally(failure); }
        return reply;
    }

    public CompletableFuture<GameSnapshot> acceptAnswer(long id,long authenticatedUserId,int index,Option option) {
        Node node=node(id); var reply=new CompletableFuture<GameSnapshot>();
        submit(node,ingress -> node.lifecycle.answer(authenticatedUserId,index,option,ingress.receivedAtMs(),reply)); return reply;
    }
    /** Capture, subscribe and deliver in the same actor turn as commands/timers. No gameplay writes. */
    public CompletableFuture<Void> reconnect(long id,long authenticatedUserId,Runnable validateIdentity,Consumer<GameSnapshot> deliver) {
        cleanup(); // Reclaim expired idle actors before choosing the immutable terminal DB fallback.
        var reply=new CompletableFuture<Void>();
        Consumer<Ingress<Job>> read=ignored -> {
            try {
                validateIdentity.run();
                if(ignored==null) {
                    var committed=transactions.read(id,authenticatedUserId);
                    if(committed.publicView().status()!=GameStatus.FINISHED) throw GameFailure.unavailable();
                    deliver.accept(committed.publicView().contextual(clock.sample().epochMs(),null,null,"FINISHED",false,committed.players().get(authenticatedUserId)));
                } else deliver.accept(snapshot(id,authenticatedUserId));
                reply.complete(null);
            }
            catch(RuntimeException failure) { reply.completeExceptionally(failure); }
        };
        try {
            Node node=nodes.get(id);
            if(node==null) read.accept(null); // Only immutable FINISHED snapshots are readable after retention.
            else {
                try { submit(node,read).whenComplete((ignored,failure) -> { if(failure!=null) reply.completeExceptionally(failure); }); }
                catch(RejectedExecutionException unavailable) {
                    // Expired/busy terminal actor or cleanup race: final state cannot advance again.
                    if(node.lifecycle.snapshot(authenticatedUserId).status()!=GameStatus.FINISHED) throw unavailable;
                    read.accept(null);
                }
            }
        } catch(RuntimeException failure) { reply.completeExceptionally(failure); }
        return reply;
    }
    public CompletableFuture<Void> interrupt(long id) {
        Node node=node(id); var reply=new CompletableFuture<Void>();
        submit(node,ignored -> { node.lifecycle.interrupt(); reply.complete(null); }); return reply;
    }
    public GameSnapshot snapshot(long id,long authenticatedUserId) {
        Node node=nodes.get(id);
        if(node!=null) return node.lifecycle.snapshot(authenticatedUserId);
        var data=transactions.read(id,authenticatedUserId);
        if(data.publicView().status()!=GameStatus.FINISHED) throw GameFailure.unavailable();
        return data.publicView().contextual(clock.sample().epochMs(),null,null,"FINISHED",false,data.players().get(authenticatedUserId));
    }
    public boolean installed(long id) {return nodes.containsKey(id);}
    private Node node(long id) { Node node=nodes.get(id); if(node==null) throw GameFailure.unavailable(); return node; }
    private CompletableFuture<Void> submit(Node node,Consumer<Ingress<Job>> operation) { return queue.submit(node.key,new Job(operation)).processed(); }
    @Scheduled(fixedDelay=60_000) public void cleanup() {
        queue.cleanup(); nodes.forEach((id,node) -> { if(!queue.registered(node.key) && nodes.remove(id,node)) { node.replay.clear(); node.reservation.close(); } });
    }
    @PreDestroy public synchronized void close() { closed=true; sockets.removePresenceListener(presenceListener); queue.close(); nodes.values().forEach(n -> { n.replay.clear(); n.reservation.close(); }); nodes.clear(); }
}
