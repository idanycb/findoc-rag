package com.danycb.findocAnalyzer.features.chat.adapter.out.vector;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties("findoc.retrieval")
public class RetrievalProperties {
    private int tracePoolSize = 15;
    private int maxSections = 6;
    private double minScore = 0.60;

    public int getTracePoolSize() {
        return tracePoolSize;
    }

    public void setTracePoolSize(int tracePoolSize) {
        this.tracePoolSize = tracePoolSize;
    }

    public int getMaxSections() {
        return maxSections;
    }

    public void setMaxSections(int maxSections) {
        this.maxSections = maxSections;
    }

    public double getMinScore() {
        return minScore;
    }

    public void setMinScore(double minScore) {
        this.minScore = minScore;
    }
}
