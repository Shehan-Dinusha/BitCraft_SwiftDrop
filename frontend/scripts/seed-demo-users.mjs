#!/usr/bin/env node
/**
 * Creates local demo accounts, one per role, so you can sign in for real instead of using the
 * Dev persona bar. Safe to run again: accounts that already exist are skipped.
 *
 *   npm run seed:demo
 *
 * Customer and merchant accounts are self-service. Courier, dispatcher and system admin accounts
 * can only be created by an admin, so they are added when you pass an admin login:
 *
 *   SEED_ADMIN_USER=admin SEED_ADMIN_PASSWORD=... npm run seed:demo
 *
 * (An admin exists when the auth service starts with BOOTSTRAP_ADMIN_USERNAME and
 * BOOTSTRAP_ADMIN_PASSWORD set, see .env.example.) For local development only.
 */
const GATEWAY = process.env.GATEWAY_URL ?? 'http://localhost:8080';
const PASSWORD = process.env.SEED_PASSWORD ?? 'Demo@12345';
const MERCHANT_ID = '0f5e8c4a-2c6d-4a58-9c8f-4c2c4e7b8a11';

const accounts = [
  { role: 'CUSTOMER', username: 'demo.customer', selfService: true },
  { role: 'MERCHANT', username: 'demo.merchant', selfService: true, merchantId: MERCHANT_ID },
  { role: 'COURIER', username: 'demo.courier', selfService: false },
  { role: 'DISPATCHER', username: 'demo.dispatcher', selfService: false },
  { role: 'SYSTEM_ADMIN', username: 'demo.admin', selfService: false }
];

const body = (account, index) => ({
  username: account.username,
  email: `${account.username}@example.com`,
  phone: `07712345${String(60 + index).padStart(2, '0')}`,
  firstName: 'Demo',
  lastName: account.role.replace(/_/g, ' ').toLowerCase(),
  password: PASSWORD,
  role: account.role,
  district: 'COLOMBO',
  ...(account.merchantId ? { merchantId: account.merchantId } : {})
});

async function call(path, init) {
  const response = await fetch(`${GATEWAY}${path}`, {
    ...init,
    headers: { 'Content-Type': 'application/json', ...(init?.headers ?? {}) }
  });
  const text = await response.text();
  let json = null;
  try {
    json = JSON.parse(text);
  } catch {
    /* not JSON */
  }
  return { status: response.status, json, text };
}

async function adminToken() {
  const user = process.env.SEED_ADMIN_USER;
  const password = process.env.SEED_ADMIN_PASSWORD;
  if (!user || !password) return null;
  const result = await call('/api/v1/auth/login', {
    method: 'POST',
    body: JSON.stringify({ username: user, password })
  });
  if (result.status !== 200) {
    console.warn(`! Admin login failed (HTTP ${result.status}). Staff accounts will be skipped.`);
    return null;
  }
  return result.json.accessToken;
}

const alreadyThere = (result) =>
  result.status === 409 || /already|exist|taken|duplicate/i.test(result.text);

const token = await adminToken();
const rows = [];

for (const [index, account] of accounts.entries()) {
  let outcome;
  if (!account.selfService && !token) {
    outcome = 'skipped (needs SEED_ADMIN_USER and SEED_ADMIN_PASSWORD)';
  } else {
    const result = account.selfService
      ? await call('/api/v1/auth/register', {
          method: 'POST',
          body: JSON.stringify(body(account, index))
        })
      : await call('/api/v1/users', {
          method: 'POST',
          headers: { Authorization: `Bearer ${token}` },
          body: JSON.stringify(body(account, index))
        });
    outcome =
      result.status === 201 || result.status === 200
        ? 'created'
        : alreadyThere(result)
          ? 'already exists'
          : `failed (HTTP ${result.status}: ${result.json?.message ?? result.text.slice(0, 80)})`;
  }
  rows.push({ role: account.role, username: account.username, password: PASSWORD, outcome });
}

console.table(rows);
