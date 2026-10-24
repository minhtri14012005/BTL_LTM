import { EmptyState, ErrorState, Loading } from "@/components/feedback";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { FormField } from "@/components/ui/form";
import { Input } from "@/components/ui/input";
import { Table } from "@/components/ui/table";
import { useResource } from "@/hooks/use-resource";
import { api, post } from "@/lib/api";
import { realtime } from "@/lib/session";
import { useEffect, useRef, useState } from "react";
import { type Board, type Range, type RankConfig } from "./teaching-api";
import { FormError, Notice, useTeachingAction } from "./teaching-shared";
export function rankError(ranges: Range[]) {
  const sorted = [...ranges].sort((a, b) => a.from - b.from);
  return sorted.some(
    (r, i) =>
      !r.name.trim() ||
      !Number.isSafeInteger(r.from) ||
      !Number.isSafeInteger(r.to) ||
      r.from < 1 ||
      r.to < r.from ||
      (i > 0 && sorted[i - 1].to >= r.from),
  )
    ? "Tên Rank phải có nội dung; khoảng vị trí là số nguyên dương và không được chồng lấn."
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
            {ranges.map((r, index) => (
              <div
                key={index}
                className="grid items-end gap-3 sm:grid-cols-[minmax(0,2fr)_minmax(0,1fr)_minmax(0,1fr)_auto]"
              >
                <FormField label={`Tên Rank ${index + 1}`}>
                  {(p) => (
                    <Input
                      {...p}
                      maxLength={100}
                      value={r.name}
                      disabled={action.pending}
                      onChange={(e) =>
                        setRanges((old) =>
                          old.map((v, i) =>
                            i === index ? { ...v, name: e.target.value } : v,
                          ),
                        )
                      }
                    />
                  )}
                </FormField>
                {(["from", "to"] as const).map((field) => (
                  <FormField
                    key={field}
                    label={`${field === "from" ? "Từ" : "Đến"} vị trí ${index + 1}`}
                  >
                    {(p) => (
                      <Input
                        {...p}
                        type="number"
                        min={1}
                        step={1}
                        value={r[field] || ""}
                        disabled={action.pending}
                        onChange={(e) =>
                          setRanges((old) =>
                            old.map((v, i) =>
                              i === index
                                ? { ...v, [field]: Number(e.target.value) }
                                : v,
                            ),
                          )
                        }
                      />
                    )}
                  </FormField>
                ))}
                <Button
                  variant="outline"
                  disabled={action.pending}
                  onClick={() =>
                    setRanges((old) => old.filter((_, i) => i !== index))
                  }
                >
                  Xóa mức {index + 1}
                </Button>
              </div>
            ))}
            <div className="flex flex-wrap gap-3">
              <Button
                variant="outline"
                disabled={action.pending || ranges.length >= 100}
                onClick={() =>
                  setRanges((old) => [...old, { name: "", from: 0, to: 0 }])
                }
              >
                Thêm mức Rank
              </Button>
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
