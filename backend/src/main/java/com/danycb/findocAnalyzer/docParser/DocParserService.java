package com.danycb.findocAnalyzer.docParser;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

@Slf4j
@Service
public class DocParserService {

    private final WebClient webClient;

    public DocParserService(@Value("${unstructured.url:http://localhost:8000/general/v0/general}") String unstructuredUrl) {
        this.webClient = WebClient.create(unstructuredUrl);
    }

    public Flux<UnstructuredResponseDTO> extractTextFromPdf(byte[] fileBytes, String fileName) {
        log.info("Sending document {} to Unstructured.io container for reactive partitioning", fileName);

        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("files", new ByteArrayResource(fileBytes))
                .filename(fileName)
                .contentType(MediaType.APPLICATION_PDF);

        builder.part("strategy", "fast");
        builder.part("chunking_strategy", "by_title");

        MultiValueMap<String, HttpEntity<?>> multipartBody = builder.build();

        return webClient.post()
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(multipartBody))
                .retrieve()
                .bodyToFlux(UnstructuredResponseDTO.class)
                .doOnComplete(() -> log.info("Completed processing document {} with Unstructured.io", fileName))
                .doOnError(throwable -> log.error("Error processing document {} with Unstructured.io: {}", fileName, throwable.getMessage()));
    }
}


