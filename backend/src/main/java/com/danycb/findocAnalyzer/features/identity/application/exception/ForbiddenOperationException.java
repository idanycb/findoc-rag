package com.danycb.findocAnalyzer.features.identity.application.exception;

/**
 * Thrown when the acting user is not permitted to perform the requested operation.
 */
public class ForbiddenOperationException extends RuntimeException {
    public ForbiddenOperationException(String message) {
        super(message);
    }
}
