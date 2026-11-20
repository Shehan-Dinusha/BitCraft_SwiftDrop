#!/usr/bin/env node
// SwiftDrop demo-data seeder.
//
// Drives the system through its public APIs (via the gateway), exactly like real users:
// the admin provisions staff, customers and merchant owners, customers book parcels,
// merchants are approved and send B2B orders with an API key, dispatchers assign
// them, couriers deliver them with proof-of-delivery photos. Every downstream service
// (dispatch, delivery, notifications, analytics) fills itself from the events this
// produces — no SQL, no cross-database IDs to keep in sync.
//
// Usage:  node scripts/seed/seed.mjs <stage> [<stage> ...]
//   stages: users orders merchants shifts cancel assign deliver notifications summary
//           all  (every stage in order — for a cluster that runs all services at once)
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
  DISTRICTS, STREETS, NAMES, ITEMS, NOTES, CANCEL_REASONS, FAILURES, VEHICLES, MERCHANTS,
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
//  20–99  assigned; the courier then takes it to a deliveryPlan() outcome
// (Mostly finished orders: after timeshift.mjs spreads them over a month, a large
// in-progress share would pile up on the last two days.)
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
        town: d.towns[(i - 1) % d.towns.length], phone: uniquePhone(r), vehicle: r.weighted(VEHICLES),
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
    codAmount: r.chance(0.3) ? declared : 0,
    prohibitedItemsDeclared: true,
  };
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
      prohibitedItemsDeclared: true,
      itemDescription: r.pick(plan.items),
      declaredValue: declared,
      codAmount: r.chance(0.5) ? declared : 0,
    },
    speed: r.chance(0.2) ? 'EXPRESS' : 'STANDARD',
  };
}

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
      app = await api('POST', '/api/v1/merchants/register', {
        token: owner.token,
        body: {
          businessName: plan.business, businessRegNo: m.regNo, contactEmail: m.email, contactPhone: m.phone,
          pickupAddress: `${m.street}, ${m.town[0]} ${m.town[1]}`, operatingDistrict: plan.district,
        },
      });
      count('businesses applied');
    }
    // 2. The admin decides.
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

