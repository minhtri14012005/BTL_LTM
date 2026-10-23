import { spawnSync } from "node:child_process";
import { createHash } from "node:crypto";
import { readFileSync, writeFileSync, existsSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const env = {
  ...process.env,
  PATH: `${dirname(process.execPath)}${process.platform === "win32" ? ";" : ":"}${process.env.PATH || ""}`,
};
function npm(args) {
  const result = spawnSync(
    process.platform === "win32" ? "cmd.exe" : "npm",
    process.platform === "win32"
      ? ["/d", "/s", "/c", `npm.cmd ${args.join(" ")}`]
      : args,
    { cwd: root, env, stdio: "inherit" },
  );
  if (result.error) throw result.error;
  if (result.status !== 0) process.exit(result.status || 1);
}
const hash = createHash("sha256")
  .update(readFileSync(resolve(root, "package-lock.json")))
  .update(process.version)
  .digest("hex");
const marker = resolve(root, "node_modules/.multigame-lock");
if (!existsSync(marker) || readFileSync(marker, "utf8") !== hash) {
  npm([
    "ci",
    "--prefer-offline",
    "--no-audit",
    "--no-fund",
    "--cache",
    "../.cache/npm",
  ]);
  writeFileSync(marker, hash);
}
npm(["run", "build"]);
