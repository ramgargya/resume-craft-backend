package com.resumebuilder.backend.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "education_details")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EducationDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String institution;
    private String degree;
    private String fieldOfStudy;
    private String graduationDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resume_id")
    @JsonBackReference
    private Resume resume;
}
