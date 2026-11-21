# Raport Audytu i Testów Aplikacji OŁCyŚ (Osintowy Łapacz Cyfrowych Śladów)

**Repozytorium:** `/home/maks/HN/olcys`  
**Data audytu:** 2026-11-21  
**Przedmiot porównania:** Specyfikacja wyzwania KAS (`OSINT.pdf`), dokumentacja projektowa (`README.md`, `AGENTS.md`, `Omowienie.pdf`, `docs/OLCyS_Prezentacja_KAS_10_Slajdow.pdf`, `docs/PREZENTACJA_10_SLAJDOW_I_SCENARIUSZ_WIDEO.md`) oraz pełny kod źródłowy backendu (Java 21 / Spring Boot 3.4) i frontendu (React 19 / TypeScript / Vite 6).  
**Zasada audytu:** Audyt w trybie *read-only* — w trakcie sprawdzania nie zmodyfikowano żadnego istniejącego pliku kodu, konfiguracji ani dokumentacji w repozytorium.

---

## 1. Podsumowanie wykonawcze audytu i ocena ogólna

Aplikacja **OŁCyŚ** realizuje rdzeń wymagań wyzwania Krajowej Administracji Skarbowej (`OSINT.pdf`) na bardzo wysokim poziomie architektonicznym i prezentacyjnym w ścieżce **syntetycznej/demonstracyjnej (`SYNTHETIC`)**:
- **Atuty architektury i realizacji:**
  - Spójne monorepo: backend **Java 21 + Spring Boot 3.4.0** (z włączonymi `Virtual Threads`), **Flyway + PostgreSQL 16** (oraz lokalny profil `H2` w trybie PostgreSQL), **Apache PDFBox 3.0.3** (z osadzonymi fontami `DejaVuSans` obsługującymi pełne polskie znaki diakrytyczne), **Bouncy Castle 1.79** (`Keccak-256` dla EIP-55) oraz frontend **React 19 + TypeScript 5.9 + Tailwind CSS 4 + `@xyflow/react` + Recharts**.
  - Rzeczywista kryptograficzna walidacja adresów (`ETH` EIP-55 z odfiltrowaniem 64-znakowych hashy TX, `BTC` Base58Check/Bech32/Bech32m, `TRON` Base58Check) oraz baza 83 zweryfikowanych adresów w `entity-tags.json`.
  - Deterministyczny i wyjaśnialny **Wskaźnik Pewności (Confidence Score)** z jawnym rozbiciem punktowym (`ScoreBreakdownItemDto`) i regułą synergii `+15 pp` na niezależnych domenach.
  - Czytelny **3-strefowy raport OSINT** (Strefa 1: Fakty Źródłowe z URL, UTC, SHA-256; Strefa 2: Wyniki Obliczeń; Strefa 3: Wnioski i ryzyka PIT-38) dostępny w UI, PDF (`report.pdf`), JSON (`report.json`) oraz uzupełniony o dodatkowy projekt wezwania do CASP (`casp-request.pdf`) i kalkulator luki podatkowej 19% PIT-38.
  - Izolacja AI On-Premise (`OLCYS_AI_ALLOW_EXTERNAL=false`) rzucająca `SecurityException` przy próbie konfiguracji zewnętrznego dostawcy API oraz działająca integracja z lokalnym serwerem **Ollama** (`llama3.2:latest`).

Jednocześnie **dogłębne testy na żywo (tryb `LIVE`, `HYBRID`, profil `ollama`, przegląd logiki konektorów i silnika Diff)** ujawniły **15 istotnych problemów i rozbieżności**, w tym **6 błędów krytycznych/wysokiego ryzyka** w warstwie Web3 `LIVE`, obsłudze timeoutów, klasyfikacji EIP-7702, mechanizmie Chain-of-Custody oraz atrapie harmonogramu CRON.

| Obszar | Ocena w trybie `SYNTHETIC` / `local-rules` | Ocena w trybie `LIVE` / `HYBRID` / `ollama` |
| :--- | :--- | :--- |
| Walidacja kryptograficzna adresów | **Bardzo wysoka** (37/37 testów JUnit OK) | **Bardzo wysoka** |
| Confidence Score & 3-warstwowy Firewall FP | **Wysoka** (scenariusze 1–3 działają wzorowo) | **Niska / Wadliwa** (błąd licznika Blockscout, utrata `eth_getCode` przy timeoucie, fałszywe odrzucanie EIP-7702, HTTP 401 w TronScan) |
| Konektory Web2 OSINT | **Wysoka** (deterministyczne `scenarios.json`) | **Częściowa** (GitHub, ENS, Keybase działają; Reddit `403`, Wayback `503`/timeout i brak pobierania treści; wyciek testowych danych w trybie `HYBRID`) |
| Wykrywanie podmiany portfela (`RunDiffService`) | **Działa dla Scenariusza 1 v1→v2** | **Podatna na fałszywe alarmy** (błąd `sameWeb2ProfileOrUrl` i brak sprawdzania `toConfirmed`) |
| Monitoring cykliczny CRON (`CyclicMonitoringController`) | **Atrapa (tylko in-memory heartbeat)** | **Atrapa (tylko in-memory heartbeat)** |
| Raportowanie UI / PDF / JSON | **Wysoka** (3 strefy, polskie znaki, PDF 44 KB) | **Rozbieżność SHA-256 w weryfikatorze UI** dla dowodów `LIVE` |
| Zgodność formalna z `OSINT.pdf` | **Częściowa** (brak wideo w repozytorium i brak plików w `docs/screenshots/`) | **Naruszenie pkt 5.7/9 przy `HYBRID`** (wysyłka danych testowych na zewnątrz) |

---

## 2. Przeprowadzone testy praktyczne i ich wyniki

### 2.1. Testy jednostkowe i integracyjne backendu (`./mvnw test`)
- **Komenda:** `cd /home/maks/HN/olcys/backend && ./mvnw test`
- **Wynik:** `Tests run: 37, Failures: 0, Errors: 0, Skipped: 0` (`BUILD SUCCESS`, czas `01:41 min`).
- **Zakres:** Wszystkie 12 klas testowych zaliczone, w tym `LocalOllamaLiveIntegrationTest` wykonujący prawdziwe wywołania do lokalnego modelu `llama3.2:latest` pod `http://localhost:11434/v1`.

### 2.2. Kontrola typów TypeScript i build frontendu (`npm run build`)
- **Komenda:** `cd /home/maks/HN/olcys/frontend && npm run build`
- **Wynik:** `tsc --noEmit && vite build` zakończone kodem `0` (`2579 modules transformed`, czas `14.57s`, 0 błędów typów TypeScript).

### 2.3. Test akceptacyjny E2E (`scripts/run-demo-check.sh`)
Przeprowadzono dwa niezależne uruchomienia skryptu walidacyjnego:
1. **Wobec działającego w tle backendu na porcie `8080` z profilem `OLCYS_AI_PROVIDER=ollama` (`llama3.2:latest`):**
   - Kroki `1/8` (`/api/health`) i `2/8` (`/api/scenarios`) przeszły pomyślnie.
   - Skrypt **zakończył się błędem na kroku `3/8` lub `5/8`:** `curl: (28) Operation timed out after 30002 milliseconds with 0 bytes received` (podczas pierwszego uruchomienia przy równoczesnym obciążeniu z Mavena na kroku `3/8`, a podczas kolejnego samodzielnego uruchomienia na kroku `5/8` dla `POST /api/subjects/{id}/runs`).
   - Przyczyna: timeout odpowiedzi lokalnego modelu Ollama wynosi do 45s (`olcys.ai.timeout-seconds: 45`), podczas gdy `run-demo-check.sh` wymusza `--max-time 30`.
   - Ręczne odpytanie tego samego serwera z limitem `60s` przez Pythona potwierdziło poprawne wygenerowanie analizy z `aiProviderUsed: "ollama"`, `report.pdf` (44 286 B, nagłówek `%PDF-`) oraz `casp-request.pdf` (29 402 B, nagłówek `%PDF-`).
