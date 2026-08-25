package com.danycb.findocAnalyzer.features.vault.application;

import com.danycb.findocAnalyzer.features.vault.application.out.DocumentParserPort;
import com.danycb.findocAnalyzer.features.vault.application.out.DocumentRepositoryPort;
import com.danycb.findocAnalyzer.features.vault.application.out.ExternalStoragePort;
import com.danycb.findocAnalyzer.features.vault.application.out.FilingSectionsPort;
import com.danycb.findocAnalyzer.features.vault.application.out.VectorIndexPort;
import com.danycb.findocAnalyzer.features.vault.domain.Document;
import com.danycb.findocAnalyzer.features.vault.domain.DocumentSource;
import com.danycb.findocAnalyzer.features.vault.domain.DocumentStatus;
import com.danycb.findocAnalyzer.features.vault.domain.FilingSectionsResult;
import com.danycb.findocAnalyzer.features.vault.domain.ParsedSection;
import com.danycb.findocAnalyzer.features.vault.domain.AmendmentLinkStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;

class AnalyzeDocumentServiceTest {
    private static final String ORIGINAL = "0000320193-24-000123";
    private static final String AMENDMENT = "0000320193-25-000020";

    private final FakeDocumentRepository repository = new FakeDocumentRepository();
    private final RecordingStorage storage = new RecordingStorage();
    private final RecordingParser parser = new RecordingParser();
    private final RecordingFilingSections filingSections = new RecordingFilingSections();
    private final RecordingVectorIndex vectorIndex = new RecordingVectorIndex();
    private final AnalyzeDocumentService service = new AnalyzeDocumentService(
            repository, storage, parser, filingSections, vectorIndex, new VaultAuditLogger());

    @BeforeEach
    void searchableResult() {
        filingSections.result = new FilingSectionsResult(
                AMENDMENT,
                ORIGINAL,
                "10-K/A",
                LocalDate.of(2025, 1, 2),
                LocalDate.of(2024, 9, 28),
                true,
                List.of(new ParsedSection(1, "Item 1A", "Risk Factors", "risk text")));
    }

    @Test
    void uploadDocumentDownloadsParsesAndIndexesContent() {
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
        assertThat(vectorIndex.sections).containsExactly(new ParsedSection(2, null, "Upload Section", "upload text"));
        assertThat(vectorIndex.document.getId()).isEqualTo(document.getId());
        assertThat(repository.store.get(document.getId()).getStatus()).isEqualTo(DocumentStatus.COMPLETED);
    }

    @Test
    void searchableEdgarDocumentIndexesStableItemsAndCompletes() {
        Document document = repository.save(edgarDocument(AMENDMENT, "10-K/A"));

        service.analyze(document.getId(), null);

        assertThat(storage.downloadedKey).isNull();
        assertThat(parser.called).isFalse();
        assertThat(filingSections.called).isTrue();
        assertThat(vectorIndex.sections).singleElement().satisfies(section ->
                assertThat(section.item()).isEqualTo("Item 1A"));
        Document completed = repository.store.get(document.getId());
        assertThat(completed.getStatus()).isEqualTo(DocumentStatus.COMPLETED);
        assertThat(completed.isSearchable()).isTrue();
        assertThat(completed.getAmendsAccessionNumber()).isEqualTo(ORIGINAL);
    }

    @Test
    void validNonSearchableAmendmentCompletesWithoutVectorIngestion() {
        filingSections.result = new FilingSectionsResult(
                AMENDMENT, ORIGINAL, "10-K/A",
                LocalDate.of(2025, 1, 2), LocalDate.of(2024, 9, 28), false, List.of());
        Document document = repository.save(edgarDocument(AMENDMENT, "10-K/A"));

        service.analyze(document.getId(), null);

        assertThat(vectorIndex.called).isFalse();
        Document completed = repository.store.get(document.getId());
        assertThat(completed.getStatus()).isEqualTo(DocumentStatus.COMPLETED);
        assertThat(completed.isSearchable()).isFalse();
        assertThat(completed.getAmendsAccessionNumber()).isEqualTo(ORIGINAL);
    }

