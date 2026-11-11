import test from "node:test";
import assert from "node:assert/strict";
import {
  availableActions, overrideChoices, validateApplication, emptyApplication, toPayload,
  validateReason, describeSla, slaBadge, bandTone, barWidth, label, timeLabel,
} from "../src/logic.js";

const senior = { name: "sam", authority: "SENIOR" };
const junior = { name: "alex", authority: "JUNIOR" };
const base = { status: "TRIAGED", route: "STANDARD", assignee: null, forcedSeniorReview: false };

test("label turns enums into words", () => {
  assert.equal(label("SENIOR_REVIEW"), "Senior review");
  assert.equal(label(null), "");
});

test("bandTone and slaBadge map to tones", () => {
  assert.equal(bandTone("HIGH"), "bad");
  assert.equal(bandTone("LOW"), "ok");
  assert.equal(slaBadge("AT_RISK").tone, "warn");
  assert.equal(slaBadge("BREACHED").label, "Breached");
});

test("describeSla covers each state", () => {
  assert.match(describeSla({ slaStatus: "PAUSED" }), /paused/i);
  assert.equal(describeSla({ slaStatus: "ON_TRACK", minutesRemaining: 90 }), "Due in 1h 30m");
  assert.equal(describeSla({ slaStatus: "ON_TRACK", minutesRemaining: 1500 }), "Due in 1d 1h");
  assert.equal(describeSla({ slaStatus: "BREACHED", minutesRemaining: -30 }), "Overdue by 30m");
  assert.match(describeSla({ slaStatus: "MET" }), /within target/);
});

test("junior cannot take a senior review case", () => {
  const a = availableActions({ ...base, route: "SENIOR_REVIEW" }, junior);
  assert.equal(a.assign.allowed, false);
  assert.match(a.assign.why, /senior/i);
  assert.equal(availableActions({ ...base, route: "SENIOR_REVIEW" }, senior).assign.allowed, true);
});

test("only the assignee may decide", () => {
  const c = { ...base, status: "ASSIGNED", assignee: "alex" };
  assert.equal(availableActions(c, junior).decide.allowed, true);
  const other = availableActions(c, senior).decide;
  assert.equal(other.allowed, false);
  assert.match(other.why, /alex/);
  assert.equal(availableActions(base, junior).decide.allowed, false);
});

test("awaiting documents and decided cases lock actions", () => {
  const w = availableActions({ ...base, status: "AWAITING_DOCUMENTS" }, senior);
  assert.equal(w.receiveDocuments.allowed, true);
  assert.equal(w.assign.allowed, false);
  const d = availableActions({ ...base, status: "DECIDED" }, senior);
  assert.ok(!d.assign.allowed && !d.decide.allowed && !d.override.allowed && !d.receiveDocuments.allowed);
});

test("hard-rule cases can only be overridden up to senior review", () => {
  assert.deepEqual(overrideChoices({ route: "STANDARD", forcedSeniorReview: true }), ["SENIOR_REVIEW"]);
  assert.deepEqual(overrideChoices({ route: "SENIOR_REVIEW", forcedSeniorReview: true }), []);
  assert.deepEqual(overrideChoices({ route: "STANDARD", forcedSeniorReview: false }), ["FAST_TRACK", "SENIOR_REVIEW"]);
});

const valid = () => ({ ...emptyApplication(), applicantRef: "APP-1", sumAssured: "100000", age: "30", bmi: "22" });

test("a valid application has no errors", () => assert.deepEqual(validateApplication(valid()), []));

test("every bad field is reported at once", () => {
  const errs = validateApplication({ ...emptyApplication(), sumAssured: "abc", age: "17", bmi: "5", applicantRef: " " });
  assert.equal(errs.length, 4);
});

test("boundaries", () => {
  const v = valid();
  assert.deepEqual(validateApplication({ ...v, age: "18" }), []);
  assert.deepEqual(validateApplication({ ...v, age: "75" }), []);
  assert.equal(validateApplication({ ...v, age: "76" }).length, 1);
  assert.equal(validateApplication({ ...v, sumAssured: "10000001" }).length, 1);
  assert.equal(validateApplication({ ...v, sumAssured: "0" }).length, 1);
  assert.equal(validateApplication({ ...v, sumAssured: "1.5" }).length, 1);
  assert.equal(validateApplication({ ...v, applicantRef: "x".repeat(41) }).length, 1);
  assert.deepEqual(validateApplication({ ...v, bmi: "10" }), []);
  assert.equal(validateApplication({ ...v, bmi: "60.5" }).length, 1);
});

test("toPayload converts types and trims", () => {
  const p = toPayload({ ...valid(), applicantRef: "  APP-1 ", smoker: true }, "alex");
  assert.equal(p.applicantRef, "APP-1");
  assert.strictEqual(p.sumAssured, 100000);
  assert.strictEqual(p.occupationClass, 1);
  assert.equal(p.smoker, true);
  assert.equal(p.actor, "alex");
});

test("reason validation", () => {
  assert.ok(validateReason("short", "decline"));
  assert.equal(validateReason("long enough reason", "decline"), null);
  assert.ok(validateReason("         ", "decline"));
});

test("barWidth and timeLabel", () => {
  assert.equal(barWidth(15, 30), 50);
  assert.equal(barWidth(0), 4);
  assert.equal(barWidth(100), 100);
  assert.equal(timeLabel("2026-01-02T03:04:05Z"), "2026-01-02 03:04 UTC");
});
