ALTER TABLE document_embeddings
    ALTER COLUMN page DROP NOT NULL,
    ADD COLUMN chunk_start INT;
