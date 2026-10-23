import { useEffect, useRef, useState } from "react";
/** The same server-timed visual is used by teachers and students. No client random result. */
export function ServerWheel({
  wheel,
  serverTimeMs,
  showCells = true,
}: {
  wheel: {
    cells: string[];
    challengeIndex?: number;
    spinStartedAtMs?: number;
    spinEndsAtMs?: number;
  } | null;
  serverTimeMs: number;
  showCells?: boolean;
}) {
  const disk = useRef<HTMLDivElement>(null);
  const [spinning, setSpinning] = useState(false);
  const angle =
    wheel?.challengeIndex === undefined
      ? 0
      : 1800 - ((wheel.challengeIndex + 0.5) * 360) / wheel.cells.length;
  const start = wheel?.spinStartedAtMs || 0,
    end = wheel?.spinEndsAtMs || 0;
  useEffect(() => {
    const remaining = Math.max(0, end - serverTimeMs);
    setSpinning(remaining > 0);
    const timer = setTimeout(() => setSpinning(false), remaining);
    let animation: Animation | undefined;
    if (
      remaining > 0 &&
      end > start &&
      disk.current?.animate &&
      !window.matchMedia?.("(prefers-reduced-motion: reduce)").matches
    ) {
      animation = disk.current.animate(
        [{ transform: "rotate(0deg)" }, { transform: `rotate(${angle}deg)` }],
        {
          duration: end - start,
          fill: "forwards",
          easing: "cubic-bezier(.16,.65,.25,1)",
        },
      );
      animation.currentTime = Math.max(0, serverTimeMs - start);
    }
    return () => {
      clearTimeout(timer);
      animation?.cancel();
    };
  }, [angle, start, end, serverTimeMs]);
  return (
    <div className="space-y-4">
      <div
        className="wheel-stage"
        aria-label={spinning ? "Vòng quay đang quay" : "Kết quả vòng quay"}
      >
        <div className="wheel-pointer" />
        <div
          ref={disk}
          className="teacher-wheel"
          style={{
            background: wheel?.cells.length
              ? `conic-gradient(${wheel.cells.map((_, i) => `${["#dcd3f6", "#d4e5fa", "#f6dce9", "#d5eee4"][i % 4]} ${(i * 360) / wheel.cells.length}deg ${((i + 1) * 360) / wheel.cells.length}deg`).join(",")})`
              : "var(--gradient-welcome)",
            transform: `rotate(${angle}deg)`,
            transitionDuration: "0ms",
          }}
        />
        <span className="wheel-center">
          {spinning
            ? "Đang quay…"
            : wheel?.challengeIndex !== undefined
              ? "Server đã chọn"
              : "Vòng quay"}
        </span>
      </div>
      {wheel?.challengeIndex !== undefined && (
        <p
          className="rounded-xl bg-accent p-4 text-center text-lg font-semibold"
          data-challenge-index={wheel.challengeIndex}
        >
          Thử thách chung: {wheel.cells[wheel.challengeIndex]}
        </p>
      )}
      {wheel && showCells && (
        <details className="rounded-xl border p-3">
          <summary className="cursor-pointer font-medium">
            Các ô thử thách đã lưu
          </summary>
          <ol className="mt-3 list-inside list-decimal space-y-2 text-sm">
            {wheel.cells.map((c, i) => (
              <li key={i}>{c}</li>
            ))}
          </ol>
        </details>
      )}
    </div>
  );
}
