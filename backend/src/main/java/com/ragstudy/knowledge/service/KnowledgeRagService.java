package com.ragstudy.knowledge.service;

import com.ragstudy.knowledge.controller.dto.KnowledgeSearchResultDto;
import com.ragstudy.knowledge.framework.RagProperties;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class KnowledgeRagService {

    private final KnowledgeRetrievalService retrievalService;
    private final RagProperties ragProperties;

    public KnowledgeRagService(KnowledgeRetrievalService retrievalService, RagProperties ragProperties) {
        this.retrievalService = retrievalService;
        this.ragProperties = ragProperties;
    }

    public Optional<String> buildContextualPrompt(String userId, String knowledgeBaseId, String originalQuestion) {
        if (!StringUtils.hasText(knowledgeBaseId)) {
            return Optional.empty();
        }

        List<KnowledgeSearchResultDto> searchResults;

        try {
            searchResults = retrievalService.search(userId, knowledgeBaseId, originalQuestion, ragProperties.getTopK());
        } catch (Exception exception) {
            searchResults = retrievalService.listContextChunks(userId, knowledgeBaseId, ragProperties.getTopK());
        }

        if (searchResults.isEmpty()) {
            searchResults = retrievalService.listContextChunks(userId, knowledgeBaseId, ragProperties.getTopK());
        }

        searchResults = limitContext(searchResults);

        if (searchResults.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(buildRagPrompt(originalQuestion, searchResults));
    }

    private String buildRagPrompt(String originalQuestion, List<KnowledgeSearchResultDto> searchResults) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("用户已经选择了知识库，以下资料就是从该知识库中取出的内容。")
                .append("\n请直接基于这些资料回答用户问题，不要要求用户再次提供资料。")
                .append("\n如果资料确实不足，请先总结已提供资料，再说明还缺少什么。")
                .append("\n\n");

        for (int index = 0; index < searchResults.size(); index += 1) {
            KnowledgeSearchResultDto result = searchResults.get(index);
            prompt.append("[资料 ").append(index + 1).append("]")
                    .append("\n")
                    .append("来源：").append(formatSource(result)).append("\n")
                    .append(result.content())
                    .append("\n\n");
        }

        prompt.append("用户问题：\n").append(originalQuestion);
        return prompt.toString();
    }

    private List<KnowledgeSearchResultDto> limitContext(List<KnowledgeSearchResultDto> searchResults) {
        List<KnowledgeSearchResultDto> limitedResults = new ArrayList<>();
        Map<String, Integer> documentChunkCounts = new HashMap<>();
        int usedTokens = 0;

        for (KnowledgeSearchResultDto result : searchResults) {
            int documentChunkCount = documentChunkCounts.getOrDefault(result.documentId(), 0);

            if (documentChunkCount >= ragProperties.getSameDocumentMaxChunks()) {
                continue;
            }

            int chunkTokens = estimateTokenCount(result.content());

            if (usedTokens > 0 && usedTokens + chunkTokens > ragProperties.getMaxContextTokens()) {
                continue;
            }

            limitedResults.add(result);
            documentChunkCounts.put(result.documentId(), documentChunkCount + 1);
            usedTokens += chunkTokens;
        }

        return limitedResults;
    }

    private int estimateTokenCount(String content) {
        return Math.max(1, (int) Math.ceil(content.length() / 1.8));
    }

    private String formatSource(KnowledgeSearchResultDto result) {
        if (StringUtils.hasText(result.titlePath())) {
            return result.titlePath().replace(" / ", " > ");
        }

        if (StringUtils.hasText(result.heading())) {
            return result.heading();
        }

        return result.documentId();
    }
}
