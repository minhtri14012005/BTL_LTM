import { chromium, expect } from "@playwright/test";
import { createServer } from "vite";
import { randomUUID } from "node:crypto";
import { resolve } from "node:path";
import { verifyAdmin } from "./admin.mjs";
import { verifyTeaching } from "./teaching.mjs";

const [backend, classId] = process.argv.slice(2);
if (
  !backend ||
  !/^http:\/\/(?:\d{1,3}\.){3}\d{1,3}:\d+$/.test(backend) ||
  !/^\d+$/.test(classId || "")
)
  throw new Error("Requires the owned baseline HTTP fixture");
const browser = await chromium.launch({ headless: true });
let vite;
try {
  await verify(backend, "packaged");
  await verifyAdmin(browser, backend, "packaged");
  await verifyTeaching(browser, backend, "packaged");
  process.env.BACKEND_URL = backend;
  vite = await createServer({
    configFile: resolve("frontend/vite.config.ts"),
    root: resolve("frontend"),
    server: { host: "127.0.0.1", port: 5173, strictPort: true },
  });
  await vite.listen();
  await verify("http://127.0.0.1:5173", "vite");
  await verifyAdmin(browser, "http://127.0.0.1:5173", "vite");
  await verifyTeaching(browser, "http://127.0.0.1:5173", "vite");
  console.log(
    "Frontend browser acceptance PASS: packaged + Vite, desktop + mobile, real HTTP/session/CSRF/WS.",
  );
} finally {
  await vite?.close();
  await browser.close();
}

