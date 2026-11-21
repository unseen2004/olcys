package pl.kas.olcys;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import pl.kas.olcys.api.CyclicMonitoringController;
import pl.kas.olcys.api.dto.AddressFindingDto;
import pl.kas.olcys.api.dto.AnalysisMode;
import pl.kas.olcys.api.dto.AnalysisRunDto;
import pl.kas.olcys.api.dto.CashOutSignalDto;
import pl.kas.olcys.api.dto.CreateSubjectRequest;
import pl.kas.olcys.api.dto.HealthStatusDto;
import pl.kas.olcys.api.dto.IdentifierDto;
import pl.kas.olcys.api.dto.RunDiffDto;
import pl.kas.olcys.api.dto.ScenarioPresetDto;
import pl.kas.olcys.api.dto.SubjectSummaryDto;
import pl.kas.olcys.api.dto.TriggerRunRequest;
import pl.kas.olcys.domain.AnalysisRunRepository;
import pl.kas.olcys.domain.MonitoringScheduleEntity;
import pl.kas.olcys.domain.MonitoringScheduleRepository;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OlcysEndToEndTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CyclicMonitoringController cyclicMonitoringController;

    @Autowired
    private MonitoringScheduleRepository monitoringScheduleRepository;

    @Autowired
    private AnalysisRunRepository analysisRunRepository;

    @Test
    void shouldExposeHealthyOnPremiseStatusAndThreeScenarios() throws Exception {
        MvcResult healthRes = mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andReturn();
        HealthStatusDto health = objectMapper.readValue(
                healthRes.getResponse().getContentAsString(StandardCharsets.UTF_8),
                HealthStatusDto.class
        );
        assertThat(health.status()).isEqualTo("UP");
        assertThat(health.virtualThreadsEnabled()).isTrue();
        assertThat(health.externalDataTransfer()).isFalse();
        assertThat(health.aiProvider()).isEqualTo("local-rules");
        assertThat(health.web2Connectors()).containsExactlyInAnyOrder(
                "ENS", "GitHub", "Keybase", "Reddit", "Synthetic", "WaybackArchive"
        );
        assertThat(health.web3Connectors()).containsExactlyInAnyOrder("BTC", "ETH", "TRON");

        MvcResult scenariosRes = mockMvc.perform(get("/api/scenarios"))
                .andExpect(status().isOk())
                .andReturn();
        ScenarioPresetDto[] scenarios = objectMapper.readValue(
                scenariosRes.getResponse().getContentAsString(StandardCharsets.UTF_8),
                ScenarioPresetDto[].class
        );
        assertThat(scenarios).hasSize(3);
    }

    @Test
    void shouldExecuteScenario1RotationDiffAndPdfJsonExportEndToEnd() throws Exception {
        // Step 1 (v1): Create subject & run Scenario 1
        CreateSubjectRequest reqV1 = new CreateSubjectRequest(
                "Jan Kowalski (@krypto_janek)",
                List.of(
                        new IdentifierDto("NICK", "krypto_janek"),
                        new IdentifierDto("FULL_NAME", "Jan Kowalski"),
                        new IdentifierDto("EMAIL", "janek.dev@example.pl")
                ),
                AnalysisMode.SYNTHETIC,
                List.of("GitHub", "KantorP2P.pl", "WaybackArchive", "ForumKrypto.pl"),
                List.of("ETH", "BTC"),
                1
        );

        MvcResult v1Res = mockMvc.perform(post("/api/subjects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqV1)))
                .andExpect(status().isCreated())
                .andReturn();

        AnalysisRunDto runV1 = objectMapper.readValue(
                v1Res.getResponse().getContentAsString(StandardCharsets.UTF_8),
                AnalysisRunDto.class
        );

        // Verify confirmed wallets: ETH 100% (85 + 15 synergy) and BTC 70%
        assertThat(runV1.confirmedAddresses()).hasSize(2);
        AddressFindingDto janekEthV1 = findByAddress(runV1.confirmedAddresses(), "0x71C7656EC7ab88b098defB751B7401B5f6d8976F");
        assertThat(janekEthV1.confidenceScore()).isEqualTo(100);
        assertThat(janekEthV1.falsePositive()).isFalse();

        AddressFindingDto janekBtc = findByAddress(runV1.confirmedAddresses(), "bc1qar0srrr7xfkvy5l643lydnw9re59gtzzwf5mdq");
        assertThat(janekBtc.confidenceScore()).isEqualTo(70);

        // Verify rejected False Positives: Uniswap V3 (DEX_ROUTER, 0%) & Binance 14 (CEX, 0%, interactionRecorded=true)
        assertThat(runV1.rejectedAddresses()).hasSize(2);
        AddressFindingDto uniswap = findByAddress(runV1.rejectedAddresses(), "0xE592427A0AEce92De3Edee1F18E0157C05861564");
        assertThat(uniswap.falsePositive()).isTrue();
        assertThat(uniswap.confidenceScore()).isZero();
        assertThat(uniswap.rejectionCategory()).isEqualTo("DEX_ROUTER");
        assertThat(uniswap.rejectionReason()).contains("bytecode != 0x");

        AddressFindingDto binance14 = findByAddress(runV1.rejectedAddresses(), "0x28C6c06298d514Db089934071355E5743bf21d60");
        assertThat(binance14.falsePositive()).isTrue();
        assertThat(binance14.confidenceScore()).isZero();
        assertThat(binance14.rejectionCategory()).isEqualTo("CEX");
        assertThat(binance14.interactionRecorded()).isTrue();

        // Verify Cash-Out signals & 3 report zones (including 19% PIT-38 tax calculator & AML_STRUCTURING_OR_2HOP)
        assertThat(runV1.cashOutSignals())
                .extracting(CashOutSignalDto::signalType)
                .contains("CEX_OFFRAMP", "STABLECOIN_CONVERSION", "P2P_OR_GATEWAY", "AML_STRUCTURING_OR_2HOP");
        assertThat(runV1.report()).isNotNull();
        assertThat(runV1.report().zone1SourceFacts()).hasSize(4);
        assertThat(runV1.report().zone2Computations().confirmedWallets()).hasSize(2);
        assertThat(runV1.report().zone2Computations().rejectedFalsePositives()).hasSize(2);
        assertThat(runV1.report().zone3VerificationConclusions().pit38RiskIndicatorsPl())
                .anySatisfy(line -> assertThat(line).contains("19%"));

        // Verify GET /api/runs/{runId} deserialization from DB
        mockMvc.perform(get("/api/runs/" + runV1.runId()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));

        // Step 2 (v2): Trigger follow-up run with snapshotStep=2 (wallet rotation!)
        TriggerRunRequest reqV2 = new TriggerRunRequest(
                AnalysisMode.SYNTHETIC,
                List.of("GitHub", "KantorP2P.pl", "WaybackArchive", "ForumKrypto.pl"),
                List.of("ETH", "BTC"),
                2
        );
        MvcResult v2Res = mockMvc.perform(post("/api/subjects/" + runV1.subjectId() + "/runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqV2)))
                .andExpect(status().isOk())
                .andReturn();

        AnalysisRunDto runV2 = objectMapper.readValue(
                v2Res.getResponse().getContentAsString(StandardCharsets.UTF_8),
                AnalysisRunDto.class
        );
        assertThat(runV2.runNumber()).isEqualTo(2);
        assertThat(runV2.snapshotStep()).isEqualTo(2);

        // Diff v1 -> v2
        MvcResult diffRes = mockMvc.perform(get("/api/subjects/" + runV1.subjectId() + "/diff")
                        .param("from", runV1.runId())
                        .param("to", runV2.runId()))
                .andExpect(status().isOk())
                .andReturn();

        RunDiffDto diff = objectMapper.readValue(
                diffRes.getResponse().getContentAsString(StandardCharsets.UTF_8),
                RunDiffDto.class
        );
        assertThat(diff.walletRotationDetected()).isTrue();
        assertThat(diff.walletRotations()).isNotEmpty();
        assertThat(diff.walletRotations().get(0).sourceName()).isEqualTo("GitHub");
        assertThat(diff.walletRotations().get(0).previousAddress())
                .isEqualTo("0x71C7656EC7ab88b098defB751B7401B5f6d8976F");
        assertThat(diff.walletRotations().get(0).newAddress())
                .isEqualTo("0xd8dA6BF26964aF9D7eEd9e03E53415D37aA96045");
        assertThat(diff.volumeDeltaPln()).isNotZero();

        // Also check diff without explicit query parameters
        mockMvc.perform(get("/api/subjects/" + runV1.subjectId() + "/diff"))
                .andExpect(status().isOk());

        // Check GET /api/subjects lists the subject with 2 runs
        MvcResult subjectsRes = mockMvc.perform(get("/api/subjects"))
                .andExpect(status().isOk())
                .andReturn();
        SubjectSummaryDto[] subjects = objectMapper.readValue(
                subjectsRes.getResponse().getContentAsString(StandardCharsets.UTF_8),
                SubjectSummaryDto[].class
        );
        assertThat(subjects)
                .anySatisfy(s -> {
                    assertThat(s.id()).isEqualTo(runV1.subjectId());
                    assertThat(s.runs()).hasSize(2);
                });

        // Export PDF & JSON
        MvcResult pdfRes = mockMvc.perform(get("/api/runs/" + runV1.runId() + "/report.pdf"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")))
                .andReturn();
        byte[] pdfBytes = pdfRes.getResponse().getContentAsByteArray();
        assertThat(pdfBytes.length).isGreaterThan(2000);
        assertThat(new String(pdfBytes, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");

        try (PDDocument pdfDoc = Loader.loadPDF(pdfBytes)) {
            String extractedText = new PDFTextStripper().getText(pdfDoc);
            assertThat(extractedText).contains("SEKCJA 1: FAKTY ŹRÓDŁOWE");
            assertThat(extractedText).contains("SEKCJA 2: WYNIKI OBLICZEŃ");
            assertThat(extractedText).contains("SEKCJA 3: WNIOSKI WYMAGAJĄCE DALSZEJ WERYFIKACJI");
            assertThat(extractedText).contains("OŁCyŚ");
        }

        // Export CASP demand letter draft PDF (art. 182 Ordynacji podatkowej)
        MvcResult caspPdfRes = mockMvc.perform(get("/api/runs/" + runV1.runId() + "/casp-request.pdf"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andReturn();
        byte[] caspPdfBytes = caspPdfRes.getResponse().getContentAsByteArray();
        assertThat(caspPdfBytes.length).isGreaterThan(1500);
        try (PDDocument caspDoc = Loader.loadPDF(caspPdfBytes)) {
            String caspText = new PDFTextStripper().getText(caspDoc);
            assertThat(caspText).contains("PROJEKT WEZWANIA");
            assertThat(caspText).contains("art. 182 Ordynacji podatkowej");
        }

        // Configure cyclic monitoring (24h CRON)
        mockMvc.perform(post("/api/subjects/" + runV1.subjectId() + "/monitoring")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true,\"intervalHours\":24}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/monitoring"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(runV1.subjectId())));

        MvcResult jsonExportRes = mockMvc.perform(get("/api/runs/" + runV1.runId() + "/report.json"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")))
                .andReturn();
        AnalysisRunDto exportedRun = objectMapper.readValue(
                jsonExportRes.getResponse().getContentAsString(StandardCharsets.UTF_8),
                AnalysisRunDto.class
        );
        assertThat(exportedRun.runId()).isEqualTo(runV1.runId());
        assertThat(exportedRun.report().zone1SourceFacts()).hasSize(4);
    }

    @Test
    void shouldExecuteScenario2ReverseOsintAndScenario3NoiseWithFalsePositives() throws Exception {
        // Scenario 2: Reverse OSINT (ETH + TRON, 85%, all 4 Cash-Out signal types including RECURRING_CASHOUT)
        CreateSubjectRequest s2Req = new CreateSubjectRequest(
                "Sprawa Reverse-OSINT: Niezgłoszony kantor OTC",
                List.of(
                        new IdentifierDto("CRYPTO_ADDRESS", "0xAb5801a7D398351b8bE11C439e05C5B3259aeC9B"),
                        new IdentifierDto("TELEGRAM_ID", "@marek_otc_kantor")
                ),
                AnalysisMode.SYNTHETIC,
                List.of("Telegram OTC", "Reddit"),
                List.of("ETH", "TRON"),
                1
        );

        MvcResult s2Res = mockMvc.perform(post("/api/subjects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(s2Req)))
                .andExpect(status().isCreated())
                .andReturn();
        AnalysisRunDto s2Run = objectMapper.readValue(
                s2Res.getResponse().getContentAsString(StandardCharsets.UTF_8),
                AnalysisRunDto.class
        );

        assertThat(s2Run.confirmedAddresses()).hasSize(2);
        assertThat(findByAddress(s2Run.confirmedAddresses(), "0xAb5801a7D398351b8bE11C439e05C5B3259aeC9B")
                .confidenceScore()).isEqualTo(85);
        assertThat(findByAddress(s2Run.confirmedAddresses(), "T9yD14Nj9j7xAB4dbGeiX9h8unkKHxuWwb")
                .confidenceScore()).isEqualTo(85);
        assertThat(s2Run.cashOutSignals())
                .extracting(CashOutSignalDto::signalType)
                .contains("CEX_OFFRAMP", "STABLECOIN_CONVERSION", "P2P_OR_GATEWAY", "RECURRING_CASHOUT");

        // Scenario 3: Noise & False Positives (20% personal + 0% Aid For Ukraine & Tornado Cash)
        CreateSubjectRequest s3Req = new CreateSubjectRequest(
                "Weryfikacja negatywna: @lucky_drop_2026",
                List.of(new IdentifierDto("NICK", "lucky_drop_2026")),
                AnalysisMode.SYNTHETIC,
                List.of("Reddit", "ForumKrypto.pl"),
                List.of("ETH"),
                1
        );

        MvcResult s3Res = mockMvc.perform(post("/api/subjects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(s3Req)))
                .andExpect(status().isCreated())
                .andReturn();
        AnalysisRunDto s3Run = objectMapper.readValue(
                s3Res.getResponse().getContentAsString(StandardCharsets.UTF_8),
                AnalysisRunDto.class
        );

        assertThat(s3Run.confirmedAddresses()).hasSize(1);
        assertThat(findByAddress(s3Run.confirmedAddresses(), "0x5aAeb6053F3E94C9b9A09f33669435E7Ef1BeAed")
                .confidenceScore()).isEqualTo(20);
        assertThat(s3Run.cashOutSignals()).isEmpty();

        assertThat(s3Run.rejectedAddresses()).hasSize(2);
        AddressFindingDto aid = findByAddress(s3Run.rejectedAddresses(), "0x165CD37b4C644C2921454429E7F9358d18A45e14");
        assertThat(aid.confidenceScore()).isZero();
        assertThat(aid.rejectionCategory()).isEqualTo("CHARITY");
        assertThat(aid.interactionRecorded()).isFalse();

        AddressFindingDto tornado = findByAddress(s3Run.rejectedAddresses(), "0xd90e2f925DA726b50C4Ed8D0Fb90Ad053324F31b");
        assertThat(tornado.confidenceScore()).isZero();
        assertThat(tornado.rejectionCategory()).isEqualTo("MIXER");
        assertThat(tornado.interactionRecorded()).isFalse();

        // Verify 404 & 400 error handling
        mockMvc.perform(get("/api/runs/run-does-not-exist"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/subjects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"\",\"identifiers\":[]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldValidateAllInputTypesAndDirectCryptoAddressScoring() throws Exception {
        // 1. Direct private EOA CRYPTO_ADDRESS without Web2 traces -> 50% DIRECT_ANALYST_INPUT
        CreateSubjectRequest directWalletReq = new CreateSubjectRequest(
                "Bezpośredni portfel prywatny (ETH)",
                List.of(new IdentifierDto("CRYPTO_ADDRESS", "0x1234567890123456789012345678901234567890")),
                AnalysisMode.SYNTHETIC,
                List.of("Synthetic"),
                List.of("ETH"),
                1
        );
        MvcResult directRes = mockMvc.perform(post("/api/subjects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(directWalletReq)))
                .andExpect(status().isCreated())
                .andReturn();
        AnalysisRunDto directRun = objectMapper.readValue(
                directRes.getResponse().getContentAsString(StandardCharsets.UTF_8),
                AnalysisRunDto.class
        );
        assertThat(directRun.confirmedAddresses()).hasSize(1);
        assertThat(directRun.confirmedAddresses().get(0).confidenceScore()).isEqualTo(50);
        assertThat(directRun.confirmedAddresses().get(0).scoreBreakdown().get(0).ruleId())
                .isEqualTo("DIRECT_ANALYST_INPUT");

        // 2. Direct CRYPTO_ADDRESS pointing to a smart contract / stablecoin -> rejected 0% FP
        CreateSubjectRequest contractReq = new CreateSubjectRequest(
                "Bezpośredni kontrakt USDT",
                List.of(new IdentifierDto("CRYPTO_ADDRESS", "0xdAC17F958D2ee523a2206206994597C13D831ec7")),
                AnalysisMode.SYNTHETIC,
                List.of("Synthetic"),
                List.of("ETH"),
                1
        );
        MvcResult contractRes = mockMvc.perform(post("/api/subjects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(contractReq)))
                .andExpect(status().isCreated())
                .andReturn();
        AnalysisRunDto contractRun = objectMapper.readValue(
                contractRes.getResponse().getContentAsString(StandardCharsets.UTF_8),
                AnalysisRunDto.class
        );
        assertThat(contractRun.confirmedAddresses()).isEmpty();
        assertThat(contractRun.rejectedAddresses()).hasSize(1);
        assertThat(contractRun.rejectedAddresses().get(0).confidenceScore()).isZero();
        assertThat(contractRun.rejectedAddresses().get(0).rejectionCategory()).isEqualTo("STABLECOIN");

        // 3. Single FULL_NAME / EMAIL / SOCIAL_LOGIN / TELEGRAM_ID matching Scenario 1 or unknown input
        for (IdentifierDto id : List.of(
                new IdentifierDto("FULL_NAME", "Jan Kowalski"),
                new IdentifierDto("EMAIL", "janek.dev@example.pl"),
                new IdentifierDto("SOCIAL_LOGIN", "krypto_janek")
        )) {
            CreateSubjectRequest singleIdReq = new CreateSubjectRequest(
                    "Test typu " + id.type(),
                    List.of(id),
                    AnalysisMode.SYNTHETIC,
                    List.of("GitHub", "KantorP2P.pl", "WaybackArchive", "ForumKrypto.pl"),
                    List.of("ETH", "BTC"),
                    1
            );
            MvcResult res = mockMvc.perform(post("/api/subjects")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(singleIdReq)))
                    .andExpect(status().isCreated())
                    .andReturn();
            AnalysisRunDto run = objectMapper.readValue(
                    res.getResponse().getContentAsString(StandardCharsets.UTF_8),
                    AnalysisRunDto.class
            );
            assertThat(run.confirmedAddresses()).hasSize(2);
        }

        // 4. Unknown identifier with no matches returns clean 201 with 0 findings and valid 3-zone report
        CreateSubjectRequest emptyMatchReq = new CreateSubjectRequest(
                "Podmiot bez śladów OSINT",
                List.of(new IdentifierDto("NICK", "nieistniejacy_uzytkownik_xyz_999")),
                AnalysisMode.SYNTHETIC,
                List.of("Synthetic"),
                List.of("ETH", "BTC", "TRON"),
                1
        );
        MvcResult emptyRes = mockMvc.perform(post("/api/subjects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(emptyMatchReq)))
                .andExpect(status().isCreated())
                .andReturn();
        AnalysisRunDto emptyRun = objectMapper.readValue(
                emptyRes.getResponse().getContentAsString(StandardCharsets.UTF_8),
                AnalysisRunDto.class
        );
        assertThat(emptyRun.confirmedAddresses()).isEmpty();
        assertThat(emptyRun.rejectedAddresses()).isEmpty();
        assertThat(emptyRun.report().zone3VerificationConclusions().pit38RiskIndicatorsPl()).isNotEmpty();
    }

    @Test
    void shouldPersistMonitoringScheduleInDbAndExecuteHeartbeatWithDiff() throws Exception {
        CreateSubjectRequest req = new CreateSubjectRequest(
                "Sprawa CRON Monitoring DB",
                List.of(
                        new IdentifierDto("NICK", "krypto_janek"),
                        new IdentifierDto("FULL_NAME", "Jan Kowalski"),
                        new IdentifierDto("EMAIL", "janek.dev@example.pl")
                ),
                AnalysisMode.SYNTHETIC,
                List.of("GitHub", "KantorP2P.pl", "WaybackArchive", "ForumKrypto.pl"),
                List.of("ETH", "BTC"),
                1
        );

        MvcResult createRes = mockMvc.perform(post("/api/subjects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();
        AnalysisRunDto run1 = objectMapper.readValue(
                createRes.getResponse().getContentAsString(StandardCharsets.UTF_8),
                AnalysisRunDto.class
        );
        String subjectId = run1.subjectId();

        mockMvc.perform(post("/api/subjects/" + subjectId + "/monitoring")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true,\"intervalHours\":24}"))
                .andExpect(status().isOk());

        MonitoringScheduleEntity persisted = monitoringScheduleRepository.findById(subjectId).orElseThrow();
        assertThat(persisted.isEnabled()).isTrue();
        assertThat(persisted.getIntervalHours()).isEqualTo(24);
        assertThat(persisted.getLastHeartbeatAt()).isNull();
        assertThat(analysisRunRepository.findBySubjectIdOrderByRunNumberAsc(subjectId)).hasSize(1);

        // First heartbeat triggers follow-up analysis (snapshotStep=2), computes diff, and records lastHeartbeatAt
        cyclicMonitoringController.monitoringHeartbeat();

        assertThat(analysisRunRepository.findBySubjectIdOrderByRunNumberAsc(subjectId)).hasSize(2);
        MonitoringScheduleEntity afterHeartbeat = monitoringScheduleRepository.findById(subjectId).orElseThrow();
        assertThat(afterHeartbeat.getLastHeartbeatAt()).isNotNull();

        MvcResult diffRes = mockMvc.perform(get("/api/subjects/" + subjectId + "/diff"))
                .andExpect(status().isOk())
                .andReturn();
        RunDiffDto diff = objectMapper.readValue(
                diffRes.getResponse().getContentAsString(StandardCharsets.UTF_8),
                RunDiffDto.class
        );
        assertThat(diff.walletRotationDetected()).isTrue();

        // Immediate second heartbeat must respect intervalHours=24 and NOT trigger a 3rd run
        cyclicMonitoringController.monitoringHeartbeat();
        assertThat(analysisRunRepository.findBySubjectIdOrderByRunNumberAsc(subjectId)).hasSize(2);

        // Disable schedule and verify persistence
        mockMvc.perform(post("/api/subjects/" + subjectId + "/monitoring")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false,\"intervalHours\":24}"))
                .andExpect(status().isOk());
        assertThat(monitoringScheduleRepository.findById(subjectId).orElseThrow().isEnabled()).isFalse();
    }

    private static AddressFindingDto findByAddress(List<AddressFindingDto> list, String address) {
        return list.stream()
                .filter(f -> f.address().equalsIgnoreCase(address))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Address not found in list: " + address));
    }
}
