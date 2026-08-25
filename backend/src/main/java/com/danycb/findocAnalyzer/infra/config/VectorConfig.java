package com.danycb.findocAnalyzer.infra.config;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.pgvector.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.util.Arrays;
import com.danycb.findocAnalyzer.features.vault.adapter.out.vector.PgVectorIndexAdapter;

@Configuration
public class VectorConfig {

    @Bean
    public EmbeddingModel embeddingModel() {
        return new AllMiniLmL6V2QuantizedEmbeddingModel();
    }

    @Bean
    public PgVectorIndexAdapter.AmendmentAwarePgVectorStore embeddingStore(DataSource dataSource) {
        MetadataStorageConfig metadataConfig = DefaultMetadataStorageConfig.builder()
                .storageMode(MetadataStorageMode.COLUMN_PER_KEY)
                .columnDefinitions(Arrays.asList(
                        MetadataColumDefinition.from("chunk_index INT").getFullDefinition(),
                        MetadataColumDefinition.from("document_id UUID").getFullDefinition(),
                        MetadataColumDefinition.from("team_id UUID").getFullDefinition(),
                        MetadataColumDefinition.from("file_name VARCHAR(255)").getFullDefinition(),
                        MetadataColumDefinition.from("page INT").getFullDefinition(),
                        MetadataColumDefinition.from("section_text TEXT").getFullDefinition(),
                        MetadataColumDefinition.from("section_title TEXT").getFullDefinition(),
                        MetadataColumDefinition.from("section_item TEXT").getFullDefinition(),
                        MetadataColumDefinition.from("accession_number VARCHAR(64)").getFullDefinition(),
                        MetadataColumDefinition.from("original_accession_number VARCHAR(64)").getFullDefinition(),
                        MetadataColumDefinition.from("form_type VARCHAR(32)").getFullDefinition(),
                        MetadataColumDefinition.from("filing_date VARCHAR(10)").getFullDefinition(),
                        MetadataColumDefinition.from("effective VARCHAR(5)").getFullDefinition()
                ))
                .build();

        return new PgVectorIndexAdapter.AmendmentAwarePgVectorStore(dataSource, metadataConfig);
    }
}
