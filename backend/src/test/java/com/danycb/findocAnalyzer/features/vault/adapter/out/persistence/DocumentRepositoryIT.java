package com.danycb.findocAnalyzer.features.vault.adapter.out.persistence;

import com.danycb.findocAnalyzer.features.vault.domain.Document;
import com.danycb.findocAnalyzer.features.vault.domain.DocumentSource;
import com.danycb.findocAnalyzer.features.vault.domain.DocumentStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testcontainers integration test for {@link DocumentRepository}: the real JPA persistence adapter
 * against a pgvector-capable Postgres, with the production Flyway migrations applied. Verifies the
 * domain/entity round-trip and the tenant-scoped query methods.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@Import(DocumentRepository.class)
class DocumentRepositoryIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:0.8.2-pg18-trixie").asCompatibleSubstituteFor("postgres"));

    @Autowired
    private DocumentRepository repository;

    private static Document.DocumentBuilder document(UUID teamId, String fileName) {
        return Document.builder()
                .teamId(teamId)
                .fileName(fileName)
                .fileSize(1024L)
                .contentType("application/pdf")
                .status(DocumentStatus.PENDING);
    }

    @Test
    void savePersistsAndAssignsIdAndVersion() {
        UUID teamId = UUID.randomUUID();

        Document saved = repository.save(document(teamId, "report.pdf").build());

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getVersion()).isZero();
        assertThat(saved.getUploadedAt()).isNotNull();
        assertThat(saved.getSource()).isEqualTo(DocumentSource.UPLOAD);
    }

    @Test
    void findByIdReturnsPersistedDocument() {
        UUID teamId = UUID.randomUUID();
        Document saved = repository.save(document(teamId, "10k.pdf").build());

        Optional<Document> found = repository.findById(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getFileName()).isEqualTo("10k.pdf");
        assertThat(found.get().getTeamId()).isEqualTo(teamId);
        assertThat(found.get().getStatus()).isEqualTo(DocumentStatus.PENDING);
        assertThat(found.get().getFileSize()).isEqualTo(1024L);
        assertThat(found.get().getContentType()).isEqualTo("application/pdf");
    }

    @Test
    void findByIdReturnsEmptyForUnknownId() {
        assertThat(repository.findById(UUID.randomUUID())).isEmpty();
    }

    @Test
    void persistsAllEdgarMetadataFields() {
        UUID teamId = UUID.randomUUID();
        Document edgar = document(teamId, "aapl-10k.pdf")
                .source(DocumentSource.EDGAR)
                .cik("320193")
                .ticker("AAPL")
                .companyName("Apple Inc.")
                .formType("10-K")
                .fiscalPeriod("FY2024")
                .reportDate(LocalDate.of(2024, 9, 28))
                .filingDate(LocalDate.of(2024, 11, 1))
                .accessionNumber("0000320193-24-000123")
                .sourceUrl("https://sec.example/aapl")
                .build();

        Document found = repository.findById(repository.save(edgar).getId()).orElseThrow();

        assertThat(found.getSource()).isEqualTo(DocumentSource.EDGAR);
        assertThat(found.getCik()).isEqualTo("320193");
        assertThat(found.getTicker()).isEqualTo("AAPL");
        assertThat(found.getCompanyName()).isEqualTo("Apple Inc.");
        assertThat(found.getFormType()).isEqualTo("10-K");
        assertThat(found.getFiscalPeriod()).isEqualTo("FY2024");
        assertThat(found.getReportDate()).isEqualTo(LocalDate.of(2024, 9, 28));
        assertThat(found.getFilingDate()).isEqualTo(LocalDate.of(2024, 11, 1));
        assertThat(found.getAccessionNumber()).isEqualTo("0000320193-24-000123");
        assertThat(found.getSourceUrl()).isEqualTo("https://sec.example/aapl");
    }

    @Test
    void findByTeamIdReturnsOnlyThatTeamsDocuments() {
        UUID teamA = UUID.randomUUID();
        UUID teamB = UUID.randomUUID();
        repository.save(document(teamA, "a1.pdf").build());
        repository.save(document(teamA, "a2.pdf").build());
        repository.save(document(teamB, "b1.pdf").build());

        List<Document> teamADocs = repository.findByTeamId(teamA);

        assertThat(teamADocs).extracting(Document::getFileName)
                .containsExactlyInAnyOrder("a1.pdf", "a2.pdf");
    }

    @Test
    void findByIdAndTeamIdEnforcesTenantIsolation() {
        UUID owner = UUID.randomUUID();
        UUID intruder = UUID.randomUUID();
        Document saved = repository.save(document(owner, "secret.pdf").build());

        assertThat(repository.findByIdAndTeamId(saved.getId(), owner)).isPresent();
        assertThat(repository.findByIdAndTeamId(saved.getId(), intruder)).isEmpty();
    }

    @Test
    void countAllCountsAcrossTeams() {
        long before = repository.countAll();
        repository.save(document(UUID.randomUUID(), "x.pdf").build());
        repository.save(document(UUID.randomUUID(), "y.pdf").build());

        assertThat(repository.countAll()).isEqualTo(before + 2);
    }

    @Test
    void deleteRemovesDocument() {
        Document saved = repository.save(document(UUID.randomUUID(), "gone.pdf").build());

        repository.delete(saved);

        assertThat(repository.findById(saved.getId())).isEmpty();
    }
}
