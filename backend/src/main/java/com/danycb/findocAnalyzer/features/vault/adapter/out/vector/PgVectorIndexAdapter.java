package com.danycb.findocAnalyzer.features.vault.adapter.out.vector;

import com.danycb.findocAnalyzer.features.vault.application.out.VectorIndexPort;
import com.danycb.findocAnalyzer.features.vault.domain.Document;
import com.danycb.findocAnalyzer.features.vault.domain.DocumentSource;
import com.danycb.findocAnalyzer.features.vault.domain.ParsedSection;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.pgvector.MetadataStorageConfig;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.datasource.DataSourceUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class PgVectorIndexAdapter implements VectorIndexPort {
    private static final int MAX_CHILD_CHUNK_SIZE = 600;
    private static final int CHILD_OVERLAP = 80;
    private static final int EMBEDDING_SAFE_CHAR_LIMIT = 900;

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final DocumentVectorPersistence vectorPersistence;

    @Autowired
    public PgVectorIndexAdapter(EmbeddingModel embeddingModel, EmbeddingStore<TextSegment> embeddingStore,
                                DocumentVectorPersistence vectorPersistence) {
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
        this.vectorPersistence = vectorPersistence;
    }

    @Override
    public void ingest(List<ParsedSection> sections, Document sourceDocument) {
        Map<String, Object> baseMetadata = new HashMap<>();
        baseMetadata.put("file_name", sourceDocument.getFileName());
        baseMetadata.put("document_id", sourceDocument.getId().toString());
        baseMetadata.put("team_id", sourceDocument.getTeamId().toString());
        putIfPresent(baseMetadata, "accession_number", sourceDocument.getAccessionNumber());
        putIfPresent(baseMetadata, "original_accession_number", originalAccession(sourceDocument));
        putIfPresent(baseMetadata, "form_type", sourceDocument.getFormType());
        if (sourceDocument.getFilingDate() != null) {
            baseMetadata.put("filing_date", sourceDocument.getFilingDate().toString());
        }

        List<TextSegment> segments = new ArrayList<>(sections.size() * 4);
        DocumentSplitter splitter = DocumentSplitters.recursive(MAX_CHILD_CHUNK_SIZE, CHILD_OVERLAP);

        for (ParsedSection section : sections) {
            if (section.text() == null || section.text().isBlank()) {
                continue;
            }
            if (sourceDocument.getSource() == DocumentSource.EDGAR
                    && (section.item() == null || section.item().isBlank())) {
                throw new IllegalArgumentException("EDGAR sections require a stable section item");
            }

            String sectionText = section.text();
            Metadata sectionMetadata = new Metadata(baseMetadata);
            if (section.pageNumber() != null) {
                sectionMetadata.put("page", section.pageNumber());
            }
            sectionMetadata.put("section_text", sectionText);
            if (section.item() != null && !section.item().isBlank()) {
                sectionMetadata.put("section_item", section.item());
            }
            if (section.title() != null && !section.title().isBlank()) {
                sectionMetadata.put("section_title", section.title());
            }
            Map<String, Object> baseMetadataMap = new HashMap<>(sectionMetadata.toMap());
            baseMetadataMap.put("effective", "true");

            if (sectionText.length() <= EMBEDDING_SAFE_CHAR_LIMIT) {
                Metadata metadata = new Metadata(baseMetadataMap);
                metadata.put("chunk_index", 0);
                metadata.put("chunk_start", 0);
                segments.add(new TextSegment(sectionText, metadata));
            } else {
                dev.langchain4j.data.document.Document document =
                        dev.langchain4j.data.document.Document.from(sectionText);
                List<TextSegment> children = splitter.split(document);
                int chunkIndex = 0;
                int previousStart = -1;
                for (TextSegment child : children) {
                    Metadata metadata = new Metadata(baseMetadataMap);
                    metadata.put("chunk_index", chunkIndex++);
                    int chunkStart = childStart(sectionText, child.text(), previousStart);
                    metadata.put("chunk_start", chunkStart);
                    previousStart = chunkStart;
                    segments.add(new TextSegment(child.text(), metadata));
                }
            }
        }

        if (segments.isEmpty()) {
            return;
        }
        List<dev.langchain4j.data.embedding.Embedding> embeddings =
                embeddingModel.embedAll(segments).content();
        vectorPersistence.replaceDocument(embeddings, segments, sourceDocument);
    }

    private int childStart(String sectionText, String childText, int previousStart) {
        int start = sectionText.indexOf(childText, Math.max(0, previousStart + 1));
        if (start >= 0) {
            return start;
        }
        start = sectionText.indexOf(childText);
        if (start >= 0) {
            return start;
        }
        start = whitespaceFlexibleStart(sectionText, childText, Math.max(0, previousStart + 1));
        if (start >= 0) {
            return start;
        }
        start = whitespaceFlexibleStart(sectionText, childText, 0);
        if (start >= 0) {
            return start;
        }
        throw new IllegalStateException("Could not locate generated chunk in its parent section: "
                + childText.substring(0, Math.min(160, childText.length())).replace('\n', ' '));
    }

    private int whitespaceFlexibleStart(String sectionText, String childText, int fromIndex) {
        String[] tokens = childText.strip().split("(?U)\\s+");
        if (tokens.length == 0) {
            return -1;
        }
        String expression = java.util.Arrays.stream(tokens)
                .map(Pattern::quote)
                .collect(java.util.stream.Collectors.joining("\\s+"));
        Matcher matcher = Pattern.compile(expression, Pattern.UNICODE_CHARACTER_CLASS).matcher(sectionText);
        return matcher.find(fromIndex) ? matcher.start() : -1;
    }

    private String originalAccession(Document document) {
        if (document.getAmendsAccessionNumber() != null && !document.getAmendsAccessionNumber().isBlank()) {
            return document.getAmendsAccessionNumber();
        }
        return document.getAccessionNumber();
    }

    private void putIfPresent(Map<String, Object> metadata, String key, String value) {
        if (value != null && !value.isBlank()) {
            metadata.put(key, value);
        }
    }

    @Override
    public void deleteByDocumentId(UUID docId) {
        vectorPersistence.deleteDocument(docId);
    }

    public static final class AmendmentAwarePgVectorStore extends PgVectorEmbeddingStore
            implements DocumentVectorPersistence {
        public AmendmentAwarePgVectorStore(DataSource dataSource, MetadataStorageConfig metadataConfig) {
            super(dataSource, "document_embeddings", 384, false, null, false, false, metadataConfig);
        }

        @Override
        public void replaceDocument(List<dev.langchain4j.data.embedding.Embedding> embeddings,
                                    List<TextSegment> segments, Document document) {
            inTransaction("Could not replace document vectors", connection -> {
                List<FamilyItem> affected = new ArrayList<>(familyItemsForDocument(connection, document.getId()));
                affected.addAll(familyItems(segments));
                affected = affected.stream().distinct().sorted().toList();
                lock(connection, affected);
                deleteRows(connection, document.getId());
                insertRows(connection, embeddings, segments);
                for (FamilyItem item : affected) {
                    recomputeEffective(connection, item);
                }
            });
        }

        @Override
        public void deleteDocument(UUID documentId) {
            inTransaction("Could not delete document vectors", connection -> {
                List<FamilyItem> affected = familyItemsForDocument(connection, documentId);
                lock(connection, affected);
                deleteRows(connection, documentId);
                for (FamilyItem item : affected) {
                    recomputeEffective(connection, item);
                }
            });
        }

        private void inTransaction(String failureMessage, SqlWork work) {
            Connection connection = DataSourceUtils.getConnection(datasource);
            boolean surroundingTransaction = DataSourceUtils.isConnectionTransactional(connection, datasource);
            try {
                if (!surroundingTransaction) {
                    connection.setAutoCommit(false);
                }
                work.execute(connection);
                if (!surroundingTransaction) {
                    connection.commit();
                }
            } catch (SQLException failure) {
                if (!surroundingTransaction) {
                    rollback(connection, failure);
                }
                throw new IllegalStateException(failureMessage, failure);
            } finally {
                DataSourceUtils.releaseConnection(connection, datasource);
            }
        }

        private List<FamilyItem> familyItems(List<TextSegment> segments) {
            return segments.stream()
                    .map(TextSegment::metadata)
                    .filter(metadata -> metadata.getString("team_id") != null
                            && metadata.getString("original_accession_number") != null
                            && metadata.getString("section_item") != null)
                    .map(metadata -> new FamilyItem(
                            UUID.fromString(metadata.getString("team_id")),
                            metadata.getString("original_accession_number"),
                            metadata.getString("section_item")))
                    .distinct()
                    .sorted()
                    .toList();
        }

        private List<FamilyItem> familyItemsForDocument(Connection connection, UUID documentId) throws SQLException {
            String sql = """
                    SELECT DISTINCT team_id, original_accession_number, section_item
                    FROM document_embeddings
                    WHERE document_id = ? AND team_id IS NOT NULL
                      AND original_accession_number IS NOT NULL AND section_item IS NOT NULL
                    """;
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setObject(1, documentId);
                try (var result = statement.executeQuery()) {
                    List<FamilyItem> items = new ArrayList<>();
                    while (result.next()) {
                        items.add(new FamilyItem(result.getObject(1, UUID.class), result.getString(2), result.getString(3)));
                    }
                    return items.stream().sorted().toList();
                }
            }
        }

        private void lock(Connection connection, List<FamilyItem> items) throws SQLException {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT pg_advisory_xact_lock(hashtextextended(?, 0))")) {
                for (FamilyItem item : items) {
                    statement.setString(1, item.lockKey());
                    statement.execute();
                }
            }
        }

        private void deleteRows(Connection connection, UUID documentId) throws SQLException {
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM document_embeddings WHERE document_id = ?")) {
                statement.setObject(1, documentId);
                statement.executeUpdate();
            }
        }

        private void insertRows(Connection connection,
                                List<dev.langchain4j.data.embedding.Embedding> embeddings,
                                List<TextSegment> segments) throws SQLException {
            String sql = """
                    INSERT INTO document_embeddings
                        (embedding_id, document_id, team_id, file_name, page, chunk_index, chunk_start, text, embedding,
                         section_text, section_title, section_item, accession_number,
                         original_accession_number, form_type, filing_date, effective)
                    VALUES (?, ?::uuid, ?::uuid, ?, ?, ?, ?, ?, ?::vector, ?, ?, ?, ?, ?, ?, ?, ?)
                    """;
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                for (int index = 0; index < segments.size(); index++) {
                    TextSegment segment = segments.get(index);
                    Metadata metadata = segment.metadata();
                    statement.setObject(1, UUID.randomUUID());
                    statement.setString(2, metadata.getString("document_id"));
                    statement.setString(3, metadata.getString("team_id"));
                    statement.setString(4, metadata.getString("file_name"));
                    statement.setObject(5, metadata.getInteger("page"), Types.INTEGER);
                    statement.setInt(6, metadata.getInteger("chunk_index"));
                    statement.setInt(7, metadata.getInteger("chunk_start"));
                    statement.setString(8, segment.text());
                    statement.setString(9, vectorLiteral(embeddings.get(index).vector()));
                    statement.setString(10, metadata.getString("section_text"));
                    statement.setString(11, metadata.getString("section_title"));
                    statement.setString(12, metadata.getString("section_item"));
                    statement.setString(13, metadata.getString("accession_number"));
                    statement.setString(14, metadata.getString("original_accession_number"));
                    statement.setString(15, metadata.getString("form_type"));
                    statement.setString(16, metadata.getString("filing_date"));
                    statement.setString(17,
                            metadata.getString("original_accession_number") == null
                                    || metadata.getString("section_item") == null
                                    ? "true" : "false");
                    statement.addBatch();
                }
                statement.executeBatch();
            }
        }

        private void recomputeEffective(Connection connection, FamilyItem item) throws SQLException {
            try (PreparedStatement clear = connection.prepareStatement("""
                    UPDATE document_embeddings SET effective = 'false'
                    WHERE team_id = ? AND original_accession_number = ? AND section_item = ?
                    """)) {
                clear.setObject(1, item.teamId());
                clear.setString(2, item.originalAccession());
                clear.setString(3, item.sectionItem());
                clear.executeUpdate();
            }
            try (PreparedStatement select = connection.prepareStatement("""
                    WITH winner AS (
                        SELECT document_id FROM document_embeddings
                        WHERE team_id = ? AND original_accession_number = ? AND section_item = ?
                        ORDER BY filing_date DESC NULLS LAST, accession_number DESC NULLS LAST, document_id DESC
                        LIMIT 1
                    )
                    UPDATE document_embeddings SET effective = 'true'
                    WHERE document_id = (SELECT document_id FROM winner) AND section_item = ?
                    """)) {
                select.setObject(1, item.teamId());
                select.setString(2, item.originalAccession());
                select.setString(3, item.sectionItem());
                select.setString(4, item.sectionItem());
                select.executeUpdate();
            }
        }

        private String vectorLiteral(float[] vector) {
            StringBuilder value = new StringBuilder("[");
            for (int index = 0; index < vector.length; index++) {
                if (index > 0) value.append(',');
                value.append(vector[index]);
            }
            return value.append(']').toString();
        }

        private void rollback(Connection connection, SQLException failure) {
            try {
                connection.rollback();
            } catch (SQLException rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
        }

        @FunctionalInterface
        private interface SqlWork {
            void execute(Connection connection) throws SQLException;
        }

        private record FamilyItem(UUID teamId, String originalAccession, String sectionItem)
                implements Comparable<FamilyItem> {
            String lockKey() {
                return teamId + "|" + originalAccession + "|" + sectionItem;
            }

            @Override
            public int compareTo(FamilyItem other) {
                return lockKey().compareTo(other.lockKey());
            }
        }
    }
}