    @Test
    void validNonSearchableOriginalAlsoCompletesWithoutVectorIngestion() {
        filingSections.result = new FilingSectionsResult(
                ORIGINAL, null, "10-K",
                LocalDate.of(2024, 11, 1), LocalDate.of(2024, 9, 28), false, List.of());
        Document document = repository.save(edgarDocument(ORIGINAL, "10-K"));

        service.analyze(document.getId(), null);

        assertThat(vectorIndex.called).isFalse();
        assertThat(repository.store.get(document.getId()).getStatus()).isEqualTo(DocumentStatus.COMPLETED);
        assertThat(repository.store.get(document.getId()).isSearchable()).isFalse();
    }

    @Test
    void sidecarFailureMarksEdgarDocumentFailed() {
        filingSections.failure = new IllegalStateException("EDGAR upstream failed");
        Document document = repository.save(edgarDocument(AMENDMENT, "10-K/A"));

        service.analyze(document.getId(), null);

        assertThat(vectorIndex.called).isFalse();
        assertThat(repository.store.get(document.getId()).getStatus()).isEqualTo(DocumentStatus.FAILED);
    }

    @Test
    void mismatchedReturnedAccessionFailsInsteadOfIndexingWrongFiling() {
        filingSections.result = new FilingSectionsResult(
                "wrong-accession", ORIGINAL, "10-K/A",
                LocalDate.of(2025, 1, 2), LocalDate.of(2024, 9, 28), true,
                List.of(new ParsedSection(1, "Item 1A", "Risk Factors", "risk text")));
        Document document = repository.save(edgarDocument(AMENDMENT, "10-K/A"));

        service.analyze(document.getId(), null);

        assertThat(vectorIndex.called).isFalse();
        assertThat(repository.store.get(document.getId()).getStatus()).isEqualTo(DocumentStatus.FAILED);
    }

    @Test
    void sidecarNullAmendmentReferenceClearsResolvedRelationshipAtomically() {
        UUID teamId = UUID.randomUUID();
        Document document = repository.save(edgarDocument(teamId, AMENDMENT, "10-K/A")
                .amendsAccessionNumber(ORIGINAL)
                .amendsDocumentId(UUID.randomUUID())
                .amendmentLinkStatus(AmendmentLinkStatus.LINKED)
                .build());
        filingSections.result = filingResult(null, LocalDate.of(2025, 1, 2), LocalDate.of(2024, 9, 28));

        service.analyze(document.getId(), null);

        Document reconciled = repository.store.get(document.getId());
        assertThat(reconciled.getAmendsAccessionNumber()).isNull();
        assertThat(reconciled.getAmendsDocumentId()).isNull();
        assertThat(reconciled.getAmendmentLinkStatus()).isEqualTo(AmendmentLinkStatus.UNRESOLVED);
    }

    @Test
    void changedUnresolvedReferenceClearsPreviouslyResolvedOriginal() {
        UUID teamId = UUID.randomUUID();
        Document document = repository.save(edgarDocument(teamId, AMENDMENT, "10-K/A")
                .amendsAccessionNumber(ORIGINAL)
                .amendsDocumentId(UUID.randomUUID())
                .amendmentLinkStatus(AmendmentLinkStatus.LINKED)
                .build());
        filingSections.result = filingResult("new-unresolved-accession",
                LocalDate.of(2025, 1, 2), LocalDate.of(2024, 9, 28));

        service.analyze(document.getId(), null);

        Document reconciled = repository.store.get(document.getId());
        assertThat(reconciled.getAmendsAccessionNumber()).isEqualTo("new-unresolved-accession");
        assertThat(reconciled.getAmendsDocumentId()).isNull();
        assertThat(reconciled.getAmendmentLinkStatus()).isEqualTo(AmendmentLinkStatus.UNRESOLVED);
    }

    @Test
    void changedReferenceLinksOnlyToMatchingBaseFormOriginal() {
        UUID teamId = UUID.randomUUID();
        Document correctOriginal = repository.save(edgarDocument(teamId, "new-original", "10-K").build());
        Document amendment = repository.save(edgarDocument(teamId, AMENDMENT, "10-K/A").build());
        filingSections.result = filingResult(correctOriginal.getAccessionNumber(),
                LocalDate.of(2025, 1, 2), LocalDate.of(2024, 9, 28));

        service.analyze(amendment.getId(), null);

        Document reconciled = repository.store.get(amendment.getId());
        assertThat(reconciled.getAmendsDocumentId()).isEqualTo(correctOriginal.getId());
        assertThat(reconciled.getAmendmentLinkStatus()).isEqualTo(AmendmentLinkStatus.LINKED);
    }

