package pl.kas.olcys.report;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import pl.kas.olcys.api.dto.AddressFindingDto;
import pl.kas.olcys.api.dto.AnalysisRunDto;
import pl.kas.olcys.api.dto.CashOutSignalDto;
import pl.kas.olcys.api.dto.OsintReportDto;
import pl.kas.olcys.api.dto.ReportComputationZoneDto;
import pl.kas.olcys.api.dto.ReportSourceFactDto;
import pl.kas.olcys.api.dto.ReportVerificationZoneDto;
import pl.kas.olcys.api.dto.ScoreBreakdownItemDto;
import pl.kas.olcys.api.dto.TimelineEventDto;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Generates an institutional 3-zone KAS PDF report using Apache PDFBox 3.0.3
 * with embedded DejaVu Sans fonts supporting full Polish UTF-8 diacritics.
 */
@Component
public class PdfReportGenerator {

    private static final float MARGIN_X = 42f;
    private static final float MARGIN_TOP = 46f;
    private static final float MARGIN_BOTTOM = 48f;
    private static final float PAGE_WIDTH = PDRectangle.A4.getWidth();
    private static final float PAGE_HEIGHT = PDRectangle.A4.getHeight();
    private static final float CONTENT_WIDTH = PAGE_WIDTH - 2 * MARGIN_X;

    private static final Color COLOR_NAVY = new Color(15, 41, 66);
    private static final Color COLOR_SLATE = new Color(51, 65, 85);
    private static final Color COLOR_MUTED = new Color(100, 116, 139);
    private static final Color COLOR_GREEN = new Color(21, 128, 61);
    private static final Color COLOR_AMBER = new Color(180, 83, 9);
    private static final Color COLOR_RED = new Color(185, 28, 28);
    private static final Color COLOR_LIGHT_BG = new Color(248, 250, 252);
    private static final Color COLOR_BORDER = new Color(203, 213, 225);

    private static final DateTimeFormatter UTC_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'").withZone(ZoneOffset.UTC);

