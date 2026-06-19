CREATE EXTENSION IF NOT EXISTS vector;

-- ============================================================
-- Users
-- ============================================================
CREATE TABLE users
(
    id       UUID                NOT NULL,
    username VARCHAR(255) UNIQUE NOT NULL,
    password VARCHAR(255)        NOT NULL,
    role     VARCHAR(50)         NOT NULL,

    CONSTRAINT pk_user PRIMARY KEY (id)
);

-- ============================================================
-- Document Metadata
-- ============================================================
CREATE TABLE document_metadata
(
    id               UUID                        NOT NULL,
    file_name        VARCHAR(255)                NOT NULL,
    file_size        BIGINT,
    content_type     VARCHAR(255),
    uploaded_at      TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    status           VARCHAR(255)                NOT NULL,
    ai_summary       TEXT,
    last_analyzed_at TIMESTAMP WITHOUT TIME ZONE,
    version          INTEGER,

    CONSTRAINT pk_document_metadata PRIMARY KEY (id)
);
CREATE INDEX idx_docmeta_status ON document_metadata (status);
CREATE INDEX idx_docmeta_uploaded_at ON document_metadata (uploaded_at);

-- ============================================================
-- Document Embeddings
-- ============================================================
CREATE TABLE document_embeddings
(
    embedding_id UUID,
    document_id  UUID         NOT NULL,
    file_name    VARCHAR(255) NOT NULL,
    page         INT          NOT NULL,
    chunk_index  INT          NOT NULL,
    text         TEXT,
    embedding    VECTOR(384)  NOT NULL,

    CONSTRAINT pk_doc_embeddings PRIMARY KEY (embedding_id),
    CONSTRAINT fk_document
        FOREIGN KEY (document_id)
            REFERENCES document_metadata (id)
            ON DELETE CASCADE
);

CREATE INDEX idx_doc_embeddings_hnsw ON document_embeddings USING hnsw (embedding vector_cosine_ops);
CREATE INDEX idx_doc_embeddings_doc_id ON document_embeddings (document_id);
CREATE INDEX idx_doc_embeddings_chunk ON document_embeddings (chunk_index);