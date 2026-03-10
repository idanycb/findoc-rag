package com.danycb.findocAnalyzer.exception;

/**
 * Thrown when the external AI provider fails or returns invalid data.
 * Maps to HTTP 502 Bad Gateway.
 */
public class AiAnalysisException extends RuntimeException {
    public AiAnalysisException(String message, Throwable cause) {
        super(message, cause);
    }
}
