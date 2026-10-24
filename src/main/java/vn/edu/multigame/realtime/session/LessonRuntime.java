package vn.edu.multigame.realtime.session;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.security.core.Authentication;
import org.springframework.web.socket.WebSocketSession;
import vn.edu.multigame.realtime.timer.PhaseWindow;
import vn.edu.multigame.realtime.timer.Phase;
import vn.edu.multigame.lesson.service.*;
import vn.edu.multigame.lesson.repository.LessonRealtimeStore;
import vn.edu.multigame.realtime.message.command.LessonCommand;
import vn.edu.multigame.realtime.connection.AuthenticatedSocketRegistry;
import vn.edu.multigame.realtime.timer.*;

/** The lesson actor owns ordering and timers; transactions own durable authoritative state. */
@Component @Profile("mysql")
public class LessonRuntime implements AutoCloseable {
    private record Job(Consumer<Ingress<Job>> work) {}
    private static final class Node {SessionQueue.SessionKey key;PhaseWindow window;}
    private final Map<Long,Node> nodes=new HashMap<>();
    private final SessionQueue<Job> queue;
    private final ServerClock clock;
    private final LessonRealtimeStore store;
    private final LessonRealtimeTransactions transactions;
    private final AuthenticatedSocketRegistry sockets;
    public LessonRuntime(ServerClock clock,LessonRealtimeStore store,LessonRealtimeTransactions transactions,AuthenticatedSocketRegistry sockets) {
        this.clock=clock;this.store=store;this.transactions=transactions;this.sockets=sockets;
        queue=new SessionQueue<>(clock,new ScheduledTimerScheduler(),4,512,64,32);
    }
    private synchronized Node node(long lesson) {
        queue.cleanup();nodes.entrySet().removeIf(e->queue.evictIdle(e.getValue().key));
        var existing=nodes.get(lesson);if(existing!=null)return existing;
        var node=new Node();
        node.key=queue.register(lesson,new SessionQueue.Handler<>() {
            public void onCommand(Ingress<Job> ingress) {ingress.value().work().accept(ingress);}
            public void onTimer(Ingress<PhaseTimer> timer) {
                try {
                    var nextWindow=transactions.expire(lesson,timer.value().token());
                    if(nextWindow!=null) {node.window=nextWindow;queue.armTimer(node.key,node.window);}
                    else sync(node,lesson);
                    broadcast(lesson);
                }
                catch(org.springframework.dao.TransientDataAccessException unavailable) {queue.defer(node.key,250,()->onTimer(timer));}
                catch(LessonFailure missing) {node.window=null;queue.stopTimer(node.key);queue.retire(node.key);}
            }
        });
        nodes.put(lesson,node);queue.submitControl(node.key,new Job(ignored->refresh(node,lesson)));return node;
    }
    private void refresh(Node node,long lesson) {
        try {sync(node,lesson);broadcast(lesson);}
        catch(org.springframework.dao.TransientDataAccessException unavailable) {queue.defer(node.key,250,()->refresh(node,lesson));}
    }
    private void sync(Node node,long lesson) {
        var persisted=store.window(lesson).orElse(null);
        if(persisted==null || !persisted.status().equals("OPEN")) {
            if(!store.completed(lesson)) {
                var opened=transactions.resumeObjective(lesson);
                if(opened!=null){node.window=opened;queue.armTimer(node.key,opened);return;}
            }
        }
        if(persisted==null || !persisted.status().equals("OPEN")) {
            node.window=null;queue.stopTimer(node.key);if(store.completed(lesson))queue.retire(node.key);return;
        }
        if(node.window!=null && node.window.token()==persisted.token())return;
        var now=clock.sample();long opened=now.monotonicMs()-(now.epochMs()-persisted.opened());
        node.window=PhaseWindow.open(persisted.questionIndex(),Phase.QUESTION_OPEN,persisted.token(),opened,persisted.opened(),persisted.deadline()-persisted.opened());
        queue.armTimer(node.key,node.window);
    }
    public CompletableFuture<LessonRealtimeTransactions.Result> command(Authentication caller,LessonCommand command,Runnable session) {
        session.run();transactions.snapshot(caller,command.target().id());
        var result=new CompletableFuture<LessonRealtimeTransactions.Result>();
        SessionQueue.Submission<Job> submission;
        synchronized(this) {
        var node=node(command.target().id());
        submission=queue.submit(node.key,new Job(ingress->{
            try {
                sync(node,command.target().id());var reply=transactions.execute(caller,command,ingress,node.window,session);
                if(reply.phase()!=null && (node.window==null || node.window.token()!=reply.phase().token())) {
                    node.window=reply.phase();queue.armTimer(node.key,node.window);
                }
                sync(node,command.target().id());result.complete(reply);
                if(!reply.replay() && !Set.of("SUBSCRIBE_LESSON","RECONNECT_LESSON","UNSUBSCRIBE_LESSON").contains(command.type()))broadcast(command.target().id());
            }
            catch(RuntimeException failure) {
                result.completeExceptionally(failure);
                if(failure instanceof org.springframework.dao.TransientDataAccessException)queue.defer(node.key,250,()->refresh(node,command.target().id()));
            }
        }));
        }
        submission.processed().whenComplete((ignored,failure)->{if(failure!=null)result.completeExceptionally(failure);});return result;
    }
    /** Existing REST transitions use the same actor as native WS commands. */
    public <T> T serial(Authentication caller,long lesson,Supplier<T> action) {
        return serial(caller,lesson,()->{},action);
    }
    public <T> T serial(Authentication caller,long lesson,Runnable session,Supplier<T> action) {
        session.run();transactions.snapshot(caller,lesson);var result=new CompletableFuture<T>();
        SessionQueue.Submission<Job> submission;
        synchronized(this) {
        var node=node(lesson);
        submission=queue.submit(node.key,new Job(ignored->{try {session.run();sync(node,lesson);var reply=action.get();sync(node,lesson);result.complete(reply);broadcast(lesson);}catch(RuntimeException failure){result.completeExceptionally(failure);}}));
        }
        submission.processed().whenComplete((ignored,failure)->{if(failure!=null)result.completeExceptionally(failure);});
        try {return result.join();}catch(CompletionException failure){if(failure.getCause() instanceof RuntimeException cause)throw cause;throw failure;}
    }
    @org.springframework.scheduling.annotation.Scheduled(fixedDelay=60000)
    public synchronized void cleanup() {queue.cleanup();nodes.entrySet().removeIf(e->queue.evictIdle(e.getValue().key));}
    @EventListener(ApplicationReadyEvent.class) public void recover() {for(long lesson:store.openLessons())node(lesson);}
    @TransactionalEventListener public void changed(LessonChanged event) {
        Node node; synchronized(this){node=nodes.get(event.lessonId());}
        if(node!=null)try {queue.submitControl(node.key,new Job(ignored->refresh(node,event.lessonId())));}catch(RejectedExecutionException stopped){/* Recovery reloads persisted state. */}
    }
    @TransactionalEventListener public void deleted(LessonDeleted event) {
        Node node; synchronized(this){node=nodes.get(event.lessonId());}
        if(node!=null)queue.submitControl(node.key,new Job(ignored->{node.window=null;queue.stopTimer(node.key);queue.retire(node.key);}));
        for(var socket:sockets.lessonSubscriptions().getOrDefault(event.lessonId(),List.of())) {
            sockets.unsubscribeLesson(socket,event.lessonId());
            sockets.send(socket,Map.of("v",1,"kind","EVENT","type","LESSON_DELETED","classId",event.classId(),"target",LessonCommand.Target.lesson(event.lessonId())));
        }
        for(long sibling:store.inClass(event.classId()))if(sockets.lessonSubscriptions().containsKey(sibling))broadcast(sibling);
    }
    @TransactionalEventListener public void membership(vn.edu.multigame.room.service.ClassMembershipChanged event) {
        sockets.lessonSubscriptions().keySet().forEach(this::broadcast);
    }
    private boolean allowed(WebSocketSession socket,long lesson) {
        try {sockets.requireCurrent(socket);return true;}catch(RuntimeException denied){return false;}
    }
    public void broadcast(long lesson) {
        for(var socket:sockets.lessonSubscriptions().getOrDefault(lesson,List.of())) {
            try {
                var snapshot=transactions.snapshot((Authentication)socket.getPrincipal(),lesson);
                sockets.send(socket,Map.of("v",1,"kind","EVENT","type","LESSON_UPDATED","target",LessonCommand.Target.lesson(lesson),"eventId",UUID.randomUUID().toString(),"payload",snapshot),()->allowed(socket,lesson) && transactions.canDeliver((Authentication)socket.getPrincipal(),lesson,snapshot));
            } catch(RuntimeException denied) {
                sockets.unsubscribeLesson(socket,lesson);
                sockets.send(socket,Map.of("v",1,"kind","EVENT","type","LESSON_ACCESS_REVOKED","target",LessonCommand.Target.lesson(lesson)));
            }
        }
    }
    @PreDestroy @Override public void close() {queue.close();}
}
