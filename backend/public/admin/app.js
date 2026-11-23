const API = "/api/v1";
let token = sessionStorage.getItem("pix_admin_token");
let operator = JSON.parse(sessionStorage.getItem("pix_admin_user") || "null");

const money = (cents) => new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" }).format((cents || 0) / 100);
const dateTime = (value) => value ? new Intl.DateTimeFormat("pt-BR", { dateStyle: "short", timeStyle: "short" }).format(new Date(value)) : "-";
const escapeHtml = (value) => String(value ?? "").replace(/[&<>"']/g, (character) => ({
  "&": "&amp;",
  "<": "&lt;",
  ">": "&gt;",
  '"': "&quot;",
  "'": "&#039;"
})[character]);

async function api(path, options = {}) {
  let response;
  try {
    response = await fetch(API + path, {
      ...options,
      headers: {
        "Authorization": "Bearer " + token,
        "Accept": "application/json",
        ...(options.body ? { "Content-Type": "application/json" } : {})
      }
    });
  } catch (_) {
    throw new Error("Não foi possível acessar a API. Verifique se o backend está em execução.");
  }
  let body;
  try {
    body = await response.json();
  } catch (_) {
    throw new Error("O servidor respondeu em um formato inválido.");
  }
  if (response.status === 401) {
    sessionStorage.clear();
    token = null;
    location.reload();
  }
  if (!response.ok) throw new Error(body.error?.message || body.detail || body.error || "Erro ao carregar dados");
  return body.data;
}

document.getElementById("login-form").addEventListener("submit", async (event) => {
  event.preventDefault();
  const error = document.getElementById("login-error");
  error.textContent = "";
  try {
    const response = await fetch(API + "/auth/token", {
      method: "POST",
      headers: { "Content-Type": "application/json", "Accept": "application/json" },
      body: JSON.stringify({
        email: document.getElementById("email").value,
        password: document.getElementById("password").value,
        scopes: [ "profile:read", "users:admin", "accounts:admin", "reports:read", "transfers:read" ]
      })
    });
    const body = await response.json();
    if (!response.ok) throw new Error(body.error?.message || body.detail || "Não foi possível entrar");
    if (!body.data.scopes.includes("users:admin") || !body.data.scopes.includes("accounts:admin")) {
      throw new Error("Esta conta não possui acesso administrativo");
    }
    token = body.data.access_token;
    operator = body.data.user;
    sessionStorage.setItem("pix_admin_token", token);
    sessionStorage.setItem("pix_admin_user", JSON.stringify(operator));
    showDashboard();
  } catch (err) {
    error.textContent = err.message;
  }
});

function showDashboard() {
  document.getElementById("login-screen").classList.add("hidden");
  document.getElementById("dashboard").classList.remove("hidden");
  document.getElementById("operator-name").textContent = operator?.name || "Operador";
  document.getElementById("operator-avatar").textContent = (operator?.name || "O").charAt(0).toUpperCase();
  loadOverview();
}

document.getElementById("logout").addEventListener("click", async () => {
  try { await api("/auth/token", { method: "DELETE" }); } catch (_) {}
  sessionStorage.clear();
  location.reload();
});

function switchView(viewName) {
  document.querySelectorAll(".nav-item").forEach((item) => {
    item.classList.toggle("active", item.dataset.view === viewName);
  });
  document.querySelectorAll(".view").forEach((view) => view.classList.add("hidden"));
  document.getElementById("view-" + viewName).classList.remove("hidden");
  const activeBtn = document.querySelector(`.nav-item[data-view="${viewName}"]`);
  document.getElementById("page-title").textContent = activeBtn ? activeBtn.childNodes[1]?.textContent?.trim() || activeBtn.textContent.trim() : "Painel";
  refreshCurrentView(viewName);
}

