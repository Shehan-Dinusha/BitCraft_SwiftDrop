import { useState, type ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { Check, Copy } from 'lucide-react';
import { Button, SurfaceCard, Tabs } from '@/design-system';
import {
  BULK_RESPONSE,
  CREATED_EVENT,
  ERROR_RESPONSE,
  ORDER_REQUEST,
  ORDER_RESPONSE,
  STATUS_EVENT,
  VERIFY_CODE,
  orderCurl,
  ordersUrl,
  pickupReadyCurl,
  pickupReadyUrl,
  pretty
} from '../apiGuide';

function Code({ label, children }: { label: string; children: string }) {
  const [copied, setCopied] = useState(false);
  const copy = async () => {
    try {
      await navigator.clipboard.writeText(children);
      setCopied(true);
      window.setTimeout(() => setCopied(false), 2000);
    } catch {
      setCopied(false);
    }
  };
  return (
    <figure className="overflow-hidden rounded-lg border border-border-strong">
      <figcaption className="flex items-center justify-between gap-2 border-b border-border-strong bg-elevated px-3 py-1.5">
        <span className="text-caption font-semibold text-secondary">{label}</span>
        <Button
          variant="ghost"
          density="compact"
          leftIcon={copied ? <Check /> : <Copy />}
          aria-label={`Copy ${label}`}
          onClick={() => void copy()}
        >
          {copied ? 'Copied' : 'Copy'}
        </Button>
      </figcaption>
      <pre className="max-h-96 overflow-auto bg-canvas p-3 font-mono text-caption text-primary">
        {children}
      </pre>
    </figure>
  );
}

function Table({ headers, rows }: { headers: string[]; rows: ReactNode[][] }) {
  return (
    <div className="overflow-x-auto">
      <table className="w-full min-w-[520px] text-left text-caption text-primary">
        <thead className="bg-elevated text-secondary">
          <tr>
            {headers.map((header) => (
              <th key={header} scope="col" className="px-3 py-2 font-semibold">
                {header}
              </th>
            ))}
          </tr>
        </thead>
        <tbody className="divide-y divide-border-subtle">
          {rows.map((row, index) => (
            <tr key={index}>
              {row.map((cell, column) => (
                <td key={column} className="px-3 py-2 align-top">
                  {cell}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

const Mono = ({ children }: { children: ReactNode }) => (
  <code className="rounded bg-elevated px-1 py-0.5 font-mono text-caption">{children}</code>
);

function Section({ title, children }: { title: string; children: ReactNode }) {
  return (
    <SurfaceCard title={title}>
      <div className="space-y-4 text-body text-primary">{children}</div>
    </SurfaceCard>
  );
}

const ORDER_FIELDS: [string, string, string][] = [
  ['externalRef', 'Yes', 'Your own order number. Sending the same one again returns the first order.'],
  ['pickup, delivery', 'Yes', 'Two addresses with the same fields, listed below.'],
  ['… addressLine1', 'Yes', '5 to 200 characters'],
  ['… city', 'Yes', '2 to 100 characters'],
  ['… district', 'Yes', 'A Sri Lankan district, such as COLOMBO or NUWARA_ELIYA'],
  ['… postalCode', 'Yes', '5 digits'],
  ['… contactName', 'Yes', '2 to 100 characters'],
  ['… contactPhone', 'Yes', '+94 and 9 digits, 0 and 9 digits, or just the 9 digits'],
  ['parcel.weightGrams', 'One of the two', '10 to 50000'],
  ['parcel.actualWeightKg', 'One of the two', '0.01 to 50'],
  ['parcel.lengthCm, widthCm, heightCm', 'Yes', 'Up to 500 each, and 15,000 litres in all'],
  ['parcel.prohibitedItemsDeclared', 'Yes', 'Must be true: nothing prohibited is inside'],
  ['parcel.itemDescription', 'No', 'Up to 512 characters'],
  ['parcel.declaredValue', 'No', 'LKR'],
  ['speed', 'No', 'STANDARD (default) or EXPRESS']
];

const ERRORS: [string, string, string][] = [
  ['400', 'Something in the order is wrong', 'Read message, fix it and send again'],
  ['401', 'The API key is missing, wrong, revoked or expired', 'Check the X-API-Key header'],
  ['403', 'Your business is not approved', 'Wait for approval or contact SwiftDrop'],
  ['409', 'A pickup was asked for, but there are no parcels', 'Create an order first'],
  ['502, 503', 'We could not take the order just now', 'Send again with the same externalRef']
];

function Orders() {
  return (
    <div className="space-y-4">
      <Section title="Before you start">
        <ol className="list-decimal space-y-1 pl-5">
          <li>
            Create an API key on your{' '}
            <Link to="/merchant" className="text-brand-primary underline">
              dashboard
            </Link>
            . It is shown once.
          </li>
          <li>
            Keep it on your server, and send it in the <Mono>X-API-Key</Mono> header.
          </li>
        </ol>
      </Section>

      <Section title="Create an order">
        <p>
          <Mono>POST</Mono> <Mono>{ordersUrl()}</Mono>
        </p>
        <Code label="cURL">{orderCurl('YOUR_API_KEY')}</Code>
        <Code label="Request">{pretty(ORDER_REQUEST)}</Code>
        <Table
          headers={['Field', 'Required', 'Value']}
          rows={ORDER_FIELDS.map(([field, required, value]) => [
            <Mono key={field}>{field}</Mono>,
            required,
            value
          ])}
        />
        <p>
          The reply is <strong>201</strong> for a new order, or <strong>200</strong> with{' '}
          <Mono>created: false</Mono> when we already had that <Mono>externalRef</Mono>. Keep the
          tracking number.
        </p>
        <Code label="Reply">{pretty(ORDER_RESPONSE)}</Code>
      </Section>

      <Section title="Create many orders">
        <p>
          <Mono>POST</Mono> <Mono>{`${ordersUrl()}/bulk`}</Mono> with{' '}
          <Mono>{'{ "orders": [ ... ] }'}</Mono>, up to 500. Each order is checked on its own, so one
          that is wrong does not stop the others.
        </p>
        <Code label="Reply">{pretty(BULK_RESPONSE)}</Code>
      </Section>

      <Section title="Ask for a pickup">
        <p>
          <Mono>POST</Mono> <Mono>{pickupReadyUrl()}</Mono> once your parcels are packed and labelled.
          It covers every order placed so far, and is safe to repeat. No courier comes for a standard
          order before this; an express one is collected without it.
        </p>
        <Code label="cURL">{pickupReadyCurl('YOUR_API_KEY')}</Code>
        <p>
          Pay the courier in cash: one total, the <Mono>fee</Mono> of each parcel they take. Print the
          labels on your{' '}
          <Link to="/merchant" className="text-brand-primary underline">
            dashboard
          </Link>
          .
        </p>
      </Section>

      <Section title="Errors">
        <Table
          headers={['Status', 'What it means', 'What to do']}
          rows={ERRORS.map(([status, meaning, action]) => [
            <Mono key={status}>{status}</Mono>,
            meaning,
            action
          ])}
        />
        <Code label="Error reply">{pretty(ERROR_RESPONSE)}</Code>
      </Section>
    </div>
  );
}

const HEADERS: [string, string][] = [
  ['X-SwiftDrop-Event', 'The event type'],
  ['X-SwiftDrop-Event-Id', 'The same in every attempt and replay. Ignore one you already handled.'],
  ['X-SwiftDrop-Delivery-Id', 'This delivery, the same in every retry. A replay gets a new one.'],
  ['X-SwiftDrop-Attempt', '1 for the first try'],
  ['X-SwiftDrop-Timestamp', 'Unix time in seconds'],
  ['X-SwiftDrop-Signature', 'sha256= and 64 hex characters']
];

const STATUSES = [
  'CREATED',
  'PICKUP_ASSIGNED',
  'PICKED_UP',
  'IN_TRANSIT',
  'OUT_FOR_DELIVERY',
  'DELIVERY_ATTEMPTED',
  'DELIVERED',
  'RETURNING',
  'FAILED',
  'CANCELLED',
  'RETURNED_TO_SENDER'
];

function Webhooks() {
  return (
    <div className="space-y-4">
      <Section title="Before you start">
        <ol className="list-decimal space-y-1 pl-5">
          <li>
            Add a public HTTPS address on the{' '}
            <Link to="/merchant/webhooks" className="text-brand-primary underline">
              Webhooks
            </Link>{' '}
            page and choose its events.
          </li>
          <li>Copy the signing secret. It is shown once.</li>
          <li>Press Test to send yourself a message.</li>
        </ol>
      </Section>

      <Section title="What we send">
        <p>
          A <Mono>POST</Mono> with JSON when an order is booked (<Mono>order.created</Mono>) or its
          status changes (<Mono>parcel.status.changed</Mono>). A test is{' '}
          <Mono>webhook.test.ping</Mono>, sent once and never retried.
        </p>
        <Code label="parcel.status.changed">{pretty(STATUS_EVENT)}</Code>
        <p className="flex flex-wrap items-center gap-1.5">
          <Mono>status</Mono> is one of
          {STATUSES.map((status) => (
            <Mono key={status}>{status}</Mono>
          ))}
        </p>
        <p>
          We send one only when the status changes, and it can go back: for example from{' '}
          <Mono>OUT_FOR_DELIVERY</Mono> to <Mono>IN_TRANSIT</Mono> when the courier brings the parcel
          back to the hub. <Mono>failureReason</Mono>, when present, says why a delivery attempt or
          the parcel failed.
        </p>
        <Code label="order.created">{pretty(CREATED_EVENT)}</Code>
        <p>
          <Mono>merchantOrderReference</Mono> is empty for now. Match events to your orders by{' '}
          <Mono>orderId</Mono> or <Mono>trackingNumber</Mono>.
        </p>
        <Table
          headers={['Header', 'Meaning']}
          rows={HEADERS.map(([name, meaning]) => [<Mono key={name}>{name}</Mono>, meaning])}
        />
      </Section>

      <Section title="Check that it is from us">
        <p>
          Join the timestamp, a dot and the body exactly as received, sign them with HMAC-SHA256 and
          your signing secret, and compare with <Mono>X-SwiftDrop-Signature</Mono>.
        </p>
        <Code label="Node.js">{VERIFY_CODE}</Code>
      </Section>

      <Section title="Answer and retries">
        <p>
          Answer with any <strong>2xx</strong> within 5 seconds, and do slow work afterwards. We
          send a parcel&apos;s events one at a time, oldest first, but a replay or an event we
          received late can arrive after a newer one: compare <Mono>occurredAt</Mono> and keep the
          latest status.
        </p>
        <Table
          headers={['Your answer', 'What happens']}
          rows={[
            ['2xx', 'Delivered'],
            [
              'No answer, 408, 425, 429 or 5xx',
              'We try again after about 30 seconds, 2 minutes and 10 minutes, 4 tries in all'
            ],
            ['3xx or any other 4xx', 'We stop at once']
          ]}
        />
        <p>
          A <Mono>Retry-After</Mono> on 429 or 503 can make us wait longer, up to 10 minutes. While an
          address is paused we keep its events and send them when you activate it again.
        </p>
        <p>
          Events we stopped sending are under{' '}
          <Link to="/merchant/webhooks" className="text-brand-primary underline">
            Failed events
          </Link>
          , where you can replay them. A replay sends the same event again, with the same{' '}
          <Mono>X-SwiftDrop-Event-Id</Mono>; it can be replayed again only if the replay failed too.
        </p>
      </Section>
    </div>
  );
}

/** How a merchant's own system sends us orders and hears about them. */
export function ApiGuidePage() {
  const [tab, setTab] = useState('orders');
  return (
    <main className="mx-auto max-w-5xl space-y-4 p-4 md:p-6" data-density="compact">
      <h1 className="text-h1 text-primary">API guide</h1>
      <Tabs
        aria-label="API guide"
        tabs={[
          { id: 'orders', label: 'Send orders' },
          { id: 'webhooks', label: 'Receive updates' }
        ]}
        value={tab}
        onChange={setTab}
      />
      <div role="tabpanel" aria-label={tab === 'orders' ? 'Send orders' : 'Receive updates'}>
        {tab === 'orders' ? <Orders /> : <Webhooks />}
      </div>
    </main>
  );
}

export default ApiGuidePage;
