import { useCallback, useEffect, useState } from "react";
import { api } from "./api.js";
import { Errors, SummaryCards, QueueTable, RulesExplainer } from "./components/Parts.jsx";
import SubmitForm from "./components/SubmitForm.jsx";
import CaseDetail from "./components/CaseDetail.jsx";

const USERS = [
  { name: "alex.junior", authority: "JUNIOR" },
  { name: "sam.senior", authority: "SENIOR" },
];

export default function App() {
  const [user, setUser] = useState(USERS[0]);
  const [tab, setTab] = useState("queue");
  const [queue, setQueue] = useState([]);
  const [summary, setSummary] = useState(null);
  const [rules, setRules] = useState(null);
  const [selected, setSelected] = useState(null);
  const [errors, setErrors] = useState([]);

  const refresh = useCallback(async () => {
    try {
      const [q, s] = await Promise.all([api.queue(), api.summary()]);
      setQueue(q);
      setSummary(s);
      setErrors([]);
    } catch (err) {
      setErrors(err.messages ?? [err.message]);
    }
  }, []);

  useEffect(() => { refresh(); }, [refresh]);
  useEffect(() => { api.rules().then(setRules).catch(() => {}); }, []);

  async function open(id) {
    setSelected(await api.getCase(id));
    setTab("queue");
  }

  async function act(kind, id, extra = {}) {
    let updated;
    if (kind === "documents") updated = await api.receiveDocuments(id, user.name);
    else if (kind === "assign") updated = await api.assign(id, user.name, user.authority);
    else if (kind === "decision") updated = await api.decide(id, user.name, extra.decision, extra.reason);
    else updated = await api.override(id, user.name, extra.route, extra.reason);
    setSelected(updated);
    await refresh();
  }

  async function submit(payload) {
    const created = await api.submit(payload);
    setSelected(created);
    setTab("queue");
    await refresh();
  }

  return (
    <main>
      <header className="userbar">
        <h1>Underwriting Case Triage</h1>
        <label>
          Signed in as{" "}
          <select aria-label="Signed in as" value={user.name}
            onChange={(e) => setUser(USERS.find((u) => u.name === e.target.value))}>
            {USERS.map((u) => <option key={u.name} value={u.name}>{u.name} ({u.authority.toLowerCase()})</option>)}
          </select>
        </label>
      </header>
      <p className="note">Portfolio project. All data is synthetic; rules and weights are invented.</p>
      <Errors messages={errors} />
      <SummaryCards summary={summary} />
      <nav className="tabs">
        {[["queue", "Queue"], ["submit", "Submit"], ["rules", "How scoring works"]].map(([k, text]) => (
          <button key={k} aria-pressed={tab === k} onClick={() => setTab(k)}>{text}</button>
        ))}
      </nav>
      {tab === "queue" && (
        <div className="layout-2">
          <section>
            <h2>Work queue <span className="muted">(most urgent first)</span></h2>
            <QueueTable cases={queue} selectedId={selected?.id} onSelect={open} />
          </section>
          <section>
            {selected ? <CaseDetail key={selected.id} c={selected} user={user} onAction={act} /> : <p className="muted">Select a case to see its score breakdown and audit trail.</p>}
          </section>
        </div>
      )}
      {tab === "submit" && <SubmitForm actor={user.name} onSubmit={submit} />}
      {tab === "rules" && <RulesExplainer info={rules} />}
    </main>
  );
}
