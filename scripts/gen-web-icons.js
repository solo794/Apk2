// Generates the PWA/iOS home-screen icon set into www/icons/, reusing the exact money-bag
// drawing logic from gen-icon.js (same colors/motif as the Android app icon) instead of the
// mismatched emoji-in-SVG placeholder that was in <head> before — iOS Safari doesn't support SVG
// apple-touch-icons at all (silently falls back to a screenshot thumbnail), so that icon never
// actually showed up correctly on an iPhone home screen.
const fs = require("fs");
const path = require("path");
const { makePng, drawIcon, drawBackground, drawMoneyBag, INK } = require("./gen-icon.js");

// apple-touch-icon must be a plain opaque square — iOS applies its own rounded-corner mask, so a
// source icon with pre-rounded transparent corners (like drawIcon()) would show those corners as
// black/broken instead of blending in. This is drawIcon() minus the corner-transparency clip.
function drawAppleTouchIcon(x, y, w, h) {
  const bag = drawMoneyBag(x, y, w, h, 1);
  if (bag) return bag;
  return [...INK, 255];
}

// Android/Chrome "maskable" icon: full-bleed background with the money bag kept inside the safe
// zone (same 0.72 scale used for the Capacitor adaptive-icon foreground) so it isn't clipped
// when the OS applies a circle/squircle/rounded-square mask over it.
function drawMaskable(x, y, w, h) {
  const bag = drawMoneyBag(x, y, w, h, 0.72);
  if (bag) return bag;
  return [...INK, 255];
}

const outDir = path.join(__dirname, "..", "www", "icons");
fs.mkdirSync(outDir, { recursive: true });

const files = [
  ["apple-touch-icon.png", 180, drawAppleTouchIcon],
  ["icon-192.png", 192, drawIcon],
  ["icon-512.png", 512, drawIcon],
  ["maskable-512.png", 512, drawMaskable],
];

for (const [name, size, fn] of files) {
  const png = makePng(size, fn);
  fs.writeFileSync(path.join(outDir, name), png);
  console.log("wrote", name, size + "x" + size, png.length + " bytes");
}
