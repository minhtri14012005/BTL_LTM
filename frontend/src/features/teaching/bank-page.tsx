import { EmptyState, ErrorState, Loading } from "@/components/feedback";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Confirm } from "@/components/ui/confirm";
import { FormField } from "@/components/ui/form";
import { BankIdentity, bankDisplayTitle } from "./bank-identity";
import { Input } from "@/components/ui/input";
import { useResource } from "@/hooks/use-resource";
import { api, post } from "@/lib/api";
import { useSession } from "@/lib/session";
import { useEffect, useState } from "react";
import {
  Link,
  useNavigate,
  useParams,
  useSearchParams,
} from "react-router-dom";
import {
  QuestionEditor,
  QuestionPreview,
  emptyQuestion,
} from "./question-editor";
import {
  type Bank,
  type Subject,
  type ContentMode,
  type Question,
  modeNames,
} from "./teaching-api";
import {
  FormError,
  NativeSelect,
  TeachingFrame,
  useTeachingAction,
} from "./teaching-shared";
export function BankPage() {
  const { bankId } = useParams();
  return bankId ? <BankDetail key={bankId} /> : <BankDirectory />;
}
function BankDirectory() {
  const navigate = useNavigate();
  const [params, setParams] = useSearchParams();
  const mode: ContentMode = ["QUIZ", "FILL_BLANK", "ASSIGNMENT"].includes(
    params.get("mode") || "",
  )
    ? (params.get("mode") as ContentMode)
    : "QUIZ";
  const scope = params.get("scope") || "MINE",
    subjectId = params.get("subjectId") || "",
    search = params.get("q") || "",
    page = Math.max(0, Number(params.get("page")) || 0);
  const [query, setQuery] = useState(search);
  useEffect(() => setQuery(search), [search]);
  const subjects = useResource(signal => api<Subject[]>("/api/quizzes/subjects", {signal}), []);
  const action = useTeachingAction();
  function patch(key: string, value: string) {
    setParams((old) => {
      const next = new URLSearchParams(old);
      next.set(key, value);
      if (key !== "page") next.set("page", "0");
      return next;
    });
  }
  const setMode = (value: ContentMode) => patch("mode", value),
    setScope = (value: string) => patch("scope", value),
    setSearch = (value: string) => patch("q", value),
    setPage = (value: number | ((page: number) => number)) =>
      patch("page", String(typeof value === "function" ? value(page) : value));
  const resource = useResource(
    (signal) =>
      api<{ items: Bank[]; totalElements: number }>(
        `/api/quizzes?${new URLSearchParams({ mode, scope, ...(subjectId ? {subjectId} : {}), q: search, page: String(page), size: "20" })}`,
        { signal },
      ),
    [mode, scope, subjectId, search, page],
  );
  return (
    <TeachingFrame title="Ngân hàng câu hỏi & đề bài">
      <p className="text-sm leading-6 text-muted-foreground">
        Quản lý bộ của bạn hoặc xem các bộ Public. Mỗi bộ có trang xem trước và
        soạn nội dung riêng.
      </p>
      <form
        className="grid gap-3 rounded-2xl border bg-card/80 p-4 sm:grid-cols-2 xl:grid-cols-[minmax(0,1fr)_minmax(0,1.4fr)_minmax(0,1.2fr)_minmax(0,1.4fr)_auto_auto]"
        onSubmit={(e) => {
          e.preventDefault();
          setSearch(query);
        }}
      >
        <NativeSelect
          aria-label="Loại nội dung"
          className="min-w-0"
          value={mode}
          onChange={(e) => {
            setMode(e.target.value as ContentMode);
          }}
        >
          {(["QUIZ", "FILL_BLANK", "ASSIGNMENT"] as ContentMode[]).map((m) => (
            <option key={m} value={m}>
              {m === "FILL_BLANK" ? "Điền từ" : modeNames[m]}
            </option>
          ))}
        </NativeSelect>
        <NativeSelect
          aria-label="Phạm vi tìm kiếm"
          className="min-w-0"
          value={scope}
          onChange={(e) => {
            setScope(e.target.value);
          }}
        >
          <option value="MINE">Của tôi</option>
          <option value="SHARED">PUBLIC của giảng viên khác</option>
          <option value="VISIBLE">Tất cả được phép xem</option>
        </NativeSelect>
        <NativeSelect aria-label="Lọc môn học" value={subjectId} disabled={subjects.loading || !!subjects.error} onChange={e => patch("subjectId", e.target.value)}>
          <option value="">Tất cả môn học</option>
          {subjects.data?.map(s => <option key={s.id} value={s.id}>{s.name}</option>)}
        </NativeSelect>
        <Input
          className="min-w-0"
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
            navigate(`/app/giang_vien/banks/new?mode=${mode}${subjectId ? `&subjectId=${subjectId}` : ""}`);
          }}
        >
          Tạo bộ nội dung
        </Button>
      </form>
      {subjects.error && <ErrorState message={subjects.error} retry={subjects.reload} />}
      <FormError message={action.error} />
      {resource.loading ? (
        <Loading />
      ) : resource.error ? (
        <ErrorState message={resource.error || subjects.error || ""} retry={() => { resource.reload(); subjects.reload(); }} />
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
                  <Card key={bank.id} className="overflow-hidden">
                    <CardHeader className="tone-1">
                      <BankIdentity bank={bank} />
                      <CardTitle className="break-words leading-snug">{bankDisplayTitle(bank.title)}</CardTitle>
                    </CardHeader>
                    <CardContent className="space-y-4 pt-5">
                      <p className="text-sm text-muted-foreground">
                        {bank.questionCount} {bank.mode === "ASSIGNMENT" ? "đề bài" : "câu"}
                      </p>
                      <Button
                        aria-label={`Preview ${bank.title}`}
                        variant="outline"
                        disabled={action.pending}
                        onClick={() => {
                          navigate(
                            `/app/giang_vien/banks/${bank.id}?mode=${mode}&scope=${scope}&q=${encodeURIComponent(search)}&page=${page}${subjectId ? `&subjectId=${subjectId}` : ""}`,
                          );
                        }}
                      >
                        Xem trước
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
    </TeachingFrame>
  );
}
function BankDetail() {
  const { bankId, bankAction } = useParams();
  const [params, setParams] = useSearchParams();
  const subjects = useResource(signal => api<Subject[]>("/api/quizzes/subjects", {signal}), []);
  const navigate = useNavigate();
  const { user } = useSession();
  const creating = bankId === "new",
    editing = creating || bankAction === "edit";
  const mode: ContentMode = ["QUIZ", "FILL_BLANK", "ASSIGNMENT"].includes(
    params.get("mode") || "",
  )
    ? (params.get("mode") as ContentMode)
    : "QUIZ";
  const back = `/app/giang_vien/banks?${params}`;
  const action = useTeachingAction();
  const [remove, setRemove] = useState(false);
  const resource = useResource(
    (signal) =>
      creating
        ? Promise.resolve(null)
        : api<Bank>(`/api/quizzes/${bankId}/preview`, { signal }),
    [bankId],
  );
  async function save(
    title: string,
    questions: Question[],
    visibility: "PUBLIC" | "PRIVATE",
    subjectId?: number,
  ) {
    await action.run(async () => {
      if (creating)
        await post("/api/quizzes", { title, questions, visibility, mode, subjectId });
      else
        await api(`/api/quizzes/${bankId}`, {
          method: "PUT",
          body: JSON.stringify({
            title,
            subjectId,
            questions,
            visibility,
            mode: resource.data!.mode,
            revision: resource.data!.revision,
          }),
        });
      navigate(back);
    }, "Đã lưu ngân hàng nội dung.");
  }
  return (
    <TeachingFrame
      title={
        creating
          ? "Tạo bộ nội dung"
          : resource.data ? bankDisplayTitle(resource.data.title) : "Bộ nội dung"
      }
    >
      <Button asChild variant="outline">
        <Link to={back}>Về danh sách</Link>
      </Button>
      {resource.loading || (editing && subjects.loading) ? (
        <Loading />
      ) : (resource.error || (editing && subjects.error)) ? (
        <ErrorState message={resource.error || subjects.error || ""} retry={() => { resource.reload(); subjects.reload(); }} />
      ) : editing ? (
        <Card className="space-y-5 p-5 sm:p-6">

          {!creating && resource.data?.ownerUserId !== user!.id ? (
            <ErrorState message="Chỉ tác giả được chỉnh sửa bộ nội dung gốc." />
          ) : (
            <QuestionEditor
              key={`${bankId}:${mode}:${resource.data?.revision}`}
              mode={resource.data?.mode || mode}
              initialTitle={resource.data?.title || ""}
              initialQuestions={
                resource.data?.questions || [emptyQuestion(mode)]
              }
              modeControl={<FormField label="Loại nội dung">{p => <NativeSelect {...p} disabled={!creating} value={resource.data?.mode || mode} onChange={e => setParams(old => {const next=new URLSearchParams(old);next.set("mode",e.target.value);return next;})}>{(["QUIZ","FILL_BLANK","ASSIGNMENT"] as ContentMode[]).map(m => <option key={m} value={m}>{m === "FILL_BLANK" ? "Điền từ" : modeNames[m]}</option>)}</NativeSelect>}</FormField>}
              subjects={subjects.data || []}
              initialSubjectId={resource.data?.subjectId || Number(params.get("subjectId")) || 0}
              initialVisibility={resource.data?.visibility || "PRIVATE"}
              pending={action.pending}
              error={action.error}
              onSave={save}
            />
          )}
        </Card>
      ) : (
        resource.data && (
          <Card className="space-y-5 p-5">
            <BankIdentity bank={resource.data} />
            <p className="text-sm text-muted-foreground">{modeNames[resource.data.mode]} · {resource.data.questionCount} {resource.data.mode === "ASSIGNMENT" ? "đề bài" : "câu"}</p>
            <QuestionPreview
              questions={resource.data.questions}
              bankId={resource.data.id}
            />
            {resource.data.ownerUserId === user!.id && (
              <div className="flex flex-wrap gap-3">
                <Button asChild>
                  <Link to={`/app/giang_vien/banks/${bankId}/edit?${params}`}>
                    Sửa bản gốc của tôi
                  </Link>
                </Button>
                <Button variant="destructive" onClick={() => setRemove(true)}>
                  Xóa bộ nội dung
                </Button>
              </div>
            )}
            <FormError message={action.error} />
          </Card>
        )
      )}
      <Confirm
        open={remove}
        onOpenChange={setRemove}
        error={action.error}
        title="Xóa bộ nội dung gốc?"
        description="Các bản sao đã chọn vào trò vẫn được giữ độc lập."
        pending={action.pending}
        onConfirm={() => {
          void action.run(async () => {
            await api(
              `/api/quizzes/${bankId}?revision=${resource.data!.revision}`,
              { method: "DELETE" },
            );
            navigate(back);
          }, "Đã xóa bộ nội dung.");
        }}
      />
    </TeachingFrame>
  );
}
