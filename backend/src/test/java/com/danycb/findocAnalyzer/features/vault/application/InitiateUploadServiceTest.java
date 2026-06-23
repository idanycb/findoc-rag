package com.danycb.findocAnalyzer.features.vault.application;

import com.danycb.findocAnalyzer.features.vault.application.dto.DocumentUploadCommand;
import com.danycb.findocAnalyzer.features.vault.application.out.DocumentRepositoryPort;
import com.danycb.findocAnalyzer.features.vault.application.out.ExternalStoragePort;
import com.danycb.findocAnalyzer.features.vault.domain.Document;
import com.danycb.findocAnalyzer.features.vault.domain.DocumentStatus;
import com.danycb.findocAnalyzer.infra.config.DeploymentLimitsEnforcer;
import com.danycb.findocAnalyzer.infra.config.FindocLimitsProperties;
import com.danycb.findocAnalyzer.infra.config.NoOpDeploymentLimits;
import com.danycb.findocAnalyzer.infra.exception.LimitExceededException;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InitiateUploadServiceTest {

    private final FakeDocumentRepository repository = new FakeDocumentRepository();
    private final RecordingStorage storage = new RecordingStorage();
    private final InitiateUploadService service = new InitiateUploadService(
            repository, storage, new VaultAuditLogger(), new NoOpDeploymentLimits());

    private final UUID teamId = UUID.randomUUID();

    @Test
    void initiateUpload_persistsDocumentAndReturnsPresignedUrl() {
        var result = service.execute(
                new DocumentUploadCommand("report.pdf", 1024L, "application/pdf"), teamId);

        assertThat(result.fileName()).isEqualTo("report.pdf");
        assertThat(result.uploadUrl()).isEqualTo("upload-url");
        assertThat(storage.lastContentLength).isEqualTo(1024L);
        assertThat(repository.countAll()).isEqualTo(1);
    }

    @Test
    void atDocumentLimit_isRejected() {
        FindocLimitsProperties properties = new FindocLimitsProperties();
        properties.setEnabled(true);
        properties.setMaxDocuments(15);
        InitiateUploadService limitedService = new InitiateUploadService(
                repository, storage, new VaultAuditLogger(), new DeploymentLimitsEnforcer(properties));

        for (int i = 0; i < 15; i++) {
            repository.save(Document.builder()
                    .id(UUID.randomUUID())
                    .teamId(teamId)
                    .fileName("f" + i)
                    .status(DocumentStatus.PENDING)
                    .build());
        }

        assertThatThrownBy(() -> limitedService.execute(
                new DocumentUploadCommand("extra.pdf", 100L, "application/pdf"), teamId))
                .isInstanceOf(LimitExceededException.class);
    }

    @Test
    void fileSizeExceedsLimit_isRejected() {
        FindocLimitsProperties properties = new FindocLimitsProperties();
        properties.setEnabled(true);
        properties.setMaxFileSizeBytes(5242880L);
        InitiateUploadService limitedService = new InitiateUploadService(
                repository, storage, new VaultAuditLogger(), new DeploymentLimitsEnforcer(properties));

        assertThatThrownBy(() -> limitedService.execute(
                new DocumentUploadCommand("big.pdf", 5242881L, "application/pdf"), teamId))
                .isInstanceOf(LimitExceededException.class);

        assertThat(repository.countAll()).isZero();
    }

    static class FakeDocumentRepository implements DocumentRepositoryPort {
        private final Map<UUID, Document> store = new LinkedHashMap<>();

        @Override
        public Document save(Document document) {
            UUID id = document.getId() != null ? document.getId() : UUID.randomUUID();
            Document stored = Document.builder()
                    .id(id)
                    .teamId(document.getTeamId())
                    .fileName(document.getFileName())
                    .fileSize(document.getFileSize())
                    .contentType(document.getContentType())
                    .status(document.getStatus())
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

    static class RecordingStorage implements ExternalStoragePort {
        long lastContentLength;

        @Override
        public String generateUploadUrl(UUID docId, String contentType, long contentLength) {
            lastContentLength = contentLength;
            return "upload-url";
        }

        @Override
        public String generateViewUrl(UUID docId) {
            return "view-url";
        }

        @Override
        public byte[] download(String objectKey) {
            return new byte[0];
        }

        @Override
        public void delete(UUID docId) {
        }

        @Override
        public String buildObjectKey(UUID docId) {
            return "files/" + docId;
        }
    }
}
