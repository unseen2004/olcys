# OŁCyŚ — Scenariusz Finałowej Prezentacji Live (Etap 2 • 2:45–3:00 min)

> **Czas całkowity prezentacji na żywo:** **2:50 min** (170 s — bezpieczny zapas 10 s przed twardym limitem 3:00)  
> **Prowadzenie ekranu:** Slajd 1 z `docs/prezentacja_etap2_tlo.pdf` (`0:00–0:20`) → aplikacja live `http://localhost:5173` (`0:20–2:50`) → Slajdy 2 i 3 jako tło pod sesję Q&A Jury.  
> **Rozdzielczość sceniczna:** `1920×1080`, powiększenie przeglądarki `100%–110%`, uruchomiony backend oraz frontend (`docker compose up` / lokalny Spring Boot + Vite).

---

## Część I — Scenariusz Live Pitch (sekunda po sekundzie)

### `[0:00–0:20]` Parametry wyszukiwania (20 s)

- **[CO KLIKASZ NA EKRANIE]**
  - Zaczynasz na **Slajdzie 1** (`docs/prezentacja_etap2_tlo.pdf`), pokazując tabelę parametrów i założeń, a w połowie fragmentu płynnie przełączasz okno na uruchomiony interfejs **OŁCyŚ** (`http://localhost:5173`).
  - Wskazujesz kursorem w górnym nagłówku zielony status **`AI: Lokalny On-Premise | Zero transferu zewnętrznego`** oraz licznik **`Konektory: 6 Web2 / 3 Web3`** (`ENS`, `GitHub`, `Keybase`, `Reddit`, `Synthetic`, `WaybackArchive` oraz `BTC`, `ETH`, `TRON`).
- **[CO MÓWISZ]**
  - *„Dzień dobry. Prezentujemy **OŁCyŚ** — Osintowy Łapacz Cyfrowych Śladów dla Krajowej Administracji Skarbowej, wspierający weryfikację nieujawnionych przychodów w **PIT-38**. System działa w 100% **on-premise**: stosujemy wyłącznie legalny, pasywny OSINT z sześciu konektorów Web2 i trzech sieci Web3, bez wysyłania jakichkolwiek danych testowych czy spraw do chmury — dlatego w demo używamy deterministycznego trybu **SYNTHETIC / Scenariusz Walidacyjny**. Wynik interpretujemy przez wyjaśnialny **Wskaźnik Pewności** oraz **3-warstwowy Firewall False Positives**.”*

---

### `[0:20–0:35]` Etap 1 — Wprowadzenie danych wejściowych (15 s)

- **[CO KLIKASZ NA EKRANIE]**
  - W lewym panelu **„Nowe zapytanie OSINT”** zjeżdżasz do karty **„SCENARIUSZ 1: Jan Kowalski (@krypto_janek)”** i klikasz **`Edytuj`** (wypełnia formularz: podmiot `Jan Kowalski (@krypto_janek)`, 3 identyfikatory `NICK: krypto_janek`, `FULL_NAME: Jan Kowalski`, `EMAIL: janek.dev@example.pl`, tryb `Scenariusz Walidacyjny`, sieci `ETH` i `BTC`), po czym klikasz główny przycisk **`Uruchom analizę OSINT & Web3`** *(alternatywnie dla oszczędności kliknięcia: bezpośrednio **`Uruchom`** na karcie Scenariusza 1)*.
- **[CO MÓWISZ]**
  - *„Rozpoczynamy analizę dla **Scenariusza 1: Jan Kowalski (`@krypto_janek`)**. Wprowadzamy trzy identyfikatory: pseudonim `NICK: krypto_janek`, imię i nazwisko `FULL_NAME: Jan Kowalski` oraz `EMAIL: janek.dev@example.pl`. Wybieramy tryb **`SYNTHETIC`**, zaznaczamy sieci **`ETH`** oraz **`BTC`** i klikamy **`Uruchom analizę OSINT & Web3`**, zlecając równoległe zapytanie na Java 21 Virtual Threads.”*

---

### `[0:35–0:55]` Etap 2 — Wyszukanie śladów OSINT (20 s)

- **[CO KLIKASZ NA EKRANIE]**
  - System otwiera domyślną zakładkę **`Graf powiązań`**. Wskazujesz kursorem 4 odnalezione źródła w kolumnie **`2. Źródła Web2`**: `GitHub`, `KantorP2P.pl`, `WaybackArchive` oraz `ForumKrypto.pl`.
  - Klikasz węzeł **`GitHub`** na grafie, otwierając po prawej stronie **`Inspektor Dowodowy KAS`**, i pokazujesz w nim pola **`URL źródła`** (`https://github.com/krypto_janek`), **`Znacznik czasu UTC`** oraz **`Hash dowodowy SHA-256`**.