    @Test
    void wrongBaseFormTargetRemainsUnresolved() {
        UUID teamId = UUID.randomUUID();
        repository.save(edgarDocument(teamId, "wrong-form-target", "10-Q").build());
        Document amendment = repository.save(edgarDocument(teamId, AMENDMENT, "10-K/A").build());
        filingSections.result = filingResult("wrong-form-target",
                LocalDate.of(2025, 1, 2), LocalDate.of(2024, 9, 28));

        service.analyze(amendment.getId(), null);

        Document reconciled = repository.store.get(amendment.getId());
        assertThat(reconciled.getAmendsAccessionNumber()).isEqualTo("wrong-form-target");
        assertThat(reconciled.getAmendsDocumentId()).isNull();
        assertThat(reconciled.getAmendmentLinkStatus()).isEqualTo(AmendmentLinkStatus.UNRESOLVED);
    }

    @Test
    void amendmentTargetRemainsUnresolved() {
        UUID teamId = UUID.randomUUID();
        repository.save(edgarDocument(teamId, "amendment-target", "10-K/A").build());
        Document amendment = repository.save(edgarDocument(teamId, AMENDMENT, "10-K/A").build());
        filingSections.result = filingResult("amendment-target",
                LocalDate.of(2025, 1, 2), LocalDate.of(2024, 9, 28));

        service.analyze(amendment.getId(), null);

        Document reconciled = repository.store.get(amendment.getId());
        assertThat(reconciled.getAmendsAccessionNumber()).isEqualTo("amendment-target");
        assertThat(reconciled.getAmendsDocumentId()).isNull();
        assertThat(reconciled.getAmendmentLinkStatus()).isEqualTo(AmendmentLinkStatus.UNRESOLVED);
    }

    @Test
    void nonEdgarTargetRemainsUnresolved() {
        UUID teamId = UUID.randomUUID();
        repository.save(edgarDocument(teamId, "upload-target", "10-K")
                .source(DocumentSource.UPLOAD)
                .build());
        Document amendment = repository.save(edgarDocument(teamId, AMENDMENT, "10-K/A").build());
        filingSections.result = filingResult("upload-target",
                LocalDate.of(2025, 1, 2), LocalDate.of(2024, 9, 28));

        service.analyze(amendment.getId(), null);

        Document reconciled = repository.store.get(amendment.getId());
        assertThat(reconciled.getAmendsAccessionNumber()).isEqualTo("upload-target");
        assertThat(reconciled.getAmendsDocumentId()).isNull();
        assertThat(reconciled.getAmendmentLinkStatus()).isEqualTo(AmendmentLinkStatus.UNRESOLVED);
    }

    @Test
    void crossTeamTargetRemainsUnresolved() {
        UUID amendmentTeam = UUID.randomUUID();
        repository.save(edgarDocument(UUID.randomUUID(), "other-team-original", "10-K").build());
        Document amendment = repository.save(edgarDocument(amendmentTeam, AMENDMENT, "10-K/A").build());
        filingSections.result = filingResult("other-team-original",
                LocalDate.of(2025, 1, 2), LocalDate.of(2024, 9, 28));

        service.analyze(amendment.getId(), null);

        Document reconciled = repository.store.get(amendment.getId());
        assertThat(reconciled.getAmendsDocumentId()).isNull();
        assertThat(reconciled.getAmendmentLinkStatus()).isEqualTo(AmendmentLinkStatus.UNRESOLVED);
    }

