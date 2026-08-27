package com.danycb.findocAnalyzer.features.vault.adapter.out.persistence;

import com.danycb.findocAnalyzer.features.vault.application.dto.DocumentAnalysisMessage;
import com.danycb.findocAnalyzer.features.vault.application.event.AnalysisOutboxEnqueuedEvent;
import com.danycb.findocAnalyzer.features.vault.application.out.AnalysisOutboxMaintenancePort;
import com.danycb.findocAnalyzer.features.vault.domain.Document;
import com.danycb.findocAnalyzer.features.vault.domain.DocumentStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@Import({DocumentRepository.class, AnalysisOutboxRepository.class,
        AnalysisOutboxRepositoryPostgresIT.CommittedEventRecorder.class})
class AnalysisOutboxRepositoryPostgresIT {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:0.8.2-pg18-trixie")
                    .asCompatibleSubstituteFor("postgres"));

    @Autowired private DocumentRepository documents;
    @Autowired private AnalysisOutboxRepository outbox;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private EntityManager entityManager;
    @Autowired private CommittedEventRecorder committedEvents;

    @BeforeEach
    void clearCommittedOutboxFixtures() {
        committedEvents.reset();
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            entityManager.createNativeQuery("DELETE FROM analysis_outbox").executeUpdate();
            entityManager.createNativeQuery("DELETE FROM document_metadata").executeUpdate();
        });
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void enqueueSignalsOnceAfterCommitButNeverAfterRollback() {
        enqueueNewDocumentRequest();
        assertThat(committedEvents.count()).isEqualTo(1);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            UUID documentId = documents.save(Document.builder()
                    .teamId(UUID.randomUUID())
                    .fileName("rolled-back.pdf")
                    .status(DocumentStatus.PENDING)
                    .build()).getId();
            outbox.enqueue(new DocumentAnalysisMessage(documentId, "files/" + documentId));
            status.setRollbackOnly();
        });

        assertThat(committedEvents.count()).isEqualTo(1);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void enqueueDeduplicatesActiveRequestsAndSuccessRemovesThemFromDueWork() {
        DocumentAnalysisMessage message = enqueueNewDocumentRequest();
        Instant now = Instant.now().plusSeconds(1);

        var claimed = outbox.claimDue(now, 10, Duration.ofMinutes(1));
        assertThat(claimed).singleElement().satisfies(request -> {
            assertThat(request.message().requestId()).isEqualTo(request.outboxId());
            assertThat(request.message().documentId()).isEqualTo(message.documentId());
            assertThat(request.message().objectKey()).isEqualTo(message.objectKey());
            assertThat(request.attemptCount()).isEqualTo(1);
        });

        outbox.markPublished(claimed.getFirst().outboxId(), claimed.getFirst().claimToken(), now);
        assertThat(outbox.claimDue(now.plusSeconds(1), 10, Duration.ofMinutes(1))).isEmpty();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void competingClaimsReturnOneRequestAndExpiredLeaseCanBeClaimedAgain() throws Exception {
        enqueueNewDocumentRequest();
        Instant now = Instant.now().plusSeconds(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> outbox.claimDue(now, 1, Duration.ofSeconds(1)));
            var second = executor.submit(() -> outbox.claimDue(now, 1, Duration.ofSeconds(1)));
            assertThat(first.get()).hasSizeLessThanOrEqualTo(1);
            assertThat(second.get()).hasSizeLessThanOrEqualTo(1);
            assertThat(first.get().size() + second.get().size()).isEqualTo(1);
        }

        assertThat(outbox.claimDue(now.plusSeconds(2), 1, Duration.ofMinutes(1)))
                .singleElement()
                .extracting(request -> request.attemptCount())
                .isEqualTo(2);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void failureSchedulesRetryBeforeTheRequestCanBeClaimedAgain() {
        enqueueNewDocumentRequest();
        Instant now = Instant.now().plusSeconds(1);
        var claimed = outbox.claimDue(now, 1, Duration.ofMinutes(1)).getFirst();

        outbox.markFailed(claimed.outboxId(), claimed.claimToken(), now.plus(Duration.ofMinutes(5)), "queue unavailable");

        assertThat(outbox.claimDue(now.plus(Duration.ofMinutes(4)), 1, Duration.ofMinutes(1))).isEmpty();
        assertThat(outbox.claimDue(now.plus(Duration.ofMinutes(5)), 1, Duration.ofMinutes(1))).singleElement();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void staleClaimerCannotAcknowledgeOrReleaseAReclaimedLease() {
        enqueueNewDocumentRequest();
        Instant claimedAt = Instant.now().plusSeconds(1);
        var firstClaim = outbox.claimDue(claimedAt, 1, Duration.ofMinutes(1)).getFirst();
        Instant reclaimedAt = claimedAt.plus(Duration.ofMinutes(2));
        var secondClaim = outbox.claimDue(reclaimedAt, 1, Duration.ofMinutes(1)).getFirst();

        assertThat(secondClaim.claimToken()).isNotEqualTo(firstClaim.claimToken());
        outbox.markFailed(firstClaim.outboxId(), firstClaim.claimToken(), reclaimedAt, "stale failure");
        outbox.markPublished(firstClaim.outboxId(), firstClaim.claimToken(), reclaimedAt);

        assertThat(outbox.claimDue(reclaimedAt.plusSeconds(30), 1, Duration.ofMinutes(1))).isEmpty();
        outbox.markPublished(secondClaim.outboxId(), secondClaim.claimToken(), reclaimedAt);
        assertThat(outbox.claimDue(reclaimedAt.plus(Duration.ofMinutes(2)), 1, Duration.ofMinutes(1))).isEmpty();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void outboxIdBecomesTheMessageRequestIdAndFencesDuplicateProcessing() {
        enqueueNewDocumentRequest();
        Instant now = Instant.now().plusSeconds(1);
        var publication = outbox.claimDue(now, 1, Duration.ofMinutes(1)).getFirst();

        assertThat(publication.message().requestId()).isEqualTo(publication.outboxId());
        outbox.markPublished(publication.outboxId(), publication.claimToken(), now);
        enqueueExistingDocumentRequest(publication.message());
        assertThat(countOutboxRows()).isEqualTo(1);

        var first = outbox.claim(
                publication.outboxId(), publication.message().documentId(), now, Duration.ofMinutes(1));
        assertThat(first).isPresent();
        assertThat(outbox.claim(
                publication.outboxId(), publication.message().documentId(), now, Duration.ofMinutes(1))).isEmpty();

        Instant reclaimedAt = now.plus(Duration.ofMinutes(2));
        var second = outbox.claim(
                publication.outboxId(), publication.message().documentId(), reclaimedAt, Duration.ofMinutes(1));
        assertThat(second).isPresent();
        assertThat(second.orElseThrow().claimToken()).isNotEqualTo(first.orElseThrow().claimToken());

        outbox.markProcessed(publication.outboxId(), first.orElseThrow().claimToken(), reclaimedAt);
        assertThat(outbox.inspect(reclaimedAt, reclaimedAt.minusSeconds(1), 20).pendingProcessingCount())
                .isEqualTo(1);

        outbox.markProcessed(publication.outboxId(), second.orElseThrow().claimToken(), reclaimedAt);
        assertThat(outbox.inspect(reclaimedAt, reclaimedAt.minusSeconds(1), 20).pendingProcessingCount())
                .isZero();
        enqueueExistingDocumentRequest(publication.message());
        assertThat(countOutboxRows()).isEqualTo(2);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void inspectionShowsStuckPublicationAndProcessingRows() {
        DocumentAnalysisMessage unpublished = enqueueNewDocumentRequest();
        DocumentAnalysisMessage processing = enqueueNewDocumentRequest();
        Instant now = Instant.now().plusSeconds(2);
        var claims = outbox.claimDue(now, 10, Duration.ofMinutes(1));
        var processingClaim = claims.stream()
                .filter(claim -> claim.message().documentId().equals(processing.documentId()))
                .findFirst().orElseThrow();
        outbox.markPublished(processingClaim.outboxId(), processingClaim.claimToken(), now);
        entityUpdateCreatedAt(now.minus(Duration.ofHours(2)));

        var status = outbox.inspect(now, now.minus(Duration.ofMinutes(15)), 20);

        assertThat(status.pendingPublicationCount()).isEqualTo(1);
        assertThat(status.stuckPublicationCount()).isEqualTo(1);
        assertThat(status.pendingProcessingCount()).isEqualTo(1);
        assertThat(status.stuckProcessingCount()).isEqualTo(1);
        assertThat(status.stuckRequests())
                .extracting(AnalysisOutboxMaintenancePort.StuckRequest::stage)
                .containsExactlyInAnyOrder(
                        AnalysisOutboxMaintenancePort.Stage.PUBLICATION,
                        AnalysisOutboxMaintenancePort.Stage.PROCESSING);
        assertThat(status.stuckRequests())
                .extracting(AnalysisOutboxMaintenancePort.StuckRequest::documentId)
                .containsExactlyInAnyOrder(unpublished.documentId(), processing.documentId());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void cleanupIsBoundedAndOnlyDeletesPublishedProcessedRows() {
        for (int index = 0; index < 3; index++) {
            enqueueNewDocumentRequest();
        }
        Instant now = Instant.now().plusSeconds(2);
        for (var publication : outbox.claimDue(now, 10, Duration.ofMinutes(1))) {
            outbox.markPublished(publication.outboxId(), publication.claimToken(), now.minus(Duration.ofDays(31)));
            var processing = outbox.claim(
                    publication.outboxId(), publication.message().documentId(), now, Duration.ofMinutes(1))
                    .orElseThrow();
            outbox.markProcessed(publication.outboxId(), processing.claimToken(), now);
        }

        assertThat(outbox.deletePublishedProcessedBefore(now.minus(Duration.ofDays(30)), 2)).isEqualTo(2);
        assertThat(countOutboxRows()).isEqualTo(1);
        assertThat(outbox.deletePublishedProcessedBefore(now.minus(Duration.ofDays(30)), 2)).isEqualTo(1);
        assertThat(countOutboxRows()).isZero();
    }

    private DocumentAnalysisMessage enqueueNewDocumentRequest() {
        return new TransactionTemplate(transactionManager).execute(status -> {
            UUID documentId = documents.save(Document.builder()
                    .teamId(UUID.randomUUID())
                    .fileName("report.pdf")
                    .status(DocumentStatus.PENDING)
                    .build()).getId();
            DocumentAnalysisMessage message = new DocumentAnalysisMessage(documentId, "files/" + documentId);
            outbox.enqueue(message);
            outbox.enqueue(message);
            return message;
        });
    }

    private void entityUpdateCreatedAt(Instant createdAt) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                entityManager.createNativeQuery("UPDATE analysis_outbox SET created_at = :createdAt")
                        .setParameter("createdAt", createdAt)
                        .executeUpdate());
    }

    private void enqueueExistingDocumentRequest(DocumentAnalysisMessage message) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                outbox.enqueue(new DocumentAnalysisMessage(message.documentId(), message.objectKey())));
    }

    private long countOutboxRows() {
        return new TransactionTemplate(transactionManager).execute(status ->
                ((Number) entityManager.createNativeQuery("SELECT count(*) FROM analysis_outbox")
                        .getSingleResult()).longValue());
    }

    @Component
    static class CommittedEventRecorder {
        private int count;

        @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
        public void on(AnalysisOutboxEnqueuedEvent ignored) {
            count++;
        }

        int count() {
            return count;
        }

        void reset() {
            count = 0;
        }
    }
}
