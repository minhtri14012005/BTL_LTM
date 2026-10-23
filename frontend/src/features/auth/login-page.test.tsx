import {
  cleanup,
  render,
  screen,
  fireEvent,
  waitFor,
} from "@testing-library/react";
import { MemoryRouter, Outlet, Route, Routes } from "react-router-dom";
import { afterEach, expect, it, vi } from "vitest";
import { LoginPage } from "./login-page";
import { RoleGate } from "@/app";
const mocks = vi.hoisted(() => ({
  login: vi.fn(),
  register: vi.fn(),
  user: null as null | { role: string },
}));
vi.mock("@/lib/api", async (original) => ({
  ...(await original<typeof import("@/lib/api")>()),
  post: mocks.register,
}));
vi.mock("@/lib/session", () => ({
  useSession: () => ({ user: mocks.user }),
  login: mocks.login,
  refreshSession: vi.fn(),
}));
afterEach(() => {
  cleanup();
  mocks.user = null;
});
it("keeps registration conflicts visible and never logs in an account that was not created", async () => {
  mocks.login.mockClear();
  mocks.register.mockRejectedValueOnce(new Error("Mã sinh viên đã tồn tại."));
  render(
    <MemoryRouter>
      <LoginPage />
    </MemoryRouter>,
  );
  fireEvent.click(
    screen.getByRole("button", { name: "Đăng ký sinh viên" }),
  );
  for (const [label, value] of [
    ["Họ và tên", "Sinh viên thật"],
    ["Mã sinh viên", "SV_TEST"],
    ["Tên đăng nhập", "student_new"],
    ["Mật khẩu", "LongPass123!"],
  ])
    fireEvent.change(screen.getByLabelText(label, { exact: true }), {
      target: { value },
    });
  fireEvent.click(
    screen.getByRole("button", {
      name: "Tạo tài khoản sinh viên",
    }),
  );
  await waitFor(() =>
    expect(screen.getByRole("alert").textContent).toContain("đã tồn tại"),
  );
  expect(mocks.login).not.toHaveBeenCalled();
});
it("validates credentials, toggles visibility and exposes server errors", async () => {
  mocks.login.mockRejectedValue(new Error("Thông tin đăng nhập không đúng."));
  render(
    <MemoryRouter>
      <LoginPage />
    </MemoryRouter>,
  );
  const submit = screen.getByRole("button", {
    name: /Vào không gian/,
  }) as HTMLButtonElement;
  expect(submit.disabled).toBe(true);
  fireEvent.change(screen.getByLabelText("Tên đăng nhập"), {
    target: { value: "teacher" },
  });
  fireEvent.change(screen.getByLabelText("Mật khẩu"), {
    target: { value: "incorrect" },
  });
  fireEvent.click(screen.getByRole("button", { name: "Hiện mật khẩu" }));
  expect((screen.getByLabelText("Mật khẩu") as HTMLInputElement).type).toBe(
    "text",
  );
  fireEvent.click(submit);
  await waitFor(() =>
    expect(screen.getByRole("alert").textContent).toContain("không đúng"),
  );
  expect(mocks.login).toHaveBeenCalledWith("teacher", "incorrect");
  expect(submit.disabled).toBe(false);
});
it("blocks a route belonging to another role", () => {
  mocks.user = { role: "SINH_VIEN" };
  render(
    <MemoryRouter initialEntries={["/admin"]}>
      <Routes>
        <Route element={<RoleGate role="ADMIN" />}>
          <Route
            path="/admin"
            element={
              <>
                <Outlet />
                <p>Private administration</p>
              </>
            }
          />
        </Route>
      </Routes>
    </MemoryRouter>,
  );
  expect(screen.queryByText("Private administration")).toBeNull();
  expect(screen.getByRole("alert").textContent).toContain("không có quyền");
});
it("returns a new role to its own home after signing out of another role", () => {
  mocks.user = { role: "SINH_VIEN" };
  render(
    <MemoryRouter
      initialEntries={[
        { pathname: "/login", state: { from: "/app/giang_vien" } },
      ]}
    >
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/app/sinh_vien" element={<p>Student home</p>} />
      </Routes>
    </MemoryRouter>,
  );
  expect(screen.getByText("Student home")).toBeDefined();
});
