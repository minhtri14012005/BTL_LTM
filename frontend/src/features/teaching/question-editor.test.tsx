import {
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
} from "@testing-library/react";
import { afterEach, it, expect, vi } from "vitest";
import { QuestionEditor } from "./question-editor";
afterEach(cleanup);
it("edits one question at a time while retaining and validating the whole bank", async () => {
  const save = vi.fn().mockResolvedValue(undefined);
  render(
    <QuestionEditor
      mode="FILL_BLANK"
      initialTitle="Bộ ôn tập"
      initialQuestions={[
        {
          content: "Câu 1 {{blank}}",
          acceptedAnswers: ["A"],
          matchingPolicy: "EXACT",
        },
        {
          content: "Câu 2 {{blank}}",
          acceptedAnswers: ["B"],
          matchingPolicy: "EXACT",
        },
      ]}
      pending={false}
      error=""
      onSave={save}
    />,
  );
  expect(screen.queryByLabelText("Nội dung câu 2")).toBeNull();
  fireEvent.change(screen.getByLabelText("Nội dung câu 1"), {
    target: { value: "Đã sửa {{blank}}" },
  });
  fireEvent.click(screen.getByRole("button", { name: "Câu 2" }));
  fireEvent.change(screen.getByLabelText("Nội dung câu 2"), {
    target: { value: "Thiếu ô trống" },
  });
  fireEvent.click(screen.getByRole("button", { name: "Câu 1" }));
  expect(
    (screen.getByLabelText("Nội dung câu 1") as HTMLTextAreaElement).value,
  ).toBe("Đã sửa {{blank}}");
  fireEvent.submit(
    screen.getByRole("button", { name: "Lưu nội dung" }).closest("form")!,
  );
  expect(save).not.toHaveBeenCalled();
  expect(screen.getByLabelText("Nội dung câu 2")).toBeDefined();
  fireEvent.change(screen.getByLabelText("Nội dung câu 2"), {
    target: { value: "Câu 2 {{blank}}" },
  });
  fireEvent.submit(
    screen.getByRole("button", { name: "Lưu nội dung" }).closest("form")!,
  );
  await waitFor(() =>
    expect(save).toHaveBeenCalledWith(
      "Bộ ôn tập",
      expect.arrayContaining([
        expect.objectContaining({ content: "Đã sửa {{blank}}" }),
      ]),
      "PRIVATE",
    ),
  );
});
