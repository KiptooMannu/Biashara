// The paper grain started life as an inline SVG feTurbulence filter. Chrome
// re-rasterises that filter for every tile at print resolution, which turned a
// 17-slide PDF into 55 MB. A pre-rendered noise tile is one small image the
// PDF embeds once — same texture, ~1% of the size.
import { readFileSync, writeFileSync } from 'node:fs'
import { deflateSync } from 'node:zlib'

const SIZE = 96

function crc32(buf) {
  let c, crc = 0xffffffff
  for (let n = 0; n < buf.length; n++) {
    c = (crc ^ buf[n]) & 0xff
    for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1
    crc = c ^ (crc >>> 8)
  }
  return (crc ^ 0xffffffff) >>> 0
}

function chunk(type, data) {
  const len = Buffer.alloc(4)
  len.writeUInt32BE(data.length)
  const body = Buffer.concat([Buffer.from(type, 'ascii'), data])
  const crc = Buffer.alloc(4)
  crc.writeUInt32BE(crc32(body))
  return Buffer.concat([len, body, crc])
}

// Grayscale, 8-bit. Each scanline is prefixed with its filter type (0 = none).
const raw = Buffer.alloc((SIZE + 1) * SIZE)
for (let y = 0; y < SIZE; y++) {
  raw[y * (SIZE + 1)] = 0
  for (let x = 0; x < SIZE; x++) {
    // Two octaves so the tile reads as fibre rather than television static.
    const fine = Math.random() * 70
    const coarse = Math.random() * 30
    raw[y * (SIZE + 1) + 1 + x] = Math.max(0, Math.min(255, 150 + fine + coarse - 50)) | 0
  }
}

const ihdr = Buffer.alloc(13)
ihdr.writeUInt32BE(SIZE, 0)
ihdr.writeUInt32BE(SIZE, 4)
ihdr[8] = 8   // bit depth
ihdr[9] = 0   // colour type: grayscale
ihdr[10] = 0  // compression
ihdr[11] = 0  // filter
ihdr[12] = 0  // interlace

const png = Buffer.concat([
  Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
  chunk('IHDR', ihdr),
  chunk('IDAT', deflateSync(raw, { level: 9 })),
  chunk('IEND', Buffer.alloc(0)),
])

const uri = `data:image/png;base64,${png.toString('base64')}`
console.log(`grain tile: ${SIZE}x${SIZE}, ${(png.length / 1024).toFixed(1)} KB`)

const target = process.argv[2]
let html = readFileSync(target, 'utf8')

const svgRule = /background-image:url\("data:image\/svg\+xml,%3Csvg[^"]*"\);/
if (!svgRule.test(html)) throw new Error('grain rule not found — already replaced?')

html = html.replace(
  svgRule,
  `background-image:url("${uri}");background-repeat:repeat;background-size:${SIZE}px ${SIZE}px;`
)

writeFileSync(target, html, 'utf8')
console.log(`replaced SVG grain in ${target}`)
