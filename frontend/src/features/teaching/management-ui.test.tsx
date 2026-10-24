import {
  act,
  cleanup,
  fireEvent,
  render,
  screen,
  within,
} from "@testing-library/react";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { MemoryRouter, Routes, Route } from "react-router-dom";
import { useState } from "react";
import { TeacherActions } from "@/features/admin/teacher-actions";
import { Members } from "./class-members";
import { RankRanges } from "./rank-ranges";
import { rankError } from "./rank-panel";
import type { Member, Range } from "./teaching-api";
import { TeachingClassPage } from "./class-page";
import { realtime } from "@/lib/session";
import type { Envelope } from "@/lib/realtime";

const transport = vi.hoisted(() => ({ api: vi.fn(), post: vi.fn() }));
vi.mock("@/lib/api", async (original) => ({
  ...(await original<typeof import("@/lib/api")>()),
  ...transport,
}));
beforeEach(() => {
  transport.api.mockReset();
  transport.post.mockReset();
});
afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
});
const teacher = {
  id: 12,
  username: "teacher",
  displayName: "Giảng viên A",
  role: "GIANG_VIEN" as const,
  maSinhVien: null,
  status: "ACTIVE",
  banVersion: 4,
  deletedAtMs: null,
  banned: false,
};
it("retains the bulk result through realtime invalidation and the subsequent roster reload", async () => {
  let listener: ((e: Envelope) => void) | undefined;
  vi.spyOn(realtime, "listen").mockImplementation((callback) => {
    listener = callback;
    return () => {};
  });
  let members = [member("PENDING")];
  transport.api.mockImplementation(async (path: string) => {
    if (path.endsWith("/members")) return members;
    if (path.endsWith("/lessons")) return [];
    if (path.endsWith("/lesson-progress"))
      return { required: 3, created: 0, completed: 0 };
    if (path.endsWith("/room")) return { joinCode: "CODE" };
    return { id: 3, name: "Lớp thử", teacherIds: [12] };
  });
  let finish!: (value: unknown) => void;
  transport.post.mockReturnValue(
    new Promise((resolve) => {
      finish = resolve;
    }),
  );
  render(
    <MemoryRouter initialEntries={["/classes/3/members"]}>
      <Routes>
        <Route path="/classes/:id/:section" element={<TeachingClassPage />} />
      </Routes>
    </MemoryRouter>,
  );
  fireEvent.click(
    await screen.findByRole("button", { name: "Duyệt tất cả (1)" }),
  );
  fireEvent.click(
    within(screen.getByRole("alertdialog")).getByRole("button", {
      name: "Xác nhận",
    }),
  );
  act(() =>
    listener?.({
      kind: "EVENT",
      type: "CLASS_MEMBERSHIP_UPDATED",
      target: { kind: "CLASS", id: 3 },
    }),
  );
  expect(screen.getByRole("alertdialog")).toBeTruthy();
  members = [member("APPROVED")];
  await act(async () => finish({ approved: 1, invalid: 0, skipped: [] }));
  await screen.findByText("Đã duyệt 1 yêu cầu. Không hợp lệ: 0.");
  expect(
    screen
      .getByRole("button", { name: "Duyệt tất cả (0)" })
      .hasAttribute("disabled"),
  ).toBe(true);
});
const member = (status: string) =>
  ({
    studentId: 20,
    hoTen: "Sinh viên A",
    maSinhVien: "SV20",
    status,
    revision: 7,
  }) as Member;
