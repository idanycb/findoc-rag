package com.danycb.findocAnalyzer.features.vault.application.out;

import java.util.UUID;

public interface ExternalStoragePort {
    String generateUploadUrl(UUID docId, String contentType, long contentLength);

    String generateViewUrl(UUID docId);

    byte[] download(String objectKey);

    void delete(UUID docId);

    String buildObjectKey(UUID docId);
}
