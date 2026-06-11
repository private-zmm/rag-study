package com.ragstudy.chat.controller;

import com.ragstudy.common.ApiResponse;
import com.ragstudy.auth.service.AuthService;
import com.ragstudy.auth.dal.dataobject.UserEntity;
import com.ragstudy.chat.controller.dto.ChatConversationDto;
import com.ragstudy.chat.controller.dto.ChatConversationRenameRequest;
import com.ragstudy.chat.controller.dto.ChatMessageDto;
import com.ragstudy.chat.controller.dto.ChatRequest;
import com.ragstudy.chat.controller.dto.ChatSendResponse;
import com.ragstudy.chat.dal.dataobject.ChatModelConfigEntity;
import com.ragstudy.chat.service.ChatService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;
    private final AuthService authService;

    public ChatController(ChatService chatService, AuthService authService) {
        this.chatService = chatService;
        this.authService = authService;
    }

    @GetMapping("/conversations")
    public ApiResponse<List<ChatConversationDto>> listConversations(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @RequestParam(defaultValue = "false") boolean archived
    ) {
        UserEntity user = authService.requireUser(authorizationHeader);
        return ApiResponse.ok(chatService.listConversations(user.getId(), archived));
    }

    @GetMapping("/conversations/{conversationId}/messages")
    public ApiResponse<List<ChatMessageDto>> listMessages(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable String conversationId
    ) {
        UserEntity user = authService.requireUser(authorizationHeader);
        return ApiResponse.ok(chatService.listMessages(user.getId(), conversationId));
    }

    @PatchMapping("/conversations/{conversationId}/title")
    public ApiResponse<ChatConversationDto> renameConversation(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable String conversationId,
            @Valid @RequestBody ChatConversationRenameRequest request
    ) {
        UserEntity user = authService.requireUser(authorizationHeader);
        return ApiResponse.ok(chatService.renameConversation(user.getId(), conversationId, request));
    }

    @PatchMapping("/conversations/{conversationId}/archive")
    public ApiResponse<ChatConversationDto> archiveConversation(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable String conversationId
    ) {
        UserEntity user = authService.requireUser(authorizationHeader);
        return ApiResponse.ok(chatService.archiveConversation(user.getId(), conversationId));
    }

    @PatchMapping("/conversations/{conversationId}/restore")
    public ApiResponse<ChatConversationDto> restoreConversation(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable String conversationId
    ) {
        UserEntity user = authService.requireUser(authorizationHeader);
        return ApiResponse.ok(chatService.restoreConversation(user.getId(), conversationId));
    }

    @DeleteMapping("/conversations/{conversationId}")
    public ApiResponse<Void> deleteConversation(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable String conversationId
    ) {
        UserEntity user = authService.requireUser(authorizationHeader);
        chatService.deleteConversation(user.getId(), conversationId);
        return ApiResponse.ok(null);
    }

    @PostMapping("/messages")
    public ApiResponse<ChatSendResponse> sendMessage(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @Valid @RequestBody ChatRequest request
    ) {
        UserEntity user = authService.requireUser(authorizationHeader);
        return ApiResponse.ok(chatService.sendMessage(user.getId(), request));
    }

    @PostMapping(value = "/messages/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamMessage(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @Valid @RequestBody ChatRequest request
    ) {
        UserEntity user = authService.requireUser(authorizationHeader);
        SseEmitter emitter = new SseEmitter(0L);

        CompletableFuture.runAsync(() -> {
            try {
                String conversationId = chatService.prepareStreamConversation(user.getId(), request);
                emitter.send(SseEmitter.event().name("conversation").data(conversationId));
                List<ChatMessageDto> conversationSnapshot = chatService.getConversationSnapshot(user.getId(), conversationId);
                List<ChatMessageDto> modelSnapshot = chatService.enrichStreamSnapshot(user.getId(), request, conversationSnapshot);
                ChatModelConfigEntity modelConfig = chatService.requireModelConfig(user.getId(), request.modelConfigId());
                String reply = chatService.streamReply(modelSnapshot, modelConfig, delta -> sendDelta(emitter, delta));
                chatService.saveAssistantMessage(user.getId(), conversationId, reply);
                emitter.send(SseEmitter.event().name("done").data("[DONE]"));
                emitter.complete();
            } catch (Exception exception) {
                emitter.completeWithError(exception);
            }
        });

        return emitter;
    }

    private void sendDelta(SseEmitter emitter, String delta) {
        try {
            emitter.send(SseEmitter.event().name("delta").data(delta));
        } catch (IOException exception) {
            throw new IllegalStateException("SSE 推送失败", exception);
        }
    }
}
