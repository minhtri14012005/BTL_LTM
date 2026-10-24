import { EmptyState, ErrorState, Loading } from "@/components/feedback";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Table } from "@/components/ui/table";
import { useResource } from "@/hooks/use-resource";
import { api, post } from "@/lib/api";
import { realtime } from "@/lib/session";
import { useEffect, useRef, useState } from "react";
import { type Board, type Range, type RankConfig } from "./teaching-api";
import { FormError, Notice, useTeachingAction } from "./teaching-shared";
import { RankRanges } from "./rank-ranges";
export function rankError(ranges: Range[]) {
  const invalid = ranges.some(
    (r, i) =>
      !r.name.trim() ||
      !Number.isInteger(r.from) ||
      r.from < 1 ||
      r.from > 2147483647 ||
      (r.to === null
        ? i !== ranges.length - 1
        : !Number.isInteger(r.to) || r.to < r.from || r.to > 2147483647) ||
      (i > 0 && (ranges[i - 1].to === null || ranges[i - 1].to! >= r.from)),
  );
  return invalid
    ? "Tên Rank và khoảng hạng chưa hợp lệ. Các mức phải đúng thứ tự, không chồng lấn; chỉ mức cuối được không giới hạn."
    : "";
}

export function RankPanel({
  classId,
  view = "board",
}: {
  classId: number;
  view?: "board" | "settings";
}) {
  const [updatedBoard, setUpdatedBoard] = useState<Board>();
  const sequence = useRef(0);
  useEffect(() => {
    setUpdatedBoard(undefined);
    return () => {
      sequence.current++;
    };
  }, [classId]);
  const resource = useResource(
    async (signal) => {
      const [board, config] = await Promise.all([
        api<Board>(`/api/learning/classes/${classId}/leaderboard`, { signal }),
        api<RankConfig>(`/api/teaching/classes/${classId}/rank-config`, {
          signal,
        }),
      ]);
      return { board, config };
    },
    [classId],
  );
  useEffect(
    () =>
      realtime.listen((e) => {
        if (e.type === "SCORES_UPDATED") {
          const request = ++sequence.current;
          void api<Board>(`/api/learning/classes/${classId}/leaderboard`)
            .then((board) => {
              if (request === sequence.current) setUpdatedBoard(board);
            })
            .catch(() => {});
        }
      }),
    [classId, resource.reload],
  );
  return resource.loading ? (
    <Loading />
  ) : resource.error ? (
    <ErrorState message={resource.error} retry={resource.reload} />
  ) : resource.data ? (
    <RankEditor
      key={resource.data.config.revision}
      classId={classId}
      view={view}
      board={updatedBoard || resource.data.board}
      config={resource.data.config}
      refresh={() => {
        sequence.current++;
        setUpdatedBoard(undefined);
        resource.reload();
      }}
    />
  ) : null;
}
function RankEditor({
  classId,
  view,
  board,
  config,
  refresh,
}: {
  classId: number;
  view: "board" | "settings";
  board: Board;
  config: RankConfig;
  refresh: () => void;
}) {
  const [ranges, setRanges] = useState<Range[]>(structuredClone(config.ranges)),
    [share, setShare] = useState(""),
    [code, setCode] = useState("");
  const action = useTeachingAction();
  const dirty = JSON.stringify(ranges) !== JSON.stringify(config.ranges);
  return (
    <div className="space-y-6">
      <Notice>
        Thứ hạng số và mức Rank là hai cột riêng. Server xét vị trí đầu nhóm
        đồng điểm để chọn cùng mức cao hơn ở ranh giới.
      </Notice>
      {view === "board" && (
        <>
          <div className="flex flex-wrap justify-between gap-3">
            <h2 className="text-xl font-semibold">Bảng điểm lớp</h2>
            <Button variant="outline" onClick={refresh}>
              Làm mới bảng điểm
            </Button>
          </div>
          {!board.students.length ? (
            <EmptyState
              title="Chưa có sinh viên trong bảng điểm"
              description="Sinh viên được duyệt và tham dự sẽ có kết quả theo buổi."
            />
          ) : (
            <Card>
              <Table>
                <thead>
                  <tr>
                    <th>Hạng số</th>
                    <th>Mã SV</th>
                    <th>Họ tên</th>
                    <th>Điểm</th>
                    <th>Rank do giảng viên đặt</th>
                  </tr>
                </thead>
                <tbody>
                  {board.students.map((s) => (
                    <tr key={s.userId}>
                      <td>{s.position}</td>
                      <td>{s.maSinhVien}</td>
                      <td>{s.hoTen}</td>
                      <td>{s.score}</td>
                      <td>{s.rank || "Chưa có mức"}</td>
                    </tr>
                  ))}
                </tbody>
              </Table>
            </Card>
          )}
        </>
      )}
      {view === "settings" && (
        <Card>
          <CardHeader>
            <CardTitle>Cấu hình mức Rank</CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            <RankRanges
              ranges={ranges}
              setRanges={setRanges}
              pending={action.pending}
            />
            <div className="flex flex-wrap gap-3">
              <Button
                disabled={action.pending}
                onClick={() => {
                  const error = rankError(ranges);
                  if (error) {
                    action.setError(error);
                    return;
                  }
                  void action.run(async () => {
                    await api(`/api/teaching/classes/${classId}/rank-config`, {
                      method: "PUT",
                      body: JSON.stringify({
                        revision: config.revision,
                        ranges,
                      }),
                    });
                    refresh();
                  }, "Đã lưu cấu hình Rank.");
                }}
              >
                Lưu Rank
              </Button>
              <Button
                variant="outline"
                disabled={action.pending || dirty}
                onClick={() => {
                  void action.run(async () => {
                    setShare(
                      (
                        await post<{ code: string }>(
                          `/api/teaching/classes/${classId}/rank-config/share`,
                          { revision: config.revision },
                        )
                      ).code,
                    );
                  });
                }}
              >
                Tạo mã chia sẻ
              </Button>
            </div>
            {share && (
              <p className="break-all rounded-xl bg-accent p-3">
                Mã setting: <strong>{share}</strong>
              </p>
            )}
            <form
              className="flex flex-col gap-3 sm:flex-row"
              onSubmit={(e) => {
                e.preventDefault();
                void action.run(async () => {
                  await post(
                    `/api/teaching/classes/${classId}/rank-config/import`,
                    { revision: config.revision, code: code.trim() },
                  );
                  refresh();
                }, "Đã nhập cấu hình độc lập.");
              }}
            >
              <Input
                aria-label="Mã setting để import"
                required
                value={code}
                disabled={action.pending}
                onChange={(e) => setCode(e.target.value)}
              />
              <Button
                variant="outline"
                type="submit"
                disabled={action.pending || !code.trim() || dirty}
              >
                Import Rank
              </Button>
            </form>
            {dirty && (
              <p className="text-sm text-muted-foreground">
                Hãy lưu các chỉnh sửa trước khi chia sẻ hoặc import.
              </p>
            )}
            <FormError message={action.error} />
          </CardContent>
        </Card>
      )}
    </div>
  );
}
