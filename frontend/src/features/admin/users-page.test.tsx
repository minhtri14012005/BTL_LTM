import {
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { AdminUsersPage } from "./users-page";
import { ApiError } from "@/lib/api";
const transport = vi.hoisted(() => ({ api: vi.fn(), post: vi.fn() }));
vi.mock("@/lib/api", async (original) => ({
  ...(await original<typeof import("@/lib/api")>()),
  api: transport.api,
  post: transport.post,
}));
beforeEach(() => {
  transport.api.mockReset();
  transport.post.mockReset();
  transport.api.mockResolvedValue({
    items: [
      {
        id: 45,
        username: "student_45",
        displayName: "Nguyễn An",
        maSinhVien: "SV0045",
        role: "SINH_VIEN",
        status: "ACTIVE",
        banVersion: 9,
      },
    ],
    page: 0,
    size: 20,
    totalElements: 1,
  });
});
afterEach(cleanup);
it("requires an explicit reason and sends the displayed account version only after confirmation", async () => {
  transport.post.mockRejectedValue(
    new ApiError(409, "STALE_VERSION", "STALE_VERSION"),
  );
  render(
    <MemoryRouter>
      <AdminUsersPage />
    </MemoryRouter>,
  );
  await screen.findByText("SV0045");
  fireEvent.click(screen.getByRole("button", { name: "Đuổi học" }));
  const dialog = screen.getByRole("alertdialog");
  expect(dialog.textContent).toContain("Nguyễn An");
  expect(dialog.textContent).toContain("SV0045");
  expect(transport.post).not.toHaveBeenCalled();
  fireEvent.click(within(dialog).getByRole("button", { name: "Xác nhận" }));
  expect(transport.post).not.toHaveBeenCalled();
  expect(within(dialog).getByRole("alert").textContent).toContain("lý do");
  fireEvent.change(screen.getByLabelText("Lý do"), {
    target: { value: "Quyết định đào tạo" },
  });
  fireEvent.click(within(dialog).getByRole("button", { name: "Xác nhận" }));
  await waitFor(() =>
    expect(within(dialog).getByRole("alert").textContent).toContain(
      "đã thay đổi",
    ),
  );
  expect(transport.post).toHaveBeenCalledWith(
    "/api/admin/users/45/expel",
    expect.objectContaining({
      expectedVersion: 9,
      reason: "Quyết định đào tạo",
      requestId: expect.stringMatching(/^[a-f0-9-]{36}$/),
    }),
  );
  fireEvent.click(within(dialog).getByRole("button", { name: "Ở lại" }));
  expect(screen.getByRole("cell", { name: "Đang hoạt động" })).toBeDefined();
});
it("validates a teacher name and reports duplicate identities without pretending the account was created", async () => {
  transport.post.mockRejectedValue(
    new ApiError(
      409,
      "IDENTITY_TAKEN",
      "Tên đăng nhập hoặc mã sinh viên đã tồn tại.",
    ),
  );
  render(
    <MemoryRouter initialEntries={["/?role=GIANG_VIEN"]}>
      <AdminUsersPage />
    </MemoryRouter>,
  );
  fireEvent.click(
    screen.getByRole("button", { name: "Cấp tài khoản giảng viên" }),
  );
  fireEvent.change(screen.getByLabelText("Tên đăng nhập giảng viên"), {
    target: { value: "teacher_new" },
  });
  fireEvent.change(screen.getByLabelText("Họ tên giảng viên"), {
    target: { value: "  " },
  });
  fireEvent.change(screen.getByLabelText("Mật khẩu ban đầu"), {
    target: { value: "TeacherPass123!" },
  });
  fireEvent.click(
    screen.getByRole("button", {
      name: "Cấp tài khoản giảng viên",
    }),
  );
  expect(screen.getByRole("alert").textContent).toContain("khoảng trắng");
  expect(transport.post).not.toHaveBeenCalled();
  fireEvent.change(screen.getByLabelText("Họ tên giảng viên"), {
    target: { value: "Giảng viên mới" },
  });
  fireEvent.click(
    screen.getByRole("button", {
      name: "Cấp tài khoản giảng viên",
    }),
  );
  await waitFor(() =>
    expect(screen.getByRole("alert").textContent).toContain("đã tồn tại"),
  );
  expect(
    (screen.getByLabelText("Mật khẩu ban đầu") as HTMLInputElement).value,
  ).toBe("TeacherPass123!");
});

it("shows deleted and banned accounts accurately in the read-only all-account view", async () => {
  transport.api.mockResolvedValue({
    items: [
      {
        id: 1,
        username: "old_teacher",
        displayName: "Giảng viên cũ",
        role: "GIANG_VIEN",
        status: "ACTIVE",
        deletedAtMs: 12,
        banned: false,
      },
      {
        id: 2,
        username: "blocked_admin",
        displayName: "Quản trị bị cấm",
        role: "ADMIN",
        status: "ACTIVE",
        deletedAtMs: null,
        banned: true,
      },
    ],
    page: 0,
    size: 20,
    totalElements: 2,
  });
  render(
    <MemoryRouter initialEntries={["/?role=all"]}>
      <AdminUsersPage />
    </MemoryRouter>,
  );
  await screen.findByRole("cell", { name: "Đã xóa" });
  expect(screen.getByRole("cell", { name: "Bị cấm truy cập" })).toBeDefined();
  expect(screen.queryByRole("button", { name: "Đuổi học" })).toBeNull();
  expect(
    transport.api.mock.calls.every(
      ([url]) => !new URL(url, "http://localhost").searchParams.has("role"),
    ),
  ).toBe(true);
});
