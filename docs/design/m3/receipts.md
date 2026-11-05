# Order PDFs: label, fee page and delivery receipt

Owner: `customer-order-service` (Member 3). Code: `OrderPdfGeneratorService`, download endpoints in `OrderController`.

## What gets generated

| PDF | Endpoint | Pages | Download name |
|---|---|---|---|
| Shipping label and booking receipt | `GET /api/v1/orders/{orderId}/shipping-label` | 1 label (100 x 150 mm, stuck on the parcel), 2 booking receipt | `receipt-<trackingNumber>.pdf` |
| Delivery receipt | `GET /api/v1/orders/{orderId}/delivery-receipt` | 1 | `delivery-receipt-<trackingNumber>.pdf` |

Both are sent as an attachment with `Cache-Control: no-store`, because they hold personal data. The file name keeps only letters, digits and hyphens of the tracking number (`CustomerOrderService.PdfDownload.fileName`). Ownership rules from PR 1 are unchanged: a non-owner gets 404 and no PDF.

## Decisions

1. **Which pages are customer-facing.** Page 1 is on the outside of the parcel, so the courier can see it. It keeps the **recipient's phone** (the courier needs it to deliver). The sender phone, the recipient email and the internal order id are removed from it. Page 2 and the delivery receipt carry **no phone, email or street address**; they show cities and districts only. The label layout and its reasons are in [label-design.md](label-design.md).
2. **"Prepaid / Standard" removed.** Nothing in the code records a prepayment, so the label now says "No cash to collect" when there is no COD amount.
3. **Receipt timing: generated on demand, not stored.** No new table and no stored file. The PDF always reflects current data (for example a revised fee) and there is nothing extra to keep private or clean up. The cost is a few milliseconds per download, which is fine for A5 PDFs.
4. **Booking receipt shows the stored fee only.** The order keeps one total (`estimatedFee`), not a base/weight split, and the pricing service is the only source of fees. The page therefore prints the total with service level and billable weight instead of recomputing a breakdown.
5. **QR code base URL comes from the environment.** `swiftdrop.public-tracking-base-url` (`PUBLIC_TRACKING_BASE_URL`, default `https://swiftdrop.lk`). The QR points to `<base>/track/<trackingNumber>`, matching the frontend route `/track/:trackingNumber`. A trailing `/` is stripped and anything that is not http(s) stops the service at startup.
6. **Logging.** The PDF code logs only the order id (and never names, phones, emails or addresses).
7. **Look.** All three documents share one A5 design (navy/slate palette, footer with tracking number and page count). Amounts print as `LKR 1,185.00`, times in Asia/Colombo, districts as `Nuwara Eliya`. The delivery receipt lists the fee and the cash collected as separate rows with no "total paid", and no longer says "Tax Invoice". Fonts are the built-in Helvetica/Courier, so Sinhala/Tamil names would print blank until a Unicode font is embedded (team decision).
8. **Known limits.** The delivery receipt uses `updatedAt` as the delivery time (there is no delivered-at field; PR 3 status history could supply a better one). Sinhala and Tamil text prints blank until a Unicode font is embedded.