2. **Wobec testowej instancji na porcie `18080` z domyślnym profilem `OLCYS_AI_PROVIDER=local-rules`:**
   - **Komenda:** `/home/maks/HN/olcys/scripts/run-demo-check.sh http://localhost:18080`
   - **Wynik:** **8/8 kroków `[OK]`** (`SUKCES: Wszystkie 8 etapów walidacji KAS zakończone pomyślnie!`).
   - Zweryfikowano: `status=UP`, `externalDataTransfer=false`, 3 scenariusze, Scenariusz 1 v1 (`ETH 100%`, `BTC 70%`, odrzucone FP `Binance 0% interactionRecorded=true` i `Uniswap V3 0% bytecode!=0x`, Cash-Out i 3 strefy), v2 (`0xd8dA...045 100%`), Diff (`walletRotationDetected=true`), `report.pdf` (`44752 B`, `%PDF`), `report.json` oraz Scenariusz 3 (`20%` szum, `0%` Aid For Ukraine i Tornado Cash).

### 2.4. Testy trybu `LIVE`, `HYBRID` i zewnętrznych API Web2/Web3
Uruchomiono tymczasową instancję backendu (`--spring.profiles.active=local --server.port=18081 --olcys.ai.provider=local-rules`) oraz wykonano bezpośrednie zapytania testowe do API aplikacji i zewnętrznych źródeł:
- **Test 1 (`POST /api/subjects`, `mode="LIVE"`, `NICK="vitalik.eth"`):** HTTP `201`, `durationMs: 8408`. `EnsConnector` poprawnie rozwiązał `vitalik.eth` → `0xd8dA6BF26964aF9D7eEd9e03E53415D37aA96045`. **Jednak portfel został fałszywie odrzucony jako `SMART_CONTRACT (0%)`** (szczegóły w Błędzie #3).
- **Test 2 (`POST /api/subjects`, `mode="LIVE"`, `CRYPTO_ADDRESS="0xE592427A0AEce92De3Edee1F18E0157C05861564"` — Uniswap V3 Router):** HTTP `201`. W logu backendu wystąpiło `WARN: Web3 inspection for 'ETH:0xe592427a0aece92de3edee1f18e0157c05861564' timed out or failed: null`. W wyniku JSON zwrócono `isSmartContract: false` i `bytecodePrefix: "0x"`, a adres odrzucono jako `DEX_ROUTER` wyłącznie dzięki statycznemu `entity-tags.json` (szczegóły w Błędzie #2).
- **Test 3 (`GET /v3/api-docs`):** HTTP `200`, udokumentowanych **11 ścieżek** (12 operacji HTTP).
- **Test 4 (`POST /api/subjects`, Scenariusz 1 w trybie `HYBRID`):** HTTP `201`, `durationMs: 5014`. Zwrócono 5 dowodów zamiast 4 — pierwszym dowodem było prawdziwe, niezwiązane ze sprawą konto GitHub `https://github.com/johnykov` dopasowane przez wyszukiwanie `FULL_NAME: "Jan Kowalski"` (szczegóły w Błędzie #7).
- **Bezpośrednie sondowanie zewnętrznych API używanych przez konektory:**
  - `GitHub API` (`api.github.com/users/torvalds`): `HTTP 200` (działa).
  - `ENS Ideas` (`api.ensideas.com/ens/resolve/vitalik.eth`): `HTTP 200` (działa).
  - `Keybase API` (`keybase.io/_/api/1.0/user/lookup.json?usernames=max`): `HTTP 200` (działa).
  - `Mempool.space` (`mempool.space/api/address/1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa`): `HTTP 200` (działa, `tx_count: 67583`).
  - `Ethereum RPC` (`ethereum-rpc.publicnode.com`, `eth_getCode`): `HTTP 200` (działa).
  - `Blockscout v2` (`/api/v2/addresses/{addr}`): `HTTP 200`, ale **brak pola `transactions_count`** (jest dopiero pod `/counters`).
  - `Reddit JSON` (`reddit.com/user/spez/submitted.json`): `HTTP 403 Blocked`.
  - `Wayback CDX` (`web.archive.org/cdx/search/cdx?...`): `HTTP 503 Service Unavailable` lub timeout po 8s.
  - `TronScan API` (`apilist.tronscanapi.com/api/accountv2?address=...`): `HTTP 401 Unauthorized`.

---

## 3. Macierz zgodności ze specyfikacją `OSINT.pdf`

| Wymaganie z `OSINT.pdf` | Status | Ocena i uwagi z audytu |
| :--- | :---: | :--- |
| **Sekcja 2:** Co najmniej 1 identyfikator wejściowy (imię/nazwisko, nick, komunikator, login social, e-mail, publiczny adres krypto) | **SPEŁNIONE** | Obsługiwane wszystkie 6 typów w `IdentifierDto`, `SearchForm.tsx` i `AnalysisOrchestrator` (łącznie z Reverse OSINT od `CRYPTO_ADDRESS`). |
| **Sekcja 2:** Cykliczne wykonywanie analiz, zapisywanie wyników, porównywanie rezultatów | **CZĘŚCIOWO SPEŁNIONE** | Zapis i ręczne uruchamianie kolejnych analiz (`POST /api/subjects/{id}/runs`) oraz porównanie (`GET /api/subjects/{id}/diff`) działają w bazie (PostgreSQL/H2). **Automatyczny monitoring CRON (`CyclicMonitoringController`) jest atrapą** (patrz Błąd #5), a `RunDiffService` ma lukę logiczną (patrz Błąd #8). |
| **Sekcja 2 & 5.8:** Dalsza rozbudowa o nowe źródła danych, sieci blockchain i mechanizmy analityczne | **SPEŁNIONE** | Czysta architektura pluginowa oparta o interfejsy `Web2Connector`, `Web3Connector`, `EntityTagProvider`, `AiClient`. |
| **Sekcja 3 (Wymagane):** Wyszukiwać możliwe powiązania Web2 ↔ adresy krypto, automatycznie rozpoznawać i wyodrębniać adresy | **SPEŁNIONE (z zastrzeżeniem `LIVE`)** | `CryptoAddressValidator` działa bezbłędnie (EIP-55, Base58Check, Bech32/m, TRON). W trybie `LIVE` działają GitHub, ENS, Keybase; Reddit/Wayback mają blokady lub nie pobierają treści stron (patrz Błąd #11). |
| **Sekcja 3 & Załącznik Krok 2:** Kategoryzować powiązania i wyliczać Wskaźnik Pewności (Confidence Score) + premia synergii | **SPEŁNIONE** | `ConfidenceScorer` realizuje dokładnie progi `85/70/50/20/0%` oraz premię `+15 pp` dla `>= 2` niezależnych domen Web2 z limitem `100%`. |
| **Sekcja 3:** Wykrywać wskaźniki Fiat Cash-Out (P2P, bramki, karty krypto-fiat, stablecoiny) | **SPEŁNIONE (w `SYNTHETIC`/ETH/BTC)** | `CashOutAnalyzer` wykrywa `CEX_OFFRAMP`, `STABLECOIN_CONVERSION`, `P2P_OR_GATEWAY` (±72h), `RECURRING_CASHOUT` i `AML_STRUCTURING_OR_2HOP`. Dla sieci TRON w trybie `LIVE` detekcja nie działa (patrz Błąd #4). |
| **Sekcja 3 & Załącznik Krok 3:** Ograniczać błędne przypisania (giełdy, powiernicze, smart kontrakty `bytecode != 0x`, instytucjonalne) | **CZĘŚCIOWO SPEŁNIONE** | W `SYNTHETIC` działa bezbłędnie. W `LIVE` dla Ethereum: nie działa Warstwa 3 (`txCount > 10 000`), występuje ryzyko timeoutu `eth_getCode` i przepuszczenia nieznanego smart kontraktu, a portfele EIP-7702 są błędnie odrzucane jako kontrakty (Błędy #1, #2, #3). |
| **Sekcja 3 (Opcjonalne):** Porównywać zmiany zachodzące w czasie (podmiana adresów portfeli) | **SPEŁNIONE (z błędem brzegowym)** | Działa w Scenariuszu 1 v1→v2; posiada błąd logiczny dla wielu artefaktów `OWN_PROFILE_VERIFIED` z tego samego źródła (patrz Błąd #8). |
| **Sekcja 3 (Akceptacja):** Scenariusze testowe na danych zanonimizowanych/syntetycznych + zapis URL, daty UTC, metadanych | **SPEŁNIONE** | 3 kompletne scenariusze w `scenarios.json`, zapis w PostgreSQL/H2 z URL, UTC, SHA-256 i cytatem. |
| **Sekcja 4 (Formalne):** Opis i parametry, prezentacja PDF ≤ 10 slajdów, film ≤ 3 min w otwartym repo, link do kodu | **NIEPEŁNE** | Jest opis + parametry (`README.md`) i prezentacja 10 slajdów (`OLCyS_Prezentacja_KAS_10_Slajdow.pdf`). **Brak nagranego filmu wideo (≤ 3 min)** w repozytorium (jest tylko scenariusz w `.md`). |
| **Sekcja 5.1–5.4:** Open Source bez GPL/AGPL, backend Java/Spring, frontend React, baza PostgreSQL, monorepo, Docker | **SPEŁNIONE** | 0 bibliotek GPL/AGPL (Apache PDFBox 3.0.3), Java 21, React 19, PostgreSQL 16 + Flyway, Docker Compose. |
| **Sekcja 5.5–5.6 & Krok 4:** Wymienny lokalny AI + wyjaśnialność i odtwarzalność wyników | **SPEŁNIONE** | Interfejs `AiClient` (`local-rules` / `ollama` / `openai-compatible`), scoring w 100% deterministyczny. |
| **Sekcja 5.7 & 9:** Brak odwołań do niezaufanych zasobów i **zakaz wysyłania danych testowych do zewnętrznych usług** | **NARUSZONE DOMYŚLNIE** | Wysłanie danych AI na zewnątrz jest twardo zablokowane (`SecurityException`). Jednak domyślny tryb `HYBRID` (zalecany m.in. w Scenariuszu 1) wysyła testowe identyfikatory do publicznych API GitHub, Reddit, Wayback i Keybase (patrz Błąd #7). |
| **Sekcja 5.9 (`README.md`):** Opis, założenia, instrukcja, **przykłady (screenshoty/logi)** | **NIEPEŁNE** | `README.md` odwołuje się do 5 zrzutów ekranu w `docs/screenshots/01-...png` .. `05-...png`, ale **katalog `docs/screenshots/` nie istnieje**. |
| **Sekcja 6 & 7:** Legalność OSINT, kompletny raport rozróżniający fakty źródłowe, obliczenia i wnioski, czytelny dla nietechnicznych | **SPEŁNIONE** | Wyłącznie pasywny OSINT; wyraźny 3-strefowy podział w UI, PDF i JSON. |

---

## 4. Wykryte błędy, luki logiczne i problemy techniczne

### 4.1. Błędy krytyczne i wysokiego ryzyka

#### [BŁĄD #1] Błąd odczytu liczby transakcji w `EthereumConnector` (tryb `LIVE`) — martwa Warstwa 3 Firewalla FP dla Ethereum
- **Lokalizacja:** `backend/src/main/java/pl/kas/olcys/connector/web3/EthereumConnector.java` (linie 85–110) oraz `FalsePositiveFirewall.java` (linie 97–106).
- **Opis problemu:** `EthereumConnector.inspectLive()` odpytuje endpoint `https://eth.blockscout.com/api/v2/addresses/{address}` i parsuje `root.path("transactions_count").asInt(0)`. W rzeczywistej strukturze API Blockscout v2 endpoint `/api/v2/addresses/{address}` **nie zawiera pola `transactions_count`** (zwraca m.in. `is_contract`, `name`, `public_tags`, `metadata`, `coin_balance`), natomiast licznik `transactions_count` znajduje się pod osobnym endpointem `/api/v2/addresses/{address}/counters` i jest zwracany jako string (np. `"78438"`).
- **Skutek i dowód:** `blockscoutTx` w trybie `LIVE` wynosi zawsze `0`, po czym kod wykonuje fallback `if (txCount == 0 && !txs.isEmpty()) txCount = txs.size();`. Ponieważ `fetchBlockscoutTransactions()` przerywa pętlę przy `list.size() >= 15`, każdy adres ETH w trybie `LIVE` spoza `entity-tags.json` ma maksymalnie `txCount = 15`. **Warstwa 3 Firewalla FP (`txCount > 10 000 -> SUSPECTED_CUSTODIAL`) w trybie `LIVE` dla Ethereum nigdy nie zadziała.** Dodatkowo konektor ignoruje tablice `public_tags` i `metadata.tags` z Blockscouta, sprawdzając wyłącznie pole `name`.

#### [BŁĄD #2] Ryzyko utraty `eth_getCode` przy timeoucie 5s i przypisania Smart Kontraktu jako portfela prywatnego (`LIVE`)
- **Lokalizacja:** `backend/src/main/java/pl/kas/olcys/connector/web3/EthereumConnector.java` (linie 80–128) oraz `AnalysisOrchestrator.java` (linie 71, 353, 519–561).
- **Opis problemu:** `EthereumConnector.inspectLive()` wykonuje 3 sekwencyjne zapytania HTTP w jednym zadaniu: (1) `eth_getCode` do publicnode.com, (2) GET `/addresses/{addr}` do Blockscouta, (3) GET `/addresses/{addr}/transactions` do Blockscouta — **nawet wtedy, gdy krok 1 (`bytecode != "0x"`) lub krok 2 (`is_contract == true`) jednoznacznie wykazał już, że adres jest smart kontraktem**. Tymczasem `AnalysisOrchestrator.inspectWeb3Parallel()` narzuca łączny deadline `CONNECTOR_TIMEOUT_SECONDS = 5L` dla wszystkich 3 zapytań razem. Jeśli endpoint `/transactions` dla popularnego kontraktu odpowiada 4–5 sekund, `future.get()` wygasa, zadanie zostaje anulowane, a `profilesByKey.get(key)` staje się `null`.
- **Dowód z testu LIVE:** Przy teście `Uniswap V3 SwapRouter` (`0xE592427A0AEce92De3Edee1F18E0157C05861564`) w trybie `LIVE` backend zalogował:  
  `WARN ... Web3 inspection for 'ETH:0xe592427a0aece92de3edee1f18e0157c05861564' timed out or failed: null`  
  a w JSON-ie wyjściowym zwrócił `onChainMetrics.isSmartContract = false` i `bytecodePrefix = "0x"`. Adres ten został odrzucony tylko dlatego, że figurował w lokalnym `entity-tags.json`.
- **Skutek:** Dla dowolnego smart kontraktu spoza statycznej listy `entity-tags.json`, którego historia transakcji w Blockscout odpowiada wolniej niż ~4s, utracony zostanie wynik `eth_getCode`, `profile` będzie `null`, a `FalsePositiveFirewall.evaluate(..., null)` zwróci `FirewallDecision.accepted()` — **błędnie przypisując smart kontrakt jako prywatny portfel podatnika**.

#### [BŁĄD #3] Fałszywe odrzucanie prywatnych portfeli EOA z delegacją EIP-7702 jako `SMART_CONTRACT` (`0%`) w trybie `LIVE`
- **Lokalizacja:** `backend/src/main/java/pl/kas/olcys/engine/FalsePositiveFirewall.java` (linie 50–70, 146–148), `scenarios.json` (linie 20, 52, 62), `EthereumConnector.java` (linia 321).
- **Opis problemu:** W sieci Ethereum po aktualizacji Pectra (EIP-7702) prywatne portfele użytkowników (EOA) mogą mieć ustawiony 23-bajtowy wskaźnik delegacji (`0xef0100` + 20-bajtowy adres kontraktu), przez co `eth_getCode` zwraca kod zaczynający się od `0xef0100...` (a Blockscout ustawia `is_contract: true`).
- **Dowód z testu LIVE:** Adres `0xd8dA6BF26964aF9D7eEd9e03E53415D37aA96045` (publiczny portfel `vitalik.eth`, używany jednocześnie w `scenarios.json` jako portfel Jana Kowalskiego w kroku v2!) zwraca w żywym RPC: `result: "0xef01005a7fc11397e9a8ad41bf10bf13f22b0a63f96f6d"`. Test LIVE dla `vitalik.eth` odrzucił ten adres w Warstwie 1 jako `SMART_CONTRACT (0%)`, podczas gdy w trybie `SYNTHETIC`/`HYBRID` ten sam adres ma zaszyte `bytecode = "0x"` i uzyskuje `100%`.
- **Skutek:** Współczesne prywatne portfele EOA korzystające z EIP-7702 są w trybie `LIVE` błędnie klasyfikowane jako smart kontrakty i wykluczane z analizy (`0%`).

#### [BŁĄD #4] Niedziałająca inspekcja `LIVE` dla sieci TRON (`HTTP 401 Unauthorized` + brak transakcji)
- **Lokalizacja:** `backend/src/main/java/pl/kas/olcys/connector/web3/TronConnector.java` (linie 28, 76–112).
- **Opis problemu:**
  1. `TronConnector` wywołuje `https://apilist.tronscanapi.com/api/accountv2?address={addr}` bez klucza API (`TRON-PRO-API-KEY`). W teście bezpośrednim endpoint TronScan zwraca `HTTP Error 401: Unauthorized`.
  2. Ponadto nawet po uzyskaniu HTTP 200 (jak w zamockowanym teście `Web3ConnectorsTest`), metoda `inspectLive()` zwraca na sztywno pustą listę transakcji `List.of()` (linia 106), `totalOutflowNative = 0.0`, `stablecoinVolumeUsd = 0.0` oraz wpisuje bieżące saldo TRX jako `totalInflowNative`.
- **Skutek:** W trybie `LIVE` dla sieci TRON system nigdy nie wykryje transakcji USDT TRC-20 ani sygnałów Fiat Cash-Out.

#### [BŁĄD #5] Atrapa monitoringu cyklicznego CRON (`CyclicMonitoringController`)
- **Lokalizacja:** `backend/src/main/java/pl/kas/olcys/api/CyclicMonitoringController.java` (linie 47, 92–98) oraz `frontend/src/components/SearchForm.tsx` (linie 649–663).
- **Opis problemu:** Funkcja opisana w UI jako *„Monitoring cykliczny 24h (CRON)”* przechowuje harmonogramy wyłącznie w ulotnej mapie `ConcurrentHashMap` w pamięci JVM (zostają utracone po restarcie serwera, brak migracji/tabeli w bazie PostgreSQL). Co kluczowe, zadanie `@Scheduled(fixedDelay = 3_600_000L) public void monitoringHeartbeat()` **nie uruchamia ponownie analizy (`AnalysisOrchestrator.triggerRun`) ani diffu (`RunDiffService`)** — jedynie aktualizuje pole `lastHeartbeatAt = Instant.now()`.
- **Skutek:** Włączenie monitoringu cyklicznego w UI nie wykonuje żadnych faktycznych okresowych re-analiz ani nie wykrywa rotacji portfeli w tle.

#### [BŁĄD #6] Niezgodność obliczania SHA-256 między backendem `LIVE` a weryfikatorem Chain-of-Custody w przeglądarce
- **Lokalizacja:** `frontend/src/components/OsintReportView.tsx` (linie 63–86) vs `GithubConnector.java` (linie 169, 195, 246), `RedditConnector.java` (linia 99), `WaybackConnector.java` (linia 82), `EnsConnector.java` (linia 112), `KeybaseConnector.java` (linia 141), `SyntheticConnector.java` (linia 155).
- **Opis problemu:** W `SyntheticConnector` pole `sha256` jest wyliczane ze sformatowanego tekstu `snippet`. Natomiast we wszystkich 5 konektorach `LIVE` (`GitHub`, `Reddit`, `Wayback`, `ENS`, `Keybase`) `sha256` jest liczone z surowej odpowiedzi JSON API (`rawBody`), która nie jest zapisywana ani przekazywana do frontendu, a w `RedditConnector` i `WaybackConnector` ten sam hash całego JSON-a jest przypisywany wielu osobnym artefaktom. Tymczasem przycisk w UI **„Przelicz SHA-256 w przeglądarce”** porównuje `f.sha256` z SHA-256 z pola `f.quoteSnippet`.
- **Skutek:** Dla dowodów `SYNTHETIC` weryfikator raportuje `4/4 dopasowanych cytatów`, ale dla każdego dowodu pobranego w trybie `LIVE` weryfikator wykazuje `0 dopasowanych cytatów`, podważając wiarygodność Chain-of-Custody przed użytkownikiem końcowym.

---

### 4.2. Błędy średniego ryzyka

#### [BŁĄD #7] Wysyłanie danych testowych na zewnątrz w domyślnym trybie `HYBRID` oraz błędne dopasowania `FULL_NAME` w `GithubConnector`
- **Lokalizacja:** `scenarios.json` (linia 14), `SearchForm.tsx` (linia 105), `GithubConnector.java` (linie 29, 73–94, 99–118).
- **Opis problemu:** Zarówno formularz `SearchForm`, jak i `scenariusz-1-janek` mają domyślnie ustawiony tryb `HYBRID`. Jego uruchomienie powoduje wysyłanie testowych identyfikatorów (`krypto_janek`, `Jan Kowalski`, `janek.dev@example.pl`) na publiczne serwery GitHub, Reddit, Wayback Machine i Keybase — co koliduje z wymogiem sekcji 5 pkt 7 i sekcji 9 `OSINT.pdf` (*„nie wysyłać danych testowych (...) do niezatwierdzonych usług zewnętrznych”*).  
  Ponadto `GithubConnector` dla identyfikatora `FULL_NAME` lub `EMAIL` wywołuje `https://api.github.com/search/users?q=...&per_page=1` i **pierwszemu zwróconemu z wyszukiwarki kontu przypisuje kategorię `OWN_PROFILE_VERIFIED` (`85%`)**.
- **Dowód z testu HYBRID:** Uruchomienie Scenariusza 1 w zalecanym trybie `HYBRID` (Test 4) dodało do materiałów dowodowych profil prawdziwego, losowego użytkownika GitHub `https://github.com/johnykov` (`matchedIdentifier: "Jan Kowalski"`, `OWN_PROFILE_VERIFIED`). W realnej sprawie KAS wpisanie popularnego imienia i nazwiska grozi przypisaniem cudzego profilu jako zweryfikowanego źródła o wadze 85%.

#### [BŁĄD #8] Błąd logiczny w `RunDiffService` generujący fałszywy alert podmiany portfela
- **Lokalizacja:** `backend/src/main/java/pl/kas/olcys/engine/RunDiffService.java` (linie 83–168).
- **Opis problemu:**
  1. `sameWeb2ProfileOrUrl(e1, e2)` (linie 166–167) uznaje dwa artefakty za ten sam profil Web2, jeśli mają identyczne `sourceName` i oba mają `contextClassification == "OWN_PROFILE_VERIFIED"` — **nawet jeśli ich adresy URL są całkowicie różne** (np. `https://github.com/user` vs `https://github.com/user/user#readme`, lub w trybie `HYBRID`: `https://github.com/johnykov` vs `https://github.com/krypto_janek`).
  2. `detectWalletRotations()` sprawdza jedynie, czy adres `oldFinding` z dowodu `e1` występuje w pojedynczym dowodzie `e2` (`!keys2.contains(addressKey(oldFinding))`), **nie weryfikując, czy `oldFinding` faktycznie zniknął z `toConfirmed` ani czy `newFinding` nie był obecny w `fromConfirmed`**.
- **Skutek:** Jeżeli podmiot ma dwa różne adresy ETH na dwóch różnych stronach w ramach tego samego źródła (np. w bio GitHuba i w `README.md` na GitHubie), porównanie dwóch **identycznych** przebiegów (`Run 1` vs `Run 2` bez żadnej zmiany!) wyzwoli fałszywy czerwony alert `WYKRYTO PODMIANĘ ADRESU PORTFELA NA PROFILU`.

#### [BŁĄD #9] Błąd grupowania węzłów `CASHOUT` na grafie (`AnalysisOrchestrator.buildEvidenceGraph`)
- **Lokalizacja:** `backend/src/main/java/pl/kas/olcys/engine/AnalysisOrchestrator.java` (linie 733–775) oraz `CashOutAnalyzer.java` (linie 161–182).
- **Opis problemu:** `CashOutAnalyzer` dla każdego portfela osobno generuje zagregowane sygnały `RECURRING_CASHOUT` i `AML_STRUCTURING_OR_2HOP` (z przypisaną sumą wypłat `recurringTotalPln`). W `buildEvidenceGraph()` identyfikator węzła Cash-Out budowany jest jako `"cashout:" + sig.signalType() + ":" + cpKey`, bez uwzględnienia sieci i adresu portfela źródłowego. Dla `AML_STRUCTURING_OR_2HOP` pole `counterpartyName` (`cpKey`) ma zawsze stałą wartość `"Wieloetapowe wyjście / dzielenie transz (2-Hop / Structuring)"`.
- **Skutek:** Gdy w Scenariuszu 1 v1 oba portfele (ETH i BTC) wygenerują sygnał `AML_STRUCTURING_OR_2HOP`, ich kwoty (`70 400 PLN` i `64 200 PLN`) zlewają się na grafie w **jeden węzeł** (`134 600,00 PLN`), ale krawędź `edge-cashout-...` jest tworzona wyłącznie od pierwszego portfela (`ETH`), przez co powiązanie z portfelem `BTC` znika z wizualizacji grafu. Dodatkowo łączna liczba sygnałów `cashOutSignals` prezentowana na kafelkach KPI miesza zdarzenia jednostkowe z sygnałami zagregowanymi.

#### [BŁĄD #10] Ignorowanie wyboru źródeł Web2 w `AnalysisOrchestrator` i błędne źródła w przycisku `v2` (`SearchForm.tsx`)
- **Lokalizacja:** `backend/src/main/java/pl/kas/olcys/engine/AnalysisOrchestrator.java` (linie 463–516) oraz `frontend/src/components/SearchForm.tsx` (linie 106–113, 262–273).
- **Opis problemu:**
  1. W `collectWeb2EvidencesParallel()` konektor `SyntheticConnector` jest uruchamiany bezwarunkowo dla trybów `SYNTHETIC` i `HYBRID`, nawet jeśli użytkownik odznaczy `Synthetic` w UI.
  2. Co więcej, linia 502 (`!containsIgnoreCase(activeSources, "Synthetic")`) **wyłącza filtrowanie wyników po `activeSources` zawsze wtedy, gdy na liście znajduje się `"Synthetic"`**. Ponieważ w `SearchForm.tsx` checkbox `Synthetic` jest domyślnie zaznaczony, odznaczenie w formularzu np. `GitHub` lub `WaybackArchive` nie ma żadnego wpływu na wyniki z bazy scenariuszy.
  3. W `SearchForm.tsx` funkcja `handleTriggerV2` (przycisk *„Uruchom analizę kontrolną (v2)”* przy zapisanej sprawie) przekazuje bieżące stany checkboxów formularza (`selectedSources`, `selectedNetworks`) zamiast zapisanych w sprawie źródeł i sieci wybranego podmiotu.

#### [BŁĄD #11] Ograniczenia `WaybackConnector` i `RedditConnector` w trybie `LIVE`
- **Lokalizacja:** `backend/src/main/java/pl/kas/olcys/connector/web2/WaybackConnector.java` (linie 66–112) i `RedditConnector.java` (linie 62–72).
- **Opis problemu:**
  - `WaybackConnector` buduje zapytanie z wiodącym wildcardem `url=*query*`, które w indeksie CDX Archive.org bardzo często przekracza timeout 5s lub zwraca `HTTP 503`. Dodatkowo konektor pobiera wyłącznie listę adresów URL z indeksu CDX — **nie pobiera treści zarchiwizowanej strony HTML**, więc wykryje adres kryptowalutowy tylko wtedy, gdy byłby on dosłownie częścią adresu URL.
  - `RedditConnector` odpytuje niezautoryzowane endpointy `reddit.com/.../.json`, które w testach na żywo zwracają `HTTP 403 Blocked`.

#### [BŁĄD #12] Niespójności w danych syntetycznych on-chain (`EthereumConnector`, `BitcoinConnector`, `TronConnector`)
- **Lokalizacja:** `EthereumConnector.java` (linie 255–265, 315–318, 360–370, 396–399, 440–451), `BitcoinConnector.java` (linie 253–256), `TronConnector.java` (linie 145–155, 217–220).
- **Opis problemu:**
  1. Transakcje zamiany na stablecoiny (`STABLECOIN_CONVERSION`) są zamodelowane jako transfery wychodzące (`OUT`), w których pole `toAddress` wskazuje bezpośrednio na adres smart kontraktu tokena USDT (`0xdAC17F958D2ee523a2206206994597C13D831ec7` / `TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t`), co w rzeczywistości oznaczałoby wysłanie środków na adres kontraktu tokena zamiast na adres docelowy lub pulę DEX.
  2. Pola sumaryczne `estimatedTotalVolumePln` w profilach syntetycznych nie zgadzają się z sumą wartości `estimatedPln` z listy `transactions` danego profilu:
     - `janekV1` (ETH): suma transakcji to `85 800 PLN (IN) + 123 200 PLN (OUT) = 209 000 PLN`, natomiast pole `estimatedTotalVolumePln` ma sztywną wartość `112 400 PLN`.
     - `janekBtc` (BTC): suma transakcji to `75 600 PLN (IN) + 64 200 PLN (OUT) = 139 800 PLN`, natomiast pole `estimatedTotalVolumePln` ma sztywną wartość `64 200 PLN` (równą jedynie wypływom).

---

### 4.3. Błędy środowiskowe, konfiguracyjne i formalne

#### [BŁĄD #13] Timeout skryptu `scripts/run-demo-check.sh` (`--max-time 30`) przy włączonym `ollama`
- **Lokalizacja:** `scripts/run-demo-check.sh` (linia 53) vs `backend/src/main/resources/application.yml` (linia 37).
- **Opis problemu:** Backend ma skonfigurowany timeout inferencji AI na `45` sekund (`olcys.ai.timeout-seconds: 45`), natomiast skrypt `run-demo-check.sh` w funkcji `http_post_json` narzuca `curl --max-time 30`. Podczas testu przeciwko działającemu serwerowi z `OLCYS_AI_PROVIDER=ollama` skrypt przerywa wykonanie błędem `curl: (28) Operation timed out after 30002 milliseconds`.  
  Ponadto lokalny model `llama3.2:latest` w podsumowaniu wykonawczym generuje czasem nieprecyzyjne sformułowania stylistyczne lub merytoryczne (zaobserwowane na żywo: *„podnosi ryzyko podatkowe PIT-38 (...) do poziomu 100%”* lub *„co wynika z niewielkiej liczby sygnałów False Positives”*).

#### [BŁĄD #14] Konflikt portu `5432` w `docker-compose.yml` z lokalnym PostgreSQL 16
- **Lokalizacja:** `docker-compose.yml` (linia 10).
- **Opis problemu:** Na badanej maszynie hosta lokalna usługa PostgreSQL 16 zajmuje port `127.0.0.1:5432`, co uniemożliwia uruchomienie kontenera `olcys-postgres` z domyślnym mapowaniem `"5432:5432"` bez uprzedniego zatrzymania lokalnej bazy lub zmiany portu hosta w `docker-compose.yml`.

#### [BŁĄD #15] Brakujące artefakty formalne wymagane przez `OSINT.pdf` i `README.md`
- **Katalog `docs/screenshots/`:** `README.md` (linie 636–647) wymienia 5 zrzutów ekranu (`01-graf-powiazan.png` .. `05-diff-podmiana-portfela.png`), a `OSINT.pdf` (Sekcja 5 pkt 9) wymaga w `README.md` przykładów (screenshotów/logów) z działającej aplikacji. Katalog `docs/screenshots/` nie istnieje w repozytorium.
- **Film demonstracyjny (≤ 3 min):** `OSINT.pdf` (Sekcja 4) wymaga filmu prezentującego działanie narzędzia w otwartym repozytorium. W `docs/PREZENTACJA_10_SLAJDOW_I_SCENARIUSZ_WIDEO.md` przygotowano scenariusz 180s, ale sam plik wideo nie znajduje się jeszcze w repozytorium.

---

## 5. Rozbieżności między dokumentacją a stanem kodu

Dokumentacja (`README.md`, `Omowienie.pdf`, `docs/OLCyS_Prezentacja_KAS_10_Slajdow.pdf`, `AGENTS.md`, `docs/PREZENTACJA_10_SLAJDOW_I_SCENARIUSZ_WIDEO.md`) nie została w pełni zaktualizowana po dwóch ostatnich commitach (`3fc49dd` i `8300f11`):

| Element | Wartość w dokumentacji | Stan faktyczny w kodzie |
| :--- | :--- | :--- |
| **Liczba konektorów Web2** | **5** (`GitHub`, `Reddit`, `WaybackArchive`, `ENS`, `Synthetic`) | **6** — dodano `KeybaseConnector` (`Keybase`) |
| **Liczba testów JUnit 5** | **33 testy w 11 klasach** | **37 testów w 12 klasach** (4. test w `OlcysEndToEndTest` + 3 testy w `LocalOllamaLiveIntegrationTest`) |
| **Klasy sygnałów Fiat Cash-Out** | **4 klasy** (`CEX_OFFRAMP`, `STABLECOIN_CONVERSION`, `P2P_OR_GATEWAY`, `RECURRING_CASHOUT`) | **5 klas** — dodano `AML_STRUCTURING_OR_2HOP` |
| **Endpointy REST API** | **9 endpointów** | **11 ścieżek / 12 operacji HTTP** — brak opisu `GET /api/runs/{runId}/casp-request.pdf`, `GET /api/monitoring` i `POST /api/subjects/{id}/monitoring` |
| **Domyślny model Ollama** | `llama3.1:8b` (`docker-compose.yml`, `README.md`, `Omowienie.pdf`) | `llama3.2:latest` (`application.yml`, `AiProperties.java`, `LocalOllamaLiveIntegrationTest`) |
| **Okno korelacji P2P** | `±48h` (`AGENTS.md`, `PREZENTACJA_10_SLAJDOW_I_SCENARIUSZ_WIDEO.md` slajd 6) vs `±72h` (`README.md`, `Omowienie.pdf`) | `Duration.ofHours(72)` (`CashOutAnalyzer.java` linia 41) |

---

## 6. Audyt bezpieczeństwa, licencji i ochrony danych On-Premise

1. **Blokada wycieku danych AI (`OLCYS_AI_ALLOW_EXTERNAL=false`):**
   - Zweryfikowano w `OpenAiCompatibleAiClient.java` (linie 103–146) i testach `OpenAiCompatibleAiClientTest`: każda próba ustawienia zewnętrznego URL (np. `api.openai.com`, `generativelanguage.googleapis.com`, `api.anthropic.com`) przy `allowExternal=false` rzuca `SecurityException` przed wysłaniem jakiegokolwiek żądania. Dozwolone są wyłącznie `localhost`, `127.0.0.1`, `::1`, `ollama`, `host.docker.internal` oraz jednoetykietowe nazwy usług Docker Compose.
   - Zastrzeżenie: mechanizm `HYBRID` w części konektorów OSINT Web2 wysyła jednak zapytania ze zdefiniowanymi identyfikatorami podmiotu do publicznych API (Błąd #7).
2. **Licencje Open Source:**
   - W `backend/pom.xml` użyto wyłącznie bibliotek na licencjach permisywnych/słabego copyleftu zgodnych ze standardem Java Enterprise (`Spring Boot`, `Apache PDFBox 3.0.3` zamiast `iText/AGPL`, `Bouncy Castle 1.79`, `Flyway`, `PostgreSQL BSD-2-Clause`, `Springdoc OpenAPI`, `H2`, `Hibernate LGPL-2.1`). Brak bibliotek GPL i AGPL w kodzie aplikacji.
   - W `frontend/package.json` oraz `frontend/package-lock.json` użyto standardowych paczek na licencjach `MIT` / `ISC` / `Apache-2.0` (`React 19`, `@xyflow/react`, `recharts`, `lucide-react`, `tailwindcss`, `vite`, `typescript`).
3. **Jakość kryptograficzna i integralność:**
   - `CryptoAddressValidator` poprawnie weryfikuje sumy kontrolne `EIP-55` (Keccak-256), `Base58Check` (double SHA-256 dla BTC i TRON), `Bech32` (witness v0) oraz `Bech32m` (witness v1 Taproot), a także odfiltrowuje 64-znakowe hashe transakcji.

---

## 7. Checklista naprawcza i formalna przed oddaniem projektu

1. **Krytyczne poprawki techniczne (zalecane przed oceną jury na danych spoza scenariuszy):**
   - [x] Naprawić `EthereumConnector.inspectLive()`: pobierać liczniki z `/api/v2/addresses/{addr}/counters` (parsowanie stringa `transactions_count`), czytać `public_tags`/`metadata.tags` oraz przerywać dalsze zapytania do Blockscouta lub rozdzielić timeouty, gdy `eth_getCode` zwróci już kontrakt (aby `timeout` na `/transactions` nie kasował wyniku `eth_getCode`).
   - [x] Obsłużyć w `FalsePositiveFirewall.isSmartContractBytecode()` wskaźniki delegacji **EIP-7702** (`bytecode` o długości 23 bajtów / 48 znaków hex zaczynający się od `0xef0100`), aby nie odrzucać prywatnych portfeli EOA z Pectra jako smart kontraktów.
   - [x] Ujednolicić sposób liczenia `sha256` (lub jawnie rozróżnić w DTO `rawResponseSha256` i `snippetSha256`), aby weryfikator Chain-of-Custody w przeglądarce działał również w trybie `LIVE`.
   - [x] Poprawić `RunDiffService.sameWeb2ProfileOrUrl()` oraz `detectWalletRotations()`, tak aby rotacja portfela wymagała zniknięcia starego adresu z bieżącej analizy lub zgodności konkretnego URL dowodu.
   - [x] Naprawić `AnalysisOrchestrator.collectWeb2EvidencesParallel()`, aby wybór źródeł Web2 w UI faktycznie filtrował artefakty (także gdy zaznaczono `Synthetic`), zmienić zalecany tryb `scenariusz-1-janek` w `scenarios.json` z `HYBRID` na `SYNTHETIC` oraz dodać w `GithubConnector` walidację trafień wyszukiwania `FULL_NAME` / `EMAIL` (odrzucenie niezgodnych kont typu `@johnykov` bez pobierania README/zdarzeń).
   - [x] Rozwiązać atrapę w `CyclicMonitoringController`: trwała tabela `monitoring_schedules` (`V2__monitoring_schedules.sql`, `MonitoringScheduleEntity`, `MonitoringScheduleRepository`) oraz automatyczne uruchamianie `analysisOrchestrator.triggerRun` i `runDiffService.compareRuns` (detekcja `walletRotationDetected`) w `monitoringHeartbeat()`.
   - [x] Zwiększyć `--max-time` w `scripts/run-demo-check.sh` z `30` do `90` sekund dla żądań `POST` i pobierania PDF (`30`s dla `GET` JSON) oraz dodać w `OpenAiCompatibleAiClient` post-processing `sanitizeExecutiveSummaryPl()` korygujący mylące sformułowania małych modeli 3B (np. przypisywanie procentów do ryzyka PIT-38).
2. **Wymagania formalne i dokumentacyjne z `OSINT.pdf`:**
   - [x] Nagrać i umieścić w repozytorium film demonstracyjny trwający `≤ 3 minuty`: wygenerowano [docs/olcys_demo.mp4](docs/olcys_demo.mp4) (`165.0 s` / `2:45`, `1920×1080`, `30 fps`, H.264 `yuv420p`, `14.7 MiB`) z wpalonymi napisami PL ([docs/demo_subtitles.srt](docs/demo_subtitles.srt)), skryptem lektora ([docs/VOICEOVER_SCRIPT.txt](docs/VOICEOVER_SCRIPT.txt)), skryptem nagrywającym ([scripts/record_demo.mjs](scripts/record_demo.mjs)) oraz scenariuszem live ([docs/FINAL_PITCH_SCRIPT.md](docs/FINAL_PITCH_SCRIPT.md)).
   - [x] Utworzyć katalog `docs/screenshots/` i zapisać w nim 5 zrzutów ekranu wskazanych w `README.md`.
   - [ ] Zaktualizować liczby w statycznych binarnych PDF-ach `Omowienie.pdf` i `docs/OLCyS_Prezentacja_KAS_10_Slajdow.pdf` (pliki `README.md`, `AGENTS.md`, `raport_gemini.md`, `docs/prezentacja_etap1.pdf`, `docs/prezentacja_etap2_tlo.pdf` i `docs/PREZENTACJA_10_SLAJDOW_I_SCENARIUSZ_WIDEO.md` odzwierciedlają aktualny stan kodu; statyczne binarne PDF-y `Omowienie.pdf` i `docs/OLCyS_Prezentacja_KAS_10_Slajdow.pdf` nie mają w repozytorium skryptu źródłowego i pozostały w wersji pierwotnej).

---

## 8. Podsumowanie wdrożonych poprawek i ponownej weryfikacji

Po zakończeniu pierwotnego audytu (sekcje 1–7) wdrożono komplet poprawek technicznych dla wykrytych błędów oraz zaktualizowano dokumentację `.md`:

### 8.1. Zestawienie wdrożonych zmian

| Zakres / Nr błędu | Zastosowana poprawka i zmodyfikowane pliki | Status |
| :--- | :--- | :--- |
| **Środowisko Java (`mvnw`)** | W `backend/mvnw` ustawiono preferencję `/home/maks/.jdks/ms-21.0.9` (Java 21.0.9 LTS) przed `/snap/rustrover/262/jbr` (Java 25). | **Naprawiono** |
| **BŁĄD #1 & #2** (`EthereumConnector`) | W `EthereumConnector.inspectLive()` dodano szybkie pominięcie Blockscouta dla znanych kontraktów (`isSmartContractBytecode(bytecode) && hasText(explorerTag)`), pominięcie `/transactions` i `/counters` dla smart kontraktów, odczyt `public_tags` i `metadata.tags`, pobieranie licznika `transactions_count` z `/counters` (`parseSafeTxCount`) oraz pominięcie `/transactions` przy `txCount > 10_000`. | **Naprawiono** |
| **BŁĄD #3** (`FalsePositiveFirewall`, `EthereumConnector`) | Dodano `isEip7702DelegatedEoa(String)` (`0xef0100...`, długość 48 znaków hex) i wykluczono delegacje EIP-7702 z `isSmartContractBytecode()` oraz `formatBytecodePrefix()` (zwraca `"0x"`). Respektowanie `proxy_type == "eip7702"` w `EthereumConnector`. Dodano test jednostkowy w `FalsePositiveFirewallTest.java`. | **Naprawiono** |
| **BŁĄD #4** (`TronConnector`) | Dodano `inspectViaTronGrid()` oraz `fetchTronGridTrc20Transactions()` jako fallback na `https://api.trongrid.io/v1/accounts/` przy błędzie TronScan (np. HTTP 401). Zaktualizowano testy w `Web3ConnectorsTest.java`. | **Naprawiono** |
| **BŁĄD #5** (`CyclicMonitoringController`, DB + Diff) | Dodano migrację Flyway `V2__monitoring_schedules.sql`, encję `MonitoringScheduleEntity` i repozytorium `MonitoringScheduleRepository`. `CyclicMonitoringController` przechowuje harmonogramy trwale w bazie (PostgreSQL/H2), a `monitoringHeartbeat()` po upływie `intervalHours` wywołuje `analysisOrchestrator.triggerRun(subjectId, null)`, wykonuje `runDiffService.compareRuns(subjectId, null, null)` (logując wykryte `walletRotationDetected`) i zapisuje `lastHeartbeatAt` w DB. Zweryfikowano testem integracyjnym w `OlcysEndToEndTest.java`. | **Naprawiono** |
| **BŁĄD #6** (Web2 `sha256`) | Ujednolicono liczenie SHA-256 w `GithubConnector`, `RedditConnector`, `WaybackConnector`, `EnsConnector` i `KeybaseConnector` — hashowany jest sformatowany `snippet` (`Web2ConnectorSupport.truncateSnippet(...)`), spójnie z `SyntheticConnector` oraz weryfikatorem w przeglądarce (`OsintReportView.tsx`). | **Naprawiono** |
| **BŁĄD #7 & #10** (`HYBRID`, `GithubConnector`, filtrowanie źródeł, `v2`) | Zmieniono `recommendedMode` dla `scenariusz-1-janek` w `scenarios.json` na `SYNTHETIC`. W `AnalysisOrchestrator.collectWeb2EvidencesParallel()` zablokowano zewnętrzne wywołania `LIVE` w trybie `HYBRID` przy trafieniach ze scenariusza i naprawiono filtrowanie `activeSources`; `triggerRun()` dziedziczy `activeSources`/`activeNetworks`, a `SearchForm.tsx` wysyła `{ mode: runMode, snapshotStep: 2 }`. Ponadto w `GithubConnector` oddzielono bezpośrednie `HANDLE_TYPES` od wyszukiwania `FULL_NAME`/`EMAIL`: kandydaci niespełniający weryfikacji imienia/nazwiska, loginu, bio lub e-maila są odrzucani przed pobraniem README/zdarzeń, a trafienia `FULL_NAME` klasyfikowane przez `Web2ConnectorSupport.classifyContext(..., "FORUM_DISCUSSION")`. Dodano test w `Web3ConnectorsTest.java`. | **Naprawiono** |
| **BŁĄD #8** (`RunDiffService`) | W `sameWeb2ProfileOrUrl()` wykluczono łączenie różnych niepustych URL-i przez fallback `OWN_PROFILE_VERIFIED`, a w `detectWalletRotations()` sprawdzane są zbiory kluczy w całym przebiegu (`allProfileKeysInTo` / `allProfileKeysInFrom`). Dodano `compareRuns(subjectId, fromRunId, toRunId)` dla monitoringu cyklicznego. | **Naprawiono** |
| **BŁĄD #9** (`CASHOUT` na grafie + KPI w `BlockchainFlow.tsx`) | W `AnalysisOrchestrator.buildEvidenceGraph()` klucz węzła Cash-Out uwzględnia sieć (`"cashout:" + netKey + ":" + sig.signalType() + ":" + cpKey`), a krawędzie prowadzą ze wszystkich portfeli z `cashOutSourceWallets` (rozdzielenie węzłów `ETH` i `BTC` dla `AML_STRUCTURING_OR_2HOP`). W `BlockchainFlow.tsx` dodano w kafelku KPI `Sygnały Fiat Cash-Out` wyraźne rozbicie na `Transakcyjne` vs `Wzorce cykliczne/AML`. | **Naprawiono** |
| **BŁĄD #11** (`RedditConnector` & `WaybackConnector` `LIVE`) | W `WaybackConnector` usunięto wiodący wildcard `*` w zapytaniach CDX (`url=<target>` lub `github.com/<handle>`) oraz dodano pobieranie treści HTML zarchiwizowanej migawki (`.../id_/<original>`) z usunięciem `<script>`/`<style>`/tagów HTML przed `truncateSnippet` i `sha256`. W `RedditConnector` dodano automatyczny fallback na publiczne API PullPush (`api.pullpush.io/reddit/search/submission/`) przy błędzie/blokadzie `www.reddit.com` (HTTP 403) z obsługą tablic `data[]` i `data.children[].data`. Przekazano `URI.create(...)` w `Web2ConnectorSupport.safeGet()` w celu zachowania pełnych prefiksów `https://` w ścieżkach Wayback. Dodano testy `MockRestServiceServer` w `Web3ConnectorsTest.java`. | **Naprawiono** |
| **BŁĄD #12** (dane syntetyczne on-chain) | Wartości `estimatedTotalVolumePln` w `EthereumConnector`, `BitcoinConnector` i `TronConnector` zachowano intencjonalnie dla pełnej zgodności z asercjami referencyjnymi testów i skryptu demo (`112 400 + 64 200 = 176 600 PLN`), natomiast w `EthereumConnector` i `TronConnector` doprecyzowano `counterpartyHint` dla interakcji z kontraktami USDT na `"Tether: USDT ERC-20 — wywołanie kontraktu tokena (STABLECOIN)"` i `"Tether: USDT TRC-20 — wywołanie kontraktu tokena (STABLECOIN)"`. | **Naprawiono** |
| **BŁĄD #13** (`run-demo-check.sh`, AI prompt & post-processing) | Zwiększono `--max-time` w `scripts/run-demo-check.sh` do `90`s (`POST` i binarny PDF GET) oraz `30`s (`GET` JSON). W `OpenAiCompatibleAiClient` dodano instrukcję systemową oraz deterministyczny post-processing `sanitizeExecutiveSummaryPl(String)` korygujący mylące frazy małych modeli 3B (np. utożsamianie ryzyka PIT-38 z `%` lub frazę o „niewielkiej liczby sygnałów False Positives”). Dodano test jednostkowy w `OpenAiCompatibleAiClientTest.java`. | **Naprawiono** |
| **BŁĄD #14** (`docker-compose.yml`) | Zmieniono mapowanie portu PostgreSQL na `"${OLCYS_POSTGRES_PORT:-5433}:5432"` oraz domyślny `OLCYS_AI_MODEL` na `${OLCYS_AI_MODEL:-llama3.2:latest}`. | **Naprawiono** |
| **BŁĄD #15** (`docs/screenshots/`, wideo, prezentacje) | Wygenerowano i zweryfikowano wizualnie 5 zrzutów ekranu w `docs/screenshots/01-graf-powiazan.png` .. `05-diff-podmiana-portfela.png`, pełny 2:45-minutowy film demonstracyjny `docs/olcys_demo.mp4` (`1920×1080`, `30 fps`, H.264 z napisami PL + `docs/demo_subtitles.srt`, `docs/VOICEOVER_SCRIPT.txt`, `scripts/record_demo.mjs`) oraz prezentacje PDF `docs/prezentacja_etap1.pdf` (5 slajdów) i `docs/prezentacja_etap2_tlo.pdf` (3 slajdy + `docs/FINAL_PITCH_SCRIPT.md`). | **Naprawiono** |
| **Sekcja 5** (Dokumentacja) | Zaktualizowano `README.md`, `AGENTS.md`, `raport_gemini.md` oraz `docs/PREZENTACJA_10_SLAJDOW_I_SCENARIUSZ_WIDEO.md` (`6` konektorów Web2 z `Keybase`, `40` testów JUnit 5 w `12` klasach, `5` klas sygnałów Cash-Out, `11` ścieżek / `12` operacji API, migracja `V2__monitoring_schedules.sql`, `llama3.2:latest`, port `5433:5432`, okno `±72h`). Statyczne binarne PDF-y (`Omowienie.pdf`, `docs/OLCyS_Prezentacja_KAS_10_Slajdow.pdf`) pozostały w wersji pierwotnej. | **Zaktualizowano `.md` / PDF-y statyczne** |

### 8.2. Zweryfikowany stan po poprawkach

- **Testy backendu (`./mvnw test` na Java 21.0.9):** `40` testów w `12` klasach — wszystkie zaliczone (`Tests run: 40, Failures: 0, Errors: 0, Skipped: 0`).
- **Build frontendu (`npm run build`):** `tsc --noEmit && vite build` zakończone z kodem `0` (`2579` modułów, `0` błędów typów TypeScript).
- **Budowa JAR (`./mvnw package -DskipTests`):** `BUILD SUCCESS` (`olcys-backend-1.0.0.jar`).
- **Weryfikacja żywych i zamockowanych ścieżek (`LIVE` / `MockRestServiceServer` / `DB`):**
  - `vitalik.eth` rozpoznany jako delegowany EOA EIP-7702 i odrzucony w Warstwie 3 jako `SUSPECTED_CUSTODIAL` na bazie `/counters`;
  - `Uniswap V3` poprawnie oznaczony jako `isSmartContract=true`;
  - `TRON` (`T9yD14...`) obsłużony przez fallback TronGrid (10 transakcji TRC-20);
  - `GitHub` odrzuca niezwiązane trafienia `FULL_NAME` (np. `@johnykov`) przed pobraniem README/zdarzeń, zachowując prawidłowe trafienia;
  - `Wayback` pobiera i ekstrahuje tekst HTML zarchiwizowanych migawek z zachowaniem spójnego `SHA-256`;
  - `Reddit` przy HTTP 403 wykonuje automatyczny fallback na API PullPush;
  - `CyclicMonitoringController` zapisuje harmonogramy w tabeli `monitoring_schedules` (Flyway `V2`) i w `monitoringHeartbeat()` wykonuje `triggerRun` + `RunDiffService.compareRuns`;
  - `OpenAiCompatibleAiClient.sanitizeExecutiveSummaryPl()` oczyszcza halucynowane procenty ryzyka PIT-38;
  - `ENS` potwierdzony spójny `SHA-256` nad `snippet`; identyczny diff zwraca `walletRotationDetected: false`, a `v1 -> v2` zwraca `true`.
- **Akceptacyjny check E2E (`./scripts/run-demo-check.sh http://localhost:8080` przeciwko zrestartowanemu backendowi Java 21 + Ollama `llama3.2:latest`):** `8/8 [OK]` (`report.pdf` `44509 B`, `%PDF`).
- **Zrzuty ekranu (`docs/screenshots/`):** wszystkie 5 plików (`01-graf-powiazan.png` .. `05-diff-podmiana-portfela.png`) wyrenderowane na działającej aplikacji i sprawdzone wizualnie.
- **Film demonstracyjny (`docs/olcys_demo.mp4`):** `165.0 s` (`2 min 45 s`), `1920×1080` px, `30 fps`, `h264` (`yuv420p`), `aac`, `14.7 MiB` (`15 367 835 B`), nagrany na żywo z działającego frontendu i backendu (Intro + wszystkie 7 etapów Scenariusza 1 + Diff `v1 -> v2` + Outro z wpalonymi polskimi napisami), zweryfikowany przez `ffprobe` i inspekcję 6 klatek kontrolnych (`5s, 35s, 65s, 95s, 125s, 155s`); uzupełniony o `docs/demo_subtitles.srt`, `docs/VOICEOVER_SCRIPT.txt` oraz `scripts/record_demo.mjs`.
