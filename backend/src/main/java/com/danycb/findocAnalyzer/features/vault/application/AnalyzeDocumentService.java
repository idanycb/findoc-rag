package com.danycb.findocAnalyzer.features.vault.application;

import com.danycb.findocAnalyzer.features.vault.application.in.AnalyzeDocumentUseCase;
import com.danycb.findocAnalyzer.features.vault.application.out.DocumentParserPort;
import com.danycb.findocAnalyzer.features.vault.application.out.DocumentRepositoryPort;
import com.danycb.findocAnalyzer.features.vault.application.out.ExternalStoragePort;
import com.danycb.findocAnalyzer.features.vault.application.out.FilingSectionsPort;
import com.danycb.findocAnalyzer.features.vault.application.out.VectorIndexPort;
import com.danycb.findocAnalyzer.features.vault.domain.Document;
import com.danycb.findocAnalyzer.features.vault.domain.DocumentSource;
import com.danycb.findocAnalyzer.features.vault.domain.FilingSectionsResult;
import com.danycb.findocAnalyzer.features.vault.domain.ParsedSection;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AnalyzeDocumentService implements AnalyzeDocumentUseCase {
    private final DocumentRepositoryPort repository;
    private final ExternalStoragePort objectStorage;
    private final DocumentParserPort documentParser;
    private final FilingSectionsPort filingSections;
    private final VectorIndexPort vectorIndex;
    private final VaultAuditLogger auditLogger;
    private TransactionTemplate analysisTransaction;
    private TransactionTemplate failureTransaction;

    @Autowired(required = false)
    void configureTransactions(PlatformTransactionManager transactionManager) {
        analysisTransaction = new TransactionTemplate(transactionManager);
        analysisTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        failureTransaction = new TransactionTemplate(transactionManager);
        failureTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public void analyze(UUID docId, String objectKey) {
        if (analysisTransaction == null) {
            analyzeWithoutTransactionManager(docId, objectKey);
            return;
        }
        try {
            analysisTransaction.executeWithoutResult(status -> analyzeAtomically(docId, objectKey));
        } catch (Exception failure) {
            failureTransaction.executeWithoutResult(status -> recordFailure(docId, failure));
        }
    }

    private void analyzeWithoutTransactionManager(UUID docId, String objectKey) {
        try {
            analyzeAtomically(docId, objectKey);
        } catch (Exception failure) {
            recordFailure(docId, failure);
        }
    }

    private void analyzeAtomically(UUID docId, String objectKey) {
        var optionalDocument = repository.findById(docId);
        if (optionalDocument.isEmpty()) {
            auditLogger.analysisSkipped(docId, "document_not_found");
            return;
        }
        Document document = optionalDocument.get();

        if (document.cannotAnalyze()) {
            auditLogger.analysisSkipped(document, "status_mismatch");
            return;
        }

        document.markProcessing();
        document = repository.save(document);
        auditLogger.analysisStarted(document);

        List<ParsedSection> sections;
        if (document.getSource() == DocumentSource.EDGAR) {
            FilingSectionsResult filing = filingSections.fetchSections(
                    document.getTicker(), document.getAccessionNumber());
            validateFilingIdentity(document, filing);
            reconcileFilingMetadata(document, filing);
            sections = filing.sections();
            document.markSearchable(filing.hasSearchableSections());
            if (filing.hasSearchableSections()) {
                vectorIndex.ingest(sections, document);
            } else {
                vectorIndex.deleteByDocumentId(document.getId());
            }
        } else {
            sections = uploadSections(document, objectKey);
            document.markSearchable(true);
            vectorIndex.ingest(sections, document);
        }

        document.markCompleted();
        repository.save(document);
        auditLogger.analysisCompleted(document, sections.size());
    }

    private void recordFailure(UUID docId, Exception failure) {
        repository.findById(docId).ifPresent(document -> {
            document.markFailed();
            Document failed = repository.save(document);
            auditLogger.analysisFailed(failed, failure);
        });
    }

    private void validateFilingIdentity(Document document, FilingSectionsResult filing) {
        if (!document.getAccessionNumber().equals(filing.accessionNumber())) {
            throw new IllegalStateException("EDGAR sidecar returned a different accession number");
        }
        if (document.getFormType() != null && !document.getFormType().equals(filing.formType())) {
            throw new IllegalStateException("EDGAR sidecar returned a different filing form");
        }
    }

    private void reconcileFilingMetadata(Document document, FilingSectionsResult filing) {
        UUID resolvedOriginalId = null;
        if (document.isAmendment() && filing.amendsAccessionNumber() != null) {
            resolvedOriginalId = repository
                    .findByTeamIdAndAccessionNumber(document.getTeamId(), filing.amendsAccessionNumber())
                    .filter(original -> OriginalFilingEligibility.isEligible(document, original))
                    .map(Document::getId)
                    .orElse(null);
        }
        document.reconcileAmendmentReference(filing.amendsAccessionNumber(), resolvedOriginalId);
        document.reconcileFilingDates(filing.filingDate(), filing.reportDate());
    }

    private List<ParsedSection> uploadSections(Document document, String objectKey) {
        byte[] content = objectStorage.download(objectKey);
        return documentParser.parse(content, document.getFileName(), document.getContentType());
    }
}
