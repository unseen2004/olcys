# AGENTS.md — Projekt: OŁCyś (Java 21 + Spring Boot 3.4 + React — HackNation 2026 / KAS)

## 1. Cel Projektu
Narzędzie OSINT + analiza blockchain dla analityków Krajowej Administracji Skarbowej (KAS).
Na podstawie >=1 identyfikatora wejściowego (imię i nazwisko, nick, ID komunikatora, login social, e-mail lub publiczny adres krypto) system:
1. Przeszukuje wyłącznie publiczne, legalne źródła Web2 (pasywny biały wywiad OSINT).
2. Ekstrahuje i kryptograficznie waliduje adresy kryptowalutowe (BTC Base58Check/Bech32, ETH EIP-55, TRON Base58Check).
3. Odrzuca błędne przypisania (CEX, portfele powiernicze, smart kontrakty przez `eth_getCode` -> `bytecode != 0x`, miksery, zbiórki).
4. Wylicza deterministyczny, wyjaśnialny Wskaźnik Pewności (Confidence Score 0-100%) zgodnie z metodyką KAS (w tym premię +15 pp za >=2 niezależne źródła, max 100%).
5. Analizuje aktywność on-chain (wolumen, wymiany, sygnały Fiat Cash-Out: CEX/CASP, P2P, bramki/karty krypto-fiat, stablecoiny).
6. Zapisuje cykliczne analizy w PostgreSQL, porównuje zmiany w czasie (wykrywanie podmiany portfeli na profilach) i generuje raport OSINT (UI + PDF + JSON) z wyraźnym podziałem na 3 strefy:
   - FAKTY ŹRÓDŁOWE (URL, timestamp UTC, hash SHA-256, cytat)
   - WYNIKI OBLICZEŃ (Confidence Score breakdown, wolumeny, metryki on-chain, odrzucone FP)
   - WNIOSKI WYMAGAJĄCE DALSZEJ WERYFIKACJI (sygnały Fiat Cash-Out, ryzyka PIT-38, ograniczenia)

## 2. Twarde Zasady Techniczne i Bezpieczeństwa
- **MONOREPO:** Jedno repozytorium: `backend/` (Maven, Java 21, Spring Boot 3.4) + `frontend/` (React + Vite + TS) + `docker-compose.yml`.
- **LICENCJE OPEN SOURCE:** Wyłącznie Apache 2.0, MIT, BSD, ISC, EPL 2.0, PostgreSQL. ZAKAZ iText/GPL/AGPL! Do PDF używaj Apache PDFBox 3.x.
- **ZERO WYCIEKU DANYCH (KRYTYCZNE):** ZAKAZ wysyłania danych testowych, zapytań o podmioty lub wyników analiz do zewnętrznych komercyjnych API AI (OpenAI, Gemini, Anthropic). Moduł AI używa uniwersalnego klienta OpenAI-compatible skierowanego wyłącznie na lokalną Ollamę (`OLCYS_AI_BASE_URL=http://ollama:11434/v1`) z bezpiecznym fallbackiem `local-rules` i blokadą hostów spoza localhost/sieci dockerowej (`OLCYS_AI_ALLOW_EXTERNAL=false`).
- **ARCHITEKTURA PLUGINOWA:** Nowe źródła Web2 implementują interfejs `Web2Connector`, nowe sieci blockchain implementują `Web3Connector` (Spring automatycznie wstrzykuje `List<Web2Connector>` i `List<Web3Connector>`).
- **WSPÓŁBIEŻNOŚĆ:** Włącz Java 21 Virtual Threads (`spring.threads.virtual.enabled=true`) i wykonuj zapytania konektorów równolegle przez `Executors.newVirtualThreadPerTaskExecutor()` z twardym timeoutem 5s na konektor.
- **JĘZYK:** Kod i nazwy klas po angielsku; cała warstwa UI, uzasadnienia reguł i raport PDF w czystym, zrozumiałym języku polskim dla analityka KAS.

