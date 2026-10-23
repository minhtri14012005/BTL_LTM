import { requestId as newRequestId } from "./request-id";
export type ConnectionState =
  "closed" | "connecting" | "connected" | "reconnecting" | "replaced";
export interface Envelope {
  kind: string;
  type: string;
  requestId?: string;
  code?: string;
  message?: string;
  target?: { kind: string; id: number };
  payload?: unknown;
}
type Pending = {
  resolve: (e: Envelope) => void;
  reject: (e: Error) => void;
  timer: ReturnType<typeof setTimeout>;
};

// Owned by the authenticated app session, never by a route or React render.
export class RealtimeClient {
  private socket?: WebSocket;
  private user?: number;
  private retry?: ReturnType<typeof setTimeout>;
  private attempts = 0;
  private pending = new Map<string, Pending>();
  private lessons = new Map<number, Set<(event: Envelope) => void>>();
  private listeners = new Set<() => void>();
  private events = new Set<(event: Envelope) => void>();
  private state: ConnectionState = "closed";
  constructor(private expired: () => void = () => {}) {}
  subscribe = (listener: () => void) => {
    this.listeners.add(listener);
    return () => {
      this.listeners.delete(listener);
    };
  };
  getSnapshot = () => this.state;
  private set(state: ConnectionState) {
    this.state = state;
    this.listeners.forEach((l) => l());
  }
  listen(listener: (event: Envelope) => void) {
    this.events.add(listener);
    return () => {
      this.events.delete(listener);
    };
  }
  start(userId: number) {
    if (this.user === userId) return;
    this.stop();
    this.user = userId;
    this.connect();
  }
  stop() {
    this.user = undefined;
    clearTimeout(this.retry);
    this.retry = undefined;
    const socket = this.socket;
    this.socket = undefined;
    socket?.close(1000, "Session ended");
    this.failPending();
    this.lessons.clear();
    this.attempts = 0;
    this.set("closed");
  }
  private failPending() {
    this.pending.forEach((p) => {
      clearTimeout(p.timer);
      p.reject(new Error("Mất kết nối. Tải lại trạng thái trước khi thử lại."));
    });
    this.pending.clear();
  }
  private connect() {
    if (this.user === undefined || this.socket) return;
    this.set(this.attempts ? "reconnecting" : "connecting");
    const url = new URL("/ws", window.location.href);
    url.protocol = url.protocol === "https:" ? "wss:" : "ws:";
    const socket = new WebSocket(url);
    this.socket = socket;
    socket.onmessage = ({ data }) => {
      if (this.socket !== socket) return;
      let event: Envelope;
      try {
        event = JSON.parse(data);
      } catch {
        return;
      }
      if (event.type === "AUTH_READY") {
        this.attempts = 0;
        this.set("connected");
        this.lessons.forEach((_, id) => {
          void this.command("RECONNECT_LESSON", id).catch(() => {});
        });
      }
      if (event.requestId && (event.kind === "ACK" || event.kind === "ERROR")) {
        const request = this.pending.get(event.requestId);
        if (request) {
          clearTimeout(request.timer);
          this.pending.delete(event.requestId);
          if (event.kind === "ERROR")
            request.reject(
              new Error(event.message || event.code || "Yêu cầu bị từ chối."),
            );
          else request.resolve(event);
        }
      }
      this.dispatch(event);
      if (event.type === "LESSON_ACCESS_REVOKED" && event.target)
        this.lessons.delete(event.target.id);
    };
    socket.onclose = (event) => {
      if (this.socket !== socket) return;
      this.socket = undefined;
      this.failPending();
      if (event.code === 4002) {
        this.set("replaced");
        return;
      }
      if (event.code === 4001 || event.code === 1008) {
        this.set("closed");
        this.expired();
        return;
      }
      if (this.user !== undefined) {
        this.set("reconnecting");
        this.retry = setTimeout(
          () => this.connect(),
          Math.min(1000 * 2 ** this.attempts++, 15000),
        );
      }
    };
    socket.onerror = () => {
      socket.close();
    };
  }
  private dispatch(event: Envelope) {
    this.events.forEach((l) => l(event));
    if (event.target?.kind === "LESSON")
      this.lessons.get(event.target.id)?.forEach((l) => l(event));
  }
  command(
    type: string,
    lessonId: number,
    payload: object = {},
    requestId: string = newRequestId(),
  ): Promise<Envelope> {
    if (
      this.state !== "connected" ||
      this.socket?.readyState !== WebSocket.OPEN
    )
      return Promise.reject(new Error("Đang kết nối lại. Vui lòng chờ."));
    if (this.pending.has(requestId))
      return Promise.reject(new Error("Yêu cầu đang được xử lý."));
    return new Promise((resolve, reject) => {
      const timer = setTimeout(() => {
        this.pending.delete(requestId);
        reject(
          new Error(
            "Chưa nhận xác nhận. Tải lại trạng thái trước khi thử lại.",
          ),
        );
      }, 15000);
      this.pending.set(requestId, { resolve, reject, timer });
      try {
        this.socket!.send(
          JSON.stringify({
            v: 1,
            kind: "COMMAND",
            requestId,
            type,
            target: { kind: "LESSON", id: lessonId },
            payload,
          }),
        );
      } catch {
        clearTimeout(timer);
        this.pending.delete(requestId);
        reject(new Error("Không gửi được yêu cầu."));
      }
    });
  }
  watchLesson(id: number, listener: (event: Envelope) => void) {
    let watchers = this.lessons.get(id);
    if (!watchers) {
      watchers = new Set();
      this.lessons.set(id, watchers);
    }
    watchers.add(listener);
    if (this.state === "connected")
      void this.command("RECONNECT_LESSON", id).catch(() => {});
    return () => {
      watchers.delete(listener);
      if (!watchers.size) {
        this.lessons.delete(id);
        if (this.state === "connected")
          void this.command("UNSUBSCRIBE_LESSON", id).catch(() => {});
      }
    };
  }
}
