// Dependency-free browser acceptance checks using Chrome DevTools Protocol.
// Run: node docs/design/m10/fixtures/verify-frontend.mjs <path-to-chrome>
// API responses and sessions are fixtures; live authorization is checked separately.
import { spawn } from 'node:child_process';
import { mkdir, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import assert from 'node:assert/strict';

const root = fileURLToPath(new URL('../../../../', import.meta.url));
const output = resolve(root, 'backend/analytics-ai-service/target/frontend-acceptance');
await mkdir(output, { recursive: true });
const chromePath = process.argv[2] || process.env.CHROME_PATH;
if (!chromePath) throw new Error('Provide a Chrome executable path or CHROME_PATH');
const origin = 'http://127.0.0.1:5187';
const vite = spawn(
  process.execPath,
  ['node_modules/vite/bin/vite.js', '--host', '127.0.0.1', '--port', '5187', '--strictPort'],
  { cwd: resolve(root, 'frontend'), windowsHide: true, stdio: 'ignore' }
);
let chrome, ws;
const checks = [];
const pause = (ms) => new Promise((done) => setTimeout(done, ms));
async function until(action) {
  for (let attempt = 0; attempt < 200; attempt++) {
    try {
      if (await action()) return;
    } catch {}
    await pause(100);
  }
  throw new Error('Browser check timed out');
}
function check(name, value) {
  assert.ok(value, name);
  checks.push(name);
  console.log('PASS', name);
}
let next = 0;
const pending = new Map();
function cdp(method, params = {}) {
  return new Promise((done, fail) => {
    const id = ++next;
    pending.set(id, { done, fail });
    ws.send(JSON.stringify({ id, method, params }));
  });
}
async function evaluate(expression) {
  const response = await cdp('Runtime.evaluate', {
    expression,
    returnByValue: true,
    awaitPromise: true
  });
  if (response.exceptionDetails) throw new Error(response.exceptionDetails.text);
  return response.result.value;
}
const body = () => evaluate('document.body.innerText');
async function click(label) {
  await until(() =>
    evaluate(`(() => {
    const button = [...document.querySelectorAll('button')].find(b => b.textContent.trim() === ${JSON.stringify(label)} || b.getAttribute('aria-label') === ${JSON.stringify(label)});
    return Boolean(button && !button.disabled);
  })()`)
  );
  assert.ok(
    await evaluate(`(() => {
    const button = [...document.querySelectorAll('button')].find(b => b.textContent.trim() === ${JSON.stringify(label)} || b.getAttribute('aria-label') === ${JSON.stringify(label)});
    if (!button || button.disabled) return false; button.click(); return true;
  })()`),
    `Enabled button: ${label}`
  );
}
let sessionScript;
async function session(role, route, id = 'browser-user', dev = false) {
  if (sessionScript)
    await cdp('Page.removeScriptToEvaluateOnNewDocument', {
      identifier: sessionScript
    });
  const auth = {
    state: {
      token: 'fixture-token',
      refreshToken: null,
      user: { id, username: 'Browser check', role },
      role: ['SYSTEM_ADMIN', 'DISPATCHER'].includes(role) ? 'ADMIN_OPS' : role,
      isDevSession: dev
    },
    version: 0
  };
  sessionScript = (
    await cdp('Page.addScriptToEvaluateOnNewDocument', {
      source: `sessionStorage.setItem('swiftdrop-auth', ${JSON.stringify(JSON.stringify(auth))})`
    })
  ).identifier;
  await cdp('Page.navigate', { url: origin + route });
  await until(() => evaluate('document.readyState === "complete"'));
}
let empty = false,
  unavailable = false,
  chatFailure = false,
  created = 3;
const chats = [];
const reportDates = [];
const errors = [];
try {
  await until(async () => (await fetch(origin)).ok);
  chrome = spawn(
    chromePath,
    [
      '--headless',
      '--no-first-run',
      '--disable-gpu',
      '--remote-debugging-port=0',
      `--user-data-dir=${resolve(output, 'chrome-profile')}`,
      'about:blank'
    ],
    { windowsHide: true, stdio: ['ignore', 'ignore', 'pipe'] }
  );
  let debug = '';
  chrome.stderr.on('data', (chunk) => {
    debug += chunk;
  });
  await until(() => /DevTools listening on (ws:\/\/\S+)/.test(debug));
  const debugUrl = new URL(debug.match(/DevTools listening on (ws:\/\/\S+)/)[1]);
  const targets = await (await fetch(`http://${debugUrl.host}/json/list`)).json();
  ws = new WebSocket(targets.find((target) => target.type === 'page').webSocketDebuggerUrl);
  await new Promise((done) => ws.addEventListener('open', done, { once: true }));
  ws.addEventListener('message', async ({ data }) => {
    const message = JSON.parse(data);
    if (message.id) {
      const callback = pending.get(message.id);
      pending.delete(message.id);
      if (message.error) callback?.fail(new Error(message.error.message));
      else callback?.done(message.result);
    }
    if (message.method === 'Runtime.exceptionThrown') {
      const detail = message.params.exceptionDetails;
      errors.push(detail.exception?.description || detail.text);
      console.error('Browser exception:', errors.at(-1));
    }
    if (message.method !== 'Fetch.requestPaused') return;
    const { requestId, request } = message.params;
    const url = new URL(request.url);
    let value = {},
      status = 200;
    if (url.pathname.includes('/analytics/')) {
      if (unavailable) status = 503;
      if (url.pathname.endsWith('/daily')) {
        reportDates.push(url.search);
        value = {
          from: url.searchParams.get('from'),
          to: url.searchParams.get('to'),
          timezone: 'Asia/Colombo',
          days: [
            {
              date: url.searchParams.get('from'),
              created: empty ? 0 : created,
              delivered: empty ? 0 : 2,
              failed: empty ? 0 : 1
            }
          ]
        };
      } else if (url.pathname.endsWith('/success-rate'))
        value = {
          delivered: empty ? 0 : 2,
          failed: empty ? 0 : 1,
          completed: empty ? 0 : 3,
          successRatePercent: empty ? null : 66.67,
          failureRatePercent: empty ? null : 33.33
        };
      else if (url.pathname.endsWith('/workload'))
        value = {
          asOf: new Date().toISOString(),
          totalActiveOrders: empty ? 0 : 2,
          couriers: empty
            ? []
            : [
                {
                  courierId: '00000000-0000-0000-0000-000000000002',
                  activeOrders: 2
                }
              ]
        };
    } else if (url.pathname.endsWith('/ai/chat')) {
      const payload = JSON.parse(request.postData);
      chats.push(payload);
      if (chatFailure) status = Number(chatFailure);
      value = {
        reply: 'The estimated delivery fee is LKR 350.',
        conversationId: '00000000-0000-0000-0000-000000000003',
        toolCalls: [{ name: 'calculate_estimate', outcome: 'OK' }]
      };
    } else if (url.pathname.endsWith('/assignments')) value = [];
    else if (url.pathname.includes('/orders'))
      value = { content: [], totalElements: 0, totalPages: 0 };
    await cdp('Fetch.fulfillRequest', {
      requestId,
      responseCode: status,
      responseHeaders: [{ name: 'Content-Type', value: 'application/json' }],
      body: Buffer.from(JSON.stringify({ success: status === 200, data: value })).toString('base64')
    });
  });
  await cdp('Page.enable');
  await cdp('Runtime.enable');
  await cdp('Fetch.enable', { patterns: [{ urlPattern: `${origin}/api/*` }] });
  await cdp('Emulation.setDeviceMetricsOverride', {
    width: 1440,
    height: 1100,
    deviceScaleFactor: 1,
    mobile: false
  });
  await session('SYSTEM_ADMIN', '/ops/analytics');
  await until(async () => (await body()).includes('66.67%'));
  await until(() => evaluate('document.querySelectorAll(".recharts-wrapper").length === 3'));
  check('dashboard renders all three charts', true);
  check('operations pages have no chatbot', !(await body()).includes('Ask SwiftDrop'));
  await evaluate(`(() => { const input = document.querySelector('input[type=date]');
    Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set.call(input, '2026-11-01');
    input.dispatchEvent(new Event('input', { bubbles: true })); input.dispatchEvent(new Event('change', { bubbles: true })); })()`);
  await click('Apply dates');
  await until(() => reportDates.some((query) => query.includes('from=2026-11-01')));
  check('date picker requests the applied report range', true);
  created = 8;
  await click('Refresh reports');
  await until(async () =>
    (await evaluate('document.querySelector("tbody")?.textContent || ""')).includes('8')
  );
  check('refresh updates chart source and accessible table', true);
  const desktop = await cdp('Page.captureScreenshot', { format: 'png' });
  await writeFile(resolve(output, 'analytics-desktop.png'), Buffer.from(desktop.data, 'base64'));
  empty = true;
  await click('Refresh reports');
  await until(async () => (await body()).includes('No completed outcomes in this period.'));
  check(
    'empty outcomes and courier states are explicit',
    (await body()).includes('No couriers in the projection yet.')
  );
  unavailable = true;
  await click('Refresh reports');
  await until(async () => (await body()).includes('Reports could not be refreshed'));
  check(
    'refresh failure keeps previous results with an error',
    (await body()).includes('last successful report')
  );
  await session('CUSTOMER', '/customer/orders');
  await until(async () => (await body()).includes('Ask SwiftDrop'));
  await click('Ask SwiftDrop');
  await click('How much to send 2 kg from Colombo to Kandy?');
  await until(async () => (await body()).includes('LKR 350'));
  await click('Close assistant');
  await click('Ask SwiftDrop');
  check('close and reopen preserves conversation', (await body()).includes('LKR 350'));
  await evaluate(`(() => { const input = document.querySelector('input[placeholder*="Ask about"]');
    Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set.call(input, 'What about 3 kg?');
    input.dispatchEvent(new Event('input', { bubbles: true })); })()`);
  chatFailure = 503;
  await click('Send');
  await until(async () => (await body()).includes('Retry message'));
  chatFailure = false;
  await click('Retry message');
  await until(async () => !(await body()).includes('Retry message'));
  check(
    'follow-up and retry carry conversation ID',
    chats.slice(1).every((payload) => Boolean(payload.conversationId))
  );
  check(
    'retry does not duplicate the user bubble',
    (await body()).split('What about 3 kg?').length === 2
  );
  await click('New conversation');
  check('new conversation clears previous messages', !(await body()).includes('LKR 350'));
  await click('How much to send 2 kg from Colombo to Kandy?');
  await until(async () => (await body()).includes('LKR 350'));
  await evaluate(`(async () => {
    const { useAuthStore } = await import('/src/store/useAuthStore.ts');
    useAuthStore.setState({ user: { ...useAuthStore.getState().user, id: 'second-account' } });
  })()`);
  await until(async () => (await body()).includes('Ask SwiftDrop'));
  await click('Ask SwiftDrop');
  check('account switch starts with an empty assistant', !(await body()).includes('LKR 350'));
  await cdp('Emulation.setDeviceMetricsOverride', {
    width: 390,
    height: 844,
    deviceScaleFactor: 1,
    mobile: true
  });
  const mobile = await cdp('Page.captureScreenshot', { format: 'png' });
  await writeFile(resolve(output, 'assistant-mobile.png'), Buffer.from(mobile.data, 'base64'));
  check(
    'mobile page has no horizontal overflow',
    await evaluate('document.documentElement.scrollWidth <= 390')
  );
  chatFailure = 429;
  await click('How much to send 2 kg from Colombo to Kandy?');
  await until(async () => (await body()).includes('Retry in'));
  check(
    'rate limit disables composer and retry during cooldown',
    await evaluate(`
    document.querySelector('input[placeholder*="Ask about"]').disabled &&
    [...document.querySelectorAll('button')].some(b => b.textContent.includes('Retry in') && b.disabled)
  `)
  );
  chatFailure = false;
  for (const [role, route, dev] of [
    ['CUSTOMER', '/customer/orders', true],
    ['MERCHANT', '/merchant', false],
    ['COURIER', '/courier', false],
    ['DISPATCHER', '/ops/analytics', false]
  ]) {
    await session(role, route, role, dev);
    await pause(500);
    check(
      `${role}${dev ? ' dev session' : ''} cannot see customer chatbot`,
      !(await body()).includes('Ask SwiftDrop')
    );
  }
  await cdp('Page.removeScriptToEvaluateOnNewDocument', { identifier: sessionScript });
  sessionScript = undefined;
  await evaluate('sessionStorage.clear()');
  await cdp('Page.navigate', { url: origin + '/customer/orders' });
  await until(() => evaluate('location.pathname === "/login"'));
  check(
    'signed-out visitors cannot see customer chatbot',
    !(await body()).includes('Ask SwiftDrop')
  );
  check('no unhandled browser exceptions', errors.length === 0);
} catch (failure) {
  console.error('Browser body:', ws?.readyState === 1 ? await body() : 'browser unavailable');
  console.error('Browser exceptions:', errors);
  throw failure;
} finally {
  await writeFile(
    resolve(output, 'result.json'),
    JSON.stringify({ passed: checks, count: checks.length, errors }, null, 2)
  );
  ws?.close();
  chrome?.kill();
  vite.kill();
}
