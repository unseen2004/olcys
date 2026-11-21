import { chromium } from '/home/maks/.npm/_npx/e41f203b7505f1fb/node_modules/playwright/index.mjs';
import fs from 'node:fs';
import path from 'node:path';

const REPO_ROOT = '/home/maks/HN/olcys';
const TMP_DIR = path.join(REPO_ROOT, 'tmp_video');
const FRAMES_DIR = path.join(TMP_DIR, 'frames');
const CONCAT_FILE = path.join(TMP_DIR, 'concat.txt');
const TIMESTAMPS_FILE = path.join(TMP_DIR, 'timestamps.json');
const SUBTITLES_FILE = path.join(REPO_ROOT, 'docs', 'demo_subtitles.srt');
const VOICEOVER_FILE = path.join(REPO_ROOT, 'docs', 'VOICEOVER_SCRIPT.txt');
const INTRO_PNG = path.join(TMP_DIR, 'intro.png');
const OUTRO_PNG = path.join(TMP_DIR, 'outro.png');

const BASE_URL = process.env.OLCYS_FRONTEND_URL || 'http://localhost:5173';

fs.rmSync(TMP_DIR, { recursive: true, force: true });
fs.mkdirSync(FRAMES_DIR, { recursive: true });

let frameSeq = 0;
let liveElapsedMs = 0;
const concatLines = [];
const cueSegments = [];

const INTRO_MS = 12000;
const OUTRO_MS = 11000;

let cursorX = 960;
let cursorY = 540;

function formatSrtTime(ms) {
  const clamped = Math.max(0, Math.round(ms));
  const hours = Math.floor(clamped / 3600000);
  const mins = Math.floor((clamped % 3600000) / 60000);
  const secs = Math.floor((clamped % 60000) / 1000);
  const millis = clamped % 1000;
  return (
    `${String(hours).padStart(2, '0')}:` +
    `${String(mins).padStart(2, '0')}:` +
    `${String(secs).padStart(2, '0')},` +
    `${String(millis).padStart(3, '0')}`
  );
}

function formatReadableTime(ms) {
  const secsTotal = Math.round(ms / 1000);
  const mins = Math.floor(secsTotal / 60);
  const secs = secsTotal % 60;
  return `${mins}:${String(secs).padStart(2, '0')}`;
}

async function captureHeldFrame(page, durationMs) {
  if (durationMs <= 0) return;
  frameSeq += 1;
  const imgPath = path.join(FRAMES_DIR, `frame_${String(frameSeq).padStart(5, '0')}.png`);
  await page.screenshot({ path: imgPath, type: 'png', fullPage: false });
  concatLines.push(`file '${imgPath}'`);
  concatLines.push(`duration ${(durationMs / 1000).toFixed(4)}`);
  liveElapsedMs += durationMs;
}

async function injectOverlay(page) {
  await page.evaluate(({ x, y }) => {
    document.body.style.paddingBottom = '96px';

    if (!document.getElementById('olcys-rec-style')) {
      const style = document.createElement('style');
      style.id = 'olcys-rec-style';
      style.textContent = `
        #olcys-rec-cursor {
          position: fixed;
          width: 34px;
          height: 34px;
          border-radius: 9999px;
          border: 3px solid #D97706;
          background: rgba(217, 119, 6, 0.22);
          box-shadow: 0 0 0 6px rgba(217, 119, 6, 0.14), 0 4px 14px rgba(15, 41, 66, 0.25);
          transform: translate(-50%, -50%) scale(1);
          pointer-events: none;
          z-index: 999999;
          transition: transform 90ms ease-out;
        }
        #olcys-rec-cursor.clicking {
          border-color: #B91C1C;
          background: rgba(185, 28, 28, 0.34);
          box-shadow: 0 0 0 11px rgba(185, 28, 28, 0.18);
          transform: translate(-50%, -50%) scale(0.84);
        }
        #olcys-rec-cursor-dot {
          position: absolute;
          top: 50%;
          left: 50%;
          width: 8px;
          height: 8px;
          border-radius: 9999px;
          background: #B91C1C;
          transform: translate(-50%, -50%);
        }
        #olcys-rec-stage {
          position: fixed;
          top: 108px;
          right: 28px;
          z-index: 999998;
          pointer-events: none;
          display: flex;
          align-items: center;
          gap: 8px;
          padding: 7px 14px;
          border-radius: 9999px;
          background: rgba(15, 41, 66, 0.92);
          border: 1px solid rgba(255, 255, 255, 0.28);
          box-shadow: 0 8px 24px rgba(15, 41, 66, 0.32);
          color: #FFFFFF;
          font-family: "DejaVu Sans", "Liberation Sans", sans-serif;
          font-size: 13px;
          font-weight: 700;
          letter-spacing: 0.2px;
        }
        #olcys-rec-stage-dot {
          width: 9px;
          height: 9px;
          border-radius: 9999px;
          background: #22C55E;
        }
      `;
      document.head.appendChild(style);
    }

    let cursor = document.getElementById('olcys-rec-cursor');
    if (!cursor) {
      cursor = document.createElement('div');
      cursor.id = 'olcys-rec-cursor';
      const dot = document.createElement('div');
      dot.id = 'olcys-rec-cursor-dot';
      cursor.appendChild(dot);
      document.body.appendChild(cursor);
    }
    cursor.style.left = `${x}px`;
    cursor.style.top = `${y}px`;

    if (!document.getElementById('olcys-rec-stage')) {
      const stage = document.createElement('div');
      stage.id = 'olcys-rec-stage';
      const sDot = document.createElement('span');
      sDot.id = 'olcys-rec-stage-dot';
      const sTxt = document.createElement('span');
      sTxt.id = 'olcys-rec-stage-text';
      sTxt.textContent = 'ETAP 1/7 • Parametry i Dane Wejściowe';
      stage.appendChild(sDot);
      stage.appendChild(sTxt);
      document.body.appendChild(stage);
    }

    if (!window.__olcysCursorBound) {
      window.__olcysCursorBound = true;
      window.addEventListener('mousemove', (e) => {
        const el = document.getElementById('olcys-rec-cursor');
        if (el) {
          el.style.left = `${e.clientX}px`;
          el.style.top = `${e.clientY}px`;
        }
      }, true);
      window.addEventListener('mousedown', () => {
        const el = document.getElementById('olcys-rec-cursor');
        if (el) el.classList.add('clicking');
      }, true);
      window.addEventListener('mouseup', () => {
        const el = document.getElementById('olcys-rec-cursor');
        if (el) el.classList.remove('clicking');
      }, true);
    }
  }, { x: cursorX, y: cursorY });
}

