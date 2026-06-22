package com.danycb.findocAnalyzer.features.vault.adapter.out.vector;

import com.danycb.findocAnalyzer.features.vault.application.out.VectorIndexPort;
import com.danycb.findocAnalyzer.features.vault.domain.ParsedSection;
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
    private static final int MAX_CHILD_CHUNK_SIZE = 600;
    private static final int CHILD_OVERLAP = 80;
    private static final int EMBEDDING_SAFE_CHAR_LIMIT = 900;

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;

    @Override
    public void ingest(List<ParsedSection> sections, UUID docId, UUID teamId, String fileName) {
        Metadata baseMetadata = new Metadata();
        baseMetadata.put("file_name", fileName);
        baseMetadata.put("document_id", docId.toString());
        baseMetadata.put("team_id", teamId.toString());

        List<TextSegment> segments = new ArrayList<>(sections.size() * 4);
        DocumentSplitter splitter = DocumentSplitters.recursive(MAX_CHILD_CHUNK_SIZE, CHILD_OVERLAP);

        for (ParsedSection section : sections) {
            if (section.text() == null || section.text().isBlank()) {
                continue;
            }

            String sectionText = section.text();
            baseMetadata.put("page", section.pageNumber());
            baseMetadata.put("section_text", sectionText);
            Map<String, Object> baseMetadataMap = baseMetadata.toMap();

            if (sectionText.length() <= EMBEDDING_SAFE_CHAR_LIMIT) {
                Metadata metadata = new Metadata(baseMetadataMap);
                metadata.put("chunk_index", 0);
                segments.add(new TextSegment(sectionText, metadata));
            } else {
                Document document = Document.from(sectionText);
                List<TextSegment> children = splitter.split(document);
                int chunkIndex = 0;
                for (TextSegment child : children) {
                    Metadata metadata = new Metadata(baseMetadataMap);
                    metadata.put("chunk_index", chunkIndex++);
                    segments.add(new TextSegment(child.text(), metadata));
                }
            }
        }

        if (segments.isEmpty()) {
            return;
        }
        embeddingStore.addAll(embeddingModel.embedAll(segments).content(), segments);
    }

    @Override
    public void deleteByDocumentId(UUID docId) {
        Filter docFilter = metadataKey("document_id").isEqualTo(docId.toString());

        try {
            embeddingStore.removeAll(docFilter);
        } catch (Exception e) {
            log.error(
                    "event=document_vectors_delete_failed documentId={} exception={} reason={}",
                    docId, e.getClass().getSimpleName(), e.getMessage(), e);
        }
    }
}
