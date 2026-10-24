import { ServerWheel } from "@/components/server-wheel";
import { type Game, type Snapshot } from "@/features/teaching/teaching-api";
import { useSession } from "@/lib/session";
import { Notice } from "./student-shared";
export function StudentWheel({
  snapshot,
  game,
}: {
  snapshot: Snapshot;
  game: Game;
}) {
  const { user } = useSession();
  const wheel = snapshot.wheel;
  return (
    <div className="space-y-4">
      <ServerWheel wheel={wheel} serverTimeMs={snapshot.serverTimeMs} />
      {wheel?.selectedForChallenge ? (
        <div
          role="status"
          className="rounded-2xl border-2 border-primary bg-accent p-5 font-semibold"
        >
          Giảng viên đã chọn bạn để thực hiện vòng quay.{" "}
          {game.status === "COMPLETED"
            ? `Điểm đã chốt: ${game.score}`
            : "Thực hiện thử thách chung và chờ giảng viên xác nhận điểm."}
        </div>
      ) : wheel?.pool.includes(user!.id) &&
        wheel.challengeIndex !== undefined ? (
        <Notice>
          Bạn không bị chọn và được Server ghi nhận mức tối đa: {game.score}/
          {game.budget} điểm.
        </Notice>
      ) : game.participating && game.status !== "COMPLETED" ? (
        <p>
          Đã tham gia. Chờ Server xác nhận tập quay và lựa chọn của giảng viên.
          Điểm hiện tại: {game.score}.
        </p>
      ) : (
        <p>
          Bạn chưa tham gia tập quay hợp lệ. Điểm Server ghi nhận: {game.score}.
        </p>
      )}
    </div>
  );
}
