#!/usr/bin/env node
// SwiftDrop demo-data time-shift.
//
// The seeder drives every order through the real APIs, so every event is stamped
// "now": the whole history lands on one day, and a courier's pickup → in transit →
// out for delivery → delivered happen seconds apart. This script runs ONCE, after
// the last seed stage, and rebuilds each seeded order's timeline over the past
// ~30 days with realistic step durations:
//
//   booked → courier assigned      10 min – 3 h   (dispatchers work 07:00–21:00)
//   reassigned                     20 min – 2 h later
//   assigned → picked up           30 min – 4 h
//   picked up → in transit         15 – 90 min    (to the hub)
//   in transit → out for delivery  express 1–4 h; same district 2–6 h;
//                                  other district: overnight, out next morning 08:00–10:00
//   out for delivery → delivered   30 min – 5 h   (→ failed: 1 – 6 h)
//   booked → cancelled             10 min – 4 h
//
// Courier steps happen 08:00–19:30 Colombo time (later ones roll to the next morning).
// Finished orders are booked 0–29 days ago (more in recent days, so charts show
// growth); orders still in progress end a realistic time before now.
//
// The order's status history gives the anchors. Every other timestamp of that
// order, in every database, is placed between the new anchor times according to
// where it fell between the old ones, so all services stay consistent. Then
// notifications move to the time of the status they announce, analytics' totals
// are rebuilt with its own rules, and seeded accounts get sign-up dates.
//
// Only seeded data is touched: orders whose customer has an @example.com account.
// It edits demo data directly in six service databases (owned by other members);
// it never changes their schema or code. Runs through `kubectl exec` into the
// PostgreSQL pods, so it works on the local cluster and on AKS alike.
//
// Usage:  node scripts/seed/timeshift.mjs [--dry-run] [--force]
//   --dry-run  print the planned spread and one sample timeline, change nothing
//   --force    run even though the data already looks shifted (safe: the new
//              timeline depends only on the ORDER of each order's events)

import { execFileSync } from 'node:child_process';

const NS = process.env.SEED_DATA_NAMESPACE || 'swiftdrop-data';
const DRY_RUN = process.argv.includes('--dry-run');
const FORCE = process.argv.includes('--force');
const HISTORY_DAYS = 30;
const COLOMBO = 5.5 * 3600; // UTC+05:30, no daylight saving
const MIN = 60;
const HOUR = 3600;
const DAY = 86400;

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
const sqlArray = (xs) => `ARRAY[${xs.map(num).join(',')}]::float8[]`;

function columnsOf(db) {
  const out = psql(db, `SELECT table_name, column_name FROM information_schema.columns WHERE table_schema = 'public';`);
  const map = {};
  for (const [t, c] of rows(out)) (map[t] ||= new Set()).add(c);
  return map;
}

// Colombo-local clock helpers (epoch seconds).
const colomboDayStart = (t) => Math.floor((t + COLOMBO) / DAY) * DAY - COLOMBO;
const colomboHour = (t) => (t - colomboDayStart(t)) / HOUR;
/** Move t into the [open, close) window of its Colombo day, or the next morning. */
function withinHours(t, open, close, r) {
  const h = colomboHour(t);
  if (h >= open && h < close) return t;
  const day = h < open ? colomboDayStart(t) : colomboDayStart(t) + DAY;
  return day + open * HOUR + r.between(0, 75) * MIN;
}

// ── Timeline planning ────────────────────────────────────────────────────────
const COURIER_STEPS = new Set(['PICKED_UP', 'IN_TRANSIT', 'OUT_FOR_DELIVERY', 'DELIVERED', 'FAILED']);

/** New epoch for `status`, given the previous status and its new time. */
function nextStep(prevStatus, prevAt, status, o, r) {
  let t;
  if (status === 'PICKUP_ASSIGNED') {
    t = prevAt + (prevStatus === 'PICKUP_ASSIGNED' ? r.between(20, 120) : r.between(10, 180)) * MIN;
    return withinHours(t, 7, 21, r);
  }
  if (status === 'CANCELLED') return prevAt + r.between(10, 240) * MIN;
  if (status === 'OUT_FOR_DELIVERY' && prevStatus === 'IN_TRANSIT'
      && o.speed !== 'EXPRESS' && o.pickupDistrict !== o.deliveryDistrict) {
    // Line-haul to the destination hub overnight; out on the morning round.
    return colomboDayStart(prevAt) + DAY + r.between(8, 10) * HOUR;
  }
  const minutes = {
    'PICKUP_ASSIGNED>PICKED_UP': [30, 240],
    'PICKED_UP>IN_TRANSIT': [15, 90],
    'IN_TRANSIT>OUT_FOR_DELIVERY': o.speed === 'EXPRESS' ? [60, 240] : [120, 360],
    'OUT_FOR_DELIVERY>DELIVERED': [30, 300],
    'OUT_FOR_DELIVERY>FAILED': [60, 360],
    'IN_TRANSIT>FAILED': [120, 600],
    'PICKED_UP>FAILED': [60, 300],
  }[`${prevStatus}>${status}`] || [10, 120];
  t = prevAt + r.between(minutes[0], minutes[1]) * MIN;
  return COURIER_STEPS.has(status) ? withinHours(t, 8, 19.5, r) : t;
}

