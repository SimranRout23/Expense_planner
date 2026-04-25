/* ══════════════════════════════════════════════════════════════
   script.js  –  Shared utilities for all pages
   Backend: Java/Tomcat at http://localhost:8080/expenseplanner/api
   ══════════════════════════════════════════════════════════════ */

const API = "http://localhost:8080/expenseplanner";

/* ── HTTP helpers ──────────────────────────────────────────────── */
async function apiGet(path) {
  const res = await fetch(API + path, { credentials: "include" });
  return res.json();
}
async function apiPost(path, body) {
  const res = await fetch(API + path, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    credentials: "include",
    body: JSON.stringify(body)
  });
  return res.json();
}
async function apiDelete(path) {
  const res = await fetch(API + path, { method: "DELETE", credentials: "include" });
  return res.json();
}

/* ── Alert helper ──────────────────────────────────────────────── */
function showAlert(id, msg, type = "success") {
  const el = document.getElementById(id);
  if (!el) return;
  el.textContent = msg;
  el.className = `alert alert-${type} show`;
  setTimeout(() => el.classList.remove("show"), 4000);
}

/* ── Format currency ───────────────────────────────────────────── */
function fmt(n) {
  return "₹" + Number(n).toLocaleString("en-IN", { minimumFractionDigits: 2 });
}

/* ── Current month/year defaults ───────────────────────────────── */
function currentMonth() { return new Date().getMonth() + 1; }
function currentYear()  { return new Date().getFullYear(); }

const MONTH_NAMES = ["", "January","February","March","April","May","June",
                     "July","August","September","October","November","December"];

/* ── Navbar: check login & highlight active link ───────────────── */
async function initNavbar() {
  const data = await apiGet("/me").catch(() => null);
  const nameEl = document.getElementById("nav-name");
  if (nameEl && data?.name) nameEl.textContent = data.name;

  // Highlight active nav link
  const links = document.querySelectorAll(".nav-links a");
  links.forEach(a => {
    if (a.href === location.href ||
        location.pathname.endsWith(a.getAttribute("href"))) {
      a.classList.add("active");
    }
  });

  // Logout button
  const logoutBtn = document.getElementById("btn-logout");
  if (logoutBtn) {
    logoutBtn.addEventListener("click", async () => {
      await apiPost("/logout", {});
      location.href = "index.html";
    });
  }

  // Redirect to login if not authenticated (skip on index)
  if (!location.pathname.endsWith("index.html") && !location.pathname.endsWith("/")) {
    if (!data?.logged_in) location.href = "index.html";
  }
}

/* ── Populate month/year selects ──────────────────────────────── */
function populateMonthYear(monthId, yearId) {
  const monthSel = document.getElementById(monthId);
  const yearSel  = document.getElementById(yearId);
  if (!monthSel || !yearSel) return;

  MONTH_NAMES.slice(1).forEach((m, i) => {
    const opt = document.createElement("option");
    opt.value = i + 1; opt.textContent = m;
    if (i + 1 === currentMonth()) opt.selected = true;
    monthSel.appendChild(opt);
  });

  const yr = currentYear();
  for (let y = yr; y >= yr - 3; y--) {
    const opt = document.createElement("option");
    opt.value = y; opt.textContent = y;
    if (y === yr) opt.selected = true;
    yearSel.appendChild(opt);
  }
}

/* Run navbar init on every page */
document.addEventListener("DOMContentLoaded", initNavbar);


/* ── CDN libraries (loaded lazily when first needed) ──────── */
const CHARTJS_URL   = "https://cdnjs.cloudflare.com/ajax/libs/Chart.js/4.4.1/chart.umd.min.js";
const PAPAPARSE_URL = "https://cdnjs.cloudflare.com/ajax/libs/PapaParse/5.4.1/papaparse.min.js";
const JSPDF_URL     = "https://cdnjs.cloudflare.com/ajax/libs/jspdf/2.5.1/jspdf.umd.min.js";
const JSPDF_AUTO_URL= "https://cdnjs.cloudflare.com/ajax/libs/jspdf-autotable/3.8.2/jspdf.plugin.autotable.min.js";

