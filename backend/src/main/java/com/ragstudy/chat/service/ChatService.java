package com.ragstudy.chat.service;

import com.ragstudy.chat.controller.dto.ChatConversationDto;
import com.ragstudy.chat.controller.dto.ChatConversationRenameRequest;
import com.ragstudy.chat.controller.dto.ChatMessageDto;
import com.ragstudy.chat.controller.dto.ChatRequest;
import com.ragstudy.chat.controller.dto.ChatSendResponse;
import com.ragstudy.chat.convert.ChatConvert;
import com.ragstudy.chat.dal.dataobject.ChatConversationEntity;
import com.ragstudy.chat.dal.dataobject.ChatMessageEntity;
import com.ragstudy.chat.dal.dataobject.ChatModelConfigEntity;
import com.ragstudy.chat.dal.repository.ChatConversationRepository;
import com.ragstudy.chat.dal.repository.ChatMessageRepository;
import com.ragstudy.chat.framework.AiChatProperties;
import com.ragstudy.chat.framework.OpenAiCompatibleChatClient;
import com.ragstudy.knowledge.service.KnowledgeRagService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class ChatService {

    private final OpenAiCompatibleChatClient chatClient;
    private final ChatModelConfigService modelConfigService;
    private final ChatConversationRepository conversationRepository;
    private final ChatMessageRepository messageRepository;
    private final KnowledgeRagService knowledgeRagService;
    private final AiChatProperties aiChatProperties;
    private final ChatSuggestionService chatSuggestionService;

    public ChatService(
            OpenAiCompatibleChatClient chatClient,
            ChatModelConfigService modelConfigService,
            ChatConversationRepository conversationRepository,
            ChatMessageRepository messageRepository,
            KnowledgeRagService knowledgeRagService,
            AiChatProperties aiChatProperties,
            ChatSuggestionService chatSuggestionService
    ) {
        this.chatClient = chatClient;
        this.modelConfigService = modelConfigService;
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.knowledgeRagService = knowledgeRagService;
        this.aiChatProperties = aiChatProperties;
        this.chatSuggestionService = chatSuggestionService;
    }

    @Transactional(readOnly = true)
    public List<ChatConversationDto> listConversations(String userId, boolean archived) {
        return conversationRepository.findAllByUserIdAndArchivedOrderByUpdatedAtDesc(userId, archived)
                .stream()
                .map(ChatConvert::toConversationDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ChatMessageDto> listMessages(String userId, String conversationId) {
        ChatConversationEntity conversation = requireOwnedConversation(userId, conversationId);
        return messageRepository.findAllByConversationIdOrderBySortOrderAsc(conversation.getId())
                .stream()
                .map(ChatConvert::toMessageDto)
                .toList();
    }

    @Transactional
    public ChatSendResponse sendMessage(String userId, ChatRequest request) {
        ChatModelConfigEntity modelConfig = modelConfigService.requireConfigForChat(userId, request.modelConfigId());
        ChatConversationEntity conversation = getOrCreateConversation(userId, request, modelConfig.getId());

        saveMessage(conversation, userId, "user", request.content());
        List<ChatMessageDto> conversationSnapshot = listMessages(userId, conversation.getId());
        List<ChatMessageDto> modelSnapshot = buildModelSnapshot(userId, request, conversationSnapshot);
        String reply = chatClient.generateReply(modelSnapshot, modelConfig);
        List<String> suggestedQuestions = chatSuggestionService.generateFollowUpSuggestions(modelSnapshot, reply, modelConfig);
        saveMessage(conversation, userId, "ai", reply, suggestedQuestions);

        return new ChatSendResponse(conversation.getId(), listMessages(userId, conversation.getId()));
    }

    @Transactional
    public ChatConversationDto renameConversation(String userId, String conversationId, ChatConversationRenameRequest request) {
        ChatConversationEntity conversation = requireOwnedConversation(userId, conversationId);
        conversation.rename(request.title().trim());
        return ChatConvert.toConversationDto(conversationRepository.save(conversation));
    }

    @Transactional
    public ChatConversationDto archiveConversation(String userId, String conversationId) {
        ChatConversationEntity conversation = requireOwnedConversation(userId, conversationId);
        conversation.archive();
        return ChatConvert.toConversationDto(conversationRepository.save(conversation));
    }

    @Transactional
    public ChatConversationDto restoreConversation(String userId, String conversationId) {
        ChatConversationEntity conversation = requireOwnedConversation(userId, conversationId);
        conversation.restore();
        return ChatConvert.toConversationDto(conversationRepository.save(conversation));
    }

    @Transactional
    public void deleteConversation(String userId, String conversationId) {
        ChatConversationEntity conversation = requireOwnedConversation(userId, conversationId);
        messageRepository.deleteAllByConversationId(conversation.getId());
        conversationRepository.delete(conversation);
    }

    @Transactional
    public String prepareStreamConversation(String userId, ChatRequest request) {
        ChatModelConfigEntity modelConfig = modelConfigService.requireConfigForChat(userId, request.modelConfigId());
        ChatConversationEntity conversation = getOrCreateConversation(userId, request, modelConfig.getId());
        bindKnowledgeBaseIfPresent(conversation, request.knowledgeBaseId());
        saveMessage(conversation, userId, "user", request.content());
        return conversation.getId();
    }

    @Transactional(readOnly = true)
    public List<ChatMessageDto> getConversationSnapshot(String userId, String conversationId) {
        return listMessages(userId, conversationId);
    }

    @Transactional
    public void saveAssistantMessage(String userId, String conversationId, String content) {
        saveAssistantMessage(userId, conversationId, content, List.of());
    }

    @Transactional
    public void saveAssistantMessage(String userId, String conversationId, String content, List<String> suggestedQuestions) {
        ChatConversationEntity conversation = requireOwnedConversation(userId, conversationId);
        saveMessage(conversation, userId, "ai", content, suggestedQuestions);
    }

    @Transactional(readOnly = true)
    public ChatModelConfigEntity requireModelConfig(String userId, String modelConfigId) {
        return modelConfigService.requireConfigForChat(userId, modelConfigId);
    }

    public String streamReply(List<ChatMessageDto> conversationSnapshot, ChatModelConfigEntity modelConfig, ChatStreamHandler handler) {
        return chatClient.streamReply(conversationSnapshot, modelConfig, handler);
    }

    public List<String> generateFollowUpSuggestions(
            List<ChatMessageDto> conversationSnapshot,
            String assistantReply,
            ChatModelConfigEntity modelConfig
    ) {
        return chatSuggestionService.generateFollowUpSuggestions(conversationSnapshot, assistantReply, modelConfig);
    }

    public List<ChatMessageDto> enrichStreamSnapshot(String userId, ChatRequest request, List<ChatMessageDto> conversationSnapshot) {
        return buildModelSnapshot(userId, request, conversationSnapshot);
    }

    private ChatConversationEntity getOrCreateConversation(String userId, ChatRequest request, String modelConfigId) {
        if (request.conversationId() != null && !request.conversationId().isBlank()) {
            ChatConversationEntity conversation = requireOwnedConversation(userId, request.conversationId());
            bindKnowledgeBaseIfPresent(conversation, request.knowledgeBaseId());
            return conversation;
        }

        LocalDateTime now = LocalDateTime.now();
        ChatConversationEntity conversation = new ChatConversationEntity(
                UUID.randomUUID().toString(),
                userId,
                createTitle(request.content()),
                modelConfigId,
                request.knowledgeBaseId(),
                now,
                now
        );

        return conversationRepository.save(conversation);
    }

    private void bindKnowledgeBaseIfPresent(ChatConversationEntity conversation, String knowledgeBaseId) {
        if (!StringUtils.hasText(knowledgeBaseId)) {
            return;
        }

        if (knowledgeBaseId.equals(conversation.getKnowledgeBaseId())) {
            return;
        }

        conversation.bindKnowledgeBase(knowledgeBaseId);
        conversationRepository.save(conversation);
    }

    private ChatConversationEntity requireOwnedConversation(String userId, String conversationId) {
        return conversationRepository.findByIdAndUserId(conversationId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "会话不存在"));
    }

    private void saveMessage(ChatConversationEntity conversation, String userId, String role, String content) {
        saveMessage(conversation, userId, role, content, List.of());
    }

    private void saveMessage(
            ChatConversationEntity conversation,
            String userId,
            String role,
            String content,
            List<String> suggestedQuestions
    ) {
        int sortOrder = messageRepository.countByConversationId(conversation.getId()) + 1;
        ChatMessageEntity message = new ChatMessageEntity(
                UUID.randomUUID().toString(),
                conversation.getId(),
                userId,
                role,
                content,
                chatSuggestionService.toJson(suggestedQuestions),
                sortOrder,
                LocalDateTime.now()
        );
        messageRepository.save(message);

        conversation.touch();
        conversationRepository.save(conversation);
    }

    private String createTitle(String content) {
        String normalizedContent = content.replaceAll("\\s+", " ").trim();

        if (normalizedContent.length() <= 28) {
            return normalizedContent;
        }

        return normalizedContent.substring(0, 28);
    }

    private List<ChatMessageDto> buildModelSnapshot(String userId, ChatRequest request, List<ChatMessageDto> conversationSnapshot) {
        return enrichWithKnowledgeContext(userId, request, trimConversationSnapshot(conversationSnapshot));
    }

    private List<ChatMessageDto> trimConversationSnapshot(List<ChatMessageDto> conversationSnapshot) {
        int maxContextMessages = aiChatProperties.getMaxContextMessages();

        if (conversationSnapshot.size() <= maxContextMessages) {
            return conversationSnapshot;
        }

        return conversationSnapshot.subList(conversationSnapshot.size() - maxContextMessages, conversationSnapshot.size());
    }

    private List<ChatMessageDto> enrichWithKnowledgeContext(String userId, ChatRequest request, List<ChatMessageDto> conversationSnapshot) {
        if (!StringUtils.hasText(request.knowledgeBaseId()) || conversationSnapshot.isEmpty()) {
            return conversationSnapshot;
        }

        String contextualPrompt = knowledgeRagService
                .buildContextualPrompt(userId, request.knowledgeBaseId(), request.content())
                .orElse(null);

        if (!StringUtils.hasText(contextualPrompt)) {
            return conversationSnapshot;
        }

        List<ChatMessageDto> enrichedSnapshot = new ArrayList<>(conversationSnapshot);
        int lastIndex = enrichedSnapshot.size() - 1;
        ChatMessageDto lastMessage = enrichedSnapshot.get(lastIndex);

        if (!"user".equals(lastMessage.role())) {
            return conversationSnapshot;
        }

        enrichedSnapshot.set(lastIndex, new ChatMessageDto(
                lastMessage.id(),
                lastMessage.role(),
                contextualPrompt,
                lastMessage.suggestedQuestions()
        ));

        return enrichedSnapshot;
    }

}