function refreshCurrentView(viewName = document.querySelector(".nav-item.active")?.dataset.view || "overview") {
  if (viewName === "overview") loadOverview();
  if (viewName === "refunds") loadRefunds(document.getElementById("refund-status-filter").value);
  if (viewName === "reports") loadReports(document.getElementById("report-status-filter").value);
  if (viewName === "users") loadUsers(document.getElementById("user-search").value);
  if (viewName === "keys") loadPixKeys();
  if (viewName === "transactions") loadTransactions(document.getElementById("status-filter").value);
  if (viewName === "audit") loadAuditLogs();
}

document.querySelectorAll(".nav-item").forEach((button) => {
  button.addEventListener("click", () => switchView(button.dataset.view));
});

document.getElementById("refresh-btn").addEventListener("click", () => refreshCurrentView());

function markUpdated() {
  const el = document.getElementById("last-updated");
  if (el) el.textContent = "Atualizado às " + new Intl.DateTimeFormat("pt-BR", { timeStyle: "medium" }).format(new Date());
}

function updateBadges(queues = {}) {
  const rBadge = document.getElementById("badge-refunds");
  const fBadge = document.getElementById("badge-reports");
  const rCount = Number(queues.pending_refund_requests || 0);
  const fCount = Number(queues.pending_fraud_reports || 0);
  rBadge.textContent = rCount;
  rBadge.classList.toggle("hidden", rCount === 0);
  fBadge.textContent = fCount;
  fBadge.classList.toggle("hidden", fCount === 0);
}

