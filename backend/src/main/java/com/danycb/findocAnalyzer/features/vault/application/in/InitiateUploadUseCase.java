package com.danycb.findocAnalyzer.features.vault.application.in;

import com.danycb.findocAnalyzer.features.vault.application.dto.DocumentUploadCommand;
import com.danycb.findocAnalyzer.features.vault.application.dto.UploadResult;

import java.util.UUID;

public interface InitiateUploadUseCase {
    UploadResult execute(DocumentUploadCommand command, UUID teamId);
}
