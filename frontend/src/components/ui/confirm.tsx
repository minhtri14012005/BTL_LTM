import * as Alert from "@radix-ui/react-alert-dialog";
import { Button } from "./button";
import type { ReactNode } from "react";
export function Confirm({
  open,
  onOpenChange,
  title,
  description,
  onConfirm,
  pending = false,
  children,
  error,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  title: string;
  description: string;
  onConfirm: () => void;
  pending?: boolean;
  children?: ReactNode;
  error?: string;
}) {
  return (
    <Alert.Root open={open} onOpenChange={onOpenChange}>
      <Alert.Portal>
        <Alert.Overlay className="fixed inset-0 z-50 bg-foreground/30 backdrop-blur-xs" />
        <Alert.Content className="fixed left-1/2 top-1/2 z-50 max-h-[90dvh] overflow-y-auto w-[calc(100%-2rem)] max-w-md -translate-x-1/2 -translate-y-1/2 rounded-2xl bg-card p-6 shadow-lg">
          <Alert.Title className="text-xl font-semibold">{title}</Alert.Title>
          <Alert.Description className="mt-3 text-sm leading-6 text-muted-foreground">
            {description}
          </Alert.Description>
          {children}
          {error && (
            <p role="alert" className="mt-3 text-sm text-destructive">
              {error}
            </p>
          )}
          <div className="mt-6 flex flex-wrap justify-end gap-3">
            <Alert.Cancel asChild>
              <Button variant="outline" disabled={pending}>
                Ở lại
              </Button>
            </Alert.Cancel>
            <Button
              variant="destructive"
              disabled={pending}
              onClick={onConfirm}
            >
              {pending ? "Đang xử lý…" : "Xác nhận"}
            </Button>
          </div>
        </Alert.Content>
      </Alert.Portal>
    </Alert.Root>
  );
}
