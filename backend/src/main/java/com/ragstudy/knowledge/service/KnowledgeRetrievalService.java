package com.ragstudy.knowledge.service;

import com.ragstudy.knowledge.controller.dto.KnowledgeSearchResultDto;
import com.ragstudy.knowledge.dal.dataobject.KnowledgeDocumentChunkEntity;
import com.ragstudy.knowledge.dal.repository.KnowledgeBaseRepository;
import com.ragstudy.knowledge.dal.repository.KnowledgeDocumentChunkRepository;
import com.ragstudy.knowledge.framework.QdrantVectorService;
import com.ragstudy.knowledge.framework.RagProperties;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class KnowledgeRetrievalService {

    private static final double RRF_K = 60.0;
    private static final int CANDIDATE_MULTIPLIER = 4;
    private static final Pattern QUERY_TOKEN_PATTERN = Pattern.compile("[\\p{Punct}\\s]+");

    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final KnowledgeDocumentChunkRepository chunkRepository;
    private final QdrantVectorService qdrantVectorService;
    private final RagProperties ragProperties;

    public KnowledgeRetrievalService(
            KnowledgeBaseRepository knowledgeBaseRepository,
            KnowledgeDocumentChunkRepository chunkRepository,
            QdrantVectorService qdrantVectorService,
            RagProperties ragProperties
    ) {
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.chunkRepository = chunkRepository;
        this.qdrantVectorService = qdrantVectorService;
        this.ragProperties = ragProperties;
    }

    @Transactional(readOnly = true)
    public List<KnowledgeSearchResultDto> search(String userId, String knowledgeBaseId, String query, int limit) {
        return search(userId, knowledgeBaseId, query, Integer.valueOf(limit));
    }

    @Transactional(readOnly = true)
    public List<KnowledgeSearchResultDto> search(String userId, String knowledgeBaseId, String query, Integer limit) {
        requireOwnedKnowledgeBase(userId, knowledgeBaseId);
        int normalizedLimit = normalizeLimit(limit);
        int candidateLimit = Math.min(50, Math.max(normalizedLimit * CANDIDATE_MULTIPLIER, normalizedLimit));

        List<SearchCandidate> vectorCandidates = searchVectorCandidates(userId, knowledgeBaseId, query, candidateLimit);
        List<SearchCandidate> keywordCandidates = searchKeywordCandidates(userId, knowledgeBaseId, query, candidateLimit);

        return mergeCandidates(vectorCandidates, keywordCandidates)
                .stream()
                .limit(normalizedLimit)
                .map(SearchCandidate::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<KnowledgeSearchResultDto> listContextChunks(String userId, String knowledgeBaseId, int limit) {
        return listContextChunks(userId, knowledgeBaseId, Integer.valueOf(limit));
    }

    @Transactional(readOnly = true)
    public List<KnowledgeSearchResultDto> listContextChunks(String userId, String knowledgeBaseId, Integer limit) {
        requireOwnedKnowledgeBase(userId, knowledgeBaseId);
        int normalizedLimit = normalizeLimit(limit);

        return chunkRepository.findAllByKnowledgeBaseIdAndUserIdOrderByDocumentIdAscChunkIndexAsc(knowledgeBaseId, userId)
                .stream()
                .limit(normalizedLimit)
                .map(chunk -> new KnowledgeSearchResultDto(
                        chunk.getId(),
                        chunk.getDocumentId(),
                        chunk.getTitlePath(),
                        chunk.getHeading(),
                        chunk.getContent(),
                        0
                ))
                .toList();
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null) {
            return ragProperties.getTopK();
        }

        return Math.max(1, Math.min(limit, 50));
    }

    private void requireOwnedKnowledgeBase(String userId, String knowledgeBaseId) {
        knowledgeBaseRepository.findByIdAndUserId(knowledgeBaseId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "知识库不存在"));
    }

    private List<SearchCandidate> searchVectorCandidates(String userId, String knowledgeBaseId, String query, int limit) {
        try {
            return qdrantVectorService.search(userId, knowledgeBaseId, query, limit)
                    .stream()
                    .filter(result -> result.score() >= ragProperties.getMinScore())
                    .map(result -> new SearchCandidate(
                            result.chunkId(),
                            result.documentId(),
                            result.titlePath(),
                            result.heading(),
                            result.content(),
                            result.score(),
                            0,
                            0
                    ))
                    .toList();
        } catch (Exception exception) {
            return List.of();
        }
    }

    private List<SearchCandidate> searchKeywordCandidates(String userId, String knowledgeBaseId, String query, int limit) {
        if (!StringUtils.hasText(query)) {
            return List.of();
        }

        return chunkRepository.searchByKeyword(userId, knowledgeBaseId, query.trim(), PageRequest.of(0, limit))
                .stream()
                .map(chunk -> new SearchCandidate(
                        chunk.getId(),
                        chunk.getDocumentId(),
                        chunk.getTitlePath(),
                        chunk.getHeading(),
                        chunk.getContent(),
                        0,
                        keywordScore(chunk, query),
                        0
                ))
                .toList();
    }

    private List<SearchCandidate> mergeCandidates(List<SearchCandidate> vectorCandidates, List<SearchCandidate> keywordCandidates) {
        Map<String, SearchCandidate> candidateMap = new LinkedHashMap<>();
        applyRrfScores(candidateMap, vectorCandidates);
        applyRrfScores(candidateMap, keywordCandidates);

        return new ArrayList<>(candidateMap.values())
                .stream()
                .sorted(Comparator.comparing(SearchCandidate::score).reversed())
                .toList();
    }

    private void applyRrfScores(Map<String, SearchCandidate> candidateMap, List<SearchCandidate> candidates) {
        for (int index = 0; index < candidates.size(); index += 1) {
            SearchCandidate candidate = candidates.get(index);
            double rrfScore = 1.0 / (RRF_K + index + 1);
            SearchCandidate currentCandidate = candidateMap.get(candidate.chunkId());

            if (currentCandidate == null) {
                candidateMap.put(candidate.chunkId(), candidate.withScore(rrfScore));
                continue;
            }

            candidateMap.put(candidate.chunkId(), currentCandidate.merge(candidate, rrfScore));
        }
    }

    private double keywordScore(KnowledgeDocumentChunkEntity chunk, String query) {
        List<String> queryTerms = extractQueryTerms(query);

        if (queryTerms.isEmpty()) {
            return 0;
        }

        String titlePath = normalizeText(chunk.getTitlePath());
        String heading = normalizeText(chunk.getHeading());
        String content = normalizeText(chunk.getContent());
        double score = 0;

        for (String term : queryTerms) {
            if (titlePath.contains(term)) {
                score += 3;
            }

            if (heading.contains(term)) {
                score += 2;
            }

            if (content.contains(term)) {
                score += 1;
            }
        }

        return score;
    }

    private List<String> extractQueryTerms(String query) {
        String normalizedQuery = normalizeText(query);

        if (!StringUtils.hasText(normalizedQuery)) {
            return List.of();
        }

        List<String> splitTerms = QUERY_TOKEN_PATTERN.splitAsStream(normalizedQuery)
                .map(String::trim)
                .filter(StringUtils::hasText)
                .filter(term -> term.length() > 1)
                .toList();

        if (!splitTerms.isEmpty()) {
            return splitTerms;
        }

        return List.of(normalizedQuery);
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private record SearchCandidate(
            String chunkId,
            String documentId,
            String titlePath,
            String heading,
            String content,
            double vectorScore,
            double keywordScore,
            double score
    ) {

        private SearchCandidate withScore(double rrfScore) {
            return new SearchCandidate(
                    chunkId,
                    documentId,
                    titlePath,
                    heading,
                    content,
                    vectorScore,
                    keywordScore,
                    rrfScore + normalizedKeywordScore()
            );
        }

        private SearchCandidate merge(SearchCandidate candidate, double rrfScore) {
            return new SearchCandidate(
                    chunkId,
                    documentId,
                    titlePath,
                    heading,
                    content,
                    Math.max(vectorScore, candidate.vectorScore),
                    Math.max(keywordScore, candidate.keywordScore),
                    score + rrfScore + candidate.normalizedKeywordScore()
            );
        }

        private double normalizedKeywordScore() {
            if (keywordScore <= 0) {
                return 0;
            }

            return Math.min(keywordScore, 10) / 100.0;
        }

        private KnowledgeSearchResultDto toDto() {
            return new KnowledgeSearchResultDto(
                    chunkId,
                    documentId,
                    titlePath,
                    heading,
                    content,
                    score
            );
        }
    }
}
