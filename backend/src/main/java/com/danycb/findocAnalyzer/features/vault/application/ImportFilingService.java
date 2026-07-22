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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        Document document = Document.builder()
                .teamId(teamId)
                .fileName(fileName(command))
                .status(DocumentStatus.PENDING)
                .source(DocumentSource.EDGAR)
                .cik(command.cik())
                .ticker(normalizeTicker(command.ticker()))
                .companyName(command.companyName())
                .formType(command.formType())
                .fiscalPeriod(command.fiscalPeriod())
                .reportDate(command.reportDate())
                .filingDate(command.filingDate())
                .accessionNumber(command.accessionNumber())
                .sourceUrl(command.sourceUrl())
                .build();

        Document saved = repository.save(document);
        analysisQueue.enqueue(new DocumentAnalysisMessage(saved.getId(), null));
        auditLogger.analysisRequested(saved);
        return new ImportFilingResult(saved.getId(), saved.getFileName(), saved.getStatus());
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
