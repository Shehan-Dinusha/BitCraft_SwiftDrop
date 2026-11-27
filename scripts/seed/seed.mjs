#!/usr/bin/env node
// SwiftDrop demo-data seeder.
//
// Drives the system through its public APIs (via the gateway), exactly like real users:
// the admin provisions staff, customers and merchant owners, customers book parcels,
// merchants are approved, send B2B orders with an API key and mark their parcels ready,
// dispatchers assign them, couriers collect the fee in cash, hand parcels in at the hub,
// hubs send them on and accept them, couriers load and deliver them with photo proof, and
// the cash is handed over, counted, banked and confirmed. Every downstream service
// (dispatch, delivery, notifications, analytics) fills itself from the events this
// produces — no SQL, no cross-database IDs to keep in sync.
//
// Shifts start only on a working day (Monday to Saturday, not a holiday) from 07:30, and
// pickups are given out only until 15:00 Sri Lanka time, so run shifts/assign/deliver in
// that window.
//
// Usage:  node scripts/seed/seed.mjs <stage> [<stage> ...]
//   stages: users orders merchants ready shifts cancel assign deliver cash notifications summary
//           all  (every stage in order — for a cluster that runs all services at once)
//           vehicles  (not part of all: registers vehicles for couriers seeded before they existed)
//
// Environment:
//   SEED_API              gateway base URL      (default http://127.0.0.1:18080)
//   SEED_PASSWORD         password for every seeded account (required; ≥8 chars, a letter and a digit)
//   SEED_ADMIN_USERNAME   bootstrap admin        (default admin)
//   SEED_ADMIN_PASSWORD   bootstrap admin password (required by users/summary)
//   SEED_CUSTOMERS        number of customers    (default 40)
//   SEED_CONCURRENCY      parallel requests      (default 3; run-local.sh uses 2)
//
// Every stage is safe to re-run: existing users are skipped, customers are topped up to
// their planned order count, and parcels only move forward to their planned state.

import fs from 'node:fs';
import path from 'node:path';
import readline from 'node:readline/promises';
import zlib from 'node:zlib';
import { fileURLToPath } from 'node:url';
import {
  DISTRICTS, STREETS, NAMES, ITEMS, NOTES, CANCEL_REASONS, FAILURES, VEHICLES, LARGE_VEHICLES, MERCHANTS,
} from './data.mjs';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const API = (process.env.SEED_API || 'http://127.0.0.1:18080').replace(/\/$/, '');
const PASSWORD = process.env.SEED_PASSWORD || '';
const ADMIN_USER = process.env.SEED_ADMIN_USERNAME || 'admin';
const ADMIN_PASSWORD = process.env.SEED_ADMIN_PASSWORD || '';
const CUSTOMER_COUNT = Number(process.env.SEED_CUSTOMERS || 40);
const COURIERS_PER_DISTRICT = 2;
const CONCURRENCY = Math.max(1, Number(process.env.SEED_CONCURRENCY || 3));

// ── Outcome mix (by a stable hash of the order id) ──────────────────────────
//   0– 9  cancelled by the customer before dispatch
//  10–19  left unassigned, so the dispatcher board has work waiting
//  20–99  assigned; it then goes as far as its deliveryPlan() says
// (Mostly finished orders: after timeshift.mjs spreads them over a month, a large
// in-progress share would pile up on the last two days.) A shop's parcels are collected
// in one visit, so they are all assigned once the shop has marked them ready.
const CANCEL_BELOW = 10;
const ASSIGN_FROM = 20;

// ── Small utilities ─────────────────────────────────────────────────────────
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

function hash(str) { // FNV-1a, 32-bit
  let h = 0x811c9dc5;
  for (let i = 0; i < str.length; i++) { h ^= str.charCodeAt(i); h = Math.imul(h, 0x01000193); }
  return h >>> 0;
}

