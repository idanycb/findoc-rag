package com.danycb.findocAnalyzer.features.vault.application;

import com.danycb.findocAnalyzer.features.vault.application.dto.DocumentAnalysisMessage;
import com.danycb.findocAnalyzer.features.vault.application.out.AnalysisOutboxPort;
import com.danycb.findocAnalyzer.features.vault.application.out.DocumentRepositoryPort;
import com.danycb.findocAnalyzer.features.vault.application.out.ExternalStoragePort;
import com.danycb.findocAnalyzer.features.vault.domain.Document;
import com.danycb.findocAnalyzer.features.vault.domain.DocumentStatus;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class RequestDocumentAnalysisServiceTest {

    private final FakeDocumentRepository repository = new FakeDocumentRepository();
    private final RecordingStorage storage = new RecordingStorage();
    private final RecordingOutbox outbox = new RecordingOutbox();
    private final RequestDocumentAnalysisService service = new RequestDocumentAnalysisService(
            repository, storage, outbox, new VaultAuditLogger());

    private final UUID teamId = UUID.randomUUID();

    private Document doc(DocumentStatus status) {
        return repository.save(Document.builder()
                .id(UUID.randomUUID())
                .teamId(teamId)
                .fileName("report.pdf")
                .status(status)
                .build());
    }

    @Test
    void completedDocument_skipsAnalysisWithoutEnqueueing() {
        Document document = doc(DocumentStatus.COMPLETED);

        service.execute(document.getId(), teamId);

        assertThat(outbox.messages).isEmpty();
        assertThat(repository.store.get(document.getId()).getStatus()).isEqualTo(DocumentStatus.COMPLETED);
    }

    @Test
    void pendingDocument_marksPendingBuildsObjectKeyAndEnqueues() {
        Document document = doc(DocumentStatus.PENDING);

        service.execute(document.getId(), teamId);

        assertThat(repository.store.get(document.getId()).getStatus()).isEqualTo(DocumentStatus.PENDING);
        assertThat(outbox.messages).hasSize(1);
        DocumentAnalysisMessage message = outbox.messages.get(0);
        assertThat(message.documentId()).isEqualTo(document.getId());
        assertThat(message.objectKey()).isEqualTo("files/" + document.getId());
    }

    @Test
    void failedDocument_marksPendingAndEnqueuesForReanalysis() {
        Document document = doc(DocumentStatus.FAILED);

        service.execute(document.getId(), teamId);

        assertThat(repository.store.get(document.getId()).getStatus()).isEqualTo(DocumentStatus.PENDING);
        assertThat(outbox.messages).hasSize(1);
        assertThat(outbox.messages.get(0).documentId()).isEqualTo(document.getId());
    }

    @Test
    void execute_scopesLookupToOwningTeam() {
        Document document = doc(DocumentStatus.PENDING);

        service.execute(document.getId(), teamId);

        assertThat(repository.lastLookupId).isEqualTo(document.getId());
        assertThat(repository.lastLookupTeamId).isEqualTo(teamId);
    }

    static class RecordingOutbox implements AnalysisOutboxPort {
        final List<DocumentAnalysisMessage> messages = new ArrayList<>();

        @Override
        public void enqueue(DocumentAnalysisMessage message) {
            messages.add(message);
        }

        @Override public List<ClaimedAnalysisRequest> claimDue(Instant now, int limit, Duration leaseDuration) { return List.of(); }
        @Override public void markPublished(UUID outboxId, UUID claimToken, Instant publishedAt) { }
        @Override public void markFailed(UUID outboxId, UUID claimToken, Instant nextAttemptAt, String error) { }
    }

    static class RecordingStorage implements ExternalStoragePort {
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
        }

        @Override
        public String buildObjectKey(UUID docId) {
            return "files/" + docId;
        }
    }

    static class FakeDocumentRepository implements DocumentRepositoryPort {
        @Override public InsertResult insertOrGet(Document document) { return new InsertResult(save(document), true); }
        final Map<UUID, Document> store = new LinkedHashMap<>();
        UUID lastLookupId;
        UUID lastLookupTeamId;

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
                    .ticker(document.getTicker())
                    .accessionNumber(document.getAccessionNumber())
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

        @Override public Optional<Document> findByTeamIdAndAccessionNumber(UUID teamId, String accession) {
            return Optional.empty();
        }
        @Override public List<Document> findByTeamIdAndAmendsAccessionNumber(UUID teamId, String accession) {
            return List.of();
        }

        @Override
        public List<Document> findByTeamId(UUID teamId) {
            return store.values().stream().filter(d -> teamId.equals(d.getTeamId())).toList();
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