it("edits the existing teacher ID and requires confirmation before soft deletion", async () => {
  const saved = vi.fn();
  transport.api.mockResolvedValue({});
  transport.post.mockResolvedValue({});
  render(
    <MemoryRouter>
      <TeacherActions user={teacher} saved={saved} />
    </MemoryRouter>,
  );
  expect(screen.queryByRole("button", { name: /cấm|ban|gỡ cấm/i })).toBeNull();
  fireEvent.click(screen.getByRole("button", { name: "Chỉnh sửa" }));
  fireEvent.change(screen.getByLabelText("Họ tên"), {
    target: { value: "Giảng viên B" },
  });
  fireEvent.change(screen.getByLabelText("Username"), {
    target: { value: "teacher_new" },
  });
  fireEvent.click(screen.getByRole("button", { name: "Lưu thay đổi" }));
  await screen.findByRole("button", { name: "Xóa tài khoản" });
  expect(transport.api).toHaveBeenCalledWith(
    "/api/admin/teachers/12",
    expect.objectContaining({
      method: "PUT",
      body: JSON.stringify({
        expectedVersion: 4,
        username: "teacher_new",
        hoTen: "Giảng viên B",
      }),
    }),
  );
  fireEvent.click(screen.getByRole("button", { name: "Xóa tài khoản" }));
  expect(transport.post).not.toHaveBeenCalled();
  const dialog = screen.getByRole("alertdialog");
  expect(dialog.textContent).toContain("giữ nguyên");
  fireEvent.click(within(dialog).getByRole("button", { name: "Xác nhận" }));
  await vi.waitFor(() =>
    expect(transport.post).toHaveBeenCalledWith(
      "/api/admin/teachers/12/delete",
      { expectedVersion: 4 },
    ),
  );
});
it("sends only the replacement password and clears the password dialog after saving", async () => {
  transport.post.mockResolvedValue({});
  render(
    <MemoryRouter>
      <TeacherActions user={teacher} saved={() => {}} />
    </MemoryRouter>,
  );
  fireEvent.click(screen.getByRole("button", { name: "Đặt lại mật khẩu" }));
  const input = screen.getByLabelText("Mật khẩu mới");
  expect(input.getAttribute("type")).toBe("password");
  fireEvent.change(input, { target: { value: "NewPass123!" } });
  fireEvent.click(screen.getByRole("button", { name: "Lưu thay đổi" }));
  await vi.waitFor(() =>
    expect(transport.post).toHaveBeenCalledWith(
      "/api/admin/teachers/12/password",
      { expectedVersion: 4, password: "NewPass123!" },
    ),
  );
  await vi.waitFor(() =>
    expect(screen.queryByLabelText("Mật khẩu mới")).toBeNull(),
  );
});
it("restores a removed membership only after confirmation using its current revision", async () => {
  transport.post.mockResolvedValue({});
  render(
    <Members classId={3} members={[member("REMOVED")]} refresh={() => {}} />,
  );
  fireEvent.click(screen.getByRole("button", { name: "Khôi phục SV20" }));
  expect(transport.post).not.toHaveBeenCalled();
  fireEvent.click(
    within(screen.getByRole("alertdialog")).getByRole("button", {
      name: "Xác nhận",
    }),
  );
  await vi.waitFor(() =>
    expect(transport.post).toHaveBeenCalledWith(
      "/api/teaching/classes/3/members/20/restore",
      expect.objectContaining({ revision: 7, requestId: expect.any(String) }),
    ),
  );
});
it("confirms one bulk operation and displays the server's approved and invalid counts", async () => {
  transport.post.mockResolvedValue({
    approved: 0,
    invalid: 1,
    skipped: [{ studentId: 20, reason: "ACCOUNT_UNAVAILABLE" }],
  });
  render(
    <Members classId={3} members={[member("PENDING")]} refresh={() => {}} />,
  );
  fireEvent.click(screen.getByRole("button", { name: "Duyệt tất cả (1)" }));
  expect(transport.post).not.toHaveBeenCalled();
  fireEvent.click(
    within(screen.getByRole("alertdialog")).getByRole("button", {
      name: "Xác nhận",
    }),
  );
  await screen.findByText("Đã duyệt 0 yêu cầu. Không hợp lệ: 1.");
  expect(screen.getByRole("status").textContent).toContain(
    "Tài khoản đã bị hạn chế hoặc xóa",
  );
  expect(transport.post).toHaveBeenCalledTimes(1);
});
it("keeps an unlimited final rank as null and rejects unordered or unlimited middle ranks", () => {
  function Editor() {
    const [ranges, setRanges] = useState<Range[]>([
      { name: "Vàng", from: 1, to: 10 },
      { name: "Sắt", from: 11, to: null },
    ]);
    return <RankRanges ranges={ranges} setRanges={setRanges} pending={false} />;
  }
  render(<Editor />);
  expect(screen.getByText("Mức Rank 2")).toBeTruthy();
  expect(
    (screen.getByLabelText("Đến hạng mức 2") as HTMLInputElement).value,
  ).toBe("");
  expect(
    screen
      .getByRole("button", { name: "Thêm mức Rank" })
      .hasAttribute("disabled"),
  ).toBe(true);
  expect(
    rankError([
      { name: "Vàng", from: 1, to: 10 },
      { name: "Sắt", from: 11, to: null },
    ]),
  ).toBe("");
  expect(
    rankError([
      { name: "Vàng", from: 1, to: null },
      { name: "Sắt", from: 11, to: 20 },
    ]),
  ).not.toBe("");
  expect(
    rankError([
      { name: "Sắt", from: 11, to: 20 },
      { name: "Vàng", from: 1, to: 10 },
    ]),
  ).not.toBe("");
});
