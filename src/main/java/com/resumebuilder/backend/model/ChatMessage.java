package com.resumebuilder.backend.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "chat_messages", indexes = {
    @Index(name = "idx_chat_msg_user_thread", columnList = "userId, threadId")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId; // The owner user's ID
    private String role; // "user" or "model"

    @Column(columnDefinition = "TEXT")
    private String content;

    private LocalDateTime timestamp;

    private String threadId;
    private String threadTitle;
}
