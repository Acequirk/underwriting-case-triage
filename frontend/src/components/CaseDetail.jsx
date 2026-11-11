import { useState } from "react";
import Field from "./Field.jsx";
import { Badge, Errors, ScoreBreakdown, AuditTrail } from "./Parts.jsx";
import {
  availableActions, overrideChoices, validateReason, label, bandTone, slaBadge, describeSla, formatMoney,
} from "../logic.js";

function ReasonAction({ id, title, options, needsReason, check, disabledWhy, onRun, buttonText }) {
  const [choice, setChoice] = useState(options[0] ?? "");
  const [reason, setReason] = useState("");
  const [errors, setErrors] = useState([]);
  const value = options.includes(choice) ? choice : options[0] ?? "";
  async function run() {
    const problem = needsReason(value) ? check(reason) : null;
    if (problem) return setErrors([problem]);
    try {
      setErrors([]);
      await onRun(value, reason.trim());
      setReason("");
    } catch (err) {
      setErrors(err.messages ?? [err.message]);
    }
  }
  return (
    <fieldset disabled={Boolean(disabledWhy)}>
      <legend>{title}</legend>
      {disabledWhy && <p className="muted">{disabledWhy}</p>}
      <Errors messages={errors} />
      <Field id={`${id}-choice`} label="Choice">
        <select id={`${id}-choice`} value={value} onChange={(e) => setChoice(e.target.value)}>
          {options.map((o) => <option key={o} value={o}>{label(o)}</option>)}
        </select>
      </Field>
      <Field id={`${id}-reason`} label="Reason" hint={needsReason(value) ? "(required)" : "(optional)"}>
        <input id={`${id}-reason`} value={reason} onChange={(e) => setReason(e.target.value)} />
      </Field>
      <button type="button" onClick={run}>{buttonText}</button>
    </fieldset>
  );
}

export default function CaseDetail({ c, user, onAction }) {
  const [errors, setErrors] = useState([]);
  const can = availableActions(c, user);
  const sla = slaBadge(c.slaStatus);
  const guard = (fn) => async (...args) => {
    try {
      setErrors([]);
      await fn(...args);
    } catch (err) {
      setErrors(err.messages ?? [err.message]);
      throw err;
    }
  };
  const overrides = overrideChoices(c);

  return (
    <article aria-label={`Case ${c.id}`}>
      <h2>Case #{c.id} · {c.applicantRef}</h2>
      <p>
        <Badge tone={bandTone(c.band)}>{label(c.band)} risk, score {c.score}</Badge>{" "}
        <Badge tone={sla.tone}>{sla.label}</Badge> <span className="muted">{describeSla(c)}</span>
      </p>
      <dl>
        <dt>Status</dt><dd>{label(c.status)}</dd>
        <dt>Route</dt><dd>{label(c.route)}{c.overridden ? ` (triage chose ${label(c.triageRoute)})` : ""}</dd>
        <dt>Product</dt><dd>{label(c.productType)}, ${formatMoney(c.sumAssured)}</dd>
        <dt>Assignee</dt><dd>{c.assignee ? `${c.assignee} (${label(c.assigneeAuthority)})` : "Unassigned"}</dd>
        {c.decision && (<><dt>Decision</dt><dd>{label(c.decision)}{c.decisionReason ? `: ${c.decisionReason}` : ""}</dd></>)}
      </dl>
      <p>{c.rationale}</p>
      <h3>Score breakdown</h3>
      <ScoreBreakdown rules={c.firedRules} score={c.score} />
      <Errors messages={errors} />
      <div className="actions">
        <button type="button" disabled={!can.receiveDocuments.allowed}
          onClick={guard(() => onAction("documents", c.id))}>
          Mark documents received
        </button>
        <button type="button" disabled={!can.assign.allowed}
          onClick={guard(() => onAction("assign", c.id))}
          title={can.assign.why}>
          Assign to me ({user.name})
        </button>
        {!can.assign.allowed && c.status !== "DECIDED" && c.status !== "AWAITING_DOCUMENTS" && <p className="muted">{can.assign.why}</p>}
        <ReasonAction id="decide" title="Decide" options={["APPROVE", "DECLINE", "COUNTER_OFFER"]}
          needsReason={(o) => o !== "APPROVE"} check={(r) => validateReason(r, "decline or counter-offer")}
          disabledWhy={can.decide.allowed ? "" : can.decide.why}
          onRun={guard((d, r) => onAction("decision", c.id, { decision: d, reason: r }))} buttonText="Record decision" />
        {overrides.length > 0 && (
          <ReasonAction id="override" title="Override route" options={overrides}
            needsReason={() => true} check={(r) => validateReason(r, "override the route")}
            disabledWhy={can.override.allowed ? "" : can.override.why}
            onRun={guard((route, r) => onAction("override", c.id, { route, reason: r }))} buttonText="Override route" />
        )}
        {c.forcedSeniorReview && <p className="muted">A hard rule applies, so this case cannot be routed below senior review.</p>}
      </div>
      <h3>Audit trail</h3>
      <AuditTrail events={c.audit} />
    </article>
  );
}
