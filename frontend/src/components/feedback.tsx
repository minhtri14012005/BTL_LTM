import { AlertCircle, BookOpen, LoaderCircle } from "lucide-react";
import { Button } from "./ui/button";
import { Card } from "./ui/card";
export function Loading({ label = "Đang tải dữ liệu…" }: { label?: string }) {
  return (
    <div role="status" className="space-y-4 py-6">
      <div className="flex items-center gap-2 text-muted-foreground">
        <LoaderCircle className="size-4 animate-spin" />
        {label}
      </div>
      <div className="grid gap-5 sm:grid-cols-2 xl:grid-cols-3">
        {[1, 2, 3].map((i) => (
          <div key={i} className="h-48 animate-pulse rounded-2xl bg-muted" />
        ))}
      </div>
    </div>
  );
}
export function ErrorState({
  message,
  retry,
}: {
  message: string;
  retry?: () => void;
}) {
  return (
    <Card role="alert" className="flex flex-col items-start gap-4 p-6">
      <AlertCircle className="size-6 text-destructive" />
      <p className="leading-6">{message}</p>
      {retry && (
        <Button variant="outline" onClick={retry}>
          Thử lại
        </Button>
      )}
    </Card>
  );
}
export function EmptyState({
  title,
  description,
}: {
  title: string;
  description: string;
}) {
  return (
    <Card className="flex flex-col items-center gap-3 border-dashed px-6 py-12 text-center">
      <div className="rounded-2xl bg-accent p-4">
        <BookOpen className="size-7 text-primary" />
      </div>
      <h2 className="text-lg font-semibold">{title}</h2>
      <p className="max-w-md text-sm leading-6 text-muted-foreground">
        {description}
      </p>
    </Card>
  );
}
