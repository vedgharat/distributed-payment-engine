/* ========================================================================
   DISTRIBUTED PAYMENT ENGINE — Frontend Application
   Vanilla JS • API Client • DOM Manipulation • Tab Router
   ======================================================================== */

// ─── Constants ───
const isProd = window.location.hostname !== 'localhost' && window.location.hostname !== '127.0.0.1';
// In production, you will set this to your Render/Koyeb backend URL.
// Since we don't know it yet, we default it. You may need to change this later!
const API_BASE = isProd 
  ? 'https://payment-gateway-production.up.railway.app/api/v1' // Replace with your actual backend URL when hosted
  : 'http://localhost:8080/api/v1';

// ─── State ───
const state = {
  wallets: [],
  selectedWalletId: null,
  transactions: [],
  loading: false,
};

// ─── API Client ───
const api = {
  async request(method, path, body = null, headers = {}) {
    const opts = {
      method,
      headers: { 'Content-Type': 'application/json', ...headers },
    };
    if (body) opts.body = JSON.stringify(body);

    const res = await fetch(`${API_BASE}${path}`, opts);
    const text = await res.text();
    let data;
    try { data = JSON.parse(text); } catch { data = text; }

    if (!res.ok) {
      const msg = data?.message || data?.error || data || `HTTP ${res.status}`;
      throw new Error(msg);
    }
    return data;
  },

  getWallets()          { return this.request('GET', '/wallets'); },
  getWallet(id)         { return this.request('GET', `/wallets/${id}`); },
  createWallet(body)    { return this.request('POST', '/wallets', body); },
  transfer(body, key)   { return this.request('POST', '/transfers', body, { 'Idempotency-Key': key }); },
  getTransactions(id)   { return this.request('GET', `/wallets/${id}/transactions`); },
};

// ─── Helpers ───
function $(sel, ctx = document) { return ctx.querySelector(sel); }
function $$(sel, ctx = document) { return [...ctx.querySelectorAll(sel)]; }

function formatMoney(amount, currency = 'USD') {
  const num = parseFloat(amount);
  if (isNaN(num)) return '$0.00';
  return num.toLocaleString('en-US', {
    style: 'currency',
    currency: currency || 'USD',
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });
}

function shortId(uuid) {
  if (!uuid) return '—';
  return uuid.substring(0, 8) + '…';
}

function formatDate(iso) {
  if (!iso) return '—';
  const d = new Date(iso);
  return d.toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' }) +
    ' ' + d.toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit' });
}

function getInitials(name) {
  if (!name) return '?';
  return name.split(' ').map(w => w[0]).join('').toUpperCase().slice(0, 2);
}

function generateIdempotencyKey() {
  return 'txn-' + Date.now() + '-' + Math.random().toString(36).slice(2, 8);
}

// ─── Toasts ───
function showToast(message, type = 'info') {
  const container = $('#toast-container');
  const toast = document.createElement('div');
  toast.className = `toast ${type}`;

  const icons = { success: '✓', error: '✕', info: 'ℹ' };
  toast.innerHTML = `<span>${icons[type] || 'ℹ'}</span> <span>${message}</span>`;

  container.appendChild(toast);
  setTimeout(() => {
    toast.style.animation = 'toastOut 0.3s ease forwards';
    setTimeout(() => toast.remove(), 300);
  }, 4000);
}

// ─── Tab Navigation ───
function initTabs() {
  $$('.tab-btn').forEach(btn => {
    btn.addEventListener('click', () => {
      const tab = btn.dataset.tab;
      $$('.tab-btn').forEach(b => b.classList.remove('active'));
      $$('.tab-content').forEach(c => c.classList.remove('active'));
      btn.classList.add('active');
      $(`#tab-${tab}`).classList.add('active');

      // Refresh data when switching tabs
      if (tab === 'dashboard') refreshDashboard();
      if (tab === 'wallets') refreshWallets();
      if (tab === 'transfer') refreshTransferForm();
      if (tab === 'history') refreshHistorySelectors();
    });
  });
}

