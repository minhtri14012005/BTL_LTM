import {
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
} from "@testing-library/react";
import { afterEach, it, expect, vi } from "vitest";
import { GameConfiguration } from "./game-configuration";
import type { Game, Snapshot } from "./teaching-api";
const api = vi.hoisted(() => vi.fn());
vi.mock("@/lib/api", async (original) => ({
  ...(await original<typeof import("@/lib/api")>()),
  api,
}));
afterEach(() => {
  cleanup();
  api.mockReset();
});
it.each(["QUIZ", "FILL_BLANK"] as const)(
  "saves ten of twenty %s questions with duration and unchanged total budget",
  async (mode) => {
    api.mockResolvedValue({
      questions: Array.from({ length: 20 }, () => ({})),
    });
    const game = {
      id: 8,
      position: 2,
      mode,
      status: "READY",
      budget: 40,
      durationMs: 30000,
      questionCount: 20,
    } as Game;
    const snapshot = {
      lesson: { id: 1, revision: 3 },
      questionCounts: { 8: 20 },
    } as unknown as Snapshot;
    const refresh = vi.fn().mockResolvedValue(undefined);
    const run = vi.fn(async (work: () => Promise<unknown>) => {
      await work();
      return true;
    });
    const view = render(
      <GameConfiguration
        game={game}
        snapshot={snapshot}
        disabled={false}
        run={run}
        refresh={refresh}
        command={vi.fn()}
      />,
    );
    await screen.findByText(/Bộ câu hỏi có 20 câu hợp lệ/);
    expect(screen.queryByLabelText("Điểm mỗi câu đúng")).toBeNull();
    fireEvent.change(screen.getByLabelText("Số câu hỏi sử dụng"), {
      target: { value: "10" },
    });
    fireEvent.change(screen.getByLabelText("Thời gian mỗi câu (giây)"), {
      target: { value: "15" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Lưu cấu hình chung" }));
    await waitFor(() => expect(refresh).toHaveBeenCalledOnce());
    const put = api.mock.calls.find(([, options]) => options.method === "PUT")!;
    expect(JSON.parse(put[1].body)).toMatchObject({
      questionCount: 10,
      durationMs: 15000,
      budget: 40,
    });
    expect(JSON.parse(put[1].body)).not.toHaveProperty("pointsPerCorrect");
    fireEvent.change(screen.getByLabelText("Số câu hỏi sử dụng"), {
      target: { value: "21" },
    });
    fireEvent.submit(
      screen
        .getByRole("button", { name: "Lưu cấu hình chung" })
        .closest("form")!,
    );
    expect(screen.getByRole("alert").textContent).toContain("Số câu sử dụng");
    expect(run).toHaveBeenCalledOnce();
    view.rerender(
      <GameConfiguration
        game={{ ...game, status: "RUNNING" }}
        snapshot={snapshot}
        disabled={false}
        run={run}
        refresh={refresh}
        command={vi.fn()}
      />,
    );
    expect(
      screen.getByLabelText("Số câu hỏi sử dụng").closest("fieldset")!.disabled,
    ).toBe(true);
  },
);
