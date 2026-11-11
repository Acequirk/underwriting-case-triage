// End-to-end check in a real browser against the live Java API.
// Start the API (port 8080, demo data on) and serve dist/ on port 5173 (`npm run dev`), then:
//   node e2e/smoke.mjs      (needs playwright installed)
import { createRequire } from "node:module";
import assert from "node:assert/strict";

const { chromium } = createRequire(import.meta.url)("playwright");
const URL = process.env.APP_URL ?? "http://localhost:5173";
const SHOT = process.env.SHOT;

const browser = await chromium.launch();
const page = await browser.newPage({ viewport: { width: 1280, height: 900 } });
const problems = [];
page.on("pageerror", (e) => problems.push(e.message));
page.on("console", (m) => { if (m.type() === "error") problems.push(m.text()); });

await page.goto(URL);
await page.getByRole("heading", { name: "Underwriting Case Triage" }).waitFor();
await page.getByLabel("Open case 7").waitFor();
console.log("ok  app loads with demo queue");

// submit validation
await page.getByRole("button", { name: "Submit", exact: true }).click();
await page.getByRole("button", { name: "Submit application" }).click();
await page.getByRole("alert").getByText("applicantRef is required").waitFor();
console.log("ok  empty form shows validation errors");

// submit a high-risk case (PEP -> hard rule -> senior review)
await page.locator("#ref").fill("E2E-001");
await page.locator("#sum").fill("300000");
await page.locator("#age").fill("35");
await page.locator("#bmi").fill("23");
await page.getByLabel("Politically exposed person").check();
await page.getByRole("button", { name: "Submit application" }).click();
await page.getByRole("heading", { name: /E2E-001/ }).waitFor();
await page.getByText("hard rule").first().waitFor();
const article = page.getByRole("article");
await article.getByText("Senior review").first().waitFor();
console.log("ok  PEP case routed to senior review with a hard rule");

// junior cannot assign
assert.equal(await page.getByRole("button", { name: /Assign to me/ }).isDisabled(), true);
await page.getByText("Senior review needs a senior underwriter.").waitFor();
console.log("ok  junior blocked from senior review");

// switch to senior, assign, decline needs a reason
await page.getByLabel("Signed in as").selectOption("sam.senior");
await page.getByRole("button", { name: /Assign to me/ }).click();
await page.getByRole("button", { name: "Record decision" }).waitFor();
await page.locator("#decide-choice").selectOption("DECLINE");
await page.getByRole("button", { name: "Record decision" }).click();
await page.getByRole("alert").getByText(/at least 10 characters/).waitFor();
console.log("ok  decline without a reason is refused");
await page.locator("#decide-reason").fill("PEP requires enhanced due diligence");
await page.getByRole("button", { name: "Record decision" }).click();
await page.getByText("Decided within target").waitFor();
await page.getByRole("list", { name: "Audit trail" }).getByText("DECISION").first().waitFor();
console.log("ok  decision recorded, audit trail updated");

// rules explainer
await page.getByRole("button", { name: "How scoring works" }).click();
await page.getByText("Weights and thresholds are invented").waitFor();
console.log("ok  rules explainer loads from the API");

await page.getByRole("button", { name: "Queue" }).click();
if (SHOT) await page.screenshot({ path: SHOT, fullPage: true });

assert.deepEqual(problems, []);
console.log("ok  no console errors");
await browser.close();
