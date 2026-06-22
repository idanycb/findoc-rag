package com.danycb.findocAnalyzer.features.vault.application;

import com.danycb.findocAnalyzer.features.vault.application.in.AnalyzeDocumentUseCase;
import com.danycb.findocAnalyzer.features.vault.application.out.DocumentParserPort;
import com.danycb.findocAnalyzer.features.vault.application.out.DocumentRepositoryPort;
import com.danycb.findocAnalyzer.features.vault.application.out.ExternalStoragePort;
import com.danycb.findocAnalyzer.features.vault.application.out.VectorIndexPort;
import com.danycb.findocAnalyzer.features.vault.domain.Document;
import com.danycb.findocAnalyzer.features.vault.domain.ParsedSection;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AnalyzeDocumentService implements AnalyzeDocumentUseCase {
    private final DocumentRepositoryPort repository;
    private final ExternalStoragePort objectStorage;
    private final DocumentParserPort documentParser;
    private final VectorIndexPort vectorIndex;
    private final VaultAuditLogger auditLogger;

    @Override
    public void analyze(UUID docId, String objectKey) {
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

        try {
            byte[] content = objectStorage.download(objectKey);
            List<ParsedSection> sections = documentParser.parse(content, document.getFileName(), document.getContentType());
            vectorIndex.ingest(sections, docId, document.getTeamId(), document.getFileName());

            document.markCompleted();
            auditLogger.analysisCompleted(document, sections.size());
        } catch (Exception e) {
            document.markFailed();
            auditLogger.analysisFailed(document, e);
        } finally {
            repository.save(document);
        }
    }
}