## 3. Struktura Monorepo
```text
/
├── AGENTS.md
├── docker-compose.yml                  # postgres, backend, frontend (+ profil ollama)
├── README.md                           # Pełna dokumentacja KAS + tabela licencji + przykłady/logi
├── backend/
│   ├── pom.xml                         # Spring Boot 3.4, Java 21, JPA, Flyway, PostgreSQL, PDFBox, BouncyCastle, Springdoc
│   ├── Dockerfile
│   └── src/
│       ├── main/
│       │   ├── resources/
│       │   │   ├── application.yml
│       │   │   ├── db/migration/V1__init.sql
│       │   │   ├── data/entity-tags.json        # Baza znanych adresów CEX/DEX/CASP/Mixer/Charity
│       │   │   ├── data/scenarios.json          # 3 zanonimizowane/syntetyczne scenariusze walidacyjne KAS
│       │   │   └── fonts/DejaVuSans.ttf         # Font z polskimi znakami do PDFBox
│       │   └── java/pl/kas/olcys/
│       │       ├── OlcysApplication.java
│       │       ├── config/                      # RestClient, CORS, OpenAPI, VirtualThreads
│       │       ├── api/                         # Kontrolery REST + DTO (Java records!)
│       │       │   ├── dto/                     # Request/Response records (ZAMROŻONY KONTRAKT)
│       │       │   ├── SubjectController.java
│       │       │   ├── AnalysisController.java
│       │       │   └── HealthController.java
│       │       ├── domain/                      # Encje JPA + Repo (Subject, AnalysisRun, EvidenceArtifact, AddressFinding)
│       │       ├── crypto/
│       │       │   └── CryptoAddressValidator.java  # BTC (Base58Check/Bech32), ETH (EIP-55), TRON + ekstraktor
│       │       ├── connector/
│       │       │   ├── web2/                    # Web2Connector + GithubConnector, RedditConnector, WaybackConnector, EnsConnector, SyntheticConnector
│       │       │   └── web3/                    # Web3Connector + EthereumConnector, BitcoinConnector, EntityTagRegistry
│       │       ├── engine/
│       │       │   ├── FalsePositiveFirewall.java   # bytecode != 0x, tagi CEX/Mixer/Charity, heurystyka
│       │       │   ├── ConfidenceScorer.java        # Deterministyczne progi KAS + premia synergii +15pp
│       │       │   ├── CashOutAnalyzer.java         # CEX Off-Ramp, Stablecoin Swap, P2P/Bramki, Cykliczność
│       │       │   ├── RunDiffService.java          # Porównywanie analiz w czasie (podmiana portfela!)
│       │       │   ├── AnalysisOrchestrator.java    # Równoległy pipeline (Virtual Threads)
│       │       │   └── ai/                          # AiClient interface + OpenAiCompatibleAiClient (Ollama) + LocalRulesAiClient
│       │       └── report/
│       │           ├── ReportBuilder.java           # Buduje 3-strefowy raport OSINT
│       │           └── PdfReportGenerator.java      # Apache PDFBox 3.0 z polskimi znakami
│       └── test/java/pl/kas/olcys/                  # JUnit 5: walidatory, firewall, scoring, cashout, diff, e2e
└── frontend/
    ├── package.json                    # React 19, Vite, TS, Tailwind, @xyflow/react, recharts, lucide-react
    ├── Dockerfile
    └── src/
        ├── types/api.ts                # 1:1 mirror Java DTO records
        ├── components/
        │   ├── SearchForm.tsx
        │   ├── EvidenceGraph.tsx
        │   ├── AddressCards.tsx
        │   ├── BlockchainFlow.tsx
        │   ├── HistoryDiff.tsx
        │   └── OsintReportView.tsx
        └── App.tsx
Przestawienie backendu na **Java 21 + Spring Boot 3.4** to ruch idealny pod wymagania KAS — **Java** jest wprost wskazana w czołówce preferowanych technologii backendowych (*„Najlepiej .NET, JAVA, NodeJS”*), a w instytucjach publicznych (w tym w środowisku on-premise KAS) standard Spring Boot + PostgreSQL + OpenAPI ma najwyższy potencjał wdrożeniowy.

Poniżej zaktualizowany stack, kompletny `AGENTS.md` oraz gotowe prompty dla równoległych agentów AI pod **monorepo Java + React**.

---

## 1. Zaktualizowany Stack Technologiczny (Java Enterprise + On-Premise KAS)

Wszystkie wybrane komponenty mają wyłącznie permisywne licencje Open Source (**Apache 2.0 / MIT / BSD / PostgreSQL**) — zero GPL/AGPL (w szczególności świadomie **omijamy iText/AGPL** na rzecz **Apache PDFBox**), co gwarantuje pełną zgodność z wymogami licencyjnymi.

| Warstwa | Technologia (Licencja) | Rola i zalety w zadaniu KAS |
| --- | --- | --- |
| **Backend Core** | **Java 21 LTS + Spring Boot 3.4 + Maven** (Apache 2.0) | Najwyższa kategoria preferencji KAS; **Virtual Threads** (`spring.threads.virtual.enabled=true`) pozwalają równolegle odpytywać konektory Web2 i Web3 na prostym, synchronicznym `RestClient` bez złożoności WebFluxa.

 |
| **Baza danych & Migracje** | **PostgreSQL 16 + Spring Data JPA + Flyway** (PostgreSQL / Apache 2.0) | Baza z listy preferowanej przez KAS; wersjonowane migracje SQL, zapis historii cyklicznych uruchomień (`analysis_runs`), dowodów z SHA-256 i diffów czasowych.

 |
| **Kontrakt API** | **Springdoc OpenAPI 3 (`springdoc-openapi`) + Jakarta Validation** (Apache 2.0) | Automatyczna dokumentacja `/swagger-ui.html` (świetnie wygląda na ocenie architektury technicznej) oraz jednoznaczny kontrakt DTO (`record`) między backendem a Reactem.

 |
| **Kryptografia & Walidacja** | **Bouncy Castle (`bcprov-jdk18on`)** (MIT-equivalent) | Keccak-256 (dla sum kontrolnych ETH EIP-55) + wbudowany `java.security.MessageDigest` (SHA-256 dla BTC/TRON Base58Check i hashy dowodowych Chain-of-Custody). |
| **Generator PDF** | **Apache PDFBox 3.0** (Apache 2.0) | 100% bezpieczna licencja Apache 2.0, osadzony font TTF (`DejaVuSans.ttf` / `Inter`) z pełnymi polskimi znakami, zero zewnętrznych przeglądarek w kontenerze. |
| **Warstwa AI (On-Prem / Wymienna)** | **Uniwersalny `AiClient` (OpenAI-compatible REST)** $\rightarrow$ **Ollama** lub profil **`local-rules`** (Apache 2.0) | Podmiana modelu jedną zmienną (`OLCYS_AI_BASE_URL`, `OLCYS_AI_MODEL`), zero wysyłania danych testowych i wyników analizy do niezatwierdzonych usług zewnętrznych.

 |
| **Frontend (Monorepo)** | **React 19 + Vite + TypeScript + Tailwind CSS + React Flow + Recharts** (MIT) | Wiodący framework z listy KAS umieszczony w tym samym monorepo (`frontend/`).

 |
| **Testy & Kontenery** | **JUnit 5 + Mockito + Testcontainers/H2** + **Docker Compose** | Powtarzalne testy jednostkowe i integracyjne scenariuszy walidacyjnych; uruchomienie całości przez `docker compose up --build`.

 |

---

## 2. Gotowy plik `AGENTS.md` (Monorepo Java 21 + React)

Wklej ten plik w katalogu głównym repozytorium jako `AGENTS.md` (oraz skopiuj jako `CLAUDE.md` / `.cursorrules`).

```markdown
# AGENTS.md — Projekt: OŁCyś (Java 21 + Spring Boot 3.4 + React — HackNation 2026 / KAS)

