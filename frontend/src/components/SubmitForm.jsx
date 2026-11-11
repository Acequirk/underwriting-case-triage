import { useState } from "react";
import Field from "./Field.jsx";
import { Errors } from "./Parts.jsx";
import { PRODUCTS, COUNTRY_RISKS, emptyApplication, validateApplication, toPayload, label } from "../logic.js";

export default function SubmitForm({ actor, onSubmit }) {
  const [v, setV] = useState(emptyApplication());
  const [errors, setErrors] = useState([]);
  const [busy, setBusy] = useState(false);
  const set = (k) => (e) => setV({ ...v, [k]: e.target.type === "checkbox" ? e.target.checked : e.target.value });

  async function submit(e) {
    e.preventDefault();
    const problems = validateApplication(v);
    setErrors(problems);
    if (problems.length) return;
    setBusy(true);
    try {
      await onSubmit(toPayload(v, actor));
      setV(emptyApplication());
    } catch (err) {
      setErrors(err.messages ?? [err.message]);
    } finally {
      setBusy(false);
    }
  }

  return (
    <form onSubmit={submit} noValidate aria-label="Submit application">
      <h2>Submit an application</h2>
      <p className="muted">Synthetic data only. Do not enter real applicant details.</p>
      <Errors messages={errors} />
      <Field id="ref" label="Applicant reference" hint="(e.g. APP-1001)">
        <input id="ref" value={v.applicantRef} onChange={set("applicantRef")} />
      </Field>
      <Field id="product" label="Product">
        <select id="product" value={v.productType} onChange={set("productType")}>
          {PRODUCTS.map((p) => <option key={p} value={p}>{label(p)}</option>)}
        </select>
      </Field>
      <Field id="sum" label="Sum assured">
        <input id="sum" inputMode="numeric" value={v.sumAssured} onChange={set("sumAssured")} />
      </Field>
      <Field id="age" label="Age">
        <input id="age" inputMode="numeric" value={v.age} onChange={set("age")} />
      </Field>
      <Field id="bmi" label="BMI">
        <input id="bmi" inputMode="decimal" value={v.bmi} onChange={set("bmi")} />
      </Field>
      <Field id="occ" label="Occupation class" hint="(1 to 4)">
        <select id="occ" value={v.occupationClass} onChange={set("occupationClass")}>
          {[1, 2, 3, 4].map((n) => <option key={n} value={n}>{n}</option>)}
        </select>
      </Field>
      <Field id="country" label="Country risk">
        <select id="country" value={v.countryRisk} onChange={set("countryRisk")}>
          {COUNTRY_RISKS.map((c) => <option key={c} value={c}>{label(c)}</option>)}
        </select>
      </Field>
      <div className="checks">
        {[["smoker", "Smoker"], ["priorClaims", "Prior claims"], ["pep", "Politically exposed person"], ["docsComplete", "Documents complete"]].map(([k, text]) => (
          <label key={k}>
            <input type="checkbox" checked={v[k]} onChange={set(k)} /> {text}
          </label>
        ))}
      </div>
      <button type="submit" disabled={busy}>{busy ? "Submitting…" : "Submit application"}</button>
    </form>
  );
}
