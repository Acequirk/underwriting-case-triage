import { build } from "esbuild";
import { cp, mkdir } from "node:fs/promises";

await mkdir("dist", { recursive: true });
await build({
  entryPoints: { app: "src/main.jsx" },
  bundle: true,
  minify: true,
  sourcemap: true,
  outdir: "dist",
  jsx: "automatic",
  target: "es2020",
  define: { "process.env.NODE_ENV": '"production"' },
});
await cp("index.html", "dist/index.html");
console.log("Built to dist/");
