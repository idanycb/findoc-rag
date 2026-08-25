package com.danycb.findocAnalyzer.features.vault.application;

import com.danycb.findocAnalyzer.features.vault.application.dto.DocumentAnalysisMessage;
import com.danycb.findocAnalyzer.features.vault.application.dto.ImportFilingCommand;
import com.danycb.findocAnalyzer.features.vault.application.dto.ImportFilingResult;
import com.danycb.findocAnalyzer.features.vault.application.in.ImportFilingUseCase;
import com.danycb.findocAnalyzer.features.vault.application.out.AnalysisQueuePort;
import com.danycb.findocAnalyzer.features.vault.application.out.DocumentRepositoryPort;
import com.danycb.findocAnalyzer.features.vault.domain.Document;
import com.danycb.findocAnalyzer.features.vault.domain.DocumentSource;
import com.danycb.findocAnalyzer.features.vault.domain.DocumentStatus;
import com.danycb.findocAnalyzer.features.vault.domain.AmendmentLinkStatus;
import com.danycb.findocAnalyzer.features.vault.domain.EdgarFormType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ImportFilingService implements ImportFilingUseCase {
    private final DocumentRepositoryPort repository;
    private final AnalysisQueuePort analysisQueue;
    private final VaultAuditLogger auditLogger;

    @Override
    @Transactional
    public ImportFilingResult importFiling(ImportFilingCommand command, UUID teamId) {
        EdgarFormType form = EdgarFormType.parse(command.formType());
        String accessionNumber = requireAccession(command.accessionNumber());

        var existing = repository.findByTeamIdAndAccessionNumber(teamId, accessionNumber);
        if (existing.isPresent()) {
            enqueuePendingRetry(existing.get());
            return result(existing.get());
        }

        UUID originalDocumentId = null;
        AmendmentLinkStatus linkStatus = AmendmentLinkStatus.NOT_APPLICABLE;
        if (form.isAmendment()) {
            linkStatus = AmendmentLinkStatus.UNRESOLVED;
            if (!isBlank(command.amendsAccessionNumber())) {
                originalDocumentId = repository
                        .findByTeamIdAndAccessionNumber(teamId, command.amendsAccessionNumber())
                        .filter(candidate -> OriginalFilingEligibility.isEligible(
                                teamId, form.baseForm(), candidate))
                        .map(Document::getId)
                        .orElse(null);
                if (originalDocumentId != null) {
                    linkStatus = AmendmentLinkStatus.LINKED;
                }
            }
        }

        Document document = Document.builder()
                .teamId(teamId)
                .fileName(fileName(command))
                .status(DocumentStatus.PENDING)
                .source(DocumentSource.EDGAR)
                .cik(command.cik())
                .ticker(normalizeTicker(command.ticker()))
                .companyName(command.companyName())
                .formType(form.value())
                .baseFormType(form.baseForm())
                .amendment(form.isAmendment())
                .amendsAccessionNumber(command.amendsAccessionNumber())
                .amendsDocumentId(originalDocumentId)
                .amendmentLinkStatus(linkStatus)
                .fiscalPeriod(command.fiscalPeriod())
                .reportDate(command.reportDate())
                .filingDate(command.filingDate())
                .accessionNumber(accessionNumber)
                .sourceUrl(command.sourceUrl())
                .build();

        DocumentRepositoryPort.InsertResult persistence = repository.insertOrGet(document);
        Document saved = persistence.document();
        if (!persistence.inserted()) {
            enqueuePendingRetry(saved);
            return result(saved);
        }
        if (!form.isAmendment()) {
            reconcileWaitingAmendments(saved);
        }
        enqueueAfterCommit(saved);
        auditLogger.analysisRequested(saved);
        return result(saved);
    }

    private void enqueueAfterCommit(Document saved) {
        DocumentAnalysisMessage message = new DocumentAnalysisMessage(saved.getId(), null);
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            claimAndPublish(message);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                claimAndPublish(message);
            }
        });
    }

    private void claimAndPublish(DocumentAnalysisMessage message) {
        if (!repository.claimAnalysisPublication(message.documentId())) {
            return;
        }
        try {
            analysisQueue.enqueue(message);
        } catch (RuntimeException failure) {
            repository.releaseAnalysisPublication(message.documentId());
            throw failure;
        }
    }

    private void enqueuePendingRetry(Document document) {
        if (document.getStatus() == DocumentStatus.PENDING) {
            enqueueAfterCommit(document);
        }
    }

    private void reconcileWaitingAmendments(Document original) {
        repository.findByTeamIdAndAmendsAccessionNumber(
                        original.getTeamId(), original.getAccessionNumber())
                .stream()
                .filter(Document::isAmendment)
                .filter(amendment -> amendment.getAmendsDocumentId() == null)
                .filter(amendment -> OriginalFilingEligibility.isEligible(amendment, original))
                .forEach(amendment -> {
                    amendment.linkToOriginal(original.getId());
                    repository.save(amendment);
                });
    }

    private ImportFilingResult result(Document document) {
        return new ImportFilingResult(document.getId(), document.getFileName(), document.getStatus());
    }

    private String requireAccession(String accessionNumber) {
        if (isBlank(accessionNumber)) {
            throw new IllegalArgumentException("EDGAR accession number must not be blank");
        }
        return accessionNumber.trim();
    }

    private String fileName(ImportFilingCommand command) {
        String ticker = normalizeTicker(command.ticker());
        String form = isBlank(command.formType()) ? "Filing" : command.formType().trim();
        String period = isBlank(command.fiscalPeriod()) ? null : command.fiscalPeriod().trim();
        if (period != null) {
            return String.format("%s %s %s", ticker, form, period);
        }
        if (command.reportDate() != null) {
            return String.format("%s %s %s", ticker, form, command.reportDate().getYear());
        }
        return String.format("%s %s %s", ticker, form, command.accessionNumber());
    }

    private String normalizeTicker(String ticker) {
        return ticker == null ? null : ticker.trim().toUpperCase(Locale.ROOT);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
