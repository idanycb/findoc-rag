CREATE TABLE analysis_outbox
(
    id               UUID                     NOT NULL,
    document_id      UUID                     NOT NULL,
    object_key       TEXT,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    published_at     TIMESTAMP WITH TIME ZONE,
    attempt_count    INTEGER                  NOT NULL DEFAULT 0,
    next_attempt_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    claim_expires_at TIMESTAMP WITH TIME ZONE,
    claim_token      UUID,
    last_error       VARCHAR(1000),
    processing_started_at       TIMESTAMP WITH TIME ZONE,
    processing_completed_at     TIMESTAMP WITH TIME ZONE,
    processing_claim_expires_at TIMESTAMP WITH TIME ZONE,
    processing_claim_token      UUID,
    processing_last_error       VARCHAR(1000),

    CONSTRAINT pk_analysis_outbox PRIMARY KEY (id),
    CONSTRAINT fk_analysis_outbox_document
        FOREIGN KEY (document_id)
            REFERENCES document_metadata (id)
            ON DELETE CASCADE
);

CREATE INDEX idx_analysis_outbox_due
    ON analysis_outbox (next_attempt_at, created_at)
    WHERE published_at IS NULL;

CREATE UNIQUE INDEX uq_analysis_outbox_active_document
    ON analysis_outbox (document_id)
    WHERE processing_completed_at IS NULL;

CREATE INDEX idx_analysis_outbox_processing_pending
    ON analysis_outbox (created_at)
    WHERE processing_completed_at IS NULL;

CREATE INDEX idx_analysis_outbox_cleanup
    ON analysis_outbox (published_at)
    WHERE published_at IS NOT NULL AND processing_completed_at IS NOT NULL;

ALTER TABLE document_metadata
    DROP COLUMN analysis_publication_claimed;
