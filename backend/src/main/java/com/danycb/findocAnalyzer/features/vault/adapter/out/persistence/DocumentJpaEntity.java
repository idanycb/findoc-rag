package com.danycb.findocAnalyzer.features.vault.adapter.out.persistence;

import com.danycb.findocAnalyzer.features.vault.domain.DocumentSource;
import com.danycb.findocAnalyzer.features.vault.domain.DocumentStatus;
import com.danycb.findocAnalyzer.features.vault.domain.AmendmentLinkStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;
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

    @Column(name = "team_id", nullable = false)
    private UUID teamId;

    @Column(nullable = false)
    private String fileName;

    private Long fileSize;

    private String contentType;

    @Column(nullable = false, updatable = false)
    private Instant uploadedAt; // UTC Time

    @Enumerated(EnumType.STRING) // Default is EnumType.ORDINAL which is dangerous
    @Column(nullable = false)
    private DocumentStatus status;

    private Instant lastAnalyzedAt;

    @Version
    private Integer version;

    private String cik;

    private String ticker;

    private String companyName;

    private String formType;

    private String baseFormType;

    @Column(name = "is_amendment", nullable = false)
    private boolean amendment;

    private String amendsAccessionNumber;

    private UUID amendsDocumentId;

    @Enumerated(EnumType.STRING)
    private AmendmentLinkStatus amendmentLinkStatus;

    @Column(nullable = false)
    @Builder.Default
    private boolean searchable = true;

    private String fiscalPeriod;

    private LocalDate reportDate;

    private LocalDate filingDate;

    private String accessionNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private DocumentSource source = DocumentSource.UPLOAD;

    private String sourceUrl;

    @PrePersist
    protected void onCreate() {
        this.uploadedAt = Instant.now();
        if (this.source == null) {
            this.source = DocumentSource.UPLOAD;
        }
    }
}
