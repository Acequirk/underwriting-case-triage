// Pure helpers: no React, no network. Easy to test, and the place where UI rules live.

export const PRODUCTS = ["TERM_LIFE", "WHOLE_LIFE", "CRITICAL_ILLNESS", "HEALTH"];
export const COUNTRY_RISKS = ["LOW", "MEDIUM", "HIGH"];
export const REVIEW_ROUTES = ["FAST_TRACK", "STANDARD", "SENIOR_REVIEW"];
export const MIN_REASON = 10;

export const label = (value) =>
  value ? value.toLowerCase().replace(/_/g, " ").replace(/^\w/, (c) => c.toUpperCase()) : "";

export const formatMoney = (n) => Number(n).toLocaleString("en-US");

export function bandTone(band) {
  return { LOW: "ok", MEDIUM: "warn", HIGH: "bad" }[band] ?? "neutral";
}

export function slaBadge(slaStatus) {
  const map = {
    ON_TRACK: { label: "On track", tone: "ok" },
    AT_RISK: { label: "At risk", tone: "warn" },
    BREACHED: { label: "Breached", tone: "bad" },
    MET: { label: "Met", tone: "ok" },
    PAUSED: { label: "Paused", tone: "neutral" },
  };
  return map[slaStatus] ?? { label: slaStatus ?? "", tone: "neutral" };
}

function duration(totalMinutes) {
  const m = Math.abs(Math.round(totalMinutes));
  const days = Math.floor(m / 1440);
  const hours = Math.floor((m % 1440) / 60);
  const mins = m % 60;
  if (days > 0) return `${days}d ${hours}h`;
  if (hours > 0) return `${hours}h ${mins}m`;
  return `${mins}m`;
}

/** Human wording for the service clock of a case. */
export function describeSla(c) {
  switch (c.slaStatus) {
    case "PAUSED":
      return "Clock paused until documents arrive";
    case "MET":
      return "Decided within target";
    case "BREACHED":
      return c.minutesRemaining == null ? "Decided after target" : `Overdue by ${duration(c.minutesRemaining)}`;
    default:
      return c.minutesRemaining == null ? "" : `Due in ${duration(c.minutesRemaining)}`;
  }
}

/** What the signed-in user may do to a case right now, with the reason when they may not. */
export function availableActions(c, user) {
  const none = { why: "" };
  const result = {
    receiveDocuments: { ...none, allowed: c.status === "AWAITING_DOCUMENTS" },
    assign: { ...none, allowed: false },
    decide: { ...none, allowed: false },
    override: { ...none, allowed: false },
  };
  if (c.status === "DECIDED") {
    const why = "This case has been decided.";
    result.assign = { allowed: false, why };
    result.decide = { allowed: false, why };
    result.override = { allowed: false, why };
    return result;
  }
  if (c.status === "AWAITING_DOCUMENTS") {
    const why = "Waiting for documents.";
    result.assign = { allowed: false, why };
    result.decide = { allowed: false, why };
    result.override = { allowed: false, why };
    return result;
  }
  if (c.route === "SENIOR_REVIEW" && user.authority !== "SENIOR") {
    result.assign = { allowed: false, why: "Senior review needs a senior underwriter." };
  } else {
    result.assign = { allowed: true, why: "" };
  }
  if (c.status === "ASSIGNED") {
    result.decide =
      c.assignee === user.name
        ? { allowed: true, why: "" }
        : { allowed: false, why: `Only ${c.assignee} can decide this case.` };
  } else {
    result.decide = { allowed: false, why: "Assign the case first." };
  }
  result.override = { allowed: true, why: "" };
  return result;
}

/** Routes a user may choose when overriding: hard rules pin a case to senior review. */
export function overrideChoices(c) {
  const routes = REVIEW_ROUTES.filter((r) => r !== c.route);
  return c.forcedSeniorReview ? routes.filter((r) => r === "SENIOR_REVIEW") : routes;
}

export const emptyApplication = () => ({
  applicantRef: "",
  productType: "TERM_LIFE",
  sumAssured: "",
  age: "",
  bmi: "",
  occupationClass: "1",
  countryRisk: "LOW",
  smoker: false,
  priorClaims: false,
  pep: false,
  docsComplete: true,
});

/** Client-side checks that mirror the server, so people get instant feedback. */
export function validateApplication(v) {
  const errors = [];
  const ref = v.applicantRef.trim();
  if (!ref) errors.push("applicantRef is required");
  else if (ref.length > 40) errors.push("applicantRef must be 40 characters or fewer");
  const sum = Number(v.sumAssured);
  if (v.sumAssured === "" || !Number.isInteger(sum)) errors.push("sumAssured must be a whole number");
  else if (sum <= 0) errors.push("sumAssured must be greater than 0");
  else if (sum > 10_000_000) errors.push("sumAssured must not exceed 10000000");
  const age = Number(v.age);
  if (v.age === "" || !Number.isInteger(age) || age < 18 || age > 75) errors.push("age must be between 18 and 75");
  const bmi = Number(v.bmi);
  if (v.bmi === "" || Number.isNaN(bmi) || bmi < 10 || bmi > 60) errors.push("bmi must be between 10 and 60");
  const occ = Number(v.occupationClass);
  if (!Number.isInteger(occ) || occ < 1 || occ > 4) errors.push("occupationClass must be between 1 and 4");
  return errors;
}

export function toPayload(v, actor) {
  return {
    applicantRef: v.applicantRef.trim(),
    productType: v.productType,
    sumAssured: Number(v.sumAssured),
    age: Number(v.age),
    smoker: v.smoker,
    bmi: Number(v.bmi),
    occupationClass: Number(v.occupationClass),
    priorClaims: v.priorClaims,
    countryRisk: v.countryRisk,
    pep: v.pep,
    docsComplete: v.docsComplete,
    actor,
  };
}

export function validateReason(reason, what) {
  return reason.trim().length >= MIN_REASON
    ? null
    : `Give a reason of at least ${MIN_REASON} characters to ${what}.`;
}

/** Width (percent) of a rule's bar, relative to the largest single rule weight. */
export function barWidth(points, max = 30) {
  return Math.max(4, Math.min(100, Math.round((points / max) * 100)));
}

export function timeLabel(iso) {
  return iso ? iso.replace("T", " ").replace(/:\d\d(\.\d+)?Z$/, " UTC") : "";
}
