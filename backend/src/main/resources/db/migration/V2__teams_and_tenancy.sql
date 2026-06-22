-- ============================================================
-- Teams
-- ============================================================
CREATE TABLE teams
(
    id         UUID                        NOT NULL,
    name       VARCHAR(255) UNIQUE         NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,

    CONSTRAINT pk_team PRIMARY KEY (id)
);

-- ============================================================
-- Users: attach to a team (nullable; SUPER_ADMIN is teamless)
-- ============================================================
ALTER TABLE users
    ADD COLUMN team_id UUID;

ALTER TABLE users
    ADD CONSTRAINT fk_users_team
        FOREIGN KEY (team_id) REFERENCES teams (id);

CREATE INDEX idx_users_team_id ON users (team_id);

-- ============================================================
-- Documents: scope every document to a team
-- ============================================================
ALTER TABLE document_metadata
    ADD COLUMN team_id UUID;

CREATE INDEX idx_docmeta_team_id ON document_metadata (team_id);

-- ============================================================
-- Embeddings: team_id metadata column for tenant-scoped retrieval
-- (kept in sync with LangChain4j COLUMN_PER_KEY config in VectorConfig)
-- ============================================================
ALTER TABLE document_embeddings
    ADD COLUMN team_id UUID;

CREATE INDEX idx_doc_embeddings_team_id ON document_embeddings (team_id);
