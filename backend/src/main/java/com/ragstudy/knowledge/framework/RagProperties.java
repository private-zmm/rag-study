package com.ragstudy.knowledge.framework;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rag-study.rag")
public class RagProperties {

    private int chunkSize = 900;
    private int chunkOverlap = 120;
    private int topK = 5;
    private double minScore = 0;
    private int maxContextTokens = 2400;
    private int sameDocumentMaxChunks = 2;
    private boolean indexAsyncEnabled = false;

    public int getChunkSize() {
        return Math.max(100, chunkSize);
    }

    public void setChunkSize(int chunkSize) {
        this.chunkSize = chunkSize;
    }

    public int getChunkOverlap() {
        return Math.max(0, Math.min(chunkOverlap, getChunkSize() - 1));
    }

    public void setChunkOverlap(int chunkOverlap) {
        this.chunkOverlap = chunkOverlap;
    }

    public int getTopK() {
        return Math.max(1, Math.min(topK, 50));
    }

    public void setTopK(int topK) {
        this.topK = topK;
    }

    public double getMinScore() {
        return Math.max(0, minScore);
    }

    public void setMinScore(double minScore) {
        this.minScore = minScore;
    }

    public int getMaxContextTokens() {
        return Math.max(1, maxContextTokens);
    }

    public void setMaxContextTokens(int maxContextTokens) {
        this.maxContextTokens = maxContextTokens;
    }

    public int getSameDocumentMaxChunks() {
        return Math.max(1, sameDocumentMaxChunks);
    }

    public void setSameDocumentMaxChunks(int sameDocumentMaxChunks) {
        this.sameDocumentMaxChunks = sameDocumentMaxChunks;
    }

    public boolean isIndexAsyncEnabled() {
        return indexAsyncEnabled;
    }

    public void setIndexAsyncEnabled(boolean indexAsyncEnabled) {
        this.indexAsyncEnabled = indexAsyncEnabled;
    }
}
