package pl.kas.olcys.report;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import pl.kas.olcys.api.dto.AddressFindingDto;
import pl.kas.olcys.api.dto.AnalysisMode;
import pl.kas.olcys.api.dto.AnalysisRunDto;
import pl.kas.olcys.api.dto.CashOutSignalDto;
import pl.kas.olcys.api.dto.EvidenceGraphDto;
import pl.kas.olcys.api.dto.IdentifierDto;
import pl.kas.olcys.api.dto.OnChainMetricsDto;
import pl.kas.olcys.api.dto.OsintReportDto;
import pl.kas.olcys.api.dto.ReportComputationZoneDto;
import pl.kas.olcys.api.dto.ReportSourceFactDto;
import pl.kas.olcys.api.dto.ReportVerificationZoneDto;
import pl.kas.olcys.api.dto.ScoreBreakdownItemDto;
import pl.kas.olcys.api.dto.TimelineEventDto;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PdfReportGeneratorTest {

    private final PdfReportGenerator generator = new PdfReportGenerator();

    @Test
    void shouldRenderAllPolishDiacriticsPaginateAndSanitizeUnsupportedEmoji() throws Exception {
        String polishPangram = "Zażółć gęślą jaźń — ZAŻÓŁĆ GĘŚLĄ JAŹŃ (ą ć ę ł ń ó ś ź ż Ą Ć Ę Ł Ń Ó Ś Ź Ż)";

        List<ReportSourceFactDto> facts = new ArrayList<>();
        for (int i = 1; i <= 16; i++) {
            facts.add(new ReportSourceFactDto(
                    "ev-" + i,
                    "Źródło #" + i,
                    "https://example.pl/bardzo/dlugi/adres/url/dla/testu/lamania/wierszy/" + i + "/" + "a".repeat(60),
                    Instant.parse("2026-03-14T12:00:00Z"),
                    "f".repeat(64),
                    "@krypto_janek",
                    polishPangram + " 🚀✨",
                    List.of("0x71C7656EC7ab88b098defB751B7401B5f6d8976F")
            ));
        }

        AddressFindingDto confirmed = new AddressFindingDto(
                "addr-1",
                "0x71C7656EC7ab88b098defB751B7401B5f6d8976F",
                "ETH",
                "EIP-55",
                true,
                100,
                "BARDZO WYSOKIE PRAWDOPODOBIEŃSTWO",
                false,
                null,
                null,
                null,
                false,
                List.of(new ScoreBreakdownItemDto("OWN_PROFILE_VERIFIED", "Oficjalny profil", 85, List.of("ev-1"), polishPangram)),
                List.of("ev-1"),
                new OnChainMetricsDto(
                        "0x71C7656EC7ab88b098defB751B7401B5f6d8976F",
                        "ETH", false, "0x", 34, 7.8, 6.8, "ETH", 112400.0, 12100.0,
                        List.of("Binance 14"),
                        List.of(new TimelineEventDto(
                                "0x" + "1".repeat(64),
                                Instant.parse("2026-03-12T17:40:00Z"),
                                "ETH", "OUT",
                                "0x71C7656EC7ab88b098defB751B7401B5f6d8976F",
                                "0x28C6c06298d514Db089934071355E5743bf21d60",
                                1.85, "ETH", 20350.0, "Binance 14", "CEX", "CEX_OFFRAMP", polishPangram
                        ))
                )
        );

        AddressFindingDto rejected = new AddressFindingDto(
                "addr-2",
                "0x28C6c06298d514Db089934071355E5743bf21d60",
                "ETH", "EIP-55", true, 0, "WYKLUCZENIE / FALSE POSITIVE (0%)", true,
                "Adres giełdy Binance 14 — " + polishPangram, "CEX", "Binance 14 (Hot Wallet)", true,
                List.of(), List.of("ev-1"), null
        );

        CashOutSignalDto signal = new CashOutSignalDto(
                "sig-1", "CEX_OFFRAMP", "HIGH",
                "0x71C7656EC7ab88b098defB751B7401B5f6d8976F", "ETH",
                "0x" + "1".repeat(64), Instant.parse("2026-03-12T17:40:00Z"),
                20350.0, "Binance 14", polishPangram, "Weryfikacja PIT-38 i KYC"
        );

        OsintReportDto report = new OsintReportDto(
                "Podsumowanie wykonawcze: " + polishPangram,
                "local-rules",
                Instant.now(),
                facts,
                new ReportComputationZoneDto(
                        "Metodyka KAS: " + polishPangram,
                        List.of(confirmed),
                        List.of(rejected),
                        112400.0,
                        12100.0,
                        34,
                        confirmed.onChainMetrics().timeline()
                ),
                new ReportVerificationZoneDto(
                        List.of(signal),
                        List.of("Wskaźnik PIT-38: " + polishPangram),
                        List.of("Zalecenie KAS: " + polishPangram),
                        List.of("Zastrzeżenie: " + polishPangram)
                )
        );

        AnalysisRunDto run = new AnalysisRunDto(
                "run-pdf-test",
                "subj-pdf-test",
                "Sprawa Testowa KAS — " + polishPangram,
                1,
                1,
                AnalysisMode.SYNTHETIC,
                Instant.parse("2026-03-20T10:00:00Z"),
                120L,
                List.of(new IdentifierDto("NICK", "krypto_janek")),
                List.of("GitHub"),
                List.of("ETH"),
                List.of(),
                List.of(confirmed),
                List.of(rejected),
                List.of(signal),
                new EvidenceGraphDto(List.of(), List.of()),
                report
        );

        byte[] pdfBytes = generator.generatePdf(run);
        assertThat(pdfBytes.length).isGreaterThan(3000);
        assertThat(new String(pdfBytes, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");

        try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
            assertThat(doc.getNumberOfPages()).isGreaterThanOrEqualTo(2);
            String text = new PDFTextStripper().getText(doc);
            assertThat(text).contains("Zażółć gęślą jaźń");
            assertThat(text).contains("ZAŻÓŁĆ GĘŚLĄ JAŹŃ");
            assertThat(text).contains("SEKCJA 1: FAKTY ŹRÓDŁOWE");
            assertThat(text).contains("SEKCJA 2: WYNIKI OBLICZEŃ");
            assertThat(text).contains("SEKCJA 3: WNIOSKI WYMAGAJĄCE DALSZEJ WERYFIKACJI");
        }
    }
}
