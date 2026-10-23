import { useEffect, useState } from "react";
import { realtime } from "@/lib/session";
import type { Lesson } from "@/lib/types";
import { statusNames } from "@/lib/types";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import { Table } from "@/components/ui/table";

export function LessonStructure({ lesson }: { lesson: Lesson }) {
  const [open, setOpen] = useState(false);
  const [current, setCurrent] = useState<Lesson | null>(lesson);
  const [error, setError] = useState("");
  useEffect(() => {
    if (!open) return;
    setCurrent(lesson);
    setError("");
    return realtime.watchLesson(lesson.id, (event) => {
      if (event.type === "LESSON_ACCESS_REVOKED") {
        setCurrent(null);
        setError("Quyền xem buổi học đã được thu hồi.");
        return;
      }
      if (event.kind === "ERROR") {
        setError(event.message || "Không tải được trạng thái mới nhất.");
        return;
      }
      const snapshot = event.payload as { lesson?: Lesson } | undefined;
      if (snapshot?.lesson?.id === lesson.id) setCurrent(snapshot.lesson);
    });
  }, [open, lesson]);
  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button variant="outline" className="w-full">
          Xem cấu trúc buổi học
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogTitle className="pr-6 text-xl font-semibold">
          {lesson.title}
        </DialogTitle>
        <DialogDescription className="my-3 text-sm text-muted-foreground">
          Ba hoạt động của buổi {lesson.number}. Trạng thái và ngân sách do máy
          chủ cung cấp.
        </DialogDescription>
        {error && (
          <p role="alert" className="mb-3 text-sm text-destructive">
            {error}
          </p>
        )}
        {current && (
          <Table>
            <thead>
              <tr>
                <th>Hoạt động</th>
                <th>Trạng thái</th>
                <th>Ngân sách</th>
              </tr>
            </thead>
            <tbody>
              {current.games.map((game) => (
                <tr key={game.id}>
                  <td>
                    {game.position}. {game.modeLabel}
                  </td>
                  <td>{statusNames[game.status] || game.status}</td>
                  <td>{game.budget}</td>
                </tr>
              ))}
            </tbody>
          </Table>
        )}
      </DialogContent>
    </Dialog>
  );
}
