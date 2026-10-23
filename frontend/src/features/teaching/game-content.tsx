import { useEffect, useState } from "react";
import { api, post, ApiError } from "@/lib/api";
import { useResource } from "@/hooks/use-resource";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Card } from "@/components/ui/card";
import { Loading, ErrorState } from "@/components/feedback";
import {
  type Bank,
  type Content,
  type Game,
  type Lesson,
} from "./teaching-api";
import { useTeachingAction, FormError, Notice } from "./teaching-shared";
import { QuestionEditor, QuestionPreview } from "./question-editor";
export function GameContent({
  lesson,
  game,
  refresh,
  locked,
  onBusyChange,
}: {
  lesson: Lesson;
  game: Game;
  refresh: () => Promise<void>;
  locked: boolean;
  onBusyChange: (gameId: number, busy: boolean) => void;
}) {
  const url = `/api/teaching/lessons/${lesson.id}/games/${game.position}/content`;
  const resource = useResource(
    async (signal) => {
      try {
        return await api<Content>(url, { signal });
      } catch (e) {
        if (e instanceof ApiError && e.status === 404) return null;
        throw e;
      }
    },
    [lesson.id, game.id],
  );
  const [editing, setEditing] = useState(false),
    [choosing, setChoosing] = useState(false);
  const action = useTeachingAction();
  const busy = action.pending || locked;
  useEffect(() => {
    onBusyChange(game.id, action.pending);
    return () => onBusyChange(game.id, false);
  }, [game.id, action.pending, onBusyChange]);
  if (game.mode === "LUCKY_WHEEL") return null;
  return (
    <div className="space-y-4">
      <Button
        variant="outline"
        disabled={busy}
        onClick={() => {
          setEditing(false);
          resource.reload();
        }}
      >
        Đọc lại nội dung trò
      </Button>
      <FormError message={action.error} />
      {resource.loading ? (
        <p role="status">Đang tải nội dung trò…</p>
      ) : resource.error ? (
        <ErrorState message={resource.error} retry={resource.reload} />
      ) : (
        <>
          {resource.data ? (
            <>
              <Notice>
                BẢN SAO TRONG TRÒ · Ngân hàng nguồn #
                {resource.data.sourceQuizId}. Sửa ở đây chỉ đổi snapshot của trò
                này, không sửa hay tạo thêm bộ trong ngân hàng.
              </Notice>
              {editing && game.status === "READY" ? (
                <QuestionEditor
                  key={resource.data.revision}
                  mode={resource.data.mode}
                  initialTitle={resource.data.title}
                  initialQuestions={resource.data.questions}
                  pending={busy}
                  error={action.error}
                  label="Lưu bản sao trong trò"
                  onSave={async (title, questions) => {
                    await action.run(async () => {
                      await api(url, {
                        method: "PUT",
                        body: JSON.stringify({
                          title,
                          questions,
                          revision: resource.data!.revision,
                        }),
                      });
                      await refresh();
                      setEditing(false);
                      resource.reload();
                    }, "Đã lưu bản sao độc lập.");
                  }}
                />
              ) : (
                <QuestionPreview
                  questions={resource.data.questions}
                  bankId={resource.data.sourceQuizId}
                />
              )}
            </>
          ) : (
            <p className="text-sm text-muted-foreground">
              Chưa có nội dung. Chọn một bộ đúng loại từ ngân hàng; bạn có thể
              soạn bộ mới tại trang Ngân hàng nội dung.
            </p>
          )}
          {game.status === "READY" && (
            <div className="flex flex-wrap gap-3">
              <Button
                variant="outline"
                onClick={() => setChoosing(!choosing)}
                disabled={busy}
              >
                {choosing ? "Đóng chọn nguồn" : "Chọn bộ từ ngân hàng"}
              </Button>
              {resource.data && (
                <Button
                  variant="outline"
                  disabled={busy}
                  onClick={() => setEditing(!editing)}
                >
                  {editing ? "Hủy sửa bản sao" : "Sửa bản sao trong trò"}
                </Button>
              )}
            </div>
          )}
          {choosing && game.status === "READY" && (
            <SourcePicker
              mode={game.mode}
              pending={busy}
              apply={async (bank) => {
                await action.run(async () => {
                  await post(url, {
                    quizId: bank.id,
                    lessonRevision: lesson.revision,
                  });
                  await refresh();
                  setChoosing(false);
                  setEditing(false);
                  resource.reload();
                }, "Đã sao chép nguồn vào trò.");
              }}
            />
          )}
        </>
      )}
    </div>
  );
}
function SourcePicker({
  mode,
  pending,
  apply,
}: {
  mode: string;
  pending: boolean;
  apply: (bank: Bank) => Promise<void>;
}) {
  const [query, setQuery] = useState(""),
    [search, setSearch] = useState(""),
    [page, setPage] = useState(0),
    [preview, setPreview] = useState<Bank>();
  const action = useTeachingAction();
  const resource = useResource(
    (signal) =>
      api<{ items: Bank[]; totalElements: number }>(
        `/api/quizzes?${new URLSearchParams({ mode, scope: "VISIBLE", q: search, page: String(page), size: "20" })}`,
        { signal },
      ),
    [mode, search, page],
  );
  return (
    <Card className="space-y-4 bg-accent/20 p-4">
      <h3 className="font-semibold">Nguồn được phép sử dụng</h3>
      <form
        className="flex gap-2"
        onSubmit={(e) => {
          e.preventDefault();
          setSearch(query);
          setPage(0);
        }}
      >
        <Input
          aria-label="Tìm nguồn cho trò"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
        />
        <Button variant="outline" type="submit">
          Tìm nguồn
        </Button>
      </form>
      <FormError message={action.error} />
      {resource.loading ? (
        <Loading />
      ) : resource.error ? (
        <ErrorState message={resource.error} retry={resource.reload} />
      ) : (
        resource.data && (
          <>
            {!resource.data.items.length && (
              <p>Chưa có bộ phù hợp. Hãy tạo tại Ngân hàng nội dung.</p>
            )}
            {resource.data.items.map((bank) => (
              <div
                key={bank.id}
                className="flex flex-wrap items-center justify-between gap-3 rounded-xl border bg-card p-3"
              >
                <span>
                  {bank.title} · {bank.visibility} · {bank.questionCount} câu/đề
                </span>
                <div className="flex flex-wrap gap-2">
                  <Button
                    variant="outline"
                    disabled={pending || action.pending}
                    onClick={() => {
                      void action.run(async () =>
                        setPreview(
                          await api<Bank>(`/api/quizzes/${bank.id}/preview`),
                        ),
                      );
                    }}
                  >
                    Preview nguồn {bank.title}
                  </Button>
                  <Button
                    disabled={pending}
                    onClick={() => {
                      void apply(bank);
                    }}
                  >
                    Dùng nguồn {bank.title}
                  </Button>
                </div>
              </div>
            ))}
            <div className="flex gap-2">
              <Button
                variant="outline"
                disabled={!page}
                onClick={() => setPage((p) => p - 1)}
              >
                Nguồn trước
              </Button>
              <Button
                variant="outline"
                disabled={(page + 1) * 20 >= resource.data.totalElements}
                onClick={() => setPage((p) => p + 1)}
              >
                Nguồn sau
              </Button>
            </div>
          </>
        )
      )}
      {preview && (
        <Card className="space-y-4 p-4">
          <h4 className="font-semibold">
            Preview nguồn (chỉ xem): {preview.title}
          </h4>
          <QuestionPreview questions={preview.questions} bankId={preview.id} />
          <Button variant="outline" onClick={() => setPreview(undefined)}>
            Đóng Preview nguồn
          </Button>
        </Card>
      )}
    </Card>
  );
}