function loadScript(url) {
  return new Promise((resolve, reject) => {
    if (document.querySelector(`script[src="${url}"]`)) { resolve(); return; }
    const s = document.createElement("script");
    s.src = url; s.onload = resolve; s.onerror = reject;
    document.head.appendChild(s);
  });
}

/* ── Chart colour palette ─────────────────────────────────── */
const CHART_COLORS = [
  "#FF7B5E","#2DC79A","#F5A623","#4EA8DE","#9B59B6",
  "#E74C3C","#1ABC9C","#F39C12","#3498DB","#95A5A6"
];

/* ── Render Pie Chart ─────────────────────────────────────── */
let pieChartInstance = null;
async function renderPieChart(canvasId, labels, data) {
  await loadScript(CHARTJS_URL);
  const ctx = document.getElementById(canvasId);
  if (!ctx) return;
  if (pieChartInstance) { pieChartInstance.destroy(); }
  pieChartInstance = new Chart(ctx, {
    type: "doughnut",
    data: {
      labels,
      datasets: [{
        data,
        backgroundColor: CHART_COLORS.slice(0, data.length),
        borderWidth: 2,
        borderColor: "#fff",
        hoverOffset: 8
      }]
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      plugins: {
        legend: {
          position: "right",
          labels: {
            font: { family: "'Plus Jakarta Sans',sans-serif", size: 11 },
            boxWidth: 12, padding: 10
          }
        },
        tooltip: {
          callbacks: {
            label: ctx => ` ₹${Number(ctx.parsed).toLocaleString("en-IN",{minimumFractionDigits:2})}`
          }
        }
      }
    }
  });
}

/* ── Render Bar Chart (Planned vs Actual) ─────────────────── */
let barChartInstance = null;
async function renderBarChart(canvasId, labels, plannedData, actualData) {
  await loadScript(CHARTJS_URL);
  const ctx = document.getElementById(canvasId);
  if (!ctx) return;
  if (barChartInstance) { barChartInstance.destroy(); }
  barChartInstance = new Chart(ctx, {
    type: "bar",
    data: {
      labels,
      datasets: [
        {
          label: "Planned ₹",
          data: plannedData,
          backgroundColor: "rgba(45,199,154,0.7)",
          borderColor: "#2DC79A",
          borderWidth: 1.5,
          borderRadius: 6
        },
        {
          label: "Actual ₹",
          data: actualData,
          backgroundColor: "rgba(255,123,94,0.7)",
          borderColor: "#FF7B5E",
          borderWidth: 1.5,
          borderRadius: 6
        }
      ]
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      plugins: {
        legend: {
          labels: { font: { family: "'Plus Jakarta Sans',sans-serif", size: 11 }, boxWidth: 12 }
        },
        tooltip: {
          callbacks: {
            label: ctx => ` ${ctx.dataset.label}: ₹${Number(ctx.parsed.y).toLocaleString("en-IN",{minimumFractionDigits:2})}`
          }
        }
      },
      scales: {
        x: { grid: { display: false }, ticks: { font: { size: 11 } } },
        y: {
          grid: { color: "rgba(0,0,0,.05)" },
          ticks: {
            font: { size: 11 },
            callback: v => "₹" + Number(v).toLocaleString("en-IN")
          }
        }
      }
    }
  });
}

