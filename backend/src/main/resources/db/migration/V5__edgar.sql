ALTER TABLE document_metadata
    ADD COLUMN cik VARCHAR(32),
    ADD COLUMN ticker VARCHAR(32),
    ADD COLUMN company_name VARCHAR(255),
    ADD COLUMN form_type VARCHAR(32),
    ADD COLUMN fiscal_period VARCHAR(64),
    ADD COLUMN report_date DATE,
    ADD COLUMN filing_date DATE,
    ADD COLUMN accession_number VARCHAR(64),
    ADD COLUMN source VARCHAR(32) NOT NULL DEFAULT 'UPLOAD',
    ADD COLUMN source_url TEXT;

ALTER TABLE document_embeddings
    ADD COLUMN section_title TEXT;

CREATE INDEX idx_docmeta_source ON document_metadata (source);
CREATE INDEX idx_docmeta_ticker ON document_metadata (ticker);
CREATE INDEX idx_docmeta_accession_number ON document_metadata (accession_number);