// ─── Dashboard ───
async function refreshDashboard() {
  try {
    const wallets = await api.getWallets();
    state.wallets = wallets;

    const totalWallets = wallets.length;
    const totalBalance = wallets.reduce((acc, w) => acc + parseFloat(w.balance), 0);

    // Count currencies
    const currencies = new Set(wallets.map(w => w.currency));

    // Get transaction counts for all wallets
    let totalTransactions = 0;
    for (const w of wallets) {
      try {
        const txns = await api.getTransactions(w.id);
        totalTransactions += txns.length;
      } catch { /* ignore */ }
    }

    $('#stat-total-wallets').textContent = totalWallets;
    $('#stat-total-balance').textContent = formatMoney(totalBalance);
    $('#stat-total-transactions').textContent = totalTransactions;
    $('#stat-currencies').textContent = currencies.size || 0;

    // Render recent wallets
    renderRecentWallets(wallets.slice(0, 4));
  } catch (err) {
    showToast('Failed to load dashboard: ' + err.message, 'error');
  }
}

function renderRecentWallets(wallets) {
  const container = $('#recent-wallets');
  if (!wallets.length) {
    container.innerHTML = `
      <div class="empty-state">
        <div class="empty-state-icon">💳</div>
        <h3>No wallets yet</h3>
        <p>Create your first wallet to get started</p>
      </div>`;
    return;
  }

  container.innerHTML = wallets.map((w, i) => `
    <div class="wallet-card" style="animation-delay: ${i * 0.08}s" onclick="viewWalletDetails('${w.id}')">
      <div class="wallet-card-header">
        <div class="wallet-avatar">${getInitials(w.ownerName)}</div>
        <div>
          <div class="wallet-owner">${w.ownerName}</div>
          <div class="wallet-id">${shortId(w.id)}</div>
        </div>
      </div>
      <div class="wallet-balance-section">
        <span class="wallet-balance">${formatMoney(w.balance, w.currency)}</span>
        <span class="wallet-currency">${w.currency}</span>
      </div>
    </div>
  `).join('');
}

// ─── Wallets View ───
async function refreshWallets() {
  try {
    const wallets = await api.getWallets();
    state.wallets = wallets;
    renderWalletGrid(wallets);
  } catch (err) {
    showToast('Failed to load wallets: ' + err.message, 'error');
  }
}

function renderWalletGrid(wallets) {
  const container = $('#wallet-grid');
  if (!wallets.length) {
    container.innerHTML = `
      <div class="empty-state" style="grid-column: 1 / -1">
        <div class="empty-state-icon">💳</div>
        <h3>No wallets yet</h3>
        <p>Click "New Wallet" to create your first wallet</p>
      </div>`;
    return;
  }

  container.innerHTML = wallets.map((w, i) => `
    <div class="wallet-card" style="animation-delay: ${i * 0.06}s">
      <div class="wallet-card-header">
        <div class="wallet-avatar">${getInitials(w.ownerName)}</div>
        <div>
          <div class="wallet-owner">${w.ownerName}</div>
          <div class="wallet-id">${w.id}</div>
        </div>
      </div>
      <div class="wallet-balance-section">
        <span class="wallet-balance">${formatMoney(w.balance, w.currency)}</span>
        <span class="wallet-currency">${w.currency}</span>
      </div>
      <div class="wallet-actions">
        <button class="btn btn-sm btn-secondary" onclick="viewWalletHistory('${w.id}')">📋 History</button>
        <button class="btn btn-sm btn-primary" onclick="prefillTransfer('${w.id}')">💸 Send</button>
      </div>
    </div>
  `).join('');
}

function openCreateWalletModal() {
  $('#create-wallet-modal').classList.add('active');
  $('#cw-owner-name').value = '';
  $('#cw-initial-balance').value = '';
  $('#cw-currency').value = 'USD';
  $('#cw-owner-name').focus();
}

function closeCreateWalletModal() {
  $('#create-wallet-modal').classList.remove('active');
}

async function submitCreateWallet(e) {
  e.preventDefault();
  const ownerName = $('#cw-owner-name').value.trim();
  const initialBalance = parseFloat($('#cw-initial-balance').value);
  const currency = $('#cw-currency').value;

  if (!ownerName) return showToast('Please enter an owner name', 'error');
  if (isNaN(initialBalance) || initialBalance < 0) return showToast('Please enter a valid balance', 'error');

  const btn = $('#cw-submit-btn');
  btn.disabled = true;
  btn.innerHTML = '<span class="spinner"></span> Creating…';

  try {
    await api.createWallet({ ownerName, initialBalance, currency });
    showToast(`Wallet created for ${ownerName}`, 'success');
    closeCreateWalletModal();
    refreshWallets();
    refreshDashboard();
  } catch (err) {
    showToast('Failed: ' + err.message, 'error');
  } finally {
    btn.disabled = false;
    btn.innerHTML = '✓ Create Wallet';
  }
}

