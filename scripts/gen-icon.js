// Pure-Node PNG generator for the app icon — no native deps (sharp/canvas) required.
// Draws a rounded dark-teal square with a centered gold wallet (matches the app's brand colors).
const fs = require("fs");
const path = require("path");
const zlib = require("zlib");

const INK = [0x16, 0x30, 0x2e];   // #16302E
const GOLD = [0xb8, 0x86, 0x3b];  // #B8863B
const GOLD_SOFT = [0xe4, 0xc8, 0x88]; // #E4C888

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

// Wallet pictogram: a billfold body with a card peeking out above it and a round clasp/button
// on its side — the standard flat-icon "wallet" silhouette (distinct from a plain card, which
// is just a rectangle). `scale` shrinks the whole motif around the canvas center — used to keep
// it inside the adaptive-icon safe zone for the foreground layer.
function drawWallet(x, y, w, h, scale) {
  const cx = w / 2, cy = h / 2;

  // main billfold body
  const bw = w * 0.60 * scale, bh = h * 0.38 * scale;
  const bLeft = cx - bw / 2, bTop = cy - bh / 2 + h * 0.05 * scale;
  const bRadius = bh * 0.20;

  // a card peeking out the top, offset toward the right, mostly hidden behind the body
  const cw = w * 0.30 * scale, ch = h * 0.24 * scale;
  const cLeft = cx - cw * 0.10, cTop = bTop - ch * 0.55;
  const cRadius = ch * 0.16;

  // clasp/button on the body's right side
  const claspR = bh * 0.18;
  const claspCx = bLeft + bw * 0.84, claspCy = bTop + bh * 0.55;

  if (inRoundedRect(x, y, bLeft, bTop, bw, bh, bRadius)) {
    const d = dist(x, y, claspCx, claspCy);
    if (d < claspR) return [...INK, 255];
    if (d < claspR * 1.45) return [...GOLD_SOFT, 255];
    // thin fold crease near the top of the body
    if (Math.abs(y - (bTop + bh * 0.24)) < h * 0.010 * scale) return [...INK, 255];
    return [...GOLD_SOFT, 255];
  }
  if (inRoundedRect(x, y, cLeft, cTop, cw, ch, cRadius)) {
    return [...GOLD, 255]; // the peeking card, darker gold for contrast against the body
  }
  return null; // outside the wallet shape
}

// square icon with rounded corners, dark teal bg, gold wallet centered on top
function drawIcon(x, y, w, h) {
  const radius = w * 0.22; // corner radius
  const inCorner =
    (x < radius && y < radius && dist(x, y, radius, radius) > radius) ||
    (x > w - radius && y < radius && dist(x, y, w - radius, radius) > radius) ||
    (x < radius && y > h - radius && dist(x, y, radius, h - radius) > radius) ||
    (x > w - radius && y > h - radius && dist(x, y, w - radius, h - radius) > radius);
  if (inCorner) return [0, 0, 0, 0];

  const wallet = drawWallet(x, y, w, h, 1);
  if (wallet) return wallet;
  return [...INK, 255];
}

// adaptive-icon foreground: transparent bg, wallet only, slightly smaller (safe zone)
function drawForeground(x, y, w, h) {
  const wallet = drawWallet(x, y, w, h, 0.72);
  if (wallet) return wallet;
  return [0, 0, 0, 0];
}

function drawBackground(x, y, w, h) {
  return [...INK, 255];
}

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
