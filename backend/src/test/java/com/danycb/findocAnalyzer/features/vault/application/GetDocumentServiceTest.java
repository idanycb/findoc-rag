package com.danycb.findocAnalyzer.features.vault.application;

import com.danycb.findocAnalyzer.features.vault.application.out.DocumentRepositoryPort;
import com.danycb.findocAnalyzer.features.vault.domain.Document;
import com.danycb.findocAnalyzer.features.vault.domain.DocumentStatus;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class GetDocumentServiceTest {

    private final FakeDocumentRepository repository = new FakeDocumentRepository();
    private final GetDocumentService service = new GetDocumentService(repository);

    private final UUID teamId = UUID.randomUUID();

    @Test
    void execute_delegatesToRepositoryGetByIdForTeam() {
        Document document = repository.save(Document.builder()
                .id(UUID.randomUUID())
                .teamId(teamId)
                .fileName("report.pdf")
                .status(DocumentStatus.COMPLETED)
                .build());

        Document result = service.execute(document.getId(), teamId);

        assertThat(result.getId()).isEqualTo(document.getId());
        assertThat(repository.lastLookupId).isEqualTo(document.getId());
        assertThat(repository.lastLookupTeamId).isEqualTo(teamId);
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