- **[CO MÓWISZ]**
  - *„W ułamku sekundy graf powiązań pokazuje **cztery odnalezione źródła Web2**: profil deweloperski **`GitHub`**, ogłoszenie **`KantorP2P.pl`**, historyczną migawkę **`WaybackArchive`** oraz wpis na **`ForumKrypto.pl`**. Po kliknięciu węzła `GitHub` otwiera się **Inspektor Dowodowy KAS**, w którym każdy pozyskany materiał ma zapisany dokładny **URL źródła**, **znacznik czasu UTC**, pełny **hash SHA-256** treści oraz oryginalny cytat dla zachowania łańcucha dowodowego.”*

---

### `[0:55–1:10]` Etap 3 — Wykrycie i weryfikacja adresu kryptowalutowego (15 s)

- **[CO KLIKASZ NA EKRANIE]**
  - Klikasz drugą zakładkę: **`Portfele & Firewall FP`** (`2 / 2 FP`).
  - Wskazujesz górną kartę **ETH** (`0x71C7656EC7ab88b098defB751B7401B5f6d8976F`) z etykietami **`EIP-55 (Keccak-256)`** i **`Suma kontrolna poprawna`**, a następnie kartę **BTC** poniżej (`bc1qar0srrr7xfkvy5l643lydnw9re59gtzzwf5mdq`) z etykietami **`Bech32 (SegWit)`** i **`Suma kontrolna poprawna`**.
- **[CO MÓWISZ]**
  - *„Przechodzimy do zakładki **`Portfele & Firewall FP`**. Z surowych tekstów Web2 parser automatycznie wyekstrahował adresy kryptowalutowe i zweryfikował je kryptograficznie: adres Ethereum `0x71C7656EC7ab88b098defB751B7401B5f6d8976F` przeszedł pełną walidację sumy kontrolnej **`EIP-55`**, a adres Bitcoin `bc1qar0srrr7xfkvy5l643lydnw9re59gtzzwf5mdq` walidację **`Bech32`**, z automatycznym odfiltrowaniem hashy transakcji.”*

---

### `[1:10–1:35]` Etap 4 — Wyliczenie Wskaźnika Pewności (25 s)

- **[CO KLIKASZ NA EKRANIE]**
  - Na karcie ETH wskazujesz wynik **`100%`** (`BARDZO WYSOKIE PRAWDOPODOBIEŃSTWO`) oraz tabelę **`Deterministyczne rozbicie punktowe (metodyka KAS)`**: wiersz `+85 pkt` (`Oficjalny, autorski profil podmiotu`, źródło `GitHub`) oraz wiersz `+15 pkt` (`Premia synergii (>=2 niezależne źródła Web2)`, `GitHub + KantorP2P.pl`).
  - Przewijasz lekko niżej do karty BTC i wskazujesz wynik **`70%`** (`PRAWDOPODOBNY`) oraz regułę `+70 pkt` (`Kontekst transakcyjny / ogłoszenie P2P / stopka rozliczeniowa`, źródło `WaybackArchive`).
- **[CO MÓWISZ]**
  - *„Spójrzmy na **Wskaźnik Pewności** — bez czarnej skrzynki AI, z pełną wyjaśnialnością każdej reguły. Portfel **ETH uzyskał `100%`**: **`85 pkt`** bazowych za oficjalny profil na **`GitHub`** plus **`+15 pkt`** premii synergii za potwierdzenie w drugim, niezależnym źródle **`KantorP2P.pl`**. Z kolei portfel **BTC otrzymał `70%`** za kontekst transakcyjny B2B w archiwum **`WaybackArchive`**.”*

---

### `[1:35–2:00]` Etap 5 — Odrzucenie błędnych przypisań (25 s)

- **[CO KLIKASZ NA EKRANIE]**
  - Zjeżdżasz niżej do sekcji **`Odrzucone trafienia — Ochrona przed błędnym przypisaniem (Firewall False Positives)`** (`Wykluczone FP: 2`).
  - Wskazujesz na pierwszej czerwonej karcie wynik **`0% • ODRZUCONO FP`**, przekreślony adres **`Uniswap V3: SwapRouter`** (`0xE592...1564`) oraz linię `eth_getCode bytecode: ... (bytecode != 0x)`. Na drugiej czerwonej karcie wskazujesz **`Binance 14 (Hot Wallet)`** (`0x28C6...1d60`, kategoria `CEX`) oraz na obu kartach bursztynową plakietkę **`Odnotowano interakcję on-chain`**.
