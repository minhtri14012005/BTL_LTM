import { useSyncExternalStore } from "react";
import { toast } from "sonner";
import {
  api,
  post,
  resetCsrf,
  setSessionExpiredHandler,
  ApiError,
} from "./api";
import { RealtimeClient } from "./realtime";
import type { User } from "./types";

type State = { user: User | null; loading: boolean; error: string | null };
let state: State = { user: null, loading: true, error: null };
const listeners = new Set<() => void>();
let boot: Promise<void> | undefined;
let keepalive: ReturnType<typeof setInterval> | undefined;
let epoch = 0;
export const realtime = new RealtimeClient(() => {
  void refreshSession();
});
function set(next: State) {
  state = next;
  listeners.forEach((l) => l());
}
function apply(user: User | null, error: string | null = null) {
  clearInterval(keepalive);
  keepalive = undefined;
  if (user?.profileComplete) realtime.start(user.id);
  else realtime.stop();
  if (user)
    keepalive = setInterval(() => {
      void post("/api/auth/keepalive").catch((error) => {
        if (error instanceof ApiError && [401, 403].includes(error.status))
          void refreshSession();
      });
    }, 60_000);
  set({ user, loading: false, error });
}
setSessionExpiredHandler((message) => {
  const authenticated = !!state.user;
  epoch++;
  resetCsrf();
  apply(null);
  if (authenticated)
    toast.error(
      message || "Phiên đăng nhập đã kết thúc. Vui lòng đăng nhập lại.",
    );
});
export async function refreshSession() {
  const version = ++epoch;
  try {
    const user = await api<User>("/api/auth/me");
    if (version === epoch) apply(user);
  } catch (error) {
    if (version === epoch)
      apply(
        null,
        error instanceof ApiError && error.status === 401
          ? null
          : error instanceof Error
            ? error.message
            : "Chưa tải được phiên đăng nhập.",
      );
  }
}
export function initializeSession() {
  boot ??= refreshSession();
  return boot;
}
export async function login(username: string, password: string) {
  const version = ++epoch;
  const user = await post<User>("/api/auth/login", { username, password });
  resetCsrf();
  if (version === epoch) apply(user);
}
export async function logout() {
  await post("/api/auth/logout");
  epoch++;
  resetCsrf();
  apply(null);
}
export function useSession() {
  return useSyncExternalStore(
    (listener) => {
      listeners.add(listener);
      return () => {
        listeners.delete(listener);
      };
    },
    () => state,
  );
}
export function useConnection() {
  return useSyncExternalStore(realtime.subscribe, realtime.getSnapshot);
}