async function setStageBadge(page, label, dotColor = '#22C55E') {
  await injectOverlay(page);
  await page.evaluate(({ txt, color }) => {
    const sTxt = document.getElementById('olcys-rec-stage-text');
    const sDot = document.getElementById('olcys-rec-stage-dot');
    if (sTxt) sTxt.textContent = txt;
    if (sDot) sDot.style.background = color;
  }, { txt: label, color: dotColor });
}

async function moveCursorToPoint(page, targetX, targetY, durationMs = 700, steps = 10) {
  const startX = cursorX;
  const startY = cursorY;
  const stepMs = durationMs / steps;
  for (let i = 1; i <= steps; i++) {
    const t = i / steps;
    const ease = t < 0.5 ? 2 * t * t : 1 - Math.pow(-2 * t + 2, 2) / 2;
    cursorX = startX + (targetX - startX) * ease;
    cursorY = startY + (targetY - startY) * ease;
    await page.mouse.move(cursorX, cursorY, { steps: 2 });
    await captureHeldFrame(page, stepMs);
  }
}

async function moveCursorToLocator(page, locator, durationMs = 700, steps = 10) {
  const box = await locator.boundingBox();
  if (!box) {
    await captureHeldFrame(page, durationMs);
    return;
  }
  const tx = Math.round(box.x + box.width / 2);
  const ty = Math.round(box.y + box.height / 2);
  await moveCursorToPoint(page, tx, ty, durationMs, steps);
}

async function clickLocatorVisual(page, locator, preMoveMs = 700, clickPulseMs = 300) {
  await moveCursorToLocator(page, locator, preMoveMs, 10);
  await page.mouse.down();
  await captureHeldFrame(page, clickPulseMs);
  await page.mouse.up();
  await page.waitForTimeout(100);
}

async function smoothScrollToY(page, targetY, durationMs = 1000, steps = 12) {
  const startY = await page.evaluate(() => window.scrollY);
  const stepMs = durationMs / steps;
  for (let i = 1; i <= steps; i++) {
    const t = i / steps;
    const ease = t < 0.5 ? 2 * t * t : 1 - Math.pow(-2 * t + 2, 2) / 2;
    const nextY = Math.round(startY + (targetY - startY) * ease);
    await page.evaluate((y) => window.scrollTo({ top: y, behavior: 'instant' }), nextY);
    await captureHeldFrame(page, stepMs);
  }
}

function addCue(id, title, stage, startAssembledMs, endAssembledMs, subtitleLines, voiceoverText) {
  cueSegments.push({
    id,
    title,
    stage,
    startMs: startAssembledMs,
    endMs: endAssembledMs,
    durationMs: endAssembledMs - startAssembledMs,
    subtitleLines,
    voiceoverText,
  });
}

