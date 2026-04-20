-- ══════════════════════════════════════════════════════════════
--  Expense Planner – PostgreSQL Schema  (UPGRADED v2)
--  Run once: psql -U postgres -d expense_tracker -f init.sql
-- ══════════════════════════════════════════════════════════════

-- CREATE DATABASE expense_tracker;  ← run separately if needed

-- ── TABLE: users ────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS users (
    user_id    SERIAL PRIMARY KEY,
    name       VARCHAR(100)  NOT NULL,
    email      VARCHAR(150)  UNIQUE NOT NULL,
    password   VARCHAR(255)  NOT NULL,
    created_at TIMESTAMP     DEFAULT CURRENT_TIMESTAMP
);

-- ── TABLE: budget ────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS budget (
    budget_id  SERIAL PRIMARY KEY,
    user_id    INTEGER       REFERENCES users(user_id) ON DELETE CASCADE,
    month      INTEGER       NOT NULL,
    year       INTEGER       NOT NULL,
    version_no INTEGER       NOT NULL DEFAULT 1,
    created_at TIMESTAMP     DEFAULT CURRENT_TIMESTAMP
);

-- ── TABLE: budget_details ────────────────────────────────────
CREATE TABLE IF NOT EXISTS budget_details (
    detail_id      SERIAL PRIMARY KEY,
    budget_id      INTEGER        REFERENCES budget(budget_id) ON DELETE CASCADE,
    category       VARCHAR(100)   NOT NULL,
    planned_amount NUMERIC(10,2)  NOT NULL DEFAULT 0
);

-- ── TABLE: expenses ─────────────────────────────────────────
--   UPGRADED: added source column (manual | import)
CREATE TABLE IF NOT EXISTS expenses (
    expense_id SERIAL PRIMARY KEY,
    user_id    INTEGER        REFERENCES users(user_id) ON DELETE CASCADE,
    category   VARCHAR(100)   NOT NULL,
    amount     NUMERIC(10,2)  NOT NULL,
    date       DATE           NOT NULL,
    note       TEXT,
    source     VARCHAR(20)    NOT NULL DEFAULT 'manual',
    created_at TIMESTAMP      DEFAULT CURRENT_TIMESTAMP
);

-- Run on existing DB to upgrade (safe; skips if column already exists)
ALTER TABLE expenses ADD COLUMN IF NOT EXISTS source VARCHAR(20) NOT NULL DEFAULT 'manual';

-- ── TABLE: payment_imports ───────────────────────────────────
--   Audit log of every CSV/UPI import batch
CREATE TABLE IF NOT EXISTS payment_imports (
    import_id      SERIAL PRIMARY KEY,
    user_id        INTEGER        REFERENCES users(user_id) ON DELETE CASCADE,
    filename       VARCHAR(255),
    total_records  INTEGER        DEFAULT 0,
    imported_at    TIMESTAMP      DEFAULT CURRENT_TIMESTAMP
);

-- ── INDEXES ──────────────────────────────────────────────────
CREATE INDEX IF NOT EXISTS idx_budget_user      ON budget(user_id, month, year);
CREATE INDEX IF NOT EXISTS idx_expenses_user    ON expenses(user_id, date);
CREATE INDEX IF NOT EXISTS idx_expenses_source  ON expenses(user_id, source);
CREATE INDEX IF NOT EXISTS idx_imports_user     ON payment_imports(user_id);

-- ══════════════════════════════════════════════════════════════
--  DBMS LAB REFERENCE QUERIES
-- ══════════════════════════════════════════════════════════════

-- Q1: Monthly spending totals (last 6 months)
-- SELECT EXTRACT(MONTH FROM date) AS mon, EXTRACT(YEAR FROM date) AS yr,
--        SUM(amount) AS total
-- FROM   expenses WHERE user_id = 1 AND date >= NOW() - INTERVAL '6 months'
-- GROUP  BY mon, yr ORDER BY yr DESC, mon DESC;

-- Q2: Category breakdown for a month
-- SELECT category, SUM(amount) AS total FROM expenses
-- WHERE  user_id = 1 AND EXTRACT(MONTH FROM date) = 4 AND EXTRACT(YEAR FROM date) = 2025
-- GROUP  BY category ORDER BY total DESC;

-- Q3: Budget vs Actual
-- SELECT bd.category, bd.planned_amount,
--        COALESCE(SUM(e.amount),0) AS actual,
--        bd.planned_amount - COALESCE(SUM(e.amount),0) AS saved
-- FROM   budget b JOIN budget_details bd ON b.budget_id = bd.budget_id
-- LEFT   JOIN expenses e ON e.user_id = b.user_id AND e.category = bd.category
--          AND EXTRACT(MONTH FROM e.date) = b.month AND EXTRACT(YEAR FROM e.date) = b.year
-- WHERE  b.user_id = 1 AND b.month = 4 AND b.year = 2025
--   AND  b.version_no = (SELECT MAX(version_no) FROM budget WHERE user_id=1 AND month=4 AND year=2025)
-- GROUP  BY bd.category, bd.planned_amount;

-- Q4: Prediction – 3-month average per category
-- SELECT category, ROUND(AVG(monthly_total),2) AS predicted
-- FROM (SELECT category, SUM(amount) AS monthly_total
--       FROM expenses WHERE user_id=1
--         AND date >= date_trunc('month',NOW()) - INTERVAL '3 months'
--         AND date <  date_trunc('month',NOW())
--       GROUP BY category, EXTRACT(MONTH FROM date), EXTRACT(YEAR FROM date)) t
-- GROUP BY category;

-- Q5: Import source breakdown
-- SELECT source, COUNT(*) AS txn_count, SUM(amount) AS total
-- FROM expenses WHERE user_id = 1 GROUP BY source;

SELECT 'Schema v2 ready.' AS status;