## 1. Cel Projektu
Narzędzie OSINT + analiza blockchain dla analityków Krajowej Administracji Skarbowej (KAS).
Na podstawie >=1 identyfikatora wejściowego (imię i nazwisko, nick, ID komunikatora, login social, e-mail lub publiczny adres krypto) system:
1. Przeszukuje wyłącznie publiczne, legalne źródła Web2 (pasywny biały wywiad OSINT).
2. Ekstrahuje i kryptograficznie waliduje adresy kryptowalutowe (BTC Base58Check/Bech32, ETH EIP-55, TRON Base58Check).
3. Odrzuca błędne przypisania (CEX, portfele powiernicze, smart kontrakty przez `eth_getCode` -> `bytecode != 0x`, miksery, zbiórki).
4. Wylicza deterministyczny, wyjaśnialny Wskaźnik Pewności (Confidence Score 0-100%) zgodnie z metodyką KAS (w tym premię +15 pp za >=2 niezależne źródła, max 100%).
5. Analizuje aktywność on-chain (wolumen, wymiany, sygnały Fiat Cash-Out: CEX/CASP, P2P, bramki/karty krypto-fiat, stablecoiny).
6. Zapisuje cykliczne analizy w PostgreSQL, porównuje zmiany w czasie (wykrywanie podmiany portfeli na profilach) i generuje raport OSINT (UI + PDF + JSON) z wyraźnym podziałem na 3 strefy:
   - FAKTY ŹRÓDŁOWE (URL, timestamp UTC, hash SHA-256, cytat)
   - WYNIKI OBLICZEŃ (Confidence Score breakdown, wolumeny, metryki on-chain, odrzucone FP)
   - WNIOSKI WYMAGAJĄCE DALSZEJ WERYFIKACJI (sygnały Fiat Cash-Out, ryzyka PIT-38, ograniczenia)

## 2. Twarde Zasady Techniczne i Bezpieczeństwa
- **MONOREPO:** Jedno repozytorium: `backend/` (Maven, Java 21, Spring Boot 3.4) + `frontend/` (React + Vite + TS) + `docker-compose.yml`.
- **LICENCJE OPEN SOURCE:** Wyłącznie Apache 2.0, MIT, BSD, ISC, EPL 2.0, PostgreSQL. ZAKAZ iText/GPL/AGPL! Do PDF używaj Apache PDFBox 3.x.
- **ZERO WYCIEKU DANYCH (KRYTYCZNE):** ZAKAZ wysyłania danych testowych, zapytań o podmioty lub wyników analiz do zewnętrznych komercyjnych API AI (OpenAI, Gemini, Anthropic). Moduł AI używa uniwersalnego klienta OpenAI-compatible skierowanego wyłącznie na lokalną Ollamę (`OLCYS_AI_BASE_URL=http://ollama:11434/v1`) z bezpiecznym fallbackiem `local-rules` i blokadą hostów spoza localhost/sieci dockerowej (`OLCYS_AI_ALLOW_EXTERNAL=false`).
- **ARCHITEKTURA PLUGINOWA:** Nowe źródła Web2 implementują interfejs `Web2Connector`, nowe sieci blockchain implementują `Web3Connector` (Spring automatycznie wstrzykuje `List<Web2Connector>` i `List<Web3Connector>`).
- **WSPÓŁBIEŻNOŚĆ:** Włącz Java 21 Virtual Threads (`spring.threads.virtual.enabled=true`) i wykonuj zapytania konektorów równolegle przez `Executors.newVirtualThreadPerTaskExecutor()` z twardym timeoutem 5s na konektor.
- **JĘZYK:** Kod i nazwy klas po angielsku; cała warstwa UI, uzasadnienia reguł i raport PDF w czystym, zrozumiałym języku polskim dla analityka KAS.

