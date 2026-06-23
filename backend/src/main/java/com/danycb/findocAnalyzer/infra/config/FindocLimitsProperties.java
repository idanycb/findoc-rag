package com.danycb.findocAnalyzer.infra.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "findoc.limits")
public class FindocLimitsProperties {
    private boolean enabled;
    private Integer maxDocuments;
    private Integer maxUsers;
    private Integer maxTeams;
    private Long maxFileSizeBytes;
}
