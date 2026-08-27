package com.danycb.findocAnalyzer.features.vault.adapter.out.persistence;

import com.danycb.findocAnalyzer.features.vault.application.dto.DocumentAnalysisMessage;
import com.danycb.findocAnalyzer.features.vault.application.event.AnalysisOutboxEnqueuedEvent;
import com.danycb.findocAnalyzer.features.vault.application.out.AnalysisOutboxPort;
import com.danycb.findocAnalyzer.features.vault.application.out.AnalysisOutboxMaintenancePort;
import com.danycb.findocAnalyzer.features.vault.application.out.AnalysisRequestReceiptPort;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class AnalysisOutboxRepository implements
        AnalysisOutboxPort, AnalysisRequestReceiptPort, AnalysisOutboxMaintenancePort {
    private final EntityManager entityManager;
    private final ApplicationEventPublisher events;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(DocumentAnalysisMessage message) {
        UUID outboxId = UUID.randomUUID();
        int inserted = entityManager.createNativeQuery("""
                        INSERT INTO analysis_outbox
                            (id, document_id, object_key, created_at, next_attempt_at)
                        VALUES (:id, :documentId, :objectKey, :now, :now)
                        ON CONFLICT (document_id) WHERE processing_completed_at IS NULL
                        DO NOTHING
                        """)
                .setParameter("id", outboxId)
                .setParameter("documentId", message.documentId())
                .setParameter("objectKey", message.objectKey())
                .setParameter("now", Instant.now())
                .executeUpdate();
        if (inserted == 1) {
            events.publishEvent(new AnalysisOutboxEnqueuedEvent(outboxId));
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<ClaimedAnalysisRequest> claimDue(Instant now, int limit, Duration leaseDuration) {
        if (limit <= 0) {
            return List.of();
        }
        UUID claimToken = UUID.randomUUID();
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery("""
                        WITH due AS (
                            SELECT id
                            FROM analysis_outbox
                            WHERE published_at IS NULL
                              AND next_attempt_at <= :now
                              AND (claim_expires_at IS NULL OR claim_expires_at <= :now)
                            ORDER BY next_attempt_at, created_at
                            FOR UPDATE SKIP LOCKED
                            LIMIT :limit
                        )
                        UPDATE analysis_outbox AS outbox
                        SET claim_expires_at = :claimExpiresAt,
                            claim_token = :claimToken,
                            attempt_count = outbox.attempt_count + 1
                        FROM due
                        WHERE outbox.id = due.id
                        RETURNING outbox.id, outbox.claim_token, outbox.document_id,
                                  outbox.object_key, outbox.attempt_count
                        """)
                .setParameter("now", now)
                .setParameter("claimExpiresAt", now.plus(leaseDuration))
                .setParameter("claimToken", claimToken)
                .setParameter("limit", limit)
                .getResultList();
        return rows.stream()
                .map(this::toClaimedRequest)
                .toList();
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markPublished(UUID outboxId, UUID claimToken, Instant publishedAt) {
        entityManager.createNativeQuery("""
                        UPDATE analysis_outbox
                        SET published_at = :publishedAt,
                            claim_expires_at = NULL,
                            claim_token = NULL,
                            last_error = NULL
                        WHERE id = :outboxId
                          AND claim_token = :claimToken
                          AND published_at IS NULL
                        """)
                .setParameter("publishedAt", publishedAt)
                .setParameter("claimToken", claimToken)
                .setParameter("outboxId", outboxId)
                .executeUpdate();
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(UUID outboxId, UUID claimToken, Instant nextAttemptAt, String error) {
        entityManager.createNativeQuery("""
                        UPDATE analysis_outbox
                        SET next_attempt_at = :nextAttemptAt,
                            claim_expires_at = NULL,
                            claim_token = NULL,
                            last_error = :error
                        WHERE id = :outboxId
                          AND claim_token = :claimToken
                          AND published_at IS NULL
                        """)
                .setParameter("nextAttemptAt", nextAttemptAt)
                .setParameter("error", error)
                .setParameter("claimToken", claimToken)
                .setParameter("outboxId", outboxId)
                .executeUpdate();
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<ProcessingClaim> claim(
            UUID requestId, UUID documentId, Instant now, Duration leaseDuration) {
        UUID claimToken = UUID.randomUUID();
        @SuppressWarnings("unchecked")
        List<Object> rows = entityManager.createNativeQuery("""
                        UPDATE analysis_outbox
                        SET processing_started_at = COALESCE(processing_started_at, :now),
                            processing_claim_expires_at = :claimExpiresAt,
                            processing_claim_token = :claimToken,
                            processing_last_error = NULL
                        WHERE id = :requestId
                          AND document_id = :documentId
                          AND processing_completed_at IS NULL
                          AND (processing_claim_expires_at IS NULL
                               OR processing_claim_expires_at <= :now)
                        RETURNING processing_claim_token
                        """)
                .setParameter("requestId", requestId)
                .setParameter("documentId", documentId)
                .setParameter("now", now)
                .setParameter("claimExpiresAt", now.plus(leaseDuration))
                .setParameter("claimToken", claimToken)
                .getResultList();
        return rows.isEmpty()
                ? Optional.empty()
                : Optional.of(new ProcessingClaim(requestId, (UUID) rows.getFirst()));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markProcessed(UUID requestId, UUID claimToken, Instant processedAt) {
        entityManager.createNativeQuery("""
                        UPDATE analysis_outbox
                        SET processing_completed_at = :processedAt,
                            processing_claim_expires_at = NULL,
                            processing_claim_token = NULL,
                            processing_last_error = NULL
                        WHERE id = :requestId
                          AND processing_claim_token = :claimToken
                          AND processing_completed_at IS NULL
                        """)
                .setParameter("processedAt", processedAt)
                .setParameter("requestId", requestId)
                .setParameter("claimToken", claimToken)
                .executeUpdate();
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void release(UUID requestId, UUID claimToken, String error) {
        entityManager.createNativeQuery("""
                        UPDATE analysis_outbox
                        SET processing_claim_expires_at = NULL,
                            processing_claim_token = NULL,
                            processing_last_error = :error
                        WHERE id = :requestId
                          AND processing_claim_token = :claimToken
                          AND processing_completed_at IS NULL
                        """)
                .setParameter("requestId", requestId)
                .setParameter("claimToken", claimToken)
                .setParameter("error", error)
                .executeUpdate();
    }

    @Override
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public OutboxStatus inspect(Instant now, Instant stuckBefore, int detailLimit) {
        Object[] counts = (Object[]) entityManager.createNativeQuery("""
                        SELECT
                            count(*) FILTER (WHERE published_at IS NULL),
                            count(*) FILTER (
                                WHERE published_at IS NULL AND created_at <= :stuckBefore),
                            count(*) FILTER (
                                WHERE published_at IS NOT NULL AND processing_completed_at IS NULL),
                            count(*) FILTER (
                                WHERE published_at IS NOT NULL
                                  AND processing_completed_at IS NULL
                                  AND created_at <= :stuckBefore),
                            min(created_at) FILTER (
                                WHERE published_at IS NULL OR processing_completed_at IS NULL),
                            COALESCE(max(attempt_count) FILTER (
                                WHERE published_at IS NULL OR processing_completed_at IS NULL), 0)
                        FROM analysis_outbox
                        """)
                .setParameter("stuckBefore", stuckBefore)
                .getSingleResult();

        @SuppressWarnings("unchecked")
        List<Object[]> stuckRows = detailLimit <= 0
                ? List.of()
                : entityManager.createNativeQuery("""
                                SELECT id, document_id,
                                       CASE WHEN published_at IS NULL
                                            THEN 'PUBLICATION' ELSE 'PROCESSING' END,
                                       created_at, attempt_count, next_attempt_at,
                                       CASE WHEN published_at IS NULL
                                            THEN last_error ELSE processing_last_error END
                                FROM analysis_outbox
                                WHERE created_at <= :stuckBefore
                                  AND (published_at IS NULL OR processing_completed_at IS NULL)
                                ORDER BY created_at, id
                                LIMIT :detailLimit
                                """)
                        .setParameter("stuckBefore", stuckBefore)
                        .setParameter("detailLimit", detailLimit)
                        .getResultList();

        return new OutboxStatus(
                now,
                number(counts[0]).longValue(),
                number(counts[1]).longValue(),
                number(counts[2]).longValue(),
                number(counts[3]).longValue(),
                instant(counts[4]),
                number(counts[5]).intValue(),
                stuckRows.stream().map(this::toStuckRequest).toList());
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int deletePublishedProcessedBefore(Instant cutoff, int limit) {
        if (limit <= 0) {
            return 0;
        }
        return entityManager.createNativeQuery("""
                        WITH expired AS (
                            SELECT id
                            FROM analysis_outbox
                            WHERE published_at < :cutoff
                              AND processing_completed_at IS NOT NULL
                            ORDER BY published_at, id
                            LIMIT :limit
                            FOR UPDATE SKIP LOCKED
                        )
                        DELETE FROM analysis_outbox AS outbox
                        USING expired
                        WHERE outbox.id = expired.id
                        """)
                .setParameter("cutoff", cutoff)
                .setParameter("limit", limit)
                .executeUpdate();
    }

    private ClaimedAnalysisRequest toClaimedRequest(Object[] row) {
        UUID outboxId = (UUID) row[0];
        UUID claimToken = (UUID) row[1];
        UUID documentId = (UUID) row[2];
        String objectKey = (String) row[3];
        int attemptCount = ((Number) row[4]).intValue();
        return new ClaimedAnalysisRequest(
                outboxId,
                claimToken,
                new DocumentAnalysisMessage(outboxId, documentId, objectKey),
                attemptCount);
    }

    private StuckRequest toStuckRequest(Object[] row) {
        return new StuckRequest(
                (UUID) row[0],
                (UUID) row[1],
                Stage.valueOf((String) row[2]),
                instant(row[3]),
                number(row[4]).intValue(),
                instant(row[5]),
                (String) row[6]);
    }

    private Number number(Object value) {
        return value == null ? 0 : (Number) value;
    }

    private Instant instant(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Instant instant) {
            return instant;
        }
        return ((OffsetDateTime) value).toInstant();
    }
}