/* ── Render Trend Line Chart ──────────────────────────────── */
let trendChartInstance = null;
async function renderTrendChart(canvasId, labels, totals) {
  await loadScript(CHARTJS_URL);
  const ctx = document.getElementById(canvasId);
  if (!ctx) return;
  if (trendChartInstance) { trendChartInstance.destroy(); }
  trendChartInstance = new Chart(ctx, {
    type: "line",
    data: {
      labels,
      datasets: [{
        label: "Monthly Spending ₹",
        data: totals,
        borderColor: "#FF7B5E",
        backgroundColor: "rgba(255,123,94,0.12)",
        borderWidth: 2.5,
        pointBackgroundColor: "#FF7B5E",
        pointRadius: 5,
        pointHoverRadius: 7,
        fill: true,
        tension: 0.35
      }]
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      plugins: {
        legend: { labels: { font: { family: "'Plus Jakarta Sans',sans-serif", size: 11 }, boxWidth: 12 } },
        tooltip: {
          callbacks: {
            label: ctx => ` ₹${Number(ctx.parsed.y).toLocaleString("en-IN",{minimumFractionDigits:2})}`
          }
        }
      },
      scales: {
        x: { grid: { display: false }, ticks: { font: { size: 11 } } },
        y: {
          grid: { color: "rgba(0,0,0,.05)" },
          ticks: {
            font: { size: 11 },
            callback: v => "₹" + Number(v).toLocaleString("en-IN")
          }
        }
      }
    }
  });
}

/* ── Load & render Smart Insights ─────────────────────────── */
async function loadInsights(month, year, containerId) {
  const container = document.getElementById(containerId);
  if (!container) return;
  container.innerHTML = '<div style="display:flex;align-items:center;gap:.5rem;color:var(--text-dim);font-size:.85rem"><div class="spinner"></div> Analysing your spending…</div>';
  const data = await apiGet(`/insights?month=${month}&year=${year}`);
  if (!data.success) { container.innerHTML = ""; return; }
  if (!data.insights || data.insights.length === 0) {
    container.innerHTML = '<div class="insight-item info"><span class="i-icon">💡</span><span class="i-text">Add some expenses to see personalised insights here.</span></div>';
    return;
  }
  container.innerHTML = data.insights.map(ins =>
    `<div class="insight-item ${ins.type}">
       <span class="i-icon">${ins.icon}</span>
       <span class="i-text">${ins.msg}</span>
     </div>`
  ).join("");
  return data;
}

/* ── Load & render Prediction ─────────────────────────────── */
async function loadPrediction(containerId) {
  const panel = document.getElementById(containerId);
  if (!panel) return;
  panel.innerHTML = '<div style="color:#7aafc4;font-size:.85rem;padding:.5rem 0">⏳ Calculating prediction…</div>';
  const data = await apiGet("/prediction");
  if (!data.success) { panel.innerHTML = ""; return; }

  const monthNames = ["","Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec"];
  const nextMonth  = new Date(); nextMonth.setMonth(nextMonth.getMonth() + 1);
  const nextLabel  = monthNames[nextMonth.getMonth() + 1] + " " + nextMonth.getFullYear();

  const catRows = (data.categories || []).slice(0, 5).map(c =>
    `<div class="pred-cat-row">
       <span class="pcat-name">${c.category}</span>
       <span class="pcat-val">${fmt(c.predicted)}</span>
     </div>`
  ).join("");

  panel.innerHTML = `
    <div class="pred-grid">
      <div class="pred-item">
        <div class="pred-label">Predicted for ${nextLabel}</div>
        <div class="pred-value">${fmt(data.predicted_total)}</div>
        <div class="pred-sub">based on last ${data.months_used || "few"} months</div>
      </div>
      <div class="pred-item">
        <div class="pred-label">Yearly Projection</div>
        <div class="pred-value">${fmt(data.yearly_projection)}</div>
        <div class="pred-sub">If you continue like this…</div>
      </div>
      <div class="pred-item">
        <div class="pred-label">Monthly Average</div>
        <div class="pred-value">${fmt(data.predicted_total)}</div>
        <div class="pred-sub">across tracked months</div>
      </div>
    </div>
    ${catRows ? `<div class="pred-cats">
      <div class="pred-cats-title">Top predicted categories</div>
      ${catRows}
    </div>` : ""}`;
}

/* ── CSV Export (triggers backend download) ───────────────── */
function exportCSV(month, year) {
  const url = `${API}/export?month=${month}&year=${year}`;
  const a   = document.createElement("a");
  a.href    = url;
  a.target  = "_blank";
  a.click();
}

