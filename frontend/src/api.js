// Thin client for the Java API. All errors are normalised into ApiError.

export class ApiError extends Error {
  constructor(status, messages) {
    super(messages.join("; "));
    this.status = status;
    this.messages = messages;
  }
}

const baseUrl = () => globalThis.API_BASE ?? "http://localhost:8080";

async function request(path, { method = "GET", body } = {}) {
  let response;
  try {
    response = await fetch(baseUrl() + path, {
      method,
      headers: body ? { "Content-Type": "application/json" } : undefined,
      body: body ? JSON.stringify(body) : undefined,
    });
  } catch {
    throw new ApiError(0, ["Cannot reach the server. Is the API running?"]);
  }
  let data = null;
  const text = await response.text();
  if (text) {
    try {
      data = JSON.parse(text);
    } catch {
      data = null;
    }
  }
  if (!response.ok) {
    const messages = Array.isArray(data?.errors) ? data.errors : [data?.error ?? `Request failed (${response.status})`];
    throw new ApiError(response.status, messages);
  }
  return data;
}

export const api = {
  queue: () => request("/queue"),
  listCases: () => request("/cases"),
  getCase: (id) => request(`/cases/${id}`),
  summary: () => request("/reports/summary"),
  rules: () => request("/rules"),
  submit: (payload) => request("/cases", { method: "POST", body: payload }),
  receiveDocuments: (id, actor) => request(`/cases/${id}/documents`, { method: "POST", body: { actor } }),
  assign: (id, assignee, authority) => request(`/cases/${id}/assign`, { method: "POST", body: { assignee, authority } }),
  decide: (id, actor, decision, reason) =>
    request(`/cases/${id}/decision`, { method: "POST", body: { actor, decision, reason } }),
  override: (id, actor, route, reason) =>
    request(`/cases/${id}/override`, { method: "POST", body: { actor, route, reason } }),
};
