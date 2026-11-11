/** A form field whose label is explicitly linked to its control (better for screen readers). */
export default function Field({ id, label, hint, children }) {
  return (
    <div className="field">
      <label htmlFor={id}>
        {label}
        {hint ? <span className="muted"> {hint}</span> : null}
      </label>
      {children}
    </div>
  );
}
