/**
 * Sample requests, replies and code for merchants. The API guide shows them, and the API key dialog offers the
 * order as a ready command, so a merchant never sees two versions. Keep them equal to the real contracts:
 * B2bOrderRequest, B2bOrderResult and B2bBulkResponse (merchant-b2b-service), WebhookPayloadV1 and WebhookSigner
 * (webhook-resiliency-service).
 */
export const ordersUrl = () => `${window.location.origin}/api/v1/b2b/orders`;

export const pickupReadyUrl = () => `${window.location.origin}/api/v1/b2b/pickups/ready`;

export const pickupReadyCurl = (apiKey: string) =>
  `curl -X POST ${pickupReadyUrl()} \\\n  -H "X-API-Key: ${apiKey}"`;

export const ORDER_REQUEST = {
  externalRef: 'SHOP-001',
  pickup: {
    addressLine1: '10 Galle Road',
    city: 'Colombo',
    district: 'COLOMBO',
    postalCode: '00300',
    contactName: 'Test Shop',
    contactPhone: '+94771234567'
  },
  delivery: {
    addressLine1: '25 Kandy Road',
    city: 'Gampaha',
    district: 'GAMPAHA',
    postalCode: '11000',
    contactName: 'Test Recipient',
    contactPhone: '+94711234567'
  },
  parcel: {
    weightGrams: 1500,
    lengthCm: 30,
    widthCm: 20,
    heightCm: 10,
    prohibitedItemsDeclared: true,
    itemDescription: 'Books'
  },
  speed: 'STANDARD'
};

const ORDER_ID = '0f0d6e8b-7ea6-4a34-b31d-ab984b0ba7ca';

export const ORDER_RESPONSE = {
  success: true,
  message: 'Merchant order accepted',
  data: {
    externalRef: 'SHOP-001',
    orderId: ORDER_ID,
    trackingNumber: 'SD-7K4M-9QX2',
    created: true,
    fee: 430,
    currency: 'LKR'
  },
  timestamp: '2026-11-22T06:10:00Z'
};

export const BULK_RESPONSE = {
  success: true,
  message: 'Bulk processed',
  data: {
    total: 2,
    successCount: 1,
    failedCount: 1,
    items: [
      {
        externalRef: 'SHOP-001',
        orderId: ORDER_ID,
        trackingNumber: 'SD-7K4M-9QX2',
        created: true,
        fee: 430,
        currency: 'LKR'
      },
      {
        externalRef: 'SHOP-002',
        created: false,
        error: 'delivery.contactPhone: Enter 9 digits, with or without 0 or +94 in front'
      }
    ]
  },
  timestamp: '2026-11-22T06:10:00Z'
};

export const ERROR_RESPONSE = {
  status: 400,
  error: 'Validation Failed',
  message: 'delivery.contactPhone: Enter 9 digits, with or without 0 or +94 in front',
  path: '/api/v1/b2b/orders',
  timestamp: '2026-11-22T06:10:00Z'
};

export const STATUS_EVENT = {
  id: '4b1c6f2e-8d52-4f3a-9a55-2f6f0c1d7e90',
  type: 'parcel.status.changed',
  apiVersion: 1,
  occurredAt: '2026-11-22T09:30:00Z',
  data: {
    orderId: ORDER_ID,
    merchantOrderReference: null,
    trackingNumber: 'SD-7K4M-9QX2',
    status: 'OUT_FOR_DELIVERY',
    previousStatus: 'IN_TRANSIT',
    failureReason: null
  }
};

export const CREATED_EVENT = {
  id: '9c0e7a11-3a7b-4d0e-8c43-5d1b6b0f2a77',
  type: 'order.created',
  apiVersion: 1,
  occurredAt: '2026-11-22T06:10:00Z',
  data: {
    orderId: ORDER_ID,
    merchantOrderReference: null,
    trackingNumber: 'SD-7K4M-9QX2',
    speed: 'STANDARD'
  }
};

export const pretty = (value: unknown) => JSON.stringify(value, null, 2);

export const orderCurl = (apiKey: string) =>
  `curl --request POST '${ordersUrl()}' \\
  --header 'X-API-Key: ${apiKey}' \\
  --header 'Content-Type: application/json' \\
  --data '${JSON.stringify(ORDER_REQUEST)}'`;

export const orderPostman = (apiKey: string) =>
  pretty({
    info: {
      name: 'SwiftDrop merchant order',
      schema: 'https://schema.getpostman.com/json/collection/v2.1.0/collection.json'
    },
    item: [
      {
        name: 'Create order',
        request: {
          method: 'POST',
          url: ordersUrl(),
          header: [
            { key: 'X-API-Key', value: apiKey },
            { key: 'Content-Type', value: 'application/json' }
          ],
          body: {
            mode: 'raw',
            raw: pretty(ORDER_REQUEST),
            options: { raw: { language: 'json' } }
          }
        }
      }
    ]
  });

/** Checks a webhook in Node.js. `rawBody` is the request body exactly as received, before any JSON parsing. */
export const VERIFY_CODE = `const crypto = require('crypto');

function isFromSwiftDrop(rawBody, headers, secret) {
  const timestamp = headers['x-swiftdrop-timestamp'];
  const signature = headers['x-swiftdrop-signature'];
  if (!/^\\d{1,12}$/.test(timestamp || '') || !signature) return false;

  // Refuse anything more than 5 minutes old.
  if (Math.abs(Date.now() / 1000 - Number(timestamp)) > 300) return false;

  const expected = 'sha256=' + crypto
    .createHmac('sha256', secret)
    .update(timestamp + '.')
    .update(rawBody)
    .digest('hex');
  return expected.length === signature.length
    && crypto.timingSafeEqual(Buffer.from(expected), Buffer.from(signature));
}`;