/* ── PDF Export using jsPDF + autotable ───────────────────── */
async function exportPDF(month, year) {
  await loadScript(JSPDF_URL);
  await loadScript(JSPDF_AUTO_URL);
  const { jsPDF } = window.jspdf;

  const expData = await apiGet(`/expenses?month=${month}&year=${year}`);
  const insData = await apiGet(`/insights?month=${month}&year=${year}`);

  const mNames  = ["","January","February","March","April","May","June",
                   "July","August","September","October","November","December"];
  const doc = new jsPDF();

  // Header
  doc.setFillColor(255, 123, 94);
  doc.rect(0, 0, 210, 28, "F");
  doc.setTextColor(255, 255, 255);
  doc.setFont("helvetica", "bold");
  doc.setFontSize(16);
  doc.text("My Expense Planner – Monthly Report", 14, 13);
  doc.setFontSize(11);
  doc.text(`${mNames[month]} ${year}`, 14, 22);

  // Summary row
  const budget = insData.success ? insData.total_budget  : 0;
  const spent  = insData.success ? insData.total_actual   : 0;
  const saved  = insData.success ? insData.savings         : 0;
  doc.setTextColor(60, 60, 60);
  doc.setFontSize(11);
  doc.setFont("helvetica", "normal");
  doc.text(`Budget: ₹${Number(budget).toFixed(2)}   Spent: ₹${Number(spent).toFixed(2)}   Remaining: ₹${Number(saved).toFixed(2)}`, 14, 38);

  // Expense table
  const rows = (expData.expenses || []).map(e => [e.date, e.category, `₹${Number(e.amount).toFixed(2)}`, e.note || "—", e.source || "manual"]);
  doc.autoTable({
    head: [["Date","Category","Amount","Note","Source"]],
    body: rows,
    startY: 44,
    styles: { fontSize: 10, cellPadding: 3 },
    headStyles: { fillColor: [255, 123, 94], textColor: 255, fontStyle: "bold" },
    alternateRowStyles: { fillColor: [253, 248, 243] }
  });

  // Insights section
  if (insData.success && insData.insights && insData.insights.length) {
    const finalY = doc.lastAutoTable.finalY + 10;
    doc.setFontSize(12);
    doc.setFont("helvetica", "bold");
    doc.setTextColor(60, 60, 60);
    doc.text("Smart Insights", 14, finalY);
    doc.setFont("helvetica", "normal");
    doc.setFontSize(10);
    insData.insights.forEach((ins, i) => {
      doc.text(`${ins.icon}  ${ins.msg}`, 16, finalY + 8 + i * 7, { maxWidth: 180 });
    });
  }

  doc.save(`ExpenseReport_${mNames[month]}_${year}.pdf`);
}

/* ── Import: parse CSV/Excel file in browser ──────────────── */
async function parseImportFile(file, previewBodyId, previewSectionId, countId) {
  await loadScript(PAPAPARSE_URL);

  Papa.parse(file, {
    header: true,
    skipEmptyLines: true,
    complete: function(results) {
      const rows    = results.data;
      const preview = document.getElementById(previewBodyId);
      const section = document.getElementById(previewSectionId);
      const counter = document.getElementById(countId);
      if (!preview || !rows.length) return;

      // Store parsed rows globally for import button
      window._importRows = rows;

      // Detect column names (case-insensitive)
      const header = Object.keys(rows[0]).map(k => k.toLowerCase().trim());
      const getCol = (row, ...names) => {
        for (const n of names) {
          const key = Object.keys(row).find(k => k.toLowerCase().trim() === n);
          if (key && row[key]) return row[key].toString().trim();
        }
        return "";
      };

      // Parse debit transactions only
      const transactions = [];
      for (const row of rows) {
        const debit = parseFloat(getCol(row,"debit","dr","withdrawal","amount","amt","credit")) || 0;
        if (debit <= 0) continue;
        const dateRaw = getCol(row,"date","value date","txn date","transaction date");
        const note    = getCol(row,"description","narration","particulars","remarks","merchant","note","details");
        transactions.push({ date: normaliseDate(dateRaw), amount: debit, note, category: "" });
      }

      window._importRows = transactions;

      if (counter) counter.innerHTML = `Found <strong>${transactions.length}</strong> debit transactions to import.`;
      if (section) section.classList.add("visible");

      preview.innerHTML = transactions.slice(0, 20).map(t =>
        `<tr>
           <td>${t.date}</td>
           <td>₹${t.amount.toFixed(2)}</td>
           <td style="max-width:200px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap">${t.note}</td>
           <td><span class="badge-cat">${autoCategorise(t.note)}</span></td>
         </tr>`
      ).join("");
    }
  });
}