function rng(seed) { // mulberry32 — deterministic, so re-runs plan the same people
  let a = hash(String(seed));
  const next = () => {
    a = (a + 0x6d2b79f5) | 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
  return {
    next,
    int: (min, max) => min + Math.floor(next() * (max - min + 1)),
    between: (lo, hi) => lo + next() * (hi - lo),
    pick: (arr) => arr[Math.floor(next() * arr.length)],
    chance: (p) => next() < p,
    weighted: (pairs) => { // [[value, weight], ...]
      const total = pairs.reduce((s, [, w]) => s + w, 0);
      let x = next() * total;
      for (const [v, w] of pairs) { if ((x -= w) < 0) return v; }
      return pairs[pairs.length - 1][0];
    },
  };
}

async function mapLimit(items, limit, fn) {
  const out = new Array(items.length);
  let i = 0;
  const workers = Array.from({ length: Math.min(limit, items.length) }, async () => {
    while (i < items.length) { const k = i++; out[k] = await fn(items[k], k); }
  });
  await Promise.all(workers);
  return out;
}

const counters = {};
const count = (key, n = 1) => { counters[key] = (counters[key] || 0) + n; };
function report(stage) {
  const parts = Object.entries(counters).map(([k, v]) => `${k}=${v}`);
  console.log(`   ${stage}: ${parts.join('  ') || 'nothing to do'}`);
  for (const k of Object.keys(counters)) delete counters[k];
}

// ── HTTP ────────────────────────────────────────────────────────────────────
function errorText(body) {
  if (!body) return '';
  if (typeof body === 'string') return body.slice(0, 200);
  return body.message || body.error || JSON.stringify(body).slice(0, 200);
}

async function api(method, urlPath, { token, body, form, headers: extra = {}, expect = [200, 201], retries = 6 } = {}) {
  for (let attempt = 0; ; attempt++) {
    let res;
    try {
      const headers = { ...extra };
      if (token) headers.Authorization = `Bearer ${token}`;
      let payload;
      if (form) payload = form;
      else if (body !== undefined) { headers['Content-Type'] = 'application/json'; payload = JSON.stringify(body); }
      res = await fetch(API + urlPath, { method, headers, body: payload });
    } catch (e) {
      if (attempt < retries) { await sleep(2000 * (attempt + 1)); continue; }
      throw new Error(`${method} ${urlPath}: ${e.cause?.code || e.message} (is ${API} reachable?)`);
    }
    const text = await res.text();
    let json;
    try { json = text ? JSON.parse(text) : null; } catch { json = text; }
    // 502/503/504: the service behind the gateway is still starting — wait and retry.
    if ([502, 503, 504].includes(res.status) && attempt < retries) { await sleep(3000 * (attempt + 1)); continue; }
    if (!expect.includes(res.status)) {
      const err = new Error(`${method} ${urlPath} -> ${res.status} ${errorText(json)}`);
      err.status = res.status;
      err.body = json;
      throw err;
    }
    // Unwrap the shared ApiResponse envelope ({ success, message, data }).
    if (json && typeof json === 'object' && 'success' in json && 'data' in json) return json.data;
    return json;
  }
}

// Admin and dispatcher sign-ins need an emailed code when AUTH_MFA_ENABLED=true.
async function completeMfa(username, challengeId) {
  if (!process.stdin.isTTY) {
    throw new Error(`"${username}" needs a second sign-in factor (AUTH_MFA_ENABLED=true). ` +
      'Run the seed in a terminal to type the emailed code, or seed with AUTH_MFA_ENABLED=false.');
  }
  const rl = readline.createInterface({ input: process.stdin, output: process.stdout });
  try {
    const code = (await rl.question(`Sign-in code emailed to ${username}: `)).trim();
    return await api('POST', '/api/v1/auth/login/mfa', { body: { challengeId, code } });
  } finally {
    rl.close();
  }
}

const sessions = new Map();
async function login(username, password = PASSWORD) {
  if (sessions.has(username)) return sessions.get(username);
  let res;
  try {
    res = await api('POST', '/api/v1/auth/login', { body: { username, password }, expect: [200, 202] });
    if (res && res.mfaRequired) res = await completeMfa(username, res.challengeId);
  } catch (e) {
    if (e.status === 401) {
      throw new Error(`Login failed for "${username}". If the account already existed before this seed, its password differs from SEED_PASSWORD.`);
    }
    throw e;
  }
  const s = { token: res.accessToken, userId: res.userId };
  if (!s.token) throw new Error(`Login for ${username} returned no access token`);
  sessions.set(username, s);
  return s;
}

// ── Planning people (deterministic) ─────────────────────────────────────────
const slug = (code) => code.toLowerCase().replace(/_/g, '-');
const titleCase = (code) => code.split('_').map((w) => w[0] + w.slice(1).toLowerCase()).join(' ');

function personName(r, district) {
  const group = r.weighted([['sinhala', district.mix[0]], ['tamil', district.mix[1]], ['muslim', district.mix[2]]]);
  const pool = NAMES[group];
  const gender = r.chance(0.5) ? 'male' : 'female';
  return { first: r.pick(pool[gender]), last: r.pick(pool.last) };
}

const usedPhones = new Set();
function uniquePhone(r) {
  for (;;) {
    const p = `07${r.pick(['0', '1', '2', '4', '5', '6', '7', '8'])}${String(r.int(0, 9999999)).padStart(7, '0')}`;
    if (!usedPhones.has(p)) { usedPhones.add(p); return p; }
  }
}
const anyPhone = (r) => `07${r.pick(['0', '1', '2', '4', '5', '6', '7', '8'])}${String(r.int(0, 9999999)).padStart(7, '0')}`;

function customerAllocation() {
  // At least one customer per district, the rest spread by population weight.
  const alloc = Object.fromEntries(DISTRICTS.map((d) => [d.code, 1]));
  let remaining = Math.max(0, CUSTOMER_COUNT - DISTRICTS.length);
  const total = DISTRICTS.reduce((s, d) => s + d.weight, 0);
  const shares = DISTRICTS.map((d) => ({ code: d.code, exact: (remaining * d.weight) / total }));
  for (const s of shares) { const f = Math.floor(s.exact); alloc[s.code] += f; remaining -= f; }
  shares.sort((a, b) => (b.exact % 1) - (a.exact % 1));
  for (let i = 0; i < remaining; i++) alloc[shares[i % shares.length].code] += 1;
  return alloc;
}

const districtOf = (code) => DISTRICTS.find((d) => d.code === code);

function planPeople() {
  const r = rng('swiftdrop-seed-people-v1');
  const staff = [];
  const customers = [];
  const usernames = new Set();
  for (const d of DISTRICTS) {
    const town = d.towns[0];
    const dn = personName(r, d);
    staff.push({ role: 'DISPATCHER', username: `dispatcher.${slug(d.code)}`, ...dn, district: d, town, phone: uniquePhone(r) });
    for (let i = 1; i <= COURIERS_PER_DISTRICT; i++) {
      const cn = personName(r, d);
      staff.push({
        role: 'COURIER', username: `courier.${slug(d.code)}.${i}`, ...cn, district: d,
        town: d.towns[(i - 1) % d.towns.length], phone: uniquePhone(r),
        // The second courier has room for the boxes a motorcycle cannot take (over 50 cm).
        vehicle: r.weighted(i === 1 ? VEHICLES : LARGE_VEHICLES),
      });
    }
  }
  const alloc = customerAllocation();
  for (const d of DISTRICTS) {
    for (let i = 0; i < alloc[d.code]; i++) {
      const n = personName(r, d);
      let base = `${n.first}.${n.last}`.toLowerCase().replace(/[^a-z.]/g, '');
      let username = base;
      for (let k = 2; usernames.has(username); k++) username = `${base}${k}`;
      usernames.add(username);
      customers.push({
        role: 'CUSTOMER', username, ...n, district: d, town: r.pick(d.towns), phone: uniquePhone(r),
        street: `No. ${r.int(1, 240)}, ${r.pick(STREETS)}`,
      });
    }
  }
  const merchants = MERCHANTS.map((m) => {
    const d = districtOf(m.district);
    const n = personName(r, d);
    return {
      role: 'MERCHANT', username: `merchant.${m.slug}`, ...n, district: d, town: d.towns[0], phone: uniquePhone(r),
      street: `No. ${r.int(1, 240)}, ${r.pick(STREETS)}`, regNo: `PV ${r.int(100000, 999999)}`, plan: m,
    };
  });
  for (const p of [...staff, ...customers, ...merchants]) p.email = `${p.username}@example.com`;
  return { staff, customers, merchants, all: [...staff, ...customers, ...merchants] };
}

const PEOPLE = planPeople();
const couriersIn = (code) => PEOPLE.staff.filter((p) => p.role === 'COURIER' && p.district.code === code);
const dispatcherOf = (code) => PEOPLE.staff.find((p) => p.role === 'DISPATCHER' && p.district.code === code);
const districtByCode = Object.fromEntries(DISTRICTS.map((d) => [d.code, d]));

// ── Stage: users ────────────────────────────────────────────────────────────
async function stageUsers() {
  if (!ADMIN_PASSWORD) throw new Error('SEED_ADMIN_PASSWORD is required for the users stage');
  const admin = await login(ADMIN_USER, ADMIN_PASSWORD);
  const isDuplicate = (e) => [400, 409].includes(e.status) && /already/i.test(errorText(e.body));
  const profile = (p) => ({
    username: p.username, email: p.email, phone: p.phone, firstName: p.first, lastName: p.last,
    password: PASSWORD, role: p.role, district: p.district.code,
  });

  // The admin provisions every account. Self-registered accounts must confirm a code sent
  // by e-mail before they can sign in (email verification), which a script cannot do;
  // admin-provisioned accounts are trusted and verified from the start.
  await mapLimit(PEOPLE.all, CONCURRENCY, async (p) => {
    try {
      await api('POST', '/api/v1/users', { token: admin.token, body: profile(p) });
      count(`${p.role.toLowerCase()}s created`);
    } catch (e) {
      if (!isDuplicate(e)) throw e;
      count('already existed');
    }
  });
  report('users');
}

// ── Stage: orders ───────────────────────────────────────────────────────────
const PACKAGES = [
  // [lengthCm, widthCm, heightCm, minKg, maxKg]
  [32, 23, 2, 0.1, 0.5],   // envelope / documents
  [25, 20, 10, 0.5, 2],    // small box
  [40, 30, 20, 1.5, 6],    // medium box
  [60, 40, 40, 5, 18],     // large box
];

function orderBody(customer, r, k) {
  const pickup = customer.district;
  const toOther = r.chance(0.55);
  const dest = toOther
    ? districtByCode[r.weighted(DISTRICTS.filter((d) => d.code !== pickup.code).map((d) => [d.code, d.weight]))]
    : pickup;
  const [destCity, destPostal] = r.pick(dest.towns);
  const recipient = personName(r, dest);
  const [l, w, h, minKg, maxKg] = r.weighted([[PACKAGES[0], 25], [PACKAGES[1], 35], [PACKAGES[2], 28], [PACKAGES[3], 12]]);
  const jitter = (v) => Math.round(v * (0.85 + r.next() * 0.3) * 10) / 10;
  const declared = r.int(10, 300) * 100;
  return {
    pickupAddressLine1: customer.street,
    pickupCity: customer.town[0],
    pickupDistrict: pickup.code,
    pickupPostalCode: customer.town[1],
    pickupContactName: `${customer.first} ${customer.last}`,
    pickupContactPhone: customer.phone,
    deliveryAddressLine1: `No. ${r.int(1, 320)}, ${r.pick(STREETS)}`,
    deliveryCity: destCity,
    deliveryDistrict: dest.code,
    deliveryPostalCode: destPostal,
    recipientName: `${recipient.first} ${recipient.last}`,
    recipientPhone: anyPhone(r),
    recipientEmail: `${recipient.first}.${recipient.last}.${k}`.toLowerCase().replace(/[^a-z0-9.]/g, '') + '@example.com',
    lengthCm: jitter(l), widthCm: jitter(w), heightCm: jitter(h),
    actualWeightKg: Math.round((minKg + r.next() * (maxKg - minKg)) * 100) / 100,
    speed: r.chance(0.25) ? 'EXPRESS' : 'STANDARD',
    itemDescription: r.pick(ITEMS),
    customerNotes: r.chance(0.5) ? r.pick(NOTES) : undefined,
    declaredValue: declared,
    prohibitedItemsDeclared: true,
    // One per booking, so a retried request returns the same order instead of a second one.
    clientRequestId: uuidFrom(`booking:${customer.username}:${k}`),
  };
}

/** A stable UUID (version-4 layout) from a string, so re-runs send the same request id. */
function uuidFrom(text) {
  const r = rng(text);
  const hex = Array.from({ length: 32 }, () => Math.floor(r.next() * 16).toString(16));
  hex[12] = '4';
  hex[16] = ((parseInt(hex[16], 16) & 0x3) | 0x8).toString(16);
  const h = hex.join('');
  return `${h.slice(0, 8)}-${h.slice(8, 12)}-${h.slice(12, 16)}-${h.slice(16, 20)}-${h.slice(20)}`;
}

async function stageOrders() {
  await mapLimit(PEOPLE.customers, CONCURRENCY, async (c) => {
    const s = await login(c.username);
    const r = rng(`orders:${c.username}`);
    const planned = r.int(4, 6);
    const page = await api('GET', '/api/v1/orders?page=0&size=1', { token: s.token });
    const existing = Number(page?.totalElements || 0);
    // Draw the bodies for orders already booked too, so re-runs continue the same sequence.
    for (let k = 0; k < planned; k++) {
      const body = orderBody(c, r, k);
      if (k < existing) continue;
      await api('POST', '/api/v1/orders', { token: s.token, body });
      count('orders booked');
    }
    if (existing >= planned) count('customers already complete');
  });
  report('orders');
}

// ── Stage: merchants ────────────────────────────────────────────────────────
const jwtClaims = (token) => JSON.parse(Buffer.from(token.split('.')[1], 'base64url').toString('utf8'));

function merchantOrderBody(m, r, k) {
  const plan = m.plan;
  const toOther = r.chance(0.7);
  const dest = toOther
    ? districtByCode[r.weighted(DISTRICTS.filter((d) => d.code !== plan.district).map((d) => [d.code, d.weight]))]
    : m.district;
  const [destCity, destPostal] = r.pick(dest.towns);
  const recipient = personName(r, dest);
  const declared = r.int(15, 250) * 100;
  // Shops ship small and medium boxes; merchants must give all three sides.
  const [l, w, h] = r.weighted([[PACKAGES[0], 20], [PACKAGES[1], 50], [PACKAGES[2], 30]]);
  const side = (v) => Math.round(v * (0.85 + r.next() * 0.3) * 10) / 10;
  return {
    externalRef: `SO-${plan.slug.split('-')[0].toUpperCase()}-${1001 + k}`,
    pickup: {
      addressLine1: m.street, city: m.town[0], district: plan.district, postalCode: m.town[1],
      contactName: plan.business, contactPhone: m.phone,
    },
    delivery: {
      addressLine1: `No. ${r.int(1, 320)}, ${r.pick(STREETS)}`, city: destCity, district: dest.code, postalCode: destPostal,
      contactName: `${recipient.first} ${recipient.last}`, contactPhone: anyPhone(r),
    },
    parcel: {
      actualWeightKg: Math.round(r.between(0.3, 6) * 100) / 100,
      lengthCm: side(l), widthCm: side(w), heightCm: side(h),
      prohibitedItemsDeclared: true,
      itemDescription: r.pick(plan.items),
      declaredValue: declared,
    },
    speed: r.chance(0.2) ? 'EXPRESS' : 'STANDARD',
  };
}

/** The business application: what it sells, rough volume, and its one pickup location. */
const applicationOf = (m) => ({
  businessName: m.plan.business, businessCategory: m.plan.category, monthlyParcelVolume: m.plan.volume,
  businessRegNo: m.regNo, contactEmail: m.email, contactPhone: m.phone,
  pickupAddress: m.street, pickupCity: m.town[0], pickupPostalCode: m.town[1], operatingDistrict: m.plan.district,
});

async function stageMerchants() {
  if (!ADMIN_PASSWORD) throw new Error('SEED_ADMIN_PASSWORD is required for the merchants stage');
  const admin = await login(ADMIN_USER, ADMIN_PASSWORD);
  const applications = (await api('GET', '/api/v1/admin/merchants', { token: admin.token })) || [];
  for (const m of PEOPLE.merchants) {
    const plan = m.plan;
    const owner = await login(m.username);
    // 1. The owner applies (once).
    let app = applications.find((a) => a.ownerUserId === owner.userId);
    if (!app) {
      app = await api('POST', '/api/v1/merchants/register', { token: owner.token, body: applicationOf(m) });
      count('businesses applied');
    }
    // A database seeded before every demo merchant was approved may hold a rejected application:
    // the owner corrects and resubmits it (allowed after a rejection), so it can be approved below.
    if (app.status === 'REJECTED' && plan.outcome === 'APPROVED') {
      app = await api('POST', '/api/v1/merchants/register', { token: owner.token, body: applicationOf(m) });
      count('resubmitted');
    }
    // 2. The admin decides (in production the district dispatcher does; the admin can for any district).
    if (app.status === 'PENDING_APPROVAL' && plan.outcome === 'APPROVED') {
      app = await api('POST', `/api/v1/admin/merchants/${app.merchantId}/approve`, { token: admin.token });
      count('approved');
    } else if (app.status === 'PENDING_APPROVAL' && plan.outcome === 'REJECTED') {
      app = await api('POST', `/api/v1/admin/merchants/${app.merchantId}/reject`, { token: admin.token, body: { reason: plan.reason } });
      count('rejected');
    }
    if (plan.outcome !== 'APPROVED' || app.status !== 'APPROVED') { count(`left ${app.status.toLowerCase()}`); continue; }

    // 3. IAM links the merchant to the owner's account from the merchant.approved event;
    //    sign in again until the token carries the merchant id.
    let session;
    for (let i = 0; i < 30; i++) {
      sessions.delete(m.username);
      session = await login(m.username);
      if (jwtClaims(session.token).merchantId === app.merchantId) break;
      session = null;
      await sleep(2000);
    }
    if (!session) throw new Error(`${m.username}: the account was not linked to merchant ${app.merchantId} (is the IAM merchant-event consumer running?)`);

    // 4. An API key for their online store (re-runs regenerate it: the secret is shown only once).
    const keys = (await api('GET', `/api/v1/merchants/${app.merchantId}/api-keys`, { token: session.token })) || [];
    const existing = keys.find((k) => k.active && k.keyName === 'Online store');
    const key = existing
      ? await api('POST', `/api/v1/merchants/${app.merchantId}/api-keys/${existing.keyId}/regenerate`, { token: session.token })
      : await api('POST', `/api/v1/merchants/${app.merchantId}/api-keys`, { token: session.token, body: { keyName: 'Online store', expiresInDays: 365 } });
    if (!key?.rawApiKey) throw new Error(`${m.username}: no API key secret returned`);

    // 5. B2B orders through the gateway with the API key; externalRef makes re-runs idempotent.
    const r = rng(`merchant-orders:${plan.slug}`);
    for (let k = 0; k < plan.orders; k++) {
      const result = await api('POST', '/api/v1/b2b/orders', {
        headers: { 'X-API-Key': key.rawApiKey }, body: merchantOrderBody(m, r, k),
      });
      count(result?.created ? 'b2b orders booked' : 'b2b orders already there');
    }
  }
  report('merchants');
}

// ── Stage: ready ────────────────────────────────────────────────────────────
// The shops have packed their parcels, so their Standard ones can be collected in one visit. Dispatch
// marks the orders it has filed, so this runs once it has processed the bookings. One shop is left
// unpacked, so the dispatcher sees a shop still waiting.
async function stageReady() {
  for (const m of PEOPLE.merchants.filter((x) => x.plan.outcome === 'APPROVED')) {
    if (!m.plan.ready) { count('shops not packed yet'); continue; }
    const s = await login(m.username);
    const merchantId = jwtClaims(s.token).merchantId;
    if (!merchantId) { count('shops not approved yet'); continue; }
    try {
      await api('POST', `/api/v1/merchants/${merchantId}/pickup/ready`, { token: s.token });
      count('shops ready for pickup');
    } catch (e) {
      // Refused when nothing is left to collect (a re-run after the courier came): nothing to do then.
      if (![400, 409, 422].includes(e.status)) throw e;
      count('shops with nothing to collect');
    }
  }
  report('ready');
}

// ── Stage: shifts ───────────────────────────────────────────────────────────
// A shift takes its vehicle and capacity from the courier's registered vehicle, so each courier
// first registers theirs (409 on a re-run: already registered). The district dispatcher then raises
// the capacity to 20 so the demo queues have room.
function vehicleOf(c, index) {
  return {
    vehicleType: c.vehicle,
    // Unique and valid for the plate format (e.g. SDA-1000).
    plateNumber: `SD${String.fromCharCode(65 + (index % 26))}-${1000 + index}`,
    vehicleModel: { MOTORCYCLE: 'Honda Dio', THREE_WHEELER: 'Bajaj RE', VAN: 'Toyota HiAce', LORRY: 'Isuzu Elf' }[c.vehicle],
    vehicleColour: 'White',
    licenceNumber: `B${String(1000000 + index)}`,
  };
}

// ── Working day ─────────────────────────────────────────────────────────────
// Dispatch checks the real clock: a shift starts only Monday to Saturday (not on a holiday) from
// 07:30, and pickups are given out only until 15:00 Sri Lanka time. Stop early with a clear message
// rather than half-way through a stage.
function requireWorkingHours(stage, untilHour) {
  const local = new Date(Date.now() + 5.5 * 3600 * 1000);
  const day = local.getUTCDay();
  const hour = local.getUTCHours() + local.getUTCMinutes() / 60;
  if (day !== 0 && hour >= 7.5 && hour < untilHour) return;
  const at = local.toISOString().slice(0, 16).replace('T', ' ');
  throw new Error(`The ${stage} stage needs a working day: Monday to Saturday, 07:30 to ${untilHour}:00 Sri Lanka time `
    + `(it is ${at} there now). Run it again then; the stages before it are kept.`);
}

async function stageShifts() {
  requireWorkingHours('shifts', 17);
  const couriers = PEOPLE.staff.filter((p) => p.role === 'COURIER');
  await mapLimit(couriers, CONCURRENCY, async (c) => {
    const s = await login(c.username);
    const vehicle = vehicleOf(c, couriers.indexOf(c));
    await api('PUT', '/api/v1/dispatch/couriers/me/profile', { token: s.token, body: vehicle, expect: [200, 409] });
    const d = await login(dispatcherOf(c.district.code).username);
    await api('PUT', `/api/v1/dispatch/couriers/${s.userId}/profile`, {
      token: d.token, body: { ...vehicle, maxCapacity: 20 },
    });
    // The shift starts in the courier's own district; starting it again returns the running one.
    await api('POST', '/api/v1/dispatch/couriers/shifts/start', {
      token: s.token,
      body: { courierId: s.userId, courierName: `${c.first} ${c.last}`, phone: c.phone },
    });
    count('shifts started');
  });
  report('shifts');
}

// ── Stage: vehicles ─────────────────────────────────────────────────────────
// Registers each seeded courier's vehicle (capacity 20) without starting a shift or creating
// orders, so it sends no e-mail. For a database seeded before couriers had a vehicle record:
// without one a courier cannot start a shift. Couriers who already have a vehicle are left alone.
async function stageVehicles() {
  const couriers = PEOPLE.staff.filter((p) => p.role === 'COURIER');
  await mapLimit(couriers, CONCURRENCY, async (c) => {
    const s = await login(c.username);
    const existing = await api('GET', '/api/v1/dispatch/couriers/me/profile', { token: s.token, expect: [200, 404] });
    if (existing?.vehicleType) { count('already had a vehicle'); return; }
    const vehicle = vehicleOf(c, couriers.indexOf(c));
    await api('PUT', '/api/v1/dispatch/couriers/me/profile', { token: s.token, body: vehicle });
    const d = await login(dispatcherOf(c.district.code).username);
    await api('PUT', `/api/v1/dispatch/couriers/${s.userId}/profile`, {
      token: d.token, body: { ...vehicle, maxCapacity: 20 },
    });
    count('vehicles registered');
  });
  report('vehicles');
}

// ── Stage: cancel ───────────────────────────────────────────────────────────
async function customerOrders(token) {
  const all = [];
  for (let page = 0; page < 10; page++) {
    const res = await api('GET', `/api/v1/orders?page=${page}&size=100`, { token });
    all.push(...(res?.content || []));
    if (!res?.hasNext) break;
  }
  return all;
}

async function stageCancel() {
  await mapLimit(PEOPLE.customers, CONCURRENCY, async (c) => {
    const s = await login(c.username);
    for (const o of await customerOrders(s.token)) {
      if (hash(o.orderId) % 100 >= CANCEL_BELOW) continue;
      if (o.status === 'CANCELLED') { count('already cancelled'); continue; }
      if (o.status !== 'CREATED') { count('too late to cancel'); continue; }
      try {
        await api('POST', `/api/v1/orders/${o.orderId}/cancel`, {
          token: s.token, body: { reason: rng(o.orderId).pick(CANCEL_REASONS) },
        });
        count('orders cancelled');
      } catch (e) {
        if (e.status >= 500) throw e;
        count('could not cancel');
      }
    }
  });
  report('cancel');
}

// ── Dispatch helpers ────────────────────────────────────────────────────────
// A dispatcher only ever sees their own district, so every district's dispatcher works their own
// lists; districts run in parallel, each dispatcher one order at a time, as a person would.
async function forEachDistrict(fn) {
  await mapLimit(DISTRICTS, CONCURRENCY, async (d) => {
    const dispatcher = dispatcherOf(d.code);
    await fn(d.code, await login(dispatcher.username), dispatcher);
  });
}

const allCouriers = () => PEOPLE.staff.filter((p) => p.role === 'COURIER');
async function forEachCourier(fn) {
  await mapLimit(allCouriers(), CONCURRENCY, async (c) => fn(c, await login(c.username)));
}

const list = async (token, urlPath) => (await api('GET', urlPath, { token })) || [];

/** Sums one dispatch list over every district, for waiting until the events have been applied. */
async function districtTotal(urlPath) {
  let total = 0;
  for (const d of DISTRICTS) total += (await list((await login(dispatcherOf(d.code).username)).token, urlPath)).length;
  return total;
}

/** The courier dispatch suggests first among those whose vehicle and load fit, or null. */
async function bestCourier(token, orderId) {
  const options = await list(token, `/api/v1/dispatch/orders/${orderId}/suggestions`);
  return options.find((c) => c.eligible)?.courierId ?? null;
}

/** Assigns, retrying a 409 conflict ("refresh and retry", as the API asks). False when refused. */
async function assignCourier(token, dispatcher, orderId, courierId, reason) {
  for (let attempt = 1; ; attempt++) {
    try {
      await api('POST', '/api/v1/dispatch/assign', {
        token, body: { orderId, courierId, assignedBy: dispatcher.username, reason },
      });
      return true;
    } catch (e) {
      if (e.status === 409 && attempt < 5) { count('retried after conflict'); await sleep(300 * attempt); continue; }
      if ([400, 409, 422].includes(e.status)) { count('assignments refused'); return false; }
      throw e;
    }
  }
}

// ── Stage: assign ───────────────────────────────────────────────────────────
// First-leg orders: each one to the best-fitting courier of its pickup district; a shop's packed
// parcels to one courier, all of them or none. Hub parcels are assigned in the deliver stage.
async function stageAssign() {
  requireWorkingHours('assign', 15);
  await forEachDistrict(async (code, d, dispatcher) => {
    const shops = new Map();
    for (const a of await list(d.token, '/api/v1/dispatch/unassigned')) {
      if (a.leg && a.leg !== 'FIRST_MILE') continue;
      if (a.shopPickup) {
        if (!a.readyForPickup) { count('shop parcels not packed yet'); continue; }
        if (!shops.has(a.merchantId)) shops.set(a.merchantId, []);
        shops.get(a.merchantId).push(a.orderId);
        continue;
      }
      if (hash(a.orderId) % 100 < ASSIGN_FROM) { count('left unassigned'); continue; }
      const courierId = await bestCourier(d.token, a.orderId);
      if (!courierId) { count('no courier fits (left in the queue)'); continue; }
      if (await assignCourier(d.token, dispatcher, a.orderId, courierId, 'Best match in the pickup district')) count('assigned');
    }
    for (const [merchantId, orderIds] of shops) {
      const options = await list(d.token, `/api/v1/dispatch/shops/${merchantId}/couriers`);
      const courier = options.find((o) => orderIds.every((id) => (o.fittingOrderIds || []).includes(id)));
      if (!courier) { count('shops no courier can take in one visit'); continue; }
      try {
        await api('POST', `/api/v1/dispatch/shops/${merchantId}/assign`, { token: d.token, body: { courierId: courier.courierId, orderIds } });
        count('shop visits assigned');
        count('shop parcels assigned', orderIds.length);
      } catch (e) {
        if (![400, 409, 422].includes(e.status)) throw e;
        count('shop visits refused');
      }
    }
  });
  report('assign');
}

// ── Stage: deliver ──────────────────────────────────────────────────────────
// Where each assigned parcel stops, by a stable hash, so every console tab and both sides of a
// return have something in them:
function deliveryPlan(orderId) {
  const x = hash(`${orderId}:delivery`) % 100;
  if (x < 4) return 'NOT_COLLECTED';      // Assigned: the courier has not been yet
  if (x < 8) return 'AT_ORIGIN_HUB';      // Ready to send
  if (x < 12) return 'SENT';              // Sent, not accepted yet (the drop-off hub's Incoming)
  if (x < 16) return 'AT_HUB';            // At the drop-off hub, waiting for a courier
  if (x < 23) return 'OUT_FOR_DELIVERY';
  if (x < 27) return 'HELD';              // Returns: failed attempt, held at the hub
  if (x < 30) return 'RETURNING';         // Returns: at the sender's hub, waiting for a courier
  if (x < 33) return 'RETURNED';          // handed back to the sender
  if (x < 37) return 'RETRIED';           // delivered on the second attempt
  return 'DELIVERED';
}

async function myParcels(s) {
  return list(s.token, `/api/v1/deliveries/courier/${s.userId}`);
}

async function transition(token, deliveryId, targetState, extra = {}) {
  await api('POST', `/api/v1/deliveries/${deliveryId}/transition`, { token, body: { targetState, ...extra } });
}

/** A hub step, taken by the dispatcher of that hub on the parcel's delivery record. */
async function hubStep(token, orderId, targetState, notes) {
  const delivery = await api('GET', `/api/v1/deliveries/order/${orderId}`, { token });
  await transition(token, delivery.deliveryId, targetState, { notes });
}

/** Retries a call the services refuse until the previous step's event has reached them. */
async function whenCaughtUp(fn, tries = 10) {
  for (let attempt = 1; ; attempt++) {
    try { return await fn(); } catch (e) {
      if (![400, 409, 422].includes(e.status) || attempt >= tries) throw e;
      await sleep(1500);
    }
  }
}

// Collect: a shop's parcels in one visit with one payment, any other parcel with its exact fee.
async function collectPickups() {
  await forEachCourier(async (c, s) => {
    const waiting = (await myParcels(s)).filter((d) => d.currentState === 'ASSIGNED');
    for (const merchantId of new Set(waiting.filter((d) => d.merchantId).map((d) => d.merchantId))) {
      const stop = await api('GET', `/api/v1/deliveries/shops/${merchantId}`, { token: s.token });
      const parcels = stop?.parcels || [];
      if (!parcels.length) continue;
      const fees = parcels.reduce((sum, p) => sum + Math.round(Number(p.fee || 0) * 100), 0) / 100;
      await api('POST', `/api/v1/deliveries/shops/${merchantId}/collect`, {
        token: s.token, body: { deliveryIds: parcels.map((p) => p.deliveryId), cashCollected: fees },
      });
      count('shop visits made');
      count('picked up', parcels.length);
    }
    for (const d of waiting.filter((x) => !x.merchantId)) {
      if (deliveryPlan(d.orderId) === 'NOT_COLLECTED') { count('not collected yet'); continue; }
      const job = await api('GET', `/api/v1/deliveries/${d.deliveryId}/job`, { token: s.token });
      const fee = Number(job?.feeToCollect || 0);
      await transition(s.token, d.deliveryId, 'PICKED_UP', fee > 0 ? { cashCollected: fee } : {});
      count('picked up');
    }
  });
}

async function handIn(states, label) {
  await forEachCourier(async (c, s) => {
    const ids = (await myParcels(s)).filter((d) => states.includes(d.currentState)).map((d) => d.deliveryId);
    if (!ids.length) return;
    await api('POST', '/api/v1/deliveries/hand-in', { token: s.token, body: { deliveryIds: ids } });
    count(label, ids.length);
  });
}

// The pickup hub sends parcels for other districts Outbound and sorts its own for delivery.
async function sendFromHub() {
  await forEachDistrict(async (code, d) => {
    for (const a of await list(d.token, '/api/v1/dispatch/ready-to-send')) {
      if (deliveryPlan(a.orderId) === 'AT_ORIGIN_HUB') { count('left at the hub'); continue; }
      const local = a.pickupDistrict === a.deliveryDistrict;
      await hubStep(d.token, a.orderId, local ? 'AT_DESTINATION_HUB' : 'IN_TRANSIT',
        local ? 'Dispatcher: sorted for delivery' : 'Dispatcher: marked outbound');
      count(local ? 'sorted for delivery' : 'marked outbound');
    }
  });
}

// The drop-off hub accepts what was sent to it.
async function acceptIncoming() {
  await forEachDistrict(async (code, d) => {
    for (const a of (await list(d.token, '/api/v1/dispatch/incoming')).filter((x) => x.hubStage === 'OUTBOUND')) {
      if (deliveryPlan(a.orderId) === 'SENT') { count('left on the way'); continue; }
      await hubStep(d.token, a.orderId, 'AT_DESTINATION_HUB', 'Dispatcher: accepted at the hub');
      count('accepted at the hub');
    }
  });
}

// Each hub gives its waiting parcels to a courier who has not left yet: deliveries (last leg) and
// returns to the sender.
async function assignFromHub() {
  await forEachDistrict(async (code, d, dispatcher) => {
    const queue = (await list(d.token, '/api/v1/dispatch/unassigned')).filter((a) => a.leg === 'LAST_MILE' || a.leg === 'RETURN');
    for (const a of queue) {
      const plan = deliveryPlan(a.orderId);
      const isReturn = a.leg === 'RETURN';
      if (isReturn ? plan === 'RETURNING' : ['SENT', 'AT_HUB'].includes(plan)) {
        count(isReturn ? 'returns waiting for a courier' : 'waiting at the hub for a courier');
        continue;
      }
      const courierId = await bestCourier(d.token, a.orderId);
      if (!courierId) { count('no courier fits (left at the hub)'); continue; }
      if (await assignCourier(d.token, dispatcher, a.orderId, courierId, isReturn ? 'Back to the sender' : 'Next run from the hub')) {
        count(isReturn ? 'returns assigned' : 'hub parcels assigned');
      }
    }
  });
}

const AT_HUB_FOR_ME = ['AT_DESTINATION_HUB', 'RETURN_AT_HUB'];

async function couriersWaitingParcels() {
  let total = 0;
  for (const c of allCouriers()) total += (await myParcels(await login(c.username))).filter((d) => AT_HUB_FOR_ME.includes(d.currentState)).length;
  return total;
}

// Couriers load what waits for them at the hub, in one go, and leave on their run.
async function loadAndLeave() {
  await forEachCourier(async (c, s) => {
    const ids = (await myParcels(s)).filter((d) => AT_HUB_FOR_ME.includes(d.currentState)).map((d) => d.deliveryId);
    if (!ids.length) return;
    await api('POST', '/api/v1/deliveries/load', { token: s.token, body: { deliveryIds: ids } });
    count('loaded', ids.length);
    // Dispatch refuses the run until it has seen every parcel loaded.
    await whenCaughtUp(() => api('POST', `/api/v1/dispatch/couriers/shifts/${s.userId}/run`, { token: s.token }));
    count('runs started');
  });
}

// On the run: deliver with a photo, or report a failed attempt; returns are handed back to the sender
// with a photo. Back at the hub, failed attempts are handed in.
async function onTheRun() {
  await forEachCourier(async (c, s) => {
    let failed = 0;
    for (const d of await myParcels(s)) {
      const plan = deliveryPlan(d.orderId);
      if (d.currentState === 'OUT_FOR_RETURN') {
        await uploadProof(s.token, d, c);
        await transition(s.token, d.deliveryId, 'RETURNED', { notes: 'Handed back to the sender' });
        count('returned to the sender');
        continue;
      }
      if (d.currentState !== 'OUT_FOR_DELIVERY') continue;
      if (plan === 'OUT_FOR_DELIVERY') { count('still out for delivery'); continue; }
      if (!(d.attemptCount > 0) && ['HELD', 'RETURNING', 'RETURNED', 'RETRIED'].includes(plan)) {
        const r = rng(`attempt:${d.deliveryId}`);
        // A refused parcel is never tried again, so only some of the returns are refusals.
        const reasons = ['HELD', 'RETRIED'].includes(plan) || r.chance(0.5) ? FAILURES.RETRY : FAILURES.REFUSED;
        await transition(s.token, d.deliveryId, 'ATTEMPT_FAILED', { failureReason: r.pick(reasons), notes: 'Reported by courier' });
        count('delivery attempts failed');
        failed++;
        continue;
      }
      await uploadProof(s.token, d, c);
      await transition(s.token, d.deliveryId, 'DELIVERED');
      count('delivered');
    }
    await api('POST', `/api/v1/dispatch/couriers/shifts/${s.userId}/back`, { token: s.token });
    if (failed) {
      const ids = (await myParcels(s)).filter((d) => d.currentState === 'ATTEMPT_FAILED').map((d) => d.deliveryId);
      if (ids.length) {
        await api('POST', '/api/v1/deliveries/hand-in', { token: s.token, body: { deliveryIds: ids } });
        count('failed attempts handed in', ids.length);
      }
    }
  });
}

// The hub holding a failed parcel tries it again or sends it back to the sender (through the
// sender's hub when the districts differ).
async function decideHeld() {
  await forEachDistrict(async (code, d) => {
    for (const a of (await list(d.token, '/api/v1/dispatch/returns')).filter((x) => x.hubStage === 'HELD_AT_HUB')) {
      const plan = deliveryPlan(a.orderId);
      if (plan === 'HELD') { count('held at the hub'); continue; }
      if (plan === 'RETRIED' && a.canRetry !== false) {
        await hubStep(d.token, a.orderId, 'AT_DESTINATION_HUB', 'Dispatcher: try again');
        count('sent out again');
        continue;
      }
      const local = a.pickupDistrict === a.deliveryDistrict;
      await hubStep(d.token, a.orderId, local ? 'RETURN_AT_HUB' : 'RETURN_IN_TRANSIT', 'Dispatcher: return to the sender');
      count('sent back towards the sender');
    }
  });
}

// The sender's hub accepts returns sent to it.
async function acceptReturns() {
  await forEachDistrict(async (code, d) => {
    const coming = (await list(d.token, '/api/v1/dispatch/returns'))
      .filter((x) => x.hubStage === 'RETURN_OUTBOUND' && x.pickupDistrict === code);
    for (const a of coming) {
      await hubStep(d.token, a.orderId, 'RETURN_AT_HUB', 'Dispatcher: return accepted at the hub');
      count('returns accepted at the hub');
    }
  });
}

/** One working day of hub traffic. A second round delivers the retries and takes the returns back. */
async function deliveryRound() {
  await collectPickups();
  await handIn(['PICKED_UP'], 'handed in at the hub');
  await settle('ready to send', () => districtTotal('/api/v1/dispatch/ready-to-send'));
  await sendFromHub();
  await settle('incoming', () => districtTotal('/api/v1/dispatch/incoming'));
  await acceptIncoming();
  await settle('hub queues', () => districtTotal('/api/v1/dispatch/unassigned'));
  await assignFromHub();
  await settle('parcels waiting for their courier', couriersWaitingParcels);
  await loadAndLeave();
  await onTheRun();
  await settle('returns', () => districtTotal('/api/v1/dispatch/returns'));
  await decideHeld();
  await settle('returns', () => districtTotal('/api/v1/dispatch/returns'));
  await acceptReturns();
  await settle('hub queues', () => districtTotal('/api/v1/dispatch/unassigned'));
}

async function stageDeliver() {
  requireWorkingHours('deliver', 17);
  for (const round of [1, 2]) {
    await deliveryRound();
    report(`deliver (round ${round} of 2)`);
  }
}

// ── Stage: cash ─────────────────────────────────────────────────────────────
// Most districts close their day: couriers hand over the fees they collected, the dispatcher counts
// each handover and banks the hub's cash, and the admin confirms the deposit (revenue on the
// Analytics page comes from this ledger). A few stop earlier, so the cash pages show every state.
function cashPlan(code) {
  const x = hash(`cash:${code}`) % 10;
  return x < 7 ? 'CONFIRMED' : x < 8 ? 'BANKED' : x < 9 ? 'AT_HUB' : 'WITH_COURIERS';
}

async function stageCash() {
  await forEachCourier(async (c, s) => {
    if (cashPlan(c.district.code) === 'WITH_COURIERS') { count('couriers still holding cash'); return; }
    try {
      await api('POST', '/api/v1/cash/handovers', { token: s.token });
      count('cash handed over');
    } catch (e) {
      if (![400, 409, 422].includes(e.status)) throw e;
      count('couriers with no cash');
    }
  });
  await forEachDistrict(async (code, d) => {
    if (cashPlan(code) === 'WITH_COURIERS') return;
    for (const h of (await api('GET', '/api/v1/cash/desk', { token: d.token }))?.toCount || []) {
      await api('POST', `/api/v1/cash/handovers/${h.handoverId}/count`, {
        token: d.token, body: { countedAmount: Number(h.expectedAmount) },
      });
      count('handovers counted');
    }
    if (cashPlan(code) === 'AT_HUB') return;
    const hubCash = Number((await api('GET', '/api/v1/cash/desk', { token: d.token }))?.hubCash || 0);
    if (hubCash <= 0) return;
    const day = new Date().toISOString().slice(0, 10).replace(/-/g, '');
    await api('POST', '/api/v1/cash/deposits', {
      token: d.token, body: { amount: hubCash, reference: `BOC-${slug(code).toUpperCase()}-${day}` },
    });
    count('deposits banked');
  });
  if (!ADMIN_PASSWORD) {
    count('deposits left for the admin (no SEED_ADMIN_PASSWORD)');
  } else {
    const admin = await login(ADMIN_USER, ADMIN_PASSWORD);
    for (const d of DISTRICTS.filter((x) => cashPlan(x.code) === 'CONFIRMED')) {
      const desk = await api('GET', `/api/v1/cash/desk?district=${d.code}`, { token: admin.token });
      for (const deposit of (desk?.deposits || []).filter((x) => x.status === 'SUBMITTED')) {
        await api('POST', `/api/v1/cash/deposits/${deposit.depositId}/confirm`, { token: admin.token });
        count('deposits confirmed');
      }
    }
  }
  report('cash');
}

let PHOTOS;
function photos() {
  if (PHOTOS) return PHOTOS;
  const dir = path.join(HERE, 'assets');
  const files = fs.existsSync(dir) ? fs.readdirSync(dir).filter((f) => /\.(jpe?g|png)$/i.test(f)) : [];
  PHOTOS = files.length
    ? files.map((f) => ({
      name: f, bytes: fs.readFileSync(path.join(dir, f)),
      type: /\.png$/i.test(f) ? 'image/png' : 'image/jpeg',
    }))
    : placeholderPhotos();
  return PHOTOS;
}

/** A photo taken on this trip (the delivery, or the return to the sender) unlocks the final step. */
async function uploadProof(token, delivery, courier) {
  const r = rng(`pod:${delivery.deliveryId}:${delivery.currentState}`);
  const photo = photos()[hash(delivery.deliveryId) % photos().length];
  const who = personName(r, courier.district);
  const form = new FormData();
  form.append('photo', new Blob([photo.bytes], { type: photo.type }), photo.name);
  form.append('recipientName', `${who.first} ${who.last}`);
  form.append('signature', `Signed by ${who.first} ${who.last}`);
  await api('POST', `/api/v1/deliveries/${delivery.deliveryId}/proof-of-delivery`, { token, form });
  count('proof photos uploaded');
}

// ── Stage: notifications ────────────────────────────────────────────────────
// Notifications are created by notification-service from events; here customers just
// "read" the older ~60% so the bell shows a realistic unread count.
async function stageNotifications() {
  await mapLimit(PEOPLE.customers, CONCURRENCY, async (c) => {
    const s = await login(c.username);
    const list = (await api('GET', '/api/v1/notifications/me', { token: s.token })) || [];
    list.sort((a, b) => String(a.createdAt).localeCompare(String(b.createdAt)));
    count('notifications seen', list.length);
    for (const n of list.slice(0, Math.floor(list.length * 0.6))) {
      if (n.read) continue;
      await api('PATCH', `/api/v1/notifications/me/${n.id}/read`, { token: s.token });
      count('marked read');
    }
  });
  report('notifications');
}

// ── Stage: summary ──────────────────────────────────────────────────────────
async function stageSummary() {
  const rows = [];
  if (ADMIN_PASSWORD) {
    const admin = await login(ADMIN_USER, ADMIN_PASSWORD);
    for (const role of ['SYSTEM_ADMIN', 'DISPATCHER', 'COURIER', 'CUSTOMER', 'MERCHANT']) {
      const res = await api('GET', `/api/v1/users?role=${role}&size=1`, { token: admin.token }).catch(() => null);
      rows.push([`${role} accounts`, res?.totalElements ?? '?']);
    }
  }
  console.log('\n   Accounts in the system');
  for (const [k, v] of rows) console.log(`     ${k.padEnd(22)} ${v}`);
  const customer = PEOPLE.customers[0];
  console.log('\n   Demo logins (all seeded accounts use the SEED_PASSWORD you chose)');
  console.log(`     System admin   ${ADMIN_USER}  (bootstrap admin password)`);
  console.log(`     Dispatcher     ${dispatcherOf('COLOMBO').username}   (one per district: dispatcher.<district>)`);
  console.log(`     Courier        ${couriersIn('COLOMBO')[0].username}  (courier.<district>.1 / .2)`);
  console.log(`     Customer       ${customer.username}  (${customer.district.code})`);
  console.log(`     Merchant       ${PEOPLE.merchants[0].username}  (approved; also: ${PEOPLE.merchants.slice(1).map((m) => `${m.username} [${m.plan.outcome.toLowerCase()}]`).join(', ')})`);
  console.log(`     ...and ${PEOPLE.customers.length - 1} more customers; e-mails are <username>@example.com\n`);
}

// ── Waiting helpers for "all" mode (every service running at once) ──────────
async function settle(label, measure) {
  let last = -1; let stable = 0;
  for (let i = 0; i < 60 && stable < 3; i++) {
    const now = await measure().catch(() => -1);
    stable = now === last && now >= 0 ? stable + 1 : 0;
    last = now;
    if (stable < 3) await sleep(3000);
  }
  console.log(`   ${label} settled at ${last}`);
}

// ── Placeholder proof-of-delivery photos (used when scripts/seed/assets/ is empty) ──
const CRC_TABLE = Array.from({ length: 256 }, (_, n) => {
  let c = n;
  for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
  return c >>> 0;
});
function crc32(buf) {
  let c = 0xffffffff;
  for (const b of buf) c = CRC_TABLE[(c ^ b) & 0xff] ^ (c >>> 8);
  return (c ^ 0xffffffff) >>> 0;
}
function encodePng(w, h, paint) {
  const raw = Buffer.alloc((w * 3 + 1) * h);
  for (let y = 0; y < h; y++) {
    const row = y * (w * 3 + 1);
    for (let x = 0; x < w; x++) {
      const [r, g, b] = paint(x, y);
      raw[row + 1 + x * 3] = r; raw[row + 2 + x * 3] = g; raw[row + 3 + x * 3] = b;
    }
  }
  const chunk = (type, data) => {
    const len = Buffer.alloc(4); len.writeUInt32BE(data.length);
    const td = Buffer.concat([Buffer.from(type, 'ascii'), data]);
    const crc = Buffer.alloc(4); crc.writeUInt32BE(crc32(td));
    return Buffer.concat([len, td, crc]);
  };
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(w, 0); ihdr.writeUInt32BE(h, 4); ihdr[8] = 8; ihdr[9] = 2;
  return Buffer.concat([
    Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]),
    chunk('IHDR', ihdr), chunk('IDAT', zlib.deflateSync(raw)), chunk('IEND', Buffer.alloc(0)),
  ]);
}
function placeholderPhotos() {
  // A simple "parcel at the door" scene: wall, floor, door, cardboard box with tape.
  const walls = [[226, 214, 190], [200, 214, 222], [232, 224, 206], [210, 222, 200], [236, 210, 196], [218, 218, 226]];
  return walls.map((wall, i) => ({
    name: `pod-${i + 1}.png`,
    type: 'image/png',
    bytes: encodePng(480, 360, (x, y) => {
      const shade = 1 - y / 1400;
      if (y > 250) return [120 - (y - 250) / 4, 104 - (y - 250) / 5, 92 - (y - 250) / 6].map((v) => v | 0); // floor
      if (x > 300 && x < 420 && y > 40) return x > 405 && x < 412 && y > 140 && y < 160 ? [212, 175, 55] : [96, 62, 40]; // door
      const bx = 110 + i * 8; const by = 170;
      if (x > bx && x < bx + 150 && y > by && y < 300) {
        if (x > bx + 66 && x < bx + 84) return [200, 180, 140]; // tape
        return [186 - (y - by) / 6, 140 - (y - by) / 6, 92 - (y - by) / 8].map((v) => v | 0);  // cardboard
      }
      return wall.map((v) => (v * shade) | 0);
    }),
  }));
}