## 3. Struktura Monorepo
```text
/
├── AGENTS.md
├── docker-compose.yml                  # postgres, backend, frontend (+ profil ollama)
├── README.md                           # Pełna dokumentacja KAS + tabela licencji + przykłady/logi
├── backend/
│   ├── pom.xml                         # Spring Boot 3.4, Java 21, JPA, Flyway, PostgreSQL, PDFBox, BouncyCastle, Springdoc
│   ├── Dockerfile
│   └── src/
│       ├── main/
│       │   ├── resources/
│       │   │   ├── application.yml
│       │   │   ├── db/migration/V1__init.sql
│       │   │   ├── data/entity-tags.json        # Baza znanych adresów CEX/DEX/CASP/Mixer/Charity
│       │   │   ├── data/scenarios.json          # 3 zanonimizowane/syntetyczne scenariusze walidacyjne KAS
│       │   │   └── fonts/DejaVuSans.ttf         # Font z polskimi znakami do PDFBox
│       │   └── java/pl/kas/olcys/
│       │       ├── OlcysApplication.java
│       │       ├── config/                      # RestClient, CORS, OpenAPI, VirtualThreads
│       │       ├── api/                         # Kontrolery REST + DTO (Java records!)
│       │       │   ├── dto/                     # Request/Response records (ZAMROŻONY KONTRAKT)
│       │       │   ├── SubjectController.java
│       │       │   ├── AnalysisController.java
│       │       │   └── HealthController.java
│       │       ├── domain/                      # Encje JPA + Repo (Subject, AnalysisRun, EvidenceArtifact, AddressFinding)
│       │       ├── crypto/
│       │       │   └── CryptoAddressValidator.java  # BTC (Base58Check/Bech32), ETH (EIP-55), TRON + ekstraktor
│       │       ├── connector/
│       │       │   ├── web2/                    # Web2Connector + GithubConnector, RedditConnector, WaybackConnector, EnsConnector, SyntheticConnector
│       │       │   └── web3/                    # Web3Connector + EthereumConnector, BitcoinConnector, EntityTagRegistry
│       │       ├── engine/
│       │       │   ├── FalsePositiveFirewall.java   # bytecode != 0x, tagi CEX/Mixer/Charity, heurystyka
│       │       │   ├── ConfidenceScorer.java        # Deterministyczne progi KAS + premia synergii +15pp
│       │       │   ├── CashOutAnalyzer.java         # CEX Off-Ramp, Stablecoin Swap, P2P/Bramki, Cykliczność
│       │       │   ├── RunDiffService.java          # Porównywanie analiz w czasie (podmiana portfela!)
│       │       │   ├── AnalysisOrchestrator.java    # Równoległy pipeline (Virtual Threads)
│       │       │   └── ai/                          # AiClient interface + OpenAiCompatibleAiClient (Ollama) + LocalRulesAiClient
│       │       └── report/
│       │           ├── ReportBuilder.java           # Buduje 3-strefowy raport OSINT
│       │           └── PdfReportGenerator.java      # Apache PDFBox 3.0 z polskimi znakami
│       └── test/java/pl/kas/olcys/                  # JUnit 5: walidatory, firewall, scoring, cashout, diff, e2e
└── frontend/
    ├── package.json                    # React 19, Vite, TS, Tailwind, @xyflow/react, recharts, lucide-react
    ├── Dockerfile
    └── src/
        ├── types/api.ts                # 1:1 mirror Java DTO records
        ├── components/
        │   ├── SearchForm.tsx
        │   ├── EvidenceGraph.tsx
        │   ├── AddressCards.tsx
        │   ├── BlockchainFlow.tsx
        │   ├── HistoryDiff.tsx
        │   └── OsintReportView.tsx
        └── App.tsx

```

## 4. Zamrożony Kontrakt REST API (JSON camelCase)

* `POST /api/subjects` -> Body `{ displayName, identifiers: [{ type, value }], mode: "HYBRID"|"LIVE"|"SYNTHETIC", sources: string[], networks: string[], snapshotStep?: number }` -> tworzy podmiot i uruchamia pierwszą analizę, zwraca `AnalysisRunDto`.
* `POST /api/subjects/{id}/runs` -> Body `{ mode, sources, networks, snapshotStep?: number }` -> uruchamia kolejną cykliczną analizę.
* `GET /api/subjects` -> zwraca `SubjectSummaryDto[]` (lista spraw + historia uruchomień).
* `GET /api/runs/{runId}` -> zwraca pełny `AnalysisRunDto` (graf, dowody z SHA-256, zatwierdzone adresy z `scoreBreakdown`, odrzucone FP, analiza on-chain, sygnały Cash-Out, 3-strefowy `report`).
* `GET /api/subjects/{id}/diff?from={runId1}&to={runId2}` -> zwraca `RunDiffDto` (nowe/usunięte/podmienione adresy, `walletRotationDetected`, delta wolumenu, nowe sygnały Cash-Out).
* `GET /api/runs/{runId}/report.pdf` -> zwraca `application/pdf` (Attachment).
* `GET /api/runs/{runId}/report.json` -> zwraca kompletny eksport dowodowy JSON.
* `GET /api/scenarios` -> zwraca 3 wbudowane zanonimizowane scenariusze walidacyjne KAS.
* `GET /api/health` -> status PostgreSQL, listy zarejestrowanych konektorów Web2/Web3 oraz aktywnego providera AI (`externalDataTransfer: false`).

## 5. Komendy Weryfikacji (uruchamiaj po każdej zmianie!)

* Backend: `cd backend && ./mvnw test` (lub `mvn test`)
* Frontend: `cd frontend && npm run build`

