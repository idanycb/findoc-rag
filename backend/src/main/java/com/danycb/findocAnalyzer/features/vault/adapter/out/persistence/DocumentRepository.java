package com.danycb.findocAnalyzer.features.vault.adapter.out.persistence;

import com.danycb.findocAnalyzer.features.vault.application.out.DocumentRepositoryPort;
import com.danycb.findocAnalyzer.features.vault.domain.Document;
import lombok.RequiredArgsConstructor;
import jakarta.persistence.EntityManager;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class DocumentRepository implements DocumentRepositoryPort {
    private final DocumentJpaRepository jpaRepository;
    private final EntityManager entityManager;

    @Override
    public Document save(Document document) {
        DocumentJpaEntity saved = jpaRepository.saveAndFlush(toEntity(document));
        return toDomain(saved);
    }

    @Override
    public InsertResult insertOrGet(Document document) {
        UUID id = document.getId() == null ? UUID.randomUUID() : document.getId();
        int inserted = entityManager.createNativeQuery("""
                        INSERT INTO document_metadata
                            (id, team_id, file_name, uploaded_at, status, version, source, cik, ticker,
                             company_name, form_type, base_form_type, is_amendment, amends_accession_number,
                             amends_document_id, amendment_link_status, searchable, fiscal_period, report_date,
                             filing_date, accession_number, source_url)
                        VALUES (:id, :teamId, :fileName, now(), :status, 0, :source, :cik, :ticker,
                                :companyName, :formType, :baseFormType, :amendment, :amendsAccession,
                                :amendsDocumentId, :linkStatus, :searchable, :fiscalPeriod, :reportDate,
                                :filingDate, :accession, :sourceUrl)
                        ON CONFLICT (team_id, accession_number)
                            WHERE source = 'EDGAR' AND accession_number IS NOT NULL
                        DO NOTHING
                        """)
                .setParameter("id", id)
                .setParameter("teamId", document.getTeamId())
                .setParameter("fileName", document.getFileName())
                .setParameter("status", document.getStatus().name())
                .setParameter("source", document.getSource().name())
                .setParameter("cik", document.getCik())
                .setParameter("ticker", document.getTicker())
                .setParameter("companyName", document.getCompanyName())
                .setParameter("formType", document.getFormType())
                .setParameter("baseFormType", document.getBaseFormType())
                .setParameter("amendment", document.isAmendment())
                .setParameter("amendsAccession", document.getAmendsAccessionNumber())
                .setParameter("amendsDocumentId", document.getAmendsDocumentId())
                .setParameter("linkStatus", document.getAmendmentLinkStatus().name())
                .setParameter("searchable", document.isSearchable())
                .setParameter("fiscalPeriod", document.getFiscalPeriod())
                .setParameter("reportDate", document.getReportDate())
                .setParameter("filingDate", document.getFilingDate())
                .setParameter("accession", document.getAccessionNumber())
                .setParameter("sourceUrl", document.getSourceUrl())
                .executeUpdate();
        Document persisted = inserted == 1
                ? findById(id).orElseThrow()
                : findByTeamIdAndAccessionNumber(document.getTeamId(), document.getAccessionNumber()).orElseThrow();
        return new InsertResult(persisted, inserted == 1);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean claimAnalysisPublication(UUID documentId) {
        return entityManager.createNativeQuery("""
                        UPDATE document_metadata
                        SET analysis_publication_claimed = TRUE
                        WHERE id = :documentId
                          AND status = 'PENDING'
                          AND analysis_publication_claimed = FALSE
                        """)
                .setParameter("documentId", documentId)
                .executeUpdate() == 1;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void releaseAnalysisPublication(UUID documentId) {
        entityManager.createNativeQuery("""
                        UPDATE document_metadata
                        SET analysis_publication_claimed = FALSE
                        WHERE id = :documentId
                        """)
                .setParameter("documentId", documentId)
                .executeUpdate();
    }

    @Override
    public Optional<Document> findById(UUID id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public List<Document> findByTeamId(UUID teamId) {
        return jpaRepository.findByTeamId(teamId).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public Optional<Document> findByIdAndTeamId(UUID id, UUID teamId) {
        return jpaRepository.findByIdAndTeamId(id, teamId).map(this::toDomain);
    }

    @Override
    public Optional<Document> findByTeamIdAndAccessionNumber(UUID teamId, String accessionNumber) {
        return jpaRepository.findByTeamIdAndAccessionNumber(teamId, accessionNumber).map(this::toDomain);
    }

    @Override
    public List<Document> findByTeamIdAndAmendsAccessionNumber(UUID teamId, String accessionNumber) {
        return jpaRepository.findByTeamIdAndAmendsAccessionNumber(teamId, accessionNumber).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public long countAll() {
        return jpaRepository.count();
    }

    @Override
    public void delete(Document document) {
        jpaRepository.deleteById(document.getId());
    }

    private DocumentJpaEntity toEntity(Document document) {
        return DocumentJpaEntity.builder()
                .id(document.getId())
                .teamId(document.getTeamId())
                .fileName(document.getFileName())
                .fileSize(document.getFileSize())
                .contentType(document.getContentType())
                .uploadedAt(document.getUploadedAt())
                .status(document.getStatus())
                .lastAnalyzedAt(document.getLastAnalyzedAt())
                .version(document.getVersion())
                .source(document.getSource())
                .cik(document.getCik())
                .ticker(document.getTicker())
                .companyName(document.getCompanyName())
                .formType(document.getFormType())
                .baseFormType(document.getBaseFormType())
                .amendment(document.isAmendment())
                .amendsAccessionNumber(document.getAmendsAccessionNumber())
                .amendsDocumentId(document.getAmendsDocumentId())
                .amendmentLinkStatus(document.getAmendmentLinkStatus())
                .searchable(document.isSearchable())
                .fiscalPeriod(document.getFiscalPeriod())
                .reportDate(document.getReportDate())
                .filingDate(document.getFilingDate())
                .accessionNumber(document.getAccessionNumber())
                .sourceUrl(document.getSourceUrl())
                .build();
    }

    private Document toDomain(DocumentJpaEntity entity) {
        return Document.builder()
                .id(entity.getId())
                .teamId(entity.getTeamId())
                .fileName(entity.getFileName())
                .fileSize(entity.getFileSize())
                .contentType(entity.getContentType())
                .uploadedAt(entity.getUploadedAt())
                .status(entity.getStatus())
                .lastAnalyzedAt(entity.getLastAnalyzedAt())
                .version(entity.getVersion())
                .source(entity.getSource())
                .cik(entity.getCik())
                .ticker(entity.getTicker())
                .companyName(entity.getCompanyName())
                .formType(entity.getFormType())
                .baseFormType(entity.getBaseFormType())
                .amendment(entity.isAmendment())
                .amendsAccessionNumber(entity.getAmendsAccessionNumber())
                .amendsDocumentId(entity.getAmendsDocumentId())
                .amendmentLinkStatus(entity.getAmendmentLinkStatus())
                .searchable(entity.isSearchable())
                .fiscalPeriod(entity.getFiscalPeriod())
                .reportDate(entity.getReportDate())
                .filingDate(entity.getFilingDate())
                .accessionNumber(entity.getAccessionNumber())
                .sourceUrl(entity.getSourceUrl())
                .build();
    }
}

interface DocumentJpaRepository extends JpaRepository<DocumentJpaEntity, UUID> {
    List<DocumentJpaEntity> findByTeamId(UUID teamId);

    Optional<DocumentJpaEntity> findByIdAndTeamId(UUID id, UUID teamId);

    Optional<DocumentJpaEntity> findByTeamIdAndAccessionNumber(UUID teamId, String accessionNumber);

    List<DocumentJpaEntity> findByTeamIdAndAmendsAccessionNumber(UUID teamId, String accessionNumber);
}
