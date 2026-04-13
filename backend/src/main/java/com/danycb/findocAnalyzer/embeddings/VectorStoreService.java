package com.danycb.findocAnalyzer.embeddings;

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
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

@Slf4j
@Service
@RequiredArgsConstructor
public class VectorStoreService {
    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;

    public void ingestDocument(List<String> allPages, UUID docId, String fileName, UUID tenantId) {
        Metadata baseMetadata = new Metadata();
        baseMetadata.put("tenant_id", tenantId.toString());
        baseMetadata.put("file_name", fileName);
        baseMetadata.put("document_id", docId.toString());
        List<TextSegment> segments = new ArrayList<>(allPages.size() * 4);
        DocumentSplitter splitter = DocumentSplitters.recursive(600, 80);

        int page_num = 1;
        for (String page : allPages) {
            baseMetadata.put("page", page_num++);
            Map<String, Object> baseMDHashMap = baseMetadata.toMap();

            Document document = Document.from(page);
            List<TextSegment> rawSegments = splitter.split(document);

            int chunk_idx = 0;
            for (TextSegment segment : rawSegments) {
                Metadata metadata = new Metadata(baseMDHashMap);
                metadata.put("chunk_index", chunk_idx++);
                segments.add(new TextSegment(segment.text(), metadata));
            }
        }

        if (segments.isEmpty()) return;
        embeddingStore.addAll(embeddingModel.embedAll(segments).content(), segments);
    }

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
