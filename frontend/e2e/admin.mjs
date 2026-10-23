import { expect } from "@playwright/test";

export async function verifyAdmin(browser, base, variant) {
  const context = await browser.newContext({
    viewport: { width: 1440, height: 1000 },
  });
  const page = await context.newPage(),
    errors = [];
  page.on("pageerror", (error) => errors.push(error.message));
  const code = variant === "packaged" ? "A14PKG" : "A14DEV";
  const year = `Năm học ${code}`,
    updatedYear = `${year} cập nhật`,
    semester = `Học kỳ ${code}`,
    subject = `Môn học ${code}`;
  const teacher = `teacher_${code.toLowerCase()}`;
  async function fits() {
    expect(
      await page.evaluate(
        () => document.documentElement.scrollWidth <= innerWidth,
      ),
    ).toBe(true);
  }
  async function tab(name) {
    await page.getByRole("tab", { name }).click();
  }
  async function go(name) {
    await page
      .getByRole("navigation", { name: "Quản lý đào tạo" })
      .getByRole("link", { name, exact: true })
      .click();
  }
  try {
    await page.goto(`${base}/login`);
    await page.getByLabel("Tên đăng nhập", { exact: true }).fill("ui_admin");
    await page.getByLabel("Mật khẩu", { exact: true }).fill("BrowserPass123!");
    await page.getByRole("button", { name: "Vào không gian học tập" }).click();
    await page.getByRole("link", { name: "Đào tạo", exact: true }).click();
    await page.getByLabel("Mã năm học", { exact: true }).fill(code);
    await page.getByLabel("Tên năm học", { exact: true }).fill("   ");
    await page
      .getByRole("button", { name: "Tạo năm học", exact: true })
      .click();
    await expect(page.getByRole("alert")).toContainText("khoảng trắng");
    await page.getByLabel("Tên năm học", { exact: true }).fill(year);
    await page
      .getByRole("button", { name: "Tạo năm học", exact: true })
      .click();
    await expect(
      page.getByRole("cell", { name: year, exact: true }),
    ).toBeVisible();
    await page
      .getByRole("button", { name: `Sửa ${year}`, exact: true })
      .click();
    await page.getByLabel("Tên năm học", { exact: true }).fill(updatedYear);
    await page
      .getByRole("button", { name: "Lưu thay đổi", exact: true })
      .click();
    await expect(
      page.getByRole("cell", { name: updatedYear, exact: true }),
    ).toBeVisible();
    // A duplicate is rejected by the real server and is never shown as a success.
    await page.getByLabel("Mã năm học", { exact: true }).fill(code);
    await page.getByLabel("Tên năm học", { exact: true }).fill(year);
    await page
      .getByRole("button", { name: "Tạo năm học", exact: true })
      .click();
    await expect(page.getByRole("alert")).toContainText("đã tồn tại");
    await tab("2. Học kỳ");
    await page
      .getByLabel("Năm học", { exact: true })
      .selectOption({ label: `${updatedYear} (${code})` });
    await page.getByLabel("Mã học kỳ", { exact: true }).fill("HK1");
    await page.getByLabel("Tên học kỳ", { exact: true }).fill(semester);
    await page.getByRole("button", { name: "Tạo học kỳ", exact: true }).click();
    await expect(
      page.getByRole("cell", { name: semester, exact: true }),
    ).toBeVisible();
    await tab("3. Môn học");
    await page.getByLabel("Mã môn học", { exact: true }).fill(code);
    await page.getByLabel("Tên môn học", { exact: true }).fill(subject);
    await page
      .getByRole("button", { name: "Tạo môn học", exact: true })
      .click();
    await expect(
      page.getByRole("cell", { name: subject, exact: true }),
    ).toBeVisible();
    await tab("4. Môn theo học kỳ");
    await page
      .getByLabel("Năm học", { exact: true })
      .selectOption({ label: updatedYear });
    await page
      .getByLabel("Học kỳ", { exact: true })
      .selectOption({ label: semester });
    await page
      .getByLabel("Môn học", { exact: true })
      .selectOption({ label: `${subject} (${code})` });
    await page
      .getByRole("button", { name: "Mở môn theo học kỳ", exact: true })
      .click();
    await expect(
      page.getByRole("cell", {
        name: `${updatedYear} / ${semester} / ${subject}`,
        exact: true,
      }),
    ).toBeVisible();
    await page
      .getByRole("link", { name: "Tiếp tục: Tạo lớp & phân công" })
      .click();
    for (const n of [1, 2]) {
      await page
        .getByLabel("Môn theo học kỳ", { exact: true })
        .selectOption({ label: `${updatedYear} / ${semester} / ${subject}` });
      await page.getByLabel("Mã lớp", { exact: true }).fill(`${code}_${n}`);
      await page
        .getByLabel("Tên lớp", { exact: true })
        .fill(`Lớp ${code} ${n}`);
      await page
        .getByLabel("Số buổi bắt buộc", { exact: true })
        .fill(String(n + 5));
      await page
        .getByRole("button", { name: "Tạo lớp học", exact: true })
        .click();
      await expect(
        page.getByRole("link", { name: `Lớp ${code} ${n}`, exact: true }),
      ).toBeVisible();
    }
    await page.reload();
    await expect(
      page.getByRole("row").filter({ hasText: `Lớp ${code} 1` }),
    ).toContainText("6");
    await expect(
      page.getByRole("row").filter({ hasText: `Lớp ${code} 2` }),
    ).toContainText("7");
    await go("Tài khoản đào tạo");
    await tab("Giảng viên");
    await page
      .getByLabel("Tên đăng nhập giảng viên", { exact: true })
      .fill(teacher);
    await page
      .getByLabel("Họ tên giảng viên", { exact: true })
      .fill(`Giảng viên ${code}`);
    await page
      .getByLabel("Mật khẩu ban đầu", { exact: true })
      .fill("TeacherPass123!");
    await page
      .getByRole("button", { name: "Cấp tài khoản giảng viên", exact: true })
      .click();
    await expect(
      page.getByRole("cell", { name: teacher, exact: true }),
    ).toBeVisible();
    await expect(
      page.getByLabel("Mật khẩu ban đầu", { exact: true }),
    ).toHaveValue("");
    await go("Lớp & phân công");
    await page
      .getByRole("textbox", { name: "Tìm giảng viên phân công" })
      .fill(teacher);
    await page.getByRole("button", { name: "Tìm", exact: true }).click();
    await page
      .getByLabel("Giảng viên được phân công", { exact: true })
      .selectOption({ label: `Giảng viên ${code} (${teacher})` });
    for (const n of [1, 2])
      await page
        .getByRole("checkbox", { name: new RegExp(`Lớp ${code} ${n}`) })
        .check();
    await page
      .getByRole("button", { name: "Phân công 2 lớp", exact: true })
      .click();
    for (const n of [1, 2])
      await expect(
        page.getByRole("row").filter({ hasText: `Lớp ${code} ${n}` }),
      ).toContainText("1 giảng viên");
    await fits();
    await page.setViewportSize({ width: 390, height: 844 });
    await fits();
    await go("Đào tạo");
    await fits();
    await tab("1. Năm học");
    await page
      .getByRole("button", { name: `Xóa ${updatedYear}`, exact: true })
      .click();
    await page
      .getByRole("alertdialog")
      .getByRole("button", { name: "Xác nhận", exact: true })
      .click();
    await expect(page.getByRole("alert")).toContainText("đang được sử dụng");
    // A separate unused year can be deleted without removing linked data.
    await page.getByLabel("Mã năm học", { exact: true }).fill(`${code}_DEL`);
    await page
      .getByLabel("Tên năm học", { exact: true })
      .fill(`Xóa thử ${code}`);
    await page
      .getByRole("button", { name: "Tạo năm học", exact: true })
      .click();
    await page
      .getByRole("button", { name: `Xóa Xóa thử ${code}`, exact: true })
      .click();
    await page
      .getByRole("alertdialog")
      .getByRole("button", { name: "Xác nhận", exact: true })
      .click();
    await expect(
      page.getByRole("cell", { name: `Xóa thử ${code}`, exact: true }),
    ).toHaveCount(0);
    await go("Tài khoản đào tạo");
    await page
      .getByRole("textbox", { name: "Tìm tài khoản" })
      .fill("UI_STUDENT");
    await page.getByRole("button", { name: "Tìm kiếm", exact: true }).click();
    const studentRow = page.getByRole("row").filter({ hasText: "UI_STUDENT" });
    await expect(studentRow).toContainText("Nguyễn Minh Anh");
    await fits();
    await studentRow
      .getByRole("button", { name: "Đuổi học", exact: true })
      .click();
    const dialog = page.getByRole("alertdialog");
    await expect(dialog).toContainText("Nguyễn Minh Anh");
    await expect(dialog).toContainText("UI_STUDENT");
    await dialog.getByRole("button", { name: "Xác nhận", exact: true }).click();
    await expect(dialog.getByRole("alert")).toContainText("lý do");
    await page
      .getByLabel("Lý do", { exact: true })
      .fill(`Kiểm thử giao diện ${code}`);
    await dialog.getByRole("button", { name: "Xác nhận", exact: true }).click();
    await expect(studentRow).toContainText("EXPELLED");
    await page.reload();
    await page
      .getByRole("textbox", { name: "Tìm tài khoản" })
      .fill("UI_STUDENT");
    await page.getByRole("button", { name: "Tìm kiếm", exact: true }).click();
    await studentRow
      .getByRole("button", { name: "Khôi phục", exact: true })
      .click();
    await expect(dialog).toContainText("không được khôi phục tự động");
    await page
      .getByLabel("Lý do", { exact: true })
      .fill(`Khôi phục sau kiểm thử ${code}`);
    await dialog.getByRole("button", { name: "Xác nhận", exact: true }).click();
    await expect(studentRow).toContainText("ACTIVE");
    await fits();
    await page.setViewportSize({ width: 320, height: 844 });
    await fits();
    expect(errors).toEqual([]);
    console.log(
      `${variant}: ADMIN academic CRUD, dependency protection, two classes/N, teacher provisioning/multi-assignment, search, expel/restore and desktop/mobile PASS`,
    );
    // Confirm the new account really signs in and sees both persisted assignments.
    // A separate context prevents reusing the administrator cookie.
    const teacherContext = await browser.newContext();
    try {
      const teacherPage = await teacherContext.newPage();
      await teacherPage.goto(`${base}/login`);
      await teacherPage
        .getByLabel("Tên đăng nhập", { exact: true })
        .fill(teacher);
      await teacherPage
        .getByLabel("Mật khẩu", { exact: true })
        .fill("TeacherPass123!");
      await teacherPage
        .getByRole("button", { name: "Vào không gian học tập" })
        .click();
      for (const n of [1, 2])
        await expect(
          teacherPage.getByRole("heading", {
            name: `Lớp ${code} ${n}`,
            exact: true,
          }),
        ).toBeVisible();
      expect(
        (
          await teacherPage.request.get(
            `${base}/api/admin/users?role=SINH_VIEN`,
          )
        ).status(),
      ).toBe(403);
    } finally {
      await teacherContext.close();
    }
  } finally {
    await context.close();
  }
}
