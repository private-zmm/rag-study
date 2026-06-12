package com.ragstudy.chat.dal.dataobject;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "chat_messages")
public class ChatMessageEntity {

    @Id
    private String id;

    @Column(nullable = false)
    private String conversationId;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false)
    private String role;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(columnDefinition = "text")
    private String suggestedQuestions;

    @Column(nullable = false)
    private int sortOrder;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected ChatMessageEntity() {
    }

    public ChatMessageEntity(String id, String conversationId, String userId, String role, String content, int sortOrder, LocalDateTime createdAt) {
        this(id, conversationId, userId, role, content, null, sortOrder, createdAt);
    }

    public ChatMessageEntity(
            String id,
            String conversationId,
            String userId,
            String role,
            String content,
            String suggestedQuestions,
            int sortOrder,
            LocalDateTime createdAt
    ) {
        this.id = id;
        this.conversationId = conversationId;
        this.userId = userId;
        this.role = role;
        this.content = content;
        this.suggestedQuestions = suggestedQuestions;
        this.sortOrder = sortOrder;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public String getConversationId() {
        return conversationId;
    }

    public String getUserId() {
        return userId;
    }

    public String getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }

    public String getSuggestedQuestions() {
        return suggestedQuestions;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