/** Build new anchor times for the order's status sequence, starting at `start`. */
function buildTimeline(o, start, seed) {
  const r = rng(seed);
  const times = [start];
  for (let i = 1; i < o.statuses.length; i++) {
    times.push(nextStep(o.statuses[i - 1], times[i - 1], o.statuses[i], o, r));
  }
  return times;
}

// The most hours an unfinished order's latest step can realistically be in the past
// (e.g. a parcel is not "out for delivery" for two days).
const MAX_AGE_HOURS = {
  CREATED: 30, PICKUP_ASSIGNED: 14, PICKED_UP: 12, IN_TRANSIT: 18, OUT_FOR_DELIVERY: 16,
};
const bookingHour = (t) => { const h = colomboHour(t); return h >= 7 && h < 23; };

function planOrder(o, now) {
  const r = rng(`timeshift:${o.orderId}`);
  const finished = ['DELIVERED', 'FAILED', 'CANCELLED'].includes(o.status);
  let start; let times;
  if (finished) {
    let days = Math.floor(HISTORY_DAYS * (1 - Math.sqrt(r.next()))); // 0–29, recent-heavy
    for (let attempt = 0; attempt < 30; attempt++, days++) {
      start = colomboDayStart(now) - days * DAY + r.between(8, 21) * HOUR; // customers book 08:00–21:00
      times = buildTimeline(o, start, `steps:${o.orderId}`);
      if (times[times.length - 1] < now - HOUR) break; // must be over well before now
    }
  } else {
    // Try booking times going back from now (20-minute steps, random starting point);
    // keep the first whose timeline ends recently enough for its status, never in the
    // future, with the booking itself at a believable hour. Steps keep their working
    // hours because the timeline is always built forwards.
    const last = o.statuses[o.statuses.length - 1];
    const maxAge = (MAX_AGE_HOURS[last] || 12) * HOUR;
    let candidate = now - r.between(0, 0.5) * maxAge;
    for (let tries = 0; tries < 600; tries++, candidate -= 20 * MIN) {
      if (!bookingHour(candidate)) continue;
      const t = buildTimeline(o, candidate, `steps:${o.orderId}`);
      const end = t[t.length - 1];
      if (end <= now - 10 * MIN && end >= now - maxAge) { start = candidate; times = t; break; }
      if (end < now - maxAge && tries > 0) break; // going further back only makes it older
    }
    if (!times) { // no fit (very unusual): fall back to the most recent timeline that has ended
      for (candidate = now - HOUR; ; candidate -= HOUR) {
        times = buildTimeline(o, candidate, `steps:${o.orderId}`);
        if (times[times.length - 1] <= now - 10 * MIN && bookingHour(candidate)) break;
      }
    }
  }
  return {
    ...o,
    newTimes: times,
    fakeTracking: `SD-${o.orderId.slice(0, 8).toUpperCase()}`,
  };
}

