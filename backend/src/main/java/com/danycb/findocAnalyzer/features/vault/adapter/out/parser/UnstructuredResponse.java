package com.danycb.findocAnalyzer.features.vault.adapter.out.parser;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class UnstructuredResponse {
    @JsonProperty("element_id")
    private String elementId;
    private String type;
    private String text;
    private Metadata metadata;

    @Getter
    @Setter
    private static class Metadata {
        @JsonProperty("page_number")
        private int pageNumber;
        private List<String> languages;
        private String filename;
        private String filetype;
    }

    public Integer getPageNumber() {
        if (metadata != null && metadata.getPageNumber() != 0) {
            return metadata.getPageNumber();
        }
        return 1;
    }
}