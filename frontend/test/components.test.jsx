import test from "node:test";
import assert from "node:assert/strict";
import { renderToStaticMarkup as html } from "react-dom/server";
import { QueueTable, ScoreBreakdown, SummaryCards, Errors, AuditTrail, RulesExplainer } from "../src/components/Parts.jsx";
import CaseDetail from "../src/components/CaseDetail.jsx";
import SubmitForm from "../src/components/SubmitForm.jsx";

const kase = {
  id: 7, applicantRef: "APP-7", productType: "TERM_LIFE", sumAssured: 2500000, age: 52, smoker: true, bmi: 24,
  status: "TRIAGED", route: "SENIOR_REVIEW", triageRoute: "SENIOR_REVIEW", overridden: false, score: 80, band: "HIGH",
  forcedSeniorReview: true, assignee: null, assigneeAuthority: null, decision: null, decisionReason: null,
  slaStatus: "AT_RISK", minutesRemaining: 600, rationale: "Hard rule applied.",
  firedRules: [{ ruleId: "R11", name: "Very large sum", points: 10, reason: "Sum is 2.5M", forcesSeniorReview: true }],
  audit: [{ at: "2026-01-01T00:00:00Z", actor: "alex", action: "SUBMITTED", detail: "Submitted APP-7" }],
};
const junior = { name: "alex", authority: "JUNIOR" };

test("queue table shows case, risk and SLA", () => {
  const out = html(<QueueTable cases={[kase]} selectedId={null} onSelect={() => {}} />);
  assert.match(out, /APP-7/);
  assert.match(out, /High 80/);
  assert.match(out, /At risk/);
  assert.match(out, /Due in 10h 0m/);
});

test("empty queue message", () => {
  assert.match(html(<QueueTable cases={[]} onSelect={() => {}} />), /No cases/);
});

test("score breakdown shows hard rule badge and reason", () => {
  const out = html(<ScoreBreakdown rules={kase.firedRules} score={10} />);
  assert.match(out, /hard rule/);
  assert.match(out, /Sum is 2.5M/);
});

test("summary cards flag breaches", () => {
  const out = html(<SummaryCards summary={{ total: 3, awaitingDocuments: 0, open: 3, decided: 0, atRisk: 1, breached: 2 }} />);
  assert.match(out, /stat bad/);
  assert.equal(html(<SummaryCards summary={null} />), "");
});

test("errors render as alert list, nothing when empty", () => {
  assert.match(html(<Errors messages={["bad"]} />), /role="alert"/);
  assert.equal(html(<Errors messages={[]} />), "");
});

test("audit trail renders events", () => {
  assert.match(html(<AuditTrail events={kase.audit} />), /SUBMITTED/);
});

test("case detail disables assign for a junior on senior review and explains why", () => {
  const out = html(<CaseDetail c={kase} user={junior} onAction={() => {}} />);
  assert.match(out, /Senior review needs a senior underwriter/);
  assert.match(out, /<button type="button" disabled=""[^>]*>Assign to me/);
  assert.match(out, /cannot be routed below senior review/);
  assert.match(out, /Assign the case first/);
});

test("case detail lets a senior assign", () => {
  const out = html(<CaseDetail c={kase} user={{ name: "sam", authority: "SENIOR" }} onAction={() => {}} />);
  assert.doesNotMatch(out, /<button type="button" disabled=""[^>]*>Assign to me/);
});

test("override offers no downgrade for a hard-rule case already at senior review", () => {
  assert.doesNotMatch(html(<CaseDetail c={kase} user={junior} onAction={() => {}} />), /Override route/);
});

test("submit form labels every field", () => {
  const out = html(<SubmitForm actor="alex" onSubmit={() => {}} />);
  for (const id of ["ref", "product", "sum", "age", "bmi", "occ", "country"]) {
    assert.match(out, new RegExp(`for="${id}"`));
    assert.match(out, new RegExp(`id="${id}"`));
  }
  assert.match(out, /Synthetic data only/);
});

test("rules explainer lists rules and targets", () => {
  const info = { note: "Invented.", bands: { MEDIUM: 25, HIGH: 50 }, rules: [{ id: "R01", name: "Age", description: "d", points: 15, forcesSeniorReview: false }], routes: [{ route: "FAST_TRACK", targetHours: 24 }, { route: "AWAITING_DOCUMENTS", targetHours: 0 }] };
  const out = html(<RulesExplainer info={info} />);
  assert.match(out, /R01/);
  assert.match(out, /24 hours/);
  assert.doesNotMatch(out, /Awaiting documents:/);
});
