// Pure-Node PNG generator for the app icon — no native deps (sharp/canvas) required.
// Draws a rounded dark-teal square with a centered gold money bag (matches the app's brand
// colors, and the 💰 money-bag emoji already used for the favicon/app tagline elsewhere).
const fs = require("fs");
const path = require("path");
const zlib = require("zlib");

const INK = [0x16, 0x30, 0x2e];      // #16302E — app background/brand ink
const GOLD = [0xb8, 0x86, 0x3b];     // #B8863B — kept for anything else that imports it
const GOLD_SOFT = [0xe4, 0xc8, 0x88]; // #E4C888
// Money-bag palette — closer to the classic 💰 emoji look (bright yellow pouch, brown tie/$)
// than the app's own muted brand gold, since that's specifically the reference being matched.
const BAG_YELLOW = [0xf2, 0xb3, 0x42];      // #F2B342 — main pouch fill
const BAG_YELLOW_SHADE = [0xd6, 0x93, 0x2c]; // #D6932C — shaded side of the pouch
const BAG_BROWN = [0x5a, 0x38, 0x1e];        // #5A381E — tie, bow, and $ mark

function crc32(buf) {
  let c, crc = 0xffffffff;
  for (let i = 0; i < buf.length; i++) {
    c = (crc ^ buf[i]) & 0xff;
    for (let k = 0; k < 8; k++) c = c & 1 ? (0xedb88320 ^ (c >>> 1)) : (c >>> 1);
    crc = (crc >>> 8) ^ c;
  }
  return (crc ^ 0xffffffff) >>> 0;
}

function chunk(type, data) {
  const len = Buffer.alloc(4);
  len.writeUInt32BE(data.length, 0);
  const typeBuf = Buffer.from(type, "ascii");
  const crcBuf = Buffer.alloc(4);
  crcBuf.writeUInt32BE(crc32(Buffer.concat([typeBuf, data])), 0);
  return Buffer.concat([len, typeBuf, data, crcBuf]);
}

function makePng(size, draw) {
  const width = size, height = size;
  const raw = Buffer.alloc((width * 4 + 1) * height);
  for (let y = 0; y < height; y++) {
    raw[y * (width * 4 + 1)] = 0; // filter: none
    for (let x = 0; x < width; x++) {
      const [r, g, b, a] = draw(x, y, width, height);
      const off = y * (width * 4 + 1) + 1 + x * 4;
      raw[off] = r; raw[off + 1] = g; raw[off + 2] = b; raw[off + 3] = a;
    }
  }
  const idat = zlib.deflateSync(raw, { level: 9 });
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(width, 0);
  ihdr.writeUInt32BE(height, 4);
  ihdr[8] = 8;  // bit depth
  ihdr[9] = 6;  // color type RGBA
  ihdr[10] = 0; ihdr[11] = 0; ihdr[12] = 0;
  const sig = Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]);
  return Buffer.concat([sig, chunk("IHDR", ihdr), chunk("IDAT", idat), chunk("IEND", Buffer.alloc(0))]);
}

function dist(x, y, cx, cy) { return Math.hypot(x - cx, y - cy); }

// Point-in-ellipse test.
function inEllipse(x, y, cx, cy, rx, ry) {
  const dx = (x - cx) / rx, dy = (y - cy) / ry;
  return dx * dx + dy * dy <= 1;
}

// Point-in-rounded-rect test (rect given by top-left + size, uniform corner radius).
function inRoundedRect(x, y, left, top, w, h, radius) {
  if (x < left || x > left + w || y < top || y > top + h) return false;
  const rLeft = left + radius, rRight = left + w - radius, rTop = top + radius, rBottom = top + h - radius;
  if (x < rLeft && y < rTop) return dist(x, y, rLeft, rTop) <= radius;
  if (x > rRight && y < rTop) return dist(x, y, rRight, rTop) <= radius;
  if (x < rLeft && y > rBottom) return dist(x, y, rLeft, rBottom) <= radius;
  if (x > rRight && y > rBottom) return dist(x, y, rRight, rBottom) <= radius;
  return true;
}

