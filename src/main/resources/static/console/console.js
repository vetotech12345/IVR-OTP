/*
 * OTP Console - the operator's screen for the OTP service.
 *
 * Plain JavaScript, no build step, served by the service itself at /console.
 * Every value from the API is escaped with esc() before it reaches the page.
 * The admin token lives in sessionStorage: it is gone when the tab closes.
 */
(function () {
  "use strict";

  const TOKEN_KEY = "otp-console-token";
  const PAGE_SIZE = 25;
  const STATUS = {
    VERIFIED: ["ok", "Verified"],
    PENDING: ["info", "Waiting"],
    EXPIRED: ["", "Expired"],
    ATTEMPTS_EXHAUSTED: ["bad", "Too many wrong tries"],
  };
  const CHANNEL = { SMS: ["info", "SMS"], WHATSAPP: ["ok", "WhatsApp"], RANDOM: ["warn", "Random"] };

  const $ = (selector) => document.querySelector(selector);
  const view = $("#view");

  // ------------------------------------------------------------------ helpers

  function esc(value) {
    return String(value ?? "").replace(/[&<>"']/g, (c) =>
      ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[c]);
  }

  function pill(map, key) {
    const [tone, label] = map[key] || ["", key || "-"];
    return `<span class="pill ${tone}">${esc(label)}</span>`;
  }

  function when(iso) {
    if (!iso) return "-";
    const date = new Date(iso);
    return date.toLocaleString(undefined, { dateStyle: "medium", timeStyle: "short" });
  }

  function ago(iso) {
    if (!iso) return "Never";
    const seconds = Math.round((Date.now() - new Date(iso).getTime()) / 1000);
    if (seconds < 60) return "Just now";
    if (seconds < 3600) return `${Math.floor(seconds / 60)} min ago`;
    if (seconds < 86400) return `${Math.floor(seconds / 3600)} h ago`;
    return `${Math.floor(seconds / 86400)} d ago`;
  }

  function rate(verified, sent) {
    return sent ? `${Math.round((verified / sent) * 100)}%` : "-";
  }

  function token() {
    try { return sessionStorage.getItem(TOKEN_KEY); } catch { return null; }
  }

  function setToken(value) {
    try {
      if (value) sessionStorage.setItem(TOKEN_KEY, value); else sessionStorage.removeItem(TOKEN_KEY);
    } catch { /* storage blocked: the session lasts until reload */ }
  }

  async function api(path, options = {}) {
    const response = await fetch(path, {
      method: options.method || "GET",
      headers: { Authorization: `Bearer ${token()}`, "Content-Type": "application/json" },
      body: options.body ? JSON.stringify(options.body) : undefined,
    });
    if (response.status === 401) {
      signOut("Your token was not accepted. Sign in again.");
      throw new Error("Not signed in.");
    }
    const text = await response.text();
    const data = text ? JSON.parse(text) : null;
    if (!response.ok) {
      throw new Error(data?.error?.message || `Request failed (${response.status}).`);
    }
    return data;
  }

  function loading() {
    view.innerHTML = `<div class="card empty">Loading…</div>`;
  }

  function failed(error, retry) {
    view.innerHTML = `<div class="card empty"><p class="error">${esc(error.message)}</p>
      <button class="btn" id="retry" type="button">Try again</button></div>`;
    $("#retry").onclick = retry;
  }

  // ------------------------------------------------------------------- modal

  function openModal(html) {
    $("#modal-body").innerHTML = html;
    $("#modal").hidden = false;
  }

  function closeModal() {
    $("#modal").hidden = true;
    $("#modal-body").innerHTML = "";
  }

  /** A new key is shown exactly once. Make that impossible to miss. */
  function showKey(title, apiKey) {
    openModal(`
      <h2>${esc(title)}</h2>
      <div class="notice">Copy this key now. It is shown once and cannot be read again.</div>
      <div class="secret">
        <input id="new-key" readonly value="${esc(apiKey)}">
        <button class="btn" id="copy-key" type="button">Copy</button>
      </div>
      <p class="muted small">The application sends it in the <code>X-Api-Key</code> header.</p>
      <div class="actions"><button class="btn primary" id="close-modal" type="button">I have copied it</button></div>`);
    $("#copy-key").onclick = async () => {
      try {
        await navigator.clipboard.writeText(apiKey);
        $("#copy-key").textContent = "Copied";
      } catch {
        $("#new-key").select();
      }
    };
    $("#close-modal").onclick = closeModal;
  }

  // ------------------------------------------------------------------- login

  function showLogin(message) {
    $("#shell").hidden = true;
    $("#login").hidden = false;
    $("#login-error").hidden = !message;
    $("#login-error").textContent = message || "";
    $("#token").focus();
  }

  function signOut(message) {
    setToken(null);
    showLogin(message);
  }

  $("#login-form").onsubmit = async (event) => {
    event.preventDefault();
    setToken($("#token").value.trim());
    try {
      await api("/admin/overview");
      $("#token").value = "";
      $("#login").hidden = true;
      $("#shell").hidden = false;
      route();
    } catch (error) {
      if (token()) showLogin(error.message);
    }
  };

  $("#signout").onclick = () => signOut();
  $("#menu").onclick = () => $("#side").classList.toggle("open");
  $("#modal").onclick = (event) => { if (event.target.id === "modal") closeModal(); };

  // ---------------------------------------------------------------- overview

  async function overview() {
    loading();
    let data;
    try {
      data = await api("/admin/overview");
    } catch (error) {
      return failed(error, overview);
    }
    const sms = data.channels7d.SMS || 0;
    const wa = data.channels7d.WHATSAPP || 0;
    const total = sms + wa;
    view.innerHTML = `
      <div class="page-head"><div><h1>Overview</h1>
        <div class="muted">Which applications use the service, and how much.</div></div></div>
      <div class="tiles">
        <div class="card tile"><div class="label">Applications</div><div class="value">${data.apps}</div>
          <div class="sub">${data.activeApps} active</div></div>
        <div class="card tile"><div class="label">Active API keys</div><div class="value">${data.activeKeys}</div></div>
        <div class="card tile"><div class="label">Codes sent, 24 h</div><div class="value">${data.sends24h}</div>
          <div class="sub">${rate(data.verified24h, data.sends24h)} verified</div></div>
        <div class="card tile"><div class="label">Codes sent, 7 days</div><div class="value">${data.sends7d}</div>
          <div class="sub">${rate(data.verified7d, data.sends7d)} verified</div></div>
        <div class="card tile"><div class="label">Channels, 7 days</div>
          <div class="split" title="SMS ${sms}, WhatsApp ${wa}">
            <div class="sms" style="width:${total ? (sms / total) * 100 : 0}%"></div>
            <div class="wa" style="width:${total ? (wa / total) * 100 : 0}%"></div></div>
          <div class="sub">SMS ${sms} · WhatsApp ${wa}</div></div>
      </div>
      <div class="card flush">
        <div class="card-head"><h2>Applications using the service</h2>
          <a class="btn small" href="#/apps">Manage</a></div>
        ${data.perApp.length === 0 ? `<div class="empty">No applications yet.
            <a href="#/apps">Configure the first one.</a></div>` : `
        <div class="table-wrap"><table>
          <thead><tr><th>Application</th><th>Channel</th><th>Status</th><th>Keys</th>
            <th>Sent 24 h</th><th>Sent 7 d</th><th>Verified 7 d</th><th>Last code</th></tr></thead>
          <tbody>${data.perApp.map((app) => `
            <tr class="clickable" data-app="${esc(app.appId)}">
              <td><strong>${esc(app.name)}</strong></td>
              <td>${pill(CHANNEL, app.channel)}</td>
              <td>${app.active ? pill({ y: ["ok", "Active"] }, "y") : pill({ n: ["bad", "Off"] }, "n")}</td>
              <td>${app.activeKeys}</td>
              <td>${app.sends24h}</td><td>${app.sends7d}</td>
              <td>${rate(app.verified7d, app.sends7d)}</td>
              <td class="muted">${esc(ago(app.lastSendAt))}</td>
            </tr>`).join("")}
          </tbody></table></div>`}
      </div>`;
    view.querySelectorAll("tr[data-app]").forEach((row) => {
      row.onclick = () => { location.hash = `#/apps/${row.dataset.app}`; };
    });
  }

  // -------------------------------------------------------------------- apps

  const SETTINGS = [
    ["channel", "Channel", "select", ["SMS", "WHATSAPP", "RANDOM"]],
    ["otpLength", "Code length (4-8)", "number"],
    ["ttlSeconds", "Expires after, seconds (60-1800)", "number"],
    ["maxAttempts", "Wrong tries allowed (1-10)", "number"],
    ["maxSendsPerHour", "Codes per number per hour (1-100)", "number"],
    ["resendAfterSeconds", "Wait before resend, seconds (0-600)", "number"],
    ["senderId", "SMS sender ID (DLT header)", "text"],
    ["templateId", "Template ID (DLT / WhatsApp)", "text"],
    ["messageTemplate", "Message - must contain {code}; {minutes} optional", "textarea"],
  ];
  const CHANNEL_LABEL = { SMS: "SMS only", WHATSAPP: "WhatsApp only", RANDOM: "Random - SMS or WhatsApp per send" };

  function settingsFields(app) {
    return SETTINGS.map(([name, label, type, options]) => {
      const value = app ? app[name] ?? "" : "";
      let control;
      if (type === "select") {
        control = `<select id="f-${name}">${options.map((option) =>
          `<option value="${option}" ${value === option ? "selected" : ""}>${esc(CHANNEL_LABEL[option])}</option>`)
          .join("")}</select>`;
      } else if (type === "textarea") {
        control = `<textarea id="f-${name}" placeholder="Default message">${esc(value)}</textarea>`;
      } else {
        control = `<input id="f-${name}" type="${type}" value="${esc(value)}" ${app ? "" : 'placeholder="default"'}>`;
      }
      const wide = type === "textarea" || type === "select" ? "wide" : "";
      return `<div class="field ${wide}"><label for="f-${name}">${esc(label)}</label>${control}</div>`;
    }).join("");
  }

  /** Only fields that were filled in: an empty field means "use the default" or "leave as is". */
  function readSettings() {
    const settings = {};
    for (const [name, , type] of SETTINGS) {
      const raw = $(`#f-${name}`).value.trim();
      if (raw === "") continue;
      settings[name] = type === "number" ? Number(raw) : raw;
    }
    return settings;
  }

  async function apps() {
    loading();
    let list;
    try {
      list = await api("/admin/apps");
    } catch (error) {
      return failed(error, apps);
    }
    view.innerHTML = `
      <div class="page-head"><div><h1>Applications</h1>
        <div class="muted">Each application has its own settings, channel and API keys.</div></div>
        <button class="btn primary" id="new-app" type="button">Configure a new application</button></div>
      <div class="card flush">
        ${list.length === 0 ? `<div class="empty">No applications yet.</div>` : `
        <div class="table-wrap"><table>
          <thead><tr><th>Name</th><th>Channel</th><th>Status</th><th>Code</th><th>Expires</th><th>Created</th></tr></thead>
          <tbody>${list.map((app) => `
            <tr class="clickable" data-app="${esc(app.id)}">
              <td><strong>${esc(app.name)}</strong></td>
              <td>${pill(CHANNEL, app.channel)}</td>
              <td>${app.active ? pill({ y: ["ok", "Active"] }, "y") : pill({ n: ["bad", "Off"] }, "n")}</td>
              <td>${app.otpLength} digits</td>
              <td>${Math.round(app.ttlSeconds / 60)} min</td>
              <td class="muted">${esc(when(app.createdAt))}</td>
            </tr>`).join("")}
          </tbody></table></div>`}
      </div>`;
    view.querySelectorAll("tr[data-app]").forEach((row) => {
      row.onclick = () => { location.hash = `#/apps/${row.dataset.app}`; };
    });
    $("#new-app").onclick = newAppDialog;
  }

  function newAppDialog() {
    openModal(`
      <h2>Configure a new application</h2>
      <form id="app-form">
        <div class="field"><label for="f-name">Name</label><input id="f-name" required maxlength="100"
          placeholder="e.g. IVR console, Shop app"></div>
        <div class="form-grid">${settingsFields(null)}</div>
        <p id="form-error" class="error" hidden></p>
        <div class="actions">
          <button class="btn" type="button" id="cancel">Cancel</button>
          <button class="btn primary" type="submit">Create and get its key</button>
        </div>
      </form>`);
    $("#cancel").onclick = closeModal;
    $("#app-form").onsubmit = async (event) => {
      event.preventDefault();
      try {
        const created = await api("/admin/apps", {
          method: "POST", body: { name: $("#f-name").value.trim(), settings: readSettings() },
        });
        showKey(`${created.app.name} is ready`, created.firstKey.apiKey);
        location.hash = `#/apps/${created.app.id}`;
      } catch (error) {
        $("#form-error").hidden = false;
        $("#form-error").textContent = error.message;
      }
    };
  }

  async function appDetail(appId) {
    loading();
    let app;
    let keys;
    try {
      [app, keys] = await Promise.all([api(`/admin/apps/${appId}`), api(`/admin/apps/${appId}/keys`)]);
    } catch (error) {
      return failed(error, () => appDetail(appId));
    }
    view.innerHTML = `
      <div class="page-head"><div><a href="#/apps" class="muted small">← Applications</a>
        <h1>${esc(app.name)}</h1>
        <div>${pill(CHANNEL, app.channel)} ${app.active ? pill({ y: ["ok", "Active"] }, "y")
          : pill({ n: ["bad", "Off - every key is refused"] }, "n")}</div></div>
        <button class="btn ${app.active ? "danger" : "primary"}" id="toggle" type="button">
          ${app.active ? "Switch off" : "Switch on"}</button></div>
      <div class="two">
        <div class="card"><h2>Settings</h2>
          <form id="settings-form"><div class="form-grid">${settingsFields(app)}</div>
            <p id="settings-msg" class="small" hidden></p>
            <div class="actions"><button class="btn primary" type="submit">Save settings</button></div></form>
        </div>
        <div class="card flush">
          <div class="card-head"><h2>API keys</h2>
            <button class="btn small" id="new-key-btn" type="button">New key</button></div>
          ${keys.length === 0 ? `<div class="empty">No keys.</div>` : `
          <div class="table-wrap"><table>
            <thead><tr><th>Key</th><th>Label</th><th>Last used</th><th>Status</th><th></th></tr></thead>
            <tbody>${keys.map((key) => `
              <tr><td class="mono">otp_${esc(key.prefix)}_…</td><td>${esc(key.label || "-")}</td>
                <td class="muted">${esc(ago(key.lastUsedAt))}</td>
                <td>${key.revokedAt ? pill({ r: ["bad", "Revoked"] }, "r") : pill({ a: ["ok", "Active"] }, "a")}</td>
                <td>${key.revokedAt ? "" : `<button class="btn small danger" data-revoke="${esc(key.id)}"
                  type="button">Revoke</button>`}</td></tr>`).join("")}
            </tbody></table></div>`}
        </div>
      </div>
      <div class="card flush" style="margin-top:16px">
        <div class="card-head"><h2>Codes sent by ${esc(app.name)}</h2></div>
        <div id="app-log"></div>
      </div>`;

    $("#settings-form").onsubmit = async (event) => {
      event.preventDefault();
      const message = $("#settings-msg");
      try {
        await api(`/admin/apps/${appId}`, { method: "PATCH", body: readSettings() });
        message.hidden = false; message.className = "small muted"; message.textContent = "Saved.";
        setTimeout(() => appDetail(appId), 600);
      } catch (error) {
        message.hidden = false; message.className = "small error"; message.textContent = error.message;
      }
    };
    $("#toggle").onclick = async () => {
      const off = app.active;
      if (off && !confirm(`Switch off ${app.name}? Every request with its keys will be refused at once.`)) return;
      try {
        await api(`/admin/apps/${appId}`, { method: "PATCH", body: { active: !off } });
        appDetail(appId);
      } catch (error) { alert(error.message); }
    };
    $("#new-key-btn").onclick = async () => {
      const label = prompt("A label for the new key, such as 'production' (optional):") ?? null;
      if (label === null) return;
      try {
        const issued = await api(`/admin/apps/${appId}/keys`, { method: "POST", body: { label } });
        showKey("New API key", issued.apiKey);
        appDetail(appId);
      } catch (error) { alert(error.message); }
    };
    view.querySelectorAll("[data-revoke]").forEach((button) => {
      button.onclick = async () => {
        if (!confirm("Revoke this key? Requests using it are refused from the next one.")) return;
        try {
          await api(`/admin/apps/${appId}/keys/${button.dataset.revoke}`, { method: "DELETE" });
          appDetail(appId);
        } catch (error) { alert(error.message); }
      };
    });
    renderLog($("#app-log"), { appId, fixedApp: true });
  }

  // --------------------------------------------------------------------- log

  async function logPage() {
    let apps = [];
    try { apps = await api("/admin/apps"); } catch { /* the log still works without the filter list */ }
    view.innerHTML = `
      <div class="page-head"><div><h1>OTP log</h1>
        <div class="muted">Every code sent: to which number, by which application and key, on which channel,
        and what became of it. The code itself is never stored, so it is never shown.</div></div></div>
      <div class="card flush"><div id="log"></div></div>`;
    renderLog($("#log"), { apps });
  }

  function renderLog(container, { appId = "", fixedApp = false, apps = [] }) {
    const state = { appId, phone: "", status: "", page: 0 };

    container.innerHTML = `
      <div class="filters">
        ${fixedApp ? "" : `<div><label for="l-app">Application</label><select id="l-app">
          <option value="">All applications</option>
          ${apps.map((app) => `<option value="${esc(app.id)}">${esc(app.name)}</option>`).join("")}
        </select></div>`}
        <div><label for="l-phone">Phone number</label><input id="l-phone" placeholder="Any part of the number"></div>
        <div><label for="l-status">Status</label><select id="l-status">
          <option value="">Any</option>
          ${Object.entries(STATUS).map(([key, [, label]]) => `<option value="${key}">${esc(label)}</option>`).join("")}
        </select></div>
      </div>
      <div class="log-body"></div>`;
    const body = container.querySelector(".log-body");

    async function load() {
      body.innerHTML = `<div class="empty">Loading…</div>`;
      const query = new URLSearchParams({ page: state.page, size: PAGE_SIZE });
      if (state.appId) query.set("appId", state.appId);
      if (state.phone) query.set("phone", state.phone);
      if (state.status) query.set("status", state.status);
      let data;
      try {
        data = await api(`/admin/otp-log?${query}`);
      } catch (error) {
        body.innerHTML = `<div class="empty"><p class="error">${esc(error.message)}</p>
          <button class="btn" type="button">Try again</button></div>`;
        body.querySelector("button").onclick = load;
        return;
      }
      if (data.totalElements === 0) {
        body.innerHTML = `<div class="empty">No codes match.</div>`;
        return;
      }
      const from = data.page * data.size + 1;
      const to = Math.min(data.totalElements, from + data.rows.length - 1);
      body.innerHTML = `
        <div class="table-wrap"><table>
          <thead><tr><th>Sent</th>${fixedApp ? "" : "<th>Application</th>"}<th>Phone</th><th>Channel</th>
            <th>Purpose</th><th>Status</th><th>Tries</th><th>Key</th><th>IP</th></tr></thead>
          <tbody>${data.rows.map((row) => `
            <tr><td class="muted" title="${esc(row.requestId)}">${esc(when(row.createdAt))}</td>
              ${fixedApp ? "" : `<td>${esc(row.appName || "-")}</td>`}
              <td class="mono">${esc(row.phone)}</td>
              <td>${pill(CHANNEL, row.channel)}</td>
              <td>${esc(row.purpose)}</td>
              <td>${pill(STATUS, row.status)}</td>
              <td>${row.attempts}/${row.maxAttempts}</td>
              <td class="mono small" title="${esc(row.keyLabel || "")}">${row.keyPrefix
                ? `otp_${esc(row.keyPrefix)}_…` : "-"}</td>
              <td class="mono small">${esc(row.requestIp || "-")}</td></tr>`).join("")}
          </tbody></table></div>
        <div class="pager"><span class="muted">${from}–${to} of ${data.totalElements}</span>
          <span><button class="btn small" id="prev" type="button" ${data.page <= 0 ? "disabled" : ""}>← Previous</button>
          <span class="muted small">${data.page + 1}/${data.totalPages}</span>
          <button class="btn small" id="next" type="button" ${data.page >= data.totalPages - 1 ? "disabled" : ""}>
            Next →</button></span></div>`;
      body.querySelector("#prev").onclick = () => { state.page -= 1; load(); };
      body.querySelector("#next").onclick = () => { state.page += 1; load(); };
    }

    if (!fixedApp) {
      container.querySelector("#l-app").onchange = (event) => { state.appId = event.target.value; state.page = 0; load(); };
    }
    let typing;
    container.querySelector("#l-phone").oninput = (event) => {
      clearTimeout(typing);
      typing = setTimeout(() => { state.phone = event.target.value.trim(); state.page = 0; load(); }, 350);
    };
    container.querySelector("#l-status").onchange = (event) => { state.status = event.target.value; state.page = 0; load(); };
    load();
  }

  // ------------------------------------------------------------------ router

  function route() {
    if (!token()) return showLogin();
    $("#side").classList.remove("open");
    const [, section, id] = (location.hash || "#/overview").split("/");
    document.querySelectorAll("[data-nav]").forEach((link) =>
      link.classList.toggle("active", link.dataset.nav === (section || "overview")));
    if (section === "apps" && id) return appDetail(id);
    if (section === "apps") return apps();
    if (section === "log") return logPage();
    return overview();
  }

  window.addEventListener("hashchange", route);
  if (token()) {
    $("#shell").hidden = false;
    route();
  } else {
    showLogin();
  }
})();
