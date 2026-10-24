import { expect as baseExpect } from "@playwright/test";
import { randomUUID } from "node:crypto";
const expect = baseExpect.configure({ timeout: 20000 });

export async function verifyAccountManagement(browser, base, variant) {
  const tag = variant === "packaged" ? "MGPKG" : "MGDEV",
    contexts = [];
  async function context() {
    const c = await browser.newContext({
      viewport: { width: 1440, height: 1000 },
    });
    contexts.push(c);
    return c;
  }
  async function call(c, method, path, body) {
    const headers = {};
    if (method !== "GET") {
      const token = await (await c.request.get(`${base}/api/auth/csrf`)).json();
      headers[token.headerName] = token.token;
    }
    const response = await c.request.fetch(`${base}${path}`, {
      method,
      headers,
      ...(body === undefined ? {} : { data: body }),
    });
    expect(response.ok(), `${method} ${path}: ${await response.text()}`).toBe(
      true,
    );
    const text = await response.text();
    return text ? JSON.parse(text) : null;
  }
  const login = (c, username, password = "BrowserPass123!") =>
    call(c, "POST", "/api/auth/login", { username, password });
  const confirm = async (page) =>
    page
      .getByRole("alertdialog")
      .getByRole("button", { name: "Xác nhận", exact: true })
      .click();
  try {
    const admin = await context();
    await login(admin, "ui_admin");
    const original = await call(admin, "POST", "/api/admin/teachers", {
      username: `${tag}_teacher`,
      hoTen: `Giảng viên ${tag}`,
      password: "BrowserPass123!",
    });
    const replacement = await call(admin, "POST", "/api/admin/teachers", {
      username: `${tag}_replacement`,
      hoTen: `Tiếp quản ${tag}`,
      password: "BrowserPass123!",
    });
    const adminPage = await admin.newPage();
    await adminPage.goto(`${base}/app/admin/users?role=GIANG_VIEN`);
    let row = adminPage.getByRole("row").filter({ hasText: `${tag}_teacher` });
    await row.getByRole("button", { name: "Chỉnh sửa", exact: true }).click();
    await adminPage
      .getByLabel("Họ tên", { exact: true })
      .fill(`Giảng viên đã sửa ${tag}`);
    await adminPage
      .getByLabel("Username", { exact: true })
      .fill(`${tag}_renamed`);
    await adminPage
      .getByRole("button", { name: "Lưu thay đổi", exact: true })
      .click();
    row = adminPage.getByRole("row").filter({ hasText: `${tag}_renamed` });
    await expect(row).toBeVisible();
    const teacher = await context();
    await login(teacher, `${tag}_renamed`);
    const teacherPage = await teacher.newPage();
    await teacherPage.goto(`${base}/app/giang_vien`);
    await row
      .getByRole("button", { name: "Đặt lại mật khẩu", exact: true })
      .click();
    await adminPage
      .getByLabel("Mật khẩu mới", { exact: true })
      .fill("ReplacementPass456!");
    await adminPage
      .getByRole("button", { name: "Lưu thay đổi", exact: true })
      .click();
    await expect
      .poll(async () =>
        (await teacher.request.get(`${base}/api/auth/me`)).status(),
      )
      .toBe(401);
    await login(teacher, `${tag}_renamed`, "ReplacementPass456!");
    const offerings = await call(admin, "GET", "/api/admin/academic/offerings");
    const course = await call(admin, "POST", "/api/admin/academic/classes", {
      offeringId: offerings[0].id,
      code: tag,
      name: `Lớp tiếp quản ${tag}`,
      requiredLessons: 3,
    });
    await call(
      admin,
      "PUT",
      `/api/admin/academic/classes/${course.id}/teachers/${original.id}`,
    );
    const room = await call(
      teacher,
      "GET",
      `/api/teaching/classes/${course.id}/room`,
    );
    const students = [];
    for (let i = 1; i <= 2; i++) {
      const c = await context(),
        username = `${tag}_student${i}`;
      await call(c, "POST", "/api/auth/register", {
        username,
        hoTen: `Sinh viên ${tag} ${i}`,
        maSinhVien: username,
        password: "BrowserPass123!",
      });
      await login(c, username);
      await call(c, "POST", "/api/student/classes/join", {
        requestId: randomUUID(),
        code: room.joinCode,
      });
      students.push(c);
    }
    const classUrl = `${base}/app/giang_vien/classes/${course.id}`;
    await teacherPage.goto(`${classUrl}/members`);
    await teacherPage
      .getByRole("button", { name: "Duyệt tất cả (2)", exact: true })
      .click();
    await confirm(teacherPage);
    await expect(teacherPage.getByRole("status")).toContainText(
      "Đã duyệt 2 yêu cầu",
    );
    await teacherPage
      .getByRole("button", { name: `Loại ${tag}_STUDENT1`, exact: true })
      .click();
    await confirm(teacherPage);
    const restore = teacherPage.getByRole("button", {
      name: `Khôi phục ${tag}_STUDENT1`,
      exact: true,
    });
    await restore.click();
    await confirm(teacherPage);
    await expect(restore).toHaveCount(0);
    await teacherPage.goto(`${classUrl}/ranks`);
    await teacherPage
      .getByRole("button", { name: "Thêm mức Rank", exact: true })
      .click();
    await teacherPage.getByLabel("Tên Rank mức 1", { exact: true }).fill("Sắt");
    await expect(
      teacherPage.getByLabel("Đến hạng mức 1", { exact: true }),
    ).toHaveValue("");
    await teacherPage
      .getByRole("button", { name: "Lưu Rank", exact: true })
      .click();
    await expect
      .poll(
        async () =>
          (
            await call(
              teacher,
              "GET",
              `/api/teaching/classes/${course.id}/rank-config`,
            )
          ).ranges,
      )
      .toEqual([{ name: "Sắt", from: 1, to: null }]);
    await teacherPage.setViewportSize({ width: 390, height: 844 });
    expect(
      await teacherPage.evaluate(
        () => document.documentElement.scrollWidth <= innerWidth,
      ),
    ).toBe(true);
    await teacherPage.screenshot({
      path: `.cache/management-${variant}-rank-mobile.png`,
      fullPage: true,
    });
    const studentPage = await students[0].newPage();
    await studentPage.goto(`${base}/app/sinh_vien/classes/${course.id}`);
    await expect(
      studentPage.getByRole("heading", { name: course.name, exact: true }),
    ).toBeVisible();
    await row
      .getByRole("button", { name: "Xóa tài khoản", exact: true })
      .click();
    await confirm(adminPage);
    const message =
      "Lớp chưa có giảng viên tiếp quản. Vui lòng chờ quản trị viên phân công giảng viên mới.";
    await expect(studentPage.getByText(message, { exact: true })).toBeVisible();
    await studentPage.setViewportSize({ width: 390, height: 844 });
    expect(
      await studentPage.evaluate(
        () => document.documentElement.scrollWidth <= innerWidth,
      ),
    ).toBe(true);
    await studentPage.screenshot({
      path: `.cache/management-${variant}-handover-mobile.png`,
      fullPage: true,
    });
    await adminPage.goto(`${base}/app/admin/classes`);
    await adminPage
      .getByRole("checkbox", {
        name: "Chỉ lớp chưa có giảng viên hợp lệ",
        exact: true,
      })
      .check();
    await expect(
      adminPage.getByRole("row").filter({ hasText: course.name }),
    ).toContainText("Chưa có giảng viên phụ trách");
    await adminPage.goto(`${base}/app/admin/assignments`);
    await adminPage
      .getByLabel("Giảng viên được phân công", { exact: true })
      .selectOption(String(replacement.id));
    await adminPage
      .getByRole("checkbox", { name: new RegExp(course.name) })
      .check();
    await adminPage
      .getByRole("button", { name: "Phân công 1 lớp", exact: true })
      .click();
    await expect(studentPage.getByText(message, { exact: true })).toHaveCount(
      0,
    );
    expect(
      (await call(students[0], "GET", `/api/student/classes/${course.id}/room`))
        .joinCode,
    ).toBe(room.joinCode);
    console.log(
      `${variant}: teacher edit/password/delete, bulk approve, restore, unlimited Rank, realtime handover, mobile layouts PASS`,
    );
  } finally {
    for (const c of contexts) await c.close();
  }
}
