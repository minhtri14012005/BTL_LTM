import { verifyLessonManagement } from "./lesson-management.mjs";
import { execFile } from "node:child_process";
import { promisify } from "node:util";
import { fileURLToPath } from "node:url";
import { readFile } from "node:fs/promises";
import { expect as baseExpect } from "@playwright/test";
const expect = baseExpect.configure({ timeout: 20000 });
import { randomUUID } from "node:crypto";

export async function verifyTeaching(browser, base, variant) {
  const tag = variant === "packaged" ? "T15PKG" : "T15DEV";
  const contexts = [];
  const context = async () => {
    const c = await browser.newContext({
      viewport: { width: 1440, height: 1000 },
    });
    contexts.push(c);
    return c;
  };
  async function call(c, method, path, body) {
    const headers = {};
    if (method !== "GET") {
      const r = await c.request.get(`${base}/api/auth/csrf`);
      expect(r.ok()).toBe(true);
      const csrf = await r.json();
      headers[csrf.headerName] = csrf.token;
    }
    const r = await c.request.fetch(`${base}${path}`, {
      method,
      headers,
      ...(body === undefined ? {} : { data: body }),
    });
    expect(r.ok(), `${method} ${path}: ${await r.text()}`).toBe(true);
    const text = await r.text();
    return text ? JSON.parse(text) : null;
  }
  const login = (c, user, password = "BrowserPass123!") =>
    call(c, "POST", "/api/auth/login", { username: user, password });
  async function command(page, type, id, payload = {}) {
    return page.evaluate(
      ({ type, id, payload, requestId }) =>
        new Promise((resolve, reject) => {
          const socket = window.__playSockets.at(-1);
          const timer = setTimeout(() => {
            socket.removeEventListener("message", listener);
            reject(new Error("ACK timeout"));
          }, 15000);
          function listener({ data }) {
            const event = JSON.parse(data);
            if (event.requestId !== requestId) return;
            clearTimeout(timer);
            socket.removeEventListener("message", listener);
            if (event.kind === "ERROR") reject(new Error(event.code));
            else resolve(event.payload);
          }
          socket.addEventListener("message", listener);
          socket.send(
            JSON.stringify({
              v: 1,
              kind: "COMMAND",
              requestId,
              type,
              target: { kind: "LESSON", id },
              payload,
            }),
          );
        }),
      { type, id, payload, requestId: randomUUID() },
    );
  }
  const errors = [];
  const debugStudents = [];
  let debugPage;
  try {
    const admin = await context();
    await login(admin, "ui_admin");
    const offerings = await call(admin, "GET", "/api/admin/academic/offerings");
    const subjectId = offerings[0].subjectId;
    const teacher = await context();
    await login(teacher, "ui_teacher");
    const me = await call(teacher, "GET", "/api/auth/me");
    const courses = [];
    for (const n of [1, 2]) {
      const course = await call(admin, "POST", "/api/admin/academic/classes", {
        offeringId: offerings[0].id,
        code: `${tag}_${n}`,
        name: `Lớp thực hành ${tag} ${n}`,
        requiredLessons: 2,
      });
      await call(
        admin,
        "PUT",
        `/api/admin/academic/classes/${course.id}/teachers/${me.id}`,
      );
      courses.push(course);
    }
    const course = courses[0];
    const page = await teacher.newPage();
    debugPage = page;
    page.on("pageerror", (e) => errors.push(e.message));
    const classUrl = `${base}/app/giang_vien/classes/${course.id}`;
    const students = [];
    const room = await call(
      teacher,
      "GET",
      `/api/teaching/classes/${course.id}/room`,
    );
    for (let i = 1; i <= 6; i++) {
      const c = await context();
      const username = `${tag.toLowerCase()}_s${i}`;
      if (i > 4) {
        await call(c, "POST", "/api/auth/register", {
          username,
          hoTen: `Sinh viên ${tag} ${i}`,
          maSinhVien: `${tag}_S${i}`,
          password: "BrowserPass123!",
        });
        await login(c, username);
      }
      const student = { context: c, code: `${tag}_S${i}` };
      if (i <= 4) {
        await c.addInitScript(() => {
          window.__playSockets = [];
          window.__studentMessages = [];
          const Native = window.WebSocket;
          window.WebSocket = class extends Native {
            constructor(...args) {
              super(...args);
              if (new URL(String(args[0]), location.href).pathname === "/ws") {
                window.__playSockets.push(this);
                this.addEventListener("message", (e) =>
                  window.__studentMessages.push(JSON.parse(e.data)),
                );
              }
            }
          };
        });
        student.page = await c.newPage();
        debugStudents.push(student.page);
        student.page.on("pageerror", (e) => errors.push(e.message));
        await student.page.goto(`${base}/login`);
        await student.page
          .getByRole("button", { name: "Đăng ký sinh viên", exact: true })
          .click();
        await student.page
          .getByLabel("Họ và tên", { exact: true })
          .fill(`Sinh viên ${tag} ${i}`);
        await student.page
          .getByLabel("Mã sinh viên", { exact: true })
          .fill(student.code);
        await student.page
          .getByLabel("Tên đăng nhập", { exact: true })
          .fill(username);
        await student.page
          .getByLabel("Mật khẩu", { exact: true })
          .fill("BrowserPass123!");
        await student.page
          .getByRole("button", { name: "Tạo tài khoản sinh viên", exact: true })
          .click();
        await student.page
          .getByRole("button", { name: "Tham gia bằng mã lớp", exact: true })
          .click();
        await student.page
          .getByRole("textbox", { name: "Mã tham gia lớp", exact: true })
          .fill(room.joinCode);
        await student.page
          .getByRole("button", { name: "Gửi yêu cầu tham gia", exact: true })
          .click();
        await expect(
          student.page.getByText("Đã gửi yêu cầu tham gia lớp.", {
            exact: true,
          }),
        ).toBeVisible();
        await expect(
          student.page
            .locator("span.status-badge")
            .filter({ hasText: "Chờ duyệt" }),
        ).toBeVisible();
      } else
        await call(c, "POST", "/api/student/classes/join", {
          requestId: randomUUID(),
          code: room.joinCode,
        });
      students.push(student);
    }
    await page.goto(classUrl);
    await page.getByRole("link", { name: "Thành viên", exact: true }).click();
    for (let i = 0; i < 4; i++)
      await page
        .getByRole("button", { name: `Duyệt ${students[i].code}`, exact: true })
        .click();
    await page
      .getByRole("button", { name: `Từ chối ${students[4].code}`, exact: true })
      .click();
    await page
      .getByRole("button", { name: `Duyệt ${students[5].code}`, exact: true })
      .click();
    await page
      .getByRole("button", { name: `Loại ${students[5].code}`, exact: true })
      .click();
    await page
      .getByRole("alertdialog")
      .getByRole("button", { name: "Xác nhận", exact: true })
      .click();
    await expect(
      page.getByRole("row").filter({ hasText: students[5].code }),
    ).toContainText("Đã bị loại");
    const otherRoom = await call(
      teacher,
      "GET",
      `/api/teaching/classes/${courses[1].id}/room`,
    );
    const csrf = await (
      await students[5].context.request.get(`${base}/api/auth/csrf`)
    ).json();
    expect(
      (
        await students[5].context.request.post(
          `${base}/api/student/classes/join`,
          {
            headers: { [csrf.headerName]: csrf.token },
            data: { requestId: randomUUID(), code: otherRoom.joinCode },
          },
        )
      ).status(),
    ).toBe(403);
    for (const s of students.slice(0, 4)) {
      await expect(
        s.page
          .locator("span.status-badge")
          .filter({ hasText: "Đã được duyệt" }),
      ).toBeVisible();
      await expect(
        s.page.locator('[title="Đã kết nối trực tiếp"]'),
      ).toBeAttached();
    }
    await page.getByRole("link", { name: "Ô vòng quay", exact: true }).click();
    for (const text of ["Không phải làm gì", "Lên trả lời câu hỏi bài cũ"]) {
      await page
        .getByRole("button", { name: "Thêm ô thử thách", exact: true })
        .click();
      const fields = page.getByRole("textbox", { name: /Ô thử thách \d/ });
      await fields.last().fill(text);
    }
    await page
      .getByRole("button", { name: "Lưu ô thử thách", exact: true })
      .click();
    await expect(
      page.getByRole("textbox", { name: "Ô thử thách 2", exact: true }),
    ).toHaveValue("Lên trả lời câu hỏi bài cũ");
    // Another author supplies the Public source. The teacher must use its snapshot, never edit that source.
    const sourceName = `source_${tag.toLowerCase()}`;
    await call(admin, "POST", "/api/admin/teachers", {
      username: sourceName,
      hoTen: `Tác giả ${tag}`,
      password: "BrowserPass123!",
    });
    const source = await context();
    await login(source, sourceName);
    const quiz = await call(source, "POST", "/api/quizzes", {
      subjectId,
      title: `Quiz Public ${tag}`,
      visibility: "PUBLIC",
      mode: "QUIZ",
      questions: Array.from({ length: 20 }, (_, i) => i + 1).map((n) => ({
        content: `Câu nguồn ${tag} ${n}`,
        options: { A: "Đúng", B: "Sai B", C: "Sai C", D: "Sai D" },
        correctAnswer: "A",
      })),
    });
    const seeds = JSON.parse(
      await readFile(
        new URL(
          "../../data/learning-banks/question-banks.json",
          import.meta.url,
        ),
        "utf8",
      ),
    );
    expect(seeds.packs).toHaveLength(15);
    for (const mode of ["QUIZ", "FILL_BLANK", "ASSIGNMENT"])
      expect(seeds.packs.filter((p) => p.mode === mode)).toHaveLength(5);
    for (const pack of seeds.packs) {
      const list = await call(
        teacher,
        "GET",
        `/api/quizzes?scope=MINE&mode=${pack.mode}&q=${encodeURIComponent(pack.title)}&size=100`,
      );
      const saved =
        list.items.find((p) => p.title === pack.title) ||
        (await call(teacher, "POST", "/api/quizzes", {...pack, subjectId}));
      const preview = await call(
        source,
        "GET",
        `/api/quizzes/${saved.id}/preview`,
      );
      expect(preview.mode).toBe(pack.mode);
      expect(preview.visibility).toBe("PUBLIC");
      expect(preview.questions).toHaveLength(
        pack.mode === "ASSIGNMENT" ? 1 : 10,
      );
      for (let index = 0; index < pack.questions.length; index++) {
        for (const [key, value] of Object.entries(pack.questions[index]))
          expect(preview.questions[index][key]).toEqual(value);
      }
    }
    console.log(
      `${variant}: 15 Public learning banks imported and validated through the real API PASS`,
    );
    if (process.platform === "win32") {
      const literal = (value) => "'" + value.replaceAll("'", "''") + "'";
      const scriptPath = fileURLToPath(
        new URL("../../scripts/Import-LearningBanks.ps1", import.meta.url),
      );
      const code = `[Console]::OutputEncoding=[Text.UTF8Encoding]::new(); $fixtureCredential=[pscredential]::new(${literal(sourceName)},(ConvertTo-SecureString 'BrowserPass123!' -AsPlainText -Force)); & ${literal(scriptPath)} -BaseUrl ${literal(base)} -Credential $fixtureCredential -SubjectId ${subjectId};`;
      const execute = promisify(execFile);
      const first = await execute(
        "powershell.exe",
        [
          "-NoProfile",
          "-NonInteractive",
          "-ExecutionPolicy",
          "Bypass",
          "-Command",
          code,
        ],
        { timeout: 60000, encoding: "utf8" },
      );
      expect(first.stdout).toContain(
        "Created: 15; skipped existing owned banks: 0.",
      );
      const second = await execute(
        "powershell.exe",
        [
          "-NoProfile",
          "-NonInteractive",
          "-ExecutionPolicy",
          "Bypass",
          "-Command",
          code,
        ],
        { timeout: 60000, encoding: "utf8" },
      );
      expect(second.stdout).toContain(
        "Created: 0; skipped existing owned banks: 15.",
      );
      console.log(
        `${variant}: Windows importer UTF-8 and duplicate-free second import PASS`,
      );
    }
    const extraSubject = await call(admin, "POST", "/api/admin/academic/subjects", {code:`S_${tag}`,name:`Môn riêng ${tag}`});
    const foreignTitle = `Khác môn ${tag}`;
    await call(teacher, "POST", "/api/quizzes", {subjectId:extraSubject.id,title:foreignTitle,mode:"QUIZ",visibility:"PUBLIC",questions:[{content:"Foreign question",options:{A:"A",B:"B",C:"C",D:"D"},correctAnswer:"A"}]});
    await page.goto(`${base}/app/giang_vien/banks?mode=QUIZ&scope=VISIBLE&q=${tag}`);
    await expect(page.getByRole("button", {name:`Preview ${foreignTitle}`,exact:true})).toBeVisible();
    await expect(page.getByRole("button", {name:`Preview ${quiz.title}`,exact:true})).toBeVisible();
    await page.getByLabel("Lọc môn học").selectOption(String(extraSubject.id));
    await expect(page.getByRole("button", {name:`Preview ${quiz.title}`,exact:true})).toHaveCount(0);
    await page.getByLabel("Lọc môn học").selectOption(String(subjectId));
    await expect(page.getByRole("button", {name:`Preview ${foreignTitle}`,exact:true})).toHaveCount(0);
    await expect(page.getByRole("button", {name:`Preview ${quiz.title}`,exact:true})).toBeVisible();
    await page.getByLabel("Lọc môn học").selectOption("");
    await expect(page.getByRole("button", {name:`Preview ${foreignTitle}`,exact:true})).toBeVisible();
    await page.goto(`${base}/app/giang_vien/banks?mode=FILL_BLANK&scope=MINE&subjectId=${subjectId}`);
    await expect(page.getByRole("heading", {name: seeds.packs.filter(p=>p.mode==="FILL_BLANK").at(-1).title,exact:true})).toBeVisible();
    for (const [layout,width,height] of [["desktop",1440,1000],["mobile",390,844]]) {
      await page.setViewportSize({width,height});
      expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1)).toBe(true);
      await page.screenshot({path:`.cache/subject-banks-${variant}-${layout}.png`,fullPage:true});
    }
    await page.setViewportSize({width:1440,height:1000});
    console.log(`${variant}: subject catalog, combined bank filters, compact cards and responsive layout PASS`);
    async function createBank(mode, title) {
      await page.goto(`${base}/app/giang_vien/banks`);
      await page
        .getByRole("combobox", { name: "Loại nội dung" })
        .selectOption(mode);
      await page
        .getByRole("button", { name: "Tạo bộ nội dung", exact: true })
        .click();
      await page.getByLabel("Môn học", {exact: true}).selectOption(String(subjectId));
      await page.getByLabel("Tiêu đề nội dung", { exact: true }).fill(title);
      if(mode === "FILL_BLANK") await page.screenshot({path:`.cache/subject-form-${variant}.png`,fullPage:true});
      if (mode === "FILL_BLANK") {
        await page
          .getByLabel("Nội dung câu 1", { exact: true })
          .fill("Thủ đô Việt Nam là {{blank}}.");
        await page
          .getByLabel("Đáp án hợp lệ câu 1", { exact: true })
          .fill("Hà Nội");
      } else
        await page
          .getByLabel("Nội dung đề bài", { exact: true })
          .fill(`Giải thích kiến thức ${tag}`);
      await page
        .getByRole("button", { name: "Lưu nội dung", exact: true })
        .click();
      await expect(
        page.getByRole("heading", { name: title, exact: true }),
      ).toBeVisible();
    }
    const fillTitle = `Điền từ ${tag}`,
      assignmentTitle = `Bài tập ${tag}`;
    await createBank("FILL_BLANK", fillTitle);
    await createBank("ASSIGNMENT", assignmentTitle);
    await page
      .getByRole("combobox", { name: "Loại nội dung" })
      .selectOption("QUIZ");
    await page
      .getByRole("combobox", { name: "Phạm vi tìm kiếm" })
      .selectOption("SHARED");
    await page
      .getByRole("textbox", { name: "Tìm bộ nội dung" })
      .fill(quiz.title);
    await page.getByRole("button", { name: "Tìm kiếm", exact: true }).click();
    await page
      .getByRole("button", { name: `Preview ${quiz.title}`, exact: true })
      .click();
    await expect(
      page.getByRole("link", { name: "Sửa bản gốc của tôi", exact: true }),
    ).toHaveCount(0);
    await page
      .getByRole("button", { name: "Hiện đáp án", exact: true })
      .click();
    await expect(page.getByText("Đáp án: A", { exact: true })).toBeVisible();
    await page.getByRole("button", { name: "Câu sau", exact: true }).click();
    await expect(
      page.getByText(`Câu nguồn ${tag} 2`, { exact: true }),
    ).toBeVisible();
    // Scope activity cards by their unique Start button/header rather than their nested source cards.
    const gameCard = (position) =>
      page
        .locator("main")
        .locator("div.rounded-2xl.border")
        .filter({
          has: page
            .locator("p.eyebrow")
            .filter({ hasText: `TRÒ ${position} ·` }),
        })
        .first();
    for (let lessonNumber = 1; lessonNumber <= 2; lessonNumber++) {
      await page.goto(`${classUrl}/lessons`);
      await page
        .getByRole("button", { name: "Tạo buổi mới", exact: true })
        .click();
      await page
        .getByLabel("Tên buổi học", { exact: true })
        .fill(`Buổi ${tag} ${lessonNumber}`);
      if (lessonNumber === 2)
        await page
          .getByLabel("Mode trò 3", { exact: true })
          .selectOption("ASSIGNMENT");
      for (const p of [1, 2, 3]) {
        await page
          .getByLabel("Tên hiển thị (tùy chọn)", { exact: true })
          .nth(p - 1)
          .fill(`Hoạt động ${p}`);
        await page
          .getByLabel(`Ngân sách trò ${p}`, { exact: true })
          .fill(String(p === 1 ? 20 : 40));
      }
      await expect(page.getByRole("option", {name: `${foreignTitle} · PUBLIC`,exact:true})).toHaveCount(0);
      if(lessonNumber === 1) await page.screenshot({path:`.cache/subject-lesson-setup-${variant}.png`,fullPage:true});
      await page
        .getByRole("button", { name: "Tạo buổi học", exact: true })
        .click();
      const lessonCard = page.locator(".course-grid > div").filter({
        has: page.getByRole("heading", {
          name: `Buổi ${tag} ${lessonNumber}`,
          exact: true,
        }),
      });
      await lessonCard
        .getByRole("link", { name: "Soạn & điều khiển buổi", exact: true })
        .click();
      const lessonId = Number(new URL(page.url()).pathname.split("/").at(-1));
      for (const s of students.slice(0, 4)) {
        expect(
          (
            await s.context.request.get(
              `${base}/api/student/lessons/${lessonId}/snapshot`,
            )
          ).status(),
        ).toBe(404);
        await s.page.goto(`${base}/app/sinh_vien/classes/${course.id}`);
        await expect(
          s.page.getByText(`Buổi ${tag} ${lessonNumber}`, { exact: true }),
        ).toHaveCount(0);
      }
      for (const [position, title] of [
        [2, quiz.title],
        [3, lessonNumber === 1 ? fillTitle : assignmentTitle],
      ]) {
        await page.goto(
          `${base}/app/giang_vien/lessons/${lessonId}/games/${position}?panel=content`,
        );
        await expect(
          page.locator("main p.eyebrow").filter({ hasText: /TRÒ \d ·/ }),
        ).toHaveCount(1);
        const card = gameCard(position);
        await card
          .getByRole("button", { name: "Chọn bộ từ ngân hàng", exact: true })
          .click();
        await card
          .getByRole("textbox", { name: "Tìm nguồn cho trò" })
          .fill(title);
        await card
          .getByRole("button", { name: "Tìm nguồn", exact: true })
          .click();
        await card
          .getByRole("button", { name: `Preview nguồn ${title}`, exact: true })
          .click();
        await expect(
          card.getByText(`Preview nguồn (chỉ xem): ${title}`, { exact: true }),
        ).toBeVisible();
        await card
          .getByRole("button", { name: `Dùng nguồn ${title}`, exact: true })
          .click();
        await expect(card.getByText(/BẢN SAO TRONG TRÒ/)).toBeVisible();
      }
      if (lessonNumber === 1) {
        await page.goto(
          `${base}/app/giang_vien/lessons/${lessonId}/games/2?panel=content`,
        );
        const card = gameCard(2);
        await card
          .getByRole("button", { name: "Sửa bản sao trong trò", exact: true })
          .click();
        await card
          .getByLabel("Nội dung câu 1", { exact: true })
          .fill(`Câu sao chép riêng ${tag}`);
        await card
          .getByRole("button", { name: "Lưu bản sao trong trò", exact: true })
          .click();
        await expect(
          card.getByText(`Câu sao chép riêng ${tag}`, { exact: true }),
        ).toBeVisible();
        expect(
          (await call(source, "GET", `/api/quizzes/${quiz.id}/preview`))
            .questions[0].content,
        ).toBe(`Câu nguồn ${tag} 1`);
        expect(
          (
            await call(
              teacher,
              "GET",
              `/api/quizzes?scope=MINE&mode=QUIZ&q=${encodeURIComponent(quiz.title)}`,
            )
          ).totalElements,
        ).toBe(0);
      }
      await page.goto(
        `${base}/app/giang_vien/lessons/${lessonId}/games/1?panel=configuration`,
      );
      await page
        .getByLabel("Số sinh viên được chọn", { exact: true })
        .fill("2");
      await page
        .getByRole("button", { name: "Lưu cấu hình chung", exact: true })
        .click();
      await expect(
        page.getByText("Đã lưu cấu hình chung.", { exact: true }),
      ).toBeVisible();
      await page.goto(`${base}/app/giang_vien/lessons/${lessonId}`);
      await expect(
        page.getByRole("button", { name: "Lưu cấu hình chung", exact: true }),
      ).toHaveCount(0);
      await page
        .getByRole("button", { name: "Công bố buổi học", exact: true })
        .click();
      await page
        .getByRole("button", { name: "Bắt đầu buổi học", exact: true })
        .click();
      await page
        .getByRole("link", { name: "Quản lý trò 1", exact: true })
        .click();
      await expect(
        page.getByRole("button", { name: "Bắt đầu trò 1", exact: true }),
      ).toBeEnabled();
      await Promise.all(
        students.slice(0, 4).map(async (s) => {
          await s.page
            .getByRole("link", { name: "Vào buổi học", exact: true })
            .last()
            .click();
          await s.page
            .getByRole("button", { name: "Tham dự buổi học", exact: true })
            .click();
          await expect(s.page.getByText(/Bạn đã tham dự buổi/)).toBeVisible();
          await s.page
            .getByRole("link", { name: "Vào trò 1", exact: true })
            .click();
        }),
      );
      await page
        .getByRole("button", { name: "Bắt đầu trò 1", exact: true })
        .click();
      await expect(
        page.getByRole("button", {
          name: "Chọn tất cả đủ điều kiện",
          exact: true,
        }),
      ).toBeVisible();
      await Promise.all(
        students.slice(0, 4).map(async (s) => {
          await s.page
            .getByRole("button", { name: "Tham gia trò 1", exact: true })
            .click();
          await expect(
            s.page.getByRole("button", { name: "Tham gia trò 1", exact: true }),
          ).toHaveCount(0);
        }),
      );
      await page
        .getByRole("button", { name: "Làm mới trạng thái", exact: true })
        .click();
      await page
        .getByRole("button", { name: "Chọn tất cả đủ điều kiện", exact: true })
        .click();

      await page
        .getByRole("button", { name: "Server chọn k sinh viên", exact: true })
        .click();
      await page
        .getByRole("button", { name: "Quay một lượt chung", exact: true })
        .click();
      await expect(page.getByText(/Thử thách chung:/)).toBeVisible();
      const selected = (
        await call(teacher, "GET", `/api/teaching/lessons/${lessonId}/snapshot`)
      ).wheel;
      expect(new Set(selected.selected).size).toBe(2);
      for (const s of students.slice(0, 4)) {
        const snapshot = await command(s.page, "RECONNECT_LESSON", lessonId);
        expect(snapshot.wheel.challengeIndex).toBe(selected.challengeIndex);
        expect(snapshot.wheel.grades).toBeUndefined();
        expect(snapshot.participants).toEqual([]);
        await expect(
          s.page.locator(`[data-challenge-index="${selected.challengeIndex}"]`),
        ).toBeVisible();
        if (snapshot.wheel.selectedForChallenge)
          await expect(
            s.page.getByText(/Giảng viên đã chọn bạn để thực hiện vòng quay/),
          ).toBeVisible();
        else
          await expect(
            s.page.getByText(
              /Bạn không bị chọn và được Server ghi nhận mức tối đa/,
            ),
          ).toBeVisible();
      }
      await page
        .getByRole("spinbutton", { name: "Điểm đồng loạt", exact: true })
        .fill("8");
      await page
        .getByRole("button", {
          name: "Áp dụng cho người được chọn",
          exact: true,
        })
        .click();
      await page
        .getByRole("button", { name: "Lưu điểm người được chọn", exact: true })
        .click();
      await page
        .getByRole("button", {
          name: "Xem trước & chốt vòng quay",
          exact: true,
        })
        .click();
      await page
        .getByRole("alertdialog")
        .getByRole("button", { name: "Xác nhận", exact: true })
        .click();
      await page.goto(`${base}/app/giang_vien/lessons/${lessonId}/games/2`);
      await expect(
        page.getByRole("button", { name: "Bắt đầu trò 2", exact: true }),
      ).toBeEnabled();
      const wheelScores = await call(
        teacher,
        "GET",
        `/api/teaching/lessons/${lessonId}/snapshot`,
      );
      for (const p of wheelScores.participants.filter((p) =>
        p.games.includes(1),
      ))
        expect(Number(p.scores["1"])).toBe(
          selected.selected.includes(p.userId) ? 8 : 20,
        );
      for (const position of [2, 3]) {
        await page.goto(
          `${base}/app/giang_vien/lessons/${lessonId}/games/${position}`,
        );
        if (position === 2 || lessonNumber === 1) {
          await page
            .getByRole("tab", { name: "Cấu hình chung", exact: true })
            .click();
          await page
            .getByLabel("Thời gian mỗi câu (giây)", { exact: true })
            .fill(lessonNumber === 1 && position === 2 ? "20" : "10");
          await page
            .getByLabel("Số câu hỏi sử dụng", { exact: true })
            .fill(position === 2 ? "2" : "1");
          await page
            .getByLabel("Ngân sách điểm của trò", { exact: true })
            .fill("40");
          await page
            .getByRole("button", { name: "Lưu cấu hình chung", exact: true })
            .click();
          await page
            .getByRole("tab", { name: "Điều khiển", exact: true })
            .click();
          await expect(
            page.getByRole("button", {
              name: `Bắt đầu trò ${position}`,
              exact: true,
            }),
          ).toBeEnabled();
        }
        await Promise.all(
          students
            .slice(0, 4)
            .map((s) =>
              s.page.goto(
                `${base}/app/sinh_vien/lessons/${lessonId}/games/${position}`,
              ),
            ),
        );
        await page
          .getByRole("button", { name: `Bắt đầu trò ${position}`, exact: true })
          .click();
        await expect
          .poll(
            async () =>
              (
                await call(teacher, "GET", `/api/teaching/lessons/${lessonId}`)
              ).games.find((g) => g.position === position).status,
          )
          .toBe("RUNNING");
        await Promise.all(
          students.slice(0, 4).map(async (s) => {
            await s.page
              .getByRole("button", {
                name: `Tham gia trò ${position}`,
                exact: true,
              })
              .click();
            await expect(
              s.page.getByRole("button", {
                name: `Tham gia trò ${position}`,
                exact: true,
              }),
            ).toHaveCount(0);
          }),
        );
        const card = gameCard(position);
        if (position === 2 || lessonNumber === 1) {
          const count = position === 2 ? 2 : 1;
          for (let q = 1; q <= count; q++) {
            await expect(
              card.getByRole("heading", {
                name: new RegExp(`Tiến trình .*Câu ${q}/${count}`),
              }),
            ).toBeVisible({ timeout: 30000 });
            await expect(
              card.getByRole("button", { name: /Mở câu|Đóng câu hiện tại/ }),
            ).toHaveCount(0);
            if (lessonNumber === 1 && position === 2 && q === 1) {
              const first = students[0];
              await page
                .getByRole("button", { name: "Ẩn trò chơi", exact: true })
                .click();
              await expect(
                first.page.getByText("Trò không tồn tại hoặc đang được ẩn.", {
                  exact: true,
                }),
              ).toBeVisible();
              expect(
                (
                  await first.context.request.get(
                    `${base}/api/student/lessons/${lessonId}/games/2/snapshot`,
                  )
                ).status(),
              ).toBe(404);
              await expect(
                first.page.getByRole("radio", {
                  name: "Đáp án A",
                  exact: true,
                }),
              ).toHaveCount(0);
              await first.page.goto(
                `${base}/app/sinh_vien/lessons/${lessonId}/games/2`,
              );
              await expect(
                first.page
                  .getByText("Trò không tồn tại hoặc đang được ẩn.", {
                    exact: true,
                  })
                  .first(),
              ).toBeVisible();
              await page
                .getByRole("button", { name: "Hiện trò chơi", exact: true })
                .click();
              await expect(
                first.page.getByRole("radio", {
                  name: "Đáp án A",
                  exact: true,
                }),
              ).toBeEnabled();
              await first.page
                .getByRole("radio", { name: "Đáp án A", exact: true })
                .check();
              await first.page
                .getByRole("link", {
                  name: "Về tổng quan buổi học",
                  exact: true,
                })
                .click();
              await expect(first.page.getByRole("radio")).toHaveCount(0);
              await first.page
                .getByRole("link", { name: "Vào trò 2", exact: true })
                .click();
              await expect(
                first.page.getByRole("radio", {
                  name: "Đáp án A",
                  exact: true,
                }),
              ).toBeChecked();
              await page
                .getByRole("link", {
                  name: "Về tổng quan buổi học",
                  exact: true,
                })
                .click();
              await expect(
                page.getByRole("button", { name: "Mở câu 1", exact: true }),
              ).toHaveCount(0);
              await page
                .getByRole("button", { name: "Ẩn buổi học", exact: true })
                .click();
              await expect(
                first.page.getByText("Quyền xem buổi học đã được thu hồi.", {
                  exact: true,
                }),
              ).toBeVisible();
              expect(
                (
                  await first.context.request.get(
                    `${base}/api/student/lessons/${lessonId}/games/2/snapshot`,
                  )
                ).status(),
              ).toBe(404);
              await page
                .getByRole("button", { name: "Công bố buổi học", exact: true })
                .click();
              await expect(
                first.page.getByRole("radio", {
                  name: "Đáp án A",
                  exact: true,
                }),
              ).toBeEnabled();
              await page
                .getByRole("link", { name: "Quản lý trò 2", exact: true })
                .click();
              await expect(page.getByRole("timer")).toBeVisible();
              await students[0].page.evaluate(() =>
                window.__playSockets
                  .at(-1)
                  .close(4000, "Test student reconnect"),
              );
              await expect(
                students[0].page.getByText(
                  /Đang chờ snapshot và kết nối mới nhất/,
                ),
              ).toBeVisible();
              await expect(
                students[0].page.getByRole("radio", {
                  name: "Đáp án A",
                  exact: true,
                }),
              ).toBeEnabled();
            }
            await Promise.all(
              students.slice(0, 4).map(async (s) => {
                await expect(
                  s.page.getByRole("heading", {
                    name: `C\u00e2u ${q}/${count}`,
                    exact: true,
                  }),
                ).toBeVisible();
                if (position === 2)
                  await s.page
                    .getByRole("radio", { name: "Đáp án A", exact: true })
                    .check();
                else
                  await s.page
                    .getByRole("textbox", {
                      name: "Đáp án điền từ",
                      exact: true,
                    })
                    .fill("Hà Nội");
                await s.page
                  .getByRole("button", { name: "Gửi đáp án", exact: true })
                  .click();
                await expect(
                  s.page.getByRole("button", {
                    name: "Đã gửi đáp án",
                    exact: true,
                  }),
                ).toBeDisabled();
              }),
            );
            await expect
              .poll(
                async () => {
                  const live = await call(
                    teacher,
                    "GET",
                    `/api/teaching/lessons/${lessonId}/snapshot`,
                  );
                  return (
                    live.window?.questionIndex > q ||
                    live.lesson.games.find((g) => g.position === position)
                      .status === "COMPLETED"
                  );
                },
                { timeout: 30000 },
              )
              .toBe(true);
          }
          await expect(
            card.getByText("Đã hoàn tất", { exact: false }).first(),
          ).toBeVisible();
        } else {
          const future = await page.evaluate(() => {
            const d = new Date(Date.now() + 180000);
            return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}T${String(d.getHours()).padStart(2, "0")}:${String(d.getMinutes()).padStart(2, "0")}`;
          });
          await page
            .getByRole("tab", { name: "Cấu hình chung", exact: true })
            .click();
          await card.getByLabel("Hạn nộp trò 3", { exact: true }).fill(future);
          await card
            .getByRole("button", { name: "Đặt / sửa hạn nộp", exact: true })
            .click();
          await page
            .getByRole("tab", { name: "Điều khiển", exact: true })
            .click();
          await expect(
            card.getByRole("button", { name: "Đóng nhận bài", exact: true }),
          ).toBeEnabled();
          await page
            .getByRole("button", { name: "Ẩn trò chơi", exact: true })
            .click();
          await expect(
            students[0].page.getByRole("textbox", {
              name: "Bài làm của bạn",
              exact: true,
            }),
          ).toHaveCount(0);
          expect(
            (
              await students[0].context.request.get(
                `${base}/api/student/lessons/${lessonId}/games/3/snapshot`,
              )
            ).status(),
          ).toBe(404);
          await page
            .getByRole("button", { name: "Hiện trò chơi", exact: true })
            .click();
          await expect(
            students[0].page.getByRole("textbox", {
              name: "Bài làm của bạn",
              exact: true,
            }),
          ).toBeEnabled();
          for (const s of students.slice(0, 4))
            for (let version = 1; version <= 3; version++) {
              await s.page
                .getByRole("textbox", { name: "Bài làm của bạn", exact: true })
                .fill(`Bản ${version} của ${s.code}`);
              await s.page
                .getByRole("button", {
                  name: version === 1 ? "Nộp bài tập" : "Nộp lại bài tập",
                  exact: true,
                })
                .click();
              await expect(
                s.page.getByText(`Bản ${version} của ${s.code}`, {
                  exact: true,
                }),
              ).toBeVisible();
            }
          await page
            .getByRole("button", { name: "Làm mới trạng thái", exact: true })
            .click();
          for (const s of students.slice(0, 4)) {
            await card
              .getByRole("button", { name: `Xem bài ${s.code}`, exact: true })
              .click();
            await expect(
              page
                .getByRole("dialog")
                .getByText(`Bản 3 của ${s.code}`, { exact: true }),
            ).toBeVisible();
            await page
              .getByRole("dialog")
              .getByRole("button", { name: "Đóng", exact: true })
              .click();
          }
          await card
            .getByRole("button", { name: "Đóng nhận bài", exact: true })
            .click();
          await page
            .getByRole("tab", { name: "Cấu hình chung", exact: true })
            .click();
          await card.getByLabel("Hạn nộp trò 3", { exact: true }).fill(future);
          await card
            .getByRole("button", { name: "Mở lại / sửa hạn nộp", exact: true })
            .click();
          await page
            .getByRole("tab", { name: "Điều khiển", exact: true })
            .click();
          await expect(
            card.getByRole("button", { name: "Đóng nhận bài", exact: true }),
          ).toBeEnabled();
          await students[0].page
            .getByRole("textbox", { name: "Bài làm của bạn", exact: true })
            .fill(`Bản mở lại ${tag}`);
          await students[0].page
            .getByRole("button", { name: "Nộp lại bài tập", exact: true })
            .click();
          await expect(
            students[0].page.getByText(`Bản mở lại ${tag}`, { exact: true }),
          ).toBeVisible();
          await page
            .getByRole("button", { name: "Làm mới trạng thái", exact: true })
            .click();
          await card
            .getByRole("button", {
              name: `Xem bài ${students[0].code}`,
              exact: true,
            })
            .click();
          await expect(
            page
              .getByRole("dialog")
              .getByText(`Bản mở lại ${tag}`, { exact: true }),
          ).toBeVisible();
          await page
            .getByRole("dialog")
            .getByRole("button", { name: "Đóng", exact: true })
            .click();
          await card
            .getByRole("button", { name: "Đóng nhận bài", exact: true })
            .click();
          for (const s of students.slice(0, 4)) {
            await card
              .getByRole("button", { name: `Xem bài ${s.code}`, exact: true })
              .click();
            await page
              .getByRole("dialog")
              .getByRole("spinbutton", { name: `Chấm ${s.code}`, exact: true })
              .fill("25");
            const submissionCard = page.getByRole("dialog");
            await submissionCard
              .getByRole("button", { name: /Xác nhận chấm bản #/ })
              .click();
            await expect(submissionCard).toContainText(
              "Điểm đã xác nhận cho bản này: 25",
            );
            await submissionCard
              .getByRole("button", { name: "Đóng", exact: true })
              .click();
          }
          await card
            .getByRole("button", { name: "Chốt chấm bài tập", exact: true })
            .click();
          await page
            .getByRole("alertdialog")
            .getByRole("button", { name: "Xác nhận", exact: true })
            .click();
        }
      }
      await expect
        .poll(
          async () =>
            (await call(teacher, "GET", `/api/teaching/lessons/${lessonId}`))
              .status,
        )
        .toBe("COMPLETED");
      for (const s of students.slice(0, 4)) {
        await expect(
          s.page.getByText(/Buổi học đã hoàn tất đủ ba trò/),
        ).toBeVisible();
        const snapshot = await call(
          s.context,
          "GET",
          `/api/student/lessons/${lessonId}/snapshot`,
        );
        expect(snapshot.participants).toEqual([]);
        expect(
          snapshot.questionCounts[String(snapshot.lesson.games[1].id)],
        ).toBe(2);
        const messages = await s.page.evaluate(() => window.__studentMessages);
        expect(JSON.stringify(messages)).not.toMatch(
          /"(correctAnswer|acceptedAnswers|matchingPolicy|sourceQuizId)"/,
        );
        await s.page.setViewportSize({ width: 390, height: 844 });
        expect(
          await s.page.evaluate(
            () => document.documentElement.scrollWidth <= innerWidth,
          ),
        ).toBe(true);
        await s.page.setViewportSize({ width: 1440, height: 1000 });
      }
      expect(
        await page.evaluate(
          () => document.documentElement.scrollWidth <= innerWidth,
        ),
      ).toBe(true);
      await page.setViewportSize({ width: 390, height: 844 });
      expect(
        await page.evaluate(
          () => document.documentElement.scrollWidth <= innerWidth,
        ),
      ).toBe(true);
      await page.setViewportSize({ width: 1440, height: 1000 });
    }
    await page.goto(classUrl);
    await expect(
      page.getByText("Đã tạo 2/2 · Đã hoàn thành 2/2", { exact: true }),
    ).toBeVisible();
    await page
      .getByRole("link", { name: "Cấu hình Rank", exact: true })
      .click();
    await page
      .getByRole("button", { name: "Thêm mức Rank", exact: true })
      .click();
    await page.getByLabel("Tên Rank mức 1", { exact: true }).fill("Tiên phong");
    await page.getByLabel("Từ hạng mức 1", { exact: true }).fill("1");
    await page.getByLabel("Đến hạng mức 1", { exact: true }).fill("4");
    await students[0].page.goto(
      `${base}/app/sinh_vien/boards?classId=${course.id}`,
    );
    await expect(
      students[0].page.getByRole("columnheader", {
        name: "Rank của lớp",
        exact: true,
      }),
    ).toBeVisible();
    await page.getByRole("button", { name: "Lưu Rank", exact: true }).click();
    await expect(
      students[0].page.getByRole("cell", { name: "Tiên phong", exact: true }),
    ).toHaveCount(4);
    await page
      .getByRole("button", { name: "Tạo mã chia sẻ", exact: true })
      .click();
    const text = await page.getByText(/Mã setting:/).innerText();
    const code = text.split(":").at(-1).trim();
    expect(code).toMatch(/^[a-f0-9]{32}$/);
    await page.goto(`${base}/app/giang_vien/classes/${courses[1].id}`);
    await page
      .getByRole("link", { name: "Cấu hình Rank", exact: true })
      .click();
    await page
      .getByRole("textbox", { name: "Mã setting để import" })
      .fill(code);
    await page
      .getByRole("button", { name: "Import Rank", exact: true })
      .click();
    await expect(page.getByLabel("Tên Rank mức 1", { exact: true })).toHaveValue(
      "Tiên phong",
    );
    await page.goto(`${classUrl}/scores`);
    await expect(
      page.getByRole("columnheader", {
        name: "Rank do giảng viên đặt",
        exact: true,
      }),
    ).toBeVisible();
    for (const student of students.slice(0, 4))
      await expect(
        page.getByRole("cell", { name: student.code, exact: true }),
      ).toBeVisible();
    await expect(
      page.getByRole("button", { name: "Thêm mức Rank", exact: true }),
    ).toHaveCount(0);
    const learner = students[0].page;
    expect(
      await learner.evaluate(async () => {
        const csrf = await (await fetch("/api/auth/csrf")).json();
        return (
          await fetch("/api/auth/keepalive", {
            method: "POST",
            headers: { [csrf.headerName]: csrf.token },
          })
        ).status;
      }),
    ).toBe(204);
    await learner
      .getByRole("combobox", { name: "Phạm vi bảng xếp hạng", exact: true })
      .selectOption("global");
    await expect(
      learner.getByRole("columnheader", { name: "Rank của lớp", exact: true }),
    ).toHaveCount(0);
    const globalRows = (
      await call(
        students[0].context,
        "GET",
        `/api/learning/leaderboard?subjectId=${courses[0].subjectId}&academicYearId=${courses[0].academicYearId}`,
      )
    ).students;
    for (const s of students.slice(0, 4))
      await expect(
        learner.getByRole("cell", { name: s.code, exact: true }),
      ).toBeVisible();
    for (const row of globalRows.filter((r) =>
      students.slice(0, 4).some((s) => s.code === r.maSinhVien),
    )) {
      const uiRow = learner.getByRole("row").filter({
        has: learner.getByRole("cell", { name: row.maSinhVien, exact: true }),
      });
      await expect(uiRow.getByRole("cell").first()).toHaveText(
        String(row.position),
      );
      await expect(uiRow.getByRole("cell").nth(3)).toHaveText(
        String(row.score),
      );
    }
    expect(
      globalRows.filter((r) =>
        students.slice(0, 4).some((s) => s.code === r.maSinhVien),
      ),
    ).toHaveLength(4);
    await learner.goto(`${base}/app/sinh_vien/classes/${course.id}`);
    const ownBoard = await call(
      students[0].context,
      "GET",
      `/api/learning/classes/${course.id}/leaderboard`,
    );
    const personal = await call(students[0].context, "GET", "/api/auth/me");
    const score = ownBoard.students.find((s) => s.userId === personal.id).score;
    await expect(
      learner
        .getByText("Điểm đã ghi nhận trong lớp này", { exact: true })
        .locator(".."),
    ).toContainText(String(score));
    await expect(learner.getByText("2/2", { exact: true })).toBeVisible();
    await learner.goto(`${base}/app/sinh_vien/history?classId=${course.id}`);
    await expect(
      learner.getByText("Trang 1 · 2 buổi", { exact: true }),
    ).toBeVisible();
    await learner.goto(`${base}/app/sinh_vien/profile/edit`);
    await learner
      .getByRole("textbox", { name: "Họ và tên trong hồ sơ", exact: true })
      .fill(`Hồ sơ đã sửa ${tag}`);
    await learner
      .getByRole("button", { name: "Lưu hồ sơ", exact: true })
      .click();
    await expect(
      learner.getByText("Đã cập nhật hồ sơ.", { exact: true }),
    ).toBeVisible();
    await learner.goto(`${base}/app/sinh_vien/profile`);
    await expect(
      learner.getByRole("heading", {
        name: `Hồ sơ đã sửa ${tag}`,
        exact: true,
      }),
    ).toBeVisible();
    await learner.reload();
    await expect(
      learner.getByRole("heading", {
        name: `Hồ sơ đã sửa ${tag}`,
        exact: true,
      }),
    ).toBeVisible();
    await learner.setViewportSize({ width: 320, height: 844 });
    expect(
      await learner.evaluate(
        () => document.documentElement.scrollWidth <= innerWidth,
      ),
    ).toBe(true);
    expect(
      (
        await students[0].context.request.get(
          `${base}/api/quizzes/${quiz.id}/preview`,
        )
      ).status(),
    ).toBe(403);
    const lastLesson = (
      await call(teacher, "GET", `/api/teaching/classes/${course.id}/lessons`)
    ).at(-1);
    await students[3].page.goto(
      `${base}/app/sinh_vien/lessons/${lastLesson.id}`,
    );
    await page.goto(classUrl);
    await page.getByRole("link", { name: "Thành viên", exact: true }).click();
    await page
      .getByRole("button", { name: `Loại ${students[3].code}`, exact: true })
      .click();
    await page
      .getByRole("alertdialog")
      .getByRole("button", { name: "Xác nhận", exact: true })
      .click();
    await expect(students[3].page.getByRole("alert")).toContainText("thu hồi");
    expect(
      (
        await students[3].context.request.get(
          `${base}/api/student/lessons/${lastLesson.id}/snapshot`,
        )
      ).status(),
    ).toBe(403);
    await students[3].page.goto(
      `${base}/app/sinh_vien/history?classId=${course.id}`,
    );
    await expect(
      students[3].page.getByText("Trang 1 · 2 buổi", { exact: true }),
    ).toBeVisible();
    const expelled = (
      await call(admin, "GET", `/api/admin/users?q=${tag.toLowerCase()}_s3`)
    ).items[0];
    await call(admin, "POST", `/api/admin/users/${expelled.id}/expel`, {
      requestId: randomUUID(),
      expectedVersion: expelled.banVersion,
      reason: "Student session revocation acceptance",
    });
    await expect(
      students[2].page.getByRole("heading", { name: "Đăng nhập", exact: true }),
    ).toBeVisible();
    expect(
      (
        await source.request.get(`${base}/api/teaching/classes/${course.id}`)
      ).status(),
    ).toBe(403);
    expect(
      (
        await source.request.get(
          `${base}/api/teaching/lessons/${(await call(teacher, "GET", `/api/teaching/classes/${course.id}/lessons`))[0].id}/snapshot`,
        )
      ).status(),
    ).toBe(403);
    await verifyLessonManagement({
      page,
      teacher,
      admin,
      student: students[0],
      base,
      course,
      teacherId: me.id,
      call,
      tag,
    });
    expect(errors).toEqual([]);
    console.log(
      `${variant}: TEACHER UI membership, bank preview/copy isolation, 2 lessons/4 modes, 4 concurrent students, wheel/grades, deadlines/resubmissions, ranks/share/import and mobile PASS`,
    );
  } catch (error) {
    console.log(
      "TEACHER_FAILURE_UI",
      await debugPage
        ?.locator("main")
        .innerText()
        .catch(() => ""),
    );
    for (const studentPage of debugStudents)
      console.log(
        "STUDENT_FAILURE_UI",
        studentPage.url(),
        (
          await studentPage
            .locator("main")
            .innerText()
            .catch(() => "")
        ).slice(-4000),
      );
    throw error;
  } finally {
    await Promise.all(contexts.map((c) => c.close()));
  }
}
