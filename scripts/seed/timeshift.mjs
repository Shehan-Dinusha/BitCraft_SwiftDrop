#!/usr/bin/env node
// SwiftDrop demo-data time-shift.
//
// The seeder drives every order through the real APIs, so every event is stamped
// "now" and the whole history lands on one day. This script runs ONCE, after the
// last seed stage, and spreads that history over the past ~30 days:
//
//   * finished orders (delivered / failed / cancelled) get a booking time 0–29 days
//     ago (more in recent days, so charts show growth), booked 08:00–19:00 Colombo
//     time, with realistic durations (express 3–10 h, standard 18–60 h);
//   * orders still in progress stay within the last day or two;
//   * every timestamp of an order, in every database, goes through the SAME mapping
//     (booking → new booking time, the rest stretched in proportion), so event order
//     and spacing stay consistent across services;
//   * notifications move to the moment of the status they announce;
//   * analytics' daily and per-courier totals are recomputed with its own rules;
//   * seeded accounts get believable sign-up dates.
//
// Only seeded data is touched: orders whose customer has an @example.com account.
// It edits demo data directly in six service databases (owned by other members);
// it never changes their schema or code. Runs through `kubectl exec` into the
// PostgreSQL pods, so it works on the local cluster and on AKS alike.
//
// Usage:  node scripts/seed/timeshift.mjs [--dry-run] [--force]
//   --dry-run  print the planned distribution, change nothing
//   --force    run even though the data already looks shifted

import { execFileSync } from 'node:child_process';

const NS = process.env.SEED_DATA_NAMESPACE || 'swiftdrop-data';
const DRY_RUN = process.argv.includes('--dry-run');
const FORCE = process.argv.includes('--force');
const HISTORY_DAYS = 30;
const COLOMBO_OFFSET_MS = 5.5 * 3600 * 1000; // UTC+05:30, no daylight saving
const HOUR = 3600;

