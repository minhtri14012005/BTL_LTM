import { Button } from "@/components/ui/button";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { api } from "@/lib/api";
import { useSearchParams } from "react-router-dom";
import {
  AssignmentControls,
  QuizProgress,
  TeacherResults,
} from "./activity-controls";
import { GameConfiguration } from "./game-configuration";
import { GameContent } from "./game-content";
import type { Game, Snapshot } from "./teaching-api";
import {
  Notice,
  useTeachingAction,
  useTeachingSnapshot,
} from "./teaching-shared";
import { WheelControls } from "./wheel-controls";
export function TeachingGamePanel({
  id,
  data,
  game,
  live,
  action,
  disabled,
  busyContents,
  onContentBusy,
}: {
  id: number;
  data: Snapshot;
  game: Game;
  live: ReturnType<typeof useTeachingSnapshot>;
  action: ReturnType<typeof useTeachingAction>;
  disabled: boolean;
  busyContents: Record<number, boolean>;
  onContentBusy: (id: number, busy: boolean) => void;
}) {
  const [params, setParams] = useSearchParams();
  const selected = params.get("panel");
  const panel =
    ["control", "configuration", "content", "results"].includes(
      selected || "",
    ) && !(game.mode === "LUCKY_WHEEL" && selected === "content")
      ? selected!
      : "control";
  return (
    <div className="space-y-5">
      <Button
        variant="outline"
        disabled={disabled}
        onClick={() => {
          void action.run(async () => {
            await api(
              `/api/teaching/lessons/${id}/games/${game.position}/visibility`,
              {
                method: "PUT",
                body: JSON.stringify({
                  revision: data.lesson.revision,
                  visible: game.visible === false,
                }),
              },
            );
            await live.refresh();
          });
        }}
      >
        {game.visible === false ? "Hiện trò chơi" : "Ẩn trò chơi"}
      </Button>
      <Tabs
        value={panel}
        onValueChange={(value) => setParams({ panel: value })}
      >
        <div className="overflow-x-auto">
          <TabsList aria-label="Quản lý trò chơi">
            <TabsTrigger
              value="control"
              disabled={
                action.pending || Object.values(busyContents).some(Boolean)
              }
            >
              Điều khiển
            </TabsTrigger>
            <TabsTrigger
              value="configuration"
              disabled={
                action.pending || Object.values(busyContents).some(Boolean)
              }
            >
              Cấu hình chung
            </TabsTrigger>
            {game.mode !== "LUCKY_WHEEL" && (
              <TabsTrigger
                value="content"
                disabled={
                  action.pending || Object.values(busyContents).some(Boolean)
                }
              >
                Nội dung trò
              </TabsTrigger>
            )}
            <TabsTrigger
              value="results"
              disabled={
                action.pending || Object.values(busyContents).some(Boolean)
              }
            >
              Kết quả
            </TabsTrigger>
          </TabsList>
        </div>
        <TabsContent value="configuration">
          {" "}
          <GameConfiguration
            key={`${game.id}:${game.budget}:${game.durationMs}:${game.questionCount}:${data.questionCounts[String(game.id)]}`}
            snapshot={data}
            game={game}
            disabled={disabled}
            run={action.run}
            refresh={live.refresh}
            command={live.command}
          />
        </TabsContent>
        <TabsContent value="content">
          {" "}
          {game.mode !== "LUCKY_WHEEL" && (
            <GameContent
              lesson={data.lesson}
              game={game}
              refresh={live.refresh}
              locked={
                action.pending ||
                live.disabled ||
                Object.entries(busyContents).some(
                  ([id, busy]) => Number(id) !== game.id && busy,
                )
              }
              onBusyChange={onContentBusy}
            />
          )}
        </TabsContent>
        <TabsContent value="control">
          <div className="space-y-5">
            {game.status === "READY" && (
              <Notice>
                Chuẩn bị nội dung và lưu cấu hình chung trước khi bắt đầu trò.
              </Notice>
            )}{" "}
            {game.status === "READY" && (
              <Button
                disabled={
                  disabled ||
                  data.lesson.status !== "RUNNING" ||
                  data.lesson.games.some(
                    (g) =>
                      g.position < game.position && g.status !== "COMPLETED",
                  )
                }
                onClick={() => {
                  void action.run(
                    () =>
                      live.command("START_LESSON_GAME", {
                        position: game.position,
                        revision: data.lesson.revision,
                      }),
                    "Đã bắt đầu trò.",
                  );
                }}
              >
                Bắt đầu trò {game.position}
              </Button>
            )}
            {game.mode === "LUCKY_WHEEL" && game.status !== "READY" && (
              <WheelControls
                actionError={action.error}
                snapshot={data}
                game={game}
                disabled={disabled || game.status !== "RUNNING"}
                run={action.run}
                refresh={live.refresh}
              />
            )}
            {["QUIZ", "FILL_BLANK"].includes(game.mode) &&
              game.status !== "READY" && (
                <QuizProgress
                  snapshot={data}
                  game={game}
                />
              )}
            {game.mode === "ASSIGNMENT" && (
              <AssignmentControls
                actionError={action.error}
                snapshot={data}
                game={game}
                disabled={disabled || game.status === "COMPLETED"}
                command={live.command}
                run={action.run}
              />
            )}
          </div>
        </TabsContent>
        <TabsContent value="results">
          <TeacherResults snapshot={data} game={game} />
        </TabsContent>
      </Tabs>
    </div>
  );
}
