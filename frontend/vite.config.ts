import { loadEnv } from "vite";
import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";
import { fileURLToPath, URL } from "node:url";

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), "");
  const target = env.BACKEND_URL || "http://localhost:8080";
  return {
    plugins: [react(), tailwindcss()],
    resolve: {
      alias: { "@": fileURLToPath(new URL("./src", import.meta.url)) },
    },
    server: {
      host: true,
      proxy: {
        "/api": { target, changeOrigin: true },
        // Preserve Origin: the backend explicitly checks the browser's dev/LAN origin.
        "/ws": { target, ws: true, changeOrigin: false },
      },
    },
    build: { outDir: "../target/frontend", emptyOutDir: true },
    test: { environment: "jsdom", restoreMocks: true, clearMocks: true },
  };
});
