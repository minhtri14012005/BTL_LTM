package vn.edu.quiz.realtime.websocket;

import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;
import vn.edu.quiz.auth.service.*;
import vn.edu.quiz.quiz.service.QuizFailure;
import vn.edu.quiz.realtime.connection.AuthenticatedSocketRegistry;
import vn.edu.quiz.realtime.message.command.WaitingRoomCommand;
import vn.edu.quiz.realtime.message.common.*;
import vn.edu.quiz.realtime.message.event.RoomEvent;
import vn.edu.quiz.realtime.session.*;
import vn.edu.quiz.room.enums.*;
import vn.edu.quiz.room.service.*;

@Component @Profile("mysql")
public class WaitingRoomAdapter {
    private final WaitingRoomCommandParser parser;
    private final RoomOperations operations;
    private final RoomService rooms;
    private final AuthService users;
    private final AuthenticatedSocketRegistry sockets;
    private final GameOperations games;
    private final GameCommandAdapter gameplay;
    public WaitingRoomAdapter(WaitingRoomCommandParser parser,RoomOperations operations,RoomService rooms,AuthService users,AuthenticatedSocketRegistry sockets,GameOperations games,GameCommandAdapter gameplay) {
        this.parser=parser; this.operations=operations; this.rooms=rooms; this.users=users; this.sockets=sockets;
        this.games=games;
        this.gameplay=gameplay;
    }
    public void text(WebSocketSession socket,String text,Runnable validateConnection) {
        WaitingRoomCommand command=null;
        try {
            if(parser.isGameplay(text)) { gameplay.text(socket,text,validateConnection); return; }
            command=parser.read(text); var c=command; long id=c.target().id();
            Authentication auth=socket.getPrincipal() instanceof Authentication a?a:null;
            long uid=users.requireActiveUser(auth).getId(); String code=c.payload().path("roomCode").asText(null);
            if(c.type().equals("START_GAME")) {
                rooms.authorizeReceipt(uid,id,"START_GAME",null); sockets.subscribe(socket,id);
                var response=games.start(auth,id,new vn.edu.quiz.game.dto.request.StartGameRequest(c.requestId(),c.payload().path("revision").asLong(),c.payload().path("questionCount").asInt()),validateConnection);
                sockets.send(socket,new RoomAck(1,"ACK",c.requestId(),c.type(),c.target(),"ACCEPTED",response.room().revision(),response.serverTimeMs(),response)); return;
            }
            if(c.type().equals("JOIN_ROOM") || c.type().equals("SUBSCRIBE_ROOM")) sockets.requireSubscriptionCapacity(socket,id);
            operations.execute(auth,id,c.requestId(),c.type(),c.payload(),code,validateConnection,()->switch(c.type()) {
                case "JOIN_ROOM" -> rooms.join(uid,id,code,Participation.valueOf(c.payload().get("participation").asText()));
                case "LEAVE_ROOM" -> rooms.leave(uid,id);
                case "REMOVE_MEMBER" -> rooms.remove(uid,id,c.payload().get("userId").asLong());
                case "SUBSCRIBE_ROOM" -> new RoomMutation(rooms.get(uid,id),false);
                default -> throw RoomFailure.invalid();
            },(receipt,replayed)->{
                var current=receipt.snapshot();
                boolean subscribe=c.type().equals("JOIN_ROOM") || c.type().equals("SUBSCRIBE_ROOM");
                if(subscribe && replayed) current=rooms.isJoined(uid,id)?rooms.get(uid,id):null;
                if(subscribe && current!=null && current.status()!=RoomStatus.CLOSED) sockets.subscribe(socket,id);
                if(c.type().equals("LEAVE_ROOM")) sockets.unsubscribe(socket,id);
                Object payload=c.type().equals("LEAVE_ROOM")?Map.of("left",true):receipt.snapshot();
                sockets.send(socket,new RoomAck(1,"ACK",c.requestId(),c.type(),c.target(),"ACCEPTED",receipt.snapshot().revision(),receipt.serverTimeMs(),payload));
                if(subscribe && replayed && current!=null) sockets.send(socket,new RoomEvent(1,"EVENT","ROOM_UPDATED",c.target(),
                        UUID.randomUUID().toString(),current.revision(),System.currentTimeMillis(),current));
            });
        } catch(RoomFailure e) { error(socket,command,e.code(),e.retryable()); }
        catch(QuizFailure e) { error(socket,command,e.code(),false); }
        catch(vn.edu.quiz.game.service.GameFailure e) { error(socket,command,e.getMessage(),e.status()==org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE); }
        // Authentication failure is handled by the connection handler, including after queue admission.
    }
    private void error(WebSocketSession socket,WaitingRoomCommand command,String code,boolean retryable) {
        sockets.send(socket,new RoomWireError(1,"ERROR",command==null?null:command.requestId(),command==null?null:command.target(),
                code,code,retryable,System.currentTimeMillis()));
    }
}
