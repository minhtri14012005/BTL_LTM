import {
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
} from "@testing-library/react";
import { afterEach, expect, it, vi } from "vitest";
import { Assignment, Objective, StudentWheel } from "./lesson-page";
import { type Game, type Snapshot } from "@/features/teaching/teaching-api";
vi.mock("@/lib/session", () => ({
  useSession: () => ({ user: { id: 7 } }),
  useConnection: () => "connected",
  realtime: { listen: () => () => {} },
}));
afterEach(() => {
  cleanup();
  sessionStorage.clear();
});
const game = {
  id: 8,
  position: 2,
  mode: "FILL_BLANK",
  title: "Địa lý",
  budget: 40,
  status: "RUNNING",
  participating: true,
  score: 0,
  modeLabel: "Điền từ vào chỗ trống",
} as Game;
const snapshot = {
  lesson: { id: 1 },
  window: {
    gameId: 8,
    token: 3,
    position: 2,
    questionIndex: 1,
    questionCount: 2,
    status: "OPEN",
    submitted: false,
    deadlineAtMs: 9000,
    question: { content: "Thủ đô là {{blank}}." },
  },
  serverTimeMs: 1000,
  quiz: {},
  fillBlank: {},
  assignment: {},
  questionCounts: { 8: 2 },
} as unknown as Snapshot;
it("renders exactly one text blank and sends Unicode verbatim with the server token", async () => {
  const send = vi.fn().mockResolvedValue(true);
  render(
    <Objective snapshot={snapshot} game={game} pending={false} send={send} />,
  );
  expect(screen.getAllByRole("textbox")).toHaveLength(1);
  fireEvent.change(screen.getByLabelText("Đáp án điền từ"), {
    target: { value: "Hà Nội" },
  });
  fireEvent.click(screen.getByRole("button", { name: "Gửi đáp án" }));
  await waitFor(() =>
    expect(send).toHaveBeenCalledWith({ token: 3, value: "Hà Nội" }),
  );
});
it("uses server submitted/closed states to block duplicate or late answers and hides closed content", () => {
  const send = vi.fn();
  const view = render(
    <Objective
      snapshot={{
        ...snapshot,
        window: { ...snapshot.window!, submitted: true },
      }}
      game={game}
      pending={false}
      send={send}
    />,
  );
  expect(
    (screen.getByLabelText("Đáp án điền từ") as HTMLInputElement).disabled,
  ).toBe(true);
  view.rerender(
    <Objective
      snapshot={{
        ...snapshot,
        window: { ...snapshot.window!, status: "CLOSED" },
      }}
      game={game}
      pending={false}
      send={send}
    />,
  );
  expect(screen.queryByRole("textbox")).toBeNull();
  expect(send).not.toHaveBeenCalled();
});
it("shows only a latest submission's grade and sends a replacement using the new deadline token", async () => {
  const assignment = { ...game, mode: "ASSIGNMENT" } as Game;
  const send = vi.fn().mockResolvedValue(true);
  const s = {
    ...snapshot,
    window: {
      ...snapshot.window!,
      token: 9,
      question: { content: "Giải thích bài học." },
    },
    assignment: {
      8: {
        latest: [
          { id: 22, studentId: 7, text: "Bản cuối", receivedAtMs: 1200 },
        ],
        grades: [{ studentId: 7, submissionId: 21, score: 30 }],
        deadlineAtMs: 9000,
      },
    },
  } as Snapshot;
  render(
    <Assignment snapshot={s} game={assignment} pending={false} send={send} />,
  );
  expect(screen.getByText("Bản nộp cuối đang chờ chấm")).toBeTruthy();
  expect(screen.queryByText(/Điểm xác nhận cho bản này/)).toBeNull();
  fireEvent.change(screen.getByLabelText("Bài làm của bạn"), {
    target: { value: "Bản thay thế tiếng Việt" },
  });
  fireEvent.click(screen.getByRole("button", { name: "Nộp lại bài tập" }));
  await waitFor(() =>
    expect(send).toHaveBeenCalledWith({
      token: 9,
      value: "Bản thay thế tiếng Việt",
    }),
  );
});
it("restores an unsent assignment draft after reconnect without automatically resubmitting it", () => {
  const send = vi.fn();
  const s = {
    ...snapshot,
    assignment: { 8: { latest: [], grades: [], deadlineAtMs: 9000 } },
  };
  const view = render(
    <Assignment
      snapshot={s}
      game={{ ...game, mode: "ASSIGNMENT" }}
      pending={false}
      send={send}
    />,
  );
  fireEvent.change(screen.getByLabelText("Bài làm của bạn"), {
    target: { value: "Bài đang viết" },
  });
  view.unmount();
  render(
    <Assignment
      snapshot={s}
      game={{ ...game, mode: "ASSIGNMENT" }}
      pending={false}
      send={send}
    />,
  );
  expect(
    (screen.getByLabelText("Bài làm của bạn") as HTMLTextAreaElement).value,
  ).toBe("Bài đang viết");
  expect(send).not.toHaveBeenCalled();
});
it("does not award a non-pool participant maximum points or show a completed wheel as waiting", () => {
  render(
    <StudentWheel
      snapshot={{
        ...snapshot,
        wheel: {
          revision: 1,
          status: "COMPLETED",
          pool: [],
          selected: [],
          cells: ["Thử thách"],
          challengeIndex: 0,
          grades: {},
        },
      }}
      game={{
        ...game,
        mode: "LUCKY_WHEEL",
        position: 1,
        status: "COMPLETED",
        score: 0,
      }}
    />,
  );
  expect(screen.getByText(/Bạn chưa tham gia tập quay hợp lệ/)).toBeTruthy();
  expect(screen.queryByText(/Chờ Server xác nhận tập quay/)).toBeNull();
  expect(screen.queryByText(/được Server ghi nhận mức tối đa/)).toBeNull();
});
