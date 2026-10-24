import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { FormField } from "@/components/ui/form";
import { Input } from "@/components/ui/input";
import { useState, type FormEvent, type ReactNode } from "react";
import {
  type Subject,
  questionError,
  type ContentMode,
  type Question,
} from "./teaching-api";
import { FormError, NativeSelect, Textarea } from "./teaching-shared";
export function emptyQuestion(mode: ContentMode): Question {
  return mode === "QUIZ"
    ? {
        content: "",
        options: { A: "", B: "", C: "", D: "" },
        correctAnswer: "A",
      }
    : mode === "FILL_BLANK"
      ? { content: "", acceptedAnswers: [""], matchingPolicy: "EXACT" }
      : { content: "" };
}
export function questionPayload(mode: ContentMode, q: Question): Question {
  return mode === "QUIZ"
    ? {
        content: q.content,
        options: q.options,
        correctAnswer: q.correctAnswer,
        ...(q.imageRef ? { imageRef: q.imageRef } : {}),
      }
    : mode === "FILL_BLANK"
      ? {
          content: q.content,
          acceptedAnswers: q.acceptedAnswers,
          matchingPolicy: q.matchingPolicy,
        }
      : { content: q.content };
}
export function QuestionEditor({
  mode,
  initialTitle,
  initialQuestions,
  initialVisibility,
  subjects,
  modeControl,
  initialSubjectId,
  onSave,
  pending,
  error,
  label = "Lưu nội dung",
}: {
  mode: ContentMode;
  initialTitle: string;
  initialQuestions: Question[];
  initialVisibility?: "PUBLIC" | "PRIVATE";
  subjects?: Subject[];
  modeControl?: ReactNode;
  initialSubjectId?: number;
  onSave: (
    title: string,
    questions: Question[],
    visibility: "PUBLIC" | "PRIVATE",
    subjectId?: number,
  ) => Promise<unknown>;
  pending: boolean;
  error: string;
  label?: string;
}) {
  const [subjectId, setSubjectId] = useState(initialSubjectId || 0);
  const [active, setActive] = useState(0);
  const [title, setTitle] = useState(initialTitle),
    [questions, setQuestions] = useState<Question[]>(
      structuredClone(initialQuestions),
    ),
    [visibility, setVisibility] = useState(initialVisibility || "PRIVATE"),
    [validation, setValidation] = useState("");
  function update(index: number, patch: Partial<Question>) {
    setQuestions((old) =>
      old.map((q, i) => (i === index ? { ...q, ...patch } : q)),
    );
  }
  async function submit(e: FormEvent) {
    e.preventDefault();
    if(subjects && !subjects.some(s => s.id === subjectId)) { setValidation("Hãy chọn môn học do ADMIN tạo."); return; }
    const error = questionError(mode, questions);
    if (!title.trim() || error) {
      setValidation(error || "Cần nhập tiêu đề.");
      const invalid = questions.findIndex((q) => !!questionError(mode, [q]));
      if (invalid >= 0) setActive(invalid);
      return;
    }
    setValidation("");
    const payload = questions.map((q) => questionPayload(mode, q));
    if (subjects) await onSave(title.trim(), payload, visibility, subjectId);
    else await onSave(title.trim(), payload, visibility);

  }
  return (
    <form onSubmit={submit} className="space-y-5">
      <fieldset disabled={pending} className="min-w-0 space-y-5">
        <div className={subjects ? "grid gap-4 md:grid-cols-2" : "space-y-4"}>
          {modeControl}
        {initialVisibility && (
          <FormField label="Phạm vi ngân hàng">
            {(p) => (
              <NativeSelect
                {...p}
                value={visibility}
                onChange={(e) =>
                  setVisibility(e.target.value as "PUBLIC" | "PRIVATE")
                }
              >
                <option value="PRIVATE">PRIVATE · Chỉ tác giả</option>
                <option value="PUBLIC">
                  PUBLIC · Giảng viên khác được xem và sử dụng
                </option>
              </NativeSelect>
            )}
          </FormField>
        )}
        {subjects && <FormField label="Môn học">
          {p => <NativeSelect {...p} required value={subjectId || ""} onChange={e => setSubjectId(Number(e.target.value))}>
            <option value="" disabled>Chọn môn học</option>
            {subjects.map(s => <option key={s.id} value={s.id}>{s.name} · {s.code}</option>)}
          </NativeSelect>}
        </FormField>}
        {subjects && !subjects.length && <p role="status" className="text-sm text-muted-foreground">Chưa có môn học. Liên hệ ADMIN để bổ sung danh mục.</p>}
        <FormField label="Tiêu đề nội dung">
          {(p) => (
            <Input
              {...p}
              required
              maxLength={200}
              value={title}
              onChange={(e) => setTitle(e.target.value)}
            />
          )}
        </FormField>
        </div>
        <p className="text-sm text-muted-foreground">
          {questions.length}{" "}
          {mode === "ASSIGNMENT" ? "đề" : "câu hỏi"}
          {mode === "FILL_BLANK"
            ? " · Dùng đúng một {{blank}} để đánh dấu ô trống trong mỗi câu."
            : ""}
        </p>
        {mode !== "ASSIGNMENT" && (
          <nav
            aria-label="Chọn câu hỏi để soạn"
            className="flex flex-wrap gap-2"
          >
            {questions.map((_, i) => (
              <Button
                key={i}
                type="button"
                size="sm"
                variant={active === i ? "default" : "outline"}
                onClick={() => setActive(i)}
                aria-pressed={active === i}
              >
                Câu {i + 1}
              </Button>
            ))}
          </nav>
        )}
        {questions
          .map((q, index) => ({ q, index }))
          .filter(
            ({ index }) => index === Math.min(active, questions.length - 1),
          )
          .map(({ q, index }) => (
            <Card key={index} className="space-y-4 bg-muted/30 p-4">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <h3 className="font-semibold">
                  {mode === "ASSIGNMENT" ? "Đề bài" : `Câu ${index + 1}`}
                </h3>
                {questions.length > 1 && (
                  <Button
                    type="button"
                    variant="ghost"
                    onClick={() => {
                      setQuestions((old) => old.filter((_, i) => i !== index));
                      setActive(Math.max(0, index - 1));
                    }}
                  >
                    Xóa câu {index + 1}
                  </Button>
                )}
              </div>
              <FormField
                label={
                  mode === "ASSIGNMENT"
                    ? "Nội dung đề bài"
                    : `Nội dung câu ${index + 1}`
                }
              >
                {(p) => (
                  <Textarea
                    {...p}
                    required
                    maxLength={5000}
                    value={q.content}
                    onChange={(e) => update(index, { content: e.target.value })}
                  />
                )}
              </FormField>
              {mode === "QUIZ" && (
                <>
                  <div className="grid gap-4 sm:grid-cols-2">
                    {["A", "B", "C", "D"].map((option) => (
                      <FormField
                        key={option}
                        label={`Câu ${index + 1} · Lựa chọn ${option}`}
                      >
                        {(p) => (
                          <Input
                            {...p}
                            required
                            maxLength={2000}
                            value={q.options?.[option] || ""}
                            onChange={(e) =>
                              update(index, {
                                options: {
                                  ...q.options,
                                  [option]: e.target.value,
                                },
                              })
                            }
                          />
                        )}
                      </FormField>
                    ))}
                  </div>
                  <FormField label={`Đáp án đúng câu ${index + 1}`}>
                    {(p) => (
                      <NativeSelect
                        {...p}
                        value={q.correctAnswer || "A"}
                        onChange={(e) =>
                          update(index, { correctAnswer: e.target.value })
                        }
                      >
                        {["A", "B", "C", "D"].map((o) => (
                          <option key={o}>{o}</option>
                        ))}
                      </NativeSelect>
                    )}
                  </FormField>
                </>
              )}
              {mode === "FILL_BLANK" && (
                <>
                  <FormField
                    label={`Đáp án hợp lệ câu ${index + 1}`}
                    hint="Mỗi dòng là một đáp án hợp lệ. Giữ nguyên nội dung/cú pháp cần so khớp."
                  >
                    {(p) => (
                      <Textarea
                        {...p}
                        required
                        value={q.acceptedAnswers?.join("\n") || ""}
                        onChange={(e) =>
                          update(index, {
                            acceptedAnswers: e.target.value.split("\n"),
                          })
                        }
                      />
                    )}
                  </FormField>
                  <FormField label={`So khớp câu ${index + 1}`}>
                    {(p) => (
                      <NativeSelect
                        {...p}
                        value={q.matchingPolicy || "EXACT"}
                        onChange={(e) =>
                          update(index, {
                            matchingPolicy: e.target
                              .value as Question["matchingPolicy"],
                          })
                        }
                      >
                        <option value="EXACT">
                          Chính xác, phân biệt hoa/thường
                        </option>
                        <option value="CASE_INSENSITIVE">
                          Không phân biệt hoa/thường
                        </option>
                      </NativeSelect>
                    )}
                  </FormField>
                </>
              )}
            </Card>
          ))}
        {mode !== "ASSIGNMENT" && (
          <Button
            type="button"
            variant="outline"
            disabled={questions.length >= 50}
            onClick={() => {
              setActive(questions.length);
              setQuestions((old) => [...old, emptyQuestion(mode)]);
            }}
          >
            Thêm câu hỏi
          </Button>
        )}
        <FormError message={validation || error} />
        <Button type="submit" disabled={pending}>
          {pending ? "Đang lưu…" : label}
        </Button>
      </fieldset>
    </form>
  );
}
export function QuestionPreview({
  questions,
  bankId,
}: {
  questions: Question[];
  bankId?: number;
}) {
  const [index, setIndex] = useState(0),
    [answers, setAnswers] = useState(false);
  const q = questions[Math.min(index, questions.length - 1)];
  if (!q) return <p>Chưa có nội dung.</p>;
  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <span className="status-badge">
          CHỈ XEM · Câu {index + 1}/{questions.length}
        </span>
        <Button
          type="button"
          variant="outline"
          onClick={() => setAnswers(!answers)}
        >
          {answers ? "Ẩn đáp án" : "Hiện đáp án"}
        </Button>
      </div>
      <p className="whitespace-pre-wrap text-lg leading-8">{q.content}</p>
      {q.imageRef && bankId && (
        <img
          className="max-h-64 max-w-full rounded-xl"
          alt={`Hình câu ${index + 1}`}
          src={`/api/quizzes/${bankId}/images/${q.imageRef.slice(7)}`}
        />
      )}
      {q.options && (
        <div className="grid gap-3 sm:grid-cols-2">
          {Object.entries(q.options).map(([key, value]) => (
            <Card key={key} className="p-4">
              {key}. {value}
            </Card>
          ))}
        </div>
      )}
      {answers && (
        <p className="rounded-xl bg-accent p-4 text-sm">
          {q.correctAnswer
            ? `Đáp án: ${q.correctAnswer}`
            : q.acceptedAnswers
              ? `Đáp án hợp lệ: ${q.acceptedAnswers.join(" / ")} · ${q.matchingPolicy === "EXACT" ? "Phân biệt hoa/thường" : "Không phân biệt hoa/thường"}`
              : "Bài tập được giảng viên chấm."}
        </p>
      )}
      <div className="flex justify-between gap-2">
        <Button
          variant="outline"
          disabled={index === 0}
          onClick={() => setIndex((i) => i - 1)}
        >
          Câu trước
        </Button>
        <Button
          variant="outline"
          disabled={index >= questions.length - 1}
          onClick={() => setIndex((i) => i + 1)}
        >
          Câu sau
        </Button>
      </div>
    </div>
  );
}
