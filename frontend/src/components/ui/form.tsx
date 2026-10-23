import { useId, type ReactNode } from "react";
export function FormField({
  label,
  error,
  hint,
  children,
}: {
  label: string;
  error?: string;
  hint?: string;
  children: (props: {
    id: string;
    "aria-invalid": boolean;
    "aria-describedby": string | undefined;
  }) => ReactNode;
}) {
  const id = useId(),
    description = error || hint;
  return (
    <div className="space-y-2">
      <label className="block text-sm font-medium" htmlFor={id}>
        {label}
      </label>
      {children({
        id,
        "aria-invalid": !!error,
        "aria-describedby": description ? `${id}-description` : undefined,
      })}
      {description && (
        <p
          id={`${id}-description`}
          className={
            error ? "text-sm text-destructive" : "text-sm text-muted-foreground"
          }
        >
          {description}
        </p>
      )}
    </div>
  );
}
