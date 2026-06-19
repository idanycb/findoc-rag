package com.danycb.findocAnalyzer.features.vault.adapter.in.messaging;

class InvalidS3KeyException extends RuntimeException {
    InvalidS3KeyException(String key) {
        super("Invalid S3 Key format: '%s'. Expected files/{docId}".formatted(key));
    }
}