async function loadOverview() {
  try {
    const data = await api("/admin/overview");
    updateBadges(data.queues);
    const pixKeys = data.pix_keys || { active: 0, suspended: 0, by_type: {} };
    const scheduled = data.scheduled_transfers || { pending: 0, pending_amount_cents: 0 };
    const insights = data.insights || {};
    const trendPct = Number(insights.today_vs_avg_pct || 0);
    const trendLabel = trendPct > 0 ? `+${trendPct}% vs média 7d` : `${trendPct}% vs média 7d`;
    const insightCards = [
      [ "Tendência hoje", `${trendLabel} (${money(insights.daily_avg_cents || 0)}/dia)`, trendPct >= 0 ? "up" : "warn" ],
      [ "Maior transferência", money(insights.max_transaction_cents || 0), "info" ],
      [ "Estornos & Risco", `${insights.refund_rate ?? 0}% (${money(insights.refunded_cents || 0)})`, (insights.refund_rate || 0) > 10 ? "warn" : "info" ],
      [ "Saldo bloqueado / DICT", `${money(insights.blocked_balance_cents || 0)} · ${insights.avg_keys_per_account ?? 0} chaves/conta`, (insights.blocked_balance_cents || 0) > 0 ? "warn" : "up" ]
    ];
    document.getElementById("insights-strip").innerHTML = insightCards.map(([ title, value, tone ]) =>
      `<div class="insight-pill ${escapeHtml(tone)}"><span>${escapeHtml(title)}</span><strong>${escapeHtml(value)}</strong></div>`
    ).join("");

    const cards = [
      [ "Usuários cadastrados", data.total_users, `${data.active_users} com conta ativa`, "active" ],
      [ "Usuários online", data.online_users, "Sessões válidas", "info" ],
      [ "Contas & Bloqueios", data.active_accounts, `${data.blocked_accounts} bloqueadas por fraude`, data.blocked_accounts > 0 ? "warn" : "active" ],
      [ "Saldo sob custódia", money(data.total_balance_cents), `${data.total_accounts} contas totais`, "info" ],
      [ "Volume movimentado", money(data.total_moved_cents), `${money(data.moved_today_cents)} hoje`, "active" ],
      [ "Ticket médio", money(data.average_ticket_cents), `${data.success_rate ?? 100}% de sucesso`, "active" ],
      [ "Chaves PIX ativas", pixKeys.active, `${pixKeys.suspended} suspensas`, pixKeys.suspended > 0 ? "warn" : "active" ],
      [ "Agendamentos", scheduled.pending, `${money(scheduled.pending_amount_cents)} pendentes`, "info" ]
    ];
    document.getElementById("stats-grid").innerHTML = cards.map(([ label, value, note, tone ]) =>
      `<article class="stat-card"><span class="stat-label">${escapeHtml(label)}</span><strong class="stat-value">${escapeHtml(value)}</strong><span class="stat-note ${escapeHtml(tone)}">${escapeHtml(note)}</span></article>`
    ).join("");

    const max = Math.max(...data.daily.map((item) => item.amount_cents), 1);
    document.getElementById("volume-chart").innerHTML = data.daily.map((item) => {
      const height = Math.max(5, Math.round(item.amount_cents / max * 140));
      const day = new Date(item.date + "T12:00:00").toLocaleDateString("pt-BR", { weekday: "short" }).replace(".", "");
      return `<div class="bar-column" title="${escapeHtml(money(item.amount_cents))} (${escapeHtml(item.count)} ops)"><span class="bar-count">${escapeHtml(item.count)}</span><div class="bar" style="height:${height}px"></div><span>${escapeHtml(day)}</span></div>`;
    }).join("");

    const totalStatus = Object.values(data.status_counts).reduce((sum, count) => sum + count, 0) || 1;
    const labels = { completed: "Concluídas", processing: "Processando", failed: "Falhas", cancelled: "Canceladas", refunded: "Estornadas" };
    document.getElementById("status-list").innerHTML = Object.entries(data.status_counts).map(([ status, count ]) =>
      `<div class="status-row"><span>${escapeHtml(labels[status] || status)}</span><div class="status-track"><div class="status-fill ${escapeHtml(status)}" style="width:${count / totalStatus * 100}%"></div></div><strong>${escapeHtml(count)}</strong></div>`
    ).join("") || `<p class="last-updated">Sem transações.</p>`;

    const keyEntries = Object.entries(pixKeys.by_type || {});
    const totalKeys = keyEntries.reduce((sum, [ , count ]) => sum + count, 0) || 1;
    const keyLabels = { cpf: "CPF", cnpj: "CNPJ", email: "E-mail", phone: "Telefone", random: "Aleatória" };
    document.getElementById("keys-list").innerHTML = keyEntries.map(([ type, count ]) =>
      `<div class="status-row"><span>${escapeHtml(keyLabels[type] || type)}</span><div class="status-track"><div class="status-fill keys" style="width:${count / totalKeys * 100}%"></div></div><strong>${escapeHtml(count)}</strong></div>`
    ).join("") || `<p class="last-updated">Nenhuma chave ativa.</p>`;

    const failures = data.top_failure_reasons || [];
    const totalFailures = failures.reduce((sum, item) => sum + item.count, 0) || 1;
    document.getElementById("failure-reasons-list").innerHTML = failures.map((item) =>
      `<div class="failure-row" title="${escapeHtml(item.reason)}"><span>${escapeHtml(item.reason)}</span><div class="status-track"><div class="status-fill failed" style="width:${item.count / totalFailures * 100}%"></div></div><strong>${escapeHtml(item.count)}</strong></div>`
    ).join("") || `<p class="last-updated">Nenhuma falha registrada.</p>`;

    const topAccounts = data.top_accounts || [];
    document.getElementById("top-accounts-rows").innerHTML = topAccounts.map((acc) =>
      `<tr><td><strong>${escapeHtml(acc.owner_name)}</strong></td><td><small>${escapeHtml(acc.account_number)}</small></td><td>${escapeHtml(acc.tx_count)}</td><td><strong>${escapeHtml(money(acc.sent_cents))}</strong></td><td>${escapeHtml(money(acc.balance_cents))}</td></tr>`
    ).join("") || `<tr><td colspan="5">Nenhuma movimentação concluída.</td></tr>`;

    document.getElementById("recent-rows").innerHTML = data.recent_transactions.map((item) =>
      `<tr><td><strong>${escapeHtml(item.source.owner_name)}</strong><br><small>${escapeHtml(item.source.account_number)}</small></td><td>${escapeHtml(item.destination.owner_name)}<br><small>${escapeHtml(item.destination.account_number)}</small></td><td><strong>${escapeHtml(money(item.amount_cents))}</strong></td><td><span class="badge ${escapeHtml(item.status)}">${escapeHtml(item.status)}</span></td><td>${escapeHtml(dateTime(item.created_at))}</td></tr>`
    ).join("") || `<tr><td colspan="5">Nenhuma movimentação encontrada.</td></tr>`;
    markUpdated();
  } catch (error) {
    alert(error.message);
  }
}

