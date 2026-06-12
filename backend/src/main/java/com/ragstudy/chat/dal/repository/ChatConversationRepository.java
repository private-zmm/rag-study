package com.ragstudy.chat.dal.repository;

import com.ragstudy.chat.dal.dataobject.ChatConversationEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChatConversationRepository extends JpaRepository<ChatConversationEntity, String> {

    List<ChatConversationEntity> findAllByUserIdAndArchivedOrderByUpdatedAtDesc(String userId, boolean archived);

    Optional<ChatConversationEntity> findByIdAndUserId(String id, String userId);

    @Query("""
            select conversation from ChatConversationEntity conversation
            where conversation.userId = :userId
              and conversation.archived = false
              and lower(conversation.title) like lower(concat('%', :query, '%'))
            order by conversation.updatedAt desc
            """)
    List<ChatConversationEntity> searchActiveByUserId(
            @Param("userId") String userId,
            @Param("query") String query,
            Pageable pageable
    );
}
