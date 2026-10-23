import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, expect, it, vi } from "vitest";
import { cents, planError, questionError } from "./teaching-api";
import { QuestionEditor, questionPayload } from "./question-editor";
import { rankError } from "./rank-panel";
afterEach(cleanup);
it("validates exact three different slots, decimal budgets and the total without floating point sums", () => {
  expect(
    planError([
      { mode: "LUCKY_WHEEL", budget: "20" },
      { mode: "QUIZ", budget: "40.01" },
      { mode: "FILL_BLANK", budget: "39.99" },
    ]),
  ).toBe("");
  expect(
    planError([
      { mode: "LUCKY_WHEEL", budget: "20" },
      { mode: "QUIZ", budget: "40.01" },
      { mode: "FILL_BLANK", budget: "40" },
    ]),
  ).toContain("100");
  expect(
    planError([
      { mode: "LUCKY_WHEEL", budget: "20" },
      { mode: "QUIZ", budget: "40" },
      { mode: "QUIZ", budget: "40" },
    ]),
  ).toContain("khác nhau");
  expect(cents("1.005")).toBeNaN();
  expect(cents("-1")).toBeNaN();
});
it("keeps Vietnamese and exact case answers and validates exactly one blank in every sentence", () => {
  expect(
    questionError("FILL_BLANK", [
      {
        content: "Thủ đô là {{blank}}.",
        acceptedAnswers: ["Hà Nội", "HANOI"],
        matchingPolicy: "CASE_INSENSITIVE",
      },
    ]),
  ).toBe("");
  expect(
    questionError("FILL_BLANK", [
      {
        content: "{{blank}} và {{blank}}",
        acceptedAnswers: ["a"],
        matchingPolicy: "EXACT",
      },
    ]),
  ).toContain("đúng một");
  expect(
    questionPayload("FILL_BLANK", {
      content: "{{blank}}",
      acceptedAnswers: ["System.out"],
      matchingPolicy: "EXACT",
      correctAnswer: "A",
    }),
  ).toEqual({
    content: "{{blank}}",
    acceptedAnswers: ["System.out"],
    matchingPolicy: "EXACT",
  });
});
it("rejects overlapping ranks while accepting adjacent intervals", () => {
  expect(
    rankError([
      { name: "Cao", from: 1, to: 10 },
      { name: "Tiếp", from: 10, to: 20 },
    ]),
  ).toContain("chồng lấn");
  expect(
    rankError([
      { name: "Cao", from: 1, to: 10 },
      { name: "Tiếp", from: 11, to: 20 },
    ]),
  ).toBe("");
});
it("saves only the edited snapshot payload and does not mutate the original questions", () => {
  const questions = [{ content: "Đề nguồn" }],
    save = vi.fn();
  render(
    <QuestionEditor
      mode="ASSIGNMENT"
      initialTitle="Bản sao"
      initialQuestions={questions}
      pending={false}
      error=""
      onSave={save}
    />,
  );
  fireEvent.change(screen.getByLabelText("Nội dung đề bài"), {
    target: { value: "Đề chỉnh riêng" },
  });
  fireEvent.click(screen.getByRole("button", { name: "Lưu nội dung" }));
  expect(save).toHaveBeenCalledWith(
    "Bản sao",
    [{ content: "Đề chỉnh riêng" }],
    "PRIVATE",
  );
  expect(questions[0].content).toBe("Đề nguồn");
});
