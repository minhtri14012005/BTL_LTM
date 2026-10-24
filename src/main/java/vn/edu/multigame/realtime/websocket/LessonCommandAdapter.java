package vn.edu.multigame.realtime.websocket;

import java.util.*;
import java.util.concurrent.*;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.web.socket.*;
import vn.edu.multigame.lesson.service.*;
import vn.edu.multigame.auth.service.AuthFailure;
import vn.edu.multigame.realtime.session.LessonRuntime;
import vn.edu.multigame.realtime.connection.*;
import vn.edu.multigame.realtime.message.command.LessonCommand;

@Component @Profile("mysql")
public class LessonCommandAdapter {
    private final LessonCommandParser parser;
    private final LessonRuntime runtime;
    private final AuthenticatedSocketRegistry sockets;
    private final LessonRealtimeTransactions transactions;
    public LessonCommandAdapter(LessonCommandParser parser,LessonRuntime runtime,AuthenticatedSocketRegistry sockets,LessonRealtimeTransactions transactions) {
        this.parser=parser;this.runtime=runtime;this.sockets=sockets;this.transactions=transactions;
    }
    public boolean handles(String text) {return parser.handles(text);}
    public void text(WebSocketSession socket,String text,Runnable session) {
        LessonCommand command;
        try {command=parser.read(text);}catch(RuntimeException failure){error(socket,null,failure);return;}
        var auth=(Authentication)socket.getPrincipal();
        try {
            sockets.requireLessonSubscriptionCapacity(socket,command.target().id());
            runtime.command(auth,command,session).whenComplete((result,failure)->{
                if(failure!=null){error(socket,command,failure);return;}
                try {
                    session.run();if(command.type().equals("UNSUBSCRIBE_LESSON"))sockets.unsubscribeLesson(socket,command.target().id());else sockets.subscribeLesson(socket,command.target().id());
                    sockets.send(socket,Map.of("v",1,"kind","ACK","requestId",command.requestId(),"type",command.type(),"target",command.target(),"sequence",result.sequence(),"receivedAtMs",result.receivedAtMs(),"replay",result.replay(),"payload",result.snapshot()),()->{
                        try {session.run();return transactions.canDeliver(auth,command.target().id(),result.snapshot());}catch(RuntimeException denied){return false;}
                    });
                } catch(RuntimeException denied){error(socket,command,denied);}
            });
        } catch(RuntimeException failure){error(socket,command,failure);}
    }
    private void error(WebSocketSession socket,LessonCommand command,Throwable failure) {
        while(failure instanceof CompletionException && failure.getCause()!=null)failure=failure.getCause();
        if(failure instanceof SocketReplaced){sockets.close(socket,new CloseStatus(4002,"SESSION_REPLACED"));return;}
        if(failure instanceof AuthFailure expired && expired.status()==org.springframework.http.HttpStatus.UNAUTHORIZED){sockets.close(socket,new CloseStatus(4001,expired.socketReason()));return;}
        String code=failure instanceof AuthFailure denied?denied.code():failure instanceof LessonFailure || failure instanceof vn.edu.multigame.room.service.RoomFailure?failure.getMessage():failure instanceof RejectedExecutionException?"RUNTIME_BUSY":"COMMAND_UNAVAILABLE";
        var message=new LinkedHashMap<String,Object>();message.put("v",1);message.put("kind","ERROR");message.put("code",code);
        if(command!=null){message.put("requestId",command.requestId());message.put("target",command.target());}
        sockets.send(socket,message);
    }
}
