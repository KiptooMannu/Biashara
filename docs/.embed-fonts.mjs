// One-off build step: inline the latin subsets of the deck's webfonts as
// base64 so the HTML file is a single self-contained artifact — it renders
// identically offline and when emailed around, and headless Chrome does not
// need network access to produce the PDF.
import { readFileSync, writeFileSync } from 'node:fs'

const CSS_URL =
  'https://fonts.googleapis.com/css2' +
  '?family=Bricolage+Grotesque:opsz,wght@12..96,400..800' +
  '&family=Inter:wght@400..700' +
  '&family=JetBrains+Mono:wght@400..700' +
  '&display=swap'

const UA =
  'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 ' +
  '(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36'

const css = await (await fetch(CSS_URL, { headers: { 'User-Agent': UA } })).text()

// Google emits one @font-face per subset. Only latin is needed here, and it is
// the block whose unicode-range covers basic latin.
const blocks = css.split('@font-face').slice(1)
const latin = blocks.filter((b) => /U\+0000-00FF/.test(b))

let out = ''
for (const block of latin) {
  const family = /font-family:\s*'([^']+)'/.exec(block)[1]
  const weight = /font-weight:\s*([^;]+);/.exec(block)[1].trim()
  const url = /url\((https:\/\/[^)]+\.woff2)\)/.exec(block)[1]
  const bytes = Buffer.from(await (await fetch(url)).arrayBuffer())
  out +=
    `@font-face{font-family:'${family}';font-style:normal;font-weight:${weight};` +
    `font-display:block;src:url(data:font/woff2;base64,${bytes.toString('base64')}) format('woff2');}\n`
  console.log(`${family} ${weight} — ${(bytes.length / 1024).toFixed(0)} KB`)
}

const target = process.argv[2]
const html = readFileSync(target, 'utf8')
if (!html.includes('/*@FONTS@*/')) throw new Error('font placeholder already consumed')
writeFileSync(target, html.replace('/*@FONTS@*/', out), 'utf8')
console.log(`embedded ${latin.length} faces into ${target}`)
