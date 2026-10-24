import { PageHeader } from "@/components/page-layout";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { api } from "@/lib/api";
import { realtime, useConnection } from "@/lib/session";
import { cn } from "@/lib/utils";
import { useEffect, useRef, useState, type ReactNode } from "react";
import { Link, useNavigate } from "react-router-dom";
import { toast } from "sonner";
import { teachingError, type Snapshot } from "./teaching-api";
export { FormError, NativeSelect } from "@/features/admin/admin-shared";
export function TeachingFrame({
  title,
  children,
  classId,
}: {
  title: string;
  children: ReactNode;
  classId?: number;
}) {
  return (
    <div className="space-y-6">
      {classId && (
        <Button asChild variant="outline">
          <Link to={`/app/giang_vien/classes/${classId}`}>Về lớp học</Link>
        </Button>
      )}
      <PageHeader title={title} />
      {children}
    </div>
  );
}
export function Textarea({
  className,
  ...props
}: React.ComponentProps<"textarea">) {
  return (
    <textarea
      className={cn(
        "min-h-28 w-full min-w-0 rounded-xl border bg-card p-3 text-base outline-none focus-visible:ring-2 focus-visible:ring-ring disabled:opacity-60",
        className,
      )}
      {...props}
    />
  );
}
export function useTeachingAction() {
  const [pending, setPending] = useState(false),
    [error, setError] = useState("");
  const lock = useRef(false);
  async function run(work: () => Promise<unknown>, success?: string) {
    if (lock.current) return false;
    lock.current = true;
    setPending(true);
    setError("");
    try {
      await work();
      if (success) toast.success(success);
      return true;
    } catch (e) {
      setError(teachingError(e));
      return false;
    } finally {
      lock.current = false;
      setPending(false);
    }
  }
  return { pending, error, run, setError };
}
export function useTeachingSnapshot(
  id: number,
  scope: "teaching" | "student" = "teaching",
  position?: number,
) {
  const navigate = useNavigate();
  const connection = useConnection();
  const [data, setData] = useState<Snapshot>(),
    [error, setError] = useState(""),
    [fresh, setFresh] = useState(false);
  const current = useRef(id);
  current.current = id;
  const accept = (snapshot: Snapshot) => {
    if (snapshot?.lesson?.id !== current.current) return;
    setData((old) =>
      !old ||
      snapshot.lesson.revision > old.lesson.revision ||
      (snapshot.lesson.revision === old.lesson.revision &&
        snapshot.serverTimeMs >= old.serverTimeMs)
        ? snapshot
        : old,
    );
    setFresh(true);
    setError("");
  };
  async function refresh() {
    try {
      const s = await api<Snapshot>(
        `/api/${scope}/lessons/${id}${scope === "student" && position ? `/games/${position}` : ""}/snapshot`,
      );
      accept(s);
    } catch (e) {
      setFresh(false);
      setError(teachingError(e));
    }
  }
  useEffect(() => {
    setData(undefined);
    setFresh(false);
    void refresh();
    return realtime.watchLesson(id, (event) => {
      if (event.type === "LESSON_DELETED") {
        setData(undefined);
        setFresh(false);
        toast.info("Buổi học đã được giảng viên xóa.");
        navigate(
          `/app/${scope === "teaching" ? "giang_vien" : "sinh_vien"}/classes/${event.classId}${scope === "teaching" ? "/lessons" : ""}`,
          { replace: true },
        );
      } else if (event.type === "LESSON_ACCESS_REVOKED") {
        setData(undefined);
        setFresh(false);
        setError("Quyền xem buổi học đã được thu hồi.");
      } else if (event.kind === "ERROR") {
        setFresh(false);
        setError(teachingError(new Error(event.code || event.message)));
      } else if (event.payload) accept(event.payload as Snapshot);
    });
  }, [id, scope, position]);
  useEffect(() => {
    if (connection !== "connected") setFresh(false);
  }, [connection]);
  async function command(type: string, payload: object = {}) {
    try {
      const reply = await realtime.command(type, id, payload);
      accept(reply.payload as Snapshot);
    } catch (e) {
      setFresh(false);
      throw e;
    }
  }
  return {
    data,
    error,
    refresh,
    command,
    disabled: connection !== "connected" || !fresh,
  };
}
export function ServerCountdown({
  serverTimeMs,
  deadlineAtMs,
}: {
  serverTimeMs: number;
  deadlineAtMs: number;
}) {
  const [now, setNow] = useState(serverTimeMs);
  useEffect(() => {
    const start = performance.now();
    setNow(serverTimeMs);
    const timer = setInterval(
      () => setNow(serverTimeMs + performance.now() - start),
      250,
    );
    return () => clearInterval(timer);
  }, [serverTimeMs]);
  return (
    <p role="timer" className="text-xl font-semibold tabular-nums">
      {Math.max(0, Math.ceil((deadlineAtMs - now) / 1000))} giây{" "}
      <span className="text-xs font-normal text-muted-foreground">
        · theo thời gian Server, Server quyết định hạn
      </span>
    </p>
  );
}
export function ScoreInput({
  label,
  value,
  onChange,
  max,
  disabled,
}: {
  label: string;
  value: string;
  onChange: (value: string) => void;
  max: number;
  disabled?: boolean;
}) {
  return (
    <label className="block min-w-0 space-y-1 text-sm">
      <span>{label}</span>
      <Input
        aria-label={label}
        type="number"
        min={0}
        max={max}
        step="0.01"
        required
        value={value}
        disabled={disabled}
        onChange={(e) => onChange(e.target.value)}
      />
    </label>
  );
}
export function Notice({ children }: { children: ReactNode }) {
  return <Card className="bg-accent/40 p-4 text-sm leading-6">{children}</Card>;
}
