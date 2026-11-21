#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${1:-http://localhost:8080}"
BASE_URL="${BASE_URL%/}"

require_cmd() {
  local cmd="$1"
  if ! command -v "$cmd" >/dev/null 2>&1; then
    echo "[BŁĄD] Wymagane narzędzie '$cmd' nie jest zainstalowane w PATH." >&2
    exit 1
  fi
}

require_cmd curl
require_cmd jq

TMP_DIR="$(mktemp -d)"
cleanup() {
  rm -rf "$TMP_DIR"
}
trap cleanup EXIT

pass() {
  echo "[OK] $1"
}

fail() {
  echo "[BŁĄD] $1" >&2
  exit 1
}

http_get_json() {
  local url="$1"
  local out_file="$2"
  local status
  status="$(curl -sS --connect-timeout 5 --max-time 20 \
    -H "Accept: application/json" \
    -o "$out_file" -w "%{http_code}" "$url")" || fail "Połączenie z $url nie powiodło się."
  if [[ "$status" -lt 200 || "$status" -ge 300 ]]; then
    echo "--- Odpowiedź HTTP $status z $url ---" >&2
    cat "$out_file" >&2 || true
    echo "" >&2
    fail "Żądanie GET $url zwróciło kod HTTP $status."
  fi
}

http_post_json() {
  local url="$1"
  local payload="$2"
  local out_file="$3"
  local status
  status="$(curl -sS --connect-timeout 5 --max-time 30 \
    -X POST \
    -H "Content-Type: application/json" \
    -H "Accept: application/json" \
    -d "$payload" \
    -o "$out_file" -w "%{http_code}" "$url")" || fail "Połączenie z $url nie powiodło się."
  if [[ "$status" -lt 200 || "$status" -ge 300 ]]; then
    echo "--- Odpowiedź HTTP $status z $url ---" >&2
    cat "$out_file" >&2 || true
    echo "" >&2
    fail "Żądanie POST $url zwróciło kod HTTP $status."
  fi
}

http_get_binary() {
  local url="$1"
  local out_file="$2"
  local status
  status="$(curl -sS --connect-timeout 5 --max-time 30 \
    -o "$out_file" -w "%{http_code}" "$url")" || fail "Pobranie pliku z $url nie powiodło się."
  if [[ "$status" -lt 200 || "$status" -ge 300 ]]; then
    fail "Pobranie $url zwróciło kod HTTP $status."
  fi
}

echo "================================================================"
echo " OŁCyŚ — Automatyczna walidacja scenariuszy KAS (E2E API Check)"
echo " Target: ${BASE_URL}"
echo "================================================================"

# 1. Health check (/api/health)
HEALTH_JSON="${TMP_DIR}/health.json"
http_get_json "${BASE_URL}/api/health" "$HEALTH_JSON"
jq -e '.status == "UP" and .externalDataTransfer == false' "$HEALTH_JSON" >/dev/null \
  || fail "/api/health nie potwierdziło status == UP i externalDataTransfer == false."
AI_PROVIDER="$(jq -r '.aiProvider // "brak"' "$HEALTH_JSON")"
JAVA_VER="$(jq -r '.javaVersion // "?"' "$HEALTH_JSON")"
pass "1/8 Health check: status=UP, externalDataTransfer=false (Java ${JAVA_VER}, AI=${AI_PROVIDER})"

# 2. Lista scenariuszy (/api/scenarios)
SCENARIOS_JSON="${TMP_DIR}/scenarios.json"
http_get_json "${BASE_URL}/api/scenarios" "$SCENARIOS_JSON"
SCENARIO_COUNT="$(jq 'length' "$SCENARIOS_JSON")"
jq -e 'type == "array" and length >= 3' "$SCENARIOS_JSON" >/dev/null \
  || fail "/api/scenarios musi zwracać co najmniej 3 scenariusze walidacyjne."
pass "2/8 Lista scenariuszy KAS: pobrano ${SCENARIO_COUNT} scenariusze"

# 3. Scenariusz 1 v1 (POST /api/subjects — @krypto_janek, tryb SYNTHETIC, snapshotStep: 1)
RUN1_JSON="${TMP_DIR}/run1.json"
REQ_S1_V1='{
  "displayName": "Jan Kowalski (@krypto_janek)",
  "identifiers": [
    { "type": "NICK", "value": "krypto_janek" },
    { "type": "FULL_NAME", "value": "Jan Kowalski" },
    { "type": "EMAIL", "value": "janek.dev@example.pl" }
  ],
  "mode": "SYNTHETIC",
  "sources": ["GitHub", "KantorP2P.pl", "WaybackArchive", "ForumKrypto.pl", "Synthetic"],
  "networks": ["ETH", "BTC"],
  "snapshotStep": 1
}'
http_post_json "${BASE_URL}/api/subjects" "$REQ_S1_V1" "$RUN1_JSON"