// ── Helpers ──────────────────────────────────────────────────────────────────
function hash(str) {
  let h = 0x811c9dc5;
  for (let i = 0; i < str.length; i++) { h ^= str.charCodeAt(i); h = Math.imul(h, 0x01000193); }
  return h >>> 0;
}
function rng(seed) {
  let a = hash(String(seed));
  const next = () => {
    a = (a + 0x6d2b79f5) | 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
  return { next, between: (lo, hi) => lo + next() * (hi - lo) };
}

function psql(db, sql) {
  return execFileSync('kubectl', [
    '-n', NS, 'exec', '-i', `${db}-postgres-0`, '--', 'sh', '-c',
    'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 -q -tA -F "|"',
  ], { input: `SET TimeZone = 'UTC';\n${sql}`, encoding: 'utf8', maxBuffer: 64 * 1024 * 1024 });
}
const rows = (out) => out.split('\n').filter(Boolean).map((l) => l.split('|'));
const sqlText = (s) => `'${String(s).replace(/'/g, "''")}'`;
const num = (n) => Number(n).toFixed(3);

function columnsOf(db) {
  const out = psql(db, `SELECT table_name, column_name FROM information_schema.columns WHERE table_schema = 'public';`);
  const map = {};
  for (const [t, c] of rows(out)) (map[t] ||= new Set()).add(c);
  return map;
}

// Shared SQL: the per-order mapping table and the mapping function.
//   shifted(t) = n0 + clamp(t - t0, 0, s) * k, never later than one minute ago.
const MAPPING_FN = `
CREATE FUNCTION pg_temp.shifted(col timestamptz, t0 float8, s float8, n0 float8, k float8)
RETURNS timestamptz LANGUAGE sql STABLE AS $$
  SELECT CASE WHEN col IS NULL THEN NULL ELSE
    to_timestamp(LEAST(n0 + LEAST(GREATEST(extract(epoch FROM col) - t0, 0), s) * k,
                       extract(epoch FROM now()) - 60)) END
$$;
CREATE FUNCTION pg_temp.status_of(txt text) RETURNS text LANGUAGE sql IMMUTABLE AS $$
  SELECT CASE
    WHEN txt ~* 'out.for.delivery' THEN 'OUT_FOR_DELIVERY'
    WHEN txt ~* 'picked' THEN 'PICKED_UP'
    WHEN txt ~* 'fail|unsuccessful|could not' THEN 'FAILED'
    WHEN txt ~* 'delivered' THEN 'DELIVERED'
    WHEN txt ~* 'cancel' THEN 'CANCELLED'
    ELSE 'CREATED' END
$$;`;

function mappingTable(plan) {
  const values = plan.map((p) => `(${sqlText(p.orderId)}::uuid, ${sqlText(p.tracking)}, ${sqlText(p.fakeTracking)}, `
    + `${num(p.t0)}, ${num(p.s)}, ${num(p.n0)}, ${p.k.toFixed(6)})`).join(',\n');
  return `CREATE TEMP TABLE shift (order_id uuid PRIMARY KEY, tn text, tf text, t0 float8, s float8, n0 float8, k float8);
INSERT INTO shift VALUES\n${values};\n`;
}

// UPDATE every listed timestamp column that exists in this database.
function shiftUpdate(cols, table, columns, { from = 'shift s', where = 'x.order_id = s.order_id' } = {}) {
  const present = columns.filter((c) => cols[table]?.has(c));
  if (!present.length) return '';
  const sets = present.map((c) => `${c} = pg_temp.shifted(x.${c}, s.t0, s.s, s.n0, s.k)`).join(', ');
  return `UPDATE ${table} x SET ${sets} FROM ${from} WHERE ${where};\n`;
}

// ── 1. Plan ──────────────────────────────────────────────────────────────────
function colomboDayStart(epochSec, daysAgo) {
  const local = new Date(epochSec * 1000 + COLOMBO_OFFSET_MS);
  local.setUTCHours(0, 0, 0, 0);
  return (local.getTime() - COLOMBO_OFFSET_MS) / 1000 - daysAgo * 86400;
}

function planOrder(o, now) {
  const r = rng(`timeshift:${o.orderId}`);
  const s = Math.max(1, o.last - o.t0); // seconds the order actually took during seeding
  const finished = ['DELIVERED', 'FAILED', 'CANCELLED'].includes(o.status);
  let n0; let span;
  if (finished) {
    const days = Math.floor(HISTORY_DAYS * (1 - Math.sqrt(r.next()))); // 0–29, recent-heavy
    n0 = colomboDayStart(now, days) + r.between(8, 19) * HOUR;
    if (n0 > now - 3 * HOUR) n0 = colomboDayStart(now, 1) + r.between(8, 19) * HOUR;
    span = o.status === 'CANCELLED' ? r.between(0.3, 4) * HOUR
      : o.status === 'FAILED' ? r.between(8, 40) * HOUR
        : o.speed === 'EXPRESS' ? r.between(3, 10) * HOUR : r.between(18, 60) * HOUR;
    span = Math.min(span, now - HOUR - n0);
  } else {
    // Still waiting for a courier: booked within the last day and a half. On the road:
    // booked within the last 2.5 days (a standard delivery can take that long).
    n0 = now - (o.status === 'CREATED' ? r.between(1, 36) : r.between(8, 60)) * HOUR;
    span = (now - 20 * 60 - n0) * r.between(0.5, 0.95);
  }
  return { ...o, s, n0, k: Math.max(span, 60) / s, fakeTracking: `SD-${o.orderId.slice(0, 8).toUpperCase()}` };
}
const shiftedEpoch = (p, t) => Math.min(p.n0 + Math.min(Math.max(t - p.t0, 0), p.s) * p.k, Date.now() / 1000 - 60);

// ── Main ─────────────────────────────────────────────────────────────────────
function main() {
  const now = Date.now() / 1000;

  // Seeded accounts and their orders.
  const users = rows(psql('iam', `SELECT public_id, role FROM user_accounts WHERE email LIKE '%@example.com';`));
  const customers = new Set(users.filter(([, role]) => role === 'CUSTOMER').map(([id]) => id));
  if (!customers.size) throw new Error('No seeded customers (@example.com) found. Run the seeder first.');

  const orders = rows(psql('order', `
    SELECT o.order_id, o.tracking_number, o.status, coalesce(o.order_speed, 'STANDARD'), o.customer_id,
           extract(epoch FROM o.created_at),
           extract(epoch FROM coalesce((SELECT max(h.occurred_at) FROM order_status_history h WHERE h.order_id = o.order_id), o.created_at))
    FROM customer_orders o;`))
    .filter(([, , , , customerId]) => customers.has(customerId))
    .map(([orderId, tracking, status, speed, customerId, t0, last]) => ({
      orderId, tracking, status, speed, customerId, t0: Number(t0), last: Number(last),
    }));
  if (!orders.length) throw new Error('No seeded orders found.');

  const oldest = Math.min(...orders.map((o) => o.t0));
  if (now - oldest > 3 * 86400 && !FORCE) {
    throw new Error('Seeded orders are already spread over several days — the time-shift has run before. Use --force to run it again.');
  }

  const plan = orders.map((o) => planOrder(o, now));
  const byOrder = new Map(plan.map((p) => [p.orderId, p]));

  // Report the planned spread.
  const perDay = {};
  for (const p of plan) {
    const d = new Date(p.n0 * 1000 + COLOMBO_OFFSET_MS).toISOString().slice(0, 10);
    perDay[d] = (perDay[d] || 0) + 1;
  }
  console.log(`Time-shift: ${plan.length} seeded orders over ${Object.keys(perDay).length} days`);
  for (const [d, n] of Object.entries(perDay).sort()) console.log(`   ${d}  ${'#'.repeat(n)} ${n}`);
  if (DRY_RUN) { console.log('Dry run: nothing changed.'); return; }

  const header = `${MAPPING_FN}\n${mappingTable(plan)}`;

  // Order service.
  let cols = columnsOf('order');
  psql('order', `BEGIN;\n${header}${shiftUpdate(cols, 'customer_orders', ['created_at', 'updated_at', 'cancelled_at'])}`
    + `${shiftUpdate(cols, 'order_status_history', ['occurred_at', 'created_at'])}COMMIT;`);
  console.log('   order: done');

  // Dispatch.
  cols = columnsOf('dispatch');
  psql('dispatch', `BEGIN;\n${header}${shiftUpdate(cols, 'dispatch_assignments', ['created_at', 'updated_at', 'assigned_at'])}COMMIT;`);
  console.log('   dispatch: done');

  // Delivery (photos, audits and breadcrumbs follow their delivery session's order).
  cols = columnsOf('delivery');
  const viaSession = { from: 'delivery_sessions d JOIN shift s ON s.order_id = d.order_id', where: 'x.delivery_id = d.delivery_id' };
  psql('delivery', `BEGIN;\n${header}`
    + shiftUpdate(cols, 'delivery_sessions', ['created_at', 'updated_at', 'pickup_at', 'delivered_at'])
    + shiftUpdate(cols, 'state_transition_audits', ['created_at'])
    + shiftUpdate(cols, 'proof_of_delivery', ['created_at', 'verified_at'], viaSession)
    + shiftUpdate(cols, 'tracking_breadcrumbs', ['recorded_at'], viaSession)
    + 'COMMIT;');
  console.log('   delivery: done');

  // Notifications: move each one to the time of the status it announces. The order
  // update above already shifted order_status_history, so these times are the new ones.
  // 'END' (the order's last event) is the fallback for notifications whose status
  // cannot be recognised from their text.
  const history = rows(psql('order', `SELECT order_id, status, extract(epoch FROM occurred_at) FROM order_status_history
    WHERE order_id IN (${plan.map((p) => sqlText(p.orderId)).join(',')});`));
  const statusTimes = plan.map((p) => [p, 'END', shiftedEpoch(p, p.last)]);
  for (const [orderId, status, at] of history) {
    const p = byOrder.get(orderId);
    if (p) statusTimes.push([p, status, Number(at)]);
  }
  cols = columnsOf('notify');
  const st = statusTimes.map(([p, status, at]) => `(${sqlText(p.tracking)}, ${sqlText(p.fakeTracking)}, ${sqlText(status)}, ${num(Math.min(at, now - 60))})`).join(',\n');
  let notifySql = `BEGIN;\nCREATE TEMP TABLE st (tn text, tf text, status text, at float8);\nINSERT INTO st VALUES\n${st};\n${MAPPING_FN}\n`;
  if (cols.in_app_notifications) {
    notifySql += `UPDATE in_app_notifications n SET created_at = to_timestamp(st.at + 30 + (n.id % 90))
      FROM st WHERE st.status = 'END' AND n.tracking_number IN (st.tn, st.tf);
    UPDATE in_app_notifications n SET created_at = to_timestamp(st.at + 30 + (n.id % 90))
      FROM st WHERE n.tracking_number IN (st.tn, st.tf)
        AND st.status = pg_temp.status_of(coalesce(n.title, '') || ' ' || coalesce(n.body, ''));
    UPDATE in_app_notifications n SET read = true
      WHERE n.created_at < now() - interval '2 days' AND n.tracking_number IN (SELECT tn FROM st UNION SELECT tf FROM st);\n`;
  }
  if (cols.notification_dispatch_logs) {
    const logCols = ['created_at', 'updated_at', 'sent_at'].filter((c) => cols.notification_dispatch_logs.has(c));
    const text = ['template_code', 'subject'].filter((c) => cols.notification_dispatch_logs.has(c))
      .map((c) => `coalesce(l.${c}, '')`).join(` || ' ' || `) || `''`;
    const sets = logCols.map((c) => `${c} = to_timestamp(st.at + 20)`).join(', ');
    notifySql += `UPDATE notification_dispatch_logs l SET ${sets} FROM st WHERE st.status = 'END' AND l.reference_id IN (st.tn, st.tf);
    UPDATE notification_dispatch_logs l SET ${sets} FROM st WHERE l.reference_id IN (st.tn, st.tf) AND st.status = pg_temp.status_of(${text});\n`;
  }
  psql('notify', `${notifySql}COMMIT;`);
  console.log('   notify: done');

  // Analytics: shift events and projections, then rebuild the totals with analytics' own rules
  // (created → creation date; delivered/failed → outcome date; active → assignment date; Colombo time).
  cols = columnsOf('analytics');
  psql('analytics', `BEGIN;\n${header}`
    + shiftUpdate(cols, 'processed_telemetry_events', ['occurred_at', 'processed_at'])
    + shiftUpdate(cols, 'analytics_order_projection', ['created_at', 'status_at', 'assigned_at', 'outcome_at'])
    + `UPDATE analytics_couriers c SET last_assignment_at = m.last FROM (
         SELECT courier_id, max(assigned_at) AS last FROM analytics_order_projection WHERE courier_id IS NOT NULL GROUP BY courier_id
       ) m WHERE c.courier_id = m.courier_id;
    DELETE FROM daily_delivery_stats;
    INSERT INTO daily_delivery_stats (report_date, created)
      SELECT (created_at AT TIME ZONE 'Asia/Colombo')::date, count(*) FROM analytics_order_projection
      WHERE created_at IS NOT NULL GROUP BY 1;
    INSERT INTO daily_delivery_stats AS d (report_date, delivered, failed)
      SELECT (outcome_at AT TIME ZONE 'Asia/Colombo')::date,
             count(*) FILTER (WHERE outcome = 'DELIVERED'), count(*) FILTER (WHERE outcome = 'FAILED')
      FROM analytics_order_projection
      WHERE conflict_reason IS NULL AND outcome IN ('DELIVERED', 'FAILED') AND outcome_at IS NOT NULL GROUP BY 1
      ON CONFLICT (report_date) DO UPDATE SET delivered = EXCLUDED.delivered, failed = EXCLUDED.failed;
    DELETE FROM courier_daily_stats;
    INSERT INTO courier_daily_stats (courier_id, report_date, delivered, failed)
      SELECT outcome_courier_id, (outcome_at AT TIME ZONE 'Asia/Colombo')::date,
             count(*) FILTER (WHERE outcome = 'DELIVERED'), count(*) FILTER (WHERE outcome = 'FAILED')
      FROM analytics_order_projection
      WHERE conflict_reason IS NULL AND outcome IN ('DELIVERED', 'FAILED') AND outcome_courier_id IS NOT NULL GROUP BY 1, 2;
    INSERT INTO courier_daily_stats AS c (courier_id, report_date, active)
      SELECT courier_id, (assigned_at AT TIME ZONE 'Asia/Colombo')::date, count(*)
      FROM analytics_order_projection
      WHERE conflict_reason IS NULL AND courier_id IS NOT NULL AND assigned_at IS NOT NULL
        AND (current_status IS NULL OR current_status NOT IN ('FAILED', 'DELIVERED', 'CANCELLED', 'RETURNED_TO_SENDER'))
      GROUP BY 1, 2
      ON CONFLICT (courier_id, report_date) DO UPDATE SET active = EXCLUDED.active;
    COMMIT;`);
  console.log('   analytics: done (daily and courier totals rebuilt)');

  // Accounts: customers signed up 1–20 days before their first order; staff ~5–6 weeks ago.
  const firstOrder = {};
  for (const p of plan) firstOrder[p.customerId] = Math.min(firstOrder[p.customerId] ?? Infinity, p.n0);
  const signups = users.map(([id, role]) => {
    const r = rng(`signup:${id}`);
    const at = role === 'CUSTOMER' && firstOrder[id] !== undefined
      ? firstOrder[id] - r.between(1, 20) * 86400
      : now - r.between(35, 45) * 86400;
    return `(${sqlText(id)}::uuid, ${num(at)})`;
  });
  cols = columnsOf('iam');
  const userCols = ['created_at', 'updated_at'].filter((c) => cols.user_accounts?.has(c));
  psql('iam', `BEGIN;\nCREATE TEMP TABLE signup (id uuid PRIMARY KEY, at float8);\nINSERT INTO signup VALUES\n${signups.join(',\n')};\n`
    + `UPDATE user_accounts u SET ${userCols.map((c) => `${c} = to_timestamp(s.at)`).join(', ')} FROM signup s WHERE u.public_id = s.id;\nCOMMIT;`);
  console.log('   iam: done (sign-up dates)');

  const summary = rows(psql('analytics', `SELECT count(*), min(report_date), max(report_date), sum(created), sum(delivered), sum(failed) FROM daily_delivery_stats;`))[0];
  console.log(`\nAnalytics now covers ${summary[0]} days (${summary[1]} → ${summary[2]}): created ${summary[3]}, delivered ${summary[4]}, failed ${summary[5]}.`);
}

try { main(); } catch (e) { console.error(`\nTime-shift failed: ${e.message}`); process.exit(1); }
