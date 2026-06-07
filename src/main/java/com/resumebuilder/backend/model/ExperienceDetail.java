package com.resumebuilder.backend.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "experience_details")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExperienceDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String companyName;
    private String role;
    private String startDate;
    private String endDate;
    
    @Column(columnDefinition = "TEXT")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resume_id")
    @JsonBackReference
    private Resume resume;
}
