import {
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
} from "@testing-library/react";
import { MemoryRouter, Routes, Route } from "react-router-dom";
import { afterEach, beforeEach, it, expect, vi } from "vitest";
import { StudentDashboard } from "./dashboard";
import { StudentClassPage } from "./class-page";
import { StudentBoardsPage } from "./boards-page";
const api = vi.hoisted(() => vi.fn());
vi.mock("@/lib/api", async (original) => ({
  ...(await original<typeof import("@/lib/api")>()),
  api,
}));
vi.mock("@/lib/session", () => ({
  useSession: () => ({ user: { id: 7, hoTen: "An", maSinhVien: "SV7" } }),
  useConnection: () => "connected",
  realtime: { listen: () => () => {} },
}));
const member = {
  courseClassId: 12,
  className: "Lớp B",
  classCode: "B",
  subjectId: 3,
  academicYearId: 4,
  subjectName: "Môn B",
  academicYearName: "2026",
  requiredLessons: 6,
  status: "APPROVED",
};
beforeEach(() => {
  api.mockReset();
});
afterEach(cleanup);
it("keeps the student overview focused on memberships without requesting aggregate profile scores", async () => {
  api.mockResolvedValue([member]);
  render(
    <MemoryRouter>
      <StudentDashboard />
    </MemoryRouter>,
  );
  await screen.findByText("1 lớp đã được duyệt");
  expect(api.mock.calls.every(([url]) => url === "/api/student/classes")).toBe(
    true,
  );
  expect(screen.queryByText("Điểm tích lũy")).toBeNull();
  expect(screen.queryByRole("textbox", { name: "Mã tham gia lớp" })).toBeNull();
  fireEvent.click(screen.getByRole("button", { name: "Tham gia bằng mã lớp" }));
  expect(screen.getByRole("dialog")).toBeDefined();
});
it("uses the exact class board and all paginated attended history, including hidden lessons", async () => {
  api.mockImplementation((url: string) =>
    Promise.resolve(
      url === "/api/student/classes"
        ? [member]
        : url.endsWith("/lessons")
          ? []
          : url.endsWith("/leaderboard")
            ? {
                students: [
                  { userId: 7, score: 87.5 },
                  { userId: 8, score: 900 },
                ],
              }
            : url.includes("page=0")
              ? {
                  items: [{ status: "COMPLETED" }, { status: "RUNNING" }],
                  totalElements: 51,
                }
              : { items: [{ status: "COMPLETED" }], totalElements: 51 },
    ),
  );
  render(
    <MemoryRouter initialEntries={["/classes/12"]}>
      <Routes>
        <Route path="classes/:id" element={<StudentClassPage />} />
      </Routes>
    </MemoryRouter>,
  );
  await screen.findByText("87.5");
  expect(screen.getByText("2/6")).toBeDefined();
  expect(api).toHaveBeenCalledWith(
    "/api/learning/history?classId=12&page=1&size=50",
    expect.anything(),
  );
  expect(screen.queryByText("900")).toBeNull();
});
it("keeps subject/year scope tied to the selected class, allows clearing and survives URL state", async () => {
  api.mockImplementation((url: string) =>
    Promise.resolve(
      url === "/api/student/classes"
        ? [
            { ...member, courseClassId: 1, subjectId: 1, academicYearId: 2 },
            member,
          ]
        : { students: [] },
    ),
  );
  render(
    <MemoryRouter initialEntries={["/boards?classId=12"]}>
      <StudentBoardsPage />
    </MemoryRouter>,
  );
  await screen.findByRole("combobox", { name: "Phạm vi bảng xếp hạng" });
  fireEvent.change(screen.getByLabelText("Phạm vi bảng xếp hạng"), {
    target: { value: "global" },
  });
  await waitFor(() =>
    expect(api).toHaveBeenCalledWith(
      "/api/learning/leaderboard?subjectId=3&academicYearId=4",
      expect.anything(),
    ),
  );
  fireEvent.change(screen.getByLabelText("Môn và năm học"), {
    target: { value: "" },
  });
  await waitFor(() =>
    expect(
      (screen.getByLabelText("Môn và năm học") as HTMLSelectElement).value,
    ).toBe(""),
  );
});
