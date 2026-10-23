import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { RealtimeClient, type Envelope } from "./realtime";
import { requestId } from "./request-id";

class Socket {
  static OPEN = 1;
  static instances: Socket[] = [];
  readyState = 1;
  onmessage?: (event: { data: string }) => void;
  onclose?: (event: { code: number }) => void;
  onerror?: () => void;
  sent: Envelope[] = [];
  constructor(public url: URL) {
    Socket.instances.push(this);
  }
  send(data: string) {
    this.sent.push(JSON.parse(data));
  }
  close(code = 1000) {
    this.readyState = 3;
    this.onclose?.({ code });
  }
  receive(event: object) {
    this.onmessage?.({ data: JSON.stringify(event) });
  }
}
beforeEach(() => {
  vi.useFakeTimers();
  Socket.instances = [];
  vi.stubGlobal("WebSocket", Socket);
});
afterEach(() => {
  vi.useRealTimers();
  vi.unstubAllGlobals();
});
describe("one app socket", () => {
  it("does not duplicate connections on route remount or repeated starts", () => {
    const client = new RealtimeClient();
    client.start(1);
    client.start(1);
    const unsubscribe = client.subscribe(() => {});
    unsubscribe();
    client.subscribe(() => {});
    client.start(1);
    expect(Socket.instances).toHaveLength(1);
    expect(Socket.instances[0].url.host).toBe(window.location.host);
    expect(Socket.instances[0].url.pathname).toBe("/ws");
    client.stop();
  });
  it("restores watched snapshots after reconnect and resolves server acknowledgements", async () => {
    const client = new RealtimeClient();
    client.start(1);
    const observer = vi.fn();
    const unwatch = client.watchLesson(7, observer);
    let socket = Socket.instances[0];
    socket.receive({ kind: "EVENT", type: "AUTH_READY" });
    expect(socket.sent[0]).toMatchObject({
      type: "RECONNECT_LESSON",
      target: { kind: "LESSON", id: 7 },
      payload: {},
    });
    socket.receive({
      kind: "ACK",
      type: "RECONNECT_LESSON",
      requestId: socket.sent[0].requestId,
      target: { kind: "LESSON", id: 7 },
      payload: { lesson: { id: 7 } },
    });
    await Promise.resolve();
    expect(observer).toHaveBeenCalled();
    socket.close(1006);
    expect(client.getSnapshot()).toBe("reconnecting");
    vi.advanceTimersByTime(1000);
    socket = Socket.instances[1];
    socket.receive({ kind: "EVENT", type: "AUTH_READY" });
    expect(socket.sent[0].type).toBe("RECONNECT_LESSON");
    const command = client.command("ATTEND_LESSON", 7);
    const message = socket.sent[1];
    expect(message.requestId).toMatch(/^[a-f0-9-]{36}$/);
    socket.receive({
      kind: "ACK",
      type: "ATTEND_LESSON",
      requestId: message.requestId,
      payload: { attending: true },
    });
    await expect(command).resolves.toMatchObject({
      payload: { attending: true },
    });
    unwatch();
    client.stop();
  });
  it("rejects in-flight commands on loss and never replays scoring/actions", async () => {
    const client = new RealtimeClient();
    client.start(1);
    const socket = Socket.instances[0];
    socket.receive({ type: "AUTH_READY" });
    const command = client.command("SUBMIT_LESSON_ACTION", 7, {
      token: 1,
      value: "A",
    });
    const rejected = expect(command).rejects.toThrow("Mất kết nối");
    socket.close(1006);
    await rejected;
    vi.advanceTimersByTime(1000);
    Socket.instances[1].receive({ type: "AUTH_READY" });
    expect(Socket.instances[1].sent).toHaveLength(0);
    client.stop();
  });
  it("stops reconnect loops on replacement/revocation and cancels pending retry on logout", () => {
    const expired = vi.fn(),
      client = new RealtimeClient(expired);
    client.start(1);
    Socket.instances[0].close(4002);
    vi.advanceTimersByTime(30_000);
    expect(Socket.instances).toHaveLength(1);
    expect(client.getSnapshot()).toBe("replaced");
    client.stop();
    client.start(1);
    Socket.instances[1].close(4001);
    expect(expired).toHaveBeenCalledOnce();
    vi.advanceTimersByTime(30_000);
    expect(Socket.instances).toHaveLength(2);
    client.stop();
    client.start(1);
    Socket.instances[2].close(1006);
    client.stop();
    vi.advanceTimersByTime(30_000);
    expect(Socket.instances).toHaveLength(3);
  });
  it("notifies a lesson view before discarding its revoked subscription", () => {
    const client = new RealtimeClient();
    client.start(1);
    const listener = vi.fn();
    client.watchLesson(7, listener);
    const socket = Socket.instances[0];
    socket.receive({
      kind: "EVENT",
      type: "LESSON_ACCESS_REVOKED",
      target: { kind: "LESSON", id: 7 },
    });
    expect(listener).toHaveBeenCalledOnce();
    socket.receive({
      kind: "EVENT",
      type: "LESSON_UPDATED",
      target: { kind: "LESSON", id: 7 },
      payload: {},
    });
    expect(listener).toHaveBeenCalledOnce();
    client.stop();
  });
  it("releases subscriptions when navigating through many lessons while preserving one socket", () => {
    const client = new RealtimeClient();
    client.start(1);
    Socket.instances[0].receive({ type: "AUTH_READY" });
    for (let id = 1; id <= 40; id++) {
      const unwatch = client.watchLesson(id, () => {});
      unwatch();
    }
    expect(Socket.instances).toHaveLength(1);
    expect(
      Socket.instances[0].sent.filter((e) => e.type === "UNSUBSCRIBE_LESSON"),
    ).toHaveLength(40);
    client.stop();
  });
  it("creates valid unique UUIDs without secure-context randomUUID", () => {
    const ids = new Set(Array.from({ length: 50 }, requestId));
    expect(ids.size).toBe(50);
    ids.forEach((id) =>
      expect(id).toMatch(
        /^[a-f0-9]{8}-[a-f0-9]{4}-4[a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}$/,
      ),
    );
  });
});