document.getElementById("refund-status-filter").addEventListener("change", (event) => loadRefunds(event.target.value));
async function loadRefunds(status = "pending") {
  try {
    const query = status ? `&status=${encodeURIComponent(status)}` : "";
    const data = await api(`/admin/refund_requests?per_page=50${query}`);
    document.getElementById("refunds-rows").innerHTML = data.items.map((req) => {
      const tx = req.transaction;
      const actions = req.status === "pending"
        ? `<div class="btn-group">
             <button class="btn-sm success" data-action="approve-refund" data-id="${escapeHtml(req.id)}">Aprovar & Estornar</button>
             <button class="btn-sm danger" data-action="reject-refund" data-id="${escapeHtml(req.id)}">Rejeitar</button>
           </div>`
        : `<small>${escapeHtml(req.decision_note || (req.reviewed_by ? `Por ${req.reviewed_by.name}` : "-"))}</small>`;
      return `<tr>
        <td><strong>#${escapeHtml(req.id)}</strong><br><small>${escapeHtml(dateTime(req.created_at))}</small></td>
        <td><strong>${escapeHtml(req.requested_by.name)}</strong><br><small>${escapeHtml(req.requested_by.email)}</small></td>
        <td>${escapeHtml(tx.source_owner)} (${escapeHtml(tx.source_account)})<br><small>→ ${escapeHtml(tx.destination_owner)} (${escapeHtml(tx.destination_account)})</small></td>
        <td><strong>${escapeHtml(money(tx.amount_cents))}</strong><br><small>Saldo dest: ${escapeHtml(money(tx.destination_balance_cents))}</small></td>
        <td class="cell-wrap"><strong>${escapeHtml(req.category)}</strong>: ${escapeHtml(req.reason)}</td>
        <td><span class="badge ${escapeHtml(req.status)}">${escapeHtml(req.status)}</span></td>
        <td>${actions}</td>
      </tr>`;
    }).join("") || `<tr><td colspan="7">Nenhuma solicitação de estorno encontrada.</td></tr>`;
    markUpdated();
  } catch (error) {
    alert(error.message);
  }
}

document.getElementById("report-status-filter").addEventListener("change", (event) => loadReports(event.target.value));
async function loadReports(status = "pending") {
  try {
    const query = status ? `&status=${encodeURIComponent(status)}` : "";
    const data = await api(`/admin/fraud_reports?per_page=50${query}`);
    document.getElementById("reports-rows").innerHTML = data.items.map((rep) => {
      const acc = rep.reported_account;
      const actions = [ "pending", "under_review" ].includes(rep.status)
        ? `<div class="btn-group">
             ${acc.fraud_blocked ? "" : `<button class="btn-sm danger" data-action="resolve-report-block" data-id="${escapeHtml(rep.id)}">Bloquear Conta</button>`}
             <button class="btn-sm success" data-action="resolve-report" data-id="${escapeHtml(rep.id)}">Resolver</button>
             <button class="btn-sm" data-action="dismiss-report" data-id="${escapeHtml(rep.id)}">Arquivar</button>
           </div>`
        : `<small>${escapeHtml(rep.resolution_note || "-")}</small>`;
      return `<tr>
        <td><strong>#${escapeHtml(rep.id)}</strong><br><small>${escapeHtml(dateTime(rep.created_at))}</small></td>
        <td><strong>${escapeHtml(rep.reporter.name)}</strong><br><small>${escapeHtml(rep.reporter.email)}</small></td>
        <td><strong>${escapeHtml(acc.owner_name)}</strong><br><small>Conta ${escapeHtml(acc.account_number)} · Saldo ${escapeHtml(money(acc.balance_cents))}</small></td>
        <td class="cell-wrap"><strong>${escapeHtml(rep.category)}</strong>: ${escapeHtml(rep.description)}</td>
        <td><strong>${escapeHtml(acc.total_reports_count)} reports</strong><br>${acc.fraud_blocked ? '<span class="badge failed">Bloqueada</span>' : '<span class="badge active">Ativa</span>'}</td>
        <td><span class="badge ${escapeHtml(rep.status)}">${escapeHtml(rep.status)}</span></td>
        <td>${actions}</td>
      </tr>`;
    }).join("") || `<tr><td colspan="7">Nenhuma denúncia encontrada.</td></tr>`;
    markUpdated();
  } catch (error) {
    alert(error.message);
  }
}

