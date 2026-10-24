import { expect } from "@playwright/test";
import { randomUUID } from "node:crypto";
export async function verifyLessonManagement({
  page,
  teacher,
  admin,
  student,
  base,
  course,
  teacherId,
  call,
  tag,
}) {
  student = {
    ...student,
    id: (await call(student.context, "GET", "/api/auth/me")).id,
  };
  const sources = await call(
    teacher,
    "GET",
    `/api/teaching/classes/${course.id}/lessons`,
  );
  const source = sources.find(
    (l) => l.status === "COMPLETED" && l.games[2].mode === "FILL_BLANK",
  );
  const createdClass = await call(
    admin,
    "POST",
    "/api/admin/academic/classes",
    {
      offeringId: course.offeringId,
      code: `${tag}_COPY`,
      name: `Sao chép ${tag}`,
      requiredLessons: 2,
    },
  );
  await call(
    admin,
    "PUT",
    `/api/admin/academic/classes/${createdClass.id}/teachers/${teacherId}`,
  );
  const listUrl = `${base}/app/giang_vien/classes/${createdClass.id}/lessons`;
  await page.goto(`${listUrl}/new`);
  await page.getByLabel("Cách tạo buổi", { exact: true }).selectOption("copy");
  await page
    .getByLabel("Buổi nguồn cùng môn học", { exact: true })
    .selectOption(String(source.id));
  await expect(page.getByLabel("Tên buổi học", { exact: true })).toHaveValue(
    source.title,
  );
  await page.getByLabel("Tên buổi học", { exact: true }).fill(`Bản sao ${tag}`);
  await page.getByLabel("Số câu trò 2", { exact: true }).fill("1");
  await page.getByLabel("Giây mỗi câu trò 2", { exact: true }).fill("30");
  await page.getByRole("button", { name: "Tạo buổi học", exact: true }).click();
  await expect(page).toHaveURL(listUrl);
  let lessons = await call(
    teacher,
    "GET",
    `/api/teaching/classes/${createdClass.id}/lessons`,
  );
  expect(lessons).toHaveLength(1);
  const clone = lessons[0];
  expect(clone.number).toBe(1);
  expect(clone.status).toBe("DRAFT");
  expect(clone.games[1].questionCount).toBe(1);
  expect(clone.games[1].durationMs).toBe(30000);
  await page.getByText("Quản lý buổi", { exact: true }).click();
  await page
    .getByRole("link", { name: "Chỉnh sửa cấu hình", exact: true })
    .click();
  await expect(page).toHaveURL(
    `${base}/app/giang_vien/lessons/${clone.id}/settings`,
  );
  await page
    .getByLabel("Tên buổi học", { exact: true })
    .fill(`Đã chỉnh ${tag}`);
  await page
    .getByRole("button", { name: "Lưu kế hoạch buổi", exact: true })
    .click();
  await expect(
    page.getByText("Đã lưu cấu hình buổi học.", { exact: true }),
  ).toBeVisible();
  await page.screenshot({
    path: `.cache/lesson-settings-${tag}.png`,
    fullPage: true,
  });
  await page.setViewportSize({ width: 390, height: 844 });
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= window.innerWidth,
    ),
  ).toBe(true);
  await page.screenshot({
    path: `.cache/lesson-settings-mobile-${tag}.png`,
    fullPage: true,
  });
  await page.setViewportSize({ width: 1440, height: 1000 });
  expect(
    (await call(teacher, "GET", `/api/teaching/lessons/${source.id}`)).title,
  ).toBe(source.title);
  await page.goto(listUrl);
  await page.getByText("Quản lý buổi", { exact: true }).click();
  await page.getByRole("button", { name: "Xóa buổi", exact: true }).click();
  await expect(page.getByRole("alertdialog")).toContainText(
    `Buổi 1: Đã chỉnh ${tag}`,
  );
  await page
    .getByRole("alertdialog")
    .getByRole("button", { name: "Ở lại", exact: true })
    .click();
  expect(
    await call(
      teacher,
      "GET",
      `/api/teaching/classes/${createdClass.id}/lessons`,
    ),
  ).toHaveLength(1);
  const room = await call(
    teacher,
    "GET",
    `/api/teaching/classes/${createdClass.id}/room`,
  );
  await call(student.context, "POST", "/api/student/classes/join", {
    requestId: randomUUID(),
    code: room.joinCode,
  });
  await call(
    teacher,
    "POST",
    `/api/teaching/classes/${createdClass.id}/members/${student.id}/approve`,
    { requestId: randomUUID(), revision: 0 },
  );
  let live = await call(teacher, "GET", `/api/teaching/lessons/${clone.id}`);
  await call(
    teacher,
    "PUT",
    `/api/teaching/lessons/${clone.id}/games/1/configuration`,
    {
      revision: live.revision,
      budget: 20,
      wheelCells: ["Thử thách chung"],
      selectedCount: 1,
    },
  );
  live = await call(teacher, "GET", `/api/teaching/lessons/${clone.id}`);
  await call(teacher, "PUT", `/api/teaching/lessons/${clone.id}/visibility`, {
    revision: live.revision,
    visible: true,
  });
  live = await call(teacher, "GET", `/api/teaching/lessons/${clone.id}`);
  await call(teacher, "POST", `/api/teaching/lessons/${clone.id}/start`, {
    revision: live.revision,
  });
  await call(
    student.context,
    "POST",
    `/api/student/lessons/${clone.id}/attend`,
  );
  live = await call(teacher, "GET", `/api/teaching/lessons/${clone.id}`);
  await call(
    teacher,
    "POST",
    `/api/teaching/lessons/${clone.id}/games/1/start`,
    { revision: live.revision },
  );
  await call(
    student.context,
    "POST",
    `/api/student/lessons/${clone.id}/games/1/participate`,
  );
  await call(
    teacher,
    "POST",
    `/api/teaching/lessons/${clone.id}/wheel/select`,
    { requestId: randomUUID(), revision: 0, pool: [student.id] },
  );
  await call(teacher, "POST", `/api/teaching/lessons/${clone.id}/wheel/spin`, {
    requestId: randomUUID(),
    revision: 1,
  });
  await call(teacher, "POST", `/api/teaching/lessons/${clone.id}/wheel/grade`, {
    requestId: randomUUID(),
    revision: 2,
    grades: { [student.id]: 10 },
  });
  await call(
    teacher,
    "POST",
    `/api/teaching/lessons/${clone.id}/wheel/finalize`,
    { requestId: randomUUID(), revision: 3 },
  );
  live = await call(teacher, "GET", `/api/teaching/lessons/${clone.id}`);
  await call(
    teacher,
    "POST",
    `/api/teaching/lessons/${clone.id}/games/2/start`,
    { revision: live.revision },
  );
  await call(
    student.context,
    "POST",
    `/api/student/lessons/${clone.id}/games/2/participate`,
  );
  await student.page.goto(`${base}/app/sinh_vien/lessons/${clone.id}/games/2`);
  await expect(
    student.page.getByRole("radio", { name: "Đáp án A", exact: true }),
  ).toBeEnabled();
  await page.goto(listUrl);
  await page.getByText("Quản lý buổi", { exact: true }).click();
  await page.getByRole("button", { name: "Xóa buổi", exact: true }).click();
  await expect(page.getByRole("alertdialog")).toContainText(
    "Buổi đang diễn ra",
  );
  await page
    .getByRole("alertdialog")
    .getByRole("button", { name: "Xác nhận", exact: true })
    .click();
  await expect(
    page.getByRole("heading", { name: "Chưa có buổi học", exact: true }),
  ).toBeVisible();
  await expect(student.page).toHaveURL(
    `${base}/app/sinh_vien/classes/${createdClass.id}`,
  );
  expect(
    await call(teacher, "GET", `/api/teaching/classes/${course.id}/lessons`),
  ).toHaveLength(2);
  console.log(
    `${tag}: lesson copy/edit/delete, running timer cancellation and student realtime redirect PASS`,
  );
}
