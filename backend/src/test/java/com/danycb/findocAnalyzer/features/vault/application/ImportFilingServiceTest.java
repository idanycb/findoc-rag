package com.danycb.findocAnalyzer.features.vault.application;

import com.danycb.findocAnalyzer.features.vault.application.dto.DocumentAnalysisMessage;
import com.danycb.findocAnalyzer.features.vault.application.dto.ImportFilingCommand;
import com.danycb.findocAnalyzer.features.vault.application.out.AnalysisOutboxPort;
import com.danycb.findocAnalyzer.features.vault.application.out.DocumentRepositoryPort;
import com.danycb.findocAnalyzer.features.vault.domain.AmendmentLinkStatus;
import com.danycb.findocAnalyzer.features.vault.domain.Document;
import com.danycb.findocAnalyzer.features.vault.domain.DocumentSource;
import com.danycb.findocAnalyzer.features.vault.domain.DocumentStatus;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImportFilingServiceTest {
    private static final String ORIGINAL = "0000320193-24-000123";
    private static final String AMENDMENT_ONE = "0000320193-25-000020";
    private static final String AMENDMENT_TWO = "0000320193-25-000021";

    private final FakeDocumentRepository repository = new FakeDocumentRepository();
    private final RecordingOutbox outbox = new RecordingOutbox();
    private final ImportFilingService service = new ImportFilingService(repository, outbox, new VaultAuditLogger());

    @Test
    void importsOriginalWithDerivedFormMetadataAndRecordsOneAnalysisRequest() {
        UUID teamId = UUID.randomUUID();

        var result = service.importFiling(original(), teamId);

        Document saved = repository.store.get(result.documentId());
        assertThat(saved.getSource()).isEqualTo(DocumentSource.EDGAR);
        assertThat(saved.getStatus()).isEqualTo(DocumentStatus.PENDING);
        assertThat(saved.getTeamId()).isEqualTo(teamId);
        assertThat(saved.getTicker()).isEqualTo("AAPL");
        assertThat(saved.getBaseFormType()).isEqualTo("10-K");
        assertThat(saved.isAmendment()).isFalse();
        assertThat(saved.getAmendmentLinkStatus()).isEqualTo(AmendmentLinkStatus.NOT_APPLICABLE);
        assertThat(outbox.messages).singleElement().satisfies(message -> {
            assertThat(message.documentId()).isEqualTo(result.documentId());
            assertThat(message.objectKey()).isNull();
        });
    }

    @Test
    void sameTeamAccessionIsIdempotentAndDoesNotRecordAnotherActiveRequest() {
        UUID teamId = UUID.randomUUID();

        var first = service.importFiling(original(), teamId);
        var second = service.importFiling(original(), teamId);

        assertThat(second.documentId()).isEqualTo(first.documentId());
        assertThat(repository.store).hasSize(1);
        assertThat(outbox.messages).hasSize(1);
    }

    @Test
    void existingCompletedFilingDoesNotRecordAnotherAnalysisRequest() {
        UUID teamId = UUID.randomUUID();
        Document completed = repository.save(Document.builder()
                .id(UUID.randomUUID())
                .teamId(teamId)
                .fileName("completed filing")
                .status(DocumentStatus.COMPLETED)
                .source(DocumentSource.EDGAR)
                .accessionNumber(ORIGINAL)
                .build());

        var result = service.importFiling(original(), teamId);

        assertThat(result.documentId()).isEqualTo(completed.getId());
        assertThat(outbox.messages).isEmpty();
    }

    @Test
    void concurrentSameTeamImportReturnsOneDocumentAndRecordsOneActiveRequest() throws Exception {
        UUID teamId = UUID.randomUUID();
        ConcurrentRepository concurrentRepository = new ConcurrentRepository();
        RecordingOutbox concurrentOutbox = new RecordingOutbox();
        ImportFilingService concurrentService = new ImportFilingService(
                concurrentRepository, concurrentOutbox, new VaultAuditLogger());

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> concurrentService.importFiling(original(), teamId));
            var second = executor.submit(() -> concurrentService.importFiling(original(), teamId));

            var firstResult = first.get(5, TimeUnit.SECONDS);
            var secondResult = second.get(5, TimeUnit.SECONDS);

            assertThat(firstResult).isEqualTo(secondResult);
            assertThat(concurrentRepository.documents).hasSize(1);
            assertThat(concurrentOutbox.messages).hasSize(1);
        }
    }

    @Test
    void sameAccessionCanBeImportedByDifferentTeams() {
        var first = service.importFiling(original(), UUID.randomUUID());
        var second = service.importFiling(original(), UUID.randomUUID());

        assertThat(second.documentId()).isNotEqualTo(first.documentId());
        assertThat(repository.store).hasSize(2);
        assertThat(outbox.messages).hasSize(2);
    }

    @Test
    void amendmentLinksToOriginalAlreadyInSameTeam() {
        UUID teamId = UUID.randomUUID();
        var original = service.importFiling(original(), teamId);

        var amendment = service.importFiling(amendment(AMENDMENT_ONE, ORIGINAL), teamId);

        Document saved = repository.store.get(amendment.documentId());
        assertThat(saved.getAccessionNumber()).isEqualTo(AMENDMENT_ONE);
        assertThat(saved.getAmendsAccessionNumber()).isEqualTo(ORIGINAL);
        assertThat(saved.getAmendsDocumentId()).isEqualTo(original.documentId());
        assertThat(saved.getBaseFormType()).isEqualTo("10-K");
        assertThat(saved.isAmendment()).isTrue();
        assertThat(saved.getAmendmentLinkStatus()).isEqualTo(AmendmentLinkStatus.LINKED);
    }

    @Test
    void amendmentImportedBeforeOriginalIsReconciledWhenOriginalArrives() {
        UUID teamId = UUID.randomUUID();
        var amendment = service.importFiling(amendment(AMENDMENT_ONE, ORIGINAL), teamId);

        Document unresolved = repository.store.get(amendment.documentId());
        assertThat(unresolved.getAmendsDocumentId()).isNull();
        assertThat(unresolved.getAmendmentLinkStatus()).isEqualTo(AmendmentLinkStatus.UNRESOLVED);

        var original = service.importFiling(original(), teamId);

        Document reconciled = repository.store.get(amendment.documentId());
        assertThat(reconciled.getAmendsDocumentId()).isEqualTo(original.documentId());
        assertThat(reconciled.getAmendmentLinkStatus()).isEqualTo(AmendmentLinkStatus.LINKED);
        assertThat(outbox.messages).hasSize(2);
    }

    @Test
    void severalAmendmentsCanReferenceOneOriginal() {
        UUID teamId = UUID.randomUUID();
        var original = service.importFiling(original(), teamId);
        var first = service.importFiling(amendment(AMENDMENT_ONE, ORIGINAL), teamId);
        var second = service.importFiling(amendment(AMENDMENT_TWO, ORIGINAL), teamId);

        assertThat(repository.store.get(first.documentId()).getAmendsDocumentId()).isEqualTo(original.documentId());
        assertThat(repository.store.get(second.documentId()).getAmendsDocumentId()).isEqualTo(original.documentId());
        assertThat(repository.store.get(first.documentId()).getAccessionNumber()).isNotEqualTo(
                repository.store.get(second.documentId()).getAccessionNumber());
    }

    @Test
    void amendmentWithoutInferredOriginalRemainsExplicitlyUnresolved() {
        UUID teamId = UUID.randomUUID();

        var result = service.importFiling(amendment(AMENDMENT_ONE, null), teamId);

        Document saved = repository.store.get(result.documentId());
        assertThat(saved.isAmendment()).isTrue();
        assertThat(saved.getAmendsAccessionNumber()).isNull();
        assertThat(saved.getAmendsDocumentId()).isNull();
        assertThat(saved.getAmendmentLinkStatus()).isEqualTo(AmendmentLinkStatus.UNRESOLVED);
    }

    @Test
    void amendmentNeverLinksToOriginalOwnedByAnotherTeam() {
        service.importFiling(original(), UUID.randomUUID());

        var result = service.importFiling(amendment(AMENDMENT_ONE, ORIGINAL), UUID.randomUUID());

        Document saved = repository.store.get(result.documentId());
        assertThat(saved.getAmendsDocumentId()).isNull();
        assertThat(saved.getAmendmentLinkStatus()).isEqualTo(AmendmentLinkStatus.UNRESOLVED);
    }

    @Test
    void amendmentDoesNotLinkToWrongBaseFormOriginalAtImportTime() {
        UUID teamId = UUID.randomUUID();
        service.importFiling(command("10-Q", "quarterly-original", null), teamId);

        var result = service.importFiling(amendment(AMENDMENT_ONE, "quarterly-original"), teamId);

        Document saved = repository.store.get(result.documentId());
        assertThat(saved.getAmendsDocumentId()).isNull();
        assertThat(saved.getAmendmentLinkStatus()).isEqualTo(AmendmentLinkStatus.UNRESOLVED);
    }

    @Test
    void amendmentDoesNotLinkToAnotherAmendmentAtImportTime() {
        UUID teamId = UUID.randomUUID();
        service.importFiling(amendment(AMENDMENT_ONE, null), teamId);

        var result = service.importFiling(amendment(AMENDMENT_TWO, AMENDMENT_ONE), teamId);

        Document saved = repository.store.get(result.documentId());
        assertThat(saved.getAmendsDocumentId()).isNull();
        assertThat(saved.getAmendmentLinkStatus()).isEqualTo(AmendmentLinkStatus.UNRESOLVED);
    }

    @Test
    void amendmentDoesNotLinkToNonEdgarDocumentAtImportTime() {
        UUID teamId = UUID.randomUUID();
        repository.save(Document.builder()
                .id(UUID.randomUUID())
                .teamId(teamId)
                .fileName("uploaded 10-K.pdf")
                .status(DocumentStatus.COMPLETED)
                .source(DocumentSource.UPLOAD)
                .formType("10-K")
                .baseFormType("10-K")
                .accessionNumber(ORIGINAL)
                .build());

        var result = service.importFiling(amendment(AMENDMENT_ONE, ORIGINAL), teamId);

        Document saved = repository.store.get(result.documentId());
        assertThat(saved.getAmendsDocumentId()).isNull();
        assertThat(saved.getAmendmentLinkStatus()).isEqualTo(AmendmentLinkStatus.UNRESOLVED);
    }

    @Test
    void waitingAmendmentWithWrongBaseFormRemainsUnresolvedWhenOriginalArrives() {
        UUID teamId = UUID.randomUUID();
        var amendment = service.importFiling(
                command("10-Q/A", AMENDMENT_ONE, ORIGINAL), teamId);

        service.importFiling(original(), teamId);

        Document saved = repository.store.get(amendment.documentId());
        assertThat(saved.getAmendsDocumentId()).isNull();
        assertThat(saved.getAmendmentLinkStatus()).isEqualTo(AmendmentLinkStatus.UNRESOLVED);
    }

    @Test
    void waitingAmendmentInAnotherTeamIsNotReconciledWhenOriginalArrives() {
        UUID amendmentTeam = UUID.randomUUID();
        UUID originalTeam = UUID.randomUUID();
        var amendment = service.importFiling(amendment(AMENDMENT_ONE, ORIGINAL), amendmentTeam);

        service.importFiling(original(), originalTeam);

        Document saved = repository.store.get(amendment.documentId());
        assertThat(saved.getAmendsDocumentId()).isNull();
        assertThat(saved.getAmendmentLinkStatus()).isEqualTo(AmendmentLinkStatus.UNRESOLVED);
    }

    @Test
    void unsupportedFormIsRejectedWithoutPersistenceOrQueueing() {
        ImportFilingCommand command = command("8-K", ORIGINAL, null);

        assertThatThrownBy(() -> service.importFiling(command, UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(repository.store).isEmpty();
        assertThat(outbox.messages).isEmpty();
    }

    private ImportFilingCommand original() {
        return command("10-K", ORIGINAL, null);
    }

    private ImportFilingCommand amendment(String accession, String amendsAccession) {
        return command("10-K/A", accession, amendsAccession);
    }

    private ImportFilingCommand command(String form, String accession, String amendsAccession) {
        return new ImportFilingCommand(
                "aapl",
                accession,
                amendsAccession,
                "320193",
                "Apple Inc.",
                form,
                "FY",
                LocalDate.parse("2024-09-28"),
                LocalDate.parse("2025-01-02"),
                "https://sec.example/" + accession);
    }

    static class RecordingOutbox implements AnalysisOutboxPort {
        final List<DocumentAnalysisMessage> messages = new ArrayList<>();

        @Override
        public void enqueue(DocumentAnalysisMessage message) {
            if (!messages.contains(message)) {
                messages.add(message);
            }
        }

        @Override public List<ClaimedAnalysisRequest> claimDue(Instant now, int limit, Duration leaseDuration) { return List.of(); }
        @Override public void markPublished(UUID outboxId, UUID claimToken, Instant publishedAt) { }
        @Override public void markFailed(UUID outboxId, UUID claimToken, Instant nextAttemptAt, String error) { }
    }

    static class ConcurrentRepository implements DocumentRepositoryPort {
        final Map<String, Document> documents = new ConcurrentHashMap<>();
        final CyclicBarrier initialLookups = new CyclicBarrier(2);
        final AtomicInteger lookupCount = new AtomicInteger();

        @Override
        public InsertResult insertOrGet(Document document) {
            try {
                return new InsertResult(save(document), true);
            } catch (DataIntegrityViolationException duplicate) {
                return new InsertResult(findByTeamIdAndAccessionNumber(
                        document.getTeamId(), document.getAccessionNumber()).orElseThrow(), false);
            }
        }

        @Override
        public Optional<Document> findByTeamIdAndAccessionNumber(UUID teamId, String accession) {
            if (lookupCount.incrementAndGet() <= 2) {
                try {
                    initialLookups.await(5, TimeUnit.SECONDS);
                } catch (Exception failure) {
                    throw new IllegalStateException(failure);
                }
            }
            return Optional.ofNullable(documents.get(teamId + ":" + accession));
        }

        @Override
        public synchronized Document save(Document document) {
            String key = document.getTeamId() + ":" + document.getAccessionNumber();
            if (documents.containsKey(key)) {
                throw new DataIntegrityViolationException("duplicate team/accession");
            }
            Document stored = Document.builder()
                    .id(UUID.randomUUID())
                    .teamId(document.getTeamId())
                    .fileName(document.getFileName())
                    .status(document.getStatus())
                    .source(document.getSource())
                    .ticker(document.getTicker())
                    .formType(document.getFormType())
                    .baseFormType(document.getBaseFormType())
                    .amendment(document.isAmendment())
                    .accessionNumber(document.getAccessionNumber())
                    .amendmentLinkStatus(document.getAmendmentLinkStatus())
                    .build();
            documents.put(key, stored);
            return stored;
        }

        @Override public Optional<Document> findById(UUID id) { return Optional.empty(); }
        @Override public Optional<Document> findByIdAndTeamId(UUID id, UUID teamId) { return Optional.empty(); }
        @Override public List<Document> findByTeamIdAndAmendsAccessionNumber(UUID teamId, String accession) {
            return List.of();
        }
        @Override public List<Document> findByTeamId(UUID teamId) { return List.of(); }
        @Override public long countAll() { return documents.size(); }
        @Override public void delete(Document document) { }
    }

    static class FakeDocumentRepository implements DocumentRepositoryPort {
        final Map<UUID, Document> store = new LinkedHashMap<>();

        @Override
        public InsertResult insertOrGet(Document document) {
            return findByTeamIdAndAccessionNumber(document.getTeamId(), document.getAccessionNumber())
                    .map(existing -> new InsertResult(existing, false))
                    .orElseGet(() -> new InsertResult(save(document), true));
        }

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
            return findById(id).filter(d -> teamId.equals(d.getTeamId()));
        }

        @Override
        public Optional<Document> findByTeamIdAndAccessionNumber(UUID teamId, String accessionNumber) {
            return store.values().stream()
                    .filter(d -> teamId.equals(d.getTeamId()))
                    .filter(d -> accessionNumber.equals(d.getAccessionNumber()))
                    .findFirst();
        }

        @Override
        public List<Document> findByTeamIdAndAmendsAccessionNumber(UUID teamId, String accessionNumber) {
            return store.values().stream()
                    .filter(d -> teamId.equals(d.getTeamId()))
                    .filter(d -> accessionNumber.equals(d.getAmendsAccessionNumber()))
                    .toList();
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
                    .baseFormType(document.getBaseFormType())
                    .amendment(document.isAmendment())
                    .amendsAccessionNumber(document.getAmendsAccessionNumber())
                    .amendsDocumentId(document.getAmendsDocumentId())
                    .amendmentLinkStatus(document.getAmendmentLinkStatus())
                    .searchable(document.isSearchable())
                    .fiscalPeriod(document.getFiscalPeriod())
                    .reportDate(document.getReportDate())
                    .filingDate(document.getFilingDate())
                    .accessionNumber(document.getAccessionNumber())
                    .sourceUrl(document.getSourceUrl())
                    .build();
        }
    }
}
