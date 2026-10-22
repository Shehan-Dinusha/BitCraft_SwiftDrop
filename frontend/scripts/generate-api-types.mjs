import { mkdir, readdir } from 'node:fs/promises';
import { join, resolve } from 'node:path';
import { spawn } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const frontendRoot = resolve(fileURLToPath(new URL('..', import.meta.url)));
const specsRoot = resolve(frontendRoot, '../docs/api-specs');
const outputRoot = resolve(frontendRoot, 'src/types/generated');
const source = process.argv.includes('--source')
  ? process.argv[process.argv.indexOf('--source') + 1]
  : 'specs';

const liveSpecs = {
  'auth-iam-service': 'http://localhost:8080/api/v1/iam/v3/api-docs',
  'customer-order-service': 'http://localhost:8080/api/v1/orders/v3/api-docs',
  'pricing-zone-service': 'http://localhost:8080/api/v1/pricing/v3/api-docs',
  'dispatch-assignment-service': 'http://localhost:8080/api/v1/dispatch/v3/api-docs',
  'delivery-execution-service': 'http://localhost:8080/api/v1/deliveries/v3/api-docs',
  'merchant-b2b-service': 'http://localhost:8080/api/v1/merchants/v3/api-docs',
  'webhook-resiliency-service': 'http://localhost:8080/api/v1/webhooks/v3/api-docs',
  'notification-service': 'http://localhost:8080/api/v1/notifications/v3/api-docs',
  'analytics-ai-service': 'http://localhost:8080/api/v1/analytics/v3/api-docs'
};

await mkdir(outputRoot, { recursive: true });
const inputs =
  source === 'live'
    ? Object.entries(liveSpecs)
    : (await readdir(specsRoot))
        .filter((name) => name.endsWith('.yaml') && name !== 'README.md')
        .map((name) => [name.replace(/\.yaml$/, ''), join(specsRoot, name)]);

for (const [name, input] of inputs) {
  const output = join(outputRoot, `${name}.ts`);
  await new Promise((resolvePromise, reject) => {
    const command = process.execPath;
    const cli = join(frontendRoot, 'node_modules/openapi-typescript/bin/cli.js');
    const child = spawn(command, [cli, input, '-o', output], {
      stdio: 'inherit',
      cwd: frontendRoot
    });
    child.on('error', reject);
    child.on('exit', (code) =>
      code === 0 ? resolvePromise() : reject(new Error(`OpenAPI generation failed for ${name}`))
    );
  });
}
