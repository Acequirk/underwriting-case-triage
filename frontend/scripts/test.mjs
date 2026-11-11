// Bundles the tests with esbuild (so JSX works), then runs them with Node's built-in test runner.
import { build } from "esbuild";
import { readdir, rm } from "node:fs/promises";
import { spawnSync } from "node:child_process";

const files = (await readdir("test")).filter((f) => /\.test\.jsx?$/.test(f));
await rm(".test-out", { recursive: true, force: true });
await build({
  entryPoints: files.map((f) => `test/${f}`),
  bundle: true,
  platform: "node",
  format: "esm",
  packages: "external",
  jsx: "automatic",
  outdir: ".test-out",
  outExtension: { ".js": ".mjs" },
  logLevel: "warning",
});
const compiled = files.map((f) => `.test-out/${f.replace(/\.jsx?$/, ".mjs")}`);
const result = spawnSync(process.execPath, ["--test", "--test-reporter=spec", ...compiled], { stdio: "inherit" });
process.exit(result.status ?? 1);
