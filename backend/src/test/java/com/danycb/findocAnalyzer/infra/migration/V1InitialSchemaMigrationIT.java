package com.danycb.findocAnalyzer.infra.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class V1InitialSchemaMigrationIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:0.8.2-pg18-trixie")
                    .asCompatibleSubstituteFor("postgres"));

    @Test
    void freshSchemaIncludesTheAnalysisOutboxAndNoLongerUsesDocumentPublicationClaims() throws Exception {
        DataSource dataSource = dataSource();
        Flyway.configure().dataSource(dataSource).load().migrate();

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet migrations = statement.executeQuery("""
                     SELECT version, success
                     FROM flyway_schema_history
                     WHERE type = 'SQL'
                     ORDER BY installed_rank
                     """)) {
            assertThat(migrations.next()).isTrue();
            assertThat(migrations.getString("version")).isEqualTo("1");
            assertThat(migrations.getBoolean("success")).isTrue();
            assertThat(migrations.next()).isTrue();
            assertThat(migrations.getString("version")).isEqualTo("2");
            assertThat(migrations.getBoolean("success")).isTrue();
            assertThat(migrations.next()).isFalse();
        }

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet columns = statement.executeQuery("""
                     SELECT table_name || '.' || column_name AS qualified_name
                     FROM information_schema.columns
                     WHERE table_schema = 'public'
                       AND ((table_name = 'document_metadata' AND column_name IN
                           ('base_form_type', 'is_amendment', 'amends_accession_number',
                            'amends_document_id', 'amendment_link_status', 'searchable'))
                         OR (table_name = 'document_embeddings' AND column_name IN
                           ('section_text', 'section_title', 'section_item', 'accession_number',
                            'original_accession_number', 'form_type', 'filing_date', 'effective')))
                     """)) {
            var names = new ArrayList<String>();
            while (columns.next()) {
                names.add(columns.getString("qualified_name"));
            }
            assertThat(names)
                    .containsExactlyInAnyOrder(
                            "document_metadata.base_form_type",
                            "document_metadata.is_amendment",
                            "document_metadata.amends_accession_number",
                            "document_metadata.amends_document_id",
                            "document_metadata.amendment_link_status",
                            "document_metadata.searchable",
                            "document_embeddings.section_text",
                            "document_embeddings.section_title",
                            "document_embeddings.section_item",
                            "document_embeddings.accession_number",
                            "document_embeddings.original_accession_number",
                            "document_embeddings.form_type",
                            "document_embeddings.filing_date",
                            "document_embeddings.effective");
        }

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet columns = statement.executeQuery("""
                     SELECT column_name
                     FROM information_schema.columns
                     WHERE table_schema = 'public' AND table_name = 'analysis_outbox'
                     """)) {
            var names = new ArrayList<String>();
            while (columns.next()) {
                names.add(columns.getString("column_name"));
            }
            assertThat(names).contains(
                    "id", "document_id", "object_key", "created_at", "published_at",
                    "attempt_count", "next_attempt_at", "claim_expires_at", "claim_token", "last_error",
                    "processing_started_at", "processing_completed_at", "processing_claim_expires_at",
                    "processing_claim_token", "processing_last_error");
        }

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet oldClaim = statement.executeQuery("""
                     SELECT 1
                     FROM information_schema.columns
                     WHERE table_schema = 'public'
                       AND table_name = 'document_metadata'
                       AND column_name = 'analysis_publication_claimed'
                     """)) {
            assertThat(oldClaim.next()).isFalse();
        }

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet index = statement.executeQuery("""
                     SELECT indexdef
                     FROM pg_indexes
                     WHERE schemaname = 'public' AND indexname = 'uq_docmeta_team_edgar_accession'
                     """)) {
            assertThat(index.next()).isTrue();
            assertThat(index.getString("indexdef"))
                    .contains("UNIQUE")
                    .contains("team_id", "accession_number")
                    .contains("EDGAR", "accession_number IS NOT NULL");
        }
    }

    private DataSource dataSource() {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setUrl(POSTGRES.getJdbcUrl());
        dataSource.setUser(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        return dataSource;
    }
}
