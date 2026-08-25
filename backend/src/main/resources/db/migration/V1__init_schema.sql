CREATE EXTENSION IF NOT EXISTS vector;

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
-- Users
-- ============================================================
CREATE TABLE users
(
    id       UUID                NOT NULL,
    username VARCHAR(255) UNIQUE NOT NULL,
    password VARCHAR(255)        NOT NULL,
    role     VARCHAR(50)         NOT NULL,
    team_id  UUID,

    CONSTRAINT pk_user PRIMARY KEY (id),
    CONSTRAINT fk_users_team FOREIGN KEY (team_id) REFERENCES teams (id)
);

CREATE INDEX idx_users_team_id ON users (team_id);

-- ============================================================
-- Document Metadata
-- ============================================================
CREATE TABLE document_metadata
(
    id                           UUID                        NOT NULL,
    file_name                    VARCHAR(255)                NOT NULL,
    file_size                    BIGINT,
    content_type                 VARCHAR(255),
    uploaded_at                  TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    status                       VARCHAR(255)                NOT NULL,
    last_analyzed_at             TIMESTAMP WITHOUT TIME ZONE,
    version                      INTEGER,
    team_id                      UUID,
    cik                          VARCHAR(32),
    ticker                       VARCHAR(32),
    company_name                 VARCHAR(255),
    form_type                    VARCHAR(32),
    fiscal_period                VARCHAR(64),
    report_date                  DATE,
    filing_date                  DATE,
    accession_number             VARCHAR(64),
    source                       VARCHAR(32)                 NOT NULL DEFAULT 'UPLOAD',
    source_url                   TEXT,
    base_form_type               VARCHAR(32),
    is_amendment                 BOOLEAN                     NOT NULL DEFAULT FALSE,
    amends_accession_number      VARCHAR(64),
    amends_document_id           UUID,
    amendment_link_status        VARCHAR(32),
    searchable                   BOOLEAN                     NOT NULL DEFAULT TRUE,
    analysis_publication_claimed BOOLEAN                     NOT NULL DEFAULT FALSE,

    CONSTRAINT pk_document_metadata PRIMARY KEY (id),
    CONSTRAINT fk_document_amended_original
        FOREIGN KEY (amends_document_id)
            REFERENCES document_metadata (id)
            ON DELETE SET NULL
);

CREATE INDEX idx_docmeta_status ON document_metadata (status);
CREATE INDEX idx_docmeta_uploaded_at ON document_metadata (uploaded_at);
CREATE INDEX idx_docmeta_team_id ON document_metadata (team_id);
CREATE INDEX idx_docmeta_source ON document_metadata (source);
CREATE INDEX idx_docmeta_ticker ON document_metadata (ticker);
CREATE INDEX idx_docmeta_accession_number ON document_metadata (accession_number);
CREATE INDEX idx_docmeta_team_amends_accession
    ON document_metadata (team_id, amends_accession_number);
CREATE UNIQUE INDEX uq_docmeta_team_edgar_accession
    ON document_metadata (team_id, accession_number)
    WHERE source = 'EDGAR' AND accession_number IS NOT NULL;

-- ============================================================
-- Document Embeddings
-- ============================================================
CREATE TABLE document_embeddings
(
    embedding_id              UUID,
    document_id               UUID         NOT NULL,
    file_name                 VARCHAR(255) NOT NULL,
    page                      INT          NOT NULL,
    chunk_index               INT          NOT NULL,
    text                      TEXT,
    embedding                 VECTOR(384)  NOT NULL,
    team_id                   UUID,
    section_text              TEXT,
    section_title             TEXT,
    section_item              TEXT,
    accession_number          VARCHAR(64),
    original_accession_number VARCHAR(64),
    form_type                 VARCHAR(32),
    filing_date               VARCHAR(10),
    effective                 VARCHAR(5)   NOT NULL DEFAULT 'true',

    CONSTRAINT pk_doc_embeddings PRIMARY KEY (embedding_id),
    CONSTRAINT fk_document
        FOREIGN KEY (document_id)
            REFERENCES document_metadata (id)
            ON DELETE CASCADE
);

CREATE INDEX idx_doc_embeddings_hnsw
    ON document_embeddings USING hnsw (embedding vector_cosine_ops);
CREATE INDEX idx_doc_embeddings_doc_id ON document_embeddings (document_id);
CREATE INDEX idx_doc_embeddings_chunk ON document_embeddings (chunk_index);
CREATE INDEX idx_doc_embeddings_team_id ON document_embeddings (team_id);
CREATE INDEX idx_doc_embeddings_effective_team
    ON document_embeddings (team_id, effective);
CREATE INDEX idx_doc_embeddings_filing_family_item
    ON document_embeddings (team_id, original_accession_number, section_item, filing_date);
