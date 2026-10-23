import {
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
} from "@testing-library/react";
import { afterEach, expect, it, vi } from "vitest";
import { StudentIdentityEditor } from "./profile-page";
const calls = vi.hoisted(() => ({
  api: vi.fn(),
  refresh: vi.fn().mockResolvedValue(undefined),
}));
vi.mock("@/lib/api", async (original) => ({
  ...(await original<typeof import("@/lib/api")>()),
  api: calls.api,
}));
vi.mock("@/lib/session", () => ({
  useSession: () => ({ user: { id: 7, hoTen: "Tên thật", maSinhVien: null } }),
  refreshSession: calls.refresh,
  useConnection: () => "connected",
  realtime: { listen: () => () => {} },
}));
afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});
it("allows legacy accounts to supply their real student code, rejects invalid identities and surfaces conflicts", async () => {
  const reload = vi.fn();
  render(<StudentIdentityEditor reload={reload} />);
  const input = screen.getByLabelText("Mã sinh viên trong hồ sơ");
  const form = input.closest("form")!;
  fireEvent.change(input, { target: { value: "Mã không hợp lệ!" } });
  fireEvent.submit(form);
  expect(calls.api).not.toHaveBeenCalled();
  expect(screen.getByRole("alert").textContent).toContain("1–32");
  calls.api.mockRejectedValueOnce(new Error("Mã sinh viên đã tồn tại."));
  fireEvent.change(input, { target: { value: "SV_REAL_01" } });
  fireEvent.submit(form);
  await waitFor(() =>
    expect(screen.getByRole("alert").textContent).toContain("đã tồn tại"),
  );
  expect(calls.refresh).not.toHaveBeenCalled();
  calls.api.mockResolvedValueOnce({});
  fireEvent.change(input, { target: { value: "SV_REAL_02" } });
  fireEvent.submit(form);
  await waitFor(() => expect(reload).toHaveBeenCalledOnce());
  expect(calls.refresh).toHaveBeenCalledOnce();
  expect(JSON.parse(calls.api.mock.calls.at(-1)![1].body)).toEqual({
    hoTen: "Tên thật",
    maSinhVien: "SV_REAL_02",
  });
});
