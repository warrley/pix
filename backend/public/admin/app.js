const API = "/api/v1";
let token = sessionStorage.getItem("pix_admin_token");
let operator = JSON.parse(sessionStorage.getItem("pix_admin_user") || "null");

const money = (cents) => new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" }).format((cents || 0) / 100);
const dateTime = (value) => value ? new Intl.DateTimeFormat("pt-BR", { dateStyle:"short", timeStyle:"short" }).format(new Date(value)) : "-";
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
      headers: { "Authorization": "Bearer " + token, "Accept": "application/json", ...(options.body ? {"Content-Type":"application/json"} : {}) }
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
  if (!response.ok) throw new Error(body.error?.message || body.detail || "Erro ao carregar dados");
  return body.data;
}

document.getElementById("login-form").addEventListener("submit", async (event) => {
  event.preventDefault();
  const error = document.getElementById("login-error");
  error.textContent = "";
  try {
    const response = await fetch(API + "/auth/token", {
      method:"POST",
      headers:{ "Content-Type":"application/json", "Accept":"application/json" },
      body: JSON.stringify({ email:document.getElementById("email").value, password:document.getElementById("password").value,
        scopes:["profile:read","users:admin","accounts:admin","reports:read","transfers:read"] })
    });
    const body = await response.json();
    if (!response.ok) throw new Error(body.error?.message || "Não foi possível entrar");
    if (!body.data.scopes.includes("users:admin") || !body.data.scopes.includes("accounts:admin")) throw new Error("Esta conta não possui acesso administrativo");
    token = body.data.access_token;
    operator = body.data.user;
    sessionStorage.setItem("pix_admin_token", token);
    sessionStorage.setItem("pix_admin_user", JSON.stringify(operator));
    showDashboard();
  } catch (err) { error.textContent = err.message; }
});

function showDashboard() {
  document.getElementById("login-screen").classList.add("hidden");
  document.getElementById("dashboard").classList.remove("hidden");
  document.getElementById("operator-name").textContent = operator?.name || "Operador";
  document.getElementById("operator-avatar").textContent = (operator?.name || "O").charAt(0).toUpper();
  loadOverview();
}

document.getElementById("logout").addEventListener("click", async () => {
  try { await api("/auth/token", { method:"DELETE" }); } catch (_) {}
  sessionStorage.clear(); location.reload();
});

document.querySelectorAll(".nav-item").forEach(button => button.addEventListener("click", () => {
  document.querySelectorAll(".nav-item").forEach(item => item.classList.remove("active"));
  button.classList.add("active");
  document.querySelectorAll(".view").forEach(view => view.classList.add("hidden"));
  document.getElementById("view-" + button.dataset.view).classList.remove("hidden");
  document.getElementById("page-title").textContent = button.textContent.trim();
  if (button.dataset.view === "overview") loadOverview();
  if (button.dataset.view === "users") loadUsers();
  if (button.dataset.view === "transactions") loadTransactions();
}));

