package pl.kas.olcys.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "evidence_artifacts")
public class EvidenceArtifactEntity {

    @Id
    @Column(name = "id", length = 64, nullable = false)
    private String id;

    @Column(name = "run_id", length = 64, nullable = false)
    private String runId;

    @Column(name = "source_name", length = 128, nullable = false)
    private String sourceName;

    @Column(name = "source_category", length = 64, nullable = false)
    private String sourceCategory;

    @Column(name = "url", columnDefinition = "TEXT", nullable = false)
    private String url;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;

    @Column(name = "sha256", length = 64, nullable = false)
    private String sha256;

    @Column(name = "snippet", columnDefinition = "TEXT", nullable = false)
    private String snippet;

    @Column(name = "matched_identifier")
    private String matchedIdentifier;

    @Column(name = "context_classification", length = 64, nullable = false)
    private String contextClassification;

    public EvidenceArtifactEntity() {
    }

    public EvidenceArtifactEntity(
            String id,
            String runId,
            String sourceName,
            String sourceCategory,
            String url,
            Instant fetchedAt,
            String sha256,
            String snippet,
            String matchedIdentifier,
            String contextClassification
    ) {
        this.id = id;
        this.runId = runId;
        this.sourceName = sourceName;
        this.sourceCategory = sourceCategory;
        this.url = url;
        this.fetchedAt = fetchedAt;
        this.sha256 = sha256;
        this.snippet = snippet;
        this.matchedIdentifier = matchedIdentifier;
        this.contextClassification = contextClassification;
    }

    public String getId() { return id; }
    public String getRunId() { return runId; }
    public String getSourceName() { return sourceName; }
    public String getSourceCategory() { return sourceCategory; }
    public String getUrl() { return url; }
    public Instant getFetchedAt() { return fetchedAt; }
    public String getSha256() { return sha256; }
    public String getSnippet() { return snippet; }
    public String getMatchedIdentifier() { return matchedIdentifier; }
    public String getContextClassification() { return contextClassification; }
}
