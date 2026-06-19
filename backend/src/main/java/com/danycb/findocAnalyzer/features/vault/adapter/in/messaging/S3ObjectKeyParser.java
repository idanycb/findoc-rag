package com.danycb.findocAnalyzer.features.vault.adapter.in.messaging;

import java.util.UUID;

final class S3ObjectKeyParser {
    record KeyParts(UUID docId) {
    }

    static KeyParts parse(String key) {
        try {
            String[] parts = key.split("/");
            if (parts.length != 2) {
                throw new IllegalArgumentException();
            }
            UUID docId = UUID.fromString(parts[1]);
            return new KeyParts(docId);
        } catch (Exception e) {
            throw new InvalidS3KeyException(key);
        }
    }
}