async function loadOverview() {
  try {
    const data = await api("/admin/overview");
    const cards = [
      ["Usuários cadastrados", data.total_users, `${data.active_users} com conta ativa`, "active"],
      ["Usuários online", data.online_users, "Sessões válidas", "active"],
      ["Volume total", money(data.total_moved_cents), `${money(data.moved_today_cents)} hoje`, "active"],
      ["Movimentações", data.total_transactions, `${data.transactions_today} feitas hoje`, "active"]
    ];
    document.getElementById("stats-grid").innerHTML = cards.map(([label,value,note]) =>
      `<article class="stat-card"><span class="stat-label">${escapeHtml(label)}</span><strong class="stat-value">${escapeHtml(value)}</strong><span class="stat-note">${escapeHtml(note)}</span></article>`).join("");

    const max = Math.max(...data.daily.map(item => item.amount_cents), 1);
    document.getElementById("volume-chart").innerHTML = data.daily.map(item => {
      const height = Math.max(5, Math.round(item.amount_cents / max * 160));
      const day = new Date(item.date + "T12:00:00").toLocaleDateString("pt-BR", { weekday:"short" }).replace(".","");
      return `<div class="bar-column" title="${money(item.amount_cents)}"><div class="bar" style="height:${height}px"></div><span>${day}</span></div>`;
    }).join("");

    const totalStatus = Object.values(data.status_counts).reduce((sum, count) => sum + count, 0) || 1;
    const labels = {completed:"Concluídas",processing:"Processando",failed:"Falhas",cancelled:"Canceladas",refunded:"Estornadas"};
    document.getElementById("status-list").innerHTML = Object.entries(data.status_counts).map(([status,count]) =>
      `<div class="status-row"><span>${labels[status] || status}</span><div class="status-track"><div class="status-fill" style="width:${count/totalStatus*100}%"></div></div><strong>${count}</strong></div>`).join("");

    document.getElementById("recent-rows").innerHTML = data.recent_transactions.map(item =>
      `<tr><td><strong>${escapeHtml(item.source.owner_name)}</strong><br><small>${escapeHtml(item.source.account_number)}</small></td><td>${escapeHtml(item.destination.owner_name)}<br><small>${escapeHtml(item.destination.account_number)}</small></td><td><strong>${escapeHtml(money(item.amount_cents))}</strong></td><td><span class="badge ${escapeHtml(item.status)}">${escapeHtml(item.status)}</span></td><td>${escapeHtml(dateTime(item.created_at))}</td></tr>`
    ).join("") || `<tr><td colspan="5">Nenhuma movimentação encontrada.</td></tr>`;
  } catch (error) { alert(error.message); }
}

let userSearchTimer;
document.getElementById("user-search").addEventListener("input", (event) => {
  clearTimeout(userSearchTimer);
  userSearchTimer = setTimeout(() => loadUsers(event.target.value), 300);
});
async function loadUsers(search = "") {
  try {
    const data = await api("/admin/users?per_page=50" + (search ? "&search=" + encodeURIComponent(search) : ""));
    document.getElementById("users-rows").innerHTML = data.items.map(user =>
      `<tr><td><div class="person"><div class="mini-avatar">${escapeHtml(user.name.charAt(0).toUpperCase())}</div><div><strong>${escapeHtml(user.name)}</strong><span>${escapeHtml(user.email)}</span></div></div></td><td>${user.role === "operator" ? "Operador" : "Cliente"}</td><td><span class="badge ${escapeHtml(user.status)}">${user.status === "active" ? "Ativo" : "Inativo"}</span>${user.online ? ' <span class="badge active">online</span>' : ""}</td><td>${escapeHtml(user.accounts_count)}</td><td><strong>${escapeHtml(money(user.balance_cents))}</strong></td><td>${escapeHtml(dateTime(user.last_active_at))}</td></tr>`
    ).join("") || `<tr><td colspan="6">Nenhum usuário encontrado.</td></tr>`;
  } catch (error) { alert(error.message); }
}

document.getElementById("status-filter").addEventListener("change", (event) => loadTransactions(event.target.value));
async function loadTransactions(status = "") {
  try {
    const data = await api("/admin/transactions?per_page=50" + (status ? "&status=" + encodeURIComponent(status) : ""));
    document.getElementById("transactions-rows").innerHTML = data.items.map(item =>
      `<tr><td><small>${escapeHtml(item.end_to_end_id)}</small></td><td>${escapeHtml(item.source.owner_name)}<br><small>${escapeHtml(item.source.account_number)}</small></td><td>${escapeHtml(item.destination.owner_name)}<br><small>${escapeHtml(item.destination.account_number)}</small></td><td><strong>${escapeHtml(money(item.amount_cents))}</strong></td><td><span class="badge ${escapeHtml(item.status)}">${escapeHtml(item.status)}</span></td><td>${escapeHtml(dateTime(item.created_at))}</td></tr>`
    ).join("") || `<tr><td colspan="6">Nenhuma movimentação encontrada.</td></tr>`;
  } catch (error) { alert(error.message); }
}

if (token) showDashboard();