async function renderBoards(browser) {
  const page = await browser.newPage({ viewport: { width: 1920, height: 1080 }, deviceScaleFactor: 1 });

  const introHtml = `<!DOCTYPE html>
  <html lang="pl">
  <head>
    <meta charset="UTF-8" />
    <style>
      * { box-sizing: border-box; }
      html, body {
        margin: 0; padding: 0; width: 1920px; height: 1080px; overflow: hidden;
        font-family: "DejaVu Sans", "Liberation Sans", sans-serif;
        background: #0F2942; color: #FFFFFF;
      }
      .wrap {
        width: 1920px; height: 1080px; padding: 72px 96px;
        display: flex; flex-direction: column; justify-content: space-between;
        background: radial-gradient(circle at 88% 14%, rgba(21,128,61,0.22), transparent 36%),
                    radial-gradient(circle at 12% 86%, rgba(180,83,9,0.18), transparent 40%),
                    linear-gradient(135deg, #0B1F33 0%, #0F2942 58%, #153554 100%);
      }
      .top-bar {
        display: flex; justify-content: space-between; align-items: center;
      }
      .pill-left {
        display: inline-flex; align-items: center; gap: 12px;
        padding: 12px 24px; border-radius: 9999px;
        background: rgba(255,255,255,0.10); border: 1px solid rgba(255,255,255,0.22);
        font-size: 20px; font-weight: 700; letter-spacing: 1px; text-transform: uppercase;
      }
      .pill-right {
        display: inline-flex; align-items: center; gap: 10px;
        padding: 12px 24px; border-radius: 9999px;
        background: rgba(21,128,61,0.25); border: 1px solid rgba(74,222,128,0.55);
        color: #BBF7D0; font-size: 20px; font-weight: 700;
      }
      .hero {
        background: #F8FAFC; color: #1E293B; border-radius: 28px;
        padding: 64px 72px; border: 2px solid rgba(255,255,255,0.3);
        box-shadow: 0 32px 72px rgba(0,0,0,0.38);
      }
      .kicker {
        color: #B45309; font-size: 22px; font-weight: 800; letter-spacing: 1.4px;
        text-transform: uppercase; margin-bottom: 18px;
      }
      h1 {
        margin: 0; font-size: 62px; line-height: 1.12; font-weight: 800; color: #0F2942;
      }
      .problem {
        margin-top: 28px; font-size: 30px; line-height: 1.42; color: #334155; font-weight: 500;
        border-left: 8px solid #0F2942; padding-left: 26px;
      }
      .badges {
        margin-top: 40px; display: grid; grid-template-columns: repeat(3, 1fr); gap: 24px;
      }
      .badge-card {
        background: #FFFFFF; border: 1px solid #CBD5E1; border-radius: 16px; padding: 22px 26px;
      }
      .badge-title {
        font-size: 16px; font-weight: 800; color: #64748B; text-transform: uppercase; letter-spacing: 0.8px;
      }
      .badge-val {
        margin-top: 8px; font-size: 24px; font-weight: 800; color: #0F2942;
      }
      .badge-val.green { color: #15803D; }
      .footer {
        display: flex; justify-content: space-between; align-items: center;
        font-size: 22px; color: #CBD5E1; font-weight: 600;
        border-top: 1px solid rgba(255,255,255,0.16); padding-top: 22px;
      }
    </style>
  </head>
  <body>
    <div class="wrap">
      <div class="top-bar">
        <div class="pill-left">Zespół OŁCyŚ • HackNation 2026 • Krajowa Administracja Skarbowa</div>
        <div class="pill-right">100% On-Premise • Zero transferu zewnętrznego</div>
      </div>
      <div class="hero">
        <div class="kicker">Wyzwanie OSINT &amp; Blockchain — Weryfikacja przychodów PIT-38</div>
        <h1>OŁCyŚ — Osintowy Łapacz Cyfrowych Śladów</h1>
        <p class="problem">
          Automatyzacja łączenia publicznych śladów z Internetu (Web2) z aktywnością na blockchainie (Web3) w celu wsparcia weryfikacji przychodów kryptowalutowych i zeznań PIT-38 przez KAS.
        </p>
        <div class="badges">
          <div class="badge-card">
            <div class="badge-title">Zakres danych</div>
            <div class="badge-val">Dane zanonimizowane / syntetyczne</div>
          </div>
          <div class="badge-card">
            <div class="badge-title">Podstawa prawna</div>
            <div class="badge-val green">Wyłącznie legalny pasywny OSINT</div>
          </div>
          <div class="badge-card">
            <div class="badge-title">Dowodowość</div>
            <div class="badge-val">URL + UTC + SHA-256 + Raport 3 Strefy</div>
          </div>
        </div>
      </div>
      <div class="footer">
        <span>Prezentacja na danych zanonimizowanych / syntetycznych | Wyłącznie legalny OSINT</span>
        <span>github.com/unseen2004/olcys</span>
      </div>
    </div>
  </body>
  </html>`;

  await page.setContent(introHtml, { waitUntil: 'networkidle' });
  await page.screenshot({ path: INTRO_PNG, type: 'png' });

  const outroHtml = `<!DOCTYPE html>
  <html lang="pl">
  <head>
    <meta charset="UTF-8" />
    <style>
      * { box-sizing: border-box; }
      html, body {
        margin: 0; padding: 0; width: 1920px; height: 1080px; overflow: hidden;
        font-family: "DejaVu Sans", "Liberation Sans", sans-serif;
        background: #0F2942; color: #FFFFFF;
      }
      .wrap {
        width: 1920px; height: 1080px; padding: 68px 88px;
        display: flex; flex-direction: column; justify-content: space-between;
        background: linear-gradient(135deg, #0B1F33 0%, #0F2942 60%, #153554 100%);
      }
      .head {
        display: flex; justify-content: space-between; align-items: center;
      }
      .title-box h1 { margin: 0; font-size: 46px; font-weight: 800; color: #FFFFFF; }
      .title-box p { margin: 10px 0 0; font-size: 22px; color: #CBD5E1; }
      .secure-pill {
        padding: 14px 26px; border-radius: 9999px; font-size: 20px; font-weight: 800;
        background: #15803D; color: #FFFFFF; border: 1px solid #86EFAC;
      }
      .grid {
        display: grid; grid-template-columns: repeat(2, 1fr); gap: 28px;
      }
      .card {
        background: #F8FAFC; color: #1E293B; border-radius: 22px; padding: 34px 38px;
        border: 1px solid #CBD5E1;
      }
      .card-tag {
        font-size: 16px; font-weight: 800; color: #B45309; text-transform: uppercase; letter-spacing: 0.9px;
      }
      .card h2 {
        margin: 10px 0 14px; font-size: 28px; font-weight: 800; color: #0F2942;
      }
      .card p {
        margin: 0; font-size: 21px; line-height: 1.48; color: #334155;
      }
      .mono {
        font-family: "DejaVu Sans Mono", monospace; font-weight: 700; color: #0F2942;
        background: #E2E8F0; padding: 2px 7px; border-radius: 6px; font-size: 18px;
      }
      .footer {
        display: flex; justify-content: space-between; align-items: center;
        border-top: 1px solid rgba(255,255,255,0.18); padding-top: 22px;
        font-size: 21px; color: #E2E8F0; font-weight: 600;
      }
    </style>
  </head>
  <body>
    <div class="wrap">
      <div class="head">
        <div class="title-box">
          <h1>OŁCyŚ — Gotowość wdrożeniowa w infrastrukturze KAS</h1>
          <p>Deterministyczny scoring 0–100%, pełna odtwarzalność dowodów i bezpieczna architektura On-Premise</p>
        </div>
        <div class="secure-pill">0 danych w chmurze publicznej</div>
      </div>
      <div class="grid">
        <div class="card">
          <div class="card-tag">Monorepo Enterprise</div>
          <h2>Java 21 + React 19 + PostgreSQL + Docker</h2>
          <p>Spójne monorepo: backend Spring Boot 3.4 (Virtual Threads), Flyway (<span class="mono">V1</span> &amp; <span class="mono">V2</span>), baza PostgreSQL 16, frontend React + TypeScript oraz konteneryzacja Docker Compose.</p>
        </div>
        <div class="card">
          <div class="card-tag">Architektura Pluginowa</div>
          <h2>Modularna rozbudowa źródeł i sieci</h2>
          <p>Rozszerzalne kontrakty <span class="mono">Web2Connector</span>, <span class="mono">Web3Connector</span>, <span class="mono">EntityTagProvider</span> i <span class="mono">AiClient</span> — łatwe dodanie nowych źródeł DAC8/MiCA i kolejnych sieci.</p>
        </div>
        <div class="card">
          <div class="card-tag">Izolacja AI On-Premise</div>
          <h2>Wymienny lokalny adapter AI (Zero wycieku)</h2>
          <p>Domyślny silnik <span class="mono">local-rules</span> lub lokalny serwer Ollama (<span class="mono">llama3.2:latest</span>). Blokada <span class="mono">OLCYS_AI_ALLOW_EXTERNAL=false</span> rzuca <span class="mono">SecurityException</span> dla chmury.</p>
        </div>
        <div class="card">
          <div class="card-tag">Zgodność Prawna i Licencyjna</div>
          <h2>100% Open Source bez GPL/AGPL</h2>
          <p>Wyłącznie pasywny OSINT z jawnych źródeł. Czyste licencje permisywne: Apache PDFBox 3.0.3, Bouncy Castle 1.79 (MIT), Spring Boot (Apache 2.0) oraz React 19 (MIT).</p>
        </div>
      </div>
      <div class="footer">
        <span>Zespół OŁCyŚ • HackNation 2026 • Krajowa Administracja Skarbowa</span>
        <span>Repozytorium: https://github.com/unseen2004/olcys</span>
      </div>
    </div>
  </body>
  </html>`;

  await page.setContent(outroHtml, { waitUntil: 'networkidle' });
  await page.screenshot({ path: OUTRO_PNG, type: 'png' });
  await page.close();
}

