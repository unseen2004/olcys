package pl.kas.olcys.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.kas.olcys.api.dto.AnalysisRunDto;
import pl.kas.olcys.api.dto.ScenarioPresetDto;
import pl.kas.olcys.connector.web2.ScenarioProvider;
import pl.kas.olcys.engine.AnalysisOrchestrator;
import pl.kas.olcys.report.PdfReportGenerator;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api")
@Tag(name = "Analizy, Raporty i Scenariusze", description = "Pobieranie wyników analiz, eksport raportów 3-strefowych PDF/JSON oraz scenariusze walidacyjne KAS")
public class AnalysisController {

    private final AnalysisOrchestrator analysisOrchestrator;
    private final PdfReportGenerator pdfReportGenerator;
    private final ScenarioProvider scenarioProvider;

    @Autowired
    public AnalysisController(
            AnalysisOrchestrator analysisOrchestrator,
            PdfReportGenerator pdfReportGenerator,
            ScenarioProvider scenarioProvider
    ) {
        this.analysisOrchestrator = analysisOrchestrator;
        this.pdfReportGenerator = pdfReportGenerator;
        this.scenarioProvider = scenarioProvider;
    }

    @GetMapping("/runs/{runId}")
    @Operation(summary = "Pobiera pełny wynik zapisanej analizy OSINT i blockchain")
    public AnalysisRunDto getRun(@PathVariable String runId) {
        return analysisOrchestrator.getRun(runId);
    }

    @GetMapping(value = "/runs/{runId}/report.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Generuje i pobiera 3-strefowy raport PDF (Apache PDFBox 3.0.3 z polskimi znakami)")
    public ResponseEntity<byte[]> downloadPdfReport(@PathVariable String runId) {
        AnalysisRunDto run = analysisOrchestrator.getRun(runId);
        byte[] pdfBytes = pdfReportGenerator.generatePdf(run);
        String safeId = sanitizeFilenameToken(run.runId());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename("olcys-raport-" + safeId + ".pdf", StandardCharsets.UTF_8)
                .build());
        headers.setContentLength(pdfBytes.length);

        return ResponseEntity.ok()
                .headers(headers)
                .body(pdfBytes);
    }

    @GetMapping(value = "/runs/{runId}/casp-request.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Generuje projekt wezwania organu KAS do podmiotu CASP / giełdy (art. 182 Ordynacji podatkowej)")
    public ResponseEntity<byte[]> downloadCaspRequestPdf(@PathVariable String runId) {
        AnalysisRunDto run = analysisOrchestrator.getRun(runId);
        byte[] pdfBytes = pdfReportGenerator.generateCaspRequestPdf(run);
        String safeId = sanitizeFilenameToken(run.runId());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename("olcys-wezwanie-casp-" + safeId + ".pdf", StandardCharsets.UTF_8)
                .build());
        headers.setContentLength(pdfBytes.length);

        return ResponseEntity.ok()
                .headers(headers)
                .body(pdfBytes);
    }

    @GetMapping(value = "/runs/{runId}/report.json", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Eksportuje kompletny materiał dowodowy i raport 3-strefowy jako plik JSON")
    public ResponseEntity<AnalysisRunDto> downloadJsonReport(@PathVariable String runId) {
        AnalysisRunDto run = analysisOrchestrator.getRun(runId);
        String safeId = sanitizeFilenameToken(run.runId());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename("olcys-raport-" + safeId + ".json", StandardCharsets.UTF_8)
                .build());

        return ResponseEntity.ok()
                .headers(headers)
                .body(run);
    }

    @GetMapping("/scenarios")
    @Operation(summary = "Zwraca 3 wbudowane zanonimizowane scenariusze walidacyjne KAS")
    public List<ScenarioPresetDto> listScenarios() {
        return scenarioProvider.getScenarios();
    }

    private static String sanitizeFilenameToken(String raw) {
        if (raw == null || raw.isBlank()) {
            return "analiza";
        }
        return raw.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
