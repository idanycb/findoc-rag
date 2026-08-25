package com.danycb.findocAnalyzer.features.vault.adapter.out.persistence;

import com.danycb.findocAnalyzer.features.vault.domain.Document;
import com.danycb.findocAnalyzer.features.vault.domain.AmendmentLinkStatus;
import com.danycb.findocAnalyzer.features.vault.domain.DocumentSource;
import com.danycb.findocAnalyzer.features.vault.domain.DocumentStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.EntityManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Autowired
    private EntityManager entityManager;

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
    void saveReturnsIncrementedVersionAfterUpdatingAnExistingDocument() {
        Document saved = repository.save(document(UUID.randomUUID(), "report.pdf").build());

        saved.markProcessing();
        Document updated = repository.save(saved);

        assertThat(updated.getVersion()).isEqualTo(saved.getVersion() + 1);
        assertThat(updated.getStatus()).isEqualTo(DocumentStatus.PROCESSING);
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
    void persistsAmendmentRelationshipAndSearchability() {
        UUID teamId = UUID.randomUUID();
        Document original = repository.save(document(teamId, "AAPL 10-K FY")
                .source(DocumentSource.EDGAR)
                .formType("10-K")
                .baseFormType("10-K")
                .amendment(false)
                .accessionNumber("0000320193-24-000123")
                .amendmentLinkStatus(AmendmentLinkStatus.NOT_APPLICABLE)
                .searchable(true)
                .build());
        Document amendment = repository.save(document(teamId, "AAPL 10-K/A FY")
                .source(DocumentSource.EDGAR)
                .formType("10-K/A")
                .baseFormType("10-K")
                .amendment(true)
                .accessionNumber("0000320193-25-000020")
                .amendsAccessionNumber(original.getAccessionNumber())
                .amendsDocumentId(original.getId())
                .amendmentLinkStatus(AmendmentLinkStatus.LINKED)
                .searchable(false)
                .build());

        Document found = repository.findById(amendment.getId()).orElseThrow();

        assertThat(found.getBaseFormType()).isEqualTo("10-K");
        assertThat(found.isAmendment()).isTrue();
        assertThat(found.getAmendsAccessionNumber()).isEqualTo(original.getAccessionNumber());
        assertThat(found.getAmendsDocumentId()).isEqualTo(original.getId());
        assertThat(found.getAmendmentLinkStatus()).isEqualTo(AmendmentLinkStatus.LINKED);
        assertThat(found.isSearchable()).isFalse();
    }

    @Test
    void teamAndAccessionAreUniqueForEdgarDocuments() {
        UUID teamId = UUID.randomUUID();
        repository.save(edgar(teamId, "0000320193-24-000123"));
        entityManager.flush();

        assertThatThrownBy(() -> {
            repository.save(edgar(teamId, "0000320193-24-000123"));
            entityManager.flush();
        }).isInstanceOfAny(DataIntegrityViolationException.class, jakarta.persistence.PersistenceException.class);
    }

    @Test
    void insertOrGetReturnsTheExistingTeamAccessionWithoutCreatingADuplicate() {
        UUID teamId = UUID.randomUUID();
        Document candidate = edgar(teamId, "atomic-accession");

        var first = repository.insertOrGet(candidate);
        var second = repository.insertOrGet(candidate);

        assertThat(first.inserted()).isTrue();
        assertThat(second.inserted()).isFalse();
        assertThat(second.document().getId()).isEqualTo(first.document().getId());
        assertThat(repository.findByTeamIdAndAccessionNumber(teamId, "atomic-accession")).isPresent();
    }

    @Test
    void sameAccessionIsAllowedForDifferentTeams() {
        repository.save(edgar(UUID.randomUUID(), "0000320193-24-000123"));
        repository.save(edgar(UUID.randomUUID(), "0000320193-24-000123"));

        entityManager.flush();
    }

    @Test
    void amendmentQueriesAreTeamScoped() {
        UUID owner = UUID.randomUUID();
        UUID otherTeam = UUID.randomUUID();
        Document original = repository.save(edgar(owner, "0000320193-24-000123"));
        repository.save(document(owner, "amendment")
                .source(DocumentSource.EDGAR)
                .formType("10-K/A")
                .baseFormType("10-K")
                .amendment(true)
                .accessionNumber("0000320193-25-000020")
                .amendsAccessionNumber(original.getAccessionNumber())
                .amendmentLinkStatus(AmendmentLinkStatus.UNRESOLVED)
                .build());

        assertThat(repository.findByTeamIdAndAccessionNumber(owner, original.getAccessionNumber())).isPresent();
        assertThat(repository.findByTeamIdAndAccessionNumber(otherTeam, original.getAccessionNumber())).isEmpty();
        assertThat(repository.findByTeamIdAndAmendsAccessionNumber(owner, original.getAccessionNumber()))
                .hasSize(1);
        assertThat(repository.findByTeamIdAndAmendsAccessionNumber(otherTeam, original.getAccessionNumber()))
                .isEmpty();
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

    private Document edgar(UUID teamId, String accession) {
        return document(teamId, "filing")
                .source(DocumentSource.EDGAR)
                .formType("10-K")
                .baseFormType("10-K")
                .accessionNumber(accession)
                .amendmentLinkStatus(AmendmentLinkStatus.NOT_APPLICABLE)
                .build();
    }
}
