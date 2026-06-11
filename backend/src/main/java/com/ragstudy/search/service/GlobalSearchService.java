package com.ragstudy.search.service;

import com.ragstudy.chat.dal.repository.ChatConversationRepository;
import com.ragstudy.knowledge.dal.dataobject.KnowledgeBaseEntity;
import com.ragstudy.knowledge.dal.repository.KnowledgeBaseRepository;
import com.ragstudy.knowledge.dal.repository.KnowledgeDocumentRepository;
import com.ragstudy.note.dal.repository.NoteRepository;
import com.ragstudy.search.controller.dto.GlobalSearchResultDto;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class GlobalSearchService {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 100;

    private final NoteRepository noteRepository;
    private final KnowledgeDocumentRepository knowledgeDocumentRepository;
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final ChatConversationRepository chatConversationRepository;

    public GlobalSearchService(
            NoteRepository noteRepository,
            KnowledgeDocumentRepository knowledgeDocumentRepository,
            KnowledgeBaseRepository knowledgeBaseRepository,
            ChatConversationRepository chatConversationRepository
    ) {
        this.noteRepository = noteRepository;
        this.knowledgeDocumentRepository = knowledgeDocumentRepository;
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.chatConversationRepository = chatConversationRepository;
    }

    @Transactional(readOnly = true)
    public List<GlobalSearchResultDto> search(String userId, String query, String scope, Integer limit) {
        String normalizedQuery = query == null ? "" : query.trim();

        if (!StringUtils.hasText(normalizedQuery)) {
            return List.of();
        }

        int safeLimit = normalizeLimit(limit);
        PageRequest pageRequest = PageRequest.of(0, safeLimit);
        SearchScope searchScope = SearchScope.from(scope);
        List<ResultWithUpdatedAt> results = new ArrayList<>();

        if (searchScope.includes(SearchScope.NOTE)) {
            noteRepository.searchByUserId(userId, normalizedQuery, pageRequest)
                    .forEach(note -> results.add(new ResultWithUpdatedAt(
                            new GlobalSearchResultDto(
                                    "note-" + note.getId(),
                                    "NOTE",
                                    displayNoteTitle(note.getTitle()),
                                    buildDescription(note.getContent(), normalizedQuery),
                                    note.getId(),
                                    null,
                                    note.getUpdatedAt().toString()
                            ),
                            note.getUpdatedAt()
                    )));
        }

        if (searchScope.includes(SearchScope.KNOWLEDGE)) {
            Map<String, String> knowledgeBaseNames = knowledgeBaseRepository.findAllByUserIdOrderByUpdatedAtDesc(userId)
                    .stream()
                    .collect(Collectors.toMap(KnowledgeBaseEntity::getId, KnowledgeBaseEntity::getName));

            knowledgeDocumentRepository.searchByUserId(userId, normalizedQuery, pageRequest)
                    .forEach(document -> {
                        String knowledgeBaseName = knowledgeBaseNames.getOrDefault(document.getKnowledgeBaseId(), "知识库");
                        String resultType = "web".equals(document.getSourceType()) ? "WEB" : "KNOWLEDGE";
                        results.add(new ResultWithUpdatedAt(
                                new GlobalSearchResultDto(
                                        resultType.toLowerCase() + "-" + document.getId(),
                                        resultType,
                                        document.getTitle(),
                                        knowledgeBaseName + " · " + buildDescription(document.getRawContent(), normalizedQuery),
                                        document.getId(),
                                        document.getKnowledgeBaseId(),
                                        document.getUpdatedAt().toString()
                                ),
                                document.getUpdatedAt()
                        ));
                    });
        }

        if (searchScope.includes(SearchScope.CONVERSATION)) {
            chatConversationRepository.searchActiveByUserId(userId, normalizedQuery, pageRequest)
                    .forEach(conversation -> results.add(new ResultWithUpdatedAt(
                            new GlobalSearchResultDto(
                                    "conversation-" + conversation.getId(),
                                    "CONVERSATION",
                                    conversation.getTitle(),
                                    "更新时间：" + conversation.getUpdatedAt(),
                                    conversation.getId(),
                                    null,
                                    conversation.getUpdatedAt().toString()
                            ),
                            conversation.getUpdatedAt()
                    )));
        }

        return results.stream()
                .sorted(Comparator.comparing(ResultWithUpdatedAt::updatedAt).reversed())
                .limit(safeLimit)
                .map(ResultWithUpdatedAt::result)
                .toList();
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }

        return Math.min(MAX_LIMIT, Math.max(1, limit));
    }

    private String displayNoteTitle(String title) {
        String normalizedTitle = title == null ? "" : title.replace('\\', '/');
        String[] parts = normalizedTitle.split("/");

        for (int index = parts.length - 1; index >= 0; index -= 1) {
            String part = parts[index].trim();

            if (StringUtils.hasText(part)) {
                return part;
            }
        }

        return normalizedTitle;
    }

    private String buildDescription(String content, String query) {
        String text = content == null ? "" : content.replaceAll("\\s+", " ").trim();

        if (!StringUtils.hasText(text)) {
            return "";
        }

        String lowerText = text.toLowerCase();
        String lowerQuery = query.toLowerCase();
        int matchIndex = lowerText.indexOf(lowerQuery);
        int startIndex = matchIndex > 30 ? matchIndex - 30 : 0;
        int endIndex = Math.min(text.length(), startIndex + 140);

        return (startIndex > 0 ? "..." : "")
                + text.substring(startIndex, endIndex)
                + (endIndex < text.length() ? "..." : "");
    }

    private record ResultWithUpdatedAt(GlobalSearchResultDto result, LocalDateTime updatedAt) {
    }

    private enum SearchScope {
        ALL,
        NOTE,
        KNOWLEDGE,
        CONVERSATION,
        WEB;

        private boolean includes(SearchScope candidate) {
            return this == ALL || this == candidate;
        }

        private static SearchScope from(String value) {
            if (!StringUtils.hasText(value)) {
                return ALL;
            }

            try {
                return SearchScope.valueOf(value.trim().toUpperCase());
            } catch (IllegalArgumentException exception) {
                return ALL;
            }
        }
    }
}
