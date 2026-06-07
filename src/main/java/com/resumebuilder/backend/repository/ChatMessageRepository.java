package com.resumebuilder.backend.repository;

import com.resumebuilder.backend.model.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findByUserIdOrderByTimestampAsc(Long userId);

    @Transactional
    void deleteByUserId(Long userId);
}
