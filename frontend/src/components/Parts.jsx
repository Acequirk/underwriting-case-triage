import { bandTone, slaBadge, describeSla, label, formatMoney, barWidth, timeLabel } from "../logic.js";

export function Badge({ tone, children }) {
  return <span className={`badge tone-${tone}`}>{children}</span>;
}

export function Errors({ messages }) {
  if (!messages || messages.length === 0) return null;
  return (
    <ul className="errors" role="alert">
      {messages.map((m) => (
        <li key={m}>{m}</li>
      ))}
    </ul>
  );
}

export function SummaryCards({ summary }) {
  if (!summary) return null;
  const items = [
    ["Total cases", summary.total, ""],
    ["Awaiting documents", summary.awaitingDocuments, ""],
    ["Open", summary.open, ""],
    ["Decided", summary.decided, ""],
    ["SLA at risk", summary.atRisk, summary.atRisk > 0 ? "warn" : ""],
    ["SLA breached", summary.breached, summary.breached > 0 ? "bad" : ""],
  ];
  return (
    <section className="cards" aria-label="Summary">
      {items.map(([name, value, tone]) => (
        <div key={name} className={`stat ${tone}`}>
          <strong>{value}</strong>
          <span>{name}</span>
        </div>
      ))}
    </section>
  );
}

export function QueueTable({ cases, selectedId, onSelect }) {
  if (cases.length === 0) return <p className="muted">No cases in the queue.</p>;
  return (
    <table>
      <thead>
        <tr>
          <th>Case</th>
          <th>Applicant</th>
          <th>Route</th>
          <th>Risk</th>
          <th>SLA</th>
        </tr>
      </thead>
      <tbody>
        {cases.map((c) => {
          const sla = slaBadge(c.slaStatus);
          return (
            <tr key={c.id} className={`row-click ${c.id === selectedId ? "row-selected" : ""}`}>
              <td>
                <button onClick={() => onSelect(c.id)} aria-label={`Open case ${c.id}`}>
                  #{c.id}
                </button>
              </td>
              <td>
                {c.applicantRef}
                <div className="muted">{label(c.productType)} · ${formatMoney(c.sumAssured)}</div>
              </td>
              <td>
                {label(c.route)}
                {c.overridden && <span className="muted"> (overridden)</span>}
              </td>
              <td>
                <Badge tone={bandTone(c.band)}>
                  {label(c.band)} {c.score}
                </Badge>
              </td>
              <td>
                <Badge tone={sla.tone}>{sla.label}</Badge>
                <div className="muted">{describeSla(c)}</div>
              </td>
            </tr>
          );
        })}
      </tbody>
    </table>
  );
}

export function ScoreBreakdown({ rules, score }) {
  if (!rules || rules.length === 0) return <p className="muted">No rules fired. Score {score}.</p>;
  return (
    <div>
      {rules.map((r) => (
        <div className="rule-row" key={r.ruleId}>
          <div>
            <strong>{r.ruleId}</strong> {r.name} <span>+{r.points}</span>
            {r.forcesSeniorReview && <Badge tone="bad">hard rule</Badge>}
          </div>
          <div className="bar-track">
            <div className="bar-fill" style={{ width: `${barWidth(r.points)}%` }} />
          </div>
          <div className="muted">{r.reason}</div>
        </div>
      ))}
    </div>
  );
}

export function AuditTrail({ events }) {
  return (
    <ol className="history" aria-label="Audit trail">
      {events.map((e, i) => (
        <li key={i}>
          <strong>{e.action}</strong> — {e.detail}
          <div className="muted">
            {e.actor} · {timeLabel(e.at)}
          </div>
        </li>
      ))}
    </ol>
  );
}

export function RulesExplainer({ info }) {
  if (!info) return null;
  return (
    <section>
      <h2>How scoring works</h2>
      <p className="note">{info.note}</p>
      <p>
        Points add up across the rules below. Score {info.bands.MEDIUM}+ is Medium risk, {info.bands.HIGH}+ is High.
        Hard rules always send the case to senior review, whatever the score.
      </p>
      <table>
        <thead>
          <tr>
            <th>Rule</th>
            <th>What it checks</th>
            <th>Points</th>
          </tr>
        </thead>
        <tbody>
          {info.rules.map((r) => (
            <tr key={r.id}>
              <td>{r.id} {r.name}</td>
              <td>{r.description}</td>
              <td>
                +{r.points}
                {r.forcesSeniorReview && <Badge tone="bad">hard rule</Badge>}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      <h3>Service targets</h3>
      <ul>
        {info.routes
          .filter((r) => r.targetHours > 0)
          .map((r) => (
            <li key={r.route}>
              {label(r.route)}: {r.targetHours} hours from when documents are complete
            </li>
          ))}
      </ul>
    </section>
  );
}