let userSearchTimer;
document.getElementById("user-search").addEventListener("input", (event) => {
  clearTimeout(userSearchTimer);
  userSearchTimer = setTimeout(() => loadUsers(event.target.value), 300);
});

async function loadUsers(search = "") {
  try {
    const data = await api("/admin/users?per_page=50" + (search ? "&search=" + encodeURIComponent(search) : ""));
    document.getElementById("users-rows").innerHTML = data.items.map((user) =>
      `<tr>
        <td><div class="person"><div class="mini-avatar">${escapeHtml(user.name.charAt(0).toUpperCase())}</div><div><strong>${escapeHtml(user.name)}</strong><span>${escapeHtml(user.email)}</span></div></div></td>
        <td>${user.role === "operator" ? "Operador" : "Cliente"}</td>
        <td><span class="badge ${escapeHtml(user.status)}">${user.status === "active" ? "Ativo" : "Inativo"}</span>${user.online ? ' <span class="badge active">online</span>' : ""}</td>
        <td>${escapeHtml(user.accounts_count)}</td>
        <td><strong>${escapeHtml(money(user.balance_cents))}</strong></td>
        <td>${escapeHtml(dateTime(user.last_active_at))}</td>
        <td><button class="btn-sm" data-action="manage-user" data-id="${escapeHtml(user.id)}">Gerenciar</button></td>
      </tr>`
    ).join("") || `<tr><td colspan="7">Nenhum usuário encontrado.</td></tr>`;
    markUpdated();
  } catch (error) {
    alert(error.message);
  }
}

let keySearchTimer;
document.getElementById("key-search").addEventListener("input", () => {
  clearTimeout(keySearchTimer);
  keySearchTimer = setTimeout(() => loadPixKeys(), 300);
});
document.getElementById("key-status-filter").addEventListener("change", () => loadPixKeys());

async function loadPixKeys() {
  try {
    const search = document.getElementById("key-search").value.trim();
    const status = document.getElementById("key-status-filter").value;
    const params = new URLSearchParams({ per_page: "50" });
    if (search) params.set("search", search);
    if (status) params.set("status", status);

    const data = await api(`/admin/pix_keys?${params.toString()}`);
    document.getElementById("keys-rows").innerHTML = data.items.map((k) => {
      const actions = [];
      if (k.status === "suspended") actions.push(`<button class="btn-sm success" data-action="unblock-key" data-id="${escapeHtml(k.id)}">Desbloquear</button>`);
      if (k.status === "active") actions.push(`<button class="btn-sm warn" data-action="suspend-key" data-id="${escapeHtml(k.id)}">Suspender</button>`);
      if (k.status !== "cancelled") actions.push(`<button class="btn-sm danger" data-action="cancel-key" data-id="${escapeHtml(k.id)}">Cancelar</button>`);

      return `<tr>
        <td><strong>${escapeHtml(k.key_type.toUpperCase())}</strong></td>
        <td>${escapeHtml(k.key_value)}</td>
        <td><strong>${escapeHtml(k.owner_name)}</strong><br><small>Conta ${escapeHtml(k.account_number)}</small></td>
        <td><span class="badge ${escapeHtml(k.status)}">${escapeHtml(k.status)}</span></td>
        <td>${escapeHtml(dateTime(k.created_at))}</td>
        <td><div class="btn-group">${actions.join("") || "-"}</div></td>
      </tr>`;
    }).join("") || `<tr><td colspan="6">Nenhuma chave PIX encontrada.</td></tr>`;
    markUpdated();
  } catch (error) {
    alert(error.message);
  }
}

