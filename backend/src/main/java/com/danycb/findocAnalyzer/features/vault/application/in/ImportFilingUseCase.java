package com.danycb.findocAnalyzer.features.vault.application.in;

import com.danycb.findocAnalyzer.features.vault.application.dto.ImportFilingCommand;
import com.danycb.findocAnalyzer.features.vault.application.dto.ImportFilingResult;

import java.util.UUID;

public interface ImportFilingUseCase {
    ImportFilingResult importFiling(ImportFilingCommand command, UUID teamId);
}
