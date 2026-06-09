package com.resumebuilder.backend.model;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "resumes", indexes = {
    @Index(name = "idx_resume_user_id", columnList = "userId")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Resume {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String email;
    private String phone;
    
    @Column(columnDefinition = "TEXT")
    private String summary;
    
    private String experienceType; // "FRESHER" or "EXPERIENCED"
    private String theme; // "modern", "executive", "creative", "ats"
    
    private Long userId; // The owner user's ID

    @Builder.Default
    private Boolean isWhiteboard = false;

    @Column(columnDefinition = "TEXT")
    private String whiteboardData;

    @OneToMany(mappedBy = "resume", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference
    @Builder.Default
    private List<ExperienceDetail> experienceDetails = new ArrayList<>();

    @OneToMany(mappedBy = "resume", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference
    @Builder.Default
    private List<EducationDetail> educationDetails = new ArrayList<>();

    @OneToMany(mappedBy = "resume", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference
    @Builder.Default
    private List<ProjectDetail> projectDetails = new ArrayList<>();

    @OneToMany(mappedBy = "resume", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference
    @Builder.Default
    private List<Skill> skills = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "resume_achievements", joinColumns = @JoinColumn(name = "resume_id"))
    @Column(name = "achievement", columnDefinition = "TEXT")
    @Builder.Default
    private List<String> achievements = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "resume_extracurriculars", joinColumns = @JoinColumn(name = "resume_id"))
    @Column(name = "activity", columnDefinition = "TEXT")
    @Builder.Default
    private List<String> extracurriculars = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "resume_hobbies", joinColumns = @JoinColumn(name = "resume_id"))
    @Column(name = "hobby")
    @Builder.Default
    private List<String> hobbies = new ArrayList<>();
}