    @Test
    void sidecarDatesReplaceStaleImportDatesBeforeVectorOrdering() {
        UUID teamId = UUID.randomUUID();
        Document amendment = repository.save(edgarDocument(teamId, AMENDMENT, "10-K/A")
                .filingDate(LocalDate.of(2020, 1, 1))
                .reportDate(LocalDate.of(2019, 12, 31))
                .build());
        filingSections.result = filingResult(ORIGINAL,
                LocalDate.of(2025, 1, 2), LocalDate.of(2024, 9, 28));

        service.analyze(amendment.getId(), null);

        assertThat(vectorIndex.document.getFilingDate()).isEqualTo(LocalDate.of(2025, 1, 2));
        assertThat(vectorIndex.document.getReportDate()).isEqualTo(LocalDate.of(2024, 9, 28));
        assertThat(repository.store.get(amendment.getId()).getFilingDate()).isEqualTo(LocalDate.of(2025, 1, 2));
        assertThat(repository.store.get(amendment.getId()).getReportDate()).isEqualTo(LocalDate.of(2024, 9, 28));
    }

    @Test
    void nullSidecarDatesPreserveStoredDatesSoAmendmentOrderingRemainsStable() {
        UUID teamId = UUID.randomUUID();
        LocalDate storedFilingDate = LocalDate.of(2025, 1, 2);
        LocalDate storedReportDate = LocalDate.of(2024, 9, 28);
        Document amendment = repository.save(edgarDocument(teamId, AMENDMENT, "10-K/A")
                .filingDate(storedFilingDate)
                .reportDate(storedReportDate)
                .build());
        filingSections.result = filingResult(ORIGINAL, null, null);

        service.analyze(amendment.getId(), null);

        assertThat(vectorIndex.document.getFilingDate()).isEqualTo(storedFilingDate);
        assertThat(vectorIndex.document.getReportDate()).isEqualTo(storedReportDate);
        assertThat(vectorIndex.document.getFilingDate())
                .isAfter(LocalDate.of(2024, 11, 1));
        assertThat(repository.store.get(amendment.getId()).getFilingDate()).isEqualTo(storedFilingDate);
        assertThat(repository.store.get(amendment.getId()).getReportDate()).isEqualTo(storedReportDate);
    }

    @Test
    void searchableReanalysisDelegatesAtomicReplacementToVectorIngestion() {
        Document document = repository.save(edgarDocument(AMENDMENT, "10-K/A"));
        service.analyze(document.getId(), null);
        repository.store.get(document.getId()).markPendingForReanalysis();
        vectorIndex.events.clear();

        service.analyze(document.getId(), null);

        assertThat(vectorIndex.events).containsExactly("ingest:" + document.getId());
    }

    @Test
    void failedSearchableReanalysisDoesNotDeleteLastGoodVectorsBeforeReplacement() {
        Document document = repository.save(edgarDocument(AMENDMENT, "10-K/A"));
        service.analyze(document.getId(), null);
        repository.store.get(document.getId()).markPendingForReanalysis();
        vectorIndex.events.clear();
        vectorIndex.ingestFailure = new IllegalStateException("embedding failed");

        service.analyze(document.getId(), null);

        assertThat(vectorIndex.events).containsExactly("ingest:" + document.getId());
        assertThat(repository.store.get(document.getId()).getStatus()).isEqualTo(DocumentStatus.FAILED);
    }

    @Test
    void searchableToNonSearchableReanalysisRemovesStaleVectors() {
        Document document = repository.save(edgarDocument(AMENDMENT, "10-K/A"));
        service.analyze(document.getId(), null);
        repository.store.get(document.getId()).markPendingForReanalysis();
        filingSections.result = new FilingSectionsResult(
                AMENDMENT, ORIGINAL, "10-K/A",
                LocalDate.of(2025, 1, 2), LocalDate.of(2024, 9, 28), false, List.of());
        vectorIndex.events.clear();

        service.analyze(document.getId(), null);

        assertThat(vectorIndex.events).containsExactly("delete:" + document.getId());
        assertThat(repository.store.get(document.getId()).isSearchable()).isFalse();
    }

    private FilingSectionsResult filingResult(String amendsAccession, LocalDate filingDate, LocalDate reportDate) {
        return new FilingSectionsResult(
                AMENDMENT, amendsAccession, "10-K/A", filingDate, reportDate, true,
                List.of(new ParsedSection(1, "Item 1A", "Risk Factors", "risk text")));
    }

    private Document edgarDocument(String accession, String form) {
        return edgarDocument(UUID.randomUUID(), accession, form).build();
    }

