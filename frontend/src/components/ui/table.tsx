import type { ComponentProps } from "react";
import { cn } from "@/lib/utils";
export function Table({ className, ...props }: ComponentProps<"table">) {
  return (
    <div className="w-full overflow-x-auto">
      <table
        className={cn(
          "w-full text-left text-sm [&_th]:px-4 [&_th]:py-3 [&_th]:font-medium [&_th]:text-muted-foreground [&_td]:px-4 [&_td]:py-3 [&_tr]:border-b",
          className,
        )}
        {...props}
      />
    </div>
  );
}
