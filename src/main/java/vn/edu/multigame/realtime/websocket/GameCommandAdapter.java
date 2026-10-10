package vn.edu.multigame.realtime.websocket;

import java.io.IOException;
import java.util.concurrent.*;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import vn.edu.multigame.auth.service.*;
import vn.edu.multigame.game.service.GameFailure;
import vn.edu.multigame.realtime.connection.AuthenticatedSocketRegistry;
import vn.edu.multigame.realtime.message.command.GameCommand;
import vn.edu.multigame.realtime.message.common.GameWireError;
import vn.edu.multigame.realtime.session.GameRuntime;
import vn.edu.multigame.room.service.RoomFailure;

@Component @Profile("mysql")
public class GameCommandAdapter {
    private final GameCommandParser parser;
    private final GameRuntime games;
    private final AuthService users;
    private final AuthenticatedSocketRegistry sockets;
    public GameCommandAdapter(GameCommandParser parser,GameRuntime games,AuthService users,AuthenticatedSocketRegistry sockets) {
        this.parser=parser; this.games=games; this.users=users; this.sockets=sockets;
    }
    public void text(WebSocketSession socket,String text,Runnable validateConnection) {
        if(parser.isReconnect(text)) {
            try { reconnect(socket,parser.reconnect(text),validateConnection); }
            catch(RuntimeException failure) { reject(socket,null,failure); }
            return;
        }
        GameCommand command=null;
        try {
            command=parser.read(text); var parsed=command;
            var auth=socket.getPrincipal() instanceof Authentication a?a:null;
            long userId=users.requireActiveUser(auth).getId();
            var snapshot=games.snapshot(parsed.target().id(),userId);
            games.command(userId,parsed,() -> { validateConnection.run(); users.requireActiveUser(auth); },
                    () -> sockets.subscribe(socket,snapshot.roomId()))
                    .whenComplete((ack,failure) -> { if(failure==null) sockets.send(socket,ack); else reject(socket,parsed,failure); });
        } catch(RuntimeException failure) { reject(socket,command,failure); }
    }
    private void reconnect(WebSocketSession socket,vn.edu.multigame.realtime.message.command.ReconnectCommand command,Runnable validateConnection) {
        var auth=socket.getPrincipal() instanceof Authentication a?a:null;
        long userId=users.requireActiveUser(auth).getId();
        games.reconnect(command.target().id(),userId,() -> {validateConnection.run();users.requireActiveUser(auth);},snapshot -> {
            long generation=sockets.requireCurrent(socket);
            sockets.subscribe(socket,snapshot.roomId());
            sockets.send(socket,new vn.edu.multigame.realtime.message.common.ReconnectAck(1,"ACK",command.requestId(),"RECONNECT",command.target(),
                    snapshot.questionIndex()>0?snapshot.questionIndex():null,"ACCEPTED",snapshot.revision(),snapshot.serverTimeMs(),generation,snapshot));
        }).whenComplete((ignored,failure) -> {
            if(failure!=null) reject(socket,new GameCommand(1,"COMMAND",command.requestId(),"RECONNECT",command.target(),0,null),failure);
        });
    }
    private void reject(WebSocketSession socket,GameCommand command,Throwable failure) {
        while((failure instanceof CompletionException || failure instanceof ExecutionException) && failure.getCause()!=null) failure=failure.getCause();
        if(failure instanceof vn.edu.multigame.realtime.connection.SocketReplaced) { close(socket,4002,"SESSION_REPLACED"); return; }
        if(failure instanceof AuthFailure) { close(socket,4001,"SESSION_EXPIRED"); return; }
        if(failure instanceof org.springframework.dao.DataAccessException) { close(socket,1011,"AUTH_UNAVAILABLE"); return; }
        String code; boolean retryable=false;
        if(failure instanceof GameFailure game) { code=game.getMessage(); retryable=game.status()==org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE; }
        else if(failure instanceof RoomFailure room) { code=room.code(); retryable=room.retryable(); }
        else { code="SERVICE_UNAVAILABLE"; retryable=true; }
        sockets.send(socket,new GameWireError(1,"ERROR",command==null?null:command.requestId(),command==null?null:command.target(),
                command==null || command.questionIndex()==null || command.questionIndex()==0?null:command.questionIndex(),code,code,retryable,System.currentTimeMillis()));
    }
    private void close(WebSocketSession socket,int code,String reason) {
        try { socket.close(new CloseStatus(code,reason)); } catch(IOException ignored) {}
    }
}
