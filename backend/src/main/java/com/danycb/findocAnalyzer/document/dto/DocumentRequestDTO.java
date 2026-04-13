package com.danycb.findocAnalyzer.document.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DocumentRequestDTO {

    @NotBlank(message = "File name is required")
    private String fileName;

    @NotNull(message = "File size is required")
    @Positive(message = "File size must be positive")
    private Long size;

    @NotBlank(message = "Content type is required")
    private String type;
}
