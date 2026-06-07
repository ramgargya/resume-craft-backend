package com.resumebuilder.backend.repository;

import com.resumebuilder.backend.model.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findByUserIdOrderByTimestampAsc(Long userId);

    @org.springframework.data.jpa.repository.Query(
        "SELECT c.threadId, c.threadTitle, MAX(c.timestamp) " +
        "FROM ChatMessage c " +
        "WHERE c.userId = :userId " +
        "GROUP BY c.threadId, c.threadTitle " +
        "ORDER BY MAX(c.timestamp) DESC"
    )
    List<Object[]> findUniqueThreadsByUserId(@org.springframework.data.repository.query.Param("userId") Long userId);

    List<ChatMessage> findByUserIdAndThreadIdOrderByTimestampAsc(Long userId, String threadId);

    @Transactional
    void deleteByUserId(Long userId);

    @Transactional
    void deleteByUserIdAndThreadId(Long userId, String threadId);
}