// ── Main ────────────────────────────────────────────────────────────────────
const STAGES = {
  users: stageUsers, orders: stageOrders, merchants: stageMerchants, ready: stageReady, shifts: stageShifts,
  vehicles: stageVehicles,
  cancel: stageCancel, assign: stageAssign, deliver: stageDeliver, cash: stageCash, notifications: stageNotifications,
  summary: stageSummary,
};

async function stageAll() {
  // Every service is running, so dispatch reacts to bookings immediately. Shifts start
  // only after dispatch has filed every booking as UNASSIGNED; otherwise its auto-engine
  // would assign them all and the dispatcher board would be empty.
  await stageUsers();
  await stageOrders();
  await stageMerchants();
  // Every district's queue: a dispatcher sees only their own.
  await settle('unassigned queue', () => districtTotal('/api/v1/dispatch/unassigned'));
  await stageReady();
  await stageShifts();
  await stageCancel();
  await sleep(5000);
  await stageAssign();
  await settle('courier deliveries', async () => {
    let total = 0;
    for (const c of PEOPLE.staff.filter((p) => p.role === 'COURIER')) {
      const s = await login(c.username);
      total += ((await api('GET', `/api/v1/deliveries/courier/${s.userId}`, { token: s.token })) || []).length;
    }
    return total;
  });
  await stageDeliver();
  await stageCash();
  await sleep(10000); // let notification-service catch up
  await stageNotifications();
  await stageSummary();
}

async function main() {
  const args = process.argv.slice(2);
  if (!args.length || args.some((a) => a !== 'all' && !STAGES[a])) {
    console.error(`Usage: node scripts/seed/seed.mjs <${Object.keys(STAGES).join('|')}|all> ...`);
    process.exit(2);
  }
  if (!(PASSWORD.length >= 8 && /[A-Za-z]/.test(PASSWORD) && /\d/.test(PASSWORD))) {
    console.error('SEED_PASSWORD must be set: at least 8 characters with a letter and a digit.');
    process.exit(2);
  }
  console.log(`Seeding ${API}  (${PEOPLE.staff.length} staff, ${PEOPLE.customers.length} customers planned)`);
  for (const stage of args) {
    console.log(`-> ${stage}`);
    if (stage === 'all') await stageAll(); else await STAGES[stage]();
  }
}

main().catch((e) => { console.error(`\nSeed failed: ${e.message}`); process.exit(1); });
