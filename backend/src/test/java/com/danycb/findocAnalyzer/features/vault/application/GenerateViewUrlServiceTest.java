package com.danycb.findocAnalyzer.features.vault.application;

import com.danycb.findocAnalyzer.features.vault.application.out.DocumentRepositoryPort;
import com.danycb.findocAnalyzer.features.vault.application.out.ExternalStoragePort;
import com.danycb.findocAnalyzer.features.vault.domain.Document;
import com.danycb.findocAnalyzer.features.vault.domain.DocumentStatus;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class GenerateViewUrlServiceTest {

    private final FakeDocumentRepository repository = new FakeDocumentRepository();
    private final RecordingStorage storage = new RecordingStorage();
    private final GenerateViewUrlService service = new GenerateViewUrlService(repository, storage);

    private final UUID teamId = UUID.randomUUID();

    @Test
    void execute_fetchesDocumentForTeamThenGeneratesViewUrl() {
        Document document = repository.save(Document.builder()
                .id(UUID.randomUUID())
                .teamId(teamId)
                .fileName("report.pdf")
                .status(DocumentStatus.COMPLETED)
                .build());

        String url = service.execute(document.getId(), teamId);

        assertThat(url).isEqualTo("view-url-for-" + document.getId());
        assertThat(storage.lastViewUrlDocId).isEqualTo(document.getId());
        assertThat(repository.lastLookupId).isEqualTo(document.getId());
        assertThat(repository.lastLookupTeamId).isEqualTo(teamId);
    }

    static class RecordingStorage implements ExternalStoragePort {
        UUID lastViewUrlDocId;

        @Override
        public String generateUploadUrl(UUID docId, String contentType, long contentLength) {
            return "upload";
        }

        @Override
        public String generateViewUrl(UUID docId) {
            lastViewUrlDocId = docId;
            return "view-url-for-" + docId;
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