    private Document.DocumentBuilder edgarDocument(UUID teamId, String accession, String form) {
        boolean amendment = form.endsWith("/A");
        return Document.builder()
                .id(UUID.randomUUID())
                .teamId(teamId)
                .fileName("AAPL " + form + " FY")
                .status(DocumentStatus.PENDING)
                .source(DocumentSource.EDGAR)
                .ticker("AAPL")
                .formType(form)
                .baseFormType(form.replace("/A", ""))
                .amendment(amendment)
                .amendmentLinkStatus(amendment
                        ? AmendmentLinkStatus.UNRESOLVED
                        : AmendmentLinkStatus.NOT_APPLICABLE)
                .accessionNumber(accession);
    }

    static class RecordingStorage implements ExternalStoragePort {
        String downloadedKey;

        @Override
        public String generateUploadUrl(UUID docId, String contentType, long contentLength) { return "upload"; }
        @Override
        public String generateViewUrl(UUID docId) { return "view"; }
        @Override
        public byte[] download(String objectKey) { downloadedKey = objectKey; return "bytes".getBytes(); }
        @Override
        public void delete(UUID docId) { }
        @Override
        public String buildObjectKey(UUID docId) { return "files/" + docId; }
    }

    static class RecordingParser implements DocumentParserPort {
        boolean called;

        @Override
        public List<ParsedSection> parse(byte[] content, String fileName, String contentType) {
            called = true;
            return List.of(new ParsedSection(2, null, "Upload Section", "upload text"));
        }
    }

    static class RecordingFilingSections implements FilingSectionsPort {
        boolean called;
        FilingSectionsResult result;
        RuntimeException failure;

        @Override
        public FilingSectionsResult fetchSections(String ticker, String accessionNumber) {
            called = true;
            if (failure != null) {
                throw failure;
            }
            return result;
        }
    }

    static class RecordingVectorIndex implements VectorIndexPort {
        boolean called;
        List<ParsedSection> sections;
        Document document;
        RuntimeException ingestFailure;
        final List<String> events = new ArrayList<>();

        @Override
        public void ingest(List<ParsedSection> sections, Document document) {
            called = true;
            this.sections = sections;
            this.document = document;
            events.add("ingest:" + document.getId());
            if (ingestFailure != null) {
                throw ingestFailure;
            }
        }

        @Override
        public void deleteByDocumentId(UUID docId) { events.add("delete:" + docId); }
    }

    static class FakeDocumentRepository implements DocumentRepositoryPort {
        final Map<UUID, Document> store = new LinkedHashMap<>();

        @Override public InsertResult insertOrGet(Document document) { return new InsertResult(save(document), true); }
        @Override public boolean claimAnalysisPublication(UUID documentId) { return true; }
        @Override public void releaseAnalysisPublication(UUID documentId) { }

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
                    .formType(document.getFormType())
                    .baseFormType(document.getBaseFormType())
                    .amendment(document.isAmendment())
                    .accessionNumber(document.getAccessionNumber())
                    .amendsAccessionNumber(document.getAmendsAccessionNumber())
                    .amendsDocumentId(document.getAmendsDocumentId())
                    .amendmentLinkStatus(document.getAmendmentLinkStatus())
                    .searchable(document.isSearchable())
                    .filingDate(document.getFilingDate())
                    .reportDate(document.getReportDate())
                    .build();
            store.put(id, stored);
            return stored;
        }

        @Override public Optional<Document> findById(UUID id) { return Optional.ofNullable(store.get(id)); }
        @Override public Optional<Document> findByIdAndTeamId(UUID id, UUID teamId) {
            return findById(id).filter(d -> teamId.equals(d.getTeamId()));
        }
        @Override public Optional<Document> findByTeamIdAndAccessionNumber(UUID teamId, String accession) {
            return store.values().stream().filter(d -> teamId.equals(d.getTeamId()))
                    .filter(d -> accession.equals(d.getAccessionNumber())).findFirst();
        }
        @Override public List<Document> findByTeamIdAndAmendsAccessionNumber(UUID teamId, String accession) {
            return List.of();
        }
        @Override public List<Document> findByTeamId(UUID teamId) {
            return store.values().stream().filter(d -> teamId.equals(d.getTeamId())).toList();
        }
        @Override public long countAll() { return store.size(); }
        @Override public void delete(Document document) { store.remove(document.getId()); }
    }
}
