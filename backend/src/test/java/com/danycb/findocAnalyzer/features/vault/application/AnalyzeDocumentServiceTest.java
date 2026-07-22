package com.danycb.findocAnalyzer.features.vault.application;

import com.danycb.findocAnalyzer.features.vault.application.out.DocumentParserPort;
import com.danycb.findocAnalyzer.features.vault.application.out.DocumentRepositoryPort;
import com.danycb.findocAnalyzer.features.vault.application.out.ExternalStoragePort;
import com.danycb.findocAnalyzer.features.vault.application.out.FilingSectionsPort;
import com.danycb.findocAnalyzer.features.vault.application.out.VectorIndexPort;
import com.danycb.findocAnalyzer.features.vault.domain.Document;
import com.danycb.findocAnalyzer.features.vault.domain.DocumentSource;
import com.danycb.findocAnalyzer.features.vault.domain.DocumentStatus;
import com.danycb.findocAnalyzer.features.vault.domain.ParsedSection;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AnalyzeDocumentServiceTest {
    private final FakeDocumentRepository repository = new FakeDocumentRepository();
    private final RecordingStorage storage = new RecordingStorage();
    private final RecordingParser parser = new RecordingParser();
    private final RecordingFilingSections filingSections = new RecordingFilingSections();
    private final RecordingVectorIndex vectorIndex = new RecordingVectorIndex();
    private final AnalyzeDocumentService service = new AnalyzeDocumentService(
            repository, storage, parser, filingSections, vectorIndex, new VaultAuditLogger());

    @Test
    void uploadDocument_downloadsFromStorageAndParsesContent() {
        Document document = repository.save(Document.builder()
                .id(UUID.randomUUID())
                .teamId(UUID.randomUUID())
                .fileName("upload.pdf")
                .contentType("application/pdf")
                .status(DocumentStatus.PENDING)
                .build());

        service.analyze(document.getId(), "files/upload.pdf");

        assertThat(storage.downloadedKey).isEqualTo("files/upload.pdf");
        assertThat(parser.called).isTrue();
        assertThat(filingSections.called).isFalse();
        assertThat(vectorIndex.sections).containsExactly(new ParsedSection(2, "Upload Section", "upload text"));
        assertThat(repository.store.get(document.getId()).getStatus()).isEqualTo(DocumentStatus.COMPLETED);
    }

    @Test
    void edgarDocument_fetchesFilingSectionsWithoutStorageOrParser() {
        Document document = repository.save(Document.builder()
                .id(UUID.randomUUID())
                .teamId(UUID.randomUUID())
                .fileName("AAPL 10-K FY2024")
                .status(DocumentStatus.PENDING)
                .source(DocumentSource.EDGAR)
                .ticker("AAPL")
                .accessionNumber("0000320193-24-000123")
                .build());

        service.analyze(document.getId(), null);

        assertThat(storage.downloadedKey).isNull();
        assertThat(parser.called).isFalse();
        assertThat(filingSections.called).isTrue();
        assertThat(filingSections.ticker).isEqualTo("AAPL");
        assertThat(filingSections.accessionNumber).isEqualTo("0000320193-24-000123");
        assertThat(vectorIndex.sections).containsExactly(new ParsedSection(1, "Item 1A Risk Factors", "risk text"));
        assertThat(repository.store.get(document.getId()).getStatus()).isEqualTo(DocumentStatus.COMPLETED);
    }

    static class RecordingStorage implements ExternalStoragePort {
        String downloadedKey;

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
            downloadedKey = objectKey;
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

    static class RecordingParser implements DocumentParserPort {
        boolean called;

        @Override
        public List<ParsedSection> parse(byte[] content, String fileName, String contentType) {
            called = true;
            return List.of(new ParsedSection(2, "Upload Section", "upload text"));
        }
    }

    static class RecordingFilingSections implements FilingSectionsPort {
        boolean called;
        String ticker;
        String accessionNumber;

        @Override
        public List<ParsedSection> fetchSections(String ticker, String accessionNumber) {
            called = true;
            this.ticker = ticker;
            this.accessionNumber = accessionNumber;
            return List.of(new ParsedSection(1, "Item 1A Risk Factors", "risk text"));
        }
    }

    static class RecordingVectorIndex implements VectorIndexPort {
        List<ParsedSection> sections;

        @Override
        public void ingest(List<ParsedSection> sections, UUID docId, UUID teamId, String fileName) {
            this.sections = sections;
        }

        @Override
        public void deleteByDocumentId(UUID docId) {
        }
    }

    static class FakeDocumentRepository implements DocumentRepositoryPort {
        final Map<UUID, Document> store = new LinkedHashMap<>();

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