    public byte[] generatePdf(AnalysisRunDto run) {
        if (run == null) {
            throw new IllegalArgumentException("Nie można wygenerować raportu PDF dla pustego uruchomienia.");
        }

        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            PDType0Font regularFont = loadClasspathFont(document, "fonts/DejaVuSans.ttf");
            PDType0Font boldFont = loadClasspathFont(document, "fonts/DejaVuSans-Bold.ttf");

            PdfCursor cursor = new PdfCursor(document, regularFont, boldFont);
            cursor.startNewPage(false);

            drawMasthead(cursor, run);
            drawExecutiveSummary(cursor, run);
            drawSection1SourceFacts(cursor, run.report());
            drawSection2Computations(cursor, run.report());
            drawSection3Conclusions(cursor, run.report());

            cursor.closeCurrentStream();
            document.save(baos);
            return baos.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Błąd podczas generowania raportu PDF dla analizy " + run.runId(), ex);
        }
    }

    public byte[] generateCaspRequestPdf(AnalysisRunDto run) {
        if (run == null) {
            throw new IllegalArgumentException("Nie można wygenerować projektu wezwania CASP dla pustego uruchomienia.");
        }

        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            PDType0Font regularFont = loadClasspathFont(document, "fonts/DejaVuSans.ttf");
            PDType0Font boldFont = loadClasspathFont(document, "fonts/DejaVuSans-Bold.ttf");

            PdfCursor c = new PdfCursor(document, regularFont, boldFont);
            c.startNewPage(false);

            float bannerHeight = 64f;
            c.ensureSpace(bannerHeight + 40f);
            c.fillRect(MARGIN_X, c.y - bannerHeight, CONTENT_WIDTH, bannerHeight, COLOR_NAVY);
            c.drawText(c.bold, 12f, Color.WHITE, MARGIN_X + 12f, c.y - 20f,
                    "PROJEKT WEZWANIA DO PODMIOTU CASP / GIEŁDY KRYPTOWALUT");
            c.drawText(c.bold, 9.5f, new Color(186, 230, 253), MARGIN_X + 12f, c.y - 36f,
                    "Podstawa: art. 182 Ordynacji podatkowej / art. 48 ustawy o KAS / Dyrektywa DAC8");
            c.drawText(c.regular, 8.5f, new Color(226, 232, 240), MARGIN_X + 12f, c.y - 52f,
                    "Sprawa: " + safeText(run.subjectDisplayName()) + " | Analiza ID: " + run.runId() + " | Data UTC: " + formatUtc(run.createdAt()));
            c.y -= (bannerHeight + 14f);

            c.drawZoneHeader("SEKCJA A: ZIDENTYFIKOWANE PORTFELE NIEHOSTOWANE PODMIOTU", COLOR_NAVY);
            if (run.confirmedAddresses() == null || run.confirmedAddresses().isEmpty()) {
                c.drawWrappedParagraph(c.regular, 8.5f, COLOR_SLATE, MARGIN_X + 4f, CONTENT_WIDTH - 8f, 11.5f,
                        "Brak zatwierdzonych portfeli niehostowanych.");
            } else {
                for (AddressFindingDto wallet : run.confirmedAddresses()) {
                    c.drawWrappedParagraph(c.bold, 8.5f, COLOR_NAVY, MARGIN_X + 4f, CONTENT_WIDTH - 8f, 11.5f,
                            String.format("• [%s] %s — Wskaźnik Pewności KAS: %d%% (%s)",
                                    wallet.network(), wallet.address(), wallet.confidenceScore(), wallet.confidenceLevelPl()));
                }
            }
            c.y -= 6f;

            c.drawZoneHeader("SEKCJA B: WYKAZ TRANSAKCJI PRZEPŁYWU DO PODMIOTÓW CASP / CEX", COLOR_AMBER);
            List<CashOutSignalDto> transferSignals = run.cashOutSignals() == null ? List.of() : run.cashOutSignals().stream()
                    .filter(s -> "CEX_OFFRAMP".equals(s.signalType()) || "P2P_OR_GATEWAY".equals(s.signalType()))
                    .toList();
            if (transferSignals.isEmpty()) {
                c.drawWrappedParagraph(c.regular, 8.5f, COLOR_SLATE, MARGIN_X + 4f, CONTENT_WIDTH - 8f, 11.5f,
                        "Nie zarejestrowano bezpośrednich transferów wychodzących na adresy giełd CEX/CASP.");
            } else {
                for (int i = 0; i < transferSignals.size(); i++) {
                    CashOutSignalDto sig = transferSignals.get(i);
                    c.drawWrappedParagraph(c.bold, 8.5f, COLOR_SLATE, MARGIN_X + 4f, CONTENT_WIDTH - 8f, 11.5f,
                            String.format("%d) Podmiot docelowy: %s (%s) | Kwota szac.: %.2f PLN | Data: %s",
                                    i + 1, safeText(sig.counterpartyName()), sig.network(), sig.estimatedPln(), formatUtc(sig.timestamp())));
                    c.drawWrappedParagraph(c.regular, 8f, COLOR_MUTED, MARGIN_X + 12f, CONTENT_WIDTH - 16f, 11f,
                            "Portfel źródłowy: " + safeText(sig.address()) + " | TX Hash: " + safeText(sig.txHash()));
                    c.y -= 2f;
                }
            }
            c.y -= 6f;

            c.drawZoneHeader("SEKCJA C: ZAKRES ŻĄDANYCH DANYCH KYC / AML I ROZLICZEŃ FIAT", COLOR_GREEN);
            for (String item : List.of(
                    "1. Pełne dane identyfikacyjne (KYC) właściciela rachunku depozytowego powiązanego ze wskazanymi powyżej transakcjami przychodzącymi (imię, nazwisko, PESEL/NIP, seria i numer dokumentu tożsamości, adres zamieszkania).",
                    "2. Wykaz wszystkich rachunków bankowych (IBAN), kart płatniczych i instrumentów BLIK/SEPA powiązanych z wypłatami walut tradycyjnych (PLN/EUR/USD) z tego konta.",
                    "3. Zestawienie wszystkich operacji wpłat i wypłat kryptowalut oraz walut tradycyjnych (wraz z datami UTC, kwotami i prowizjami) za okres objęty analizą na potrzeby weryfikacji zeznania podatkowego PIT-38 (art. 30b ustawy o PIT).",
                    "4. Adresy IP logowań oraz powiązane identyfikatory urządzenia z rejestru dostawcy usług kryptoaktywów (CASP)."
            )) {
                c.drawWrappedParagraph(c.regular, 8.5f, COLOR_SLATE, MARGIN_X + 4f, CONTENT_WIDTH - 8f, 12f, item);
                c.y -= 3f;
            }

            c.closeCurrentStream();
            document.save(baos);
            return baos.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Błąd podczas generowania projektu wezwania CASP PDF dla " + run.runId(), ex);
        }
    }

    private void drawMasthead(PdfCursor c, AnalysisRunDto run) throws IOException {
        float bannerHeight = 68f;
        c.ensureSpace(bannerHeight + 48f);

        c.fillRect(MARGIN_X, c.y - bannerHeight, CONTENT_WIDTH, bannerHeight, COLOR_NAVY);

        float textY = c.y - 20f;
        c.drawText(c.bold, 13f, Color.WHITE, MARGIN_X + 12f, textY,
                "OŁCyŚ — RAPORT ANALITYCZNY OSINT & BLOCKCHAIN (KAS)");
        textY -= 17f;
        c.drawText(c.bold, 10f, new Color(186, 230, 253), MARGIN_X + 12f, textY,
                c.fitToWidth(c.bold, 10f, "Sprawa / Podmiot: " + safeText(run.subjectDisplayName()), CONTENT_WIDTH - 24f));
        textY -= 15f;
        String metaLine = String.format(
                "Analiza #%d (Snapshot v%d) | Tryb: %s | ID: %s | Data: %s",
                run.runNumber(),
                run.snapshotStep(),
                run.mode(),
                run.runId(),
                formatUtc(run.createdAt())
        );
        c.drawText(c.regular, 8.5f, new Color(226, 232, 240), MARGIN_X + 12f, textY, metaLine);

        c.y -= (bannerHeight + 10f);

        String idsLine = run.queriedIdentifiers() == null || run.queriedIdentifiers().isEmpty()
                ? "Brak"
                : run.queriedIdentifiers().stream()
                .map(i -> i.type() + ": " + i.value())
                .reduce((a, b) -> a + " | " + b)
                .orElse("Brak");
        c.drawWrappedParagraph(c.bold, 8.5f, COLOR_SLATE, MARGIN_X, CONTENT_WIDTH, 11.5f,
                "Identyfikatory wejściowe: " + idsLine);
        c.y -= 6f;
    }

    private void drawExecutiveSummary(PdfCursor c, AnalysisRunDto run) throws IOException {
        OsintReportDto report = run.report();
        if (report == null) {
            return;
        }
        c.ensureSpace(50f);
        c.drawSubHeader("PODSUMOWANIE WYKONAWCZE (AI On-Premise: " + safeText(report.aiProviderUsed()) + ")", COLOR_NAVY);
        c.drawWrappedParagraph(c.regular, 9f, COLOR_SLATE, MARGIN_X + 6f, CONTENT_WIDTH - 12f, 12.5f,
                safeText(report.executiveSummaryPl()));
        c.y -= 10f;
    }

    private void drawSection1SourceFacts(PdfCursor c, OsintReportDto report) throws IOException {
        c.drawZoneHeader("SEKCJA 1: FAKTY ŹRÓDŁOWE (MATERIAŁ DOWODOWY OSINT)", COLOR_NAVY);

        List<ReportSourceFactDto> facts = (report != null && report.zone1SourceFacts() != null)
                ? report.zone1SourceFacts()
                : List.of();

        if (facts.isEmpty()) {
            c.drawWrappedParagraph(c.regular, 9f, COLOR_MUTED, MARGIN_X + 6f, CONTENT_WIDTH - 12f, 12f,
                    "Brak pozyskanych artefaktów źródłowych Web2 w tym uruchomieniu.");
            c.y -= 8f;
            return;
        }

        int idx = 1;
        for (ReportSourceFactDto fact : facts) {
            c.ensureSpace(72f);
            String title = String.format("Dowód #%d | Źródło: %s | Dopasowanie: %s | %s",
                    idx++,
                    safeText(fact.sourceName()),
                    safeText(fact.matchedIdentifier()),
                    formatUtc(fact.timestampUtc()));
            c.drawWrappedParagraph(c.bold, 9f, COLOR_NAVY, MARGIN_X + 4f, CONTENT_WIDTH - 8f, 12f, title);
            c.drawWrappedParagraph(c.regular, 8f, COLOR_SLATE, MARGIN_X + 10f, CONTENT_WIDTH - 16f, 11f,
                    "URL: " + safeText(fact.url()));
            c.drawWrappedParagraph(c.regular, 8f, COLOR_MUTED, MARGIN_X + 10f, CONTENT_WIDTH - 16f, 11f,
                    "SHA-256: " + safeText(fact.sha256()));
            c.drawWrappedParagraph(c.regular, 8.5f, COLOR_SLATE, MARGIN_X + 10f, CONTENT_WIDTH - 16f, 11.5f,
                    "Cytat: \"" + safeText(fact.quoteSnippet()) + "\"");
            if (fact.detectedAddresses() != null && !fact.detectedAddresses().isEmpty()) {
                c.drawWrappedParagraph(c.bold, 8f, COLOR_GREEN, MARGIN_X + 10f, CONTENT_WIDTH - 16f, 11f,
                        "Wyekstrahowane adresy: " + String.join(", ", fact.detectedAddresses()));
            }
            c.drawDivider();
        }
        c.y -= 6f;
    }

    private void drawSection2Computations(PdfCursor c, OsintReportDto report) throws IOException {
        c.drawZoneHeader("SEKCJA 2: WYNIKI OBLICZEŃ (WSKAŹNIK PEWNOŚCI, FIREWALL FP, PRZEPŁYWY)", COLOR_GREEN);

        if (report == null || report.zone2Computations() == null) {
            return;
        }
        ReportComputationZoneDto z2 = report.zone2Computations();

        c.drawWrappedParagraph(c.regular, 8.5f, COLOR_SLATE, MARGIN_X + 4f, CONTENT_WIDTH - 8f, 11.5f,
                safeText(z2.methodologySummaryPl()));
        c.y -= 4f;

        String kpiLine = String.format(
                "Łączny szac. wolumen: %s | Stablecoiny: %s USD | Transakcje on-chain: %d | Potwierdzone portfele: %d | Odrzucone FP: %d",
                formatPln(z2.totalEstimatedVolumePln()),
                formatNumber(z2.totalStablecoinVolumeUsd()),
                z2.totalTransactionsAnalyzed(),
                z2.confirmedWallets() != null ? z2.confirmedWallets().size() : 0,
                z2.rejectedFalsePositives() != null ? z2.rejectedFalsePositives().size() : 0
        );
        c.drawWrappedParagraph(c.bold, 8.5f, COLOR_NAVY, MARGIN_X + 4f, CONTENT_WIDTH - 8f, 12f, kpiLine);
        c.y -= 6f;

        // Confirmed Wallets
        c.drawSubHeader("2A. Potwierdzone portfele niehostowane (Unhosted Wallets)", COLOR_GREEN);
        List<AddressFindingDto> confirmed = z2.confirmedWallets() != null ? z2.confirmedWallets() : List.of();
        if (confirmed.isEmpty()) {
            c.drawWrappedParagraph(c.regular, 8.5f, COLOR_MUTED, MARGIN_X + 8f, CONTENT_WIDTH - 16f, 11.5f,
                    "Brak potwierdzonych portfeli niehostowanych.");
        } else {
            for (AddressFindingDto w : confirmed) {
                c.ensureSpace(48f);
                String header = String.format("[%s] %s — Confidence Score: %d%% (%s)",
                        w.network(), w.address(), w.confidenceScore(), safeText(w.confidenceLevelPl()));
                c.drawWrappedParagraph(c.bold, 8.8f, COLOR_GREEN, MARGIN_X + 6f, CONTENT_WIDTH - 12f, 12f, header);
                if (w.scoreBreakdown() != null) {
                    for (ScoreBreakdownItemDto sb : w.scoreBreakdown()) {
                        String line = String.format("  • %s (%s%d pkt): %s",
                                safeText(sb.label()),
                                sb.delta() >= 0 ? "+" : "",
                                sb.delta(),
                                safeText(sb.explanationPl()));
                        c.drawWrappedParagraph(c.regular, 8f, COLOR_SLATE, MARGIN_X + 10f, CONTENT_WIDTH - 18f, 11f, line);
                    }
                }
                if (w.onChainMetrics() != null) {
                    String mLine = String.format("  Metryki: %d tx | Przepływ: +%s / -%s %s | Wolumen: %s | Stablecoin: %s USD",
                            w.onChainMetrics().txCount(),
                            formatNumber(w.onChainMetrics().totalInflowNative()),
                            formatNumber(w.onChainMetrics().totalOutflowNative()),
                            safeText(w.onChainMetrics().nativeSymbol()),
                            formatPln(w.onChainMetrics().estimatedTotalVolumePln()),
                            formatNumber(w.onChainMetrics().stablecoinVolumeUsd()));
                    c.drawWrappedParagraph(c.regular, 8f, COLOR_MUTED, MARGIN_X + 10f, CONTENT_WIDTH - 18f, 11f, mLine);
                }
                c.y -= 4f;
            }
        }

        // Rejected False Positives
        c.y -= 4f;
        c.drawSubHeader("2B. Odrzucone False Positives — Firewall FP (0%)", COLOR_RED);
        List<AddressFindingDto> rejected = z2.rejectedFalsePositives() != null ? z2.rejectedFalsePositives() : List.of();
        if (rejected.isEmpty()) {
            c.drawWrappedParagraph(c.regular, 8.5f, COLOR_MUTED, MARGIN_X + 8f, CONTENT_WIDTH - 16f, 11.5f,
                    "Brak adresów odrzuconych przez Firewall False Positives.");
        } else {
            for (AddressFindingDto fp : rejected) {
                c.ensureSpace(40f);
                String interactionFlag = fp.interactionRecorded() ? " [ODNOTOWANO INTERAKCJĘ ON-CHAIN]" : "";
                String header = String.format("[%s] %s — Kategoria: %s | Podmiot: %s%s (0%%)",
                        fp.network(),
                        fp.address(),
                        safeText(fp.rejectionCategory()),
                        safeText(fp.entityTag()),
                        interactionFlag);
                c.drawWrappedParagraph(c.bold, 8.5f, COLOR_RED, MARGIN_X + 6f, CONTENT_WIDTH - 12f, 11.5f, header);
                c.drawWrappedParagraph(c.regular, 8f, COLOR_SLATE, MARGIN_X + 12f, CONTENT_WIDTH - 20f, 11f,
                        "Powód wykluczenia: " + safeText(fp.rejectionReason()));
                c.y -= 3f;
            }
        }

        // Chronological Flow
        List<TimelineEventDto> flow = z2.chronologicalFlow() != null ? z2.chronologicalFlow() : List.of();
        if (!flow.isEmpty()) {
            c.y -= 4f;
            c.drawSubHeader("2C. Chronologia przepływów on-chain (" + flow.size() + " zdarzeń)", COLOR_NAVY);
            for (TimelineEventDto ev : flow) {
                c.ensureSpace(24f);
                String signalBadge = ev.cashOutSignalType() != null ? " [" + ev.cashOutSignalType() + "]" : "";
                String line = String.format("%s | %s %s | %s %s (~%s)%s — %s",
                        formatUtc(ev.timestamp()),
                        safeText(ev.network()),
                        safeText(ev.direction()),
                        formatNumber(ev.amount()),
                        safeText(ev.asset()),
                        formatPln(ev.estimatedPln()),
                        signalBadge,
                        safeText(ev.counterpartyTag() != null ? ev.counterpartyTag() : ev.descriptionPl()));
                c.drawWrappedParagraph(c.regular, 7.8f, COLOR_SLATE, MARGIN_X + 6f, CONTENT_WIDTH - 12f, 10.5f, line);
            }
        }
        c.y -= 8f;
    }

    private void drawSection3Conclusions(PdfCursor c, OsintReportDto report) throws IOException {
        c.drawZoneHeader("SEKCJA 3: WNIOSKI WYMAGAJĄCE DALSZEJ WERYFIKACJI (CASH-OUT & PIT-38)", COLOR_AMBER);

        if (report == null || report.zone3VerificationConclusions() == null) {
            return;
        }
        ReportVerificationZoneDto z3 = report.zone3VerificationConclusions();

        c.drawSubHeader("3A. Sygnały Fiat Cash-Out", COLOR_AMBER);
        List<CashOutSignalDto> signals = z3.cashOutSignals() != null ? z3.cashOutSignals() : List.of();
        if (signals.isEmpty()) {
            c.drawWrappedParagraph(c.regular, 8.5f, COLOR_MUTED, MARGIN_X + 8f, CONTENT_WIDTH - 16f, 11.5f,
                    "Nie zidentyfikowano sygnałów wyjścia do waluty fiducjarnej (Fiat Cash-Out).");
        } else {
            for (CashOutSignalDto sig : signals) {
                c.ensureSpace(44f);
                String head = String.format("[%s | Priorytet: %s] %s (%s) — %s",
                        sig.signalType(),
                        sig.severity(),
                        safeText(sig.counterpartyName()),
                        sig.network(),
                        formatPln(sig.estimatedPln()));
                c.drawWrappedParagraph(c.bold, 8.5f, COLOR_AMBER, MARGIN_X + 6f, CONTENT_WIDTH - 12f, 11.5f, head);
                c.drawWrappedParagraph(c.regular, 8f, COLOR_SLATE, MARGIN_X + 12f, CONTENT_WIDTH - 20f, 11f,
                        safeText(sig.descriptionPl()));
                c.drawWrappedParagraph(c.bold, 8f, COLOR_NAVY, MARGIN_X + 12f, CONTENT_WIDTH - 20f, 11f,
                        "Wskazówka KAS: " + safeText(sig.verificationHintPl()));
                c.y -= 3f;
            }
        }

        c.y -= 4f;
        c.drawSubHeader("3B. Wskaźniki ryzyka podatkowego PIT-38", COLOR_RED);
        if (z3.pit38RiskIndicatorsPl() != null) {
            for (String ind : z3.pit38RiskIndicatorsPl()) {
                c.drawWrappedParagraph(c.regular, 8.5f, COLOR_SLATE, MARGIN_X + 8f, CONTENT_WIDTH - 16f, 11.5f,
                        "• " + safeText(ind));
            }
        }

        c.y -= 4f;
        c.drawSubHeader("3C. Zalecenia do dalszych czynności sprawdzających KAS", COLOR_NAVY);
        if (z3.investigativeRecommendationsPl() != null) {
            for (String rec : z3.investigativeRecommendationsPl()) {
                c.drawWrappedParagraph(c.regular, 8.5f, COLOR_SLATE, MARGIN_X + 8f, CONTENT_WIDTH - 16f, 11.5f,
                        "• " + safeText(rec));
            }
        }

        c.y -= 4f;
        c.drawSubHeader("3D. Zastrzeżenia prawne i ograniczenia metodyczne", COLOR_MUTED);
        if (z3.limitationsAndLegalDisclaimerPl() != null) {
            for (String note : z3.limitationsAndLegalDisclaimerPl()) {
                c.drawWrappedParagraph(c.regular, 8f, COLOR_MUTED, MARGIN_X + 8f, CONTENT_WIDTH - 16f, 10.8f,
                        "• " + safeText(note));
            }
        }
    }

    private static PDType0Font loadClasspathFont(PDDocument doc, String classpathLocation) throws IOException {
        ClassPathResource resource = new ClassPathResource(classpathLocation);
        try (InputStream is = resource.getInputStream()) {
            return PDType0Font.load(doc, is, true);
        }
    }

    private static String safeText(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace('\r', ' ').replace('\n', ' ').replace('\t', ' ').trim();
    }

    private static String formatUtc(Instant instant) {
        return instant == null ? "Brak daty UTC" : UTC_FMT.format(instant);
    }

    private static String formatPln(double value) {
        NumberFormat nf = NumberFormat.getNumberInstance(Locale.forLanguageTag("pl-PL"));
        nf.setMinimumFractionDigits(2);
        nf.setMaximumFractionDigits(2);
        return nf.format(value) + " PLN";
    }

    private static String formatNumber(double value) {
        NumberFormat nf = NumberFormat.getNumberInstance(Locale.forLanguageTag("pl-PL"));
        nf.setMinimumFractionDigits(2);
        nf.setMaximumFractionDigits(4);
        return nf.format(value);
    }

    /**
     * Stateful page cursor managing automatic page breaks, wrapping, and font-glyph sanitization.
     */
    private static final class PdfCursor {
        private final PDDocument doc;
        private final PDType0Font regular;
        private final PDType0Font bold;
        private PDPageContentStream stream;
        private int pageNumber = 0;
        private float y = PAGE_HEIGHT - MARGIN_TOP;

        private PdfCursor(PDDocument doc, PDType0Font regular, PDType0Font bold) {
            this.doc = doc;
            this.regular = regular;
            this.bold = bold;
        }

        private void startNewPage(boolean drawRunningHeader) throws IOException {
            closeCurrentStream();
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            pageNumber++;
            stream = new PDPageContentStream(doc, page);
            y = PAGE_HEIGHT - MARGIN_TOP;

            // Footer
            drawText(regular, 7.5f, COLOR_MUTED, MARGIN_X, 24f,
                    "OŁCyŚ (KAS) — Wygenerowano lokalnie On-Premise | Dokument pomocniczy do czynności analitycznych");
            drawText(regular, 7.5f, COLOR_MUTED, PAGE_WIDTH - MARGIN_X - 48f, 24f,
                    "Strona " + pageNumber);

            if (drawRunningHeader) {
                drawText(bold, 8f, COLOR_MUTED, MARGIN_X, PAGE_HEIGHT - 26f,
                        "OŁCyŚ — Raport Analityczny OSINT & Blockchain (ciąg dalszy)");
                y = PAGE_HEIGHT - MARGIN_TOP;
            }
        }

        private void closeCurrentStream() throws IOException {
            if (stream != null) {
                stream.close();
                stream = null;
            }
        }

        private void ensureSpace(float neededHeight) throws IOException {
            if (y - neededHeight < MARGIN_BOTTOM) {
                startNewPage(true);
            }
        }

        private void drawZoneHeader(String title, Color accentColor) throws IOException {
            ensureSpace(34f);
            fillRect(MARGIN_X, y - 20f, CONTENT_WIDTH, 20f, COLOR_LIGHT_BG);
            fillRect(MARGIN_X, y - 20f, 4f, 20f, accentColor);
            drawText(bold, 10.5f, COLOR_NAVY, MARGIN_X + 10f, y - 14f, title);
            y -= 28f;
        }

        private void drawSubHeader(String title, Color color) throws IOException {
            ensureSpace(22f);
            drawText(bold, 9.5f, color, MARGIN_X + 2f, y - 10f, title);
            y -= 17f;
        }

        private void drawDivider() throws IOException {
            ensureSpace(8f);
            stream.setStrokingColor(COLOR_BORDER);
            stream.setLineWidth(0.5f);
            stream.moveTo(MARGIN_X, y - 2f);
            stream.lineTo(MARGIN_X + CONTENT_WIDTH, y - 2f);
            stream.stroke();
            y -= 8f;
        }

        private void fillRect(float x, float bottomY, float width, float height, Color color) throws IOException {
            stream.setNonStrokingColor(color);
            stream.addRect(x, bottomY, width, height);
            stream.fill();
        }

        private void drawText(PDType0Font font, float size, Color color, float x, float baselineY, String text)
                throws IOException {
            String safe = sanitizeForFont(font, text);
            stream.beginText();
            stream.setFont(font, size);
            stream.setNonStrokingColor(color);
            stream.newLineAtOffset(x, baselineY);
            stream.showText(safe);
            stream.endText();
        }

        private void drawWrappedParagraph(
                PDType0Font font,
                float fontSize,
                Color color,
                float x,
                float maxWidth,
                float lineHeight,
                String text
        ) throws IOException {
            List<String> lines = wrapText(font, fontSize, maxWidth, sanitizeForFont(font, text));
            for (String line : lines) {
                ensureSpace(lineHeight + 2f);
                drawText(font, fontSize, color, x, y - fontSize, line);
                y -= lineHeight;
            }
        }

        private List<String> wrapText(PDType0Font font, float fontSize, float maxWidth, String text) throws IOException {
            if (text == null || text.isEmpty()) {
                return List.of("");
            }
            List<String> lines = new ArrayList<>();
            String[] words = text.split("\\s+");
            StringBuilder current = new StringBuilder();

            for (String word : words) {
                if (word.isEmpty()) {
                    continue;
                }
                String candidate = current.isEmpty() ? word : current + " " + word;
                if (measureWidth(font, fontSize, candidate) <= maxWidth) {
                    current.setLength(0);
                    current.append(candidate);
                } else {
                    if (!current.isEmpty()) {
                        lines.add(current.toString());
                        current.setLength(0);
                    }
                    if (measureWidth(font, fontSize, word) <= maxWidth) {
                        current.append(word);
                    } else {
                        // Break very long tokens (URLs, SHA-256 hashes, crypto addresses) cleanly
                        StringBuilder chunk = new StringBuilder();
                        for (int i = 0; i < word.length(); i++) {
                            char ch = word.charAt(i);
                            if (measureWidth(font, fontSize, chunk.toString() + ch) > maxWidth && !chunk.isEmpty()) {
                                lines.add(chunk.toString());
                                chunk.setLength(0);
                            }
                            chunk.append(ch);
                        }
                        if (!chunk.isEmpty()) {
                            current.append(chunk);
                        }
                    }
                }
            }
            if (!current.isEmpty()) {
                lines.add(current.toString());
            }
            return lines.isEmpty() ? List.of("") : lines;
        }

        private float measureWidth(PDType0Font font, float fontSize, String text) throws IOException {
            return (font.getStringWidth(text) / 1000f) * fontSize;
        }

        private String fitToWidth(PDType0Font font, float fontSize, String text, float maxWidth) throws IOException {
            String safe = sanitizeForFont(font, text);
            if (measureWidth(font, fontSize, safe) <= maxWidth) {
                return safe;
            }
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < safe.length(); i++) {
                char ch = safe.charAt(i);
                if (measureWidth(font, fontSize, sb.toString() + ch + "...") > maxWidth && !sb.isEmpty()) {
                    break;
                }
                sb.append(ch);
            }
            return sb + "...";
        }

        private static String sanitizeForFont(PDType0Font font, String raw) {
            if (raw == null || raw.isEmpty()) {
                return "";
            }
            String normalized = raw.replace('\r', ' ').replace('\n', ' ').replace('\t', ' ');
            StringBuilder sb = new StringBuilder(normalized.length());
            int i = 0;
            while (i < normalized.length()) {
                int cp = normalized.codePointAt(i);
                int charCount = Character.charCount(cp);
                String ch = new String(Character.toChars(cp));
                try {
                    font.encode(ch);
                    sb.append(ch);
                } catch (Exception ex) {
                    sb.append('?');
                }
                i += charCount;
            }
            return sb.toString();
        }
    }
}
