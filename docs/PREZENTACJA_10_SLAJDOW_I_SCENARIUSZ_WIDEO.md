# OŁCyŚ — Konspekt Prezentacji (10 slajdów PDF) i Scenariusz Filmu Demo (180 s)

> **Wyzwanie:** Krajowa Administracja Skarbowa (KAS) — *„Osintowy Łapacz Cyfrowych Śladów — OŁCyŚ”* (HackNation 2026)  
> **Limity regulaminowe:** Prezentacja PDF **≤ 10 slajdów** | Film w repozytorium **≤ 3 minuty (180 s)**

---

## 1. Mapa pokrycia kryteriów oceny KAS (100%)

| Kryterium oceny KAS (`OSINT.pdf`, sekcja 8) | Waga | Jak OŁCyŚ maksymalizuje punktację | Gdzie eksponujemy w pitchu i wideo |
| :--- | :---: | :--- | :--- |
| **Zgodność z wyzwaniem i poprawność realizacji scenariusza** | **25%** | Pełna ścieżka PIT-38 (Web2 OSINT → krypto-walidacja → Web3 → Cash-Out → raport), 6 typów identyfikatorów, 3 tryby (`HYBRID`, `LIVE`, `SYNTHETIC`), 3 wbudowane scenariusze KAS. | Slajdy 2, 3, 7; Wideo `00:25–01:05` i `02:05–02:20` |
| **Jakość Wskaźnika Pewności i ograniczania błędnych przypisań** | **25%** | 100% deterministyczny Confidence Score (`85/70/50/20/0%` + synergia `+15 pp` na niezależnych domenach) + 3-warstwowy Firewall FP (`eth_getCode != 0x`, 83 tagi w `entity-tags.json`, `txCount > 10 000`) z flagą `interactionRecorded = true`. | Slajdy 4, 5; Wideo `01:05–01:35` |
| **Przejrzystość raportu, źródeł i uzasadnienia wyników** | **20%** | Graf warstwowy `@xyflow/react` z inspektorem Chain-of-Custody (URL, UTC, `SHA-256`) oraz 3-strefowy raport (UI + Apache PDFBox 3.0.3 PL + JSON) czytelny także dla osób nietechnicznych. | Slajdy 6, 7; Wideo `00:50–01:05` i `01:35–02:05` |
| **Pomysł i oryginalność rozwiązania** | **15%** | Cykliczne śledzenie zmian w czasie (**wykrywanie podmiany portfela v1 → v2** z czerwonym alertem), **Reverse OSINT** (start od adresu ETH do Telegram/Reddit/TRON), detekcja cyklicznych wypłat OTC. | Slajd 8; Wideo `02:05–02:35` |
| **Potencjał wdrożeniowy i architektura techniczna** | **15%** | Monorepo **Java 21 (Virtual Threads) + Spring Boot 3.4 + PostgreSQL 16 + Flyway + React 19**, pluginowe `Web2Connector`/`Web3Connector`, wymienny lokalny `AiClient` (`OLCYS_AI_ALLOW_EXTERNAL=false`, Ollama/`local-rules`), **0 bibliotek GPL/AGPL**, Swagger UI, `docker compose up`. | Slajdy 9, 10; Wideo `00:00–00:25` i `02:35–03:00` |

---

## 2. Konspekt prezentacji PDF (dokładnie 10 slajdów)

### Slajd 1 — Tytuł i pozycjonowanie rozwiązania
- **Tytuł:** **OŁCyŚ — Osintowy Łapacz Cyfrowych Śladów**
- **Podtytuł:** Zautomatyzowana analityka OSINT (Web2) i Blockchain (Web3) wspierająca weryfikację podatku **PIT-38** w Krajowej Administracji Skarbowej
- **Elementy wizualne:** Logo/tarcza OŁCyŚ (`#0F2942`), 4 plakietki wartości: *Pasywny OSINT*, *3-warstwowy Firewall FP*, *3-strefowy Raport PDF/JSON*, *100% On-Premise (Zero wycieku danych)*.

### Slajd 2 — Problem PIT-38 i wyzwanie analityka KAS *(Waga: 25%)*
- **Luka podatkowa:** Przychody z odpłatnego zbycia kryptowalut (PLN/EUR/USD, P2P/BLIK, kantory OTC) często nie trafiają do **PIT-38** lub są szacowane „na oko”.
- **Wąskie gardło dziś:** Ręczne łączenie profili Web2 z eksploratorami Web3 jest powolne, niepowtarzalne dowodowo i grozi błędnym przypisaniem adresów giełd lub smart kontraktów.
- **Nasza odpowiedź:** Jeden spójny pipeline: `Identyfikator → Web2 OSINT (SHA-256) → Walidacja Krypto → Web3 & Firewall FP → Confidence Score → Cash-Out → Raport KAS`.