- **[CO MÓWISZ]**
  - *„Na forum podatnik cytował jednak jeszcze dwa adresy — tu zadziałał **Firewall False Positives**. Oba odrzucono z wynikiem **`0%`**: router **`Uniswap V3: SwapRouter`** (`0xE592...1564`) w **Warstwie 1** przez wywołanie `eth_getCode` i niepusty **`bytecode != 0x`**, a **`Binance 14 Hot Wallet`** (`0x28C6...1d60`) w **Warstwie 2** przez bazę **Address Tags** kategorii **`CEX`**. Co ważne: system zachował przy nich flagę **`interactionRecorded=true`**, bo nie są to portfele podatnika, lecz ślady jego bezpośrednich interakcji on-chain.”*

---

### `[2:00–2:25]` Etap 6 — Analiza wybranych wskaźników aktywności blockchain (25 s)

- **[CO KLIKASZ NA EKRANIE]**
  - Klikasz trzecią zakładkę: **`Przepływy On-Chain & Cash-Out`** (`13`).
  - Wskazujesz 4 kafelki KPI na górze: **`176 600,00 zł`** (`176 600,00 PLN`), **`12 100,00 USD`**, **`50`** transakcji oraz **`13`** sygnałów Fiat Cash-Out.
  - Zjeżdżasz o jeden ekran, wskazując **`Kalkulator Szacunkowej Luki Podatkowej PIT-38`** (`25 574,00 zł`), wykres słupkowy **`Przepływy środków w czasie (Przychodzące vs Wychodzące w PLN)`** oraz karty sygnałów w 5 klasach (`CEX Off-Ramp`, `Konwersja Stablecoin`, `Korelacja P2P / Bramka krypto-fiat`, `Cykliczna wypłata środków`, `Przepływ 2-Hop / Dzielenie transz (AML)`).
- **[CO MÓWISZ]**
  - *„W zakładce **`Przepływy On-Chain & Cash-Out`** widzimy skalę aktywności zatwierdzonych portfeli: łączny wolumen **`176 600,00 PLN`**, **`12 100,00 USD`** w stablecoinach i **`50` transakcji** na chronologicznym wykresie **`IN/OUT`**. System wykrył **`13` sygnałów Fiat Cash-Out** we wszystkich **5 klasach** — od **`CEX_OFFRAMP`** i **`STABLECOIN_CONVERSION`**, przez **`P2P_OR_GATEWAY`** skorelowane w oknie **±72h** z ogłoszeniem, po **`RECURRING_CASHOUT`** i **`AML_STRUCTURING_OR_2HOP`**. Kalkulator **`19%` PIT-38** wylicza od razu szacowaną lukę podatkową na **`25 574,00 PLN`**.”*

---

### `[2:25–2:50]` Etap 7 — Przedstawienie źródeł i wyniku końcowego (25 s)

- **[CO KLIKASZ NA EKRANIE]**
  - Klikasz zakładkę **`Raport OSINT (3 strefy)`**, pokazując krótko kolejno trzy bloki: **`Strefa 1: Fakty źródłowe`**, **`Strefa 2: Wyniki obliczeń`** oraz **`Strefa 3: Wnioski wymagające dalszej weryfikacji`**, a na dole klikasz zielony przycisk **`Przelicz SHA-256 w przeglądarce`**.
  - Następnie w belce u góry klikasz złoty przycisk **`Uruchom analizę kontrolną (v2 — wykryj podmianę portfela)`** — interfejs automatycznie przechodzi do zakładki **`Porównanie w czasie (Diff)`**, gdzie wskazujesz czerwony alert **`WYKRYTO PODMIANĘ ADRESU PORTFELA NA PROFILU`** (`GitHub`: `0x71C7...76F` → `0xd8dA...96045`) oraz kafelek **`+57 000,00 zł`** (`Przyrost / Delta wolumenu`).
- **[CO MÓWISZ]**
  - *„Końcowy **`Raport OSINT (3 strefy)`** rygorystycznie oddziela **Strefę 1 `Fakty źródłowe`**, **Strefę 2 `Wyniki obliczeń`** i **Strefę 3 `Wnioski wymagające dalszej weryfikacji`**, a przycisk **`Przelicz SHA-256 w przeglądarce`** potwierdza lokalnie integralność całego materiału przed eksportem do PDF. Na koniec klikamy **`Uruchom analizę kontrolną (v2)`**: w zakładce **`Porównanie w czasie (Diff)`** system natychmiast podnosi alert **podmiany portfela na `GitHub`** na nowy adres **`0xd8dA...96045`** i wylicza przyrost wolumenu **`+57 000,00 PLN`**. Dziękujemy i zapraszamy do pytań.”*

---