```

---

## 3. Równoległe Prompty dla Agentów AI (Java 21 + React)

### KROK 0 (10 minut — odpal jako pierwszy, aby zamrozić kontrakty DTO i strukturę)
> **Prompt 0 (Bootstrap Monorepo Java 21 + DTO Records + Walidator Krypto):**
> Przeczytaj `AGENTS.md`. Zainicjuj kompletne monorepo:
> 1. `docker-compose.yml` (`postgres:16-alpine`, `backend` na porcie `8080`, `frontend` na porcie `5173`/`3000`, opcjonalny profil `ai` z `ollama/ollama`).
> 2. `backend/pom.xml` (Java 21, Spring Boot 3.4, `spring-boot-starter-web`, `spring-boot-starter-data-jpa`, `spring-boot-starter-validation`, `flyway-core`, `flyway-database-postgresql`, `postgresql`, `org.apache.pdfbox:pdfbox:3.0.3`, `org.bouncycastle:bcprov-jdk18on:1.79`, `org.springdoc:springdoc-openapi-starter-webmvc-ui:2.7.0`, test: `spring-boot-starter-test`, `com.h2database:h2`). Upewnij się, że 100% zależności ma licencje Apache 2.0 / MIT / BSD / EPL.
> 3. W `backend/src/main/java/pl/kas/olcys/api/dto/` utwórz kompletne, niezmienne **Java `record` DTO** dla całego kontraktu z sekcji 4 `AGENTS.md` oraz `frontend/src/types/api.ts` będący ich wiernym odbiciem 1:1 (camelCase).
> 4. W `CryptoAddressValidator.java` zaimplementuj kryptograficzną walidację sum kontrolnych: BTC Legacy/P2SH (Base58Check z double SHA-256), BTC SegWit/Taproot (Bech32/Bech32m), ETH/EVM (EIP-55 przez Keccak-256 z Bouncy Castle + odfiltrowanie 64-znakowych hashy) oraz TRON (Base58Check) + metodę `extractValidatedAddresses(String text)`.
> 5. Napisz testy JUnit 5 `CryptoAddressValidatorTest` i potwierdź `mvn test` oraz `frontend` `npm run build`.

---

### AGENT 1 — Warstwa DB (JPA + Flyway) & Konektory Web2/Web3 (`domain/` + `connector/` + `resources/data/`)
> **Prompt dla Agenta 1:**
> Pracujesz WYŁĄCZNIE w `backend/src/main/resources/`, `backend/src/main/java/pl/kas/olcys/domain/` oraz `backend/src/main/java/pl/kas/olcys/connector/`. Przeczytaj `AGENTS.md` i `api/dto/`.
> 1. **PostgreSQL + Flyway (`db/migration/V1__init.sql` + `domain/`):** Stwórz tabele i encje JPA: `subjects`, `subject_identifiers`, `analysis_runs` (z polem `result_json` typu `TEXT`/`JSONB` dla pełnej odtwarzalności audytowej i szybkiego diffu), `evidence_artifacts` (z `url`, `fetched_at`, `sha256`, `source_type`, `snippet`), `address_findings` (z `address`, `network`, `confidence_score`, `is_false_positive`, `rejection_reason`). Dodaj konfigurację profilu `test` na H2 (w trybie PostgreSQL compatibility) dla szybkiego `mvn test`.
> 2. **Konektory Web2 (`connector/web2/`):** Zdefiniuj interfejs `Web2Connector` (metody `String name()`, `boolean supports(AnalysisMode mode)`, `List<RawEvidence> search(List<IdentifierDto> identifiers, int snapshotStep)`). Każdy konektor używa Spring `RestClient` z timeoutem connect=3s, read=5s, liczy SHA-256 treści (`MessageDigest`), łapie wyjątki sieciowe (nigdy nie przerywa analizy) i zwraca metadane URL + `Instant.now()`:
>    - `GithubConnector`: publiczne API GitHub (`[api.github.com/users/](https://api.github.com/users/){nick}`, `/repos`, `/events/public`) szukające bio, linków, commit emaili i adresów krypto.
>    - `RedditConnector`: publiczny JSON API Reddita (`[reddit.com/user/](https://reddit.com/user/){nick}/submitted.json` i `search.json?q=...`) z klasyfikacją kontekstu (P2P vs forum vs giveaway/szum).
>    - `WaybackConnector`: Wayback Machine CDX API (`web.archive.org/cdx/search/cdx`) zwracający archiwalne migawki dla profili/stron.
>    - `EnsConnector`: publiczny resolver ENS / reverse lookup dla adresów ETH.
>    - `SyntheticConnector` + `resources/data/scenarios.json`: 3 bogate, zanonimizowane scenariusze walidacyjne KAS:
>      * **Scenariusz 1 („Jan Kowalski / @krypto_janek / janek.dev@example.pl”)**: profil GitHub z portfelem ETH + ogłoszenie P2P z tym samym portfelem ETH (**synergia +15 pp!**), post na forum z adresem gorącego portfela **Binance** i adresem **Uniswap Router (Smart Contract)** (oba muszą zostać odrzucone jako `0%` False Positive!), plus portfel BTC w stopce e-maila. Przy `snapshotStep = 2` (druga cykliczna analiza!) podmienia portfel ETH w bio GitHuba na nowy adres (demo detekcji podmiany portfela w czasie!).
>      * **Scenariusz 2 (Reverse OSINT — start od adresu ETH/BTC)**: odnajduje powiązanego nicka na forum i ogłoszeniu P2P oraz wykrywa wymianę na USDT + przelew na CEX (sygnał Fiat Cash-Out).
>      * **Scenariusz 3 (Wyłącznie szum i False Positives)**: nick w masowych komentarzach konkursowych/giveaway + cytowany adres zbiórki charytatywnej — narzędzie musi poprawnie nadać niski wynik / 0% i nie przypisać fałszywie portfela.
> 3. **Konektory Web3 (`connector/web3/`):**
>    - `resources/data/entity-tags.json` + `EntityTagRegistry`: min. 60 realnych, znanych publicznych adresów (gorące portfele Binance, Kraken, Coinbase, OKX, Bitfinex, Zonda, routery Uniswap V2/V3, kontrakty USDT/USDC/DAI, Tornado Cash, znane zbiórki charytatywne, burn) z polem `role` (`CEX`, `CASP_P2P`, `STABLECOIN`, `DEX_ROUTER`, `MIXER`, `CHARITY`, `BURN`).
>    - `EthereumConnector`: wywołuje publiczny JSON-RPC (`eth_getCode` na `ethereum-rpc.publicnode.com`) do sprawdzenia `bytecode != "0x"` (krytyczne dla odrzucania Smart Kontraktów!) oraz publiczne Blockscout API v2 (`[eth.blockscout.com/api/v2/addresses/](https://eth.blockscout.com/api/v2/addresses/){addr}`, `/transactions`, `/token-transfers`) do pobrania tagów, wolumenu i transferów USDT/USDC. Obsługuje tryb `SYNTHETIC` z gotowymi spójnymi profilami on-chain, żeby demo działało w <1s nawet bez internetu.
>    - `BitcoinConnector`: Mempool.space API (`mempool.space/api/address/{addr}` i `/txs`, fallback `blockstream.info/api`) + spójny profil syntetyczny.
> Napisz testy jednostkowe JUnit 5 dla `EntityTagRegistry` i `SyntheticConnector` i zweryfikuj `mvn test`.

---

### AGENT 2 — Silnik KAS (Firewall FP, Scoring, Cash-Out, Diff, Lokalny AI, PDFBox & Kontrolery)
> **Prompt dla Agenta 2:**
> Pracujesz w `backend/src/main/java/pl/kas/olcys/engine/`, `report/` i `api/`. Przeczytaj `AGENTS.md` i `api/dto/`.
> 1. **`FalsePositiveFirewall.java`:**
>    - Sprawdza 3 warstwy: (1) `bytecode != null && !bytecode.equals("0x")` $\rightarrow$ odrzucenie `SMART_CONTRACT` (`0%`), (2) `EntityTagRegistry` lub tag z API (`CEX`, `MIXER`, `CHARITY`, `DEX_ROUTER`, `BURN`) $\rightarrow$ odrzucenie jako portfel osobisty (`0%`), ale zachowanie flagi `recordInteraction = true` dla CEX/CASP, (3) heurystyka `txCount > 10000` $\rightarrow$ flaga `SUSPECTED_CUSTODIAL` z ostrzeżeniem.
> 2. **`ConfidenceScorer.java` (100% deterministyczny, zgodny z załącznikiem KAS):**
>    - Progi bazowe: `OWN_PROFILE_VERIFIED` = **85%** (80–100%), `TRANSACTIONAL_CONTEXT` (P2P / ogłoszenie sprzedaży / stopka e-mail / komunikator rozliczeniowy) = **70%** (60–79%), `FORUM_DISCUSSION` = **50%** (40–59%), `MASS_COMMENT_NOISE` (giveaway / konkursy) = **20%** (10–39%), `FALSE_POSITIVE` = **0%** (twardy sufit, bez premii!).
>    - **Reguła Synergii:** grupuje dowody po niezależnej platformie/domenie (kilka postów w jednym serwisie = 1 źródło!). Jeśli $\ge 2$ niezależne źródła zawierają ten sam adres $\rightarrow$ **+15 pp** (max 100%).
>    - Zwraca kompletną listę `ScoreBreakdownItem(ruleId, label, delta, evidenceIds, explanationPl)`.
> 3. **`CashOutAnalyzer.java`:**
>    - Analizuje transakcje zatwierdzonych portfeli niehostowanych i wykrywa: `CEX_OFFRAMP` (przelew do znanego CEX/CASP/kantoru), `STABLECOIN_CONVERSION` (znaczące transfery/swapy USDT/USDC/DAI), `P2P_OR_GATEWAY` (korelacja daty ogłoszenia P2P z transakcją wychodzącą w oknie ±48h lub bramka/karta krypto-fiat), `RECURRING_CASHOUT` (cykliczne wypłaty zbliżonych kwot).
>    - Buduje chronologiczną oś przepływu środków (`TimelineEventDto`) z przeliczeniem szacunkowej wartości na PLN.
> 4. **`RunDiffService.java`:**
>    - Porównuje 2 zapisane `AnalysisRun` dla podmiotu: wykrywa **podmianę adresu portfela na tym samym profilu Web2** (`walletRotationDetected = true` + opis starego i nowego adresu), nowo wykryte adresy, zmianę Confidence Score, przyrost wolumenu i nowe zdarzenia Cash-Out.
> 5. **`engine/ai/` (Uniwersalny, Lokalny Adapter AI — ZERO wycieku danych!):**
>    - Interfejs `AiClient` + dwie implementacje wybierane przez `olcys.ai.provider` (`local-rules` | `ollama` | `openai-compatible`):
>      * `OpenAiCompatibleAiClient`: wywołuje `${olcys.ai.base-url}/chat/completions` (domyślnie `http://ollama:11434/v1`), **weryfikuje hosta przed wysłaniem requestu** i rzuca wyjątek bezpieczeństwa, jeśli host nie jest lokalny/wewnętrzny przy `olcys.ai.allow-external=false`! Przy timeout/błędzie przełącza się na `LocalRulesAiClient`.
>      * `LocalRulesAiClient`: w pełni deterministyczna, lokalna klasyfikacja kontekstu wypowiedzi (słowa kluczowe giveaway vs P2P vs własny profil) + generator zwięzłego podsumowania wykonawczego po polsku na podstawie policzonych liczb.
> 6. **`AnalysisOrchestrator.java`:**
>    - Wykorzystuje Java 21 `Executors.newVirtualThreadPerTaskExecutor()` do równoległego odpytania wszystkich `Web2Connector`, ekstrakcji i walidacji adresów, równoległego odpytania `Web3Connector`, przepuszczenia przez Firewall FP, Scorer, CashOutAnalyzer, zbudowania grafu (nodes/edges) i 3-strefowego raportu (`ReportBuilder.java`), oraz zapisu do PostgreSQL.
> 7. **`PdfReportGenerator.java` (Apache PDFBox 3.0):**
>    - Osadza font `DejaVuSans.ttf` z `resources/fonts/` (jeśli brak w repo, dołącz go lub wygeneruj skryptem / użyj sprawdzonego TTF z pełnym UTF-8 PL).
>    - Tworzy czytelny raport PDF podzielony WYRAŹNIE na: **SEKCJA 1: FAKTY ŹRÓDŁOWE** (URL, data UTC, SHA-256), **SEKCJA 2: WYNIKI OBLICZEŃ** (Confidence Score z rozpiską, odrzucone False Positives, metryki on-chain i chronologia przepływu), **SEKCJA 3: WNIOSKI WYMAGAJĄCE DALSZEJ WERYFIKACJI** (sygnały Fiat Cash-Out, zalecenia PIT-38, ograniczenia).
> 8. Spięcie wszystkich endpointów w `SubjectController`, `AnalysisController`, `HealthController` + kompleksowy test E2E JUnit 5 (`OlcysEndToEndTest.java`) weryfikujący wszystkie 3 scenariusze, diff podmiany portfela i niepusty PDF! Potwierdź `mvn test`.

---

### AGENT 3 — Frontend Analityczny KAS (`frontend/` — React 19 + Vite + TypeScript)
> **Prompt dla Agenta 3:**
> Pracujesz WYŁĄCZNIE w `frontend/`. Przeczytaj `AGENTS.md` i `frontend/src/types/api.ts`.
> Zbuduj nowoczesny, instytucjonalny, maksymalnie przejrzysty interfejs analityczny po polsku dla pracownika KAS (paleta: granat `#0F2942`, czysta biel, chłodna szarość; statusy: zieleń potwierdzenia `#15803D`, bursztyn weryfikacji `#B45309`, czerwień ryzyka/wykluczenia `#B91C1C`). Skonfiguruj proxy w `vite.config.ts` dla `/api` $\rightarrow$ `http://localhost:8080`.
> Zbuduj 5 kluczowych modułów:
> 1. **`SearchForm.tsx`:** Dynamiczna lista identyfikatorów wejściowych (imię i nazwisko, nick, ID komunikatora, login social, e-mail, adres krypto), wybór trybu (`Hybrydowy`, `Live OSINT`, `Scenariusz Walidacyjny`), wybór źródeł Web2 i sieci Web3, **3 przyciski szybkiego startu scenariuszy walidacyjnych KAS** (z `GET /api/scenarios`) oraz panel boczny zapisanych spraw z przyciskiem **„Uruchom analizę kontrolną (v2)”** (wysyła `snapshotStep: 2` i od razu pozwala pokazać wykrycie podmiany portfela w czasie!).
> 2. **`EvidenceGraph.tsx` (`@xyflow/react`):** Czytelny graf warstwowy od lewej do prawej: `[Identyfikatory Podmiotu]` $\rightarrow$ `[Źródła Web2]` $\rightarrow$ `[Portfele Niehostowane (badge % pewności)]` + `[Odrzucone FP (przekreślone CEX/Smart Contract)]` $\rightarrow$ `[Węzły Fiat Cash-Out: CEX / P2P / Stablecoin]`. Kliknięcie węzła otwiera boczny inspektor dowodowy z URL, datą UTC, cytatem i hashem SHA-256.
> 3. **`AddressCards.tsx`:** Dwie wyraźnie oddzielone sekcje:
>    - **„Zidentyfikowane portfele niehostowane”** — kolorowy pasek Confidence Score (0–100%) + pełne rozbicie każdej reguły punktowej (`+70% Ogłoszenie P2P`, `+15% Synergia z GitHub`), klikalne linki źródłowe.
>    - **„Odrzucone trafienia — Ochrona przed błędnym przypisaniem (False Positives)”** — adresy CEX, smart kontrakty (`bytecode != 0x`), zbiórki z wynikiem `0%`, dokładnym powodem odrzucenia i etykietą „Odnotowano interakcję” jeśli portfel podmiotu wysyłał tam środki.
> 4. **`BlockchainFlow.tsx`:** Karty KPI (łączny wolumen przychodzący/wychodzący, liczba transakcji, sygnały Fiat Cash-Out, aktywność stablecoin), wykres `Recharts` przepływów w czasie oraz chronologiczna tabela transakcji z kolorowymi tagami (`CEX Off-Ramp`, `Stablecoin Swap`, `Korelacja P2P`, `Cykliczna wypłata`).
> 5. **`OsintReportView.tsx` + `HistoryDiff.tsx`:**
>    - Zakładka **„Raport OSINT (3 strefy)”** wyraźnie rozdzielająca wizualnymi sekcjami: **1. FAKTY ŹRÓDŁOWE**, **2. WYNIKI OBLICZEŃ**, **3. WNIOSKI WYMAGAJĄCE DALSZEJ WERYFIKACJI** + przyciski „Pobierz raport PDF” (`/api/runs/{id}/report.pdf`) i „Eksportuj JSON”.
>    - Zakładka **„Porównanie w czasie (Diff)”** z czerwonym alertem `Wykryto podmianę adresu portfela na profilu` (stary vs nowy adres, źródło, daty obu analiz) oraz przyrostem wolumenu.
>    - Pasek statusu w nagłówku z `/api/health`: np. `Backend: Java 21 | AI: Lokalny / On-Premise (0 danych poza środowiskiem KAS)`.
> Zweryfikuj przez `npm run build` bez błędów TypeScript.

---

### AGENT 4 — Dokumentacja KAS, Audyt Licencji Maven/NPM, Skrypt Walidacyjny i Pitch
> **Prompt dla Agenta 4 (odpal wieczorem / w niedzielę rano):**
> Przeskanuj całe monorepo pod kątem wymogów formalnych i technicznych KAS z `AGENTS.md`:
> 1. Wygeneruj kompletny `README.md` zawierający wszystkie wymagane elementy: (a) Opis projektu — problem PIT-38/OSINT i jak OŁCyś go rozwiązuje, (b) Założenia projektu, opis parametrów wyszukiwania, algorytm Confidence Score i 3-warstwowy Firewall FP, (c) Diagram architektury (monorepo Java 21 + Spring Boot + PostgreSQL + React + pluggable `Web2Connector`/`Web3Connector` + lokalny wymienny `AiClient`), (d) Instrukcja uruchomienia przez `docker compose up --build` oraz lokalnie (`mvn spring-boot:run` + `npm run dev`) z linkiem do `/swagger-ui.html`, (e) Kompletna tabela licencji Open Source dla wszystkich zależności z `backend/pom.xml`, `frontend/package.json` oraz źródeł danych, (f) Przykłady logów i wyniku JSON z 3 scenariuszy walidacyjnych oraz sekcja na zrzuty ekranu.
> 2. Zweryfikuj `backend/pom.xml` i `frontend/package.json` — upewnij się, że brak bibliotek GPL/AGPL oraz że domyślna konfiguracja nie wysyła danych poza środowisko lokalne.
> 3. Napisz skrypt `scripts/run-demo-check.sh` (curl + jq), który po `docker compose up` przechodzi pełny obowiązkowy scenariusz KAS (utworzenie sprawy $\rightarrow$ analiza v1 $\rightarrow$ weryfikacja odrzucenia CEX i Smart Contractu $\rightarrow$ analiza v2 $\rightarrow$ diff podmiany portfela $\rightarrow$ pobranie `report.pdf` i sprawdzenie rozmiaru >0 KB).
> 4. Przygotuj konspekt **10 slajdów prezentacji PDF** (limit regulaminowy) oraz **scenariusz 3-minutowego filmu demo** (do umieszczenia w otwartym repozytorium zgodnie z regulaminem) z podziałem na sekundy pokrywającym wszystkie obowiązkowe etapy oceny.

---

## 4. Najważniejsze detale implementacyjne w Javie (oszczędzają 2h debugowania)

* **Polskie znaki w Apache PDFBox 3.0:** Domyślne fonty `Standard14Fonts` (Helvetica/Times) **rzucają wyjątek `IllegalArgumentException: U+0142 ('lslash') is not available`** przy polskich znakach (`ą, ć, ę, ł, ń, ó, ś, ź, ż`). Od razu wrzuć `DejaVuSans.ttf` i `DejaVuSans-Bold.ttf` do `backend/src/main/resources/fonts/` i ładuj je przez `PDType0Font.load(document, inputStream)`.
* **Virtual Threads + brak blokowania konektorów:** W `application.yml` ustaw `spring.threads.virtual.enabled: true`, a w `AnalysisOrchestrator` uruchom wszystkie `Web2Connector` (a potem `Web3Connector`) równolegle przez `try (var executor = Executors.newVirtualThreadPerTaskExecutor())` i zbierz wyniki z `future.get(6, TimeUnit.SECONDS)` — nawet jeśli GitHub lub Reddit złapią rate-limit, cała analiza zamknie się w **2–4 sekundach**.
* **Docker Multi-stage build dla Mavena:** W `backend/Dockerfile` użyj `maven:3.9-eclipse-temurin-21` do buildu i `eclipse-temurin:21-jre-alpine` (z pakietem `ttf-dejavu`!) w warstwie runtime — obraz będzie lekki (~180 MB) i uruchomi się błyskawicznie przy ocenie przez jury.
* **Swagger UI jako dodatkowy atut na demo:** Włącz `springdoc-openapi` pod `/swagger-ui.html` — w kryterium *Potencjał wdrożeniowy i architektura techniczna (15%)*[cite: 31, 32] pokazanie na 10 sekund czystego OpenAPI + architektury pluginowej Springa (`Web2Connector`, `Web3Connector`, `AiClient`) + uruchomienia `docker compose up` robi ogromne wrażenie na jurorach z administracji.


```
