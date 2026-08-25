package com.danycb.findocAnalyzer.features.vault.application;

import com.danycb.findocAnalyzer.features.vault.domain.Document;
import com.danycb.findocAnalyzer.features.vault.domain.DocumentSource;

import java.util.Objects;
import java.util.UUID;

final class OriginalFilingEligibility {
    private OriginalFilingEligibility() {
    }

    static boolean isEligible(Document amendment, Document candidate) {
        return isEligible(amendment.getTeamId(), amendment.getBaseFormType(), candidate);
    }

    static boolean isEligible(UUID teamId, String baseFormType, Document candidate) {
        return candidate != null
                && Objects.equals(teamId, candidate.getTeamId())
                && candidate.getSource() == DocumentSource.EDGAR
                && !candidate.isAmendment()
                && baseFormType != null
                && baseFormType.equals(candidate.getBaseFormType());
    }
}