document.getElementById("status-filter").addEventListener("change", (event) => loadTransactions(event.target.value));
async function loadTransactions(status = "") {
  try {
    const data = await api("/admin/transactions?per_page=50" + (status ? "&status=" + encodeURIComponent(status) : ""));
    document.getElementById("transactions-rows").innerHTML = data.items.map((item) =>
      `<tr><td><small>${escapeHtml(item.end_to_end_id)}</small></td><td>${escapeHtml(item.source.owner_name)}<br><small>${escapeHtml(item.source.account_number)}</small></td><td>${escapeHtml(item.destination.owner_name)}<br><small>${escapeHtml(item.destination.account_number)}</small></td><td><strong>${escapeHtml(money(item.amount_cents))}</strong></td><td><span class="badge ${escapeHtml(item.status)}">${escapeHtml(item.status)}</span></td><td>${escapeHtml(dateTime(item.created_at))}</td></tr>`
    ).join("") || `<tr><td colspan="6">Nenhuma movimentação encontrada.</td></tr>`;
    markUpdated();
  } catch (error) {
    alert(error.message);
  }
}

async function loadAuditLogs() {
  try {
    const data = await api("/admin/audit_logs?per_page=50");
    document.getElementById("audit-rows").innerHTML = data.items.map((log) =>
      `<tr>
        <td>${escapeHtml(dateTime(log.created_at))}</td>
        <td><strong>${escapeHtml(log.operator_name)}</strong><br><small>${escapeHtml(log.operator_email)}</small></td>
        <td><span class="badge processing">${escapeHtml(log.action)}</span></td>
        <td>${escapeHtml(log.target_type)} #${escapeHtml(log.target_id)}</td>
        <td class="cell-wrap"><small>${escapeHtml(JSON.stringify(log.details || {}))}</small></td>
        <td><small>${escapeHtml(log.ip_address || "-")}</small></td>
      </tr>`
    ).join("") || `<tr><td colspan="6">Nenhum registro de auditoria.</td></tr>`;
    markUpdated();
  } catch (error) {
    alert(error.message);
  }
}

