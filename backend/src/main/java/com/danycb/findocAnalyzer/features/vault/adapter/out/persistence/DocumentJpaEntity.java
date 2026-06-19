package com.danycb.findocAnalyzer.features.vault.adapter.out.persistence;

import com.danycb.findocAnalyzer.features.vault.domain.DocumentStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "document_metadata")
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DocumentJpaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String fileName;

    private Long fileSize;

    private String contentType;

    @Column(nullable = false, updatable = false)
    private Instant uploadedAt; // UTC Time

    @Enumerated(EnumType.STRING) // Default is EnumType.ORDINAL which is dangerous
    @Column(nullable = false)
    private DocumentStatus status;

    @Column(columnDefinition = "TEXT")
    private String aiSummary;

    private Instant lastAnalyzedAt;

    @Version
    private Integer version;

    @PrePersist
    protected void onCreate() {
        this.uploadedAt = Instant.now();
    }
}