RUN1_ID="$(jq -r '.runId // empty' "$RUN1_JSON")"
SUBJECT_ID="$(jq -r '.subjectId // empty' "$RUN1_JSON")"
[[ -n "$RUN1_ID" && -n "$SUBJECT_ID" ]] \
  || fail "Brak pola runId lub subjectId w odpowiedzi POST /api/subjects."
pass "3/8 Scenariusz 1 (v1): utworzono sprawę subjectId=${SUBJECT_ID}, runId=${RUN1_ID}"

# 4. Weryfikacja wyników Scenariusza 1 v1
# 4a. Zatwierdzony ETH 100% (synergia 85 + 15) i BTC 70%
jq -e '
  (.confirmedAddresses | any(
    .network == "ETH"
    and (.address | ascii_downcase) == "0x71c7656ec7ab88b098defb751b7401b5f6d8976f"
    and .falsePositive == false
    and .confidenceScore == 100
  ))
  and
  (.confirmedAddresses | any(
    .network == "BTC"
    and .address == "bc1qar0srrr7xfkvy5l643lydnw9re59gtzzwf5mdq"
    and .falsePositive == false
    and .confidenceScore == 70
  ))
' "$RUN1_JSON" >/dev/null \
  || fail "run1: nie znaleziono oczekiwanego portfela ETH (100%) lub BTC (70%) w confirmedAddresses."

