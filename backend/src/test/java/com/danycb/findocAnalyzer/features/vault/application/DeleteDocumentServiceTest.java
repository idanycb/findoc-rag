package com.danycb.findocAnalyzer.features.vault.application;

import com.danycb.findocAnalyzer.features.vault.application.out.DocumentRepositoryPort;
import com.danycb.findocAnalyzer.features.vault.application.out.ExternalStoragePort;
import com.danycb.findocAnalyzer.features.vault.application.out.VectorIndexPort;
import com.danycb.findocAnalyzer.features.vault.domain.Document;
import com.danycb.findocAnalyzer.features.vault.domain.DocumentStatus;
import com.danycb.findocAnalyzer.features.vault.domain.ParsedSection;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

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

    static class RecordingStorage implements ExternalStoragePort {
        UUID deletedDocId;

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
        }

        @Override
        public String buildObjectKey(UUID docId) {
            return "files/" + docId;
        }
    }

    static class RecordingVectorIndex implements VectorIndexPort {
        UUID deletedDocumentId;

        @Override
        public void ingest(List<ParsedSection> sections, UUID docId, UUID teamId, String fileName) {
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
