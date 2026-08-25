package com.danycb.findocAnalyzer.features.vault.application;

import com.danycb.findocAnalyzer.features.vault.adapter.out.persistence.DocumentRepository;
import com.danycb.findocAnalyzer.features.vault.application.dto.DocumentAnalysisMessage;
import com.danycb.findocAnalyzer.features.vault.application.dto.ImportFilingCommand;
import com.danycb.findocAnalyzer.features.vault.application.out.AnalysisQueuePort;
import com.danycb.findocAnalyzer.features.vault.application.out.DocumentRepositoryPort;
import com.danycb.findocAnalyzer.features.vault.domain.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import javax.sql.DataSource;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@Import({
        DocumentRepository.class,
        ImportFilingService.class,
        VaultAuditLogger.class,
        ImportFilingServicePostgresIT.TestConfig.class
})
class ImportFilingServicePostgresIT {
    private static final String ACCESSION = "0000320193-24-000123";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:0.8.2-pg18-trixie")
                    .asCompatibleSubstituteFor("postgres"));

    @Autowired
    private ImportFilingService service;

    @Autowired
    private DocumentRepository repository;

    @Autowired
    private BarrierDocumentRepository barrierRepository;

    @Autowired
    private RecordingQueue queue;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void resetTestDoubles() {
        queue.reset();
        barrierRepository.disarm();
    }

    @Test
    void concurrentPostgresConflictCommitsOneDocumentAndEnqueuesExactlyOnceAfterCommit() throws Exception {
        UUID teamId = UUID.randomUUID();
        barrierRepository.armForTwoInserts();

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> service.importFiling(command(), teamId));
            var second = executor.submit(() -> service.importFiling(command(), teamId));

            var firstResult = first.get(10, TimeUnit.SECONDS);
            var secondResult = second.get(10, TimeUnit.SECONDS);

            assertThat(secondResult.documentId()).isEqualTo(firstResult.documentId());
        }

        assertThat(repository.findByTeamIdAndAccessionNumber(teamId, ACCESSION)).isPresent();
        assertThat(repository.findByTeamId(teamId)).hasSize(1);
        assertThat(queue.messages).singleElement().satisfies(message ->
                assertThat(message.documentId())
                        .isEqualTo(repository.findByTeamIdAndAccessionNumber(teamId, ACCESSION)
                                .orElseThrow().getId()));
        assertThat(queue.documentWasVisibleWhenPublished).containsExactly(true);
    }

    @Test
    void separateServiceInstancesUseOneDatabaseBackedPublicationClaim() throws Exception {
        UUID teamId = UUID.randomUUID();
        barrierRepository.armForTwoInserts();
        ImportFilingService firstService = new ImportFilingService(
                barrierRepository, queue, new VaultAuditLogger());
        ImportFilingService secondService = new ImportFilingService(
                barrierRepository, queue, new VaultAuditLogger());
        TransactionTemplate transactions = new TransactionTemplate(transactionManager);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> transactions.execute(status ->
                    firstService.importFiling(command(), teamId)));
            var second = executor.submit(() -> transactions.execute(status ->
                    secondService.importFiling(command(), teamId)));

            assertThat(second.get(10, TimeUnit.SECONDS)).isEqualTo(first.get(10, TimeUnit.SECONDS));
        }

        assertThat(repository.findByTeamId(teamId)).hasSize(1);
        assertThat(queue.messages).singleElement().satisfies(message ->
                assertThat(message.documentId()).isEqualTo(
                        repository.findByTeamIdAndAccessionNumber(teamId, ACCESSION).orElseThrow().getId()));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void separateServiceRetryReleasesDatabaseClaimAfterSynchronousQueueFailure() {
        UUID teamId = UUID.randomUUID();
        ImportFilingService firstService = new ImportFilingService(
                barrierRepository, queue, new VaultAuditLogger());
        ImportFilingService retryService = new ImportFilingService(
                barrierRepository, queue, new VaultAuditLogger());
        TransactionTemplate transactions = new TransactionTemplate(transactionManager);
        queue.failNextPublication();

        assertThatThrownBy(() -> transactions.execute(status ->
                firstService.importFiling(command(), teamId)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("queue unavailable");

        var retried = transactions.execute(status -> retryService.importFiling(command(), teamId));

        assertThat(repository.findByTeamId(teamId)).hasSize(1);
        assertThat(queue.messages).singleElement().satisfies(message ->
                assertThat(message.documentId()).isEqualTo(retried.documentId()));
    }

    private ImportFilingCommand command() {
        return new ImportFilingCommand(
                "AAPL",
                ACCESSION,
                null,
                "320193",
                "Apple Inc.",
                "10-K",
                "FY",
                LocalDate.of(2024, 9, 28),
                LocalDate.of(2024, 11, 1),
                "https://sec.example/" + ACCESSION);
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        RecordingQueue recordingQueue(DataSource dataSource) {
            return new RecordingQueue(dataSource);
        }

        @Bean
        @Primary
        BarrierDocumentRepository barrierDocumentRepository(DocumentRepository delegate) {
            return new BarrierDocumentRepository(delegate);
        }
    }

    static final class RecordingQueue implements AnalysisQueuePort {
        private final DataSource dataSource;
        private final List<DocumentAnalysisMessage> messages = new CopyOnWriteArrayList<>();
        private final List<Boolean> documentWasVisibleWhenPublished = new CopyOnWriteArrayList<>();
        private final AtomicBoolean failNext = new AtomicBoolean();

        private RecordingQueue(DataSource dataSource) {
            this.dataSource = dataSource;
        }

        @Override
        public void enqueue(DocumentAnalysisMessage message) {
            if (failNext.compareAndSet(true, false)) {
                throw new IllegalStateException("queue unavailable after commit");
            }
            documentWasVisibleWhenPublished.add(isVisibleFromSeparateConnection(message.documentId()));
            messages.add(message);
        }

        void failNextPublication() {
            failNext.set(true);
        }

        void reset() {
            messages.clear();
            documentWasVisibleWhenPublished.clear();
            failNext.set(false);
        }

        private boolean isVisibleFromSeparateConnection(UUID documentId) {
            try (var connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(
                         "SELECT 1 FROM document_metadata WHERE id = ?")) {
                statement.setObject(1, documentId);
                try (var result = statement.executeQuery()) {
                    return result.next();
                }
            } catch (Exception failure) {
                throw new IllegalStateException("Could not verify committed import visibility", failure);
            }
        }
    }

    static final class BarrierDocumentRepository implements DocumentRepositoryPort {
        private final DocumentRepository delegate;
        private volatile CyclicBarrier insertBarrier;

        private BarrierDocumentRepository(DocumentRepository delegate) {
            this.delegate = delegate;
        }

        void armForTwoInserts() {
            insertBarrier = new CyclicBarrier(2);
        }

        void disarm() {
            insertBarrier = null;
        }

        @Override
        public InsertResult insertOrGet(Document document) {
            CyclicBarrier barrier = insertBarrier;
            if (barrier != null) {
                try {
                    barrier.await(5, TimeUnit.SECONDS);
                } catch (Exception failure) {
                    throw new IllegalStateException("Concurrent import test did not reach the insert race", failure);
                }
            }
            return delegate.insertOrGet(document);
        }

        @Override
        public boolean claimAnalysisPublication(UUID documentId) {
            return delegate.claimAnalysisPublication(documentId);
        }

        @Override
        public void releaseAnalysisPublication(UUID documentId) {
            delegate.releaseAnalysisPublication(documentId);
        }

        @Override
        public Document save(Document document) {
            return delegate.save(document);
        }

        @Override
        public Optional<Document> findById(UUID id) {
            return delegate.findById(id);
        }

        @Override
        public Optional<Document> findByIdAndTeamId(UUID id, UUID teamId) {
            return delegate.findByIdAndTeamId(id, teamId);
        }

        @Override
        public Optional<Document> findByTeamIdAndAccessionNumber(UUID teamId, String accessionNumber) {
            return delegate.findByTeamIdAndAccessionNumber(teamId, accessionNumber);
        }

        @Override
        public List<Document> findByTeamIdAndAmendsAccessionNumber(UUID teamId, String accessionNumber) {
            return delegate.findByTeamIdAndAmendsAccessionNumber(teamId, accessionNumber);
        }

        @Override
        public List<Document> findByTeamId(UUID teamId) {
            return delegate.findByTeamId(teamId);
        }

        @Override
        public long countAll() {
            return delegate.countAll();
        }

        @Override
        public void delete(Document document) {
            delegate.delete(document);
        }
    }
}
