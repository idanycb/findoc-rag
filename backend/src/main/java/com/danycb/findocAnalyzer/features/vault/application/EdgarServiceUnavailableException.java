package com.danycb.findocAnalyzer.features.vault.application;

public class EdgarServiceUnavailableException extends RuntimeException {
    public EdgarServiceUnavailableException(String message) {
        super(message);
    }

    public EdgarServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
