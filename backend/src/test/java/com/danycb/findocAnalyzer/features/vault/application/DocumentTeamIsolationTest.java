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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentTeamIsolationTest {

    private final FakeDocumentRepository repository = new FakeDocumentRepository();
    private final ListDocumentsService listDocuments = new ListDocumentsService(repository);
    private final GetDocumentService getDocument = new GetDocumentService(repository);

    private final UUID teamA = UUID.randomUUID();
    private final UUID teamB = UUID.randomUUID();

    private Document doc(UUID teamId) {
        return repository.save(Document.builder()
                .id(UUID.randomUUID())
                .teamId(teamId)
                .fileName("f")
                .status(DocumentStatus.COMPLETED)
                .build());
    }

    @Test
    void list_returnsOnlyTheCallersTeamDocuments() {
        doc(teamA);
        doc(teamA);
        doc(teamB);

        assertThat(listDocuments.execute(teamA)).hasSize(2);
        assertThat(listDocuments.execute(teamB)).hasSize(1);
    }

    @Test
    void get_returnsDocumentForOwningTeam() {
        Document d = doc(teamA);

        assertThat(getDocument.execute(d.getId(), teamA).getId()).isEqualTo(d.getId());
    }

    @Test
    void get_forAnotherTeam_behavesAsNotFound() {
        Document d = doc(teamA);

        assertThatThrownBy(() -> getDocument.execute(d.getId(), teamB))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    static class FakeDocumentRepository implements DocumentRepositoryPort {
        @Override public InsertResult insertOrGet(Document document) { return new InsertResult(save(document), true); }
        private final Map<UUID, Document> store = new LinkedHashMap<>();

        @Override
        public Document save(Document document) {
            store.put(document.getId(), document);
            return document;
        }

        @Override
        public Optional<Document> findById(UUID id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public Optional<Document> findByIdAndTeamId(UUID id, UUID teamId) {
            return Optional.ofNullable(store.get(id))
                    .filter(d -> teamId.equals(d.getTeamId()));
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
