package pl.kas.olcys.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pl.kas.olcys.api.dto.AnalysisRunDto;
import pl.kas.olcys.api.dto.CreateSubjectRequest;
import pl.kas.olcys.api.dto.RunDiffDto;
import pl.kas.olcys.api.dto.SubjectSummaryDto;
import pl.kas.olcys.api.dto.TriggerRunRequest;
import pl.kas.olcys.engine.AnalysisOrchestrator;

import java.util.List;

@RestController
@RequestMapping("/api/subjects")
@Tag(name = "Podmioty i Sprawy KAS", description = "Zarządzanie sprawami OSINT, uruchamianie analiz cyklicznych oraz porównywanie zmian w czasie")
public class SubjectController {

    private final AnalysisOrchestrator analysisOrchestrator;

    @Autowired
    public SubjectController(AnalysisOrchestrator analysisOrchestrator) {
        this.analysisOrchestrator = analysisOrchestrator;
    }

    @PostMapping
    @Operation(summary = "Tworzy nowy podmiot i uruchamia pierwszą równoległą analizę OSINT & Web3")
    public ResponseEntity<AnalysisRunDto> createSubject(@Valid @RequestBody CreateSubjectRequest request) {
        AnalysisRunDto run = analysisOrchestrator.createSubjectAndRun(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(run);
    }

    @PostMapping("/{id}/runs")
    @Operation(summary = "Uruchamia kolejną cykliczną analizę kontrolną dla istniejącego podmiotu")
    public AnalysisRunDto triggerRun(
            @PathVariable String id,
            @RequestBody(required = false) TriggerRunRequest request
    ) {
        return analysisOrchestrator.triggerRun(id, request);
    }

    @GetMapping
    @Operation(summary = "Zwraca listę zapisanych spraw wraz z historią uruchomień")
    public List<SubjectSummaryDto> listSubjects() {
        return analysisOrchestrator.listSubjects();
    }

    @GetMapping("/{id}/diff")
    @Operation(summary = "Porównuje dwa uruchomienia analizy i wykrywa podmianę adresu portfela na profilu Web2")
    public RunDiffDto diffRuns(
            @PathVariable String id,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to
    ) {
        return analysisOrchestrator.diffRuns(id, from, to);
    }
}
