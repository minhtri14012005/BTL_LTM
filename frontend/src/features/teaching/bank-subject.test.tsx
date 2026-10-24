import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, expect, it, vi } from "vitest";
import { MemoryRouter } from "react-router-dom";
import { BankPage } from "./bank-page";
import { QuestionEditor } from "./question-editor";
import type { ContentMode, Question } from "./teaching-api";

const subjects = [{ id: 7, code: "LTM", name: "Lập trình mạng" }, { id: 8, code: "SQL", name: "Cơ sở dữ liệu" }];
vi.mock("@/lib/api", async original => ({
  ...(await original<typeof import("@/lib/api")>()),
  api: vi.fn(async (url: string) => {
    if (url === "/api/quizzes/subjects") return subjects;
    const params = new URL(url, "http://test").searchParams;
    const items = subjects.filter(s => !params.get("subjectId") || String(s.id) === params.get("subjectId")).map(s => ({
      id: s.id, subjectId: s.id, subjectName: s.name, subjectCode: s.code,
      title: `Quiz — Bộ ${s.id}`, mode: "QUIZ", visibility: "PUBLIC", questionCount: 10,
    }));
    return { items, totalElements: items.length };
  }),
}));
afterEach(cleanup);

it("shows subject badges, compact titles and filters subjects without hiding all-subject results", async () => {
  render(<MemoryRouter><BankPage /></MemoryRouter>);
  await screen.findByRole("button", { name: "Preview Quiz — Bộ 7" });
  expect(screen.getByRole("heading", { name: "Bộ 7" })).toBeDefined();
  expect(screen.getAllByText("Lập trình mạng")).toHaveLength(2);
  fireEvent.change(screen.getByLabelText("Lọc môn học"), { target: { value: "8" } });
  await waitFor(() => expect(screen.queryByRole("button", { name: "Preview Quiz — Bộ 7" })).toBeNull());
  expect(screen.getByRole("button", { name: "Preview Quiz — Bộ 8" })).toBeDefined();
  fireEvent.change(screen.getByLabelText("Lọc môn học"), { target: { value: "" } });
  await screen.findByRole("button", { name: "Preview Quiz — Bộ 7" });
});

it.each<ContentMode>(["QUIZ", "FILL_BLANK", "ASSIGNMENT"])("requires a catalog subject and submits its ID for %s", async mode => {
  const save = vi.fn().mockResolvedValue(undefined);
  const question: Question = mode === "QUIZ" ? {content:"Question",options:{A:"A",B:"B",C:"C",D:"D"},correctAnswer:"A"} : mode === "FILL_BLANK" ? {content:"Question {{blank}}",acceptedAnswers:["answer"],matchingPolicy:"EXACT"} : {content:"Assignment"};
  render(<QuestionEditor mode={mode} initialTitle="Bộ mới" initialQuestions={[question]} subjects={subjects} initialVisibility="PRIVATE" pending={false} error="" onSave={save} />);
  const form=screen.getByRole("button",{name:"Lưu nội dung"}).closest("form")!;
  fireEvent.submit(form);
  expect(save).not.toHaveBeenCalled();
  fireEvent.change(screen.getByLabelText("Môn học"),{target:{value:"7"}});
  fireEvent.submit(form);
  await waitFor(()=>expect(save).toHaveBeenCalledWith("Bộ mới",[question],"PRIVATE",7));
});
