package com.danycb.findocAnalyzer.features.vault.application;

import com.danycb.findocAnalyzer.features.vault.application.in.AnalyzeDocumentUseCase;
import com.danycb.findocAnalyzer.features.vault.application.out.DocumentParserPort;
import com.danycb.findocAnalyzer.features.vault.application.out.DocumentRepositoryPort;
import com.danycb.findocAnalyzer.features.vault.application.out.ExternalStoragePort;
import com.danycb.findocAnalyzer.features.vault.application.out.VectorIndexPort;
import com.danycb.findocAnalyzer.features.vault.domain.Document;
import com.danycb.findocAnalyzer.features.vault.domain.ParsedPage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyzeDocumentService implements AnalyzeDocumentUseCase {
    private final DocumentRepositoryPort repository;
    private final ExternalStoragePort objectStorage;
    private final DocumentParserPort documentParser;
    private final VectorIndexPort vectorIndex;

    @Override
    public void analyze(UUID docId, String objectKey) {
        var optionalDocument = repository.findById(docId);
        if (optionalDocument.isEmpty()) {
            log.warn("Skipping analysis for document: {}, REASON: Resource not found", docId);
            return;
        }
        Document document = optionalDocument.get();

        if (document.cannotAnalyze()) {
            log.info("Skipping analysis for document: {}, REASON: Document already processed or under analysis", docId);
            return;
        }

        document.markProcessing();
        document = repository.save(document);

        try {
            byte[] content = objectStorage.download(objectKey);
            List<ParsedPage> pages = documentParser.parse(content, document.getFileName(), document.getContentType());
            vectorIndex.ingest(pages, docId, document.getFileName());

            document.markCompleted();
            log.info("Document {} has been analyzed", docId);
        } catch (Exception e) {
            document.markFailed();
            log.error("Failed to analyze Document: {}, REASON: {}", docId, e.getMessage());
        } finally {
            repository.save(document);
        }
    }
}