### Slajd 3 — Założenia OSINT, parametry wyszukiwania i walidacja kryptograficzna *(Waga: 25%)*
- **Legalność:** Wyłącznie pasywne, publiczne źródła (GitHub, Reddit, Wayback Machine, ENS + RPC/Blockscout/Mempool/TronScan) — zero obchodzenia zabezpieczeń czy wycieków.
- **6 typów wejścia:** `FULL_NAME`, `NICK`, `EMAIL`, `TELEGRAM_ID`, `SOCIAL_LOGIN` oraz **`CRYPTO_ADDRESS` (Reverse OSINT)**.
- **3 tryby:** `HYBRID`, `LIVE`, `SYNTHETIC` (`<1 s` offline).
- **Walidacja sum kontrolnych:** ETH `EIP-55` (Keccak-256, odfiltrowanie 64-znakowych hashy TX), BTC `Base58Check` / `Bech32` / `Bech32m`, TRON `Base58Check`.

### Slajd 4 — Deterministyczny algorytm Confidence Score (0–100%) *(Waga: 25%)*
- **Zasada kluczowa:** AI wspiera ekstrakcję/podsumowanie, ale **punktacja jest w 100% deterministyczna, powtarzalna i wyjaśnialna** (`scoreBreakdown` w każdej karcie).
- **Tabela progów bazowych KAS:**
  - `85%` — Oficjalny profil autorski (GitHub, ENS, strona WWW) `[80–100%]`
  - `70%` — Kontekst transakcyjny (ogłoszenie P2P/OTC, stopka e-maila) `[60–79%]`
  - `50%` — Dyskusja na forum branżowym `[40–59%]`
  - `20%` — Szum / komentarze giveaway / airdrop `[10–39%]`
  - `0%` — Wykluczenie False Positive (twardy sufit, bez premii)
- **Reguła Synergii (`+15 pp`, max `100%`):** Grupowanie po niezależnej domenie/platformie. Przykład: `GitHub (85%) + KantorP2P.pl (+15 pp) = 100%`.

### Slajd 5 — 3-warstwowy Firewall False Positives *(Waga: 25%)*
- **Problem:** W sieci użytkownicy cytują adresy giełd, routerów DEX, mikserów i zbiórek. Przypisanie ich osobie fizycznej niszczy wiarygodność analizy.
- **3 warstwy ochrony w OŁCyŚ:**
  1. **`eth_getCode` (`bytecode != 0x`):** Automatyczne wykrycie i odrzucenie Smart Kontraktów (`Uniswap V3 SwapRouter` → `0%`).
  2. **Rejestr `entity-tags.json` (83 zweryfikowane adresy ETH/BTC/TRON):** `CEX`, `CASP_P2P`, `DEX_ROUTER`, `STABLECOIN`, `MIXER` (Tornado Cash), `CHARITY` (Aid For Ukraine), `BURN` → `0%`.
  3. **Heurystyka wolumenowa (`txCount > 10 000`):** Flaga ostrzegawcza `SUSPECTED_CUSTODIAL`.
- **Ważna reguła KAS:** Dla odrzuconych CEX/CASP zachowujemy **`interactionRecorded = true`** — giełda nie należy do podatnika, ale przelew na nią jest śladem Cash-Out!

### Slajd 6 — Analiza On-Chain, detekcja Fiat Cash-Out i Graf Dowodowy *(Waga: 20% + 25%)*
- **4 detektory wyjścia do walut tradycyjnych:**
  - `CEX_OFFRAMP` (przelew na Binance, Kraken, Zondacrypto)
  - `STABLECOIN_CONVERSION` (swapy i transfery USDT / USDC / DAI)
  - `P2P_OR_GATEWAY` (korelacja daty ogłoszenia P2P z transakcją ±48h + bramki Ramp/Bitomat/Kantor OTC)
  - `RECURRING_CASHOUT` (cykliczne wypłaty zbliżonych transz, np. ~5 000 USDT tygodniowo)
- **Wizualizacja dla analityka:** Interaktywny graf `@xyflow/react` (4 kolumny od podmiotu do Cash-Out) + wykres przepływów PLN w `Recharts`.

