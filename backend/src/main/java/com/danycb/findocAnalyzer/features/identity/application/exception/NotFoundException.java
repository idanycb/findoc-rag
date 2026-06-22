package com.danycb.findocAnalyzer.features.identity.application.exception;

/**
 * Thrown when a referenced team or user does not exist.
 */
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}
