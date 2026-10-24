import { useState } from "react";
import { useResource } from "@/hooks/use-resource";
import { api } from "@/lib/api";
import { FormField } from "@/components/ui/form";
import { Input } from "@/components/ui/input";
import { Card } from "@/components/ui/card";
import { ErrorState, Loading } from "@/components/feedback";
import { LessonPlan } from "./lesson-plan";
import { type Lesson, type Content, modeNames } from "./teaching-api";
import { NativeSelect, Textarea } from "./teaching-shared";
type Props = {
  classId: number;
  pending: boolean;
  error: string;
  onSave: (plan: object, banks?: Record<number, number>) => Promise<unknown>;
};
export function LessonCreation(props: Props) {
  const [source, setSource] = useState(0),
    [copy, setCopy] = useState(false);
  const sources = useResource(
    (signal) =>
      api<Lesson[]>(`/api/teaching/classes/${props.classId}/lesson-templates`, {
        signal,
      }),
    [props.classId],
  );
  const template = useResource(
    (signal) =>
      source
        ? api<{ lesson: Lesson; contents: Record<string, Content> }>(
            `/api/teaching/classes/${props.classId}/lesson-templates/${source}`,
            { signal },
          )
        : Promise.resolve(null),
    [props.classId, source],
  );
  return (
    <div className="space-y-5">
      <FormField label="Cách tạo buổi">
        {(p) => (
          <NativeSelect
            {...p}
            value={copy ? "copy" : "new"}
            disabled={props.pending}
            onChange={(e) => {
              setCopy(e.target.value === "copy");
              setSource(0);
            }}
          >
            <option value="new">Tạo buổi từ đầu</option>
            <option value="copy">
              Sao chép cấu hình từ buổi đã hoàn thành
            </option>
          </NativeSelect>
        )}
      </FormField>
      {copy && (
        <>
          <FormField label="Buổi nguồn cùng môn học">
            {(p) => (
              <NativeSelect
                {...p}
                disabled={props.pending || sources.loading}
                value={source}
                onChange={(e) => setSource(Number(e.target.value))}
              >
                <option value={0}>Chọn buổi nguồn</option>
                {sources.data?.map((l) => (
                  <option key={l.id} value={l.id}>
                    Lớp #{l.courseClassId} · Buổi {l.number}: {l.title}
                  </option>
                ))}
              </NativeSelect>
            )}
          </FormField>
          {sources.error && (
            <ErrorState message={sources.error} retry={sources.reload} />
          )}{" "}
          {!sources.loading && !sources.error && !sources.data?.length && (
            <p className="text-sm text-muted-foreground">
              Chưa có buổi đã hoàn thành cùng môn mà bạn có quyền sao chép nội
              dung.
            </p>
          )}
        </>
      )}
      {!copy ? (
        <LessonPlan {...props} />
      ) : (
        source > 0 &&
        (template.loading ? (
          <Loading />
        ) : template.error ? (
          <ErrorState message={template.error} retry={template.reload} />
        ) : (
          template.data && (
            <CopiedPlan
              key={source}
              {...props}
              source={template.data.lesson}
              contents={template.data.contents}
            />
          )
        ))
      )}
    </div>
  );
}
function CopiedPlan({
  source,
  contents,
  ...props
}: Props & { source: Lesson; contents: Record<string, Content> }) {
  const [configs, setConfigs] = useState(() =>
    source.games.map((g) => ({
      position: g.position,
      durationMs: g.durationMs || 30000,
      questionCount:
        g.questionCount || contents[g.position]?.questions.length || 1,
      wheelCells: g.wheelCells || [],
      selectedCount: g.selectedCount || 1,
      assignmentDeadlineAtMs: undefined as number | undefined,
    })),
  );
  function patch(index: number, key: string, value: unknown) {
    setConfigs((old) =>
      old.map((c, i) => (i === index ? { ...c, [key]: value } : c)),
    );
  }
  return (
    <div className="space-y-4">
      <p className="text-sm text-muted-foreground">
        Chỉ sao chép cấu hình và snapshot. Buổi mới nằm cuối danh sách, chưa bắt
        đầu và không có điểm hay bài nộp. Bài tập cần chọn hạn nộp mới.
      </p>
      <LessonPlan
        submitLabel="Tạo buổi học"
        initial={source}
        pending={props.pending}
        error={props.error}
        onSave={(plan, banks) =>
          props.onSave(
            { ...plan, sourceLessonId: source.id, gameSettings: configs },
            banks,
          )
        }
      >
        <div className="grid gap-4 lg:grid-cols-3">
          {source.games.map((g, i) => (
            <Card key={g.id} className={`space-y-3 p-4 tone-${i}`}>
              <h3 className="font-semibold">
                Cấu hình chung · {modeNames[g.mode]}
              </h3>
              {g.mode === "QUIZ" || g.mode === "FILL_BLANK" ? (
                <>
                  <FormField label={`Số câu trò ${g.position}`}>
                    {(p) => (
                      <Input
                        {...p}
                        type="number"
                        min={1}
                        max={contents[g.position]?.questions.length || 1}
                        required
                        value={configs[i].questionCount}
                        onChange={(e) =>
                          patch(i, "questionCount", Number(e.target.value))
                        }
                      />
                    )}
                  </FormField>
                  <FormField label={`Giây mỗi câu trò ${g.position}`}>
                    {(p) => (
                      <Input
                        {...p}
                        type="number"
                        min={1}
                        max={2147483}
                        required
                        value={configs[i].durationMs / 1000}
                        onChange={(e) =>
                          patch(
                            i,
                            "durationMs",
                            Math.round(Number(e.target.value) * 1000),
                          )
                        }
                      />
                    )}
                  </FormField>
                  <p className="text-xs">
                    Snapshot nguồn có{" "}
                    {contents[g.position]?.questions.length || 0} câu. Tổng điểm
                    tối đa bằng ngân sách trò ở trên.
                  </p>
                </>
              ) : g.mode === "LUCKY_WHEEL" ? (
                <>
                  <FormField label="Danh sách thử thách (mỗi dòng một ô)">
                    {(p) => (
                      <Textarea
                        {...p}
                        required
                        value={configs[i].wheelCells.join("\n")}
                        onChange={(e) =>
                          patch(i, "wheelCells", e.target.value.split("\n"))
                        }
                      />
                    )}
                  </FormField>
                  <FormField label="Số sinh viên được chọn">
                    {(p) => (
                      <Input
                        {...p}
                        required
                        type="number"
                        min={1}
                        max={10000}
                        value={configs[i].selectedCount}
                        onChange={(e) =>
                          patch(i, "selectedCount", Number(e.target.value))
                        }
                      />
                    )}
                  </FormField>
                </>
              ) : (
                <FormField label="Hạn nộp mới cho Bài tập">
                  {(p) => (
                    <Input
                      {...p}
                      type="datetime-local"
                      required
                      onChange={(e) =>
                        patch(
                          i,
                          "assignmentDeadlineAtMs",
                          e.target.value
                            ? new Date(e.target.value).getTime()
                            : undefined,
                        )
                      }
                    />
                  )}
                </FormField>
              )}
            </Card>
          ))}
        </div>
      </LessonPlan>
    </div>
  );
}