### Slajd 7 — 3-strefowy raport OSINT (UI + PDFBox + JSON) *(Waga: 20%)*
- **Czytelność także dla osób nietechnicznych:** Rygorystyczne rozdzielenie faktów od hipotez w UI, eksporcie JSON i raporcie PDF (**Apache PDFBox 3.0.3** z osadzonym `DejaVuSans.ttf` dla pełnych polskich znaków):
  - **Strefa 1: Fakty Źródłowe (Chain-of-Custody):** Pełny URL, timestamp UTC, hash `SHA-256` treści, dokładny cytat.
  - **Strefa 2: Wyniki Obliczeń:** Confidence Score z rozpisem reguł, wykluczone FP (`0%`), wolumeny PLN/USD i oś czasu transakcji.
  - **Strefa 3: Wnioski do Dalszej Weryfikacji:** Sygnały Fiat Cash-Out, wskaźniki ryzyka **PIT-38 (art. 30b)**, rekomendacje czynności sprawdzających KAS i zastrzeżenia prawne.

### Slajd 8 — Wyróżniki ponad minimum: Podmiana portfela (Diff v1→v2) i Reverse OSINT *(Waga: 15%)*
- **Monitoring zmian w czasie (`GET /api/subjects/{id}/diff`):** Porównanie cyklicznych analiz zapisanych w PostgreSQL. Wykrywa podmianę portfela na tym samym profilu Web2 (`GitHub`: `0x71C7...76F` → `0xd8dA...045`), wylicza przyrost wolumenu (`+57 000 PLN`) i nowe sygnały Cash-Out.
- **Reverse OSINT (Scenariusz 2):** Start od samego adresu ETH ujawnia powiązania na Telegram OTC i Reddit, odkrywa drugi portfel w sieci **TRON (USDT TRC-20)** i wykrywa wszystkie 4 klasy Cash-Out.
- **Test negatywny (Scenariusz 3):** Odporność na masowe komentarze giveaway (`20%`) i zbiórki charytatywne/miksery (`0%`).

### Slajd 9 — Architektura wdrożeniowa On-Premise i bezpieczeństwo danych *(Waga: 15%)*
- **Stack korporacyjny preferowany przez KAS:** Monorepo **Java 21 LTS (Virtual Threads) + Spring Boot 3.4 + PostgreSQL 16 + Flyway + React 19 + TypeScript**.
- **Architektura pluginowa:** Nowe źródło Web2 = implementacja `Web2Connector`; nowa sieć blockchain = implementacja `Web3Connector`. Równoległe zapytania na Virtual Threads z twardym timeoutem `5 s`.
- **Zero wycieku danych & wymienny AI (`AiClient`):** Domyślny `local-rules` lub lokalna **Ollama** (`http://ollama:11434/v1`), twarda blokada zewnętrznych API (`OLCYS_AI_ALLOW_EXTERNAL=false`, status `externalDataTransfer: false`).
- **Audyt Open Source:** **0 bibliotek GPL/AGPL** (Apache 2.0, MIT, BSD, ISC, EPL, PostgreSQL; brak iText/AGPL).

### Slajd 10 — Potencjał wdrożeniowy w KAS, walidacja i podsumowanie *(Waga: 15% + 25%)*
- **Gotowość uruchomieniowa:** Jedna komenda `docker compose up --build` (lub profil `ai` z Ollamą), pełna dokumentacja OpenAPI `/swagger-ui.html`, automatyczny skrypt walidacji E2E `./scripts/run-demo-check.sh`.
- **Plan wdrożenia w KAS:** Integracja z wewnętrznymi słownikami VASP/CASP (DAC8), dodanie konektorów do lokalnych rejestrów i uruchomienie w izolowanej chmurze prywatnej KAS.
- **Podsumowanie:** OŁCyŚ skraca czas wstępnej analizy krypto-OSINT z godzin do sekund, dając analitykowi KAS powtarzalny, audytowalny materiał dowodowy pod PIT-38.

---

## 3. Scenariusz 3-minutowego filmu demo (180 sekund)

> **Wskazówki realizacyjne przed nagraniem:**  
> - Rozdzielczość ekranu `1920×1080`, powiększenie przeglądarki `110%` dla pełnej czytelności tabel i adresów.  
> - Przed startem nagrania uruchom `docker compose up --build` (czysta baza lub przygotowana do prezentacji v1 → v2) i miej otwarte 2 karty: aplikację `http://localhost:5173` oraz Swagger UI `http://localhost:8080/swagger-ui.html` + terminal ze skryptem `./scripts/run-demo-check.sh`.

