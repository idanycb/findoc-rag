package com.danycb.findocAnalyzer.exception;

/**
 * Thrown when a document is in an invalid state for the requested operation.
 * Maps to HTTP 409 Conflict.
 */
public class DocumentProcessingException extends RuntimeException {
    public DocumentProcessingException(String message) {
        super(message);
    }
}
