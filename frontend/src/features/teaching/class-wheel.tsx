import { ErrorState, Loading } from "@/components/feedback";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { useResource } from "@/hooks/use-resource";
import { api } from "@/lib/api";
import { useState } from "react";
import { FormError, useTeachingAction } from "./teaching-shared";
export function WheelConfig({ classId }: { classId: number }) {
  const resource = useResource(
    (signal) =>
      api<{ revision: number; cells: string[] }>(
        `/api/teaching/classes/${classId}/wheel`,
        { signal },
      ),
    [classId],
  );
  return resource.loading ? (
    <Loading />
  ) : resource.error ? (
    <ErrorState message={resource.error} retry={resource.reload} />
  ) : resource.data ? (
    <WheelConfigEditor
      key={resource.data.revision}
      classId={classId}
      config={resource.data}
      refresh={resource.reload}
    />
  ) : null;
}
function WheelConfigEditor({
  classId,
  config,
  refresh,
}: {
  classId: number;
  config: { revision: number; cells: string[] };
  refresh: () => void;
}) {
  const [cells, setCells] = useState(config.cells);
  const action = useTeachingAction();
  return (
    <Card className="space-y-4 p-5">
      <h2 className="text-xl font-semibold">Ô thử thách dùng chung cho lớp</h2>
      <p className="text-sm text-muted-foreground">
        Tối đa 100 ô; cấu hình đã quay được snapshot riêng, chỉnh sửa này không
        đổi kết quả cũ.
      </p>
      {cells.map((cell, index) => (
        <div className="flex items-start gap-2" key={index}>
          <Input
            aria-label={`Ô thử thách ${index + 1}`}
            maxLength={500}
            value={cell}
            disabled={action.pending}
            onChange={(e) =>
              setCells((old) =>
                old.map((s, i) => (i === index ? e.target.value : s)),
              )
            }
          />
          <Button
            variant="outline"
            disabled={action.pending}
            onClick={() => setCells((old) => old.filter((_, i) => i !== index))}
          >
            Xóa ô {index + 1}
          </Button>
        </div>
      ))}
      <div className="flex flex-wrap gap-3">
        <Button
          variant="outline"
          disabled={action.pending || cells.length >= 100}
          onClick={() => setCells((old) => [...old, ""])}
        >
          Thêm ô thử thách
        </Button>
        <Button
          disabled={action.pending}
          onClick={() => {
            if (cells.some((s) => !s.trim())) {
              action.setError("Mỗi ô cần có nội dung.");
              return;
            }
            void action.run(async () => {
              await api(`/api/teaching/classes/${classId}/wheel`, {
                method: "PUT",
                body: JSON.stringify({ revision: config.revision, cells }),
              });
              refresh();
            }, "Đã lưu cấu hình vòng quay.");
          }}
        >
          Lưu ô thử thách
        </Button>
      </div>
      <FormError message={action.error} />
    </Card>
  );
}
