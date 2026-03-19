package com.danycb.findocAnalyzer.service;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class VectorStoreService {
    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment>  embeddingStore;

    public void ingestDocument(String content, String fileName, String userId) {
        Metadata metadata = new Metadata();
        metadata.put("userId", userId);
        metadata.put("fileName", fileName);

        Document document = Document.from(content);
        DocumentSplitter splitter = DocumentSplitters.recursive(300, 50);
        List<TextSegment> segments = splitter.split(document).stream()
                .map(segment -> TextSegment.from(segment.text(), metadata)).toList();

        embeddingStore.addAll(embeddingModel.embedAll(segments).content(), segments);
    }
}
