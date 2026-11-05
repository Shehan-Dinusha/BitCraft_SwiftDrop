# Shipping label (page 1): design rationale

The label was redesigned from a UI/UX engineering review: research on carrier labels and label standards, a critique of the first version, and a zone-by-zone spec. It was then implemented and checked against real renders and scans. This file records why the label looks the way it does, so reviewers and future agents can keep it consistent.

## Who reads the label, and how

| Reader | Situation | What they need first |
|---|---|---|
| **Hub sorter** | Arm's length, 1–2 seconds, conveyor | Service level, destination district and postcode, and a barcode to scan |
| **Courier** | At the door, often outdoors | Recipient name, phone, address, cash to collect |
| **Recipient / return** | If delivery fails | Who sent it and where it goes back to |

So the zones run **top to bottom in that order**. The heavier 2 pt rule under the barcode separates the "sorting half" from the "delivery half".

## Rules the layout follows

1. **100 × 150 mm (4 × 6 in) label** on the A5 sheet. That's the size of FedEx, UPS, DHL and USPS labels and label pouches. It has a 1.5 pt frame and one short line of printing help underneath: "Print at actual size, cut out the label and tape it flat on the parcel."
2. **Colour is a second signal, never the only one.** Senders print on home laser and inkjet printers, often in greyscale, so the label is designed to work in black and white first. Colour is added on top:
   - **Service colour:** blue-700 for Standard, amber-800 for Express, violet-700 for Same day. It's used on the service icon and on a 4 pt band across the top of the label, so priority is visible from across a sorting floor.
   - **Cash on delivery** uses red-700 for the COD tag, the panel border, the COD square and the panel caption, plus a red-50 tint behind the panel.
   - **The "Drop" in the wordmark** is brand blue.
   - **The barcode, QR code, addresses, names, district, postcode and the COD amount stay pure black.** Scanning and reading never depend on colour.
   - Every colour is a deep shade. White text on each one is **6.5–7.1:1** (WCAG AA needs 4.5:1), and each still prints as a dark grey. Express was moved from amber-700 to amber-800 after the greyscale check, because amber-700 printed too light.
   - The red-50 tint prints near white in greyscale. There, the 2.5 pt border and the solid COD square still carry the message.
   - No full-width solid fills, and no grey text inside the label.
3. **Nothing depends on colour:**
   - **Service icon** (16 mm): Standard is an outlined **S**, Express a solid **E**, and Same day a solid **SD** with an inset white rule. They differ by fill *and* by letters, as USPS service icons do. The colour only repeats that difference.
   - **Cash on delivery** gets a "COD" tag in the header, plus a 2.5 pt bordered panel with a solid COD square and the amount at 17 pt. Without COD, the label says "NO CASH TO COLLECT", so it's never left to guesswork.
4. **Scannable barcodes:**
   - **Code 128** of the tracking number, drawn as **vector** with OpenPDF `Barcode128`.
   - X = **1.2 pt**, which is 5 printer dots at 300 dpi. A whole number of dots means no smeared or uneven bars.
   - Bars are **18 mm** tall, above the 12.7 mm small-parcel minimum.
   - **10X quiet zones** on each side. The digits are printed below the bars.
   - If a code is ever too wide, the bar width falls back to 0.96 pt, which is still a whole number of dots.
5. **The QR code** is drawn as **vector**. It has error correction **Q** (it survives scuffs and tape), a 4-module quiet zone inside its 22 mm square, and opens `<base>/track/<trackingNumber>`.
6. **Fixed zones, fitted text.** Nothing can push other blocks around:
   - The district shrinks from 18 to 14 pt to fit, and the recipient name from 14 to 11 pt over at most 2 lines.
   - The street address shrinks from 10 to 6.5 pt. It wraps beside the QR code, then continues full width underneath. The city line follows the address.
   - Tested with a 36-character name and a 145-character address: nothing is cut off.
7. **Privacy on an object anyone can read:**
   - Printed: the recipient's name, phone and address; the sender's name and return address.
   - **Not printed:** the sender's phone, any email, the fee, the declared value, item contents (a theft risk), and internal IDs.

## Zones (top to bottom)

| # | Zone | Height | Content |
|---|---|---|---|
| 1 | Header | 16 mm | "SwiftDrop" with "swiftdrop.lk"; the service word; the 16 mm service icon; a COD tag when there's cash to collect |
| 2 | Sort | 22 mm | "DELIVERY DISTRICT" in caps (18 pt), the **province** (worked out from the district), and the **postcode at 30 pt** |
| 3 | Barcode | 30 mm | Vector Code 128 with quiet zones, and the tracking number in Courier-Bold 16 pt |
| 4 | Deliver to | 44 mm | Recipient name, "Tel +94 …", address, city and postcode; the QR code on the right with "Scan to track" |
| 5 | Cash and parcel | 18 mm | The COD panel or "NO CASH TO COLLECT"; weight; "1 of 1" piece |
| 6 | Return to | 10 mm | "IF UNDELIVERED, RETURN TO", the sender's name in bold, then their address |
| 7 | Info strip | 6 mm | Booked date, size, tracking site |

Under the label: one line, "Print at actual size, cut out the label and tape it flat on the parcel." (An earlier dashed cut line and 50 mm check bar were removed to keep the page simple.)

## Deliberately not done

- **No hub or sort code number.** Carriers such as DHL print sort codes from their own routing data. SwiftDrop has no routing table, so the label shows only the district, province and postcode, which come directly from the order. Add a sort code only once operations supplies the mapping.
- **No colour-only signals,** and no full-width solid bars.

## Sources consulted by the review

The engineer could search the web, but couldn't open the pages, so these come from search results. Items marked [K] are well-established practice that wasn't checked against the primary document.

- GS1 General Specifications, the symbol size table, and the GS1 size guide (gs1.se): Code 128 / GS1-128 minimum bar width, minimum bar height, and 10X quiet zones.
- USPS Parcel Labeling Guide and Publication 199: 4×6 label zones, the service icon block and barcode clear zones.
- DHL Paket routing code guide and DHL eCommerce UK self-label guide: sort codes come from carrier data.
- Shiprocket knowledge base and Ship&co: how COD is shown on labels, with the exact amount to collect.
- Daraz Sri Lanka seller guides: sellers print their own waybills, the same situation as SwiftDrop.
- [K] UPS and FedEx 4×6 zone order; ISO/IEC 15416 (1D print quality); ISO/IEC 18004 (QR quiet zone).

## Verification

- `OrderPdfGeneratorServiceTest` decodes the label QR matrix, the label Code 128 (drawn with the same settings as the label) and the delivery receipt QR read back from the PDF, and checks the barcode and its quiet zones fit the label width.
- Content tests check what the label shows (recipient name, phone and address, cash to collect) and what it never shows (sender phone, email, order id).
- Not covered by tests: a real scan of a printed label. Print one at actual size on a home printer and scan it with a phone before release.
