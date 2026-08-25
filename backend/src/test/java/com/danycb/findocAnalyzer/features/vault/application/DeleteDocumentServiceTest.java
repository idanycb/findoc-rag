package com.danycb.findocAnalyzer.features.vault.application;

import com.danycb.findocAnalyzer.features.vault.application.out.DocumentRepositoryPort;
import com.danycb.findocAnalyzer.features.vault.application.out.ExternalStoragePort;
import com.danycb.findocAnalyzer.features.vault.application.out.VectorIndexPort;
import com.danycb.findocAnalyzer.features.vault.domain.Document;
import com.danycb.findocAnalyzer.features.vault.domain.DocumentStatus;
import com.danycb.findocAnalyzer.features.vault.domain.ParsedSection;
import com.danycb.findocAnalyzer.features.vault.domain.DocumentSource;
import com.danycb.findocAnalyzer.features.vault.domain.AmendmentLinkStatus;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeleteDocumentServiceTest {

    private final FakeDocumentRepository repository = new FakeDocumentRepository();
    private final RecordingStorage storage = new RecordingStorage();
    private final RecordingVectorIndex vectorIndex = new RecordingVectorIndex();
    private final DeleteDocumentService service = new DeleteDocumentService(
            repository, storage, vectorIndex, new VaultAuditLogger());

    private final UUID teamId = UUID.randomUUID();

    @Test
    void execute_deletesFromRepositoryVectorIndexAndObjectStorage() {
        Document document = repository.save(Document.builder()
                .id(UUID.randomUUID())
                .teamId(teamId)
                .fileName("report.pdf")
                .status(DocumentStatus.COMPLETED)
                .build());

        service.execute(document.getId(), teamId);

        assertThat(repository.store).doesNotContainKey(document.getId());
        assertThat(vectorIndex.deletedDocumentId).isEqualTo(document.getId());
        assertThat(storage.deletedDocId).isEqualTo(document.getId());
    }

    @Test
    void execute_scopesLookupToOwningTeam() {
        Document document = repository.save(Document.builder()
                .id(UUID.randomUUID())
                .teamId(teamId)
                .fileName("report.pdf")
                .status(DocumentStatus.COMPLETED)
                .build());

        service.execute(document.getId(), teamId);

        assertThat(repository.lastLookupId).isEqualTo(document.getId());
        assertThat(repository.lastLookupTeamId).isEqualTo(teamId);
    }

    @Test
    void deletingOriginalLeavesDependentAmendmentsExplicitlyUnresolved() {
        Document original = repository.save(Document.builder()
                .id(UUID.randomUUID())
                .teamId(teamId)
                .fileName("AAPL 10-K")
                .status(DocumentStatus.COMPLETED)
                .source(DocumentSource.EDGAR)
                .formType("10-K")
                .baseFormType("10-K")
                .accessionNumber("original")
                .amendmentLinkStatus(AmendmentLinkStatus.NOT_APPLICABLE)
                .build());
        Document amendment = repository.save(Document.builder()
                .id(UUID.randomUUID())
                .teamId(teamId)
                .fileName("AAPL 10-K/A")
                .status(DocumentStatus.COMPLETED)
                .source(DocumentSource.EDGAR)
                .formType("10-K/A")
                .baseFormType("10-K")
                .amendment(true)
                .accessionNumber("amendment")
                .amendsAccessionNumber("original")
                .amendsDocumentId(original.getId())
                .amendmentLinkStatus(AmendmentLinkStatus.LINKED)
                .build());

        service.execute(original.getId(), teamId);

        Document remaining = repository.store.get(amendment.getId());
        assertThat(remaining).isNotNull();
        assertThat(remaining.getAmendsAccessionNumber()).isEqualTo("original");
        assertThat(remaining.getAmendsDocumentId()).isNull();
        assertThat(remaining.getAmendmentLinkStatus()).isEqualTo(AmendmentLinkStatus.UNRESOLVED);
    }

    @Test
    void externalStorageDeletionRunsOnlyAfterMetadataAndVectorsCommit() {
        Document document = repository.save(Document.builder()
                .id(UUID.randomUUID())
                .teamId(teamId)
                .fileName("report.pdf")
                .status(DocumentStatus.COMPLETED)
                .build());
        storage.failure = new IllegalStateException("storage unavailable after commit");
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.execute(document.getId(), teamId);

            assertThat(repository.store).doesNotContainKey(document.getId());
            assertThat(vectorIndex.deletedDocumentId).isEqualTo(document.getId());
            assertThat(storage.deletedDocId).as("external deletion must wait for commit").isNull();

            assertThatThrownBy(() -> TransactionSynchronizationManager.getSynchronizations()
                    .forEach(synchronization -> synchronization.afterCommit()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("storage unavailable");
            assertThat(repository.store).doesNotContainKey(document.getId());
            assertThat(vectorIndex.deletedDocumentId).isEqualTo(document.getId());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    static class RecordingStorage implements ExternalStoragePort {
        UUID deletedDocId;
        RuntimeException failure;

        @Override
        public String generateUploadUrl(UUID docId, String contentType, long contentLength) {
            return "upload";
        }

        @Override
        public String generateViewUrl(UUID docId) {
            return "view";
        }

        @Override
        public byte[] download(String objectKey) {
            return "bytes".getBytes();
        }

        @Override
        public void delete(UUID docId) {
            deletedDocId = docId;
            if (failure != null) {
                throw failure;
            }
        }

        @Override
        public String buildObjectKey(UUID docId) {
            return "files/" + docId;
        }
    }

    static class RecordingVectorIndex implements VectorIndexPort {
        UUID deletedDocumentId;

        @Override
        public void ingest(List<ParsedSection> sections, Document document) {
        }

        @Override
        public void deleteByDocumentId(UUID docId) {
            deletedDocumentId = docId;
        }
    }

    static class FakeDocumentRepository implements DocumentRepositoryPort {
        final Map<UUID, Document> store = new LinkedHashMap<>();
        UUID lastLookupId;
        UUID lastLookupTeamId;

        @Override public InsertResult insertOrGet(Document document) { return new InsertResult(save(document), true); }
        @Override public boolean claimAnalysisPublication(UUID documentId) { return true; }
        @Override public void releaseAnalysisPublication(UUID documentId) { }

        @Override
        public Document save(Document document) {
            UUID id = document.getId() == null ? UUID.randomUUID() : document.getId();
            Document stored = Document.builder()
                    .id(id)
                    .teamId(document.getTeamId())
                    .fileName(document.getFileName())
                    .fileSize(document.getFileSize())
                    .contentType(document.getContentType())
                    .uploadedAt(document.getUploadedAt())
                    .status(document.getStatus())
                    .lastAnalyzedAt(document.getLastAnalyzedAt())
                    .source(document.getSource())
                    .formType(document.getFormType())
                    .baseFormType(document.getBaseFormType())
                    .amendment(document.isAmendment())
                    .ticker(document.getTicker())
                    .accessionNumber(document.getAccessionNumber())
                    .amendsAccessionNumber(document.getAmendsAccessionNumber())
                    .amendsDocumentId(document.getAmendsDocumentId())
                    .amendmentLinkStatus(document.getAmendmentLinkStatus())
                    .build();
            store.put(id, stored);
            return stored;
        }

        @Override
        public Optional<Document> findById(UUID id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public Optional<Document> findByIdAndTeamId(UUID id, UUID teamId) {
            lastLookupId = id;
            lastLookupTeamId = teamId;
            return Optional.ofNullable(store.get(id)).filter(d -> teamId.equals(d.getTeamId()));
        }

        @Override
        public List<Document> findByTeamId(UUID teamId) {
            return store.values().stream().filter(d -> teamId.equals(d.getTeamId())).toList();
        }

        @Override
        public Optional<Document> findByTeamIdAndAccessionNumber(UUID teamId, String accessionNumber) {
            return store.values().stream()
                    .filter(document -> teamId.equals(document.getTeamId()))
                    .filter(document -> accessionNumber.equals(document.getAccessionNumber()))
                    .findFirst();
        }

        @Override
        public List<Document> findByTeamIdAndAmendsAccessionNumber(UUID teamId, String accessionNumber) {
            return store.values().stream()
                    .filter(document -> teamId.equals(document.getTeamId()))
                    .filter(document -> accessionNumber.equals(document.getAmendsAccessionNumber()))
                    .toList();
        }

        @Override
        public long countAll() {
            return store.size();
        }

        @Override
        public void delete(Document document) {
            store.remove(document.getId());
        }
    }
}
