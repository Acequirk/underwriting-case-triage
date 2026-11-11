import { context } from "esbuild";
import { cp, mkdir } from "node:fs/promises";

await mkdir("dist", { recursive: true });
const ctx = await context({
  entryPoints: { app: "src/main.jsx" },
  bundle: true,
  sourcemap: true,
  outdir: "dist",
  jsx: "automatic",
  target: "es2020",
  define: { "process.env.NODE_ENV": '"development"' },
});
await cp("index.html", "dist/index.html");
await ctx.watch();
const { port } = await ctx.serve({ servedir: "dist", port: 5173 });
console.log(`Front end running on http://localhost:${port}  (API expected on the address set in index.html)`);
