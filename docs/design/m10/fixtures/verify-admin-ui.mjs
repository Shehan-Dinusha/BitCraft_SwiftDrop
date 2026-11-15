// Live admin acceptance: requires the local demo on 5173/8080 and a Chrome path.
// Uses real IAM and account-status APIs; never calls a paid AI model.
import { spawn } from "node:child_process";
import { mkdir, readFile, writeFile } from "node:fs/promises";
import { resolve } from "node:path";
import assert from "node:assert/strict";
const output = resolve(
  "backend/analytics-ai-service/target/admin-ui-acceptance",
);
await mkdir(output, { recursive: true });
const chromePath = process.argv[2];
if (!chromePath) throw new Error("Provide a Chrome executable path");
const savedSession = await readFile(
  resolve("backend/analytics-ai-service/target-ai/frontend-demo/session.json"),
  "utf8",
)
  .then(JSON.parse)
  .catch(() => undefined);
const password = process.env.M10_DEMO_PASSWORD || savedSession?.password;
if (!password)
  throw new Error(
    "Set M10_DEMO_PASSWORD or start the isolated local demo first",
  );
const origin = "http://localhost:5173";
const checks = [],
  errors = [];
async function api(path, body, token, method) {
  const response = await fetch("http://localhost:8080" + path, {
    method: method || (body ? "POST" : "GET"),
    headers: {
      "Content-Type": "application/json",
      ...(token ? { Authorization: "Bearer " + token } : {}),
    },
    body: body ? JSON.stringify(body) : undefined,
  });
  const result = await response.json();
  return { status: response.status, result };
}
const login = await api("/api/v1/auth/login", {
  username:
    process.env.M10_DEMO_ADMIN || savedSession?.username || "m10.ai.demo",
  password,
});
assert.equal(login.status, 200);
const auth = login.result;
const username = "m10.ui.confirm." + Date.now().toString(36);
const created = await api(
  "/api/v1/users",
  {
    username,
    email: username + "@example.test",
    firstName: "Confirmation",
    lastName: "Demo",
    password,
    role: "CUSTOMER",
    district: "COLOMBO",
  },
  auth.accessToken,
);
assert.equal(created.status, 201);
const accountId = created.result.data.id;
let chrome, ws;
let next = 0;
const pending = new Map();
const pause = (ms) => new Promise((done) => setTimeout(done, ms));
async function until(action) {
  for (let i = 0; i < 250; i++) {
    try {
      if (await action()) return;
    } catch {
      /* Navigation briefly replaces the document and execution context. */
    }
    await pause(100);
  }
  throw new Error("Browser check timed out");
}
function check(name, value) {
  assert.ok(value, name);
  checks.push(name);
  console.log("PASS " + name);
}
function cdp(method, params = {}) {
  return new Promise((done, fail) => {
    const id = ++next;
    pending.set(id, { done, fail });
    ws.send(JSON.stringify({ id, method, params }));
  });
}
async function evaluate(expression) {
  const r = await cdp("Runtime.evaluate", {
    expression,
    returnByValue: true,
    awaitPromise: true,
  });
  if (r.exceptionDetails) throw new Error(r.exceptionDetails.text);
  return r.result.value;
}
const body = () => evaluate("document.body.innerText");
async function click(label) {
  const expression = `(() => {const b=[...document.querySelectorAll('button')].find(x=>x.textContent.trim()===${JSON.stringify(label)} || x.getAttribute('aria-label')===${JSON.stringify(label)});if(!b||b.disabled)return false;b.click();return true;})()`;
  await until(() => evaluate(expression));
}
async function screenshot(name) {
  const r = await cdp("Page.captureScreenshot", {
    format: "png",
    captureBeyondViewport: true,
  });
  await writeFile(resolve(output, name), Buffer.from(r.data, "base64"));
}
async function search(value) {
  await evaluate(
    `(() => {const e=document.querySelector('input[type="search"]');Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set.call(e,${JSON.stringify(value)});e.dispatchEvent(new Event('input',{bubbles:true}));})()`,
  );
  await pause(600);
}
try {
  chrome = spawn(
    chromePath,
    [
      "--headless",
      "--no-first-run",
      "--disable-gpu",
      "--remote-debugging-port=0",
      `--user-data-dir=${resolve(output, "chrome-" + Date.now())}`,
      "about:blank",
    ],
    { windowsHide: true, stdio: ["ignore", "ignore", "pipe"] },
  );
  let debug = "";
  chrome.stderr.on("data", (chunk) => {
    debug += chunk;
  });
  await until(() => /DevTools listening on (ws:\/\/\S+)/.test(debug));
  const debugUrl = new URL(
    debug.match(/DevTools listening on (ws:\/\/\S+)/)[1],
  );
  const targets = await (
    await fetch(`http://${debugUrl.host}/json/list`)
  ).json();
  ws = new WebSocket(
    targets.find((t) => t.type === "page").webSocketDebuggerUrl,
  );
  await new Promise((done) =>
    ws.addEventListener("open", done, { once: true }),
  );
  let failStatus = false;
  ws.addEventListener("message", async ({ data }) => {
    const m = JSON.parse(data);
    if (m.id) {
      const cb = pending.get(m.id);
      pending.delete(m.id);
      if (m.error) cb?.fail(new Error(m.error.message));
      else cb?.done(m.result);
    }
    if (m.method === "Runtime.exceptionThrown")
      errors.push(
        m.params.exceptionDetails.exception?.description ||
          m.params.exceptionDetails.text,
      );
    if (m.method === "Fetch.requestPaused") {
      if (failStatus && m.params.request.method === "PATCH")
        await cdp("Fetch.fulfillRequest", {
          requestId: m.params.requestId,
          responseCode: 503,
          responseHeaders: [
            { name: "Content-Type", value: "application/json" },
          ],
          body: Buffer.from(
            JSON.stringify({ message: "Temporary test outage. Please retry." }),
          ).toString("base64"),
        });
      else
        await cdp("Fetch.continueRequest", { requestId: m.params.requestId });
    }
  });
  await cdp("Page.enable");
  await cdp("Runtime.enable");
  await cdp("Emulation.setDeviceMetricsOverride", {
    width: 1440,
    height: 1000,
    deviceScaleFactor: 1,
    mobile: false,
  });
  const session = {
    state: {
      token: auth.accessToken,
      refreshToken: auth.refreshToken,
      user: {
        id: auth.userId,
        username: auth.username,
        email: auth.email,
        role: auth.role,
        district: auth.district,
      },
      role: "ADMIN_OPS",
      isDevSession: false,
    },
    version: 0,
  };
  await cdp("Page.addScriptToEvaluateOnNewDocument", {
    source: `sessionStorage.setItem('swiftdrop-auth',${JSON.stringify(JSON.stringify(session))})`,
  });
  await cdp("Page.navigate", { url: origin + "/ops/users" });
  await until(async () => (await body()).includes("Account directory"));
  check(
    "admin header utilities removed",
    !(await evaluate(
      `Boolean(document.querySelector('header button[aria-label="Assistant"],header button[aria-label="Search"]'))`,
    )) && !(await body()).includes("Search everything"),
  );
  await until(() => evaluate('document.querySelectorAll("tbody tr").length>0'));
  check(
    "roles are read-only badges",
    await evaluate('document.querySelectorAll("tbody select").length===0'),
  );
  await search(username);
  await until(async () => (await body()).includes(username));
  await until(() =>
    evaluate('document.querySelectorAll("tbody tr").length===1'),
  );
  check(
    "active account exposes only Suspend",
    await evaluate(
      'document.querySelectorAll("tbody button").length === 1 && document.querySelector("tbody button").textContent.trim() === "Suspend"',
    ),
  );
  await click("Suspend");
  await until(() =>
    evaluate('Boolean(document.querySelector("[role=dialog]"))'),
  );
  check(
    "suspension opens named confirmation",
    await evaluate(
      `document.querySelector('[role=dialog]').innerText.includes(${JSON.stringify(username)})`,
    ),
  );
  await screenshot("suspend-confirmation.png");
  await click("Cancel");
  check(
    "cancelling keeps account active",
    (await api("/api/v1/users/" + accountId, undefined, auth.accessToken))
      .result.data.status === "ACTIVE",
  );
  await click("Suspend");
  await click("Suspend account");
  await until(async () =>
    (await body()).includes("was suspended successfully"),
  );
  check(
    "confirmed suspension persisted",
    (await api("/api/v1/users/" + accountId, undefined, auth.accessToken))
      .result.data.status === "SUSPENDED",
  );
  check(
    "suspended account exposes only Reactivate",
    await evaluate(
      'document.querySelectorAll("tbody button").length === 1 && document.querySelector("tbody button").textContent.trim() === "Reactivate"',
    ),
  );
  await click("Reactivate");
  await click("Reactivate account");
  await until(async () =>
    (await body()).includes("was reactivated successfully"),
  );
  check(
    "reactivation persisted and success announced",
    (await api("/api/v1/users/" + accountId, undefined, auth.accessToken))
      .result.data.status === "ACTIVE",
  );
  failStatus = true;
  await cdp("Fetch.enable", {
    patterns: [{ urlPattern: "*/api/v1/users/*/status" }],
  });
  await click("Suspend");
  await click("Suspend account");
  await until(async () => (await body()).includes("Temporary test outage"));
  check(
    "failed status update stays in dialog with error",
    await evaluate(
      'Boolean(document.querySelector("[role=dialog] [role=alert]"))',
    ),
  );
  await click("Cancel");
  await cdp("Fetch.disable");
  failStatus = false;
  await search("");
  await until(() => evaluate('document.querySelectorAll("tbody tr").length>1'));
  await screenshot("users-desktop.png");
  const roleChange = await api(
    "/api/v1/users/" + accountId + "/role",
    { role: "COURIER" },
    auth.accessToken,
    "PATCH",
  );
  check("backend rejects direct role mutation", roleChange.status === 409);
  await cdp("Page.navigate", { url: origin + "/ops/analytics" });
  await until(() =>
    evaluate('document.querySelectorAll(".recharts-wrapper").length===3'),
  );
  check("analytics renders three charts from live services", true);
  check(
    "metric font matches the interface",
    !(await evaluate(`Boolean(document.querySelector('main .font-mono'))`)),
  );
  await screenshot("analytics-desktop.png");
  await click("Last 30 days");
  await until(() =>
    evaluate(
      '[...document.querySelectorAll("button[aria-pressed=true]")].some(button => button.textContent === "Last 30 days")',
    ),
  );
  check("quick date range applies", true);
  await cdp("Emulation.setDeviceMetricsOverride", {
    width: 390,
    height: 844,
    deviceScaleFactor: 1,
    mobile: true,
  });
  await pause(500);
  check(
    "analytics fits mobile width",
    await evaluate("document.documentElement.scrollWidth<=window.innerWidth"),
  );
  await screenshot("analytics-mobile.png");
  check("no unhandled browser exceptions", errors.length === 0);
  await writeFile(
    resolve(output, "result.json"),
    JSON.stringify({ count: checks.length, checks, errors }, null, 2),
  );
} finally {
  // Leave the disposable confirmation account active even if a later assertion fails.
  await api(
    "/api/v1/users/" + accountId + "/status",
    { status: "ACTIVE" },
    auth.accessToken,
    "PATCH",
  ).catch(() => {});
  if (ws) ws.close();
  if (chrome) chrome.kill();
}
