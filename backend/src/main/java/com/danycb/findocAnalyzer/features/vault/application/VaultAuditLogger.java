package com.danycb.findocAnalyzer.features.vault.application;

import com.danycb.findocAnalyzer.features.vault.domain.Document;
import com.danycb.findocAnalyzer.features.vault.domain.DocumentStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
public class VaultAuditLogger {

    public void uploadInitiated(Document document) {
        log.info(
                "event=document_upload_initiated documentId={} teamId={} status={} contentType={} fileSizeBytes={}",
                document.getId(), document.getTeamId(), document.getStatus(),
                document.getContentType(), document.getFileSize());
    }

    public void analysisRequested(Document document) {
        log.info(
                "event=document_analysis_requested documentId={} teamId={} status={}",
                document.getId(), document.getTeamId(), document.getStatus());
    }

    public void analysisStarted(Document document) {
        log.info(
                "event=document_analysis_started documentId={} teamId={} status={}",
                document.getId(), document.getTeamId(), document.getStatus());
    }

    public void analysisCompleted(Document document, int sectionCount) {
        log.info(
                "event=document_analysis_completed documentId={} teamId={} status={} parsedSections={}",
                document.getId(), document.getTeamId(), document.getStatus(), sectionCount);
    }

    public void analysisSkipped(Document document, String reason) {
        log.info(
                "event=document_analysis_skipped documentId={} teamId={} status={} reason={}",
                document.getId(), document.getTeamId(), document.getStatus(), reason);
    }

    public void analysisSkipped(UUID documentId, String reason) {
        log.warn("event=document_analysis_skipped documentId={} reason={}", documentId, reason);
    }

    public void analysisFailed(Document document, Exception exception) {
        log.error(
                "event=document_analysis_failed documentId={} teamId={} status={} exception={} reason={}",
                document.getId(), document.getTeamId(), DocumentStatus.FAILED,
                exception.getClass().getSimpleName(), exception.getMessage(), exception);
    }

    public void documentDeleted(Document document) {
        log.info(
                "event=document_deleted documentId={} teamId={} previousStatus={}",
                document.getId(), document.getTeamId(), document.getStatus());
    }
}