// ── Stage: shifts ───────────────────────────────────────────────────────────
async function stageShifts() {
  const couriers = PEOPLE.staff.filter((p) => p.role === 'COURIER');
  await mapLimit(couriers, CONCURRENCY, async (c) => {
    const s = await login(c.username);
    await api('POST', '/api/v1/dispatch/couriers/shifts/start', {
      token: s.token,
      body: {
        courierId: s.userId, courierName: `${c.first} ${c.last}`, phone: c.phone,
        vehicleType: c.vehicle, initialDistrict: c.district.code, maxCapacity: 20,
      },
    });
    count('shifts started');
  });
  report('shifts');
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

// ── Stage: assign ───────────────────────────────────────────────────────────
// Older dispatch databases may still hold the four demo courier shifts from the V2
// migration (ids 1111…, 2222…) that have no user account; V8 removes them unless an
// assignment references them. Any order still assigned to one is reassigned to a real
// courier (allowed until pickup), and the demo couriers are taken offline.
async function rescueFromGhostCouriers(anyDispatcher) {
  const real = new Set();
  for (const c of PEOPLE.staff.filter((p) => p.role === 'COURIER')) real.add((await login(c.username)).userId);
  const shifts = (await api('GET', '/api/v1/dispatch/couriers', { token: anyDispatcher.token })) || [];
  for (const ghost of shifts.filter((s) => !real.has(s.courierId))) {
    const stuck = (await api('GET', `/api/v1/dispatch/couriers/${ghost.courierId}/assignments`, { token: anyDispatcher.token })) || [];
    for (const a of stuck.filter((x) => ['ASSIGNED', 'REASSIGNED'].includes(x.status))) {
      const district = a.pickupDistrict || 'COLOMBO';
      const dispatcher = dispatcherOf(district);
      const d = await login(dispatcher.username);
      const pool = couriersIn(district);
      const c = await login(pool[hash(a.orderId) % pool.length].username);
      try {
        await api('POST', '/api/v1/dispatch/reassign', {
          token: d.token,
          body: {
            orderId: a.orderId, expectedCurrentCourierId: ghost.courierId, newCourierId: c.userId,
            assignedBy: dispatcher.username, reason: `Reassigned: ${ghost.courierName} is not an active courier`,
          },
        });
        count('reassigned from demo couriers');
      } catch (e) {
        if (e.status !== 409 && e.status !== 422) throw e; // e.g. already picked up
        count('could not reassign (already picked up)');
      }
    }
    if (ghost.shiftStatus !== 'OFFLINE') {
      await api('PUT', '/api/v1/dispatch/couriers/shifts/status', {
        token: anyDispatcher.token, body: { courierId: ghost.courierId, status: 'OFFLINE' },
      });
      count('demo couriers taken offline');
    }
  }
}

async function stageAssign() {
  const anyDispatcher = await login(dispatcherOf('COLOMBO').username);
  await rescueFromGhostCouriers(anyDispatcher);
  const unassigned = await api('GET', '/api/v1/dispatch/unassigned', { token: anyDispatcher.token });
  // Each district's dispatcher works through their own queue one order at a time, as a
  // person would; districts run in parallel. Assigning updates the courier's shift row,
  // which dispatch protects with optimistic locking, so parallel assigns to the same
  // courier would conflict (409).
  const byDistrict = {};
  for (const a of unassigned || []) {
    if (hash(a.orderId) % 100 < ASSIGN_FROM) { count('left unassigned'); continue; }
    (byDistrict[a.pickupDistrict || 'COLOMBO'] ||= []).push(a);
  }
  await mapLimit(Object.entries(byDistrict), CONCURRENCY, async ([district, orders]) => {
    const dispatcher = dispatcherOf(district);
    const d = await login(dispatcher.username);
    const pool = couriersIn(district);
    let turn = hash(district);
    for (const a of orders) {
      const c = await login(pool[turn++ % pool.length].username);
      for (let attempt = 1; ; attempt++) {
        try {
          await api('POST', '/api/v1/dispatch/assign', {
            token: d.token,
            body: { orderId: a.orderId, courierId: c.userId, assignedBy: dispatcher.username, reason: 'Nearest available courier in the pickup district' },
          });
          count('assigned');
          break;
        } catch (e) {
          if (e.status !== 409 || attempt >= 5) throw e;
          count('retried after conflict');
          await sleep(300 * attempt); // "refresh and retry", as the API asks
        }
      }
    }
  });
  report('assign');
}

// ── Stage: deliver ──────────────────────────────────────────────────────────
// Share of ASSIGNED parcels (80% of all) → overall: ~58% delivered, ~7% out for
// delivery, ~5% picked up / in transit, ~10% failed.
function deliveryPlan(orderId) {
  const x = hash(`${orderId}:delivery`) % 100;
  if (x < 72) return { target: 'DELIVERED' };
  if (x < 81) return { target: 'OUT_FOR_DELIVERY' };
  if (x < 85) return { target: 'IN_TRANSIT' };
  if (x < 88) return { target: 'PICKED_UP' };
  const leg = x < 97 ? 'OUT_FOR_DELIVERY' : x < 99 ? 'IN_TRANSIT' : 'PICKED_UP';
  return { target: 'FAILED', leg };
}

const FORWARD = ['CREATED', 'ASSIGNED', 'PICKED_UP', 'IN_TRANSIT', 'OUT_FOR_DELIVERY', 'DELIVERED'];
function stepsFrom(current, plan) {
  if (['DELIVERED', 'FAILED', 'CANCELLED'].includes(current)) return [];
  const from = FORWARD.indexOf(current);
  const to = FORWARD.indexOf(plan.target === 'FAILED' ? plan.leg : plan.target);
  if (from < 0) return [];
  const steps = FORWARD.slice(from + 1, to + 1);
  if (plan.target === 'FAILED') steps.push('FAILED');
  return steps;
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

async function ensureProof(token, delivery, courier) {
  const existing = await api('GET', `/api/v1/deliveries/${delivery.deliveryId}/proof-of-delivery`, { token });
  if (existing?.verified) return;
  const r = rng(`pod:${delivery.deliveryId}`);
  const photo = photos()[hash(delivery.deliveryId) % photos().length];
  const who = personName(r, courier.district);
  const form = new FormData();
  form.append('photo', new Blob([photo.bytes], { type: photo.type }), photo.name);
  form.append('recipientName', `${who.first} ${who.last}`);
  form.append('signature', `Signed by ${who.first} ${who.last}`);
  // A successful upload marks the proof verified, which unlocks the DELIVERED transition.
  await api('POST', `/api/v1/deliveries/${delivery.deliveryId}/proof-of-delivery`, { token, form });
  count('proof photos uploaded');
}

async function stageDeliver() {
  const couriers = PEOPLE.staff.filter((p) => p.role === 'COURIER');
  await mapLimit(couriers, CONCURRENCY, async (c) => {
    const s = await login(c.username);
    const list = await api('GET', `/api/v1/deliveries/courier/${s.userId}`, { token: s.token });
    for (const d of list || []) {
      const plan = deliveryPlan(d.orderId);
      const steps = stepsFrom(d.currentState, plan);
      if (!steps.length) { count('already at planned state'); continue; }
      const r = rng(`route:${d.deliveryId}`);
      for (const target of steps) {
        if (target === 'DELIVERED') await ensureProof(s.token, d, c);
        await api('POST', `/api/v1/deliveries/${d.deliveryId}/transition`, {
          token: s.token,
          body: {
            targetState: target,
            actorRole: 'COURIER',
            failureReason: target === 'FAILED' ? r.pick(FAILURES[plan.leg]) : undefined,
            notes: target === 'FAILED' ? 'Reported by courier' : undefined,
          },
        });
      }
      count(`now ${plan.target.toLowerCase().replace(/_/g, ' ')}`);
    }
  });
  report('deliver');
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
  users: stageUsers, orders: stageOrders, merchants: stageMerchants, shifts: stageShifts, cancel: stageCancel,
  assign: stageAssign, deliver: stageDeliver, notifications: stageNotifications, summary: stageSummary,
};

async function stageAll() {
  // Every service is running, so dispatch reacts to bookings immediately. Shifts start
  // only after dispatch has filed every booking as UNASSIGNED; otherwise its auto-engine
  // would assign them all and the dispatcher board would be empty.
  await stageUsers();
  await stageOrders();
  await stageMerchants();
  const anyDispatcher = await login(dispatcherOf('COLOMBO').username);
  await settle('unassigned queue', async () => (await api('GET', '/api/v1/dispatch/unassigned', { token: anyDispatcher.token })).length);
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
