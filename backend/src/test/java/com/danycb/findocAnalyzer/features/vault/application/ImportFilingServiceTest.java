package com.danycb.findocAnalyzer.features.vault.application;

import com.danycb.findocAnalyzer.features.vault.application.dto.DocumentAnalysisMessage;
import com.danycb.findocAnalyzer.features.vault.application.dto.ImportFilingCommand;
import com.danycb.findocAnalyzer.features.vault.application.out.AnalysisQueuePort;
import com.danycb.findocAnalyzer.features.vault.application.out.DocumentRepositoryPort;
import com.danycb.findocAnalyzer.features.vault.domain.Document;
import com.danycb.findocAnalyzer.features.vault.domain.DocumentSource;
import com.danycb.findocAnalyzer.features.vault.domain.DocumentStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ImportFilingServiceTest {
    private final FakeDocumentRepository repository = new FakeDocumentRepository();
    private final RecordingQueue queue = new RecordingQueue();
    private final ImportFilingService service = new ImportFilingService(repository, queue, new VaultAuditLogger());

    @Test
    void importFiling_createsEdgarDocumentAndEnqueuesAnalysisWithoutObjectKey() {
        UUID teamId = UUID.randomUUID();
        var command = new ImportFilingCommand(
                "aapl",
                "0000320193-24-000123",
                "320193",
                "Apple Inc.",
                "10-K",
                "FY2024",
                LocalDate.parse("2024-09-28"),
                LocalDate.parse("2024-11-01"),
                "https://sec.example/aapl");

        var result = service.importFiling(command, teamId);

        Document saved = repository.store.get(result.documentId());
        assertThat(saved.getSource()).isEqualTo(DocumentSource.EDGAR);
        assertThat(saved.getStatus()).isEqualTo(DocumentStatus.PENDING);
        assertThat(saved.getTeamId()).isEqualTo(teamId);
        assertThat(saved.getTicker()).isEqualTo("AAPL");
        assertThat(saved.getAccessionNumber()).isEqualTo("0000320193-24-000123");
        assertThat(saved.getFileName()).isEqualTo("AAPL 10-K FY2024");
        assertThat(queue.message.documentId()).isEqualTo(result.documentId());
        assertThat(queue.message.objectKey()).isNull();
    }

    static class RecordingQueue implements AnalysisQueuePort {
        DocumentAnalysisMessage message;

        @Override
        public void enqueue(DocumentAnalysisMessage message) {
            this.message = message;
        }
    }

    static class FakeDocumentRepository implements DocumentRepositoryPort {
        final Map<UUID, Document> store = new LinkedHashMap<>();

        @Override
        public Document save(Document document) {
            UUID id = document.getId() == null ? UUID.randomUUID() : document.getId();
            Document stored = copy(document, id);
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

        private Document copy(Document document, UUID id) {
            return Document.builder()
                    .id(id)
                    .teamId(document.getTeamId())
                    .fileName(document.getFileName())
                    .fileSize(document.getFileSize())
                    .contentType(document.getContentType())
                    .uploadedAt(document.getUploadedAt())
                    .status(document.getStatus())
                    .lastAnalyzedAt(document.getLastAnalyzedAt())
                    .source(document.getSource())
                    .cik(document.getCik())
                    .ticker(document.getTicker())
                    .companyName(document.getCompanyName())
                    .formType(document.getFormType())
                    .fiscalPeriod(document.getFiscalPeriod())
                    .reportDate(document.getReportDate())
                    .filingDate(document.getFilingDate())
                    .accessionNumber(document.getAccessionNumber())
                    .sourceUrl(document.getSourceUrl())
                    .build();
        }
    }
}