| Przedział czasu | Czas trwania | Akcja na ekranie (UI / Terminal / API) | Narracja lektora (do przeczytania wyraźnym tempem) | Pokryte kryterium KAS |
| :---: | :---: | :--- | :--- | :--- |
| **00:00 – 00:18** | 18 s | Ekran startowy OŁCyŚ (`http://localhost:5173`). Najazd kursorem na zielony pasek w nagłówku: `AI: Lokalny On-Premise \| Zero transferu zewnętrznego` oraz metryki `Java 21 \| Virtual Threads: ON \| DB: UP \| Konektory: 5 Web2 / 3 Web3`. | *„Oto OŁCyŚ — Osintowy Łapacz Cyfrowych Śladów dla Krajowej Administracji Skarbowej. System rozwiązuje problem nieujawnionych przychodów z kryptowalut w zeznaniach PIT-38. Całość działa w architekturze on-premise na Java 21, Spring Boot i PostgreSQL — bez wysyłania jakichkolwiek danych poza środowisko KAS.”* | Architektura (15%) + Zgodność (25%) |
| **00:18 – 00:40** | 22 s | Lewy panel `SearchForm.tsx`: pokazanie listy rozwijanej typów identyfikatorów (`NICK`, `FULL_NAME`, `EMAIL`, `TELEGRAM_ID`, `SOCIAL_LOGIN`, `CRYPTO_ADDRESS`), trybów (`Hybrydowy`, `Live OSINT`, `Scenariusz Walidacyjny`), źródeł Web2 i sieci `ETH/BTC/TRON`. Kliknięcie przycisku **„Uruchom scenariusz”** na karcie **Scenariusza 1 (`Jan Kowalski / @krypto_janek`)**. | *„Analityk może rozpocząć badanie od nicka, imienia i nazwiska, e-maila, komunikatora albo bezpośrednio od adresu krypto w trybie Reverse OSINT. System odpytuje wyłącznie legalne, pasywne źródła publiczne. Uruchamiamy Scenariusz 1 dla podmiotu @krypto_janek — Java 21 Virtual Threads w ułamku sekundy zbierają ślady równolegle.”* | Parametry i OSINT (25%) |
| **00:40 – 01:05** | 25 s | Zakładka **„Graf powiązań” (`EvidenceGraph.tsx`)**. Pokazanie 4 warstw od lewej do prawej. Kliknięcie węzła `GitHub` lub `0x71C7...76F`, aby otworzyć prawy **Inspektor Dowodowy KAS**; wskazanie URL, timestampu UTC, skrótu `SHA-256` i cytatu. | *„W zakładce Graf powiązań widzimy pełną ścieżkę od identyfikatorów przez źródła Web2 po portfele i węzły Cash-Out. Kliknięcie dowolnego węzła otwiera Inspektor Dowodowy: każdy ślad ma zabezpieczony dokładny adres URL, datę UTC i kryptograficzny skrót SHA-256 treści dla zachowania łańcucha dowodowego Chain-of-Custody.”* | Przejrzystość i źródła (20%) |
| **01:05 – 01:35** | 30 s | Przełączenie na zakładkę **„Portfele & Firewall FP” (`AddressCards.tsx`)**. Wskazanie paska **`100%`** dla `0x71C7...76F` i tabeli rozbicia (`+85 pkt GitHub`, `+15 pkt Synergia z KantorP2P.pl`) oraz portfela BTC (`70%`). Przewinięcie w dół do sekcji **„Odrzucone trafienia — Firewall False Positives”**: pokazanie `Binance 14` (`0%` + badge **`Odnotowano interakcję on-chain`**) oraz `Uniswap V3: SwapRouter` (`0%` + `eth_getCode bytecode != 0x`). | *„Przejdźmy do deterministycznego Wskaźnika Pewności i Firewalla False Positives. Portfel ETH uzyskał 100%, ponieważ pojawił się zarówno na autorskim profilu GitHub — 85% — jak i w ogłoszeniu P2P na drugiej niezależnej domenie — premia synergii +15 punktów. Poniżej Firewall automatycznie odrzucił z wynikiem 0% adres giełdy Binance oraz smart kontrakt Uniswap V3 wykryty przez eth_getCode — przy czym dla giełdy zachował flagę odnotowanej interakcji on-chain.”* | Confidence Score i Firewall FP (25%) |
| **01:35 – 01:55** | 20 s | Przełączenie na zakładkę **„Przepływy On-Chain & Cash-Out” (`BlockchainFlow.tsx`)**. Pokazanie 4 kart KPI (PLN, Stablecoin USD, transakcje, 3 sygnały Cash-Out), wykresu Recharts oraz tabeli z tagami `CEX Off-Ramp`, `Konwersja Stablecoin`, `Korelacja P2P`. | *„W zakładce Przepływy widzimy wycenę obrotów w PLN, swapy na stablecoiny USDT oraz sygnały Fiat Cash-Out — w tym bezpośrednią korelację czasową ogłoszenia sprzedaży P2P z przelewem do warszawskiego kantoru OTC i na giełdy Binance oraz Kraken.”* | Zgodność i Cash-Out (25%) |
| **01:55 – 02:15** | 20 s | Przełączenie na zakładkę **„Raport OSINT (3 strefy)” (`OsintReportView.tsx`)**. Szybkie przewinięcie przez **Strefę 1 (Fakty Źródłowe)**, **Strefę 2 (Wyniki Obliczeń)** i **Strefę 3 (Wnioski i wskaźniki PIT-38)**. Kliknięcie **„Pobierz Raport PDF (KAS)”** i pokazanie przez 3 sekundy otwartego PDF-a z polskimi znakami. | *„Raport końcowy rozdziela bezwzględnie Strefę 1 Faktów Źródłowych, Strefę 2 deterministycznych Obliczeń oraz Strefę 3 Wniosków PIT-38 wymagających weryfikacji organu. Jednym kliknięciem generujemy gotowy dokument PDF przez Apache PDFBox z pełnym wsparciem polskich znaków oraz eksport JSON.”* | Przejrzystość raportu (20%) |
| **02:15 – 02:40** | 25 s | Kliknięcie złotego przycisku **„Uruchom analizę kontrolną (v2 — wykryj podmianę portfela)”**. Automatyczne przejście do zakładki **„Porównanie w czasie (Diff)” (`HistoryDiff.tsx`)**. Pokazanie czerwonego alertu **`WYKRYTO PODMIANĘ ADRESU PORTFELA NA PROFILU`** (`0x71C7...76F` → `0xd8dA...045`), delty wolumenu i nowych sygnałów. Krótkie wspomnienie Scenariusza 2 i 3. | *„Narzędzie wspiera też cykliczny monitoring. Po uruchomieniu analizy kontrolnej v2 system porównuje migawki w PostgreSQL i natychmiast podnosi alert wykrycia podmiany portfela na profilu GitHub podatnika wraz z przyrostem wolumenu. W repozytorium dostępne są też scenariusze Reverse-OSINT z siecią TRON oraz test odporności na szum airdropowy.”* | Oryginalność i kryterium opcjonalne (15%) |
| **02:40 – 03:00** | 20 s | Krótki rzut na zakładkę Swagger UI (`http://localhost:8080/swagger-ui.html`) i uruchomienie w terminalu `./scripts/run-demo-check.sh` pokazujące wszystkie 8 kroków `[OK]`. | *„Całość wdrażana jest jedną komendą docker compose up, posiada pełną dokumentację OpenAPI oraz zero bibliotek GPL czy AGPL. Automatyczny test end-to-end potwierdza działanie całego łańcucha analitycznego. OŁCyŚ jest gotowy do pilotażu w środowisku KAS. Dziękujemy!”* | Architektura i wdrożenie (15%) |

---

## 4. Checklista przed eksportem PDF i nagraniem wideo

- [ ] **Liczba slajdów PDF:** dokładnie **10 slajdów** (eksport do PDF, czytelny font min. 14 pt, kolorystyka KAS `#0F2942` / `#15803D` / `#B45309` / `#B91C1C`).
- [ ] **Czas trwania filmu:** **≤ 180 sekund** (zalecane `02:50–02:58`, aby zmieścić się bezpiecznie w limicie 3 minut).
- [ ] **Brak danych wrażliwych:** na nagraniu używać wyłącznie wbudowanych danych syntetycznych/zanonimizowanych (`krypto_janek`, `@marek_otc_kantor`, `@lucky_drop_2026`).
- [ ] **Wszystkie etapy z sekcji 6 `OSINT.pdf` pokazane na żywo:** wejście → OSINT → walidacja krypto → Confidence Score → odrzucenie FP → wskaźniki blockchain/Cash-Out → 3-strefowy raport + źródła (+ bonus: Diff v1→v2).
- [ ] **Weryfikacja repozytorium przed oddaniem:** uruchomić `./scripts/run-demo-check.sh`, `cd backend && ./mvnw test` oraz `cd frontend && npm run build`.
