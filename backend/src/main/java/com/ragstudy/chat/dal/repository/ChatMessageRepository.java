package com.ragstudy.chat.dal.repository;

import com.ragstudy.chat.dal.dataobject.ChatMessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessageEntity, String> {

    List<ChatMessageEntity> findAllByConversationIdOrderBySortOrderAsc(String conversationId);

    int countByConversationId(String conversationId);

    void deleteAllByConversationId(String conversationId);
}