async function openUserModal(userId) {
  try {
    const u = await api(`/admin/users/${userId}`);
    const modal = document.getElementById("admin-modal");
    document.getElementById("admin-modal-title").textContent = `Gerenciar: ${u.name}`;
    document.getElementById("admin-modal-body").innerHTML = `
      <form id="edit-user-form" class="modal-section" data-user-id="${escapeHtml(u.id)}">
        <h3>Dados do Usuário (${escapeHtml(u.fraud_reports_count)} denúncias · ${escapeHtml(u.active_sessions_count)} sessões ativas)</h3>
        <div class="form-row">
          <label>Nome<input id="edit-user-name" type="text" value="${escapeHtml(u.name)}" required></label>
          <label>Telefone<input id="edit-user-phone" type="text" value="${escapeHtml(u.phone || "")}"></label>
        </div>
        <div class="form-row">
          <label>Papel (Role)
            <select id="edit-user-role">
              <option value="customer" ${u.role === "customer" ? "selected" : ""}>Cliente (customer)</option>
              <option value="operator" ${u.role === "operator" ? "selected" : ""}>Operador (operator)</option>
            </select>
          </label>
          <div style="display:flex;align-items:flex-end;gap:8px;margin-bottom:16px">
            <button type="submit" class="btn-sm success" style="height:44px;flex:1">Salvar usuário</button>
            <button type="button" class="btn-sm warn" id="revoke-sessions-btn" data-id="${escapeHtml(u.id)}" style="height:44px">Revogar sessões</button>
          </div>
        </div>
      </form>
      <div class="modal-section">
        <h3>Contas Vinculadas (${u.accounts.length})</h3>
        ${u.accounts.map((acc) => `
          <div class="account-box">
            <div class="account-head">
              <div>
                <strong>Conta ${escapeHtml(acc.account_number)}</strong> (Ag ${escapeHtml(acc.agency_number)}) ·
                <strong>${escapeHtml(money(acc.balance_cents))}</strong> ·
                <small>Limite diário: R$ ${escapeHtml(acc.daily_limit)}</small>
              </div>
              <div class="btn-group">
                <span class="badge ${escapeHtml(acc.status)}">${escapeHtml(acc.status)}</span>
                ${acc.fraud_blocked ? '<span class="badge failed">Fraude</span>' : ""}
              </div>
            </div>
            ${acc.fraud_blocked ? `<small style="color:var(--red)">Motivo do bloqueio: ${escapeHtml(acc.fraud_block_reason)}</small>` : ""}
            <div class="btn-group">
              ${acc.fraud_blocked
                ? `<button class="btn-sm success" data-action="unblock-account" data-id="${escapeHtml(acc.id)}" data-user-id="${escapeHtml(u.id)}">Desbloquear Fraude</button>`
                : `<button class="btn-sm danger" data-action="block-account" data-id="${escapeHtml(acc.id)}" data-user-id="${escapeHtml(u.id)}">Bloquear Fraude</button>`}
              <button class="btn-sm" data-action="limit-account" data-id="${escapeHtml(acc.id)}" data-user-id="${escapeHtml(u.id)}" data-limit="${escapeHtml(acc.daily_limit)}">Alterar Limite</button>
              ${acc.status === "active"
                ? `<button class="btn-sm warn" data-action="close-account" data-id="${escapeHtml(acc.id)}" data-user-id="${escapeHtml(u.id)}">Desativar Conta</button>`
                : `<button class="btn-sm success" data-action="reopen-account" data-id="${escapeHtml(acc.id)}" data-user-id="${escapeHtml(u.id)}">Reativar Conta</button>`}
            </div>
          </div>
        `).join("") || "<p>Nenhuma conta cadastrada.</p>"}
      </div>
    `;
    modal.showModal();
  } catch (error) {
    alert(error.message);
  }
}

document.addEventListener("submit", async (event) => {
  if (event.target.id !== "edit-user-form") return;
  event.preventDefault();
  const userId = event.target.dataset.userId;
  try {
    await api(`/admin/users/${userId}`, {
      method: "PATCH",
      body: JSON.stringify({
        name: document.getElementById("edit-user-name").value,
        phone: document.getElementById("edit-user-phone").value,
        role: document.getElementById("edit-user-role").value
      })
    });
    alert("Usuário atualizado com sucesso!");
    openUserModal(userId);
    loadUsers(document.getElementById("user-search").value);
  } catch (error) {
    alert(error.message);
  }
});

