package com.danycb.findocAnalyzer.document;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Slf4j
@Service
public class DocParserService {

    public String extractTextFromPdf(byte[] pdfBytes) {
        log.info("Starting text extraction from PDF bytes (Size: {} bytes)", pdfBytes.length);

        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            PDFTextStripper stripper = new PDFTextStripper();

            stripper.setEndPage(Math.min(document.getNumberOfPages(), 10));

            return stripper.getText(document);
        } catch (IOException e) {
            log.error("Failed to parse PDF content: {}", e.getMessage());
            throw new RuntimeException("Could not read PDF content", e);
        }
    }
}