// ─── Wallet Details Modal ───
async function viewWalletDetails(id) {
  try {
    const wallet = await api.getWallet(id);
    const txns = await api.getTransactions(id);

    const modal = $('#wallet-details-modal');
    $('#wd-owner').textContent = wallet.ownerName;
    $('#wd-id').textContent = wallet.id;
    $('#wd-balance').textContent = formatMoney(wallet.balance, wallet.currency);
    $('#wd-currency').textContent = wallet.currency;

    const tbody = $('#wd-txns-body');
    if (!txns.length) {
      tbody.innerHTML = `<tr><td colspan="5" style="text-align:center;color:var(--text-muted);padding:32px;">No transactions yet</td></tr>`;
    } else {
      tbody.innerHTML = txns.slice(0, 10).map(t => `
        <tr>
          <td><span class="badge badge-${t.transactionType.toLowerCase()}">${t.transactionType === 'CREDIT' ? '↓' : '↑'} ${t.transactionType}</span></td>
          <td class="${t.transactionType === 'CREDIT' ? 'amount-positive' : 'amount-negative'}" style="font-weight:600">
            ${t.transactionType === 'CREDIT' ? '+' : '−'}${formatMoney(t.amount)}
          </td>
          <td><span class="badge badge-${t.status.toLowerCase()}">${t.status}</span></td>
          <td class="mono">${shortId(t.correlationId)}</td>
          <td style="color:var(--text-muted)">${formatDate(t.createdAt)}</td>
        </tr>
      `).join('');
    }

    modal.classList.add('active');
  } catch (err) {
    showToast('Failed to load wallet: ' + err.message, 'error');
  }
}

function closeWalletDetailsModal() {
  $('#wallet-details-modal').classList.remove('active');
}

// ─── Transfer ───
async function refreshTransferForm() {
  try {
    const wallets = await api.getWallets();
    state.wallets = wallets;

    const senderSel = $('#tf-sender');
    const receiverSel = $('#tf-receiver');

    const opts = wallets.map(w =>
      `<option value="${w.id}">${w.ownerName} (${formatMoney(w.balance, w.currency)})</option>`
    ).join('');

    senderSel.innerHTML = '<option value="">Select sender wallet…</option>' + opts;
    receiverSel.innerHTML = '<option value="">Select receiver wallet…</option>' + opts;
  } catch (err) {
    showToast('Failed to load wallets: ' + err.message, 'error');
  }
}

function prefillTransfer(senderId) {
  // Switch to transfer tab
  $$('.tab-btn').forEach(b => b.classList.remove('active'));
  $$('.tab-content').forEach(c => c.classList.remove('active'));
  $('[data-tab="transfer"]').classList.add('active');
  $('#tab-transfer').classList.add('active');

  refreshTransferForm().then(() => {
    $('#tf-sender').value = senderId;
  });
}

function viewWalletHistory(walletId) {
  // Switch to history tab
  $$('.tab-btn').forEach(b => b.classList.remove('active'));
  $$('.tab-content').forEach(c => c.classList.remove('active'));
  $('[data-tab="history"]').classList.add('active');
  $('#tab-history').classList.add('active');

  refreshHistorySelectors().then(() => {
    $('#hist-wallet-select').value = walletId;
    loadTransactionHistory();
  });
}

