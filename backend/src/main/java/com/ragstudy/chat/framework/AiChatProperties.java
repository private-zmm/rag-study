package com.ragstudy.chat.framework;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "rag-study.ai")
public class AiChatProperties {

    private String baseUrl = "";
    private String apiKey = "";
    private String model = "gpt-4o-mini";
    private String systemPrompt = "你是一个面向学习、笔记和知识库整理的中文助手。回答要清晰、准确、简洁。";
    private double temperature = 0.3;
    private int maxContextMessages = 10;
    private boolean followUpSuggestionsEnabled = true;
    private int followUpSuggestionCount = 5;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    public int getMaxContextMessages() {
        return Math.max(1, maxContextMessages);
    }

    public void setMaxContextMessages(int maxContextMessages) {
        this.maxContextMessages = maxContextMessages;
    }

    public boolean isFollowUpSuggestionsEnabled() {
        return followUpSuggestionsEnabled;
    }

    public void setFollowUpSuggestionsEnabled(boolean followUpSuggestionsEnabled) {
        this.followUpSuggestionsEnabled = followUpSuggestionsEnabled;
    }

    public int getFollowUpSuggestionCount() {
        return Math.max(1, Math.min(followUpSuggestionCount, 8));
    }

    public void setFollowUpSuggestionCount(int followUpSuggestionCount) {
        this.followUpSuggestionCount = followUpSuggestionCount;
    }
}
