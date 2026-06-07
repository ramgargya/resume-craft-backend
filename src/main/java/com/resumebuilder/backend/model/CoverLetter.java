package com.resumebuilder.backend.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "cover_letters")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CoverLetter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    
    @Column(nullable = false)
    private Long userId; // Owner user ID
    
    private String theme; // "modern", "executive", "creative", "ats"

    // Sender Info
    private String senderName;
    private String senderEmail;
    private String senderPhone;

    // Recipient Info
    private String recipientName;
    private String recipientTitle;
    private String recipientCompany;
    
    @Column(columnDefinition = "TEXT")
    private String recipientAddress;

    // Content Info
    private String dateText; // Custom date string (e.g. June 7, 2026)
    private String subjectLine;
    private String salutation; // e.g. Dear Hiring Manager,

    @Column(columnDefinition = "TEXT")
    private String letterBody;

    @Column(columnDefinition = "TEXT")
    private String signOff; // e.g. Sincerely, John Doe
}