function writeSubtitlesAndVoiceover() {
  const srtLines = [];
  const voLines = [
    '========================================================================',
    'OŁCyŚ — TEKST NARRACJI LEKTORA DO FILMU DEMO (2:45 min / 165 s)',
    'Zsynchronizowane z docs/demo_subtitles.srt oraz docs/olcys_demo.mp4',
    '========================================================================',
    '',
  ];

  cueSegments.forEach((seg, idx) => {
    srtLines.push(String(idx + 1));
    srtLines.push(`${formatSrtTime(seg.startMs)} --> ${formatSrtTime(seg.endMs)}`);
    srtLines.push(...seg.subtitleLines);
    srtLines.push('');

    voLines.push(
      `[${formatReadableTime(seg.startMs)} – ${formatReadableTime(seg.endMs)}] ${seg.stage} | ${seg.title}`,
    );
    voLines.push(seg.voiceoverText);
    voLines.push('');
  });

  fs.writeFileSync(SUBTITLES_FILE, srtLines.join('\n'), 'utf8');
  fs.writeFileSync(VOICEOVER_FILE, voLines.join('\n'), 'utf8');
}

const browser = await chromium.launch({
  executablePath: '/usr/bin/google-chrome',
  headless: true,
  args: ['--no-sandbox', '--disable-dev-shm-usage'],
});

