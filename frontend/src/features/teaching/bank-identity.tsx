import { BookOpen } from "lucide-react";
import type { Bank } from "./teaching-api";

export function bankDisplayTitle(title: string) {
  return title.replace(/^(?:Quiz|Điền từ(?: vào chỗ trống)?|Bài tập)\s*[—–:-]\s*/iu, "") || title;
}

export function BankIdentity({ bank }: { bank: Pick<Bank, "visibility" | "subjectName" | "subjectCode"> }) {
  return <div className="flex min-w-0 flex-wrap items-center gap-2 text-xs font-semibold">
    <span className="rounded-full border border-primary/15 bg-white/75 px-2.5 py-1 text-primary">{bank.visibility}</span>
    <span title={bank.subjectCode} className="inline-flex min-w-0 max-w-full items-center gap-1.5 rounded-full bg-primary/10 px-2.5 py-1 text-primary">
      <BookOpen aria-hidden="true" className="h-3.5 w-3.5 shrink-0" />
      <span className="break-words">{bank.subjectName}</span>
    </span>
  </div>;
}