async function submitTransfer(e) {
  e.preventDefault();

  const senderWalletId = $('#tf-sender').value;
  const receiverWalletId = $('#tf-receiver').value;
  const amount = parseFloat($('#tf-amount').value);
  const currency = $('#tf-currency').value;

  if (!senderWalletId) return showToast('Please select a sender', 'error');
  if (!receiverWalletId) return showToast('Please select a receiver', 'error');
  if (senderWalletId === receiverWalletId) return showToast('Sender and receiver must be different', 'error');
  if (isNaN(amount) || amount <= 0) return showToast('Please enter a valid amount', 'error');

  const btn = $('#tf-submit-btn');
  btn.disabled = true;
  btn.innerHTML = '<span class="spinner"></span> Processing…';

  const resultDiv = $('#transfer-result');
  resultDiv.innerHTML = '';

  try {
    const key = generateIdempotencyKey();
    const res = await api.transfer({ senderWalletId, receiverWalletId, amount, currency }, key);

    resultDiv.innerHTML = `
      <div class="transfer-result">
        <h3>✓ Transfer Successful</h3>
        <div class="result-grid">
          <div class="result-item">
            <span class="result-item-label">Correlation ID</span>
            <span class="result-item-value mono">${shortId(res.correlationId)}</span>
          </div>
          <div class="result-item">
            <span class="result-item-label">Amount</span>
            <span class="result-item-value">${formatMoney(res.amount, res.currency)}</span>
          </div>
          <div class="result-item">
            <span class="result-item-label">Sender Balance After</span>
            <span class="result-item-value">${formatMoney(res.senderBalanceAfter, res.currency)}</span>
          </div>
          <div class="result-item">
            <span class="result-item-label">Receiver Balance After</span>
            <span class="result-item-value">${formatMoney(res.receiverBalanceAfter, res.currency)}</span>
          </div>
          <div class="result-item">
            <span class="result-item-label">Status</span>
            <span class="result-item-value"><span class="badge badge-completed">${res.status}</span></span>
          </div>
          <div class="result-item">
            <span class="result-item-label">Processed At</span>
            <span class="result-item-value">${formatDate(res.processedAt)}</span>
          </div>
        </div>
      </div>`;

    showToast('Transfer completed successfully!', 'success');

    // Refresh the dropdowns with updated balances
    refreshTransferForm();
  } catch (err) {
    resultDiv.innerHTML = `
      <div class="transfer-result error">
        <h3>✕ Transfer Failed</h3>
        <p style="color:var(--text-secondary);font-size:0.875rem;">${err.message}</p>
      </div>`;
    showToast('Transfer failed: ' + err.message, 'error');
  } finally {
    btn.disabled = false;
    btn.innerHTML = '💸 Send Money';
  }
}

// ─── History ───
async function refreshHistorySelectors() {
  try {
    const wallets = await api.getWallets();
    state.wallets = wallets;

    const sel = $('#hist-wallet-select');
    sel.innerHTML = '<option value="">Select a wallet…</option>' +
      wallets.map(w => `<option value="${w.id}">${w.ownerName} — ${shortId(w.id)}</option>`).join('');
  } catch (err) {
    showToast('Failed to load wallets: ' + err.message, 'error');
  }
}

async function loadTransactionHistory() {
  const walletId = $('#hist-wallet-select').value;
  if (!walletId) {
    $('#history-table-body').innerHTML = '';
    $('#history-empty').style.display = 'block';
    return;
  }

  $('#history-empty').style.display = 'none';

  try {
    const txns = await api.getTransactions(walletId);
    state.transactions = txns;
    renderHistoryTable(txns);
  } catch (err) {
    showToast('Failed to load history: ' + err.message, 'error');
  }
}

function renderHistoryTable(txns) {
  const tbody = $('#history-table-body');
  const empty = $('#history-empty');

  if (!txns.length) {
    tbody.innerHTML = '';
    empty.style.display = 'block';
    empty.querySelector('h3').textContent = 'No transactions';
    empty.querySelector('p').textContent = 'This wallet has no transaction history yet';
    return;
  }

  empty.style.display = 'none';
  tbody.innerHTML = txns.map(t => `
    <tr>
      <td>
        <span class="badge badge-${t.transactionType.toLowerCase()}">
          ${t.transactionType === 'CREDIT' ? '↓' : '↑'} ${t.transactionType}
        </span>
      </td>
      <td class="${t.transactionType === 'CREDIT' ? 'amount-positive' : 'amount-negative'}" style="font-weight:600">
        ${t.transactionType === 'CREDIT' ? '+' : '−'}${formatMoney(t.amount)}
      </td>
      <td><span class="badge badge-${t.status.toLowerCase()}">${t.status}</span></td>
      <td class="mono">${shortId(t.correlationId)}</td>
      <td style="max-width:200px;overflow:hidden;text-overflow:ellipsis">${t.description || '—'}</td>
      <td style="color:var(--text-muted)">${formatDate(t.createdAt)}</td>
    </tr>
  `).join('');
}

// ─── Init ───
document.addEventListener('DOMContentLoaded', () => {
  initTabs();

  // Modal close on overlay click
  $$('.modal-overlay').forEach(overlay => {
    overlay.addEventListener('click', (e) => {
      if (e.target === overlay) {
        overlay.classList.remove('active');
      }
    });
  });

  // ESC key closes modals
  document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape') {
      $$('.modal-overlay.active').forEach(m => m.classList.remove('active'));
    }
  });

  // Form submissions
  $('#create-wallet-form').addEventListener('submit', submitCreateWallet);
  $('#transfer-form').addEventListener('submit', submitTransfer);
  $('#hist-wallet-select').addEventListener('change', loadTransactionHistory);

  // Load initial data
  refreshDashboard();
});
