package pl.kas.olcys.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "analysis_runs")
public class AnalysisRunEntity {

    @Id
    @Column(name = "id", length = 64, nullable = false)
    private String id;

    @Column(name = "subject_id", length = 64, nullable = false)
    private String subjectId;

    @Column(name = "run_number", nullable = false)
    private int runNumber;

    @Column(name = "snapshot_step", nullable = false)
    private int snapshotStep;

    @Column(name = "mode", length = 32, nullable = false)
    private String mode;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "duration_ms", nullable = false)
    private long durationMs;

    @Column(name = "validated_address_count", nullable = false)
    private int validatedAddressCount;

    @Column(name = "rejected_fp_count", nullable = false)
    private int rejectedFpCount;

    @Column(name = "max_confidence_score", nullable = false)
    private int maxConfidenceScore;

    @Column(name = "cash_out_signal_count", nullable = false)
    private int cashOutSignalCount;

    @Column(name = "total_volume_pln", nullable = false)
    private double totalVolumePln;

    @Column(name = "result_json", columnDefinition = "TEXT", nullable = false)
    private String resultJson;

    public AnalysisRunEntity() {
    }

    public AnalysisRunEntity(
            String id,
            String subjectId,
            int runNumber,
            int snapshotStep,
            String mode,
            Instant createdAt,
            long durationMs,
            int validatedAddressCount,
            int rejectedFpCount,
            int maxConfidenceScore,
            int cashOutSignalCount,
            double totalVolumePln,
            String resultJson
    ) {
        this.id = id;
        this.subjectId = subjectId;
        this.runNumber = runNumber;
        this.snapshotStep = snapshotStep;
        this.mode = mode;
        this.createdAt = createdAt;
        this.durationMs = durationMs;
        this.validatedAddressCount = validatedAddressCount;
        this.rejectedFpCount = rejectedFpCount;
        this.maxConfidenceScore = maxConfidenceScore;
        this.cashOutSignalCount = cashOutSignalCount;
        this.totalVolumePln = totalVolumePln;
        this.resultJson = resultJson;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getSubjectId() { return subjectId; }
    public void setSubjectId(String subjectId) { this.subjectId = subjectId; }
    public int getRunNumber() { return runNumber; }
    public void setRunNumber(int runNumber) { this.runNumber = runNumber; }
    public int getSnapshotStep() { return snapshotStep; }
    public void setSnapshotStep(int snapshotStep) { this.snapshotStep = snapshotStep; }
    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }
    public int getValidatedAddressCount() { return validatedAddressCount; }
    public void setValidatedAddressCount(int validatedAddressCount) { this.validatedAddressCount = validatedAddressCount; }
    public int getRejectedFpCount() { return rejectedFpCount; }
    public void setRejectedFpCount(int rejectedFpCount) { this.rejectedFpCount = rejectedFpCount; }
    public int getMaxConfidenceScore() { return maxConfidenceScore; }
    public void setMaxConfidenceScore(int maxConfidenceScore) { this.maxConfidenceScore = maxConfidenceScore; }
    public int getCashOutSignalCount() { return cashOutSignalCount; }
    public void setCashOutSignalCount(int cashOutSignalCount) { this.cashOutSignalCount = cashOutSignalCount; }
    public double getTotalVolumePln() { return totalVolumePln; }
    public void setTotalVolumePln(double totalVolumePln) { this.totalVolumePln = totalVolumePln; }
    public String getResultJson() { return resultJson; }
    public void setResultJson(String resultJson) { this.resultJson = resultJson; }
}