// Shared SQL. remap(t, old, new): t's position between the order's old anchor times,
// carried over to the new anchor times (before the first → first, after the last → last).
const SQL_FUNCTIONS = `
CREATE FUNCTION pg_temp.remap(col timestamptz, o float8[], n float8[])
RETURNS timestamptz LANGUAGE plpgsql STABLE AS $$
DECLARE t float8; len int := array_length(o, 1); r float8;
BEGIN
  IF col IS NULL THEN RETURN NULL; END IF;
  t := extract(epoch FROM col);
  IF t <= o[1] THEN r := n[1];
  ELSIF t >= o[len] THEN r := n[len];
  ELSE
    FOR i IN 1 .. len - 1 LOOP
      IF t <= o[i + 1] THEN
        IF o[i + 1] = o[i] THEN r := n[i + 1];
        ELSE r := n[i] + (t - o[i]) * (n[i + 1] - n[i]) / (o[i + 1] - o[i]); END IF;
        EXIT;
      END IF;
    END LOOP;
  END IF;
  RETURN to_timestamp(LEAST(r, extract(epoch FROM now()) - 60));
END $$;
CREATE FUNCTION pg_temp.status_of(txt text) RETURNS text LANGUAGE sql IMMUTABLE AS $$
  SELECT CASE
    WHEN txt ~* 'out.for.delivery' THEN 'OUT_FOR_DELIVERY'
    WHEN txt ~* 'picked' THEN 'PICKED_UP'
    WHEN txt ~* 'fail|unsuccessful|could not' THEN 'FAILED'
    WHEN txt ~* 'delivered' THEN 'DELIVERED'
    WHEN txt ~* 'cancel' THEN 'CANCELLED'
    WHEN txt ~* 'assign' THEN 'PICKUP_ASSIGNED'
    ELSE 'CREATED' END
$$;`;

function mappingTable(plan) {
  const values = plan.map((p) => `(${sqlText(p.orderId)}::uuid, ${sqlArray(p.oldTimes)}, ${sqlArray(p.newTimes)})`).join(',\n');
  return `CREATE TEMP TABLE shift (order_id uuid PRIMARY KEY, o float8[], n float8[]);\nINSERT INTO shift VALUES\n${values};\n`;
}

// New time per (order, status): first CREATED, last occurrence of every other status,
// plus END = the order's latest step. Rows that represent a specific step are set from
// this; positional remap() is only the fallback for other columns (e.g. updated_at).
function stepTable(plan) {
  const values = [];
  for (const p of plan) {
    const at = {};
    p.statuses.forEach((st, i) => { if (st !== 'CREATED' || at.CREATED === undefined) at[st] = p.newTimes[i]; });
    at.END = p.newTimes[p.newTimes.length - 1];
    for (const [st, t] of Object.entries(at)) values.push(`(${sqlText(p.orderId)}::uuid, ${sqlText(st)}, ${num(t)})`);
  }
  return `CREATE TEMP TABLE step (order_id uuid, status text, at float8, PRIMARY KEY (order_id, status));\nINSERT INTO step VALUES\n${values.join(',\n')};\n`;
}
/** UPDATE table x SET col = <step time + offset> for rows whose step is `statusSql`. */
function stepUpdate(cols, table, col, statusSql, { offset = 0, onlyIfSet = false, from = 'step s', join = 's.order_id = x.order_id' } = {}) {
  if (!cols[table]?.has(col)) return '';
  return `UPDATE ${table} x SET ${col} = to_timestamp(s.at + ${offset}) FROM ${from} WHERE ${join} AND s.status = ${statusSql}${onlyIfSet ? ` AND x.${col} IS NOT NULL` : ''};\n`;
}

// UPDATE every listed timestamp column that exists in this database.
function shiftUpdate(cols, table, columns, { from = 'shift s', where = 'x.order_id = s.order_id' } = {}) {
  const present = columns.filter((c) => cols[table]?.has(c));
  if (!present.length) return '';
  const sets = present.map((c) => `${c} = pg_temp.remap(x.${c}, s.o, s.n)`).join(', ');
  return `UPDATE ${table} x SET ${sets} FROM ${from} WHERE ${where};\n`;
}

const fmt = (t) => new Date((t + COLOMBO) * 1000).toISOString().slice(5, 16).replace('T', ' ');

