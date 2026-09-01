// Pure-Node PNG generator for the app icon — no native deps (sharp/canvas) required.
// Draws a rounded dark-teal square with a centered gold coin (matches the app's brand colors).
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

// square icon with rounded corners, dark teal bg, gold coin with lighter ring
function drawIcon(x, y, w, h) {
  const cx = w / 2, cy = h / 2;
  const radius = w * 0.22; // corner radius
  const inCorner =
    (x < radius && y < radius && dist(x, y, radius, radius) > radius) ||
    (x > w - radius && y < radius && dist(x, y, w - radius, radius) > radius) ||
    (x < radius && y > h - radius && dist(x, y, radius, h - radius) > radius) ||
    (x > w - radius && y > h - radius && dist(x, y, w - radius, h - radius) > radius);
  if (inCorner) return [0, 0, 0, 0];

  const coinR = w * 0.28;
  const d = dist(x, y, cx, cy);
  if (d < coinR) {
    if (d > coinR * 0.82) return [...GOLD_SOFT, 255]; // ring
    // simple horizontal bar motif (like a bill/coin slot) inside the coin
    if (Math.abs(y - cy) < h * 0.035) return [...INK, 255];
    return [...GOLD, 255];
  }
  return [...INK, 255];
}

// adaptive-icon foreground: transparent bg, coin only, slightly smaller (safe zone)
function drawForeground(x, y, w, h) {
  const cx = w / 2, cy = h / 2;
  const coinR = w * 0.20;
  const d = dist(x, y, cx, cy);
  if (d < coinR) {
    if (d > coinR * 0.82) return [...GOLD_SOFT, 255];
    if (Math.abs(y - cy) < h * 0.03) return [...INK, 255];
    return [...GOLD, 255];
  }
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
