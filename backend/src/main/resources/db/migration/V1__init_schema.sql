CREATE EXTENSION IF NOT EXISTS vector;

-- ============================================================
-- Tenants
-- ============================================================
CREATE TABLE tenants
(
    tenant_id UUID         NOT NULL,
    name      VARCHAR(255) NOT NULL,
    CONSTRAINT pk_tenants PRIMARY KEY (tenant_id)
);

-- ============================================================
-- Users
-- ============================================================
CREATE TABLE users
(
    id        UUID                NOT NULL,
    username  VARCHAR(255) UNIQUE NOT NULL,
    password  VARCHAR(255)        NOT NULL,
    tenant_id UUID                NOT NULL,

    CONSTRAINT pk_user PRIMARY KEY (id),
    CONSTRAINT fk_user_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants (tenant_id)
            ON DELETE CASCADE
);
CREATE INDEX idx_users_tenant_id ON users (tenant_id);

-- ============================================================
-- Document Metadata
-- ============================================================
CREATE TABLE document_metadata
(
    id               UUID                        NOT NULL,
    tenant_id        UUID                        NOT NULL,
    file_name        VARCHAR(255)                NOT NULL,
    file_size        BIGINT,
    content_type     VARCHAR(255),
    uploaded_at      TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    status           VARCHAR(255)                NOT NULL,
    ai_summary       TEXT,
    last_analyzed_at TIMESTAMP WITHOUT TIME ZONE,
    version          INTEGER,

    CONSTRAINT pk_document_metadata PRIMARY KEY (id),
    CONSTRAINT fk_docmeta_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants (tenant_id)
            ON DELETE CASCADE
);
CREATE INDEX idx_docmeta_tenant_id ON document_metadata (tenant_id);
CREATE INDEX idx_docmeta_status ON document_metadata (status);
CREATE INDEX idx_docmeta_uploaded_at ON document_metadata (uploaded_at);

-- ============================================================
-- Document Embeddings (Partitioned)
-- ============================================================
CREATE TABLE document_embeddings
(
    embedding_id UUID,
    tenant_id    UUID         NOT NULL,
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

CREATE OR REPLACE FUNCTION create_document_embeddings_partition(p_tenant_id UUID)
    RETURNS void
    LANGUAGE plpgsql
AS
$$
DECLARE
    short_id             text := replace(p_tenant_id::text, '-', '');
    partition_table_name text := 'docemb_t_' || short_id;
BEGIN
    EXECUTE format(
            'CREATE TABLE IF NOT EXISTS %I (CHECK (tenant_id = %L)) INHERITS (document_embeddings);',
            partition_table_name,
            p_tenant_id
            );

    EXECUTE format(
            'CREATE INDEX IF NOT EXISTS %I ON %I USING hnsw (embedding vector_cosine_ops);',
            partition_table_name || '_hnsw',
            partition_table_name
            );

    EXECUTE format(
            'CREATE INDEX IF NOT EXISTS %I ON %I (document_id);',
            partition_table_name || '_docid',
            partition_table_name
            );

    EXECUTE format(
            'CREATE INDEX IF NOT EXISTS %I ON %I (chunk_index);',
            partition_table_name || '_chunk',
            partition_table_name
            );
END;
$$;

CREATE OR REPLACE FUNCTION route_document_embedding()
    RETURNS TRIGGER
    LANGUAGE plpgsql
AS
$$
DECLARE
    short_id             text := replace(NEW.tenant_id::text, '-', '');
    partition_table_name text := 'docemb_t_' || short_id;
BEGIN
    EXECUTE format(
            'INSERT INTO %I VALUES ($1.*)',
            partition_table_name
            ) USING NEW;

    RETURN NULL;
END;
$$;

CREATE TRIGGER document_embeddings_insert
    BEFORE INSERT
    ON document_embeddings
    FOR EACH ROW
EXECUTE FUNCTION route_document_embedding();