// ── Main ─────────────────────────────────────────────────────────────────────
function main() {
  const now = Date.now() / 1000;

  // Seeded accounts and their orders, with each order's status history in order.
  const users = rows(psql('iam', `SELECT public_id, role FROM user_accounts WHERE email LIKE '%@example.com';`));
  const customers = new Set(users.filter(([, role]) => role === 'CUSTOMER').map(([id]) => id));
  if (!customers.size) throw new Error('No seeded customers (@example.com) found. Run the seeder first.');

  const orders = new Map();
  for (const [orderId, tracking, status, speed, customerId, from, to, created] of rows(psql('order', `
      SELECT order_id, tracking_number, status, coalesce(order_speed, 'STANDARD'), customer_id,
             pickup_district, delivery_district, extract(epoch FROM created_at) FROM customer_orders;`))) {
    if (customers.has(customerId)) {
      orders.set(orderId, {
        orderId, tracking, status, speed, customerId, pickupDistrict: from, deliveryDistrict: to,
        statuses: [], oldTimes: [], created: Number(created),
      });
    }
  }
  if (!orders.size) throw new Error('No seeded orders found.');
  for (const [orderId, status, at] of rows(psql('order',
    'SELECT order_id, status, extract(epoch FROM occurred_at) FROM order_status_history ORDER BY order_id, occurred_at, id;'))) {
    const o = orders.get(orderId);
    if (o) { o.statuses.push(status); o.oldTimes.push(Number(at)); }
  }
  for (const o of orders.values()) {
    if (!o.statuses.length) { o.statuses.push('CREATED'); o.oldTimes.push(o.created); }
    for (let i = 1; i < o.oldTimes.length; i++) o.oldTimes[i] = Math.max(o.oldTimes[i], o.oldTimes[i - 1]);
  }

  const oldest = Math.min(...[...orders.values()].map((o) => o.oldTimes[0]));
  if (now - oldest > 3 * DAY && !FORCE) {
    throw new Error('Seeded orders are already spread over several days: the time-shift has run before. Use --force to run it again.');
  }

  const plan = [...orders.values()].map((o) => planOrder(o, now));

  // Report the planned spread and a sample delivered timeline.
  const perDay = {};
  for (const p of plan) { const d = fmt(p.newTimes[0]).slice(0, 5); perDay[d] = (perDay[d] || 0) + 1; }
  console.log(`Time-shift: ${plan.length} seeded orders over ${Object.keys(perDay).length} days`);
  for (const [d, n] of Object.entries(perDay).sort()) console.log(`   ${d}  ${'#'.repeat(n)} ${n}`);
  const sample = plan.find((p) => p.status === 'DELIVERED' && p.pickupDistrict !== p.deliveryDistrict);
  if (sample) {
    console.log(`   sample (${sample.speed}, ${sample.pickupDistrict} → ${sample.deliveryDistrict}):`);
    sample.statuses.forEach((s, i) => console.log(`     ${fmt(sample.newTimes[i])}  ${s}`));
  }
  if (DRY_RUN) { console.log('Dry run: nothing changed.'); return; }

  const header = `${SQL_FUNCTIONS}\n${mappingTable(plan)}${stepTable(plan)}`;

  // Order service.
  let cols = columnsOf('order');
  psql('order', `BEGIN;\n${header}${shiftUpdate(cols, 'customer_orders', ['created_at', 'updated_at', 'cancelled_at'])}`
    + `${shiftUpdate(cols, 'order_status_history', ['occurred_at', 'created_at'])}COMMIT;`);
  console.log('   order: done');

  // Dispatch.
  cols = columnsOf('dispatch');
  psql('dispatch', `BEGIN;\n${header}${shiftUpdate(cols, 'dispatch_assignments', ['created_at', 'updated_at', 'assigned_at'])}`
    + stepUpdate(cols, 'dispatch_assignments', 'created_at', "'CREATED'", { offset: 3 })
    + stepUpdate(cols, 'dispatch_assignments', 'assigned_at', "'PICKUP_ASSIGNED'", { onlyIfSet: true })
    + 'COMMIT;');
  console.log('   dispatch: done');

  // Delivery (photos, audits and breadcrumbs follow their delivery session's order).
  cols = columnsOf('delivery');
  const viaSession = { from: 'delivery_sessions d JOIN shift s ON s.order_id = d.order_id', where: 'x.delivery_id = d.delivery_id' };
  psql('delivery', `BEGIN;\n${header}`
    + shiftUpdate(cols, 'delivery_sessions', ['created_at', 'updated_at', 'pickup_at', 'delivered_at'])
    + shiftUpdate(cols, 'state_transition_audits', ['created_at'])
    + shiftUpdate(cols, 'proof_of_delivery', ['created_at', 'verified_at'], viaSession)
    + shiftUpdate(cols, 'tracking_breadcrumbs', ['recorded_at'], viaSession)
    + stepUpdate(cols, 'state_transition_audits', 'created_at',
      "(CASE x.to_state WHEN 'ASSIGNED' THEN 'PICKUP_ASSIGNED' WHEN 'CREATED' THEN 'PICKUP_ASSIGNED' ELSE x.to_state END)")
    + stepUpdate(cols, 'delivery_sessions', 'created_at', "'PICKUP_ASSIGNED'")
    + stepUpdate(cols, 'delivery_sessions', 'pickup_at', "'PICKED_UP'", { onlyIfSet: true })
    + stepUpdate(cols, 'delivery_sessions', 'delivered_at', "'DELIVERED'", { onlyIfSet: true })
    + stepUpdate(cols, 'delivery_sessions', 'updated_at', "'END'")
    + stepUpdate(cols, 'proof_of_delivery', 'created_at', "'DELIVERED'",
      { offset: -240, from: 'delivery_sessions d JOIN step s ON s.order_id = d.order_id', join: 'x.delivery_id = d.delivery_id' })
    + stepUpdate(cols, 'proof_of_delivery', 'verified_at', "'DELIVERED'",
      { offset: -60, onlyIfSet: true, from: 'delivery_sessions d JOIN step s ON s.order_id = d.order_id', join: 'x.delivery_id = d.delivery_id' })
    + 'COMMIT;');
  console.log('   delivery: done');

  // Notifications: a minute or two after the status they announce; the order's last
  // step is the fallback when the status cannot be recognised from the text.
  const st = [];
  for (const p of plan) {
    st.push([p, 'END', p.newTimes[p.newTimes.length - 1]]);
    p.statuses.forEach((s, i) => st.push([p, s, p.newTimes[i]]));
  }
  cols = columnsOf('notify');
  let notifySql = `BEGIN;\nCREATE TEMP TABLE st (tn text, tf text, status text, at float8);\nINSERT INTO st VALUES\n`
    + st.map(([p, status, at]) => `(${sqlText(p.tracking)}, ${sqlText(p.fakeTracking)}, ${sqlText(status)}, ${num(Math.min(at, now - 180))})`).join(',\n')
    + `;\n${SQL_FUNCTIONS}\n`;
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
    + stepUpdate(cols, 'processed_telemetry_events', 'occurred_at',
      "(CASE x.event_type WHEN 'order.created' THEN 'CREATED' WHEN 'dispatch.courier.assigned' THEN 'PICKUP_ASSIGNED' ELSE coalesce(x.payload->>'newPublicStatus', 'END') END)")
    + stepUpdate(cols, 'processed_telemetry_events', 'processed_at',
      "(CASE x.event_type WHEN 'order.created' THEN 'CREATED' WHEN 'dispatch.courier.assigned' THEN 'PICKUP_ASSIGNED' ELSE coalesce(x.payload->>'newPublicStatus', 'END') END)", { offset: 2 })
    + stepUpdate(cols, 'analytics_order_projection', 'created_at', "'CREATED'", { onlyIfSet: true })
    + stepUpdate(cols, 'analytics_order_projection', 'assigned_at', "'PICKUP_ASSIGNED'", { onlyIfSet: true })
    + stepUpdate(cols, 'analytics_order_projection', 'status_at', 'x.current_status', { onlyIfSet: true })
    + stepUpdate(cols, 'analytics_order_projection', 'outcome_at', 'x.outcome', { onlyIfSet: true })
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
  for (const p of plan) firstOrder[p.customerId] = Math.min(firstOrder[p.customerId] ?? Infinity, p.newTimes[0]);
  const signups = users.map(([id, role]) => {
    const r = rng(`signup:${id}`);
    const at = role === 'CUSTOMER' && firstOrder[id] !== undefined
      ? firstOrder[id] - r.between(1, 20) * DAY
      : now - r.between(35, 45) * DAY;
    return `(${sqlText(id)}::uuid, ${num(at)})`;
  });
  cols = columnsOf('iam');
  const userCols = ['created_at', 'updated_at'].filter((c) => cols.user_accounts?.has(c));
  psql('iam', `BEGIN;\nCREATE TEMP TABLE signup (id uuid PRIMARY KEY, at float8);\nINSERT INTO signup VALUES\n${signups.join(',\n')};\n`
    + `UPDATE user_accounts u SET ${userCols.map((c) => `${c} = to_timestamp(s.at)`).join(', ')} FROM signup s WHERE u.public_id = s.id;\nCOMMIT;`);
  console.log('   iam: done (sign-up dates)');

  const summary = rows(psql('analytics', 'SELECT count(*), min(report_date), max(report_date), sum(created), sum(delivered), sum(failed) FROM daily_delivery_stats;'))[0];
  console.log(`\nAnalytics now covers ${summary[0]} days (${summary[1]} → ${summary[2]}): created ${summary[3]}, delivered ${summary[4]}, failed ${summary[5]}.`);
}

try { main(); } catch (e) { console.error(`\nTime-shift failed: ${e.message}`); process.exit(1); }
