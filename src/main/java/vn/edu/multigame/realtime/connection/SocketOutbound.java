package vn.edu.multigame.realtime.connection;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.concurrent.Executor;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.springframework.web.socket.*;

/** One bounded FIFO and at most one writer per socket. No socket I/O under the queue lock. */
final class SocketOutbound {
    static final int BYTE_LIMIT=262144, MESSAGE_LIMIT=1024;
    static final long SEND_TIMEOUT_MS=5000;
    private final WebSocketSession socket;
    private final Executor writers;
    private final ScheduledExecutorService watchdog;
    private final Consumer<CloseStatus> detached;
    private final ArrayDeque<TextMessage> pending=new ArrayDeque<>();
    private int retainedBytes;
    private boolean running,stopped,finishing;
    private TextMessage inFlight;
    private Thread writer;
    private ScheduledFuture<?> timeout;
    private CloseStatus closeAfter;
    private final CompletableFuture<Void> closed=new CompletableFuture<>();

    SocketOutbound(WebSocketSession socket,Executor writers,ScheduledExecutorService watchdog,Consumer<CloseStatus> detached) {
        this.socket=socket;this.writers=writers;this.watchdog=watchdog;this.detached=detached;
    }
    void send(TextMessage message) {
        CloseStatus failure=null;
        synchronized(this) {
            if(stopped || finishing)return;
            if(message.getPayloadLength()>BYTE_LIMIT-retainedBytes || pending.size()+(inFlight==null?0:1)>=MESSAGE_LIMIT)
                failure=new CloseStatus(1011,"SEND_BUFFER_LIMIT");
            else {
                pending.addLast(message);retainedBytes+=message.getPayloadLength();
                try {start();}catch(RuntimeException rejected){failure=new CloseStatus(1011,"SEND_UNAVAILABLE");}
            }
        }
        if(failure!=null)abort(failure);
    }
    /** Replacement notification follows any in-flight frame; discard unsent frames of the old generation. */
    void finish(TextMessage terminal,CloseStatus status) {
        CloseStatus failure=null;
        synchronized(this) {
            if(stopped)return;
            pending.clear();retainedBytes=inFlight==null?0:inFlight.getPayloadLength();
            finishing=true;closeAfter=status;
            if(terminal.getPayloadLength()>BYTE_LIMIT-retainedBytes)failure=new CloseStatus(1011,"SEND_BUFFER_LIMIT");
            else {
                pending.addLast(terminal);retainedBytes+=terminal.getPayloadLength();
                try {start();}catch(RuntimeException rejected){failure=new CloseStatus(1011,"SEND_UNAVAILABLE");}
            }
        }
        if(failure!=null)abort(failure);
    }
    private void start() {
        if(!running){running=true;writers.execute(this::drain);}
    }
    private void drain() {
        while(true) {
            TextMessage message;
            CloseStatus terminal=null;
            boolean rejected=false;
            synchronized(this) {
                if(stopped){running=false;return;}
                message=pending.pollFirst();
                if(message==null) {
                    running=false;writer=null;
                    if(finishing){stopped=true;terminal=closeAfter;}
                } else {
                    inFlight=message;writer=Thread.currentThread();
                    try {timeout=watchdog.schedule(()->timedOut(message),SEND_TIMEOUT_MS,TimeUnit.MILLISECONDS);}
                    catch(RuntimeException failure){rejected=true;}
                }
            }
            if(rejected){abort(new CloseStatus(1011,"SEND_UNAVAILABLE"));return;}
            if(message==null){if(terminal!=null)closeAndComplete(terminal);return;}
            try {
                if(!socket.isOpen())throw new IOException("Socket closed");
                socket.sendMessage(message);
            } catch(IOException | RuntimeException failure){abort(new CloseStatus(1011,"SEND_UNAVAILABLE"));return;}
            synchronized(this) {
                if(stopped)return;
                timeout.cancel(false);timeout=null;inFlight=null;retainedBytes-=message.getPayloadLength();
            }
        }
    }
    private void timedOut(TextMessage message) {
        synchronized(this) {
            if(stopped || inFlight!=message)return; // A stale timeout cannot terminate a later send.
            // Claim the timeout before a completed writer can advance to another frame.
            stop();
        }
        try {detached.accept(new CloseStatus(1011,"SEND_TIMEOUT"));}
        finally {closeAsync(new CloseStatus(1011,"SEND_TIMEOUT"));}
    }
    CompletableFuture<Void> abort(CloseStatus status) {
        synchronized(this){if(stopped)return closed;stop();}
        try {detached.accept(status);}finally {closeAsync(status);}
        return closed;
    }
    synchronized void stop() {
        stopped=true;pending.clear();retainedBytes=0;
        if(timeout!=null){timeout.cancel(false);timeout=null;}
        if(writer!=null && writer!=Thread.currentThread())writer.interrupt();
    }
    private void closeAsync(CloseStatus status) {
        try {writers.execute(()->closeAndComplete(status));}catch(RuntimeException shuttingDown){closed.complete(null);}
    }
    private void closeAndComplete(CloseStatus status) {
        try {close(socket,status);}finally {closed.complete(null);}
    }
    static void close(WebSocketSession socket,CloseStatus status) {
        try {socket.close(status);}catch(IOException | RuntimeException ignored){/* Already unavailable. */}
    }
}