# 4b. Odrzucone FP (0%): Binance CEX z interactionRecorded == true oraz Smart Contract Uniswap V3 (bytecode != 0x)
jq -e '
  (.rejectedAddresses | any(
    (.address | ascii_downcase) == "0x28c6c06298d514db089934071355e5743bf21d60"
    and .falsePositive == true
    and .confidenceScore == 0
    and .interactionRecorded == true
  ))
  and
  (.rejectedAddresses | any(
    (.address | ascii_downcase) == "0xe592427a0aece92de3edee1f18e0157c05861564"
    and .falsePositive == true
    and .confidenceScore == 0
    and .onChainMetrics != null
    and .onChainMetrics.isSmartContract == true
    and ((.onChainMetrics.bytecodePrefix // "0x") != "0x")
    and ((.onChainMetrics.bytecodePrefix // "") != "")
  ))
' "$RUN1_JSON" >/dev/null \
  || fail "run1: nie potwierdzono odrzucenia Binance (0%, interactionRecorded=true) i Uniswap V3 (0%, bytecode != 0x)."

# 4c. Niepuste cashOutSignals oraz obecne 3 strefy raportu
jq -e '
  (.cashOutSignals | type == "array" and length > 0)
  and (.report != null)
  and (.report.zone1SourceFacts | type == "array" and length > 0)
  and (.report.zone2Computations != null)
  and (.report.zone3VerificationConclusions != null)
' "$RUN1_JSON" >/dev/null \
  || fail "run1: brak sygnałów cashOutSignals lub którejś z 3 stref raportu OSINT."

pass "4/8 Weryfikacja v1: ETH 100% (synergia), BTC 70%, FP Binance (0%, interakcja=true), Uniswap V3 (0%, bytecode!=0x), Cash-Out i 3 strefy raportu"

# 5. Analiza kontrolna v2 (POST /api/subjects/{subjectId}/runs z snapshotStep: 2)
RUN2_JSON="${TMP_DIR}/run2.json"
REQ_S1_V2='{
  "mode": "SYNTHETIC",
  "sources": ["GitHub", "KantorP2P.pl", "WaybackArchive", "ForumKrypto.pl", "Synthetic"],
  "networks": ["ETH", "BTC"],
  "snapshotStep": 2
}'
http_post_json "${BASE_URL}/api/subjects/${SUBJECT_ID}/runs" "$REQ_S1_V2" "$RUN2_JSON"

RUN2_ID="$(jq -r '.runId // empty' "$RUN2_JSON")"
[[ -n "$RUN2_ID" && "$RUN2_ID" != "$RUN1_ID" ]] \
  || fail "Niepoprawne run2Id w odpowiedzi POST /api/subjects/${SUBJECT_ID}/runs."
jq -e '
  .confirmedAddresses | any(
    (.address | ascii_downcase) == "0xd8da6bf26964af9d7eed9e03e53415d37aa96045"
    and .confidenceScore == 100
  )
' "$RUN2_JSON" >/dev/null \
  || fail "run2: nie wykryto zaktualizowanego portfela ETH 0xd8dA...045 z wynikiem 100%."
pass "5/8 Scenariusz 1 (v2): uruchomiono analizę kontrolną run2Id=${RUN2_ID}"

# 6. Diff v1 -> v2 (GET /api/subjects/{subjectId}/diff?from={run1Id}&to={run2Id})
DIFF_JSON="${TMP_DIR}/diff.json"
http_get_json "${BASE_URL}/api/subjects/${SUBJECT_ID}/diff?from=${RUN1_ID}&to=${RUN2_ID}" "$DIFF_JSON"
jq -e '.walletRotationDetected == true and (.walletRotations | length >= 1)' "$DIFF_JSON" >/dev/null \
  || fail "Diff v1->v2 nie wykrył podmiany portfela (walletRotationDetected != true)."
ROTATION_DESC="$(jq -r '.walletRotations[0] | "\(.sourceName): \(.previousAddress) -> \(.newAddress)"' "$DIFF_JSON")"
pass "6/8 Diff v1 -> v2: wykryto podmianę portfela (${ROTATION_DESC})"

# 7. Pobranie raportu PDF oraz eksportu JSON
REPORT_PDF="${TMP_DIR}/report.pdf"
http_get_binary "${BASE_URL}/api/runs/${RUN1_ID}/report.pdf" "$REPORT_PDF"

PDF_SIZE="$(wc -c < "$REPORT_PDF" | tr -d '[:space:]')"
[[ "$PDF_SIZE" -gt 2000 ]] \
  || fail "Rozmiar pobranego report.pdf (${PDF_SIZE} B) nie przekracza 2000 bajtów."

PDF_MAGIC="$(LC_ALL=C head -c 4 "$REPORT_PDF")"
[[ "$PDF_MAGIC" == "%PDF" ]] \
  || fail "Pobrany report.pdf nie zaczyna się od nagłówka %PDF (otrzymano: '${PDF_MAGIC}')."

REPORT_EXPORT_JSON="${TMP_DIR}/report.json"
http_get_json "${BASE_URL}/api/runs/${RUN1_ID}/report.json" "$REPORT_EXPORT_JSON"
jq -e '
  type == "object"
  and (
    (.runId != null and .report.zone1SourceFacts != null)
    or (.zone1SourceFacts != null and .zone2Computations != null and .zone3VerificationConclusions != null)
  )
' "$REPORT_EXPORT_JSON" >/dev/null \
  || fail "Eksport /api/runs/${RUN1_ID}/report.json nie zawiera oczekiwanej struktury 3-strefowej."

pass "7/8 Eksport dowodowy: pobrano report.pdf (${PDF_SIZE} B, nagłówek %PDF) oraz report.json"

# 8. Scenariusz 3 — Szum informacyjny i False Positives (@lucky_drop_2026)
S3_JSON="${TMP_DIR}/s3.json"
REQ_S3='{
  "displayName": "Weryfikacja negatywna: @lucky_drop_2026 (Szum i False Positives)",
  "identifiers": [
    { "type": "NICK", "value": "lucky_drop_2026" }
  ],
  "mode": "SYNTHETIC",
  "sources": ["Reddit", "ForumKrypto.pl", "Synthetic"],
  "networks": ["ETH"],
  "snapshotStep": 1
}'
http_post_json "${BASE_URL}/api/subjects" "$REQ_S3" "$S3_JSON"

jq -e '
  (.confirmedAddresses | any(
    (.address | ascii_downcase) == "0x5aaeb6053f3e94c9b9a09f33669435e7ef1beaed"
    and .confidenceScore == 20
    and .falsePositive == false
  ))
  and
  (.rejectedAddresses | any(
    (.address | ascii_downcase) == "0x165cd37b4c644c2921454429e7f9358d18a45e14"
    and .falsePositive == true
    and .confidenceScore == 0
  ))
  and
  (.rejectedAddresses | any(
    (.address | ascii_downcase) == "0xd90e2f925da726b50c4ed8d0fb90ad053324f31b"
    and .falsePositive == true
    and .confidenceScore == 0
  ))
' "$S3_JSON" >/dev/null \
  || fail "Scenariusz 3: nie potwierdzono wyniku 20% dla szumu lub 0% w rejectedAddresses dla zbiórki Aid For Ukraine i miksera Tornado Cash."

pass "8/8 Scenariusz 3 (Szum/FP): giveaway 20%, zbiórka charytatywna i mikser odrzucone z 0%"

echo "================================================================"
echo " SUKCES: Wszystkie 8 etapów walidacji KAS zakończone pomyślnie!"
echo "================================================================"