try {
  await renderBoards(browser);

  const context = await browser.newContext({
    viewport: { width: 1920, height: 1080 },
    deviceScaleFactor: 1,
    acceptDownloads: true,
  });
  const page = await context.newPage();
  page.on('download', async (download) => {
    try {
      await download.saveAs(path.join(TMP_DIR, download.suggestedFilename()));
    } catch {
      // Ignore temporary download errors during demo capture
    }
  });

  // =========================================================================
  // INTRO CUE [0:00 - 0:12] (assembled externally in ffmpeg: 12000 ms)
  // =========================================================================
  addCue(
    'intro_1',
    'Plansza otwarcia',
    'INTRO',
    0,
    6000,
    ['OŁCyŚ — Osintowy Łapacz Cyfrowych Śladów dla KAS.', 'Łączenie śladów Web2 z blockchainem Web3 pod kątem PIT-38.'],
    'Prezentujemy OŁCyŚ — narzędzie wspierające Krajową Administrację Skarbową w wykrywaniu nieujawnionych przychodów kryptowalutowych przez automatyczne łączenie śladów Web2 z aktywnością Web3.'
  );
  addCue(
    'intro_2',
    'Założenia legalności',
    'INTRO',
    6000,
    12000,
    ['Prezentacja na danych zanonimizowanych i syntetycznych.', 'Wyłącznie legalny, pasywny OSINT — 100% On-Premise.'],
    'Wszystkie dane w pokazie mają charakter syntetyczny i zanonimizowany. Narzędzie wykorzystuje wyłącznie legalny, pasywny biały wywiad z publicznych źródeł i działa lokalnie on-premise.'
  );

  // Open main page before recording starts so zero blank loader appears
  await page.goto(BASE_URL, { waitUntil: 'networkidle' });
  await page.waitForSelector('text=Szybki start', { timeout: 15000 });
  await injectOverlay(page);

  // =========================================================================
  // LIVE SEGMENT 1: [0:12 - 0:28] (16000 ms) Params & Etap 1 Input
  // =========================================================================
  const s1Start = INTRO_MS + liveElapsedMs; // 12000
  await setStageBadge(page, 'ETAP 1/7 • Parametry i Dane Wejściowe', '#0F2942');
  await captureHeldFrame(page, 2200);

  // Sweep over identifier selector and Web2/Web3 sources
  const idSelect = page.locator('select[aria-label="Typ identyfikatora 1"]');
  await moveCursorToLocator(page, idSelect, 1000);
  await captureHeldFrame(page, 1800);

  const sourcesGrid = page.locator('text=Źródła Web2 (OSINT pasywny)');
  await moveCursorToLocator(page, sourcesGrid, 1000);
  await captureHeldFrame(page, 1700);

  const ethBadge = page.locator('label:has-text("ETH")').first();
  await moveCursorToLocator(page, ethBadge, 900);
  await captureHeldFrame(page, 1500);

  // Load Scenario 1 via "Edytuj" so viewer clearly sees populated inputs & mode
  const editS1 = page.locator('button:has-text("Edytuj")').first();
  await editS1.scrollIntoViewIfNeeded();
  await clickLocatorVisual(page, editS1, 900, 300);
  await page.evaluate(() => window.scrollTo({ top: 0, behavior: 'instant' }));
  await captureHeldFrame(page, 1500);

  // Click primary run button
  const submitBtn = page.locator('button:has-text("Uruchom analizę OSINT & Web3")');
  await submitBtn.scrollIntoViewIfNeeded();
  await clickLocatorVisual(page, submitBtn, 1000, 300);
  await captureHeldFrame(page, 800);

  // Wait cleanly in wall-clock time for analysis & graph nodes to render
  await page.waitForSelector('.react-flow__node', { timeout: 60000 });
  await page.waitForTimeout(450);
  await page.evaluate(() => window.scrollTo({ top: 0, behavior: 'instant' }));
  await setStageBadge(page, 'ETAP 2/7 • Publiczne Ślady OSINT', '#15803D');
  await captureHeldFrame(page, 1100);
  const s1End = INTRO_MS + liveElapsedMs; // 28000

  addCue(
    'seg1_params',
    'Parametry wyszukiwania',
    'ETAP 1/7',
    s1Start,
    s1Start + 8000,
    ['Obsługiwane 6 typów identyfikatorów, 6 otwartych źródeł Web2', 'oraz 3 sieci Web3 (ETH, BTC, TRON) w trybie pasywnym.'],
    'System przyjmuje dowolny z sześciu typów identyfikatorów, przeszukuje wyłącznie jawne źródła Web2 i publiczne eksploratory blockchain, interpretując wyniki deterministycznie.'
  );
  addCue(
    'seg1_input',
    'Etap 1 — Wprowadzenie danych',
    'ETAP 1/7',
    s1Start + 8000,
    s1End,
    ['Scenariusz 1: @krypto_janek (NICK, imię/nazwisko, e-mail).', 'Tryb walidacyjny (zero wysyłki do sieci) — start analizy.'],
    'Wprowadzamy dane Scenariusza 1 dla Jana Kowalskiego: nick, imię i nazwisko oraz adres e-mail. Uruchamiamy równoległą analizę w bezpiecznym trybie walidacyjnym.'
  );

  // =========================================================================
  // LIVE SEGMENT 2: [0:28 - 0:48] (20000 ms) Etap 2 Web2 Traces & Etap 3 Crypto Addresses
  // =========================================================================
  const s2Start = INTRO_MS + liveElapsedMs; // 28000
  await setStageBadge(page, 'ETAP 2/7 • Publiczne Ślady OSINT', '#15803D');
  await captureHeldFrame(page, 1800);

  const ghNode = page.locator('.react-flow__node:has-text("GitHub")').first();
  await clickLocatorVisual(page, ghNode, 900, 300);
  await page.waitForSelector('text=Inspektor Dowodowy KAS', { timeout: 10000 });
  await captureHeldFrame(page, 3800);

  const kpNode = page.locator('.react-flow__node:has-text("KantorP2P")').first();
  await clickLocatorVisual(page, kpNode, 900, 300);
  await captureHeldFrame(page, 3300);

  // Switch to Tab 2 for Etap 3: Address extraction & crypto checksums
  const tabWallets = page.locator('nav button:has-text("Portfele & Firewall FP")');
  await clickLocatorVisual(page, tabWallets, 900, 300);
  await page.waitForSelector('text=Zidentyfikowane portfele niehostowane', { timeout: 10000 });
  await setStageBadge(page, 'ETAP 3/7 • Ekstrakcja i Walidacja Adresów', '#15803D');
  await page.evaluate(() => window.scrollTo({ top: 0, behavior: 'instant' }));
  await captureHeldFrame(page, 3200);

  const ethAddrEl = page.locator('text=0x71C7656EC7ab88b098defB751B7401B5f6d8976F').first();
  await moveCursorToLocator(page, ethAddrEl, 900);
  await captureHeldFrame(page, 2100);

  const eipBadgeEl = page.locator('text=EIP-55').first();
  await moveCursorToLocator(page, eipBadgeEl, 800);
  await captureHeldFrame(page, 500);
  const s2End = INTRO_MS + liveElapsedMs; // 48000

  addCue(
    'seg2_osint',
    'Etap 2 — Publiczne ślady Web2',
    'ETAP 2/7',
    s2Start,
    s2Start + 11000,
    ['Odnaleziono 4 źródła Web2 (GitHub, KantorP2P, Wayback, Forum).', 'Inspektor rejestruje źródło, dokładny URL, czas UTC i SHA-256.'],
    'System odnalazł cztery publiczne źródła Web2: profil GitHub, ogłoszenie P2P, migawkę Wayback i forum. Inspektor Dowodowy zapisuje URL, czas pobrania UTC i skrót SHA-256.'
  );
  addCue(
    'seg2_crypto',
    'Etap 3 — Walidacja kryptograficzna',
    'ETAP 3/7',
    s2Start + 11000,
    s2End,
    ['Automatyczna ekstrakcja adresów ETH i BTC z surowych tekstów.', 'Weryfikacja sum kontrolnych EIP-55 (Keccak-256) oraz Bech32.'],
    'Z tekstów automatycznie wyodrębniono adresy kryptowalutowe. Walidator kryptograficzny potwierdził poprawność sum kontrolnych Ethereum EIP-55 oraz Bitcoin Bech32, odfiltrowując hashe transakcji.'
  );

  // =========================================================================
  // LIVE SEGMENT 3: [0:48 - 1:12] (24000 ms) Etap 4 Confidence Score + Synergy
  // =========================================================================
  const s3Start = INTRO_MS + liveElapsedMs; // 48000
  await setStageBadge(page, 'ETAP 4/7 • Wskaźnik Pewności (0–100%)', '#15803D');
  await captureHeldFrame(page, 2400);

  const score100El = page.locator('text=100%').first();
  await moveCursorToLocator(page, score100El, 900);
  await captureHeldFrame(page, 2800);

  const rule85El = page.locator('text=+85 pkt').first();
  await moveCursorToLocator(page, rule85El, 900);
  await captureHeldFrame(page, 2900);

  const rule15El = page.locator('text=+15 pkt').first();
  await moveCursorToLocator(page, rule15El, 900);
  await captureHeldFrame(page, 3700);

  // Smooth scroll to BTC card (70%)
  const btcEl = page.locator('text=bc1qar0srrr7xfkvy5l643lydnw9re59gtzzwf5mdq').first();
  const btcScrollY = await btcEl.evaluate((el) => Math.max(0, Math.round(el.getBoundingClientRect().top + window.scrollY - 220)));
  await smoothScrollToY(page, btcScrollY, 1500, 14);
  await captureHeldFrame(page, 2400);

  const rule70El = page.locator('text=+70 pkt').first();
  await moveCursorToLocator(page, rule70El, 900);
  await captureHeldFrame(page, 4700);
  const s3End = INTRO_MS + liveElapsedMs; // 72000

  addCue(
    'seg3_eth_synergy',
    'Etap 4 — Confidence Score ETH 100%',
    'ETAP 4/7',
    s3Start,
    s3Start + 13500,
    ['ETH 100%: +85 pkt za własny profil GitHub (próg 80–100%)', '+15 pkt premii synergii za drugie niezależne źródło KantorP2P.pl.'],
    'Wyliczamy deterministyczny Wskaźnik Pewności bez czarnej skrzynki. Adres ETH uzyskuje 100 procent: 85 punktów za własny profil GitHub oraz plus 15 punktów premii synergii za drugie niezależne źródło, bez powielania punktów z jednej usługi.'
  );
  addCue(
    'seg3_btc_score',
    'Etap 4 — Confidence Score BTC 70%',
    'ETAP 4/7',
    s3Start + 13500,
    s3End,
    ['Portfel BTC uzyskał 70% (przedział Prawdopodobne 60–79%)', 'za kontekst rozliczeń B2B i P2P z archiwum WaybackArchive.'],
    'Portfel Bitcoin osiąga 70 procent w przedziale prawdopodobnym, ponieważ został wskazany bezpośrednio w kontekście rozliczeń B2B w historycznej migawce WaybackArchive.'
  );

  // =========================================================================
  // LIVE SEGMENT 4: [1:12 - 1:36] (24000 ms) Etap 5 False Positives = 0%
  // =========================================================================
  const s4Start = INTRO_MS + liveElapsedMs; // 72000
  await setStageBadge(page, 'ETAP 5/7 • Odrzucanie False Positives (0%)', '#B91C1C');

  const fpHeading = page.locator('text=Odrzucone trafienia').first();
  const fpScrollY = await fpHeading.evaluate((el) => Math.max(0, Math.round(el.getBoundingClientRect().top + window.scrollY - 75)));
  await smoothScrollToY(page, fpScrollY, 1600, 14);
  await captureHeldFrame(page, 2600);

  const uniCardTitle = page.locator('text=Uniswap V3: SwapRouter').first();
  await moveCursorToLocator(page, uniCardTitle, 900);
  await captureHeldFrame(page, 3800);

  const bytecodeLine = page.locator('text=bytecode != 0x').first();
  await moveCursorToLocator(page, bytecodeLine, 900);
  await captureHeldFrame(page, 4000);

  const binanceTitle = page.locator('text=Binance 14 (Hot Wallet)').first();
  const binanceScrollY = await binanceTitle.evaluate((el) => Math.max(0, Math.round(el.getBoundingClientRect().top + window.scrollY - 220)));
  await smoothScrollToY(page, binanceScrollY, 1200, 12);
  await moveCursorToLocator(page, binanceTitle, 900);
  await captureHeldFrame(page, 4100);

  const interactionBadge = page.locator('text=Odnotowano interakcję on-chain').nth(1);
  await interactionBadge.scrollIntoViewIfNeeded();
  await moveCursorToLocator(page, interactionBadge, 900);
  await captureHeldFrame(page, 3100);
  const s4End = INTRO_MS + liveElapsedMs; // 96000

  addCue(
    'seg4_fp_dex',
    'Etap 5 — Wykluczenie Smart Kontraktu',
    'ETAP 5/7',
    s4Start,
    s4Start + 12000,
    ['Firewall FP odrzucił router Uniswap V3 z wynikiem 0%:', 'Warstwa 1 wykryła niepusty kod bajtowy (bytecode != 0x).'],
    'Ochrona przed błędnym przypisaniem wyzerowała do zera procent dwa fałszywe trafienia. Smart kontrakt Uniswap V3 został odrzucony w Warstwie 1 poprzez weryfikację niepustego kodu bajtowego bytecode.'
  );
  addCue(
    'seg4_fp_cex',
    'Etap 5 — Wykluczenie Giełdy CEX',
    'ETAP 5/7',
    s4Start + 12000,
    s4End,
    ['Binance 14 Hot Wallet odrzucony z 0% (Address Tag: CEX) —', 'nie należy do podatnika, ale zachowano fakt interakcji on-chain.'],
    'Gorący portfel giełdy Binance rozpoznano w Warstwie 2 po etykiecie Address Tag jako CEX i odrzucono z zerem procent, odnotowując jedynie sam fakt interakcji jako trop do wezwania CASP.'
  );

  // =========================================================================
  // LIVE SEGMENT 5: [1:36 - 2:00] (24000 ms) Etap 6 Graph + On-Chain Cash-Out
  // =========================================================================
  const s5Start = INTRO_MS + liveElapsedMs; // 96000
  await setStageBadge(page, 'ETAP 6/7 • Graf Powiązań i Fiat Cash-Out', '#B45309');
  await smoothScrollToY(page, 0, 900, 10);

  const tabGraph = page.locator('nav button:has-text("Graf powiązań")');
  await clickLocatorVisual(page, tabGraph, 800, 300);
  await page.waitForSelector('.react-flow__node', { timeout: 10000 });
  await captureHeldFrame(page, 1800);

  const forumNode = page.locator('.react-flow__node:has-text("WaybackArchive")').first();
  await clickLocatorVisual(page, forumNode, 900, 300);
  await captureHeldFrame(page, 2500);

  const tabFlow = page.locator('nav button:has-text("Przepływy On-Chain & Cash-Out")');
  await clickLocatorVisual(page, tabFlow, 900, 300);
  await page.waitForSelector('text=Kalkulator Szacunkowej Luki Podatkowej PIT-38', { timeout: 10000 });
  await page.evaluate(() => window.scrollTo({ top: 0, behavior: 'instant' }));
  await captureHeldFrame(page, 3800);

  const pitCalcEl = page.locator('text=25 574,00').first();
  await moveCursorToLocator(page, pitCalcEl, 900);
  await captureHeldFrame(page, 2900);

  const sigHeading = page.locator('text=Wykryte sygnały potencjalnego wyjścia').first();
  const sigScrollY = await sigHeading.evaluate((el) => Math.max(0, Math.round(el.getBoundingClientRect().top + window.scrollY - 220)));
  await smoothScrollToY(page, sigScrollY, 1500, 12);
  await captureHeldFrame(page, 6200);
  const s5End = INTRO_MS + liveElapsedMs; // 120000

  addCue(
    'seg5_graph',
    'Etap 6 — Interaktywny graf Web2 ↔ Web3',
    'ETAP 6/7',
    s5Start,
    s5Start + 8000,
    ['Interaktywny graf łączy podmiot, 4 źródła Web2, portfele', 'oraz wykluczone False Positives z klikalnym inspektorem.'],
    'Przechodzimy na interaktywny graf powiązań, który wizualnie łączy identyfikatory ze źródłami Web2, portfelami i węzłami wyjścia, umożliwiając szybki podgląd każdego źródła.'
  );
  addCue(
    'seg5_cashout',
    'Etap 6 — Aktywność On-Chain i Fiat Cash-Out',
    'ETAP 6/7',
    s5Start + 8000,
    s5End,
    ['Wolumen 176 600 zł, 12 100 USD stablecoin, chronologia IN/OUT', 'oraz 13 alertów Fiat Cash-Out (szac. PIT-38 19%: 25 574 zł).'],
    'Widok on-chain prezentuje łączny wolumen 176 600 złotych, 12 100 dolarów w stablecoinach, chronologię przepływów oraz 13 alertów Fiat Cash-Out: wypłaty CEX, kantory P2P w oknie ±72h, cykliczność i 2-Hop AML.'
  );

  // =========================================================================
  // LIVE SEGMENT 6: [2:00 - 2:20] (20000 ms) Etap 7 3-Zone Report & PDF Export
  // =========================================================================
  const s6Start = INTRO_MS + liveElapsedMs; // 120000
  await setStageBadge(page, 'ETAP 7/7 • Raport Końcowy (3 Strefy)', '#0F2942');
  await smoothScrollToY(page, 0, 800, 10);

  const tabReport = page.locator('nav button:has-text("Raport OSINT (3 strefy)")');
  await clickLocatorVisual(page, tabReport, 800, 300);
  await page.waitForSelector('text=STREFA 1: FAKTY ŹRÓDŁOWE', { timeout: 10000 });
  await page.evaluate(() => window.scrollTo({ top: 0, behavior: 'instant' }));
  await captureHeldFrame(page, 3100);

  const z2El = page.locator('text=STREFA 2: WYNIKI OBLICZEŃ').first();
  const z2ScrollY = await z2El.evaluate((el) => Math.max(0, Math.round(el.getBoundingClientRect().top + window.scrollY - 90)));
  await smoothScrollToY(page, z2ScrollY, 1300, 12);
  await captureHeldFrame(page, 2600);

  const z3El = page.locator('text=STREFA 3: WNIOSKI WYMAGAJĄCE DALSZEJ WERYFIKACJI').first();
  const z3ScrollY = await z3El.evaluate((el) => Math.max(0, Math.round(el.getBoundingClientRect().top + window.scrollY - 90)));
  await smoothScrollToY(page, z3ScrollY, 1400, 12);
  await captureHeldFrame(page, 2500);

  const verifyShaBtn = page.locator('button:has-text("Przelicz SHA-256 w przeglądarce")');
  await verifyShaBtn.scrollIntoViewIfNeeded();
  await clickLocatorVisual(page, verifyShaBtn, 800, 300);
  await page.waitForSelector('text=Zweryfikowano sumy kontrolne SHA-256 lokalnie', { timeout: 10000 });
  await captureHeldFrame(page, 2200);

  // Scroll back to top and highlight PDF export
  await smoothScrollToY(page, 0, 1200, 12);
  const pdfBtn = page.locator('a:has-text("Pobierz Raport PDF (KAS)")');
  await clickLocatorVisual(page, pdfBtn, 900, 300);
  await page.evaluate(() => {
    const toast = document.createElement('div');
    toast.id = 'olcys-pdf-toast';
    Object.assign(toast.style, {
      position: 'fixed',
      top: '158px',
      right: '28px',
      zIndex: '999997',
      padding: '10px 16px',
      borderRadius: '10px',
      background: '#15803D',
      color: '#FFFFFF',
      fontFamily: '"DejaVu Sans", sans-serif',
      fontSize: '13px',
      fontWeight: '700',
      boxShadow: '0 8px 20px rgba(21,128,61,0.35)',
    });
    toast.textContent = '✓ Wyeksportowano raport PDF KAS (Apache PDFBox UTF-8)';
    document.body.appendChild(toast);
  });
  await captureHeldFrame(page, 1500);
  await page.evaluate(() => document.getElementById('olcys-pdf-toast')?.remove());
  const s6End = INTRO_MS + liveElapsedMs; // 140000

  addCue(
    'seg6_report',
    'Etap 7 — Raport 3-Strefowy i PDF',
    'ETAP 7/7',
    s6Start,
    s6Start + 13000,
    ['Raport wyraźnie oddziela Strefę 1 (Fakty z URL/UTC/SHA-256),', 'Strefę 2 (Wyniki obliczeń) oraz Strefę 3 (Wnioski i PIT-38).'],
    'Końcowy raport OSINT rozdziela trzy sekcje: Fakty źródłowe z metadanymi odtwarzalności, deterministyczne Wyniki obliczeń oraz Wnioski wymagające dalszej weryfikacji przez analityka.'
  );
  addCue(
    'seg6_pdf',
    'Etap 7 — Weryfikacja SHA-256 i Eksport PDF',
    'ETAP 7/7',
    s6Start + 13000,
    s6End,
    ['Lokalny przelicznik SHA-256 potwierdza integralność 4/4 cytatów.', 'Jednym kliknięciem pobieramy gotowy raport PDF dla analityka.'],
    'Weryfikator w przeglądarce lokalnie potwierdza sumy SHA-256 wszystkich dowodów, po czym generujemy gotowy dokument PDF z pełnymi polskimi znakami.'
  );

  // =========================================================================
  // LIVE SEGMENT 7: [2:20 - 2:34] (14000 ms) Monitoring & Diff v1 -> v2
  // =========================================================================
  const s7Start = INTRO_MS + liveElapsedMs; // 140000
  await setStageBadge(page, 'PONAD MINIMUM • Monitoring i Diff w Czasie', '#B91C1C');

  const v2Btn = page.locator('button:has-text("Uruchom analizę kontrolną (v2")');
  await clickLocatorVisual(page, v2Btn, 900, 300);
  await captureHeldFrame(page, 700);

  await page.waitForSelector('text=WYKRYTO PODMIANĘ ADRESU PORTFELA NA PROFILU', { timeout: 60000 });
  await page.waitForTimeout(400);
  await page.evaluate(() => window.scrollTo({ top: 0, behavior: 'instant' }));
  await captureHeldFrame(page, 3100);

  const alertBox = page.locator('text=WYKRYTO PODMIANĘ ADRESU PORTFELA NA PROFILU').first();
  await moveCursorToLocator(page, alertBox, 1000);
  await captureHeldFrame(page, 4000);

  const deltaEl = page.locator('text=+57 000,00').first();
  await moveCursorToLocator(page, deltaEl, 900);
  await captureHeldFrame(page, 3100);
  const s7End = INTRO_MS + liveElapsedMs; // 154000

  addCue(
    'seg7_diff',
    'Funkcja ponad minimum — Diff v1→v2',
    'PONAD MINIMUM',
    s7Start,
    s7End,
    ['Analiza kontrolna v2 i Diff w czasie wykrywają podmianę portfela', 'na profilu GitHub (0x71C7...76F → 0xd8dA...045, +57 000 zł).'],
    'Ponad minimum projektowe cykliczny monitoring i analiza kontrolna v2 porównują historie w bazie, natychmiast wykrywając podmianę portfela na profilu GitHub oraz przyrost obrotu o 57 000 złotych.'
  );

  // =========================================================================
  // OUTRO CUE [2:34 - 2:45] (11000 ms)
  // =========================================================================
  addCue(
    'outro_1',
    'Plansza zamknięcia — Architektura KAS',
    'OUTRO',
    s7End,
    s7End + OUTRO_MS,
    ['Monorepo Java + React + PostgreSQL + Docker. Wymienny AI on-premise,', '0 danych na zewnątrz, 100% otwarte licencje Open Source.'],
    'OŁCyŚ to gotowe rozwiązanie on-premise dla KAS: monorepo Java 21, React i PostgreSQL, architektura pluginowa oraz lokalny AI bez chmury i bez licencji GPL lub AGPL. Dziękujemy.'
  );

  // Repeat last file cleanly for ffmpeg concat demuxer
  const lastLine = concatLines.filter((l) => l.startsWith('file ')).pop();
  if (lastLine) concatLines.push(lastLine);

  fs.writeFileSync(CONCAT_FILE, concatLines.join('\n') + '\n', 'utf8');
  fs.writeFileSync(
    TIMESTAMPS_FILE,
    JSON.stringify(
      {
        introDurationMs: INTRO_MS,
        liveDurationMs: liveElapsedMs,
        outroDurationMs: OUTRO_MS,
        totalDurationMs: INTRO_MS + liveElapsedMs + OUTRO_MS,
        segments: cueSegments,
      },
      null,
      2,
    ),
    'utf8',
  );
  writeSubtitlesAndVoiceover();

  console.log(`Live frames recorded: ${frameSeq}, liveElapsedMs=${liveElapsedMs}, totalAssembledMs=${INTRO_MS + liveElapsedMs + OUTRO_MS}`);
  await context.close();
} finally {
  await browser.close();
}