## Część II — Sekcja Q&A: 5 precyzyjnych odpowiedzi na najtrudniejsze pytania Jury

> **Zasada sceniczna:** Każda odpowiedź ma dokładnie **2–3 zwięzłe zdania**, z konkretnymi mechanizmami w kodzie; w trakcie Q&A przełącz na **Slajd 2** (Confidence Score + Firewall FP) lub **Slajd 3** (Architektura On-Premise + Licencje).

### 1. Ochrona danych i działanie AI on-premise w środowisku KAS bez chmury publicznej
Domyślnie system pracuje na deterministycznym silniku `local-rules` albo lokalnym modelu Ollama w wewnętrznej sieci kontenerowej, dzięki czemu żadne dane ani identyfikatory spraw nie opuszczają infrastruktury KAS. Konfiguracja `OLCYS_AI_ALLOW_EXTERNAL=false` wymusza w `OpenAiCompatibleAiClient` rygorystyczną walidację adresu docelowego i rzuca wyjątek `SecurityException` przy każdej próbie wywołania zewnętrznego API chmurowego. Ponadto tryb `SYNTHETIC` pozwala na pełne testy i audyty całkowicie offline, co sygnalizuje w nagłówku wskaźnik `Zero transferu zewnętrznego`.

### 2. Deduplikacja źródeł przy premii synergii `+15 pp`
Silnik `ConfidenceScorer` wybiera wyłącznie jedną najwyższą kategorię bazową dla danego adresu i grupuje artefakty po znormalizowanej domenie lub niezależnej platformie Web2. Wielokrotne wzmianki w obrębie tej samej platformy — na przykład dziesięć powtarzających się wpisów na tym samym forum — są deduplikowane do jednego źródła i nigdy nie sumują punktów. Premia synergii `+15 pp` (ograniczona do maksymalnie `100%`) jest przyznawana wyłącznie wtedy, gdy ten sam adres zostanie niezależnie potwierdzony w co najmniej dwóch odrębnych źródłach Web2, takich jak `GitHub` i `KantorP2P.pl`.

### 3. Ochrona przed False Positives (3 warstwy Firewalla FP)
Trzywarstwowy Firewall FP eliminuje błędne przypisania kolejno: w Warstwie 1 poprzez wywołanie `eth_getCode` odrzuca smart kontrakty (`bytecode != 0x`) z jednoczesnym prawidłowym przepuszczaniem delegowanych kont EOA w standardzie `EIP-7702`. W Warstwie 2 weryfikuje wbudowany rejestr 83 zweryfikowanych tagów (`CEX`, `CASP_P2P`, `DEX_ROUTER`, `STABLECOIN`, `MIXER`, `CHARITY`, `BURN`) oraz etykiety z eksploratora on-chain, a w Warstwie 3 wyklucza konta powiernicze przekraczające próg `10 000` transakcji. Co kluczowe, pomimo wyzerowania własności do `0%`, system zachowuje flagę `interactionRecorded=true`, dzięki czemu bezpośredni przelew z prywatnego portfela podatnika na giełdę pozostaje dowodem aktywności Cash-Out.

### 4. Rozbudowa o nowe źródła Web2 i kolejne sieci blockchain
Warstwa integracyjna jest zaprojektowana w czystej architekturze pluginowej opartej na interfejsach `Web2Connector` dla źródeł białowywiadowczych oraz `Web3Connector` i `EntityTagProvider` dla sieci blockchain i rejestrów podmiotów. Również komponent sztucznej inteligencji jest odseparowany kontraktem `AiClient`, co umożliwia bezinwazyjną wymianę silnika między `local-rules` a lokalnym modelem LLM. Włączenie nowego konektora — przykładowo dla danych sprawozdawczych DAC8/MiCA, kolejnego komunikatora czy następnego łańcucha L1/L2 — wymaga jedynie dodania jednej klasy implementującej interfejs i zarejestrowania jej jako komponent Springa.

### 5. Zgodność z ramami legalnego OSINT i licencjami Open Source
OŁCyŚ przeprowadza wyłącznie pasywny OSINT na otwartych, publicznie dostępnych źródłach i eksploratorach blockchain, bez obchodzenia uwierzytelniania, paywalli ani zabezpieczeń technicznych. W całym stosie technologicznym świadomie wyeliminowano biblioteki objęte licencjami `GPL` i `AGPL` — dlatego eksport PDF oparliśmy na bibliotece `Apache PDFBox 3.0.3` (Apache 2.0) zamiast iText. Pozostałe komponenty, w tym `Bouncy Castle 1.79` (licencja typu MIT), Spring Boot (Apache 2.0) oraz React 19 (MIT), zapewniają pełną swobodę prawną wdrożenia w systemach KAS.