// Money-bag pictogram matching the classic "💰" emoji look directly: a round yellow pouch, a
// small brown bow tied at the top, and a brown "$" on the front — rather than the app's own
// muted brand gold, since that emoji reference is specifically what's being matched here.
// `scale` shrinks the whole motif around the canvas center — used to keep it inside the
// adaptive-icon safe zone for the foreground layer.
function drawMoneyBag(x, y, w, h, scale) {
  const cx = w / 2, cy = h / 2;

  // main pouch — a near-circle sitting slightly below center
  const bagCx = cx, bagCy = cy + h * 0.05 * scale;
  const bagR = w * 0.335 * scale;

  const inBag = inEllipse(x, y, bagCx, bagCy, bagR, bagR * 0.95);
  if (!inBag) {
    // bow: a small knot plus two floppy tie-ends, sitting just above the pouch
    const knotCx = cx, knotCy = bagCy - bagR * 0.98, knotR = w * 0.05 * scale;
    if (dist(x, y, knotCx, knotCy) < knotR) return [...BAG_BROWN, 255];
    const tieRx = w * 0.065 * scale, tieRy = h * 0.045 * scale;
    const tieCy = bagCy - bagR * 0.88;
    if (inEllipse(x, y, cx - w * 0.10 * scale, tieCy, tieRx, tieRy)) return [...BAG_BROWN, 255];
    if (inEllipse(x, y, cx + w * 0.10 * scale, tieCy, tieRx, tieRy)) return [...BAG_BROWN, 255];
    return null;
  }

  // gather line near the top of the pouch, where the fabric is cinched shut under the bow —
  // naturally follows the circle's own curve since it's only drawn where already inside inBag.
  const gatherY = bagCy - bagR * 0.62;
  if (Math.abs(y - gatherY) < h * 0.024 * scale) return [...BAG_BROWN, 255];

  // "$" mark on the front: a vertical bar through two opposite-opening rings (an S built from
  // two C-shapes) — the top ring opens right, the bottom ring opens left.
  const dollarR = bagR * 0.30, dollarRingW = w * 0.045 * scale;
  const armY = bagR * 0.22;
  const topCy = bagCy - armY, botCy = bagCy + armY;
  const dTop = dist(x, y, cx, topCy), dBot = dist(x, y, cx, botCy);
  const inTopRing = dTop < dollarR && dTop > dollarR - dollarRingW && x <= cx;
  const inBotRing = dBot < dollarR && dBot > dollarR - dollarRingW && x >= cx;
  const barTop = topCy - dollarR, barBottom = botCy + dollarR;
  const inBar = Math.abs(x - cx) < w * 0.016 * scale && y > barTop && y < barBottom;
  if (inTopRing || inBotRing || inBar) return [...BAG_BROWN, 255];

  // shaded lower-right of the pouch for a touch of roundness/depth
  const shadeCx = bagCx + bagR * 0.35, shadeCy = bagCy + bagR * 0.4;
  if (inEllipse(x, y, shadeCx, shadeCy, bagR * 0.55, bagR * 0.5)) return [...BAG_YELLOW_SHADE, 255];

  return [...BAG_YELLOW, 255];
}

// square icon with rounded corners, dark teal bg, gold money bag centered on top
function drawIcon(x, y, w, h) {
  const radius = w * 0.22; // corner radius
  const inCorner =
    (x < radius && y < radius && dist(x, y, radius, radius) > radius) ||
    (x > w - radius && y < radius && dist(x, y, w - radius, radius) > radius) ||
    (x < radius && y > h - radius && dist(x, y, radius, h - radius) > radius) ||
    (x > w - radius && y > h - radius && dist(x, y, w - radius, h - radius) > radius);
  if (inCorner) return [0, 0, 0, 0];

  const bag = drawMoneyBag(x, y, w, h, 1);
  if (bag) return bag;
  return [...INK, 255];
}

// adaptive-icon foreground: transparent bg, money bag only, slightly smaller (safe zone)
function drawForeground(x, y, w, h) {
  const bag = drawMoneyBag(x, y, w, h, 0.72);
  if (bag) return bag;
  return [0, 0, 0, 0];
}

function drawBackground(x, y, w, h) {
  return [...INK, 255];
}

// Exported so other generator scripts (e.g. gen-web-icons.js, for the PWA/iOS home-screen
// icons) can reuse the exact same drawing logic instead of duplicating the money-bag motif —
// running this file directly still regenerates the Capacitor/Android icon set as before.
module.exports = { makePng, drawIcon, drawForeground, drawBackground, drawMoneyBag, INK, GOLD, GOLD_SOFT };

if (require.main === module) {
  const outDir = process.argv[2] || "build/icons";
  fs.mkdirSync(outDir, { recursive: true });

  const sizes = [
    ["icon-1024.png", 1024, drawIcon],
    ["foreground-1024.png", 1024, drawForeground],
    ["background-1024.png", 1024, drawBackground],
    ["splash-2732.png", 2732, drawBackground],
  ];

  for (const [name, size, fn] of sizes) {
    const png = makePng(size, fn);
    fs.writeFileSync(path.join(outDir, name), png);
    console.log("wrote", name, size + "x" + size, png.length + " bytes");
  }
}