async function verify(base, name) {
  const context = await browser.newContext({
    viewport: { width: 1440, height: 1000 },
  });
  const page = await context.newPage();
  await page.addInitScript(() => {
    window.__lessonSockets = [];
    const NativeSocket = window.WebSocket;
    window.WebSocket = class extends NativeSocket {
      constructor(...args) {
        super(...args);
        if (new URL(String(args[0]), location.href).pathname === "/ws")
          window.__lessonSockets.push(this);
      }
    };
  });
  const errors = [],
    sockets = [];
  page.on("pageerror", (e) => errors.push(e.message));
  page.on("websocket", (socket) => {
    if (new URL(socket.url()).pathname === "/ws") {
      socket.received = [];
      socket.on("framereceived", ({ payload }) => {
        try {
          socket.received.push(JSON.parse(String(payload)));
        } catch {}
      });
      sockets.push(socket);
    }
  });
  async function fits() {
    expect(
      await page.evaluate(
        () => document.documentElement.scrollWidth <= innerWidth,
      ),
    ).toBe(true);
    expect(
      await page.evaluate(
        () => getComputedStyle(document.body).backgroundColor,
      ),
    ).toBe("rgb(248, 249, 252)");
    const grid = page.locator(".course-grid");
    if (await grid.count()) {
      const columns = await grid.evaluate(
        (element) =>
          getComputedStyle(element).gridTemplateColumns.split(" ").length,
      );
      const width = page.viewportSize().width;
      expect(columns).toBe(
        width <= 560 ? 1 : width <= 1100 ? 2 : width < 1450 ? 3 : 4,
      );
    }
  }
  async function signIn(username) {
    await page.goto(`${base}/login`);
    await page.getByLabel("Tên đăng nhập", { exact: true }).fill(username);
    await page.getByLabel("Mật khẩu", { exact: true }).fill("BrowserPass123!");
    await page.getByRole("button", { name: "Vào không gian học tập" }).click();
    await expect(page.getByRole("heading", { name: /Xin chào/ })).toBeVisible();
    await expect(page.locator('[title="Đã kết nối trực tiếp"]')).toBeAttached();
  }
  async function signOut() {
    await page.getByRole("button", { name: "Đăng xuất", exact: true }).click();
    await expect(page.getByRole("alertdialog")).toBeVisible();
    await page.getByRole("button", { name: "Xác nhận", exact: true }).click();
    await expect(
      page.getByRole("heading", { name: "Đăng nhập", exact: true }),
    ).toBeVisible();
  }
  try {
    // Deep link forwards to login, then returns to the server-authorized role route.
    await page.goto(`${base}/app/giang_vien`);
    await expect(
      page.getByRole("heading", { name: "Đăng nhập", exact: true }),
    ).toBeVisible();
    await fits();
    await page.getByLabel("Tên đăng nhập", { exact: true }).fill("ui_teacher");
    await page.getByLabel("Mật khẩu", { exact: true }).fill("WrongPass123!");
    await page.getByRole("button", { name: "Vào không gian học tập" }).click();
    await expect(page.getByRole("alert")).toBeVisible();
    await signIn("ui_teacher");
    await expect(
      page.getByText("Lập trình mạng · Nhóm thực hành giao diện", {
        exact: true,
      }),
    ).toBeVisible();
    await fits();
    expect(sockets.length).toBe(1);
    expect(new URL(sockets[0].url()).host).toBe(new URL(base).host);
    // Seed a real lesson through the existing teacher API, never fake success.
    const csrf = await (await page.request.get(`${base}/api/auth/csrf`)).json();
    const existing = await (
      await page.request.get(`${base}/api/teaching/classes/${classId}/lessons`)
    ).json();
    if (!existing.length) {
      const created = await page.request.post(
        `${base}/api/teaching/classes/${classId}/lessons`,
        {
          headers: { [csrf.headerName]: csrf.token },
          data: {
            requestId: randomUUID(),
            title: "Buổi học kết nối",
            slots: [
              { mode: "LUCKY_WHEEL", title: "Khởi động", budget: 20 },
              { mode: "QUIZ", title: "Ôn tập", budget: 40 },
              { mode: "FILL_BLANK", title: "Điền từ", budget: 40 },
            ],
          },
        },
      );
      expect(created.status()).toBe(201);
      const lesson = await created.json();
      expect(
        (
          await page.request.put(
            `${base}/api/teaching/lessons/${lesson.id}/visibility`,
            {
              headers: { [csrf.headerName]: csrf.token },
              data: { revision: lesson.revision, visible: true },
            },
          )
        ).ok(),
      ).toBe(true);
    }
    await page.locator(`a[href="/app/giang_vien/classes/${classId}"]`).click();
    await page.getByRole("button", { name: "Xem cấu trúc buổi học" }).click();
    await expect(page.getByRole("dialog")).toBeVisible();
    await expect(
      page.getByRole("cell", { name: /Điền từ vào chỗ trống/ }),
    ).toBeVisible();
    await expect
      .poll(() =>
        sockets[0].received.some(
          (e) =>
            e.kind === "ACK" &&
            e.type === "RECONNECT_LESSON" &&
            e.payload?.lesson?.games?.length === 3,
        ),
      )
      .toBe(true);
    await page.evaluate(() =>
      window.__lessonSockets[0].close(4000, "Test reconnect"),
    );
    await expect.poll(() => sockets.length).toBe(2);
    await expect
      .poll(() =>
        sockets[1].received.some(
          (e) =>
            e.kind === "ACK" &&
            e.type === "RECONNECT_LESSON" &&
            e.payload?.lesson?.games?.length === 3,
        ),
      )
      .toBe(true);
    await expect(page.getByRole("dialog")).toBeVisible();
    await page.getByRole("button", { name: "Đóng", exact: true }).click();
    await page.getByRole("link", { name: "Tài khoản", exact: true }).click();
    await page.getByRole("tab", { name: "Phiên truy cập" }).click();
    await expect(page.getByRole("tabpanel")).toContainText(
      "Phiên đăng nhập được máy chủ xác thực",
    );
    await page.getByRole("tab", { name: "Thông tin", exact: true }).click();
    expect(sockets.length).toBe(2);
    // Mobile menu, tabs, forms and modal fit and remain operable.
    for (const width of [768, 390, 320]) {
      await page.setViewportSize({ width, height: 844 });
      await fits();
    }
    await page.getByRole("button", { name: "Mở menu" }).click();
    await page.getByRole("link", { name: "Tổng quan", exact: true }).click();
    await expect(
      page.getByRole("button", { name: "Làm mới", exact: true }),
    ).toBeVisible();
    await context.setOffline(true);
    await page.getByRole("button", { name: "Làm mới", exact: true }).click();
    await expect(page.getByRole("alert")).toContainText(
      "Không kết nối được máy chủ",
    );
    await context.setOffline(false);
    await page.getByRole("button", { name: "Thử lại", exact: true }).click();
    await expect(
      page.getByRole("textbox", { name: "Tìm lớp học" }),
    ).toBeVisible();
    await page
      .getByRole("textbox", { name: "Tìm lớp học" })
      .fill("không tồn tại");
    await expect(
      page.getByRole("heading", { name: "Không tìm thấy lớp phù hợp" }),
    ).toBeVisible();
    await page.getByRole("textbox", { name: "Tìm lớp học" }).fill("");
    await fits();
    await signOut();
    await fits();
    await signIn("ui_student");
    await page
      .getByRole("combobox", { name: "Trạng thái lớp" })
      .selectOption("PENDING");
    await expect(
      page.getByRole("heading", { name: "Không tìm thấy lớp phù hợp" }),
    ).toBeVisible();
    await page
      .getByRole("combobox", { name: "Trạng thái lớp" })
      .selectOption("ALL");
    await page.getByRole("link", { name: "Xem thông tin lớp" }).click();
    await expect(
      page.getByRole("link", { name: "Vào buổi học", exact: true }),
    ).toBeVisible();
    await fits();
    await page.reload();
    await expect(
      page.getByRole("heading", {
        name: "Lập trình mạng · Nhóm thực hành giao diện",
        exact: true,
      }),
    ).toBeVisible();
    expect(
      (await page.request.get(`${base}/api/teaching/classes`)).status(),
    ).toBe(403);
    expect((await page.request.post(`${base}/api/auth/logout`)).status()).toBe(
      403,
    ); // CSRF remains enforced.
    await page.goto(`${base}/app/admin`);
    await expect(page.getByRole("alert")).toContainText("không có quyền");
    await signOut();
    await signIn("ui_empty");
    await expect(
      page.getByRole("heading", { name: "Chưa có lớp học", exact: true }),
    ).toBeVisible();
    await signOut();
    await signIn("ui_admin");
    await expect(
      page.getByText("Các lớp trong hệ thống", { exact: true }),
    ).toBeVisible();
    await fits();
    await signOut();
    if (name === "packaged") {
      await page.getByLabel("Tên đăng nhập", { exact: true }).fill("ui_legacy");
      await page
        .getByLabel("Mật khẩu", { exact: true })
        .fill("BrowserPass123!");
      await page
        .getByRole("button", { name: "Vào không gian học tập", exact: true })
        .click();
      await expect(page.getByRole("alert")).toContainText(
        "chưa có mã sinh viên",
      );
      await page
        .getByRole("textbox", { name: "Mã sinh viên trong hồ sơ", exact: true })
        .fill("UI_LEGACY_REAL");
      await page
        .getByRole("button", { name: "Lưu hồ sơ", exact: true })
        .click();
      await expect(
        page.getByRole("heading", { name: /Xin chào/ }),
      ).toBeVisible();
    } else await signIn("ui_legacy");
    expect(
      (await (await page.request.get(`${base}/api/auth/me`)).json()).maSinhVien,
    ).toBe("UI_LEGACY_REAL");
    await signOut();
    expect(errors).toEqual([]);
    console.log(
      `${name}: login/errors, role gates, real cards, search/select/dialog/tabs, desktop/tablet/mobile, logout and single WS PASS`,
    );
  } finally {
    await context.close();
  }
}
