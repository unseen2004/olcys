package pl.kas.olcys.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "subjects")
public class SubjectEntity {

    @Id
    @Column(name = "id", length = 64, nullable = false)
    private String id;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "identifiers_json", columnDefinition = "TEXT", nullable = false)
    private String identifiersJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public SubjectEntity() {
    }

    public SubjectEntity(String id, String displayName, String identifiersJson, Instant createdAt) {
        this.id = id;
        this.displayName = displayName;
        this.identifiersJson = identifiersJson;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getIdentifiersJson() {
        return identifiersJson;
    }

    public void setIdentifiersJson(String identifiersJson) {
        this.identifiersJson = identifiersJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = Instant.parse(createdAt);
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
