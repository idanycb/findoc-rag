package com.danycb.findocAnalyzer.features.vault.adapter.out.vector;

import com.danycb.findocAnalyzer.features.vault.application.out.VectorIndexPort;
import com.danycb.findocAnalyzer.features.vault.domain.ParsedPage;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

@Slf4j
@Component
@RequiredArgsConstructor
public class PgVectorIndexAdapter implements VectorIndexPort {
    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;

    @Override
    public void ingest(List<ParsedPage> pages, UUID docId, String fileName) {
        Metadata baseMetadata = new Metadata();
        baseMetadata.put("file_name", fileName);
        baseMetadata.put("document_id", docId.toString());

        List<TextSegment> segments = new ArrayList<>(pages.size() * 4);
        DocumentSplitter splitter = DocumentSplitters.recursive(600, 80);

        for (ParsedPage page : pages) {
            if (page.text() == null || page.text().isBlank()) {
                continue;
            }
            baseMetadata.put("page", page.pageNumber());
            Map<String, Object> baseMetadataMap = baseMetadata.toMap();

            Document document = Document.from(page.text());
            List<TextSegment> rawSegments = splitter.split(document);

            int chunkIndex = 0;
            for (TextSegment segment : rawSegments) {
                Metadata metadata = new Metadata(baseMetadataMap);
                metadata.put("chunk_index", chunkIndex++);
                segments.add(new TextSegment(segment.text(), metadata));
            }
        }

        if (segments.isEmpty()) {
            return;
        }
        embeddingStore.addAll(embeddingModel.embedAll(segments).content(), segments);
    }

    @Override
    public void deleteByDocumentId(UUID docId) {
        log.warn("Purging vector embeddings for document ID: {}", docId);

        Filter docFilter = metadataKey("document_id").isEqualTo(docId.toString());

        try {
            embeddingStore.removeAll(docFilter);
            log.info("Successfully cleared vectors for document: {}", docId);
        } catch (Exception e) {
            log.error("Failed to purge vectors for {}: {}", docId, e.getMessage());
        }
    }
}
