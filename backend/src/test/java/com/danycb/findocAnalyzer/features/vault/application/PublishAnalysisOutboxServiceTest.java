package com.danycb.findocAnalyzer.features.vault.application;

import com.danycb.findocAnalyzer.features.vault.application.dto.DocumentAnalysisMessage;
import com.danycb.findocAnalyzer.features.vault.application.out.AnalysisOutboxPort;
import com.danycb.findocAnalyzer.features.vault.application.out.AnalysisQueuePort;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PublishAnalysisOutboxServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-26T18:00:00Z");
    private static final Duration LEASE = Duration.ofMinutes(1);
    private static final Duration RETRY_DELAY = Duration.ofSeconds(30);
    private static final Duration MAX_RETRY_DELAY = Duration.ofMinutes(5);

    @Test
    void claimedOutboxRequestsUseAClaimTokenWhenTheyAreAcknowledged() {
        Class<?> claimedRequest = AnalysisOutboxPort.ClaimedAnalysisRequest.class;
        Method claimToken = requiredMethod(claimedRequest, "claimToken");

        assertThat(claimToken.getReturnType()).isEqualTo(UUID.class);
        assertThat(requiredMethod(AnalysisOutboxPort.class,
                "markPublished", UUID.class, UUID.class, Instant.class).getReturnType()).isEqualTo(void.class);
        assertThat(requiredMethod(AnalysisOutboxPort.class,
                "markFailed", UUID.class, UUID.class, Instant.class, String.class).getReturnType()).isEqualTo(void.class);
    }

    private Method requiredMethod(Class<?> type, String name, Class<?>... parameterTypes) {
        try {
            return type.getMethod(name, parameterTypes);
        } catch (NoSuchMethodException missing) {
            throw new AssertionError(type.getSimpleName() + " must expose " + name + " with the fencing-token contract", missing);
        }
    }

    @Test
    void claimedMessageIsPublishedAndMarkedSuccessful() {
        UUID outboxId = UUID.randomUUID();
        UUID claimToken = UUID.randomUUID();
        DocumentAnalysisMessage message = new DocumentAnalysisMessage(UUID.randomUUID(), "files/document");
        RecordingOutbox outbox = new RecordingOutbox(new AnalysisOutboxPort.ClaimedAnalysisRequest(
                outboxId, claimToken, message, 1));
        RecordingQueue queue = new RecordingQueue();

        int published = new PublishAnalysisOutboxService(outbox, queue)
                .publishDue(NOW, 10, LEASE, RETRY_DELAY, MAX_RETRY_DELAY);

        assertThat(published).isEqualTo(1);
        assertThat(queue.messages).containsExactly(message);
        assertThat(outbox.published).containsExactly(new Published(outboxId, claimToken, NOW));
        assertThat(outbox.failures).isEmpty();
    }

    @Test
    void failedMessageIsScheduledForRetryAndDoesNotStopLaterMessages() {
        UUID failedId = UUID.randomUUID();
        UUID successfulId = UUID.randomUUID();
        UUID failedToken = UUID.randomUUID();
        UUID successfulToken = UUID.randomUUID();
        DocumentAnalysisMessage failed = new DocumentAnalysisMessage(UUID.randomUUID(), null);
        DocumentAnalysisMessage successful = new DocumentAnalysisMessage(UUID.randomUUID(), "files/second");
        RecordingOutbox outbox = new RecordingOutbox(
                new AnalysisOutboxPort.ClaimedAnalysisRequest(failedId, failedToken, failed, 4),
                new AnalysisOutboxPort.ClaimedAnalysisRequest(successfulId, successfulToken, successful, 1));
        FailingFirstQueue queue = new FailingFirstQueue();

        int processed = new PublishAnalysisOutboxService(outbox, queue)
                .publishDue(NOW, 10, LEASE, RETRY_DELAY, MAX_RETRY_DELAY);

        assertThat(processed).isEqualTo(2);
        assertThat(queue.messages).containsExactly(successful);
        assertThat(outbox.published).containsExactly(new Published(successfulId, successfulToken, NOW));
        assertThat(outbox.failures).singleElement().satisfies(failure -> {
            assertThat(failure.outboxId()).isEqualTo(failedId);
            assertThat(failure.claimToken()).isEqualTo(failedToken);
            assertThat(failure.nextAttemptAt()).isEqualTo(NOW.plus(Duration.ofMinutes(4)));
            assertThat(failure.error()).isEqualTo("IllegalStateException: queue unavailable");
        });
    }

    @Test
    void exponentialRetryIsCappedAtTheConfiguredMaximum() {
        UUID outboxId = UUID.randomUUID();
        UUID claimToken = UUID.randomUUID();
        DocumentAnalysisMessage message = new DocumentAnalysisMessage(UUID.randomUUID(), null);
        RecordingOutbox outbox = new RecordingOutbox(new AnalysisOutboxPort.ClaimedAnalysisRequest(
                outboxId, claimToken, message, 20));

        new PublishAnalysisOutboxService(outbox, ignored -> {
            throw new IllegalStateException("still unavailable");
        }).publishDue(NOW, 10, LEASE, RETRY_DELAY, MAX_RETRY_DELAY);

        assertThat(outbox.failures).singleElement().satisfies(failure ->
                assertThat(failure.nextAttemptAt()).isEqualTo(NOW.plus(MAX_RETRY_DELAY)));
    }

    static class RecordingOutbox implements AnalysisOutboxPort {
        private final List<ClaimedAnalysisRequest> claimed;
        final List<Published> published = new ArrayList<>();
        final List<Failed> failures = new ArrayList<>();

        RecordingOutbox(ClaimedAnalysisRequest... claimed) {
            this.claimed = List.of(claimed);
        }

        @Override public void enqueue(DocumentAnalysisMessage message) { }
        @Override public List<ClaimedAnalysisRequest> claimDue(Instant now, int limit, Duration leaseDuration) { return claimed; }
        @Override public void markPublished(UUID outboxId, UUID claimToken, Instant publishedAt) { published.add(new Published(outboxId, claimToken, publishedAt)); }
        @Override public void markFailed(UUID outboxId, UUID claimToken, Instant nextAttemptAt, String error) { failures.add(new Failed(outboxId, claimToken, nextAttemptAt, error)); }
    }

    static class RecordingQueue implements AnalysisQueuePort {
        final List<DocumentAnalysisMessage> messages = new ArrayList<>();
        @Override public void enqueue(DocumentAnalysisMessage message) { messages.add(message); }
    }

    static final class FailingFirstQueue extends RecordingQueue {
        private boolean fail = true;
        @Override public void enqueue(DocumentAnalysisMessage message) {
            if (fail) {
                fail = false;
                throw new IllegalStateException("queue unavailable");
            }
            super.enqueue(message);
        }
    }

    record Published(UUID outboxId, UUID claimToken, Instant publishedAt) { }
    record Failed(UUID outboxId, UUID claimToken, Instant nextAttemptAt, String error) { }
}
