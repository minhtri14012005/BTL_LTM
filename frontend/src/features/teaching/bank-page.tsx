import { useState } from "react";
import { api, post } from "@/lib/api";
import { useSession } from "@/lib/session";
import { useResource } from "@/hooks/use-resource";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Card, CardHeader, CardTitle, CardContent } from "@/components/ui/card";
import { Confirm } from "@/components/ui/confirm";
import { Loading, ErrorState, EmptyState } from "@/components/feedback";
import {
  type Bank,
  type ContentMode,
  type Question,
  modeNames,
} from "./teaching-api";
import {
  TeachingFrame,
  NativeSelect,
  useTeachingAction,
  FormError,
  Notice,
} from "./teaching-shared";
import {
  QuestionEditor,
  QuestionPreview,
  emptyQuestion,
} from "./question-editor";
export function BankPage() {
  const { user } = useSession();
  const [mode, setMode] = useState<ContentMode>("QUIZ"),
    [scope, setScope] = useState("MINE"),
    [query, setQuery] = useState(""),
    [search, setSearch] = useState(""),
    [page, setPage] = useState(0),
    [creating, setCreating] = useState(false),
    [detail, setDetail] = useState<Bank>(),
    [editing, setEditing] = useState(false),
    [remove, setRemove] = useState(false);
  const action = useTeachingAction();
  const resource = useResource(
    (signal) =>
      api<{ items: Bank[]; totalElements: number }>(
        `/api/quizzes?${new URLSearchParams({ mode, scope, q: search, page: String(page), size: "20" })}`,
        { signal },
      ),
    [mode, scope, search, page],
  );
  async function save(
    title: string,
    questions: Question[],
    visibility: "PUBLIC" | "PRIVATE",
  ) {
    await action.run(async () => {
      if (editing && detail)
        await api(`/api/quizzes/${detail.id}`, {
          method: "PUT",
          body: JSON.stringify({
            title,
            questions,
            visibility,
            mode: detail.mode,
            revision: detail.revision,
          }),
        });
      else await post("/api/quizzes", { title, questions, visibility, mode });
      setCreating(false);
      setEditing(false);
      setDetail(undefined);
      resource.reload();
    }, "Đã lưu ngân hàng nội dung.");
  }
  return (
    <TeachingFrame title="Ngân hàng câu hỏi & đề bài">
      <Notice>
        Nội dung PUBLIC có thể được giảng viên khác Preview và sao chép vào trò.
        Bản sao trong trò được chỉnh sửa độc lập; ngân hàng gốc chỉ tác giả được
        sửa.
      </Notice>
      {detail ? (
        <Card className="p-6">
          <div className="mb-5 flex flex-wrap items-center justify-between gap-3">
            <h2 className="text-xl font-semibold">{detail.title}</h2>
            <Button
              variant="outline"
              disabled={action.pending}
              onClick={() => {
                setDetail(undefined);
                setEditing(false);
                action.setError("");
              }}
            >
              Về danh sách
            </Button>
          </div>
          {editing ? (
            <QuestionEditor
              key={`${detail.id}-${detail.revision}`}
              mode={detail.mode}
              initialTitle={detail.title}
              initialQuestions={detail.questions}
              initialVisibility={detail.visibility}
              pending={action.pending}
              error={action.error}
              onSave={save}
            />
          ) : (
            <>
              <p className="mb-4 text-sm text-muted-foreground">
                {detail.visibility} · {modeNames[detail.mode]}
              </p>
              <QuestionPreview
                questions={detail.questions}
                bankId={detail.id}
              />
              {detail.ownerUserId === user!.id && (
                <div className="mt-5 flex flex-wrap gap-3">
                  <Button onClick={() => setEditing(true)}>
                    Sửa bản gốc của tôi
                  </Button>
                  <Button variant="destructive" onClick={() => setRemove(true)}>
                    Xóa bộ nội dung
                  </Button>
                </div>
              )}
              <FormError message={action.error} />
            </>
          )}
        </Card>
      ) : creating ? (
        <Card className="space-y-5 p-6">
          <div className="flex flex-wrap justify-between gap-3">
            <h2 className="text-xl font-semibold">Tạo bộ {modeNames[mode]}</h2>
            <Button
              variant="outline"
              disabled={action.pending}
              onClick={() => setCreating(false)}
            >
              Hủy tạo
            </Button>
          </div>
          <QuestionEditor
            mode={mode}
            initialTitle=""
            initialQuestions={[emptyQuestion(mode)]}
            initialVisibility="PRIVATE"
            onSave={save}
            pending={action.pending}
            error={action.error}
          />
        </Card>
      ) : (
        <>
          <form
            className="flex flex-col gap-3 sm:flex-row sm:flex-wrap"
            onSubmit={(e) => {
              e.preventDefault();
              setSearch(query);
              setPage(0);
            }}
          >
            <NativeSelect
              aria-label="Loại nội dung"
              className="sm:w-auto"
              value={mode}
              onChange={(e) => {
                setMode(e.target.value as ContentMode);
                setPage(0);
              }}
            >
              {(["QUIZ", "FILL_BLANK", "ASSIGNMENT"] as ContentMode[]).map(
                (m) => (
                  <option key={m} value={m}>
                    {modeNames[m]}
                  </option>
                ),
              )}
            </NativeSelect>
            <NativeSelect
              aria-label="Phạm vi tìm kiếm"
              className="sm:w-auto"
              value={scope}
              onChange={(e) => {
                setScope(e.target.value);
                setPage(0);
              }}
            >
              <option value="MINE">Của tôi</option>
              <option value="SHARED">PUBLIC của giảng viên khác</option>
              <option value="VISIBLE">Tất cả được phép xem</option>
            </NativeSelect>
            <Input
              className="sm:w-64"
              aria-label="Tìm bộ nội dung"
              maxLength={100}
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="Tìm tiêu đề…"
            />
            <Button type="submit" variant="outline">
              Tìm kiếm
            </Button>
            <Button
              type="button"
              onClick={() => {
                action.setError("");
                setCreating(true);
              }}
            >
              Tạo bộ nội dung
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
                {!resource.data.items.length ? (
                  <EmptyState
                    title="Chưa có bộ nội dung phù hợp"
                    description="Tạo bộ của bạn hoặc tìm các bộ PUBLIC được chia sẻ."
                  />
                ) : (
                  <div className="course-grid">
                    {resource.data.items.map((bank) => (
                      <Card key={bank.id}>
                        <CardHeader className="tone-1">
                          <span className="eyebrow">
                            {bank.visibility} · {modeNames[bank.mode]}
                          </span>
                          <CardTitle>{bank.title}</CardTitle>
                        </CardHeader>
                        <CardContent className="space-y-4 pt-5">
                          <p className="text-sm text-muted-foreground">
                            {bank.questionCount} câu/đề
                          </p>
                          <Button
                            variant="outline"
                            disabled={action.pending}
                            onClick={() => {
                              void action.run(async () => {
                                setDetail(
                                  await api<Bank>(
                                    `/api/quizzes/${bank.id}/preview`,
                                  ),
                                );
                                setEditing(false);
                              });
                            }}
                          >
                            Preview {bank.title}
                          </Button>
                        </CardContent>
                      </Card>
                    ))}
                  </div>
                )}
                <div className="flex flex-wrap items-center justify-between gap-3">
                  <p className="text-sm text-muted-foreground">
                    {resource.data.totalElements} bộ · Trang {page + 1}
                  </p>
                  <div className="flex gap-2">
                    <Button
                      variant="outline"
                      disabled={!page}
                      onClick={() => setPage((p) => p - 1)}
                    >
                      Trang trước
                    </Button>
                    <Button
                      variant="outline"
                      disabled={(page + 1) * 20 >= resource.data.totalElements}
                      onClick={() => setPage((p) => p + 1)}
                    >
                      Trang sau
                    </Button>
                  </div>
                </div>
              </>
            )
          )}
        </>
      )}
      <Confirm
        open={remove}
        onOpenChange={setRemove}
        title="Xóa bộ nội dung gốc?"
        description={`Xóa “${detail?.title}”. Các snapshot đã sao chép vào trò được giữ độc lập.`}
        pending={action.pending}
        onConfirm={() => {
          void action.run(async () => {
            await api(
              `/api/quizzes/${detail!.id}?revision=${detail!.revision}`,
              { method: "DELETE" },
            );
            setRemove(false);
            setDetail(undefined);
            resource.reload();
          }, "Đã xóa bộ nội dung.");
        }}
      />
    </TeachingFrame>
  );
}