function normaliseDate(raw) {
  if (!raw) return new Date().toISOString().split("T")[0];
  // Try DD/MM/YYYY or DD-MM-YYYY
  const dmy = raw.match(/^(\d{1,2})[\/\-](\d{1,2})[\/\-](\d{2,4})$/);
  if (dmy) {
    const y = dmy[3].length === 2 ? "20" + dmy[3] : dmy[3];
    return '${y}-${dmy[2].padStart(2,"0")}-${dmy[1].padStart(2,"0")}';
  }
  // Already YYYY-MM-DD
  const ymd = raw.match(/^(\d{4})-(\d{2})-(\d{2})$/);
  if (ymd) return raw;
  // Fallback
  try { return new Date(raw).toISOString().split("T")[0]; } catch { return new Date().toISOString().split("T")[0]; }
}

/* ── Client-side auto-categorise (mirrors ImportServlet logic) */
const CLIENT_MERCHANT_MAP = {
  "swiggy":"Food","zomato":"Food","domino":"Food","pizza":"Food","mcdonald":"Food",
  "kfc":"Food","restaurant":"Food","cafe":"Food","canteen":"Food",
  "uber":"Transport","ola":"Transport","rapido":"Transport","metro":"Transport",
  "irctc":"Transport","petrol":"Transport","fuel":"Transport",
  "amazon":"Shopping","flipkart":"Shopping","myntra":"Shopping","meesho":"Shopping",
  "ajio":"Shopping","nykaa":"Shopping","mart":"Shopping",
  "netflix":"Entertainment","hotstar":"Entertainment","prime":"Entertainment",
  "spotify":"Entertainment","pvr":"Entertainment","inox":"Entertainment","cinema":"Entertainment",
  "jio":"Utilities","airtel":"Utilities","bsnl":"Utilities","electricity":"Utilities",
  "recharge":"Utilities","water":"Utilities",
  "pharmacy":"Health","apollo":"Health","hospital":"Health","clinic":"Health","doctor":"Health",
  "udemy":"Education","coursera":"Education","book":"Education","tuition":"Education","fee":"Education",
  "rent":"Rent","landlord":"Rent",
  "hotel":"Travel","oyo":"Travel","makemytrip":"Travel","goibibo":"Travel","airport":"Travel"
};
function autoCategorise(note) {
  if (!note) return "Other";
  const low = note.toLowerCase();
  for (const [kw, cat] of Object.entries(CLIENT_MERCHANT_MAP)) {
    if (low.includes(kw)) return cat;
  }
  return "Other";
}

/* ── Submit import to backend ─────────────────────────────── */
async function submitImport(filename, alertId) {
  const rows = window._importRows;
  if (!rows || rows.length === 0) { showAlert(alertId, "No transactions to import.", "error"); return; }
  const transactions = rows.map(r => ({
    date:     r.date,
    amount:   r.amount,
    note:     r.note,
    category: autoCategorise(r.note),
    source:   "import"
  }));
  const data = await apiPost("/import", { filename, transactions });
  if (data.success) {
    showAlert(alertId, `✅ Imported ${data.inserted} transactions successfully!`, "success");
    window._importRows = [];
    document.getElementById("import-preview-section") && (document.getElementById("import-preview-section").classList.remove("visible"));
  } else {
    showAlert(alertId, data.message || "Import failed.", "error");
  }
}