document.addEventListener("click", async (event) => {
  const btn = event.target.closest("button");
  if (!btn) return;

  if (btn.id === "revoke-sessions-btn") {
    const userId = btn.dataset.id;
    if (!confirm("Revogar todas as sessões ativas deste usuário?")) return;
    try {
      const res = await api(`/admin/users/${userId}/revoke_sessions`, { method: "POST" });
      alert(`${res.revoked_count} sessão(ões) revogada(s).`);
      openUserModal(userId);
      loadUsers(document.getElementById("user-search").value);
    } catch (error) { alert(error.message); }
    return;
  }

  const action = btn.dataset.action;
  const id = btn.dataset.id;
  if (!action || !id) return;

  try {
    if (action === "approve-refund") {
      if (!confirm("Aprovar estorno e transferir o valor de volta ao remetente?")) return;
      const note = prompt("Observação da aprovação (opcional):") || "";
      await api(`/admin/refund_requests/${id}/approve`, { method: "POST", body: JSON.stringify({ decision_note: note }) });
      loadRefunds(document.getElementById("refund-status-filter").value);
      loadOverview();
    } else if (action === "reject-refund") {
      const note = prompt("Informe o motivo da rejeição do estorno:");
      if (!note) return;
      await api(`/admin/refund_requests/${id}/reject`, { method: "POST", body: JSON.stringify({ decision_note: note }) });
      loadRefunds(document.getElementById("refund-status-filter").value);
      loadOverview();
    } else if (action === "resolve-report-block") {
      const note = prompt("Motivo do bloqueio antifraude:");
      if (!note) return;
      await api(`/admin/fraud_reports/${id}/resolve`, { method: "PATCH", body: JSON.stringify({ status: "resolved", resolution_note: note, block_account: true }) });
      loadReports(document.getElementById("report-status-filter").value);
      loadOverview();
    } else if (action === "resolve-report") {
      const note = prompt("Parecer da resolução:") || "Analisado e resolvido";
      await api(`/admin/fraud_reports/${id}/resolve`, { method: "PATCH", body: JSON.stringify({ status: "resolved", resolution_note: note }) });
      loadReports(document.getElementById("report-status-filter").value);
      loadOverview();
    } else if (action === "dismiss-report") {
      const note = prompt("Motivo do arquivamento:") || "Sem indícios de fraude";
      await api(`/admin/fraud_reports/${id}/resolve`, { method: "PATCH", body: JSON.stringify({ status: "dismissed", resolution_note: note }) });
      loadReports(document.getElementById("report-status-filter").value);
      loadOverview();
    } else if (action === "manage-user") {
      openUserModal(id);
    } else if (action === "block-account") {
      const reason = prompt("Motivo do bloqueio antifraude:");
      if (!reason) return;
      await api(`/admin/accounts/${id}/block_fraud`, { method: "PATCH", body: JSON.stringify({ reason }) });
      openUserModal(btn.dataset.userId);
    } else if (action === "unblock-account") {
      if (!confirm("Remover bloqueio antifraude desta conta?")) return;
      await api(`/admin/accounts/${id}/unblock_fraud`, { method: "PATCH" });
      openUserModal(btn.dataset.userId);
    } else if (action === "limit-account") {
      const daily_limit = prompt("Novo limite diário (R$):", btn.dataset.limit || "5000.00");
      if (!daily_limit) return;
      await api(`/admin/accounts/${id}/limit`, { method: "PATCH", body: JSON.stringify({ daily_limit }) });
      openUserModal(btn.dataset.userId);
    } else if (action === "close-account") {
      if (!confirm("Desativar/encerrar esta conta? (Apenas contas com saldo R$ 0,00 podem ser encerradas)")) return;
      await api(`/admin/accounts/${id}/close`, { method: "PATCH" });
      openUserModal(btn.dataset.userId);
    } else if (action === "reopen-account") {
      if (!confirm("Reativar esta conta?")) return;
      await api(`/admin/accounts/${id}/reopen`, { method: "PATCH" });
      openUserModal(btn.dataset.userId);
    } else if (action === "unblock-key") {
      await api(`/admin/pix_keys/${id}/unblock`, { method: "PATCH" });
      loadPixKeys();
    } else if (action === "suspend-key") {
      await api(`/admin/pix_keys/${id}/suspend`, { method: "PATCH" });
      loadPixKeys();
    } else if (action === "cancel-key") {
      if (!confirm("Cancelar esta chave PIX permanentemente?")) return;
      await api(`/admin/pix_keys/${id}`, { method: "DELETE" });
      loadPixKeys();
    }
  } catch (error) {
    alert(error.message);
  }
});

if (token) showDashboard();
