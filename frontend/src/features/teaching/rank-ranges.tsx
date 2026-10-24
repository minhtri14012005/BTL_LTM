import type { Dispatch, SetStateAction } from "react";
import { Button } from "@/components/ui/button";
import { FormField } from "@/components/ui/form";
import { Input } from "@/components/ui/input";
import type { Range } from "./teaching-api";

export function RankRanges({
  ranges,
  setRanges,
  pending,
}: {
  ranges: Range[];
  setRanges: Dispatch<SetStateAction<Range[]>>;
  pending: boolean;
}) {
  function change(index: number, values: Partial<Range>) {
    setRanges((old) =>
      old.map((r, i) => (i === index ? { ...r, ...values } : r)),
    );
  }
  const last = ranges.at(-1);
  return (
    <div className="space-y-4">
      {ranges.map((r, index) => (
        <fieldset key={index} className="rounded-2xl border bg-accent/25 p-4">
          <legend className="px-2 font-semibold">Mức Rank {index + 1}</legend>
          <div className="grid items-end gap-4 sm:grid-cols-[minmax(0,2fr)_minmax(0,1fr)_minmax(0,1fr)_auto]">
            <FormField label="Tên Rank">
              {(p) => (
                <Input
                  {...p}
                  aria-label={`Tên Rank mức ${index + 1}`}
                  maxLength={100}
                  value={r.name}
                  disabled={pending}
                  onChange={(e) => change(index, { name: e.target.value })}
                />
              )}
            </FormField>
            <FormField label="Từ hạng">
              {(p) => (
                <Input
                  {...p}
                  aria-label={`Từ hạng mức ${index + 1}`}
                  type="number"
                  min={1}
                  max={2147483647}
                  step={1}
                  value={r.from || ""}
                  disabled={pending}
                  onChange={(e) =>
                    change(index, { from: Number(e.target.value) })
                  }
                />
              )}
            </FormField>
            <FormField label="Đến hạng">
              {(p) => (
                <Input
                  {...p}
                  aria-label={`Đến hạng mức ${index + 1}`}
                  type="number"
                  min={1}
                  max={2147483647}
                  step={1}
                  value={r.to ?? ""}
                  placeholder={
                    index === ranges.length - 1 ? "Không giới hạn" : "Bắt buộc"
                  }
                  disabled={pending}
                  onChange={(e) =>
                    change(index, {
                      to: e.target.value === "" ? null : Number(e.target.value),
                    })
                  }
                />
              )}
            </FormField>
            <Button
              variant="outline"
              disabled={pending}
              onClick={() =>
                setRanges((old) => old.filter((_, i) => i !== index))
              }
            >
              Xóa mức {index + 1}
            </Button>
          </div>
        </fieldset>
      ))}
      <p className="text-sm text-muted-foreground">
        Chỉ mức cuối được để trống Đến hạng để áp dụng từ hạng bắt đầu trở đi.
        Điền hạng kết thúc trước khi thêm mức tiếp theo.
      </p>
      <Button
        variant="outline"
        disabled={pending || ranges.length >= 100 || last?.to === null}
        onClick={() =>
          setRanges((old) => [
            ...old,
            { name: "", from: (old.at(-1)?.to ?? 0) + 1, to: null },
          ])
        }
      >
        Thêm mức Rank
      </Button>
    </div>
  );
}
