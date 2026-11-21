package pl.kas.olcys.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "address_findings")
public class AddressFindingEntity {

    @Id
    @Column(name = "id", length = 64, nullable = false)
    private String id;

    @Column(name = "run_id", length = 64, nullable = false)
    private String runId;

    @Column(name = "address", length = 128, nullable = false)
    private String address;

    @Column(name = "network", length = 32, nullable = false)
    private String network;

    @Column(name = "confidence_score", nullable = false)
    private int confidenceScore;

    @Column(name = "is_false_positive", nullable = false)
    private boolean falsePositive;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(name = "entity_tag")
    private String entityTag;

    @Column(name = "interaction_recorded", nullable = false)
    private boolean interactionRecorded;

    public AddressFindingEntity() {
    }

    public AddressFindingEntity(
            String id,
            String runId,
            String address,
            String network,
            int confidenceScore,
            boolean falsePositive,
            String rejectionReason,
            String entityTag,
            boolean interactionRecorded
    ) {
        this.id = id;
        this.runId = runId;
        this.address = address;
        this.network = network;
        this.confidenceScore = confidenceScore;
        this.falsePositive = falsePositive;
        this.rejectionReason = rejectionReason;
        this.entityTag = entityTag;
        this.interactionRecorded = interactionRecorded;
    }

    public String getId() { return id; }
    public String getRunId() { return runId; }
    public String getAddress() { return address; }
    public String getNetwork() { return network; }
    public int getConfidenceScore() { return confidenceScore; }
    public boolean isFalsePositive() { return falsePositive; }
    public String getRejectionReason() { return rejectionReason; }
    public String getEntityTag() { return entityTag; }
    public boolean isInteractionRecorded() { return interactionRecorded; }
}
